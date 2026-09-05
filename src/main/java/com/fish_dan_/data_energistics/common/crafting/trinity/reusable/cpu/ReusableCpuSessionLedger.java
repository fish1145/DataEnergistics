package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * CPU-owned custody metadata, independent of the current job's lifetime. Only untransferred escrow is a local
 * physical asset; transferred tools and settlement fingerprints must never be reconstituted as inventory.
 * All mutation is confined to the logical server thread and saved together with the CPU inventory.
 */
public final class ReusableCpuSessionLedger {

    /** Frozen source-qualified registration in per-operation units, not an executable adapter callback. */
    public record DynamicOutput(GenericStack stack, boolean finalOutput, ResourceLocation source) {

        public DynamicOutput {
            if (!(stack.what() instanceof AEItemKey) || stack.amount() <= 0L) {
                throw new IllegalArgumentException("Dynamic output custody requires positive item units");
            }
        }
    }

    /** Output expectations of one exact-state operation. Resident tool successors are deliberately absent. */
    public record OutputContract(List<GenericStack> products, List<GenericStack> remainders,
                                 List<DynamicOutput> dynamic, List<VirtualCraftingCompletion> virtual) {

        public OutputContract {
            products = List.copyOf(products);
            remainders = List.copyOf(remainders);
            dynamic = List.copyOf(dynamic);
            virtual = List.copyOf(virtual);
        }
    }

    /** One exact submission and its one-time accounting boundary. Expected outputs are metadata, not owned items. */
    public record Submission(Work work, long count, long logicalOffer, double energy,
                             OutputContract outputs, List<SlotStack> physicalInputs,
                             boolean transferred, boolean waitingRegistered, boolean accounted, long completed) {

        public Submission {
            if (count <= 0L || logicalOffer < count || !Double.isFinite(energy) || energy < 0D ||
                    accounted && (!transferred || !waitingRegistered) || completed < 0 || completed > count ||
                    !transferred && completed != 0) {
                throw new IllegalArgumentException("Inconsistent reusable CPU submission");
            }
            physicalInputs = List.copyOf(physicalInputs);
        }

        /** Delivery history is metadata after transfer; only this view can be returned as CPU-owned escrow. */
        public List<SlotStack> escrow() {
            return transferred ? List.of() : physicalInputs;
        }

        Submission afterTransfer() {
            return new Submission(work, count, logicalOffer, energy, outputs, physicalInputs, true, waitingRegistered, accounted, completed);
        }

        Submission afterAccounting() {
            return new Submission(work, count, logicalOffer, energy, outputs, physicalInputs, transferred, waitingRegistered, true, completed);
        }

        Submission afterCompletion(long amount) {
            return new Submission(work, count, logicalOffer, energy, outputs, physicalInputs, transferred, waitingRegistered, accounted, amount);
        }

        Submission afterWaitingRegistration() {
            return new Submission(work, count, logicalOffer, energy, outputs, physicalInputs, transferred, true, accounted, completed);
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
        private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
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

        public List<SubmissionEntry> pendingSubmissions() {
            return pending.longStream().mapToObj(sequence -> new SubmissionEntry(sequence, submissions.get(sequence))).toList();
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

    public record Snapshot(UUID owner, List<SessionSnapshot> sessions, Set<UUID> replanningJobs, Set<UUID> uncertainSessions) {

        public Snapshot {
            sessions = List.copyOf(sessions);
            replanningJobs = Set.copyOf(replanningJobs);
            uncertainSessions = Set.copyOf(uncertainSessions);
        }
    }

    private final UUID owner;
    private final Object2ObjectLinkedOpenHashMap<UUID, Session> sessions = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<UUID> replanningJobs = new ObjectOpenHashSet<>();
    private final Set<UUID> uncertainSessions = new ObjectOpenHashSet<>();

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
        return !uncertainSessions.isEmpty() || sessions.values().stream().anyMatch(session -> !session.settled());
    }

    public boolean hasUnsettled(UUID jobId) {
        return sessions.values().stream().anyMatch(session -> session.jobId.equals(jobId) && !session.settled());
    }

    public boolean requiresReplan(UUID jobId) {
        return replanningJobs.contains(jobId);
    }

    public boolean requireReplan(UUID jobId) {
        return replanningJobs.add(jobId);
    }

    public void finishReplan(UUID jobId) {
        replanningJobs.remove(jobId);
    }

    public boolean hasUncertainOwnership() {
        return !uncertainSessions.isEmpty();
    }

    public void markUncertain(UUID sessionId) {
        requireSession(sessionId);
        uncertainSessions.add(sessionId);
    }

    public void confirmOwnership(UUID sessionId) {
        uncertainSessions.remove(sessionId);
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
        if (session.closing || submission.transferred()) {
            throw new IllegalStateException("Cannot prepare a transferred or closing reusable submission");
        }
        long sequence = session.nextSequence;
        session.nextSequence = Math.incrementExact(sequence);
        session.submissions.put(sequence, submission);
        session.pending.add(sequence);
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
        session.pending.remove(sequence);
        return submission.escrow();
    }

    /** Irreversible ownership marker; only metadata remains on this CPU after the provider accepts. */
    public void transferred(UUID sessionId, long sequence) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        session.submissions.put(sequence, submission.afterTransfer());
    }

    /** Advances observed native completion once, before publishing deferred virtual-completion tokens. */
    public void observeCompleted(UUID sessionId, long sequence, long completed, LongConsumer newlyCompleted) {
        Session session = requireSession(sessionId);
        Submission previous = requireSubmission(session, sequence);
        if (!previous.transferred() || completed < previous.completed() || completed > previous.count()) {
            throw new IllegalStateException("Native completion contradicts CPU-owned submission history");
        }
        if (completed != previous.completed()) {
            session.submissions.put(sequence, previous.afterCompletion(completed));
            if (completed == previous.count()) {
                session.pending.remove(sequence);
            }
            newlyCompleted.accept(completed - previous.completed());
        }
    }

    /** A proven rejected opening has no provider receipt or tool ownership to retain. */
    public void discardUnopened(UUID sessionId) {
        Session session = requireSession(sessionId);
        if (!session.submissions.isEmpty() || uncertainSessions.contains(sessionId)) {
            throw new IllegalStateException("Cannot discard a session with custody history");
        }
        sessions.remove(sessionId);
    }

    /** Marks accounting before the caller applies its prevalidated, non-throwing local plan mutation. */
    public boolean account(UUID sessionId, long sequence, Consumer<Submission> apply) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        if (!submission.transferred() || !submission.waitingRegistered()) {
            throw new IllegalStateException("Cannot account for a submission still owned by the CPU");
        }
        if (submission.accounted()) {
            return false;
        }
        session.submissions.put(sequence, submission.afterAccounting());
        apply.accept(submission);
        return true;
    }

    /** Installs provisional waiting counters before a provider can emit outputs synchronously during commit. */
    public void registerWaiting(UUID sessionId, long sequence, Consumer<Submission> register) {
        Session session = requireSession(sessionId);
        Submission submission = requireSubmission(session, sequence);
        if (submission.waitingRegistered()) {
            return;
        }
        register.accept(submission);
        session.submissions.put(sequence, submission.afterWaitingRegistration());
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
                    receipt.completed() < submission.completed() ||
                    receipt.completed() + receipt.cancelled() != receipt.accepted()) {
                throw new IllegalStateException("Reusable final receipt does not match accepted CPU work");
            }
            unsettled.remove(receipt.sequence());
        }
        if (!unsettled.isEmpty()) {
            throw new IllegalStateException("Reusable settlement omitted CPU work");
        }
        receive.accept(settlement);
        if (settlement.receipts().stream().anyMatch(receipt -> receipt.cancelled() > 0L)) {
            replanningJobs.add(session.jobId);
        }
        session.closing = true;
        session.settlementFingerprint = fingerprint;
        session.pending.clear();
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(owner, sessions.values().stream().map(session -> new SessionSnapshot(session.id, session.jobId,
                session.target, session.pattern, session.publication, session.bindings, session.submissions(),
                session.nextSequence, session.closing, session.settlementFingerprint)).toList(), replanningJobs, uncertainSessions);
    }

    public static ReusableCpuSessionLedger restore(Snapshot snapshot) {
        ReusableCpuSessionLedger result = new ReusableCpuSessionLedger(snapshot.owner());
        result.replanningJobs.addAll(snapshot.replanningJobs());
        for (UUID sessionId : snapshot.uncertainSessions()) {
            // Validated after the session index has been populated below.
            result.uncertainSessions.add(sessionId);
        }
        for (SessionSnapshot saved : snapshot.sessions()) {
            Session session = result.open(saved.id(), saved.jobId(), saved.target(), saved.pattern(), saved.publication(), saved.bindings());
            long previous = -1L;
            for (SubmissionEntry entry : saved.submissions()) {
                if (entry.sequence() <= previous || entry.sequence() >= saved.nextSequence()) {
                    throw new IllegalArgumentException("Invalid persisted reusable CPU sequence");
                }
                previous = entry.sequence();
                session.submissions.put(entry.sequence(), entry.submission());
                if (!saved.closing() || saved.settlementFingerprint() == null) {
                    if (entry.submission().completed() < entry.submission().count()) {
                        session.pending.add(entry.sequence());
                    }
                }
            }
            session.nextSequence = saved.nextSequence();
            session.closing = saved.closing();
            session.settlementFingerprint = saved.settlementFingerprint();
            if (session.settled() && (!session.closing || session.submissions.values().stream().anyMatch(submission -> !submission.escrow().isEmpty()))) {
                throw new IllegalArgumentException("Settled reusable CPU session still owns local escrow");
            }
        }
        if (!result.sessions.keySet().containsAll(result.uncertainSessions)) {
            throw new IllegalArgumentException("Unknown session in reusable ownership quarantine");
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
