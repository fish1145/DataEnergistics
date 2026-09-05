package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter.ReturnReceiver;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Append;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolDelivery;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable bridge for one native crafting location. All calls are confined to its logical server thread.
 * Only immutable binding values and actual session assets are retained; live host callbacks are supplied
 * per call. The owning core/provider must include this object in the state transferred with its item.
 */
public final class PersistentReusableCraftingEndpoint {

    /** Exact native binding retained across appends and reloads; source/world/recipe instances are excluded. */
    public record Binding(Identity identity, TrinityPatternIdentity publicationIdentity, int inputSlots, List<SlotInput> consumed,
                          List<SlotContract> tools, Optional<String> recipeId) {

        public Binding {
            consumed = List.copyOf(consumed);
            tools = List.copyOf(tools);
            if (inputSlots <= 0 || tools.isEmpty()) {
                throw new IllegalArgumentException("A native reusable binding needs original input slots and tools");
            }
            IntOpenHashSet present = new IntOpenHashSet();
            for (SlotInput input : consumed) {
                if (input.slot() >= inputSlots || !(input.stack().what() instanceof AEItemKey) || !present.add(input.slot())) {
                    throw new IllegalArgumentException("Native materials must have one exact item key per original slot");
                }
            }
            IntOpenHashSet toolSlots = new IntOpenHashSet();
            for (SlotContract tool : tools) {
                if (tool.slot() >= inputSlots || tool.ownership() != Ownership.CPU_SUPPLIED || !toolSlots.add(tool.slot())) {
                    throw new IllegalArgumentException("This native endpoint has no machine-owned tool inventory");
                }
                present.add(tool.slot());
            }
            if (present.size() != inputSlots) {
                throw new IllegalArgumentException("Native binding omits an original input slot");
            }
        }
    }

    /** Actual native outcome. Paused means no native effect occurred; failures after effects use executed=true. */
    public record NativeResult(boolean executed, List<ToolOutcome> tools, List<GenericStack> outputs,
                               Optional<String> failure) {

        public NativeResult {
            tools = List.copyOf(tools);
            outputs = checkedAssets(outputs);
            if (!executed && (!tools.isEmpty() || !outputs.isEmpty() || failure.isPresent())) {
                throw new IllegalArgumentException("An unexecuted pause cannot report native effects");
            }
            if (failure.isPresent() && failure.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("Native failure needs diagnostic context");
            }
        }

        public static NativeResult paused() {
            return new NativeResult(false, List.of(), List.of(), Optional.empty());
        }
    }

    /**
     * Host boundary for native recipe access and the owning persistent output queue. Every callback runs
     * on the server thread. No callback is serialized. Availability checks must not change ownership.
     * Native exceptions with unknown effects must propagate; they are quarantined, never treated as a pause.
     */
    public interface Host {

        /**
         * Validate current target, installed pattern, mode, recipe and exact binding without mutation.
         * The current publication's full semantic identity must match binding.publicationIdentity;
         * an unchanged encoded pattern or recipe ID alone cannot authorize a changed recipe after reload.
         */
        boolean isAvailable(Binding binding);

        /** Execute exactly one operation using its actual escrow and return all native tool outcomes. */
        NativeResult execute(Binding binding, Operation operation);

        /**
         * Atomically append this complete list to the host's ordinary persistent pending queue. Throwing
         * must leave that queue unchanged. Do not flush to an external inventory or persist midway through
         * this callback: the endpoint clears its matching queue before the next persistChanges call.
         */
        void acceptOutputs(Identity identity, List<GenericStack> outputs);

        /** Mark the owning persistent state dirty; this is not a promise of synchronous disk durability. */
        void persistChanges();
    }

    /** Complete endpoint entry for its codec; revision is separate from the session's accounting counters. */
    record EntrySnapshot(Binding binding, ReusableInputSession session, long revision, long notBefore,
                         boolean settlementAcknowledged, String failure) {}

    private final String targetIdentity;
    private final Object2ObjectLinkedOpenHashMap<UUID, Entry> sessions = new Object2ObjectLinkedOpenHashMap<>();
    private @Nullable UUID resident;
    private long generation;

    public PersistentReusableCraftingEndpoint(String targetIdentity) {
        if (targetIdentity.isBlank()) {
            throw new IllegalArgumentException("A reusable endpoint needs a stable native identity");
        }
        this.targetIdentity = targetIdentity;
    }

    public String targetIdentity() {
        return targetIdentity;
    }

    public boolean hasResidentSession() {
        return resident != null;
    }

    /** Current physical resident, without copying its held assets or historic receipts. */
    public Optional<UUID> residentSessionId() {
        return Optional.ofNullable(resident);
    }

    /** Prepares an all-or-nothing transfer of total quantities without retaining the live request references. */
    public @Nullable ReusableCraftingAdmission prepare(ReusableCraftingRequest request, long currentTick, Host host) {
        if (!targetIdentity.equals(request.target().persistentIdentity()) || currentTick < 0) {
            return null;
        }
        Binding binding;
        Append append;
        try {
            binding = binding(request);
            append = append(request, binding);
        } catch (IllegalArgumentException | ArithmeticException unsupported) {
            return null;
        }
        Entry existing = sessions.get(request.sessionId());
        if (existing != null) {
            if (!compatibleBinding(existing.binding, binding)) {
                return null;
            }
            if (existing.session.appendSnapshot(request.sequence()).isPresent()) {
                existing.session.validateAppend(append); // Different payload under the same sequence is a protocol
                                                         // error.
                return new Admission(existing, append, List.of(), generation, existing.revision, currentTick, host, true);
            }
            if (!request.sessionId().equals(resident) || !existing.failure.isEmpty()) {
                return null;
            }
        } else if (resident != null) {
            return null;
        }
        if (!host.isAvailable(binding)) {
            return null;
        }
        Entry candidate = existing != null ? existing :
                new Entry(binding, new ReusableInputSession(binding.identity(), binding.tools()), 0, currentTick, false, "");
        try {
            candidate.session.validateAppend(append);
        } catch (IllegalArgumentException | IllegalStateException unavailable) {
            return null;
        }
        return new Admission(candidate, append, physicalInputs(binding, append), generation, candidate.revision, currentTick, host, false);
    }

    public Optional<ReusableCraftingSessionView> query(UUID sessionId) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            return Optional.empty();
        }
        Identity identity = entry.binding.identity();
        List<SlotStack> held = new ObjectArrayList<>();
        entry.session.heldTools().forEach((slot, stacks) -> stacks.forEach(stack -> held.add(new SlotStack(slot, stack))));
        return Optional.of(new ReusableCraftingSessionView(identity.sessionId(), identity.jobId(), identity.cpuOwner(),
                identity.target(), visibleState(entry), entry.revision, entry.session.accepted(), entry.session.completed(),
                entry.session.cancelled(), held, failure(entry)));
    }

    public Optional<AppendReceipt> receipt(UUID sessionId, long sequence) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.session.appendSnapshot(sequence)
                .map(append -> new AppendReceipt(sequence, append.request().operations(), append.completed(), append.cancelled()));
    }

    /** Original immutable physical delivery used to replay a partially accepted sequence after reload. */
    public Optional<Append> acceptedAppend(UUID sessionId, long sequence) {
        Entry entry = sessions.get(sessionId);
        return entry == null ? Optional.empty() : entry.session.appendSnapshot(sequence).map(ReusableInputSession.AppendSnapshot::request);
    }

    /** Exact-state reservations in operation units; the session must have already been discovered. */
    public long reservedToolUses(UUID sessionId, int slot, AEItemKey state) {
        return requireEntry(sessionId).session.reservedToolUses(slot, state);
    }

    /** Uses at most operationBudget native operations; callers share this budget with their legacy batch queue. */
    public int tick(long currentTick, int operationBudget, boolean competitorWaiting, Host host) {
        if (currentTick < 0 || operationBudget < 0) {
            throw new IllegalArgumentException("Invalid reusable execution tick or budget");
        }
        if (resident == null) {
            return 0;
        }
        Entry entry = sessions.get(resident);
        if (entry.session.tick(currentTick, competitorWaiting)) {
            changed(entry, host);
        }
        if (!entry.failure.isEmpty() || visibleState(entry) != State.OPEN || currentTick < entry.notBefore) {
            return 0;
        }
        int executed = 0;
        while (executed < operationBudget && host.isAvailable(entry.binding)) {
            Optional<Operation> next = entry.session.beginOperation();
            if (next.isEmpty()) {
                break;
            }
            Operation operation = next.orElseThrow();
            try {
                changed(entry, host); // Active escrow is now part of the owning state if it is saved.
                NativeResult result = host.execute(entry.binding, operation);
                if (!result.executed()) {
                    entry.session.abortOperation(operation.id());
                    changed(entry, host);
                    break;
                }
                complete(entry, operation, result);
                executed++;
                publishOutputs(entry, host);
                changed(entry, host);
                if (visibleState(entry) != State.OPEN) {
                    break;
                }
            } catch (RuntimeException exception) {
                // Actual native effects are unknown. Closing preserves unresolved execution escrow and
                // the endpoint exposes FAULTED until an explicit actual-result reconciliation is supplied.
                entry.failure = "Native operation " + operation.id() + " failed: " + exception.getMessage();
                entry.session.close();
                Data_Energistics.LOGGER.error("Reusable endpoint {} session {} native operation {} was quarantined",
                        targetIdentity, entry.binding.identity().sessionId(), operation.id(), exception);
                changed(entry, host);
                break;
            }
        }
        return executed;
    }

    /** Explicit recovery only: the host must have verified the actual outcome, including any claim of non-execution. */
    public void reconcile(UUID sessionId, NativeResult actual, Host host) {
        Entry entry = requireEntry(sessionId);
        Operation operation = entry.session.activeOperation();
        if (operation == null) {
            throw new IllegalStateException("Session has no unresolved native operation");
        }
        if (actual.executed()) {
            complete(entry, operation, actual);
        } else {
            entry.session.abortOperation(operation.id());
        }
        entry.session.close();
        publishOutputs(entry, host);
        changed(entry, host);
    }

    public void close(UUID sessionId, Host host) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            return;
        }
        State before = visibleState(entry);
        entry.session.close();
        if (before != visibleState(entry)) {
            changed(entry, host);
        }
    }

    /**
     * Sends one authoritative final receipt even when every tool legally exhausted and no physical asset
     * remains. The receiver's durable deduplication closes the crash window between receive and local ack.
     */
    public boolean settle(UUID sessionId, ReturnReceiver receiver, Host host) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            return false;
        }
        if (entry.settlementAcknowledged) {
            return true;
        }
        if (entry.session.status() != ReusableInputSession.State.RETURN_PENDING && entry.session.status() != ReusableInputSession.State.CLOSED) {
            return false;
        }
        if (publishOutputs(entry, host)) {
            changed(entry, host);
        }
        Identity identity = entry.binding.identity();
        var outbox = entry.session.returnOutbox();
        if (outbox.size() > 1) {
            throw new IllegalStateException("Native endpoint expects a single final session settlement");
        }
        long sequence = outbox.isEmpty() ? 0 : outbox.getFirst().sequence();
        List<GenericStack> assets = outbox.isEmpty() ? List.of() : outbox.getFirst().assets();
        List<AppendReceipt> receipts = entry.session.snapshot().appends().stream().map(append -> new AppendReceipt(
                append.request().sequence(), append.request().operations(), append.completed(), append.cancelled())).toList();
        Settlement settlement = new Settlement(identity.sessionId(), identity.jobId(), identity.cpuOwner(), identity.target(),
                sequence, assets, List.of(), entry.session.exhaustedTools(), receipts, failure(entry));
        if (!receiver.receive(settlement)) {
            return false;
        }
        if (!outbox.isEmpty()) {
            entry.session.acknowledgeReturn(sequence, assets);
        }
        entry.settlementAcknowledged = true;
        if (sessionId.equals(resident)) {
            resident = null;
        }
        changed(entry, host);
        return true;
    }

    List<EntrySnapshot> snapshot() {
        return sessions.values().stream().map(entry -> new EntrySnapshot(entry.binding, entry.session, entry.revision,
                entry.notBefore, entry.settlementAcknowledged, entry.failure)).toList();
    }

    static PersistentReusableCraftingEndpoint restore(String targetIdentity, List<EntrySnapshot> snapshots) {
        PersistentReusableCraftingEndpoint result = new PersistentReusableCraftingEndpoint(targetIdentity);
        for (EntrySnapshot snapshot : snapshots) {
            Identity identity = snapshot.binding().identity();
            ReusableInputSession session = snapshot.session();
            if (!identity.target().equals(targetIdentity) || !identity.equals(session.identity()) ||
                    !snapshot.binding().tools().equals(session.slotContracts()) || snapshot.revision() < 0 || snapshot.notBefore() < 0 ||
                    result.sessions.containsKey(identity.sessionId())) {
                throw new IllegalArgumentException("Invalid persisted native reusable endpoint binding");
            }
            for (var append : session.snapshot().appends()) {
                if (!snapshot.binding().consumed().equals(append.request().consumedPerOperation())) {
                    throw new IllegalArgumentException("Persisted session changed its fixed non-tool input binding");
                }
            }
            if (snapshot.settlementAcknowledged() && session.status() != ReusableInputSession.State.CLOSED) {
                throw new IllegalArgumentException("Unsettled session cannot have an acknowledged endpoint receipt");
            }
            Entry entry = new Entry(snapshot.binding(), session, snapshot.revision(), snapshot.notBefore(),
                    snapshot.settlementAcknowledged(), snapshot.failure());
            result.sessions.put(identity.sessionId(), entry);
            if (!entry.settlementAcknowledged) {
                if (result.resident != null) {
                    throw new IllegalArgumentException("Multiple persisted sessions claim one native crafting endpoint");
                }
                result.resident = identity.sessionId();
            }
        }
        return result;
    }

    private static Binding binding(ReusableCraftingRequest request) {
        Identity identity = new Identity(request.sessionId(), request.jobId(), request.cpuOwner(), request.target().persistentIdentity(),
                request.pattern().getDefinition(), request.target().mode().map(Object::toString));
        List<SlotInput> materials = new ObjectArrayList<>();
        List<SlotContract> tools = new ObjectArrayList<>();
        for (var input : request.inputs()) {
            Object2LongLinkedOpenHashMap<AEKey> quantities = counts(input.consumedPerOperation());
            if (quantities.size() > 1) {
                throw new IllegalArgumentException("Native crafting needs one exact material key per original input slot");
            }
            quantities.forEach((key, amount) -> materials.add(new SlotInput(input.slot(), new GenericStack(key, amount))));
            input.tool().ifPresent(tool -> tools.add(new SlotContract(input.slot(), tool.heldAmount(), tool.ownership(), tool.rule())));
        }
        TrinityPatternIdentity publicationIdentity = TrinityPatternIdentity.capture(
                TrinityPatternPublicationSignature.capture(request.pattern()), request.level().registryAccess());
        return new Binding(identity, publicationIdentity, request.inputs().size(), materials, tools, request.recipeId().map(Object::toString));
    }

    private static boolean compatibleBinding(Binding frozen, Binding proposed) {
        if (!frozen.identity().equals(proposed.identity()) || !frozen.publicationIdentity().equals(proposed.publicationIdentity()) ||
                frozen.inputSlots() != proposed.inputSlots() || !frozen.recipeId().equals(proposed.recipeId()) ||
                !frozen.consumed().equals(proposed.consumed()) || frozen.tools().size() != proposed.tools().size()) {
            return false;
        }
        for (int index = 0; index < frozen.tools().size(); index++) {
            SlotContract previous = frozen.tools().get(index);
            SlotContract next = proposed.tools().get(index);
            if (previous.slot() != next.slot() || previous.heldAmount() != next.heldAmount() || previous.ownership() != next.ownership() ||
                    !sameRuleContract(previous.rule(), next.rule())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRuleContract(ReusableInputRule frozen, ReusableInputRule proposed) {
        if (!frozen.id().equals(proposed.id()) || frozen.revision() != proposed.revision() || frozen.kind() != proposed.kind() ||
                frozen.damagePerUse() != proposed.damagePerUse() || frozen.breakAtDamage() != proposed.breakAtDamage() ||
                !frozen.exhaustionByproducts().equals(proposed.exhaustionByproducts()) || !frozen.transitions().equals(proposed.transitions())) {
            return false;
        }
        try {
            // A later planning stage may start at the actual successor. Keep the original session rule:
            // checking this state must not replace its transition table, reset damage or add physical tools.
            frozen.guaranteedUses(proposed.initialKey());
            return true;
        } catch (IllegalArgumentException unknownState) {
            return false;
        }
    }

    private static Append append(ReusableCraftingRequest request, Binding binding) {
        List<GenericStack> materials = new ObjectArrayList<>();
        for (SlotInput input : binding.consumed()) {
            materials.add(new GenericStack(input.stack().what(), Math.multiplyExact(input.stack().amount(), request.requestedCount())));
        }
        List<ToolDelivery> tools = request.offeredTools().stream().map(tool -> new ToolDelivery(tool.slot(), tool.stack())).toList();
        Int2ObjectMap<AEItemKey> states = new Int2ObjectLinkedOpenHashMap<>();
        for (Input input : request.inputs()) {
            input.tool().flatMap(Tool::operationState).ifPresent(state -> states.put(input.slot(), state));
        }
        return new Append(request.sequence(), request.requestedCount(), binding.consumed(), materials, tools, states);
    }

    private static List<SlotStack> physicalInputs(Binding binding, Append append) {
        List<SlotStack> result = new ObjectArrayList<>();
        for (SlotInput input : binding.consumed()) {
            result.add(new SlotStack(input.slot(), new GenericStack(input.stack().what(),
                    Math.multiplyExact(input.stack().amount(), append.operations()))));
        }
        append.deliveredTools().forEach(tool -> result.add(new SlotStack(tool.slot(), tool.stack())));
        return List.copyOf(result);
    }

    private static void complete(Entry entry, Operation operation, NativeResult result) {
        if (result.failure().isPresent()) {
            entry.session.faultOperation(operation.id(), result.tools(), result.outputs(), result.failure().orElseThrow());
        } else {
            entry.session.completeOperation(operation.id(), result.tools(), result.outputs());
        }
    }

    private static boolean publishOutputs(Entry entry, Host host) {
        List<GenericStack> outputs = entry.session.pendingOutputs();
        if (outputs.isEmpty()) {
            return false;
        }
        host.acceptOutputs(entry.binding.identity(), outputs);
        entry.session.drainOutputs();
        return true;
    }

    private void changed(Entry entry, Host host) {
        entry.revision = Math.incrementExact(entry.revision);
        generation = Math.incrementExact(generation);
        host.persistChanges();
    }

    private static State visibleState(Entry entry) {
        if (!entry.failure.isEmpty() && entry.session.activeOperation() != null) {
            return State.FAULTED;
        }
        State result = State.valueOf(entry.session.status().name());
        return result == State.CLOSED && !entry.settlementAcknowledged ? State.RETURN_PENDING : result;
    }

    private static Optional<String> failure(Entry entry) {
        String message = entry.failure.isEmpty() ? entry.session.fault() : entry.failure;
        return message.isEmpty() ? Optional.empty() : Optional.of(message);
    }

    private Entry requireEntry(UUID sessionId) {
        Entry entry = sessions.get(sessionId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown native reusable session");
        }
        return entry;
    }

    private static List<GenericStack> checkedAssets(List<GenericStack> assets) {
        List<GenericStack> result = List.copyOf(assets);
        for (GenericStack stack : result) {
            if (stack.amount() <= 0) {
                throw new IllegalArgumentException("Native result asset quantities must be positive");
            }
        }
        return result;
    }

    private static Object2LongLinkedOpenHashMap<AEKey> counts(List<GenericStack> assets) {
        Object2LongLinkedOpenHashMap<AEKey> result = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack stack : assets) {
            result.put(stack.what(), Math.addExact(result.getLong(stack.what()), stack.amount()));
        }
        return result;
    }

    private static final class Entry {

        private final Binding binding;
        private final ReusableInputSession session;
        private long revision;
        private long notBefore;
        private boolean settlementAcknowledged;
        private String failure;

        private Entry(Binding binding, ReusableInputSession session, long revision, long notBefore,
                      boolean settlementAcknowledged, String failure) {
            this.binding = binding;
            this.session = session;
            this.revision = revision;
            this.notBefore = notBefore;
            this.settlementAcknowledged = settlementAcknowledged;
            this.failure = failure;
        }
    }

    private final class Admission implements ReusableCraftingAdmission {

        private final Entry entry;
        private final Append append;
        private final List<SlotStack> physical;
        private final long expectedGeneration;
        private final long expectedRevision;
        private final long queuedTick;
        private final Host host;
        private final boolean replay;
        private boolean used;
        private boolean transferred;

        private Admission(Entry entry, Append append, List<SlotStack> physical, long expectedGeneration,
                          long expectedRevision, long queuedTick, Host host, boolean replay) {
            this.entry = entry;
            this.append = append;
            this.physical = physical;
            this.expectedGeneration = expectedGeneration;
            this.expectedRevision = expectedRevision;
            this.queuedTick = queuedTick;
            this.host = host;
            this.replay = replay;
        }

        @Override
        public long count() {
            return append.operations();
        }

        @Override
        public List<SlotStack> physicalInputs() {
            return physical;
        }

        @Override
        public boolean replay() {
            return replay;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return transferred;
        }

        @Override
        public boolean commit(KeyCounter[] delivery) {
            if (used) {
                return false;
            }
            used = true;
            if (!matchesDelivery(delivery)) {
                return false;
            }
            if (replay) {
                return true;
            }
            if (expectedGeneration != generation || expectedRevision != entry.revision || !host.isAvailable(entry.binding)) {
                return false;
            }
            // Only opening defers execution. Continuous appends must not move the already accepted queue's
            // eligibility forward on every tick and indefinitely starve native execution.
            long eligibleTick = sessions.containsKey(entry.binding.identity().sessionId()) ? entry.notBefore :
                    Math.incrementExact(queuedTick);
            // acceptAppend validates and stages private values without invoking any host callback.
            // Mark ownership before clearing the caller's counters or publishing/persisting the entry.
            if (!entry.session.acceptAppend(append)) {
                return false;
            }
            transferred = true;
            for (KeyCounter counter : delivery) {
                counter.clear();
            }
            entry.notBefore = eligibleTick;
            sessions.put(entry.binding.identity().sessionId(), entry);
            resident = entry.binding.identity().sessionId();
            changed(entry, host);
            return true;
        }

        private boolean matchesDelivery(KeyCounter[] delivery) {
            if (delivery.length != entry.binding.inputSlots()) {
                return false;
            }
            Int2ObjectLinkedOpenHashMap<List<GenericStack>> expected = new Int2ObjectLinkedOpenHashMap<>();
            for (int slot = 0; slot < delivery.length; slot++) {
                expected.put(slot, new ObjectArrayList<>());
            }
            for (SlotStack stack : physical) {
                expected.get(stack.slot()).add(stack.stack());
            }
            for (int slot = 0; slot < delivery.length; slot++) {
                Object2LongLinkedOpenHashMap<AEKey> received = new Object2LongLinkedOpenHashMap<>();
                for (var stack : delivery[slot]) {
                    if (stack.getLongValue() < 0) {
                        return false;
                    }
                    if (stack.getLongValue() > 0) {
                        received.put(stack.getKey(), stack.getLongValue());
                    }
                }
                if (!counts(expected.get(slot)).equals(received)) {
                    return false;
                }
            }
            return true;
        }
    }
}
