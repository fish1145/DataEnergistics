package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * CPU-owned custody metadata, independent of the current job's lifetime. Only untransferred escrow is a local
 * physical asset; transferred tools and settlement fingerprints must never be reconstituted as inventory.
 * All mutation is confined to the logical server thread and saved together with the CPU inventory.
 */
public final class ReusableCpuSessionLedger {

    /** One exact submission and its one-time accounting boundary. Expected outputs are metadata, not owned items. */
    public record Submission(Work work, long count, long logicalOffer, double energy,
                             List<GenericStack> expectedOutputs, List<SlotStack> escrow,
                             boolean transferred, boolean accounted) {

        public Submission {
            if (count <= 0L || logicalOffer < count || !Double.isFinite(energy) || energy < 0D ||
                    accounted && !transferred || transferred && !escrow.isEmpty()) {
                throw new IllegalArgumentException("Inconsistent reusable CPU submission");
            }
            expectedOutputs = List.copyOf(expectedOutputs);
            escrow = List.copyOf(escrow);
        }

        Submission afterTransfer() {
            return new Submission(work, count, logicalOffer, energy, expectedOutputs, List.of(), true, accounted);
        }

        Submission afterAccounting() {
            return new Submission(work, count, logicalOffer, energy, expectedOutputs, escrow, transferred, true);
        }
    }

    /** One durable ownership relationship. Fields change only through the enclosing ledger. */
    public static final class Session {

        private final UUID id;
        private final UUID jobId;
        private final Target target;
        private final AEItemKey pattern;
        private final TrinityPatternIdentity publication;
        private final List<TrinityBoundPatternInput> bindings;
        private final Long2ObjectLinkedOpenHashMap<Submission> submissions = new Long2ObjectLinkedOpenHashMap<>();
        private long nextSequence;
        private boolean closing;
        private @Nullable String settlementFingerprint;

        private Session(UUID id, UUID jobId, Target target, AEItemKey pattern, TrinityPatternIdentity publication,
                        List<TrinityBoundPatternInput> bindings) {
            this.id = id;
            this.jobId = jobId;
            this.target = target;
            this.pattern = pattern;
            this.publication = publication;
            this.bindings = List.copyOf(bindings);
        }

        public UUID id() {
            return id;
        }

        public UUID jobId() {
            return jobId;
        }

        public Target target() {
            return target;
        }

        public AEItemKey pattern() {
            return pattern;
        }

        public TrinityPatternIdentity publication() {
            return publication;
        }

        public List<TrinityBoundPatternInput> bindings() {
            return bindings;
        }

        public long nextSequence() {
            return nextSequence;
        }

        public boolean closing() {
            return closing;
        }

        public boolean settled() {
            return settlementFingerprint != null;
        }

        public @Nullable String settlementFingerprint() {
            return settlementFingerprint;
        }

        public List<SubmissionEntry> submissions() {
            return submissions.long2ObjectEntrySet().stream()
                    .map(entry -> new SubmissionEntry(entry.getLongKey(), entry.getValue())).toList();
        }

        public @Nullable Submission submission(long sequence) {
            return submissions.getOrDefault(sequence, null);
        }
    }

    public record SubmissionEntry(long sequence, Submission submission) {}

    /** Complete immutable metadata for one session, including any genuine local escrow. */
    public record SessionSnapshot(UUID id, UUID jobId, Target target, AEItemKey pattern,
                                  TrinityPatternIdentity publication, List<TrinityBoundPatternInput> bindings,
                                  List<SubmissionEntry> submissions, long nextSequence,
                                  boolean closing, @Nullable String settlementFingerprint) {

        public SessionSnapshot {
            bindings = List.copyOf(bindings);
            submissions = List.copyOf(submissions);
            if (nextSequence < 0L) {
                throw new IllegalArgumentException("Negative reusable submission sequence");
            }
        }
    }

    public record Snapshot(UUID owner, List<SessionSnapshot> sessions) {

        public Snapshot {
            sessions = List.copyOf(sessions);
        }
    }

    private final UUID owner;
    private final Object2ObjectLinkedOpenHashMap<UUID, Session> sessions = new Object2ObjectLinkedOpenHashMap<>();

    public ReusableCpuSessionLedger(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerIdentity() {
        return owner.toString();
    }

    public List<Session> sessions() {
        return List.copyOf(sessions.values());
    }

    public @Nullable Session session(UUID id) {
        return sessions.getOrDefault(id, null);
    }

    public boolean hasUnsettled() {
        return sessions.values().stream().anyMatch(session -> !session.settled());
    }

    public boolean hasUnsettled(UUID jobId) {
        return sessions.values().stream().anyMatch(session -> session.jobId.equals(jobId) && !session.settled());
    }

    /** Records intent before a submission can transfer assets. It does not manufacture a local tool balance. */
    public Session open(UUID id, UUID jobId, Target target, AEItemKey pattern, TrinityPatternIdentity publication,
                        List<TrinityBoundPatternInput> bindings) {
        Session session = new Session(id, jobId, target, pattern, publication, bindings);
        if (sessions.putIfAbsent(id, session) != null) {
            throw new IllegalArgumentException("Reusable CPU session already exists");
        }
        return session;
    }

    /** Called only with actual assets already extracted from the CPU into a single local transfer escrow. */
    public long prepare(UUID sessionId, Submission submission) {
        Session session = requireSession(sessionId);
        if (session.closing || submission.transferred() || submission.accounted()) {
            throw new IllegalStateException("Cannot prepare a transferred or closing reusable submission");
        }
        long sequence = session.nextSequence;
        session.nextSequence = Math.incrementExact(sequence);
        session.submissions.put(sequence, submission);
        return sequence;
    }

    /** Releases only local escrow after a proven rejection before provider ownership. */
    public List<SlotStack> reject(UUID sessionId, long sequence) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        if (submission.transferred()) {
            throw new IllegalStateException("Remote reusable assets cannot be refunded by the CPU");
        }
        session.submissions.remove(sequence);
        return submission.escrow();
    }

    /** Irreversible ownership marker; only metadata remains on this CPU after the provider accepts. */
    public void transferred(UUID sessionId, long sequence) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        session.submissions.put(sequence, submission.afterTransfer());
    }

    /** Marks accounting before the caller applies its prevalidated, non-throwing local plan mutation. */
    public boolean account(UUID sessionId, long sequence, Consumer<Submission> apply) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        if (!submission.transferred()) {
            throw new IllegalStateException("Cannot account for a submission still owned by the CPU");
        }
        if (submission.accounted()) {
            return false;
        }
        session.submissions.put(sequence, submission.afterAccounting());
        apply.accept(submission);
        return true;
    }

    public void close(UUID sessionId) {
        requireSession(sessionId).closing = true;
    }

    public void closeJob(UUID jobId) {
        sessions.values().stream().filter(session -> session.jobId.equals(jobId)).forEach(session -> session.closing = true);
    }

    /**
     * Validates the receipt before any callback. The recipient must atomically deposit the actual assets and apply
     * cancellation accounting in its owning CPU state. Replays acknowledge without invoking the callback again.
     * Unknown ownership is not a reason to manufacture items from a receipt.
     */
    public boolean settle(Settlement settlement, String fingerprint, Consumer<Settlement> receive) {
        Session session = session(settlement.sessionId());
        if (session == null || !ownerIdentity().equals(settlement.cpuOwner()) ||
                !session.jobId.equals(settlement.jobId()) || !session.target.persistentIdentity().equals(settlement.targetIdentity())) {
            return false;
        }
        if (session.settlementFingerprint != null) {
            if (!session.settlementFingerprint.equals(fingerprint)) {
                throw new IllegalStateException("Reusable settlement changed after acknowledgment");
            }
            return true;
        }
        if (fingerprint.isEmpty()) {
            throw new IllegalArgumentException("Reusable settlement requires a stable fingerprint");
        }
        Long2ObjectLinkedOpenHashMap<Submission> unsettled = new Long2ObjectLinkedOpenHashMap<>(session.submissions);
        for (var receipt : settlement.receipts()) {
            Submission submission = unsettled.getOrDefault(receipt.sequence(), null);
            if (submission == null || !submission.transferred() || receipt.accepted() != submission.count() ||
                    receipt.completed() + receipt.cancelled() != receipt.accepted()) {
                throw new IllegalStateException("Reusable final receipt does not match accepted CPU work");
            }
            unsettled.remove(receipt.sequence());
        }
        if (!unsettled.isEmpty()) {
            throw new IllegalStateException("Reusable settlement omitted CPU work");
        }
        receive.accept(settlement);
        session.closing = true;
        session.settlementFingerprint = fingerprint;
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(owner, sessions.values().stream().map(session -> new SessionSnapshot(session.id, session.jobId,
                session.target, session.pattern, session.publication, session.bindings, session.submissions(),
                session.nextSequence, session.closing, session.settlementFingerprint)).toList());
    }

    public static ReusableCpuSessionLedger restore(Snapshot snapshot) {
        ReusableCpuSessionLedger result = new ReusableCpuSessionLedger(snapshot.owner());
        for (SessionSnapshot saved : snapshot.sessions()) {
            Session session = result.open(saved.id(), saved.jobId(), saved.target(), saved.pattern(), saved.publication(), saved.bindings());
            long previous = -1L;
            for (SubmissionEntry entry : saved.submissions()) {
                if (entry.sequence() <= previous || entry.sequence() >= saved.nextSequence()) {
                    throw new IllegalArgumentException("Invalid persisted reusable CPU sequence");
                }
                previous = entry.sequence();
                session.submissions.put(entry.sequence(), entry.submission());
            }
            session.nextSequence = saved.nextSequence();
            session.closing = saved.closing();
            session.settlementFingerprint = saved.settlementFingerprint();
            if (session.settled() && (!session.closing || session.submissions.values().stream().anyMatch(submission -> !submission.escrow().isEmpty()))) {
                throw new IllegalArgumentException("Settled reusable CPU session still owns local escrow");
            }
        }
        return result;
    }

    private Session requireSession(UUID id) {
        Session session = session(id);
        if (session == null) {
            throw new IllegalArgumentException("Unknown reusable CPU session");
        }
        return session;
    }

    private static Submission requireSubmission(Session session, long sequence) {
        Submission submission = session.submission(sequence);
        if (submission == null) {
            throw new IllegalArgumentException("Unknown reusable CPU submission");
        }
        return submission;
    }
}
