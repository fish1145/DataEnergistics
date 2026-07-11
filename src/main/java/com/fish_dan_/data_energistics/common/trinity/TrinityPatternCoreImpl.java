package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default state implementation for {@link TrinityPatternCore}.
 *
 * <p>
 * The owning block entity supplies decoding and dirty callbacks; this object owns all mutable crafting data and
 * commits NBT loads only after the entire payload has passed validation.
 */
public final class TrinityPatternCoreImpl implements TrinityPatternCore {

    private static final Set<Integer> SUPPORTED_CAPACITIES = Set.of(64, 128, 512);
    private static final String CORE_ID_TAG = "core_id";
    private static final String PATTERN_CAPACITY_TAG = "pattern_capacity";
    private static final String PATTERNS_TAG = "patterns";
    private static final String SLOT_TAG = "slot";
    private static final String ROUTE_TAG = "route";
    private static final String STACK_TAG = "stack";
    private static final String QUEUES_TAG = "queues";
    private static final String BATCHES_TAG = "batches";
    private static final String PENDING_OUTPUTS_TAG = "pending_outputs";
    private static final String OUTPUTS_TAG = "outputs";

    private final int patternCapacity;
    private final PatternDecoder decoder;
    private final Runnable changeListener;
    private final List<ItemStack> patterns;
    private final List<@Nullable IMolecularAssemblerSupportedPattern> decodedPatterns;
    private final List<ArrayDeque<TrinityCraftingBatch>> queues;
    private final List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> pendingOutputs;
    private final InternalInventory patternInventory = new PatternInventory();

    private UUID coreId;
    private long revision;
    private long stateRevision;
    @Nullable
    private CoreRefundTransaction activeRefundTransaction;

    /**
     * Creates a fresh pattern core state with a random stable UUID.
     *
     * @param patternCapacity one of the three physical P-core capacities
     * @param decoder         level-aware supported-pattern decoder
     * @param changeListener  callback used to dirty the owning block entity/catalog
     */
    public TrinityPatternCoreImpl(int patternCapacity, PatternDecoder decoder, Runnable changeListener) {
        this(patternCapacity, UUID.randomUUID(), decoder, changeListener);
    }

    /**
     * Creates a pattern core with an explicit identity, primarily for deterministic state restoration and tests.
     *
     * @param patternCapacity one of the three physical P-core capacities
     * @param coreId          persistent identity
     * @param decoder         level-aware supported-pattern decoder
     * @param changeListener  callback used to dirty the owning block entity/catalog
     */
    public TrinityPatternCoreImpl(int patternCapacity, UUID coreId, PatternDecoder decoder, Runnable changeListener) {
        validateCapacity(patternCapacity);
        this.patternCapacity = patternCapacity;
        this.coreId = coreId;
        this.decoder = decoder;
        this.changeListener = changeListener;
        this.patterns = new ArrayList<>(patternCapacity);
        this.decodedPatterns = new ArrayList<>(patternCapacity);
        this.queues = new ArrayList<>(patternCapacity);
        this.pendingOutputs = new ArrayList<>(patternCapacity);
        for (int slot = 0; slot < patternCapacity; slot++) {
            this.patterns.add(ItemStack.EMPTY);
            this.decodedPatterns.add(null);
            this.queues.add(new ArrayDeque<>());
            this.pendingOutputs.add(new LinkedHashMap<>());
        }
    }

    @Override
    public UUID coreId() {
        return this.coreId;
    }

    @Override
    public int patternCapacity() {
        return this.patternCapacity;
    }

    @Override
    public long revision() {
        return this.revision;
    }

    @Override
    public InternalInventory patternInventory() {
        return this.patternInventory;
    }

    @Override
    public ItemStack pattern(int slot) {
        checkSlot(slot);
        return this.patterns.get(slot).copy();
    }

    @Override
    public boolean trySetPattern(int slot, ItemStack pattern) {
        ensureNoActiveRefundTransaction();
        checkSlot(slot);
        ItemStack normalized = normalizePattern(pattern);
        IMolecularAssemblerSupportedPattern decoded = normalized.isEmpty() ? null : this.decoder.decode(normalized);
        if (!normalized.isEmpty() && decoded == null) {
            return false;
        }

        ItemStack current = this.patterns.get(slot);
        if (ItemStack.matches(current, normalized)) {
            if (!normalized.isEmpty() && this.decodedPatterns.get(slot) == null) {
                this.decodedPatterns.set(slot, decoded);
                markPatternCatalogChanged();
            }
            return true;
        }
        this.patterns.set(slot, normalized);
        this.decodedPatterns.set(slot, decoded);
        markPatternCatalogChanged();
        return true;
    }

    @Nullable
    @Override
    public IMolecularAssemblerSupportedPattern decodedPattern(int slot) {
        checkSlot(slot);
        return this.decodedPatterns.get(slot);
    }

    @Override
    public void refreshPatternCache(int slot) {
        ensureNoActiveRefundTransaction();
        checkSlot(slot);
        ItemStack pattern = this.patterns.get(slot);
        this.decodedPatterns.set(slot, pattern.isEmpty() ? null : this.decoder.decode(pattern));
        markPatternCatalogChanged();
    }

    @Override
    public void refreshAllPatternCaches() {
        ensureNoActiveRefundTransaction();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            ItemStack pattern = this.patterns.get(slot);
            this.decodedPatterns.set(slot, pattern.isEmpty() ? null : this.decoder.decode(pattern));
        }
        markPatternCatalogChanged();
    }

    @Override
    public boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick) {
        ensureNoActiveRefundTransaction();
        int slot = route.slot();
        checkSlot(slot);
        if (!this.coreId.equals(route.coreId())) {
            return false;
        }
        ItemStack installedPattern = this.patterns.get(slot);
        if (installedPattern.isEmpty() || this.decodedPatterns.get(slot) == null ||
                !ItemStack.isSameItemSameComponents(installedPattern, patternSnapshot)) {
            return false;
        }

        TrinityCraftingBatch batch = new TrinityCraftingBatch(
                queuedTick,
                route,
                normalizePattern(patternSnapshot),
                inputs);
        this.queues.get(slot).addLast(batch);
        markPersistentStateChanged();
        return true;
    }

    @Override
    public List<TrinityCraftingBatch> queuedBatches(int slot) {
        checkSlot(slot);
        ArrayList<TrinityCraftingBatch> copy = new ArrayList<>(this.queues.get(slot).size());
        for (TrinityCraftingBatch batch : this.queues.get(slot)) {
            copy.add(new TrinityCraftingBatch(
                    batch.queuedTick(),
                    batch.route(),
                    batch.patternSnapshot(),
                    batch.inputs()));
        }
        return List.copyOf(copy);
    }

    @Override
    public int queuedBatchCount(int slot) {
        checkSlot(slot);
        return this.queues.get(slot).size();
    }

    @Override
    public int queuedBatchCount() {
        int count = 0;
        for (ArrayDeque<TrinityCraftingBatch> queue : this.queues) {
            count += queue.size();
        }
        return count;
    }

    @Override
    public int executeReadyBatches(long currentTick, BatchExecutor executor) {
        ensureNoActiveRefundTransaction();
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Current tick must not be negative: " + currentTick);
        }
        int completed = 0;
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            ArrayDeque<TrinityCraftingBatch> queue = this.queues.get(slot);
            while (!queue.isEmpty()) {
                TrinityCraftingBatch batch = queue.peekFirst();
                if (batch.queuedTick() >= currentTick || this.decodedPatterns.get(slot) == null ||
                        !batch.matchesPattern(this.patterns.get(slot))) {
                    break;
                }

                BatchExecutionResult result = executor.execute(slot, batch);
                if (!result.completed()) {
                    break;
                }

                queue.removeFirst();
                appendOutputsWithoutNotification(batch.route(), result.outputs());
                completed++;
            }
        }
        if (completed > 0) {
            markPersistentStateChanged();
        }
        return completed;
    }

    @Override
    public List<ItemStack> pendingOutputs(PatternRoute route) {
        validateOwnedRoute(route);
        List<ItemStack> outputs = this.pendingOutputs.get(route.slot()).get(route);
        return outputs == null ? List.of() : copyStacks(outputs);
    }

    @Override
    public void appendPendingOutputs(PatternRoute route, List<ItemStack> outputs) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        int oldSize = pendingOutputStackCount(route);
        appendOutputsWithoutNotification(route, outputs);
        if (pendingOutputStackCount(route) != oldSize) {
            markPersistentStateChanged();
        }
    }

    @Override
    public void replacePendingOutputs(PatternRoute route, List<ItemStack> outputs) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        ArrayList<ItemStack> replacement = new ArrayList<>();
        appendNonEmptyCopies(replacement, outputs);
        Map<PatternRoute, ArrayList<ItemStack>> outputGroups = this.pendingOutputs.get(route.slot());
        if (replacement.isEmpty()) {
            outputGroups.remove(route);
        } else {
            outputGroups.put(route, replacement);
        }
        markPersistentStateChanged();
    }

    @Override
    public boolean hasWork() {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            if (!this.queues.get(slot).isEmpty() || !this.pendingOutputs.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RefundTransaction prepareRefund() {
        return beginRefund(captureRefundState(null));
    }

    @Override
    public RefundTransaction prepareRefund(UUID hostId) {
        if (hostId == null) {
            throw new IllegalArgumentException("A host-scoped Trinity refund requires a host UUID");
        }
        return beginRefund(captureRefundState(hostId));
    }

    @Override
    public boolean tryRefundAll(TrinityRefundDelivery delivery) {
        RefundTransaction transaction = prepareRefund();
        List<ItemStack> refundable = transaction.refundableStacks();
        try {
            if (refundable.isEmpty() || !delivery.prepare(refundable)) {
                transaction.rollback();
                return false;
            }
            boolean committed = transaction.commit();
            if (!committed) {
                transaction.rollback();
                return false;
            }
            try {
                delivery.deliver(refundable);
                return true;
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern core {} refund delivery failed after queued state was committed",
                        this.coreId,
                        exception);
                // The mutable core state is already committed. Reporting failure here would imply that a retry can
                // find the same items in the core, which is no longer true.
                return true;
            } finally {
                transaction.complete();
            }
        } catch (RuntimeException exception) {
            transaction.rollback();
            Data_Energistics.LOGGER.error(
                    "Failed to prepare Trinity pattern core {} refund delivery; retained queued state",
                    this.coreId,
                    exception);
            return false;
        }
    }

    private RefundState captureRefundState(@Nullable UUID routeHostId) {
        ArrayList<ItemStack> refundable = new ArrayList<>();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            for (TrinityCraftingBatch batch : this.queues.get(slot)) {
                if (routeHostId == null || routeHostId.equals(batch.route().hostId())) {
                    appendNonEmptyCopies(refundable, batch.inputs());
                }
            }
            for (Map.Entry<PatternRoute, ArrayList<ItemStack>> entry : this.pendingOutputs.get(slot).entrySet()) {
                if (routeHostId == null || routeHostId.equals(entry.getKey().hostId())) {
                    appendNonEmptyCopies(refundable, entry.getValue());
                }
            }
        }
        return new RefundState(
                routeHostId,
                this.stateRevision,
                copyQueues(this.queues),
                copyPendingOutputs(this.pendingOutputs),
                List.copyOf(refundable));
    }

    private RefundTransaction beginRefund(RefundState captured) {
        if (this.activeRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " already has an active refund");
        }
        CoreRefundTransaction transaction = new CoreRefundTransaction(captured);
        this.activeRefundTransaction = transaction;
        return transaction;
    }

    private void clearRefundState(@Nullable UUID routeHostId) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            if (routeHostId == null) {
                this.queues.get(slot).clear();
                this.pendingOutputs.get(slot).clear();
            } else {
                this.queues.get(slot).removeIf(batch -> routeHostId.equals(batch.route().hostId()));
                this.pendingOutputs.get(slot).entrySet().removeIf(entry -> routeHostId.equals(entry.getKey().hostId()));
            }
        }
        markPersistentStateChanged();
    }

    private boolean matchesRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            if (!queuesMatch(this.queues.get(slot), captured.queues().get(slot)) ||
                    !pendingOutputsMatch(this.pendingOutputs.get(slot), captured.pendingOutputs().get(slot))) {
                return false;
            }
        }
        return true;
    }

    private void restoreRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.queues.set(slot, copyQueue(captured.queues().get(slot)));
            this.pendingOutputs.set(slot, copyPendingOutputGroup(captured.pendingOutputs().get(slot)));
        }
        markPersistentStateChanged();
    }

    private static List<ArrayDeque<TrinityCraftingBatch>> copyQueues(List<ArrayDeque<TrinityCraftingBatch>> queues) {
        ArrayList<ArrayDeque<TrinityCraftingBatch>> copied = new ArrayList<>(queues.size());
        for (ArrayDeque<TrinityCraftingBatch> queue : queues) {
            copied.add(copyQueue(queue));
        }
        return copied;
    }

    private static ArrayDeque<TrinityCraftingBatch> copyQueue(Iterable<TrinityCraftingBatch> queue) {
        ArrayDeque<TrinityCraftingBatch> copied = new ArrayDeque<>();
        for (TrinityCraftingBatch batch : queue) {
            copied.addLast(new TrinityCraftingBatch(
                    batch.queuedTick(),
                    batch.route(),
                    batch.patternSnapshot(),
                    batch.inputs()));
        }
        return copied;
    }

    private static List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> copyPendingOutputs(
                                                                                              List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> pendingOutputs) {
        ArrayList<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> copied = new ArrayList<>(pendingOutputs.size());
        for (LinkedHashMap<PatternRoute, ArrayList<ItemStack>> outputGroups : pendingOutputs) {
            copied.add(copyPendingOutputGroup(outputGroups));
        }
        return copied;
    }

    private static LinkedHashMap<PatternRoute, ArrayList<ItemStack>> copyPendingOutputGroup(
                                                                                            Map<PatternRoute, ? extends List<ItemStack>> outputGroups) {
        LinkedHashMap<PatternRoute, ArrayList<ItemStack>> copied = new LinkedHashMap<>();
        for (Map.Entry<PatternRoute, ? extends List<ItemStack>> entry : outputGroups.entrySet()) {
            ArrayList<ItemStack> outputs = new ArrayList<>();
            appendNonEmptyCopies(outputs, entry.getValue());
            copied.put(entry.getKey(), outputs);
        }
        return copied;
    }

    private static boolean queuesMatch(Iterable<TrinityCraftingBatch> current,
                                       Iterable<TrinityCraftingBatch> captured) {
        var currentIterator = current.iterator();
        var capturedIterator = captured.iterator();
        while (currentIterator.hasNext() && capturedIterator.hasNext()) {
            if (!batchesMatch(currentIterator.next(), capturedIterator.next())) {
                return false;
            }
        }
        return !currentIterator.hasNext() && !capturedIterator.hasNext();
    }

    private static boolean batchesMatch(TrinityCraftingBatch current, TrinityCraftingBatch captured) {
        if (current.queuedTick() != captured.queuedTick() || !current.route().equals(captured.route()) ||
                !stacksMatch(current.patternSnapshot(), captured.patternSnapshot())) {
            return false;
        }
        List<ItemStack> currentInputs = current.inputs();
        List<ItemStack> capturedInputs = captured.inputs();
        if (currentInputs.size() != capturedInputs.size()) {
            return false;
        }
        for (int slot = 0; slot < currentInputs.size(); slot++) {
            if (!stacksMatch(currentInputs.get(slot), capturedInputs.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static boolean pendingOutputsMatch(Map<PatternRoute, ? extends List<ItemStack>> current,
                                               Map<PatternRoute, ? extends List<ItemStack>> captured) {
        if (current.size() != captured.size()) {
            return false;
        }
        var currentIterator = current.entrySet().iterator();
        var capturedIterator = captured.entrySet().iterator();
        while (currentIterator.hasNext()) {
            Map.Entry<PatternRoute, ? extends List<ItemStack>> currentEntry = currentIterator.next();
            Map.Entry<PatternRoute, ? extends List<ItemStack>> capturedEntry = capturedIterator.next();
            if (!currentEntry.getKey().equals(capturedEntry.getKey()) ||
                    !stackListsMatch(currentEntry.getValue(), capturedEntry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean stackListsMatch(List<ItemStack> current, List<ItemStack> captured) {
        if (current.size() != captured.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            if (!stacksMatch(current.get(index), captured.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean stacksMatch(ItemStack current, ItemStack captured) {
        return current.getCount() == captured.getCount() && ItemStack.isSameItemSameComponents(current, captured);
    }

    @Override
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        data.put(PATTERNS_TAG, writePatterns(registries));
        data.put(QUEUES_TAG, writeQueues(registries));
        data.put(PENDING_OUTPUTS_TAG, writePendingOutputs(registries));
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        ensureNoActiveRefundTransaction();
        if (!data.hasUUID(CORE_ID_TAG)) {
            if (containsCoreState(data)) {
                throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its UUID");
            }
            return;
        }
        if (!data.contains(PATTERN_CAPACITY_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its capacity");
        }
        int persistedCapacity = data.getInt(PATTERN_CAPACITY_TAG);
        if (persistedCapacity != this.patternCapacity) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core capacity " + persistedCapacity +
                            " does not match block capacity " + this.patternCapacity);
        }

        UUID loadedId = data.getUUID(CORE_ID_TAG);
        List<ItemStack> loadedPatterns = readPatterns(data.getList(PATTERNS_TAG, Tag.TAG_COMPOUND), registries);
        List<ArrayDeque<TrinityCraftingBatch>> loadedQueues = readQueues(
                data.getList(QUEUES_TAG, Tag.TAG_COMPOUND),
                registries,
                loadedId);
        List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> loadedOutputs = readPendingOutputs(
                data.getList(PENDING_OUTPUTS_TAG, Tag.TAG_COMPOUND),
                registries,
                loadedId);

        this.coreId = loadedId;
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.patterns.set(slot, loadedPatterns.get(slot));
            this.decodedPatterns.set(slot, null);
            this.queues.set(slot, loadedQueues.get(slot));
            this.pendingOutputs.set(slot, loadedOutputs.get(slot));
        }
        this.revision = Math.incrementExact(this.revision);
        this.stateRevision = Math.incrementExact(this.stateRevision);
    }

    private ListTag writePatterns(HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            ItemStack pattern = this.patterns.get(slot);
            if (pattern.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT_TAG, slot);
            entry.put(STACK_TAG, pattern.saveOptional(registries));
            entries.add(entry);
        }
        return entries;
    }

    private ListTag writeQueues(HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            ArrayDeque<TrinityCraftingBatch> queue = this.queues.get(slot);
            if (queue.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT_TAG, slot);
            ListTag batches = new ListTag();
            for (TrinityCraftingBatch batch : queue) {
                batches.add(batch.writeToTag(registries));
            }
            entry.put(BATCHES_TAG, batches);
            entries.add(entry);
        }
        return entries;
    }

    private ListTag writePendingOutputs(HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            for (Map.Entry<PatternRoute, ArrayList<ItemStack>> group : this.pendingOutputs.get(slot).entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.put(ROUTE_TAG, group.getKey().writeToTag());
                ListTag outputList = new ListTag();
                for (ItemStack output : group.getValue()) {
                    outputList.add(output.saveOptional(registries));
                }
                entry.put(OUTPUTS_TAG, outputList);
                entries.add(entry);
            }
        }
        return entries;
    }

    private List<ItemStack> readPatterns(ListTag entries, HolderLookup.Provider registries) {
        ArrayList<ItemStack> loaded = emptyPatternList();
        Set<Integer> populated = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            int slot = checkedPersistedSlot(entry, populated, "pattern");
            ItemStack pattern = normalizePattern(ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG)));
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("Persisted pattern slot " + slot + " is empty");
            }
            loaded.set(slot, pattern);
        }
        return loaded;
    }

    private List<ArrayDeque<TrinityCraftingBatch>> readQueues(ListTag entries, HolderLookup.Provider registries,
                                                              UUID loadedId) {
        ArrayList<ArrayDeque<TrinityCraftingBatch>> loaded = emptyQueueList();
        Set<Integer> populated = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            int slot = checkedPersistedSlot(entry, populated, "queue");
            ListTag batchList = entry.getList(BATCHES_TAG, Tag.TAG_COMPOUND);
            if (batchList.isEmpty()) {
                throw new IllegalArgumentException("Persisted queue slot " + slot + " has no batches");
            }
            ArrayDeque<TrinityCraftingBatch> queue = loaded.get(slot);
            for (int batchIndex = 0; batchIndex < batchList.size(); batchIndex++) {
                TrinityCraftingBatch batch = TrinityCraftingBatch.readFromTag(
                        batchList.getCompound(batchIndex),
                        registries);
                validatePersistedRoute(batch.route(), loadedId, slot, "queued batch");
                queue.addLast(batch);
            }
        }
        return loaded;
    }

    private List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> readPendingOutputs(
                                                                                       ListTag entries,
                                                                                       HolderLookup.Provider registries,
                                                                                       UUID loadedId) {
        ArrayList<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> loaded = emptyOutputList();
        Set<PatternRoute> populated = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.contains(ROUTE_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Persisted pending output group is missing its route");
            }
            PatternRoute route = PatternRoute.readFromTag(entry.getCompound(ROUTE_TAG));
            validatePersistedRoute(route, loadedId, route.slot(), "pending output");
            if (!populated.add(route)) {
                throw new IllegalArgumentException("Duplicate persisted pending output route: " + route);
            }
            ListTag outputList = entry.getList(OUTPUTS_TAG, Tag.TAG_COMPOUND);
            if (outputList.isEmpty()) {
                throw new IllegalArgumentException("Persisted pending output route " + route + " has no outputs");
            }
            ArrayList<ItemStack> outputs = new ArrayList<>();
            for (int outputIndex = 0; outputIndex < outputList.size(); outputIndex++) {
                ItemStack output = ItemStack.parseOptional(registries, outputList.getCompound(outputIndex));
                if (output.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Persisted pending output " + outputIndex + " for route " + route + " is empty");
                }
                outputs.add(output);
            }
            loaded.get(route.slot()).put(route, outputs);
        }
        return loaded;
    }

    private int checkedPersistedSlot(CompoundTag entry, Set<Integer> populated, String kind) {
        if (!entry.contains(SLOT_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Persisted " + kind + " entry is missing its slot");
        }
        int slot = entry.getInt(SLOT_TAG);
        checkSlot(slot);
        if (!populated.add(slot)) {
            throw new IllegalArgumentException("Duplicate persisted " + kind + " slot: " + slot);
        }
        return slot;
    }

    private ArrayList<ItemStack> emptyPatternList() {
        ArrayList<ItemStack> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(ItemStack.EMPTY);
        }
        return result;
    }

    private ArrayList<ArrayDeque<TrinityCraftingBatch>> emptyQueueList() {
        ArrayList<ArrayDeque<TrinityCraftingBatch>> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(new ArrayDeque<>());
        }
        return result;
    }

    private ArrayList<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> emptyOutputList() {
        ArrayList<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(new LinkedHashMap<>());
        }
        return result;
    }

    private void appendOutputsWithoutNotification(PatternRoute route, List<ItemStack> outputs) {
        ArrayList<ItemStack> outputGroup = this.pendingOutputs.get(route.slot())
                .computeIfAbsent(route, ignored -> new ArrayList<>());
        appendNonEmptyCopies(outputGroup, outputs);
        if (outputGroup.isEmpty()) {
            this.pendingOutputs.get(route.slot()).remove(route);
        }
    }

    private static void appendNonEmptyCopies(List<ItemStack> destination, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                destination.add(stack.copy());
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        ArrayList<ItemStack> copy = new ArrayList<>(stacks.size());
        appendNonEmptyCopies(copy, stacks);
        return List.copyOf(copy);
    }

    private int pendingOutputStackCount(PatternRoute route) {
        List<ItemStack> outputs = this.pendingOutputs.get(route.slot()).get(route);
        return outputs == null ? 0 : outputs.size();
    }

    private void validateOwnedRoute(PatternRoute route) {
        checkSlot(route.slot());
        if (!this.coreId.equals(route.coreId())) {
            throw new IllegalArgumentException(
                    "Pattern route core " + route.coreId() + " does not match " + this.coreId);
        }
    }

    private void validatePersistedRoute(PatternRoute route, UUID loadedId, int expectedSlot, String kind) {
        checkSlot(route.slot());
        if (!loadedId.equals(route.coreId()) || route.slot() != expectedSlot) {
            throw new IllegalArgumentException(
                    "Persisted " + kind + " route " + route + " does not match core " + loadedId +
                            " slot " + expectedSlot);
        }
    }

    private static ItemStack normalizePattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = pattern.copy();
        normalized.setCount(1);
        return normalized;
    }

    private static boolean containsCoreState(CompoundTag data) {
        return data.contains(PATTERN_CAPACITY_TAG) || data.contains(PATTERNS_TAG) || data.contains(QUEUES_TAG) ||
                data.contains(PENDING_OUTPUTS_TAG);
    }

    private static void validateCapacity(int patternCapacity) {
        if (!SUPPORTED_CAPACITIES.contains(patternCapacity)) {
            throw new IllegalArgumentException(
                    "Trinity pattern core capacity must be one of " + SUPPORTED_CAPACITIES + ", got " + patternCapacity);
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.patternCapacity) {
            throw new IllegalArgumentException(
                    "Trinity pattern core slot out of range: " + slot + " for capacity " + this.patternCapacity);
        }
    }

    private void ensureNoActiveRefundTransaction() {
        if (this.activeRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " is processing a refund");
        }
    }

    private void markPatternCatalogChanged() {
        this.revision = Math.incrementExact(this.revision);
        markPersistentStateChanged();
    }

    private void markPersistentStateChanged() {
        this.stateRevision = Math.incrementExact(this.stateRevision);
        this.changeListener.run();
    }

    /** Immutable private capture used to restore a core after a coordinated host refund aborts. */
    private record RefundState(@Nullable UUID routeHostId,
                               long stateRevision,
                               List<ArrayDeque<TrinityCraftingBatch>> queues,
                               List<LinkedHashMap<PatternRoute, ArrayList<ItemStack>>> pendingOutputs,
                               List<ItemStack> refundableStacks) {}

    /** Reversible state transition for one core participating in an aggregate host refund. */
    private final class CoreRefundTransaction implements RefundTransaction {

        private final RefundState captured;
        private boolean committed;
        private boolean closed;
        private long committedStateRevision = -1L;

        private CoreRefundTransaction(RefundState captured) {
            this.captured = captured;
        }

        @Override
        public List<ItemStack> refundableStacks() {
            return copyStacks(this.captured.refundableStacks());
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || this.stateChangedSincePreparation() ||
                    !matchesRefundState(this.captured)) {
                this.closed = true;
                release();
                return false;
            }
            this.committed = true;
            this.committedStateRevision = Math.incrementExact(this.captured.stateRevision());
            try {
                clearRefundState(this.captured.routeHostId());
                if (TrinityPatternCoreImpl.this.stateRevision != this.committedStateRevision) {
                    throw new IllegalStateException(
                            "Trinity pattern core " + TrinityPatternCoreImpl.this.coreId +
                                    " changed while committing a refund");
                }
                return true;
            } catch (RuntimeException exception) {
                try {
                    if (TrinityPatternCoreImpl.this.stateRevision == this.captured.stateRevision() ||
                            TrinityPatternCoreImpl.this.stateRevision == this.committedStateRevision) {
                        restoreRefundState(this.captured);
                    } else {
                        Data_Energistics.LOGGER.error(
                                "Cannot roll back Trinity pattern core {} refund after a commit error because queued state changed",
                                TrinityPatternCoreImpl.this.coreId,
                                exception);
                    }
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                this.committed = false;
                this.closed = true;
                release();
                throw exception;
            }
        }

        @Override
        public void complete() {
            if (this.closed) {
                return;
            }
            this.committed = false;
            this.closed = true;
            release();
        }

        @Override
        public void rollback() {
            if (this.closed) {
                return;
            }
            try {
                if (this.committed) {
                    if (TrinityPatternCoreImpl.this.stateRevision == this.committedStateRevision) {
                        restoreRefundState(this.captured);
                    } else {
                        Data_Energistics.LOGGER.error(
                                "Cannot roll back Trinity pattern core {} refund because queued state changed after commit",
                                TrinityPatternCoreImpl.this.coreId);
                    }
                }
            } finally {
                this.committed = false;
                this.closed = true;
                release();
            }
        }

        private boolean stateChangedSincePreparation() {
            return TrinityPatternCoreImpl.this.stateRevision != this.captured.stateRevision();
        }

        private void release() {
            if (TrinityPatternCoreImpl.this.activeRefundTransaction == this) {
                TrinityPatternCoreImpl.this.activeRefundTransaction = null;
            }
        }
    }

    private final class PatternInventory extends AppEngInternalInventory {

        private PatternInventory() {
            super(TrinityPatternCoreImpl.this.patternCapacity);
        }

        @Override
        public int getSlotLimit(int slot) {
            checkSlot(slot);
            return 1;
        }

        @Override
        public int size() {
            return TrinityPatternCoreImpl.this.patternCapacity;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return pattern(slotIndex);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            checkSlot(slot);
            return !stack.isEmpty() && TrinityPatternCoreImpl.this.decoder.decode(normalizePattern(stack)) != null;
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            trySetPattern(slotIndex, stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            checkSlot(slot);
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = TrinityPatternCoreImpl.this.patterns.get(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = current.copy();
            if (!simulate) {
                trySetPattern(slot, ItemStack.EMPTY);
            }
            return extracted;
        }
    }
}
