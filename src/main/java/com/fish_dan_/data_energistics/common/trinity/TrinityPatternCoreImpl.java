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
    /** Constant-time slot lookup for stable occupied-directory entries. */
    private Map<Integer, CachedPattern> cachedPatterns = new HashMap<>();
    /**
     * Slots retaining an encoded pattern, ordered for reload and persistence without a capacity scan.
     */
    private TreeSet<Integer> occupiedPatternSlots = new TreeSet<>();
    /**
     * Slots with at least one queued group, ordered to preserve deterministic execution.
     */
    private TreeSet<Integer> queuedSlots = new TreeSet<>();
    /**
     * Slots with at least one pending output route, ordered for sparse persistence and routing.
     */
    private TreeSet<Integer> pendingOutputSlots = new TreeSet<>();
    /**
     * Per-host working physical slots combine queued inputs and pending outputs for sparse host scans.
     */
    private Map<UUID, TreeSet<Integer>> workingSlotsByHost = new HashMap<>();
    /**
     * Per-host output slot indexes isolate sleeping routes after a movable core changes hosts.
     */
    private Map<UUID, TreeSet<Integer>> pendingOutputSlotsByHost = new HashMap<>();
    private final InternalInventory patternInventory = new PatternInventory();

    private UUID coreId;
    private long revision;
    private long stateRevision;
    /**
     * Latest structurally immutable occupied directory; runtime bindings change through stable entries.
     */
    private PatternCacheSnapshot patternCacheSnapshot = new PatternCacheSnapshot(0L, List.of());
    /** Latest immutable occupied-slot index, replaced only with the pattern directory. */
    private List<Integer> occupiedPatternSlotSnapshot = List.of();
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

    @Nullable
    @Override
    public CachedPattern cachedPattern(int slot) {
        checkSlot(slot);
        return this.cachedPatterns.get(slot);
    }

    @Override
    public List<Integer> occupiedPatternSlots() {
        return this.occupiedPatternSlotSnapshot;
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
        for (int slot : this.occupiedPatternSlots) {
            this.slots.get(slot).refreshPatternCache();
        }
    }

    @Override
    public void ensurePatternCachesCurrent() {
        ensureNoActiveRefundTransaction();
    }

    @Override
    public boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        ensurePersistentRevisionAvailable();
        return slot(route.slot()).enqueue(route, patternSnapshot, inputs, queuedTick);
    }

    @Override
    public boolean enqueueBatch(PatternRoute route,
                                CachedPattern expectedPattern,
                                long expectedRuntimeBindingRevision,
                                TrinityCraftingBatch.InputSignature inputs,
                                long queuedTick,
                                long count) {
        ensureNoActiveRefundTransaction();
        validateOwnedRoute(route);
        TrinityPatternSlotImpl slot = slot(route.slot());
        if (this.cachedPatterns.get(route.slot()) != expectedPattern ||
                expectedPattern.runtimeBindingRevision() != expectedRuntimeBindingRevision) {
            return false;
        }
        ensurePersistentRevisionAvailable();
        return slot.enqueueCached(route, expectedPattern.definition(), inputs, queuedTick, count);
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
        for (int slot : persistentSlots()) {
            slotEntries.add(this.slots.get(slot).writeV2(registries));
        }
        data.put(SLOTS_TAG, slotEntries);
        data.remove(PATTERNS_TAG);
        data.remove(QUEUES_TAG);
        data.remove(PENDING_OUTPUTS_TAG);
    }

    @Override
    public void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries) {
        if (this.stateRevision != 0L || this.revision != 0L || !persistentSlots().isEmpty()) {
            throw new IllegalStateException("Only a pristine Trinity pattern core may hydrate persisted state");
        }
        applyPersistedState(data, registries, false);
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        applyPersistedState(data, registries, true);
    }

    private void applyPersistedState(CompoundTag data, HolderLookup.Provider registries, boolean notifyChanges) {
        ensureNoActiveRefundTransaction();
        if (!data.hasUUID(CORE_ID_TAG)) {
            if (containsCoreState(data)) {
                throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its UUID");
            }
            return;
        }
        validatePersistedCapacity(data);
        UUID loadedId = data.getUUID(CORE_ID_TAG);
        boolean identityChanged = !this.coreId.equals(loadedId);
        Map<Integer, TrinityPatternSlotImpl> loadedSlots;
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
            Map<Integer, LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> loadedOutputs = readV1PendingOutputs(
                    requiredCompoundList(data, PENDING_OUTPUTS_TAG), registries, loadedId);
            for (Map.Entry<Integer, LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> entry : loadedOutputs.entrySet()) {
                loadedSlots.computeIfAbsent(entry.getKey(), this::newDetachedSlot)
                        .loadMigratedPendingOutputs(entry.getValue());
            }
        }

        TreeSet<Integer> loadedOccupiedSlots = occupiedSlots(loadedSlots.values());
        TreeSet<Integer> changedPatternSlots = changedPatternSlots(loadedSlots, loadedOccupiedSlots);
        boolean patternDirectoryChanged = !changedPatternSlots.isEmpty();
        long nextDirectoryRevision = patternDirectoryChanged ? Math.incrementExact(this.revision) : this.revision;
        long nextStateRevision = Math.incrementExact(this.stateRevision);
        WorkIndexes loadedIndexes = createWorkIndexes(loadedSlots.values());
        TreeSet<Integer> changedSlots = persistentSlots();
        changedSlots.addAll(loadedSlots.keySet());
        TreeSet<Integer> changedWorkSlots = new TreeSet<>();
        ArrayList<SlotApplication> slotApplications = new ArrayList<>(changedSlots.size());
        for (int slot : changedSlots) {
            TrinityPatternSlotImpl loadedSlot = loadedSlots.computeIfAbsent(slot, this::newDetachedSlot);
            TrinityPatternSlotImpl targetSlot = this.slots.get(slot);
            targetSlot.ensureCanApplyValidatedState(loadedSlot);
            slotApplications.add(new SlotApplication(targetSlot, loadedSlot));
            if (!targetSlot.matchesWorkMembership(loadedSlot)) {
                changedWorkSlots.add(slot);
            }
        }

        Map<Integer, CachedPattern> nextCachedPatterns = new HashMap<>(this.cachedPatterns);
        ArrayList<CachedRebind> preparedRebinds = new ArrayList<>(loadedOccupiedSlots.size());
        TreeSet<Integer> changedRuntimeBindings = new TreeSet<>();
        for (int slot : changedPatternSlots) {
            TrinityPatternSlotImpl loadedSlot = loadedSlots.get(slot);
            if (loadedSlot.pattern().isEmpty()) {
                nextCachedPatterns.remove(slot);
            } else {
                nextCachedPatterns.put(slot, new CachedPattern(
                        slot,
                        loadedSlot.requiredInstalledDefinition(),
                        loadedSlot.decodedPattern()));
            }
        }
        for (int slot : loadedOccupiedSlots) {
            if (!changedPatternSlots.contains(slot)) {
                CachedPattern cached = nextCachedPatterns.get(slot);
                if (cached == null) {
                    throw new IllegalStateException("Missing cached Trinity pattern for retained slot " + slot);
                }
                TrinityPatternSlotImpl loadedSlot = loadedSlots.get(slot);
                CachedPattern.PreparedRebind prepared = cached.prepareRebind(
                        loadedSlot.requiredInstalledDefinition(),
                        loadedSlot.decodedPattern());
                preparedRebinds.add(new CachedRebind(cached, prepared));
                if (prepared.semanticChange()) {
                    changedRuntimeBindings.add(slot);
                }
            }
        }

        List<Integer> nextOccupiedSlotSnapshot = this.occupiedPatternSlotSnapshot;
        PatternCacheSnapshot nextPatternCacheSnapshot = this.patternCacheSnapshot;
        if (patternDirectoryChanged) {
            nextOccupiedSlotSnapshot = List.copyOf(loadedOccupiedSlots);
            ArrayList<CachedPattern> orderedPatterns = new ArrayList<>(nextOccupiedSlotSnapshot.size());
            for (int slot : nextOccupiedSlotSnapshot) {
                CachedPattern cached = nextCachedPatterns.get(slot);
                if (cached == null) {
                    throw new IllegalStateException("Missing cached Trinity pattern for occupied slot " + slot);
                }
                orderedPatterns.add(cached);
            }
            nextPatternCacheSnapshot = new PatternCacheSnapshot(nextDirectoryRevision, orderedPatterns);
        }

        for (SlotApplication application : slotApplications) {
            application.target().applyValidatedState(application.loaded());
        }
        for (CachedRebind rebind : preparedRebinds) {
            rebind.target().commitRebind(rebind.prepared());
        }
        this.cachedPatterns = nextCachedPatterns;
        this.occupiedPatternSlots = loadedOccupiedSlots;
        this.occupiedPatternSlotSnapshot = nextOccupiedSlotSnapshot;
        this.patternCacheSnapshot = nextPatternCacheSnapshot;
        this.revision = nextDirectoryRevision;
        applyWorkIndexes(loadedIndexes);
        this.coreId = loadedId;
        this.stateRevision = nextStateRevision;
        if (notifyChanges && !identityChanged) {
            for (int slot : changedPatternSlots) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.CATALOG));
            }
            for (int slot : changedRuntimeBindings) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING));
            }
            for (int slot : changedWorkSlots) {
                notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.WORK));
            }
        }
    }

    private TreeSet<Integer> changedPatternSlots(Map<Integer, TrinityPatternSlotImpl> loadedSlots,
                                                 Set<Integer> loadedOccupiedSlots) {
        TreeSet<Integer> changed = new TreeSet<>(this.occupiedPatternSlots);
        changed.addAll(loadedOccupiedSlots);
        TreeSet<Integer> retained = new TreeSet<>(this.occupiedPatternSlots);
        retained.retainAll(loadedOccupiedSlots);
        changed.removeAll(retained);
        for (int slot : retained) {
            if (!ItemStack.matches(this.slots.get(slot).pattern(), loadedSlots.get(slot).pattern())) {
                changed.add(slot);
            }
        }
        return changed;
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

    private Map<Integer, TrinityPatternSlotImpl> readV2Slots(
                                                             ListTag entries,
                                                             HolderLookup.Provider registries,
                                                             UUID loadedId) {
        HashMap<Integer, TrinityPatternSlotImpl> loaded = new HashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            TrinityPatternSlotImpl slot = TrinityPatternSlotImpl.readV2(
                    entries.getCompound(index), this.decoder, this.recipeIdResolvers, change -> {}, registries);
            checkSlot(slot.index());
            if (!slot.hasPersistentState()) {
                throw new IllegalArgumentException("Empty V2 Trinity pattern slot " + slot.index());
            }
            if (loaded.putIfAbsent(slot.index(), slot) != null) {
                throw new IllegalArgumentException("Duplicate V2 Trinity pattern slot " + slot.index());
            }
            for (TrinityCraftingBatch batch : slot.queuedBatches()) {
                validatePersistedRoute(batch.route(), loadedId, slot.index(), "queued group");
            }
            for (PatternRoute route : slot.pendingOutputRoutes()) {
                validatePersistedRoute(route, loadedId, slot.index(), "pending output");
            }
        }
        return loaded;
    }

    private Map<Integer, TrinityPatternSlotImpl> migrateV1Slots(ListTag patternEntries, ListTag queueEntries,
                                                                HolderLookup.Provider registries, UUID loadedId) {
        HashMap<Integer, ItemStack> patterns = new HashMap<>();
        Set<Integer> populatedPatterns = new HashSet<>();
        for (int index = 0; index < patternEntries.size(); index++) {
            CompoundTag entry = patternEntries.getCompound(index);
            int slot = checkedPersistedSlot(entry, populatedPatterns, "pattern");
            ItemStack pattern = normalizePattern(ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG)));
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("Persisted V1 pattern slot " + slot + " is empty");
            }
            patterns.put(slot, pattern);
        }

        HashMap<Integer, List<TrinityCraftingBatch.V1Data>> queues = new HashMap<>();
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
            queues.put(slot, List.copyOf(migrated));
        }

        TreeSet<Integer> populatedSlots = new TreeSet<>(patterns.keySet());
        populatedSlots.addAll(queues.keySet());
        HashMap<Integer, TrinityPatternSlotImpl> loaded = new HashMap<>();
        for (int slot : populatedSlots) {
            loaded.put(slot, TrinityPatternSlotImpl.migrateV1(
                    slot,
                    patterns.getOrDefault(slot, ItemStack.EMPTY),
                    queues.getOrDefault(slot, List.of()),
                    this.decoder,
                    this.recipeIdResolvers,
                    change -> {}));
        }
        return loaded;
    }

    private Map<Integer, LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> readV1PendingOutputs(
                                                                                                    ListTag entries,
                                                                                                    HolderLookup.Provider registries,
                                                                                                    UUID loadedId) {
        HashMap<Integer, LinkedHashMap<PatternRoute, List<TrinityItemAmount>>> loaded = new HashMap<>();
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
            loaded.computeIfAbsent(route.slot(), ignored -> new LinkedHashMap<>())
                    .put(route, List.copyOf(outputs));
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

    private TrinityPatternSlotImpl newDetachedSlot(int slot) {
        return new TrinityPatternSlotImpl(slot, this.decoder, this.recipeIdResolvers, change -> {});
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
            slot.completeHead(batch, result.countedOutputs());
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
                markCatalogChanged(change.slot());
            }
            case RUNTIME_BINDING -> updateRuntimeBinding(change.slot());
            case WORK -> updateSlotWorkIndexes(change.slot());
            case PERSISTENT -> markPersistentChanged(change.slot());
        }
    }

    private void updateCachedPattern(int slot) {
        ItemStack pattern = this.slots.get(slot).pattern();
        if (pattern.isEmpty()) {
            this.occupiedPatternSlots.remove(slot);
            this.cachedPatterns.remove(slot);
            return;
        }
        this.occupiedPatternSlots.add(slot);
        IMolecularAssemblerSupportedPattern decoded = this.slots.get(slot).decodedPattern();
        TrinityPatternDefinition definition = this.slots.get(slot).requiredInstalledDefinition();
        this.cachedPatterns.put(slot, new CachedPattern(slot, definition, decoded));
    }

    private void updateRuntimeBinding(int slot) {
        CachedPattern cached = this.cachedPatterns.get(slot);
        if (cached == null) {
            throw new IllegalStateException(
                    "Runtime binding changed for unoccupied Trinity pattern slot " + slot);
        }
        IMolecularAssemblerSupportedPattern decoded = this.slots.get(slot).decodedPattern();
        TrinityPatternDefinition definition = this.slots.get(slot).requiredInstalledDefinition();
        boolean semanticChange = cached.rebind(definition, decoded);
        if (semanticChange) {
            notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.RUNTIME_BINDING));
        }
    }

    private void markCatalogChanged(int slot) {
        this.revision = Math.incrementExact(this.revision);
        rebuildPatternCacheSnapshot();
        notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.CATALOG));
    }

    private void rebuildPatternCacheSnapshot() {
        this.occupiedPatternSlotSnapshot = List.copyOf(this.occupiedPatternSlots);
        ArrayList<CachedPattern> orderedPatterns = new ArrayList<>(this.occupiedPatternSlotSnapshot.size());
        for (int slot : this.occupiedPatternSlotSnapshot) {
            orderedPatterns.add(this.cachedPatterns.get(slot));
        }
        this.patternCacheSnapshot = new PatternCacheSnapshot(
                this.revision,
                orderedPatterns);
    }

    private TreeSet<Integer> persistentSlots() {
        TreeSet<Integer> persistent = new TreeSet<>(this.occupiedPatternSlots);
        persistent.addAll(this.queuedSlots);
        persistent.addAll(this.pendingOutputSlots);
        return persistent;
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
            notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.WORK));
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

    private WorkIndexes createWorkIndexes(Iterable<TrinityPatternSlotImpl> sourceSlots) {
        TreeSet<Integer> loadedQueuedSlots = new TreeSet<>();
        TreeSet<Integer> loadedPendingOutputSlots = new TreeSet<>();
        HashMap<UUID, TreeSet<Integer>> loadedWorkingSlotsByHost = new HashMap<>();
        HashMap<UUID, TreeSet<Integer>> loadedPendingSlotsByHost = new HashMap<>();
        for (TrinityPatternSlotImpl patternSlot : sourceSlots) {
            int slot = patternSlot.index();
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

    private static TreeSet<Integer> occupiedSlots(Iterable<TrinityPatternSlotImpl> sourceSlots) {
        TreeSet<Integer> occupied = new TreeSet<>();
        for (TrinityPatternSlotImpl slot : sourceSlots) {
            if (!slot.pattern().isEmpty()) {
                occupied.add(slot.index());
            }
        }
        return occupied;
    }

    private void applyWorkIndexes(WorkIndexes indexes) {
        this.queuedSlots = indexes.queuedSlots();
        this.pendingOutputSlots = indexes.pendingOutputSlots();
        this.workingSlotsByHost = indexes.workingSlotsByHost();
        this.pendingOutputSlotsByHost = indexes.pendingSlotsByHost();
    }

    private void markPersistentChanged(int slot) {
        this.stateRevision = Math.incrementExact(this.stateRevision);
        notifyChange(new TrinityPatternSlot.Change(slot, TrinityPatternSlot.ChangeKind.PERSISTENT));
    }

    private void ensurePersistentRevisionAvailable() {
        if (this.stateRevision == Long.MAX_VALUE) {
            throw new ArithmeticException("Trinity pattern core state revision overflow");
        }
    }

    private void notifyChange(TrinityPatternSlot.Change change) {
        try {
            this.changeListener.onChanged(change);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity pattern core {} retained committed slot {} {} state after its owner callback failed",
                    this.coreId,
                    change.slot(),
                    change.kind(),
                    exception);
        }
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

    private record SlotApplication(TrinityPatternSlotImpl target, TrinityPatternSlotImpl loaded) {}

    private record CachedRebind(CachedPattern target, CachedPattern.PreparedRebind prepared) {}

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
