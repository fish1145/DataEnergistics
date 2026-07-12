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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Default state implementation for {@link TrinityPatternCore}.
 *
 * <p>
 * The owning block entity supplies decoding, recipe identity resolution, and typed change callbacks. Each physical
 * slot owns its stable definition table, counted FIFO, and counted pending outputs; this core owns only cross-slot
 * publication, sparse indexes, refund transactions, and atomic V1/V2 persistence.
 * </p>
 */
public final class TrinityPatternCoreImpl implements TrinityPatternCore {

    private static final Set<Integer> SUPPORTED_CAPACITIES = Set.of(64, 128, 512);
    private static final int STATE_VERSION = 2;
    private static final String BATCHES_TAG = "batches";
    private static final String CORE_ID_TAG = "core_id";
    private static final String OUTPUTS_TAG = "outputs";
    private static final String PATTERN_CAPACITY_TAG = "pattern_capacity";
    private static final String PATTERNS_TAG = "patterns";
    private static final String PENDING_OUTPUTS_TAG = "pending_outputs";
    private static final String QUEUES_TAG = "queues";
    private static final String ROUTE_TAG = "route";
    private static final String SLOT_TAG = "slot";
    private static final String SLOTS_TAG = "slots";
    private static final String STACK_TAG = "stack";
    private static final String VERSION_TAG = "version";

    private final int patternCapacity;
    private final PatternDecoder decoder;
    private final TrinityPatternRecipeIdResolvers recipeIdResolvers;
    private final TrinityPatternSlot.ChangeListener changeListener;
    private final List<TrinityPatternSlotImpl> slots;
    /**
     * Slot-ordered decoded recipes used to publish a core snapshot without rescanning empty capacity.
     */
    private final TreeMap<Integer, CachedPattern> cachedPatterns = new TreeMap<>();
    /**
     * Slots with at least one queued group, ordered to preserve deterministic execution.
     */
    private final TreeSet<Integer> queuedSlots = new TreeSet<>();
    /**
     * Slots with at least one pending output route, ordered for sparse persistence and routing.
     */
    private final TreeSet<Integer> pendingOutputSlots = new TreeSet<>();
    /**
     * Per-host working physical slots combine queued inputs and pending outputs for sparse host scans.
     */
    private final Map<UUID, TreeSet<Integer>> workingSlotsByHost = new HashMap<>();
    /**
     * Per-host output slot indexes isolate sleeping routes after a movable core changes hosts.
     */
    private final Map<UUID, TreeSet<Integer>> pendingOutputSlotsByHost = new HashMap<>();
    private final InternalInventory patternInventory = new PatternInventory();

    private UUID coreId;
    private long revision;
    private long stateRevision;
    /**
     * Latest immutable publication view; replaced atomically after each catalog revision.
     */
    private PatternCacheSnapshot patternCacheSnapshot = new PatternCacheSnapshot(0L, List.of());
    private boolean bulkRefreshing;
    @Nullable
    private CoreRefundTransaction activeRefundTransaction;

    /**
     * Creates a fresh pattern core with explicit identity resolution and typed changes.
     *
     * @param patternCapacity   one of the three physical P-core capacities
     * @param decoder           level-aware supported-pattern decoder
     * @param recipeIdResolvers registry used to resolve stable recipe identities
     * @param changeListener    typed owner callback
     */
    public TrinityPatternCoreImpl(int patternCapacity, PatternDecoder decoder,
                                  TrinityPatternRecipeIdResolvers recipeIdResolvers,
                                  TrinityPatternSlot.ChangeListener changeListener) {
        this(patternCapacity, UUID.randomUUID(), decoder, recipeIdResolvers, changeListener);
    }

    /**
     * Creates a pattern core with explicit identity, resolver registry, and typed changes.
     *
     * @param patternCapacity   one of the three physical P-core capacities
     * @param coreId            persistent identity
     * @param decoder           level-aware supported-pattern decoder
     * @param recipeIdResolvers registry used to resolve stable recipe identities
     * @param changeListener    typed owner callback
     */
    public TrinityPatternCoreImpl(int patternCapacity, UUID coreId, PatternDecoder decoder,
                                  TrinityPatternRecipeIdResolvers recipeIdResolvers,
                                  TrinityPatternSlot.ChangeListener changeListener) {
        validateCapacity(patternCapacity);
        this.patternCapacity = patternCapacity;
        this.coreId = coreId;
        this.decoder = decoder;
        this.recipeIdResolvers = recipeIdResolvers;
        this.changeListener = changeListener;
        this.slots = new ArrayList<>(patternCapacity);
        for (int slot = 0; slot < patternCapacity; slot++) {
            this.slots.add(newSlot(slot));
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
    public TrinityPatternSlot patternSlot(int slot) {
        return slot(slot);
    }

    @Override
    public long revision() {
        return this.revision;
    }

    @Override
    public PatternCacheSnapshot patternCacheSnapshot() {
        return this.patternCacheSnapshot;
    }

    @Override
    public InternalInventory patternInventory() {
        return this.patternInventory;
    }

    @Override
    public ItemStack pattern(int slot) {
        return slot(slot).pattern();
    }

    @Override
    public boolean trySetPattern(int slot, ItemStack pattern) {
        ensureNoActiveRefundTransaction();
        return slot(slot).trySetPattern(pattern);
    }

    @Nullable
    @Override
    public IMolecularAssemblerSupportedPattern decodedPattern(int slot) {
        return slot(slot).decodedPattern();
    }

    @Override
    public void refreshPatternCache(int slot) {
        ensureNoActiveRefundTransaction();
        slot(slot).refreshPatternCache();
    }

    @Override
    public void refreshAllPatternCaches() {
        ensureNoActiveRefundTransaction();
        this.bulkRefreshing = true;
        try {
            for (TrinityPatternSlotImpl slot : this.slots) {
                slot.refreshPatternCache();
            }
        } finally {
            this.bulkRefreshing = false;
        }
        markCatalogChanged(0);
    }

    @Override
    public void ensurePatternCachesCurrent() {
        ensureNoActiveRefundTransaction();
    }

    @Override
    public boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        return slot(route.slot()).enqueue(route, patternSnapshot, inputs, queuedTick);
    }

    @Override
    public List<TrinityCraftingBatch> queuedBatches(int slot) {
        return slot(slot).queuedBatches();
    }

    @Override
    public int queuedBatchCount(int slot) {
        return slot(slot).queuedBatchCount();
    }

    @Override
    public int queuedBatchCount() {
        int count = 0;
        for (int slot : this.queuedSlots) {
            count = Math.addExact(count, this.slots.get(slot).queuedBatchCount());
        }
        return count;
    }

    @Override
    public int executeReadyBatches(long currentTick, BatchExecutor executor) {
        ensureNoActiveRefundTransaction();
        validateCurrentTick(currentTick);
        int completedGroups = 0;
        for (int slotIndex : List.copyOf(this.queuedSlots)) {
            completedGroups = Math.addExact(
                    completedGroups,
                    executeReadyBatchesInSlot(slotIndex, currentTick, executor));
        }
        return completedGroups;
    }

    @Override
    public int executeReadyBatches(int slot, long currentTick, BatchExecutor executor) {
        ensureNoActiveRefundTransaction();
        checkSlot(slot);
        validateCurrentTick(currentTick);
        return executeReadyBatchesInSlot(slot, currentTick, executor);
    }

    @Override
    public List<TrinityItemAmount> pendingOutputs(PatternRoute route) {
        validateOwnedRoute(route);
        return slot(route.slot()).pendingOutputs(route);
    }

    @Override
    public void appendPendingOutputs(PatternRoute route, List<TrinityItemAmount> outputs) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        slot(route.slot()).appendPendingOutputs(route, outputs);
    }

    @Override
    public TrinityPatternOutputRouter.PendingOutputCursor openPendingOutputCursor(PatternRoute route) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        return slot(route.slot()).openPendingOutputCursor(route);
    }

    @Override
    public List<Integer> pendingOutputSlots(UUID hostId) {
        TreeSet<Integer> slots = this.pendingOutputSlotsByHost.get(hostId);
        return slots == null ? List.of() : List.copyOf(slots);
    }

    @Override
    public List<Integer> workingSlots(UUID hostId) {
        TreeSet<Integer> slots = this.workingSlotsByHost.get(hostId);
        return slots == null ? List.of() : List.copyOf(slots);
    }

    @Override
    public boolean isSlotWorking(UUID hostId, int slot) {
        checkSlot(slot);
        TreeSet<Integer> slots = this.workingSlotsByHost.get(hostId);
        return slots != null && slots.contains(slot);
    }

    @Override
    public boolean hasWork() {
        return !this.queuedSlots.isEmpty() || !this.pendingOutputSlots.isEmpty();
    }

    @Override
    public boolean hasWork(UUID hostId) {
        return this.workingSlotsByHost.containsKey(hostId);
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
        List<TrinityItemAmount> refundable = transaction.refundableItems();
        try {
            if (refundable.isEmpty() || !delivery.prepare(refundable)) {
                transaction.rollback();
                return false;
            }
            if (!transaction.commit()) {
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

    @Override
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        data.putInt(VERSION_TAG, STATE_VERSION);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        ListTag slotEntries = new ListTag();
        for (TrinityPatternSlotImpl slot : this.slots) {
            if (slot.hasPersistentState()) {
                slotEntries.add(slot.writeV2(registries));
            }
        }
        data.put(SLOTS_TAG, slotEntries);
        data.remove(PATTERNS_TAG);
        data.remove(QUEUES_TAG);
        data.remove(PENDING_OUTPUTS_TAG);
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
        validatePersistedCapacity(data);
        UUID loadedId = data.getUUID(CORE_ID_TAG);
        List<TrinityPatternSlotImpl> loadedSlots;
        if (data.contains(VERSION_TAG)) {
            if (!data.contains(VERSION_TAG, Tag.TAG_INT) || data.getInt(VERSION_TAG) != STATE_VERSION) {
                throw new IllegalArgumentException("Unsupported Trinity pattern core state version");
            }
            if (data.contains(PATTERNS_TAG) || data.contains(QUEUES_TAG) || data.contains(PENDING_OUTPUTS_TAG)) {
                throw new IllegalArgumentException("V2 Trinity pattern core state must not contain legacy state lists");
            }
            loadedSlots = readV2Slots(requiredCompoundList(data, SLOTS_TAG), registries, loadedId);
        } else {
            if (data.contains(SLOTS_TAG)) {
                throw new IllegalArgumentException("V1 Trinity pattern core state must not contain V2 slots");
            }
            loadedSlots = migrateV1Slots(
                    requiredCompoundList(data, PATTERNS_TAG),
                    requiredCompoundList(data, QUEUES_TAG),
                    registries,
                    loadedId);
            List<LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> loadedOutputs = readV1PendingOutputs(
                    requiredCompoundList(data, PENDING_OUTPUTS_TAG), registries, loadedId);
            for (int slot = 0; slot < this.patternCapacity; slot++) {
                loadedSlots.get(slot).loadMigratedPendingOutputs(loadedOutputs.get(slot));
            }
        }

        if (this.revision == Long.MAX_VALUE || this.stateRevision == Long.MAX_VALUE) {
            throw new ArithmeticException("Trinity pattern core revision overflow");
        }
        WorkIndexes loadedIndexes = createWorkIndexes(loadedSlots);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.slots.get(slot).ensureCanApplyValidatedState(loadedSlots.get(slot));
        }
        this.coreId = loadedId;
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.slots.get(slot).applyValidatedState(loadedSlots.get(slot));
        }
        this.cachedPatterns.clear();
        this.revision++;
        rebuildPatternCacheSnapshot();
        applyWorkIndexes(loadedIndexes);
        this.stateRevision++;
    }

    private RefundState captureRefundState(@Nullable UUID routeHostId) {
        ArrayList<TrinityItemAmount> refundable = new ArrayList<>();
        ArrayList<TrinityPatternSlotImpl.WorkState> capturedSlots = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            TrinityPatternSlotImpl.WorkState captured = this.slots.get(slot).captureWorkState();
            capturedSlots.add(captured);
            for (TrinityCraftingBatch batch : captured.batches()) {
                if (routeHostId == null || routeHostId.equals(batch.route().hostId())) {
                    for (ItemStack input : batch.inputs()) {
                        if (!input.isEmpty()) {
                            refundable.addAll(TrinityItemAmount.multiply(input, batch.count()));
                        }
                    }
                }
            }
            for (Map.Entry<PatternRoute, List<TrinityItemAmount>> entry : captured.pendingOutputs().entrySet()) {
                if (routeHostId == null || routeHostId.equals(entry.getKey().hostId())) {
                    refundable.addAll(entry.getValue());
                }
            }
        }
        return new RefundState(
                routeHostId,
                this.stateRevision,
                List.copyOf(capturedSlots),
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
        for (TrinityPatternSlotImpl slot : this.slots) {
            slot.clearRefundableWork(routeHostId);
        }
        rebuildWorkIndexes();
    }

    private boolean matchesRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            if (!this.slots.get(slot).matchesWorkState(captured.slots().get(slot))) {
                return false;
            }
        }
        return true;
    }

    private void restoreRefundState(RefundState captured) {
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            this.slots.get(slot).restoreWorkState(captured.slots().get(slot));
        }
        rebuildWorkIndexes();
    }

    private List<TrinityPatternSlotImpl> readV2Slots(ListTag entries, HolderLookup.Provider registries, UUID loadedId) {
        ArrayList<TrinityPatternSlotImpl> loaded = emptySlotList();
        Set<Integer> populated = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            TrinityPatternSlotImpl slot = TrinityPatternSlotImpl.readV2(
                    entries.getCompound(index), this.decoder, this.recipeIdResolvers, change -> {}, registries);
            checkSlot(slot.index());
            if (!populated.add(slot.index())) {
                throw new IllegalArgumentException("Duplicate V2 Trinity pattern slot " + slot.index());
            }
            for (TrinityCraftingBatch batch : slot.queuedBatches()) {
                validatePersistedRoute(batch.route(), loadedId, slot.index(), "queued group");
            }
            for (PatternRoute route : slot.pendingOutputRoutes()) {
                validatePersistedRoute(route, loadedId, slot.index(), "pending output");
            }
            loaded.set(slot.index(), slot);
        }
        return loaded;
    }

    private List<TrinityPatternSlotImpl> migrateV1Slots(ListTag patternEntries, ListTag queueEntries,
                                                        HolderLookup.Provider registries, UUID loadedId) {
        ArrayList<ItemStack> patterns = emptyPatternList();
        Set<Integer> populatedPatterns = new HashSet<>();
        for (int index = 0; index < patternEntries.size(); index++) {
            CompoundTag entry = patternEntries.getCompound(index);
            int slot = checkedPersistedSlot(entry, populatedPatterns, "pattern");
            ItemStack pattern = normalizePattern(ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG)));
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("Persisted V1 pattern slot " + slot + " is empty");
            }
            patterns.set(slot, pattern);
        }

        ArrayList<List<TrinityCraftingBatch.V1Data>> queues = emptyV1QueueList();
        Set<Integer> populatedQueues = new HashSet<>();
        for (int index = 0; index < queueEntries.size(); index++) {
            CompoundTag entry = queueEntries.getCompound(index);
            int slot = checkedPersistedSlot(entry, populatedQueues, "queue");
            ListTag batches = requiredCompoundList(entry, BATCHES_TAG);
            if (batches.isEmpty()) {
                throw new IllegalArgumentException("Persisted V1 queue slot " + slot + " has no batches");
            }
            ArrayList<TrinityCraftingBatch.V1Data> migrated = new ArrayList<>(batches.size());
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                TrinityCraftingBatch.V1Data v1 = TrinityCraftingBatch.readV1(
                        batches.getCompound(batchIndex), registries);
                validatePersistedRoute(v1.route(), loadedId, slot, "V1 queued batch");
                migrated.add(v1);
            }
            queues.set(slot, List.copyOf(migrated));
        }

        ArrayList<TrinityPatternSlotImpl> loaded = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            loaded.add(TrinityPatternSlotImpl.migrateV1(
                    slot, patterns.get(slot), queues.get(slot), this.decoder, this.recipeIdResolvers,
                    change -> {}));
        }
        return loaded;
    }

    private List<LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> readV1PendingOutputs(
                                                                                            ListTag entries, HolderLookup.Provider registries, UUID loadedId) {
        ArrayList<LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> loaded = emptyV1OutputList();
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
            ListTag outputList = requiredCompoundList(entry, OUTPUTS_TAG);
            if (outputList.isEmpty()) {
                throw new IllegalArgumentException("Persisted pending output route " + route + " has no outputs");
            }
            ArrayList<TrinityItemAmount> outputs = new ArrayList<>();
            for (int outputIndex = 0; outputIndex < outputList.size(); outputIndex++) {
                ItemStack output = ItemStack.parseOptional(registries, outputList.getCompound(outputIndex));
                if (output.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Persisted pending output " + outputIndex + " for route " + route + " is empty");
                }
                outputs.add(TrinityItemAmount.of(output));
            }
            loaded.get(route.slot()).put(route, List.copyOf(outputs));
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

    private ArrayList<TrinityPatternSlotImpl> emptySlotList() {
        ArrayList<TrinityPatternSlotImpl> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(new TrinityPatternSlotImpl(
                    slot, this.decoder, this.recipeIdResolvers, change -> {}));
        }
        return result;
    }

    private ArrayList<ItemStack> emptyPatternList() {
        ArrayList<ItemStack> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(ItemStack.EMPTY);
        }
        return result;
    }

    private ArrayList<List<TrinityCraftingBatch.V1Data>> emptyV1QueueList() {
        ArrayList<List<TrinityCraftingBatch.V1Data>> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(List.of());
        }
        return result;
    }

    private ArrayList<LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> emptyV1OutputList() {
        ArrayList<LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> result = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            result.add(new LinkedHashMap<>());
        }
        return result;
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

    private void validatePersistedCapacity(CompoundTag data) {
        if (!data.contains(PATTERN_CAPACITY_TAG, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its capacity");
        }
        int persistedCapacity = data.getInt(PATTERN_CAPACITY_TAG);
        if (persistedCapacity != this.patternCapacity) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core capacity " + persistedCapacity +
                            " does not match block capacity " + this.patternCapacity);
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
        return data.contains(VERSION_TAG) || data.contains(PATTERN_CAPACITY_TAG) || data.contains(SLOTS_TAG) ||
                data.contains(PATTERNS_TAG) || data.contains(QUEUES_TAG) || data.contains(PENDING_OUTPUTS_TAG);
    }

    private static ListTag requiredCompoundList(CompoundTag data, String tagName) {
        if (!(data.get(tagName) instanceof ListTag entries) ||
                !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core state requires compound list '" + tagName + "'");
        }
        return entries;
    }

    private static void validateCapacity(int patternCapacity) {
        if (!SUPPORTED_CAPACITIES.contains(patternCapacity)) {
            throw new IllegalArgumentException(
                    "Trinity pattern core capacity must be one of " + SUPPORTED_CAPACITIES + ", got " + patternCapacity);
        }
    }

    private TrinityPatternSlotImpl slot(int slot) {
        checkSlot(slot);
        return this.slots.get(slot);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.patternCapacity) {
            throw new IllegalArgumentException(
                    "Trinity pattern core slot out of range: " + slot + " for capacity " + this.patternCapacity);
        }
    }

    private static void validateCurrentTick(long currentTick) {
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Current tick must not be negative: " + currentTick);
        }
    }

    private int executeReadyBatchesInSlot(int slotIndex, long currentTick, BatchExecutor executor) {
        TrinityPatternSlotImpl slot = this.slots.get(slotIndex);
        int completedGroups = 0;
        while (slot.hasQueuedWork()) {
            TrinityCraftingBatch batch = slot.readyHead(currentTick);
            if (batch == null) {
                break;
            }
            BatchExecutionResult result = executor.execute(slotIndex, batch);
            if (!result.completed()) {
                break;
            }
            ArrayList<TrinityItemAmount> countedOutputs = new ArrayList<>();
            for (ItemStack output : result.outputs()) {
                countedOutputs.addAll(TrinityItemAmount.multiply(output, batch.count()));
            }
            slot.completeHead(batch, countedOutputs);
            completedGroups = Math.incrementExact(completedGroups);
        }
        return completedGroups;
    }

    private TrinityPatternSlotImpl newSlot(int slot) {
        return new TrinityPatternSlotImpl(slot, this.decoder, this.recipeIdResolvers, this::onSlotChanged);
    }

    private void ensureNoActiveRefundTransaction() {
        if (this.activeRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " is processing a refund");
        }
    }

    private void onSlotChanged(TrinityPatternSlot.Change change) {
        switch (change.kind()) {
            case CATALOG -> {
                updateCachedPattern(change.slot());
                if (!this.bulkRefreshing) {
                    markCatalogChanged(change.slot());
                }
            }
            case WORK -> updateSlotWorkIndexes(change.slot());
            case PERSISTENT -> markPersistentChanged(change.slot());
        }
    }

    private void updateCachedPattern(int slot) {
        IMolecularAssemblerSupportedPattern decoded = this.slots.get(slot).decodedPattern();
        if (decoded == null) {
            this.cachedPatterns.remove(slot);
        } else {
            this.cachedPatterns.put(slot, new CachedPattern(slot, decoded));
        }
    }

    private void markCatalogChanged(int slot) {
        this.revision = Math.incrementExact(this.revision);
        rebuildPatternCacheSnapshot();
        this.changeListener.onChanged(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.CATALOG));
    }

    private void rebuildPatternCacheSnapshot() {
        this.patternCacheSnapshot = new PatternCacheSnapshot(
                this.revision,
                new ArrayList<>(this.cachedPatterns.values()));
    }

    private void updateSlotWorkIndexes(int slot) {
        Set<UUID> previousWorkHosts = removeIndexedSlot(this.workingSlotsByHost, slot);
        TrinityPatternSlotImpl patternSlot = this.slots.get(slot);
        if (patternSlot.hasQueuedWork()) {
            this.queuedSlots.add(slot);
        } else {
            this.queuedSlots.remove(slot);
        }
        removeIndexedSlot(this.pendingOutputSlotsByHost, slot);
        if (patternSlot.hasPendingOutputs()) {
            this.pendingOutputSlots.add(slot);
            for (UUID hostId : patternSlot.pendingOutputHostIds()) {
                this.pendingOutputSlotsByHost
                        .computeIfAbsent(hostId, ignored -> new TreeSet<>())
                        .add(slot);
            }
        } else {
            this.pendingOutputSlots.remove(slot);
        }
        Set<UUID> currentWorkHosts = patternSlot.workHostIds();
        for (UUID hostId : currentWorkHosts) {
            this.workingSlotsByHost.computeIfAbsent(hostId, ignored -> new TreeSet<>()).add(slot);
        }
        if (!previousWorkHosts.equals(currentWorkHosts)) {
            this.changeListener.onChanged(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.WORK));
        }
    }

    private static Set<UUID> removeIndexedSlot(Map<UUID, TreeSet<Integer>> index, int slot) {
        HashSet<UUID> removedHosts = new HashSet<>();
        index.entrySet().removeIf(entry -> {
            if (!entry.getValue().remove(slot)) {
                return false;
            }
            removedHosts.add(entry.getKey());
            return entry.getValue().isEmpty();
        });
        return Set.copyOf(removedHosts);
    }

    private void rebuildWorkIndexes() {
        applyWorkIndexes(createWorkIndexes(this.slots));
    }

    private WorkIndexes createWorkIndexes(List<TrinityPatternSlotImpl> sourceSlots) {
        TreeSet<Integer> loadedQueuedSlots = new TreeSet<>();
        TreeSet<Integer> loadedPendingOutputSlots = new TreeSet<>();
        HashMap<UUID, TreeSet<Integer>> loadedWorkingSlotsByHost = new HashMap<>();
        HashMap<UUID, TreeSet<Integer>> loadedPendingSlotsByHost = new HashMap<>();
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            TrinityPatternSlotImpl patternSlot = sourceSlots.get(slot);
            if (patternSlot.hasQueuedWork()) {
                loadedQueuedSlots.add(slot);
            }
            if (patternSlot.hasPendingOutputs()) {
                loadedPendingOutputSlots.add(slot);
                for (UUID hostId : patternSlot.pendingOutputHostIds()) {
                    loadedPendingSlotsByHost
                            .computeIfAbsent(hostId, ignored -> new TreeSet<>())
                            .add(slot);
                }
            }
            for (UUID hostId : patternSlot.workHostIds()) {
                loadedWorkingSlotsByHost.computeIfAbsent(hostId, ignored -> new TreeSet<>()).add(slot);
            }
        }
        return new WorkIndexes(
                loadedQueuedSlots,
                loadedPendingOutputSlots,
                loadedWorkingSlotsByHost,
                loadedPendingSlotsByHost);
    }

    private void applyWorkIndexes(WorkIndexes indexes) {
        this.queuedSlots.clear();
        this.queuedSlots.addAll(indexes.queuedSlots());
        this.pendingOutputSlots.clear();
        this.pendingOutputSlots.addAll(indexes.pendingOutputSlots());
        this.workingSlotsByHost.clear();
        for (Map.Entry<UUID, TreeSet<Integer>> entry : indexes.workingSlotsByHost().entrySet()) {
            this.workingSlotsByHost.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
        this.pendingOutputSlotsByHost.clear();
        for (Map.Entry<UUID, TreeSet<Integer>> entry : indexes.pendingSlotsByHost().entrySet()) {
            this.pendingOutputSlotsByHost.put(entry.getKey(), new TreeSet<>(entry.getValue()));
        }
    }

    private void markPersistentChanged(int slot) {
        this.stateRevision = Math.incrementExact(this.stateRevision);
        this.changeListener.onChanged(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.PERSISTENT));
    }

    /**
     * Immutable private capture used to restore a core after a coordinated host refund aborts.
     */
    private record RefundState(@Nullable UUID routeHostId,
                               long stateRevision,
                               List<TrinityPatternSlotImpl.WorkState> slots,
                               List<TrinityItemAmount> refundableItems) {}

    /**
     * Detached sparse-index snapshot validated before an atomic load or refund restore mutates live state.
     */
    private record WorkIndexes(TreeSet<Integer> queuedSlots,
                               TreeSet<Integer> pendingOutputSlots,
                               Map<UUID, TreeSet<Integer>> workingSlotsByHost,
                               Map<UUID, TreeSet<Integer>> pendingSlotsByHost) {}

    /**
     * Reversible state transition for one core participating in an aggregate host refund.
     */
    private final class CoreRefundTransaction implements RefundTransaction {

        private final RefundState captured;
        private boolean committed;
        private boolean closed;
        private long committedStateRevision = -1L;

        private CoreRefundTransaction(RefundState captured) {
            this.captured = captured;
        }

        @Override
        public List<TrinityItemAmount> refundableItems() {
            return List.copyOf(this.captured.refundableItems());
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || stateChangedSincePreparation() || !matchesRefundState(this.captured)) {
                this.closed = true;
                release();
                return false;
            }
            try {
                clearRefundState(this.captured.routeHostId());
                this.committed = true;
                this.committedStateRevision = TrinityPatternCoreImpl.this.stateRevision;
                return true;
            } catch (RuntimeException exception) {
                try {
                    restoreRefundState(this.captured);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to restore Trinity pattern core {} after refund commit failure",
                            TrinityPatternCoreImpl.this.coreId,
                            rollbackFailure);
                }
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

    /**
     * Fixed-size AE2 menu inventory backed by stable slot models.
     */
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
            return TrinityPatternCoreImpl.this.slot(slot).acceptsPattern(stack);
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
            ItemStack current = TrinityPatternCoreImpl.this.slots.get(slot).pattern();
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                trySetPattern(slot, ItemStack.EMPTY);
            }
            return current;
        }
    }
}
