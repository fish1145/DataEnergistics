package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-executor, server-thread confined reusable-input escrow. Only actually delivered assets are
 * owned here. A begun operation transfers assets to an execution escrow; they cannot be refunded
 * until the native executor reports the actual result, including after a restart or an exception.
 * Callers must persist the begun operation before performing an external irreversible operation.
 */
public final class ReusableInputSession {

    public enum State {
        OPEN,
        CLOSING,
        FAULTED,
        RETURN_PENDING,
        CLOSED
    }

    /** Stable routing identity; it contains no live CPU, world, player or recipe callback. */
    public record Identity(UUID sessionId, UUID jobId, String cpuOwner, String target,
                           AEItemKey pattern, Optional<String> mode) {

        public Identity {
            if (cpuOwner.isBlank() || target.isBlank()) {
                throw new IllegalArgumentException("A session needs stable CPU and executor identities");
            }
        }
    }

    /** Frozen per-slot contract. heldAmount tools participate in each operation, independently of consumed material. */
    public record SlotContract(int slot, long heldAmount, Ownership ownership, ReusableInputRule rule) {

        public SlotContract {
            if (slot < 0 || heldAmount <= 0) {
                throw new IllegalArgumentException("Invalid reusable slot contract");
            }
        }
    }

    /** Exact consumed portion of one native input slot; may have the same key as its held portion. */
    public record SlotInput(int slot, GenericStack stack) {

        public SlotInput {
            if (slot < 0 || stack.amount() <= 0) {
                throw new IllegalArgumentException("Invalid consumed input slot");
            }
        }
    }

    /** Physical delivered tool units, which can contain different remaining durability in separate entries. */
    public record ToolDelivery(int slot, GenericStack stack) {

        public ToolDelivery {
            if (slot < 0 || stack.amount() <= 0 || !(stack.what() instanceof AEItemKey)) {
                throw new IllegalArgumentException("A reusable delivery must contain positive item units");
            }
        }
    }

    /** Immutable idempotency payload. Materials must equal exact per-operation consumption times operations. */
    public record Append(long sequence, long operations, List<SlotInput> consumedPerOperation,
                         List<GenericStack> deliveredMaterials, List<ToolDelivery> deliveredTools) {

        public Append {
            if (sequence < 0 || operations <= 0) {
                throw new IllegalArgumentException("Invalid append sequence or operation count");
            }
            consumedPerOperation = List.copyOf(consumedPerOperation);
            deliveredMaterials = SessionAssets.checked(deliveredMaterials);
            deliveredTools = List.copyOf(deliveredTools);
            IntOpenHashSet slots = new IntOpenHashSet();
            for (SlotInput input : consumedPerOperation) {
                if (!slots.add(input.slot())) {
                    throw new IllegalArgumentException("Duplicate consumed input slot");
                }
            }
            if (!SessionAssets.counts(deliveredMaterials).equals(SessionAssets.counts(
                    SessionAssets.multiply(materials(consumedPerOperation), operations)))) {
                throw new IllegalArgumentException("Append materials do not equal exact requested consumption");
            }
        }
    }

    /** Native execution input escrow. Tool and consumed portions remain separate even at the same slot/key. */
    public record Operation(long id, long appendSequence, List<SlotInput> consumed, List<ToolDelivery> tools) {

        public Operation {
            if (id < 0 || appendSequence < 0) {
                throw new IllegalArgumentException("Invalid operation identity");
            }
            consumed = List.copyOf(consumed);
            tools = List.copyOf(tools);
        }
    }

    /** Actual retained tool assets and transition outputs from one native slot, including unexpected states. */
    public record ToolOutcome(int slot, List<GenericStack> successors, List<GenericStack> byproducts) {

        public ToolOutcome {
            if (slot < 0) {
                throw new IllegalArgumentException("Invalid tool outcome slot");
            }
            successors = SessionAssets.checked(successors);
            byproducts = SessionAssets.checked(byproducts);
            for (GenericStack successor : successors) {
                if (!(successor.what() instanceof AEItemKey)) {
                    throw new IllegalArgumentException("A retained tool must be an item");
                }
            }
        }
    }

    /** One durable, CPU-directed refund with an exact idempotent acknowledgment payload. */
    public record ReturnBatch(long sequence, List<GenericStack> assets) {

        public ReturnBatch {
            if (sequence < 0) {
                throw new IllegalArgumentException("Invalid return sequence");
            }
            assets = SessionAssets.checked(assets);
            if (assets.isEmpty()) {
                throw new IllegalArgumentException("An empty refund must not create an acknowledgment obligation");
            }
        }
    }

    /** Per-append progress and actual unconsumed material escrow, retained after completion for replay checks. */
    public record AppendSnapshot(Append request, long completed, long cancelled, List<GenericStack> remainingMaterials) {

        public AppendSnapshot {
            remainingMaterials = SessionAssets.checked(remainingMaterials);
            if (completed < 0 || cancelled < 0 || Math.addExact(completed, cancelled) > request.operations()) {
                throw new IllegalArgumentException("Invalid append progress");
            }
        }
    }

    /** Complete serializable state; snapshots contain immutable asset lists and frozen contracts. */
    public record Snapshot(Identity identity, List<SlotContract> contracts, State state,
                           List<AppendSnapshot> appends, List<ToolDelivery> tools, @Nullable Operation active,
                           List<GenericStack> outputs, List<ReturnBatch> returns, List<ReturnBatch> acknowledged,
                           List<ToolDelivery> machineOwnedReleased, long nextOperation, long nextReturn,
                           long idleSince, long competitionSince, long exhaustedTools, String fault) {

        public Snapshot {
            contracts = List.copyOf(contracts);
            appends = List.copyOf(appends);
            tools = List.copyOf(tools);
            outputs = SessionAssets.checked(outputs);
            returns = List.copyOf(returns);
            acknowledged = List.copyOf(acknowledged);
            machineOwnedReleased = List.copyOf(machineOwnedReleased);
            if (nextOperation < 0 || nextReturn < 0 || idleSince < -1 || competitionSince < -1 || exhaustedTools < 0) {
                throw new IllegalArgumentException("Invalid session counters");
            }
        }
    }

    private final Identity identity;
    private final Int2ObjectLinkedOpenHashMap<SlotContract> contracts = new Int2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<AppendSnapshot> appends = new Long2ObjectLinkedOpenHashMap<>();
    private final LongArrayFIFOQueue pendingAppends = new LongArrayFIFOQueue();
    private final Int2ObjectLinkedOpenHashMap<List<GenericStack>> tools = new Int2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<ReturnBatch> returns = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectLinkedOpenHashMap<ReturnBatch> acknowledged = new Long2ObjectLinkedOpenHashMap<>();
    private List<GenericStack> outputs = List.of();
    private List<ToolDelivery> machineOwnedReleased = List.of();
    private @Nullable Operation active;
    private State state = State.OPEN;
    private long nextOperation;
    private long nextReturn;
    private long idleSince = -1;
    private long competitionSince = -1;
    private long exhaustedTools;
    // Derived from immutable append receipts on load; normal execution never scans completed history.
    private long acceptedCount;
    private long completedCount;
    private long cancelledCount;
    private String fault = "";

    public ReusableInputSession(Identity identity, List<SlotContract> slotContracts) {
        this.identity = identity;
        if (slotContracts.isEmpty()) {
            throw new IllegalArgumentException("A reusable session needs at least one reusable slot");
        }
        for (SlotContract contract : slotContracts) {
            if (contracts.containsKey(contract.slot())) {
                throw new IllegalArgumentException("Duplicate reusable slot contract");
            }
            contracts.put(contract.slot(), contract);
            tools.put(contract.slot(), List.of());
        }
    }

    public Identity identity() {
        return identity;
    }

    public State status() {
        return state;
    }

    public String fault() {
        return fault;
    }

    public long exhaustedTools() {
        return exhaustedTools;
    }

    public @Nullable Operation activeOperation() {
        return active;
    }

    public List<SlotContract> slotContracts() {
        return List.copyOf(contracts.values());
    }

    public Int2ObjectMap<List<GenericStack>> heldTools() {
        return Int2ObjectMaps.unmodifiable(new Int2ObjectLinkedOpenHashMap<>(tools));
    }

    public Optional<AppendSnapshot> appendSnapshot(long sequence) {
        return appends.containsKey(sequence) ? Optional.of(appends.get(sequence)) : Optional.empty();
    }

    public List<ReturnBatch> returnOutbox() {
        return List.copyOf(returns.values());
    }

    public List<GenericStack> pendingOutputs() {
        return outputs;
    }

    public long accepted() {
        return acceptedCount;
    }

    public long completed() {
        return completedCount;
    }

    public long cancelled() {
        return cancelledCount;
    }

    public long pending() {
        return Math.subtractExact(Math.subtractExact(accepted(), completed()), cancelled());
    }

    /** Validates without taking ownership. A matching replay remains valid even after closure. */
    public void validateAppend(Append request) {
        if (isReplay(request)) {
            return;
        }
        if (state != State.OPEN || active != null) {
            throw new IllegalStateException("Append requires an open session at a native-operation safe point");
        }
        if (!appends.isEmpty() && request.sequence() <= appends.lastLongKey()) {
            throw new IllegalArgumentException("Append sequence must increase");
        }
        long totalAccepted = Math.addExact(accepted(), request.operations());
        long reserved = totalAccepted - completed() - cancelled();
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> candidate = new Int2ObjectLinkedOpenHashMap<>(tools);
        for (ToolDelivery delivery : request.deliveredTools()) {
            SlotContract contract = requireContract(delivery.slot());
            contract.rule().guaranteedUses((AEItemKey) delivery.stack().what());
            candidate.put(delivery.slot(), SessionAssets.merge(candidate.get(delivery.slot()), List.of(delivery.stack())));
        }
        for (SlotContract contract : contracts.values()) {
            if (!canRun(contract, candidate.get(contract.slot()), reserved)) {
                throw new IllegalArgumentException("Insufficient guaranteed reusable input capacity in slot " + contract.slot());
            }
        }
    }

    /**
     * Takes ownership only after all slots, amounts and reservations have passed validation; false is a no-op replay.
     */
    public boolean acceptAppend(Append request) {
        validateAppend(request);
        if (isReplay(request)) {
            return false;
        }
        for (ToolDelivery delivery : request.deliveredTools()) {
            tools.put(delivery.slot(), SessionAssets.merge(tools.get(delivery.slot()), List.of(delivery.stack())));
        }
        appends.put(request.sequence(), new AppendSnapshot(request, 0, 0, request.deliveredMaterials()));
        acceptedCount += request.operations(); // Overflow was checked by validateAppend before taking ownership.
        pendingAppends.enqueue(request.sequence());
        idleSince = -1;
        return true;
    }

    /** Transfers exactly one complete operation to execution escrow; empty means there is no runnable append. */
    public Optional<Operation> beginOperation() {
        if (state != State.OPEN || active != null) {
            return Optional.empty();
        }
        if (pendingAppends.isEmpty()) {
            return Optional.empty();
        }
        AppendSnapshot append = appends.get(pendingAppends.firstLong());
        List<ToolDelivery> selected = new ObjectArrayList<>();
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> retained = new Int2ObjectLinkedOpenHashMap<>(tools);
        for (SlotContract contract : contracts.values()) {
            List<GenericStack> available = new ObjectArrayList<>(tools.get(contract.slot()));
            if (contract.heldAmount() > 1) {
                // Longest remaining lives first preserves multi-tool feasibility; one-tool slots exhaust FIFO.
                available.sort(Comparator.comparingLong((GenericStack stack) -> contract.rule().guaranteedUses((AEItemKey) stack.what())).reversed());
            }
            long needed = contract.heldAmount();
            List<GenericStack> taken = new ObjectArrayList<>();
            for (GenericStack stack : available) {
                long amount = Math.min(needed, stack.amount());
                if (amount > 0) {
                    GenericStack selection = new GenericStack(stack.what(), amount);
                    selected.add(new ToolDelivery(contract.slot(), selection));
                    taken.add(selection);
                    needed -= amount;
                }
            }
            if (needed != 0) {
                throw new IllegalStateException("Accepted reusable reservation lost its physical tools");
            }
            retained.put(contract.slot(), SessionAssets.subtract(tools.get(contract.slot()), taken));
        }
        long followingId = Math.incrementExact(nextOperation);
        List<GenericStack> remaining = SessionAssets.subtract(append.remainingMaterials(), materials(append.request().consumedPerOperation()));
        Operation operation = new Operation(nextOperation, append.request().sequence(), append.request().consumedPerOperation(), selected);
        // Verify deterministic byproduct arithmetic before any native execution is allowed to occur.
        predictedOutcomes(operation);
        tools.putAll(retained);
        appends.put(append.request().sequence(), new AppendSnapshot(append.request(), append.completed(), append.cancelled(), remaining));
        active = operation;
        nextOperation = followingId;
        return Optional.of(operation);
    }

    /** Pure prediction for adapters/tests. Native executors must compare this with actual captured remainder assets. */
    public List<ToolOutcome> predictedOutcomes(Operation operation) {
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> successors = new Int2ObjectLinkedOpenHashMap<>();
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> byproducts = new Int2ObjectLinkedOpenHashMap<>();
        for (SlotContract contract : contracts.values()) {
            successors.put(contract.slot(), List.of());
            byproducts.put(contract.slot(), List.of());
        }
        for (ToolDelivery delivery : operation.tools()) {
            ReusableInputRule.Result result = requireContract(delivery.slot()).rule().advance((AEItemKey) delivery.stack().what(), 1);
            if (result.successor() != null) {
                successors.put(delivery.slot(), SessionAssets.merge(successors.get(delivery.slot()),
                        List.of(new GenericStack(result.successor(), delivery.stack().amount()))));
            }
            byproducts.put(delivery.slot(), SessionAssets.merge(byproducts.get(delivery.slot()),
                    SessionAssets.multiply(result.byproducts(), delivery.stack().amount())));
        }
        List<ToolOutcome> result = new ObjectArrayList<>();
        for (int slot : contracts.keySet()) {
            result.add(new ToolOutcome(slot, successors.get(slot), byproducts.get(slot)));
        }
        return List.copyOf(result);
    }

    /**
     * Records actual native effects. On mismatch actual successors/outputs are retained and the session
     * faults; pre-execution tools are never reconstructed. Invalid report structure leaves execution escrow
     * unresolved so the caller can supply a complete corrected report without losing physical assets.
     */
    public boolean completeOperation(long operationId, List<ToolOutcome> actual, List<GenericStack> ordinaryOutputs) {
        return finishOperation(operationId, actual, ordinaryOutputs, "");
    }

    /** Reports captured physical assets after a native exception and closes further admission. */
    public void faultOperation(long operationId, List<ToolOutcome> actual, List<GenericStack> ordinaryOutputs, String reason) {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Native failure needs diagnostic context");
        }
        finishOperation(operationId, actual, ordinaryOutputs, reason);
    }

    private boolean finishOperation(long operationId, List<ToolOutcome> actual, List<GenericStack> ordinaryOutputs, String reason) {
        Operation operation = requireActive(operationId);
        Int2ObjectLinkedOpenHashMap<ToolOutcome> outcomes = new Int2ObjectLinkedOpenHashMap<>();
        for (ToolOutcome outcome : actual) {
            requireContract(outcome.slot());
            if (outcomes.containsKey(outcome.slot())) {
                throw new IllegalArgumentException("Duplicate actual tool outcome slot");
            }
            outcomes.put(outcome.slot(), outcome);
        }
        if (!outcomes.keySet().equals(contracts.keySet())) {
            throw new IllegalArgumentException("Native result must account for every reusable slot");
        }
        List<ToolOutcome> predicted = predictedOutcomes(operation);
        boolean matches = true;
        long exhausted = 0;
        List<GenericStack> newOutputs = SessionAssets.merge(outputs, ordinaryOutputs);
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> newTools = new Int2ObjectLinkedOpenHashMap<>(tools);
        for (ToolOutcome expected : predicted) {
            ToolOutcome outcome = outcomes.get(expected.slot());
            matches &= SessionAssets.counts(expected.successors()).equals(SessionAssets.counts(outcome.successors())) &&
                    SessionAssets.counts(expected.byproducts()).equals(SessionAssets.counts(outcome.byproducts()));
            newTools.put(outcome.slot(), SessionAssets.merge(outcome.successors(), tools.get(outcome.slot())));
            newOutputs = SessionAssets.merge(newOutputs, outcome.byproducts());
            long surviving = outcome.successors().stream().mapToLong(GenericStack::amount).reduce(0, Math::addExact);
            exhausted = Math.addExact(exhausted, Math.max(0, requireContract(outcome.slot()).heldAmount() - surviving));
        }
        long totalExhausted = Math.addExact(exhaustedTools, exhausted);
        AppendSnapshot append = appends.get(operation.appendSequence());
        AppendSnapshot advanced = new AppendSnapshot(append.request(), Math.incrementExact(append.completed()),
                append.cancelled(), append.remainingMaterials());
        tools.putAll(newTools);
        outputs = newOutputs;
        exhaustedTools = totalExhausted;
        appends.put(operation.appendSequence(), advanced);
        completedCount++;
        if (advanced.completed() + advanced.cancelled() == advanced.request().operations()) {
            pendingAppends.dequeueLong();
        }
        active = null;
        if (!matches || !reason.isEmpty()) {
            state = State.FAULTED;
            fault = reason.isEmpty() ? "Native tool successor or byproduct differs from frozen rule" : reason;
        } else if (state == State.CLOSING) {
            settleClose();
        }
        return matches && reason.isEmpty();
    }

    /** May only be called when the native executor guarantees that execution has not begun. */
    public void abortOperation(long operationId) {
        Operation operation = requireActive(operationId);
        AppendSnapshot append = appends.get(operation.appendSequence());
        List<GenericStack> restoredMaterials = SessionAssets.merge(append.remainingMaterials(), materials(operation.consumed()));
        Int2ObjectLinkedOpenHashMap<List<GenericStack>> restoredTools = new Int2ObjectLinkedOpenHashMap<>(tools);
        for (ToolDelivery delivery : operation.tools()) {
            restoredTools.put(delivery.slot(), SessionAssets.merge(restoredTools.get(delivery.slot()), List.of(delivery.stack())));
        }
        tools.putAll(restoredTools);
        appends.put(operation.appendSequence(), new AppendSnapshot(append.request(), append.completed(), append.cancelled(), restoredMaterials));
        active = null;
        if (state == State.CLOSING) {
            settleClose();
        }
    }

    /** Cancels unexecuted work at a safe point. An unresolved native operation delays all refunds. */
    public void close() {
        if (state == State.CLOSED || state == State.RETURN_PENDING) {
            return;
        }
        if (active != null) {
            if (state != State.FAULTED) {
                state = State.CLOSING;
            }
            return;
        }
        state = State.CLOSING;
        settleClose();
    }

    private void settleClose() {
        List<GenericStack> refund = List.of();
        Long2ObjectLinkedOpenHashMap<AppendSnapshot> cancelledAppends = new Long2ObjectLinkedOpenHashMap<>();
        for (AppendSnapshot append : appends.values()) {
            refund = SessionAssets.merge(refund, append.remainingMaterials());
            cancelledAppends.put(append.request().sequence(), new AppendSnapshot(append.request(), append.completed(),
                    append.request().operations() - append.completed(), List.of()));
        }
        List<ToolDelivery> released = new ObjectArrayList<>(machineOwnedReleased);
        for (SlotContract contract : contracts.values()) {
            if (contract.ownership() == Ownership.CPU_SUPPLIED) {
                refund = SessionAssets.merge(refund, tools.get(contract.slot()));
            } else {
                for (GenericStack stack : tools.get(contract.slot())) {
                    released.add(new ToolDelivery(contract.slot(), stack));
                }
            }
        }
        long followingReturn = refund.isEmpty() ? nextReturn : Math.incrementExact(nextReturn);
        if (!refund.isEmpty()) {
            returns.put(nextReturn, new ReturnBatch(nextReturn, refund));
        }
        nextReturn = followingReturn;
        appends.putAll(cancelledAppends);
        cancelledCount = acceptedCount - completedCount;
        pendingAppends.clear();
        tools.replaceAll((slot, ignored) -> List.of());
        machineOwnedReleased = List.copyOf(released);
        state = returns.isEmpty() ? State.CLOSED : State.RETURN_PENDING;
    }

    /** Exact full-batch acknowledgment; duplicate matching acknowledgments are harmless, unknown IDs fail. */
    public boolean acknowledgeReturn(long sequence, List<GenericStack> exactAssets) {
        if (!returns.containsKey(sequence)) {
            if (!acknowledged.containsKey(sequence)) {
                throw new IllegalArgumentException("Unknown return acknowledgment sequence");
            }
            requireAcknowledgment(acknowledged.get(sequence), exactAssets);
            return false;
        }
        ReturnBatch batch = returns.get(sequence);
        requireAcknowledgment(batch, exactAssets);
        returns.remove(sequence);
        acknowledged.put(sequence, batch);
        if (returns.isEmpty() && state == State.RETURN_PENDING) {
            state = State.CLOSED;
        }
        return true;
    }

    /** Atomically transfers ordinary outputs and transition byproducts to the caller's output queue. */
    public List<GenericStack> drainOutputs() {
        List<GenericStack> result = outputs;
        outputs = List.of();
        return result;
    }

    /** Transfers machine-owned survivors to the machine inventory, never to the CPU refund stream. */
    public List<ToolDelivery> drainMachineOwnedReleased() {
        List<ToolDelivery> result = machineOwnedReleased;
        machineOwnedReleased = List.of();
        return result;
    }

    /** Returns true when twenty idle ticks or twenty continuously competing ticks request safe-point closure. */
    public boolean tick(long now, boolean competitorWaiting) {
        if (now < 0) {
            throw new IllegalArgumentException("Negative session tick");
        }
        if (state != State.OPEN) {
            return false;
        }
        if (pending() == 0 && active == null) {
            if (idleSince < 0 || idleSince > now) {
                idleSince = now;
            }
        } else {
            idleSince = -1;
        }
        if (competitorWaiting) {
            if (competitionSince < 0 || competitionSince > now) {
                competitionSince = now;
            }
        } else {
            competitionSince = -1;
        }
        if ((idleSince >= 0 && now - idleSince >= 20) || (competitionSince >= 0 && now - competitionSince >= 20)) {
            close();
            return true;
        }
        return false;
    }

    public Snapshot snapshot() {
        return new Snapshot(identity, slotContracts(), state, List.copyOf(appends.values()), deliveries(tools), active,
                outputs, returnOutbox(), List.copyOf(acknowledged.values()), machineOwnedReleased,
                nextOperation, nextReturn, idleSince, competitionSince, exhaustedTools, fault);
    }

    /** Restores persisted state and quarantines an interrupted operation until its actual result is reconciled. */
    public static ReusableInputSession restore(Snapshot snapshot) {
        ReusableInputSession result = new ReusableInputSession(snapshot.identity(), snapshot.contracts());
        long previousSequence = -1;
        for (AppendSnapshot append : snapshot.appends()) {
            if (append.request().sequence() <= previousSequence) {
                throw new IllegalArgumentException("Persisted append sequences must increase strictly");
            }
            previousSequence = append.request().sequence();
            result.appends.put(append.request().sequence(), append);
            result.acceptedCount = Math.addExact(result.acceptedCount, append.request().operations());
            result.completedCount = Math.addExact(result.completedCount, append.completed());
            result.cancelledCount = Math.addExact(result.cancelledCount, append.cancelled());
            if (append.completed() + append.cancelled() < append.request().operations()) {
                result.pendingAppends.enqueue(append.request().sequence());
            }
        }
        for (ToolDelivery tool : snapshot.tools()) {
            result.requireContract(tool.slot());
            result.tools.put(tool.slot(), SessionAssets.merge(result.tools.get(tool.slot()), List.of(tool.stack())));
        }
        for (ReturnBatch batch : snapshot.returns()) {
            if (batch.sequence() >= snapshot.nextReturn() || result.returns.containsKey(batch.sequence())) {
                throw new IllegalArgumentException("Invalid pending return sequence");
            }
            result.returns.put(batch.sequence(), batch);
        }
        for (ReturnBatch batch : snapshot.acknowledged()) {
            if (batch.sequence() >= snapshot.nextReturn() || result.returns.containsKey(batch.sequence()) ||
                    result.acknowledged.containsKey(batch.sequence())) {
                throw new IllegalArgumentException("Invalid acknowledged return sequence");
            }
            result.acknowledged.put(batch.sequence(), batch);
        }
        result.active = snapshot.active();
        result.state = snapshot.state();
        result.outputs = snapshot.outputs();
        result.machineOwnedReleased = snapshot.machineOwnedReleased();
        result.nextOperation = snapshot.nextOperation();
        result.nextReturn = snapshot.nextReturn();
        result.idleSince = snapshot.idleSince();
        result.competitionSince = snapshot.competitionSince();
        result.exhaustedTools = snapshot.exhaustedTools();
        result.fault = snapshot.fault();
        result.validateRestored();
        if (result.active != null) {
            result.state = State.FAULTED;
            result.fault = "Interrupted native operation requires actual-asset reconciliation";
        }
        return result;
    }

    private void validateRestored() {
        if ((state == State.CLOSED && !returns.isEmpty()) || (state == State.RETURN_PENDING && returns.isEmpty())) {
            throw new IllegalArgumentException("Session state contradicts its return outbox");
        }
        boolean settled = state == State.CLOSED || state == State.RETURN_PENDING;
        if (settled && (active != null || pending() != 0 || !deliveries(tools).isEmpty())) {
            throw new IllegalArgumentException("Closed session still holds execution assets or reservations");
        }
        if (!settled && (!returns.isEmpty() || !acknowledged.isEmpty() || !machineOwnedReleased.isEmpty())) {
            throw new IllegalArgumentException("Unsettled session contains already settled assets");
        }
        for (ToolDelivery released : machineOwnedReleased) {
            if (requireContract(released.slot()).ownership() != Ownership.MACHINE_OWNED) {
                throw new IllegalArgumentException("Released machine tool belongs to the CPU");
            }
        }
        for (AppendSnapshot append : appends.values()) {
            if (!settled && append.cancelled() != 0) {
                throw new IllegalArgumentException("Unsettled session contains cancelled operations");
            }
            long waiting = append.request().operations() - append.completed() - append.cancelled();
            if (active != null && active.appendSequence() == append.request().sequence()) {
                waiting--;
            }
            if (waiting < 0 || !SessionAssets.counts(append.remainingMaterials()).equals(SessionAssets.counts(
                    SessionAssets.multiply(materials(append.request().consumedPerOperation()), waiting)))) {
                throw new IllegalArgumentException("Persisted material escrow contradicts append progress");
            }
        }
        if (state == State.OPEN || state == State.CLOSING) {
            for (SlotContract contract : contracts.values()) {
                for (GenericStack tool : tools.get(contract.slot())) {
                    contract.rule().guaranteedUses((AEItemKey) tool.what());
                }
            }
        }
        if (active != null) {
            if (pendingAppends.isEmpty() || pendingAppends.firstLong() != active.appendSequence()) {
                throw new IllegalArgumentException("Persisted execution escrow has no matching append");
            }
            AppendSnapshot append = appends.get(active.appendSequence());
            if (active.id() >= nextOperation || !active.consumed().equals(append.request().consumedPerOperation())) {
                throw new IllegalArgumentException("Persisted execution escrow has no matching append");
            }
            Int2ObjectLinkedOpenHashMap<List<GenericStack>> activeTools = new Int2ObjectLinkedOpenHashMap<>();
            for (SlotContract contract : contracts.values()) {
                activeTools.put(contract.slot(), List.of());
            }
            for (ToolDelivery tool : active.tools()) {
                requireContract(tool.slot()).rule().guaranteedUses((AEItemKey) tool.stack().what());
                activeTools.put(tool.slot(), SessionAssets.merge(activeTools.get(tool.slot()), List.of(tool.stack())));
            }
            for (SlotContract contract : contracts.values()) {
                if (activeTools.get(contract.slot()).stream().mapToLong(GenericStack::amount).reduce(0, Math::addExact) != contract.heldAmount()) {
                    throw new IllegalArgumentException("Persisted execution escrow has incomplete tools");
                }
            }
        } else if (state == State.OPEN) {
            for (SlotContract contract : contracts.values()) {
                if (!canRun(contract, tools.get(contract.slot()), pending())) {
                    throw new IllegalArgumentException("Persisted tools do not cover accepted reservations");
                }
            }
        }
    }

    private static boolean canRun(SlotContract contract, List<GenericStack> available, long operations) {
        if (operations == 0) {
            return true;
        }
        // Each physical tool can participate at most once per operation, even if it has unlimited durability.
        long required = Math.multiplyExact(operations, contract.heldAmount());
        for (GenericStack stack : available) {
            long perTool = Math.min(operations, contract.rule().guaranteedUses((AEItemKey) stack.what()));
            long neededTools = required / perTool + (required % perTool == 0 ? 0 : 1);
            if (stack.amount() >= neededTools) {
                return true;
            }
            required -= stack.amount() * perTool;
        }
        return false;
    }

    private boolean isReplay(Append request) {
        if (!appends.containsKey(request.sequence())) {
            return false;
        }
        AppendSnapshot previous = appends.get(request.sequence());
        if (!previous.request().equals(request)) {
            throw new IllegalArgumentException("Append sequence was replayed with a different payload");
        }
        return true;
    }

    private SlotContract requireContract(int slot) {
        if (!contracts.containsKey(slot)) {
            throw new IllegalArgumentException("No reusable contract for slot " + slot);
        }
        return contracts.get(slot);
    }

    private Operation requireActive(long operationId) {
        if (active == null || active.id() != operationId) {
            throw new IllegalArgumentException("Unknown active operation identity");
        }
        return active;
    }

    private static void requireAcknowledgment(ReturnBatch batch, List<GenericStack> exactAssets) {
        if (!SessionAssets.counts(batch.assets()).equals(SessionAssets.counts(exactAssets))) {
            throw new IllegalArgumentException("Return acknowledgment does not match the actual asset batch");
        }
    }

    private static List<GenericStack> materials(List<SlotInput> slots) {
        return slots.stream().map(SlotInput::stack).toList();
    }

    private static List<ToolDelivery> deliveries(Int2ObjectLinkedOpenHashMap<List<GenericStack>> assets) {
        List<ToolDelivery> result = new ObjectArrayList<>();
        assets.forEach((slot, stacks) -> stacks.forEach(stack -> result.add(new ToolDelivery(slot, stack))));
        return List.copyOf(result);
    }
}
