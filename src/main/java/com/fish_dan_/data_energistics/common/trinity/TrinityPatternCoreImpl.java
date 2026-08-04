package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * publication, sparse indexes, refund transactions, and atomic persistence.
 * </p>
 */
public final class TrinityPatternCoreImpl implements TrinityPatternCore {

    private static final Set<Integer> SUPPORTED_CAPACITIES = Set.of(64, 128, 512);
    private static final int CURRENT_STATE_VERSION = 3;
    private static final int LEGACY_V2_STATE_VERSION = 2;
    private static final String AMOUNT_TAG = "amount";
    private static final String CORE_ID_TAG = "core_id";
    private static final String HOST_ID_TAG = "host_id";
    private static final String ITEMS_TAG = "items";
    private static final String LEGACY_PATTERNS_TAG = "patterns";
    private static final String LEGACY_PENDING_OUTPUTS_TAG = "pending_outputs";
    private static final String LEGACY_QUEUES_TAG = "queues";
    private static final String PATTERN_CAPACITY_TAG = "pattern_capacity";
    private static final String PATTERN_REFUNDS_TAG = "patterns";
    private static final String PROTOTYPE_TAG = "prototype";
    private static final String REFUND_OUTBOX_TAG = "refund_outbox";
    private static final String RETAINED_REFUNDS_TAG = "retained";
    private static final String SLOT_TAG = "slot";
    private static final String SLOTS_TAG = "slots";
    private static final String STACK_TAG = "stack";
    private static final String VERSION_TAG = "version";

    /** Identifies the validated top-level persistence shape and whether it must be rewritten canonically. */
    private enum PersistedSchema {

        CURRENT(false),
        CURRENT_UNVERSIONED(true),
        LEGACY_V2(true);

        /**
         * Records whether the owning block entity must persist the canonical current schema after a successful load.
         */
        private final boolean rewriteRequired;

        PersistedSchema(boolean rewriteRequired) {
            this.rewriteRequired = rewriteRequired;
        }

        boolean rewriteRequired() {
            return this.rewriteRequired;
        }
    }

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
    /** Installed patterns cleared for refund but not yet confirmed by an external destination. */
    private List<PatternRefundEntry> patternRefundOutbox = new ArrayList<>();
    /** Host-isolated FIFO entries cleared for refund but not yet confirmed by an external destination. */
    private Map<UUID, ArrayList<RetainedRefundEntry>> retainedRefundOutboxByHost = new TreeMap<>();
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
    @Nullable
    private PatternRefundTransactionImpl activePatternRefundTransaction;

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
    public boolean hasPendingRefund(UUID hostId) {
        ArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(hostId);
        return entries != null && !entries.isEmpty();
    }

    @Override
    public PatternRefundTransaction preparePatternRefund() {
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
            throw new IllegalStateException("Trinity pattern core " + this.coreId + " is already processing a refund");
        }
        if (hasWork()) {
            return new PatternRefundTransactionImpl(-1L, List.of(), List.of(), 0, true);
        }
        ArrayList<PatternRefundSlot> capturedSlots = new ArrayList<>(this.occupiedPatternSlots.size());
        ArrayList<PatternRefundEntry> offeredPatterns = new ArrayList<>(this.patternRefundOutbox);
        int outboxSize = offeredPatterns.size();
        for (int slot : this.occupiedPatternSlots) {
            TrinityPatternSlot patternSlot = this.slots.get(slot);
            ItemStack pattern = patternSlot.pattern();
            if (!pattern.isEmpty()) {
                capturedSlots.add(new PatternRefundSlot(slot, patternSlot.revision(), pattern));
                offeredPatterns.add(new PatternRefundEntry(slot, pattern));
            }
        }
        PatternRefundTransactionImpl transaction = new PatternRefundTransactionImpl(
                this.stateRevision,
                List.copyOf(capturedSlots),
                List.copyOf(offeredPatterns),
                outboxSize,
                false);
        this.activePatternRefundTransaction = transaction;
        return transaction;
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
        boolean committed = false;
        try {
            if (refundable.isEmpty() || !delivery.prepare(refundable)) {
                transaction.rollback();
                return false;
            }
            if (!transaction.commit()) {
                transaction.rollback();
                return false;
            }
            committed = true;
            List<TrinityItemAmount> undelivered = refundable;
            try {
                undelivered = delivery.deliver(refundable);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern core {} refund delivery failed after queued state was committed",
                        this.coreId,
                        exception);
            }
            transaction.complete(undelivered);
            return undelivered.isEmpty();
        } catch (RuntimeException exception) {
            if (committed) {
                try {
                    transaction.complete(refundable);
                } catch (RuntimeException completionFailure) {
                    exception.addSuppressed(completionFailure);
                }
            } else {
                transaction.rollback();
            }
            Data_Energistics.LOGGER.error(
                    "Failed to prepare Trinity pattern core {} refund delivery; retained queued state",
                    this.coreId,
                    exception);
            return false;
        }
    }

    @Override
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        writeCurrentSchemaHeader(data);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        ListTag slotEntries = new ListTag();
        for (int slot : persistentSlots()) {
            slotEntries.add(this.slots.get(slot).writeToTag(registries));
        }
        data.put(SLOTS_TAG, slotEntries);
        data.put(REFUND_OUTBOX_TAG, writeRefundOutbox(registries));
    }

    /**
     * Replaces persisted core state with the exact queued-input and pending-output snapshot that remains after
     * installed patterns become independent mining drops.
     *
     * <p>
     * The resulting state keeps the core identity, capacity, route-owned work, and queue definitions, while
     * omitting every installed pattern. That preserves refundability without allowing a restored core to execute a
     * retained batch before a new valid pattern is installed.
     * </p>
     *
     * @param data       destination block-entity state
     * @param registries item component registry access
     */
    public void writeRetainedWorkToTag(CompoundTag data, HolderLookup.Provider registries) {
        ensureNoActiveRefundTransaction();
        writeCurrentSchemaHeader(data);
        data.putUUID(CORE_ID_TAG, this.coreId);
        data.putInt(PATTERN_CAPACITY_TAG, this.patternCapacity);
        TreeSet<Integer> retainedSlots = new TreeSet<>(this.queuedSlots);
        retainedSlots.addAll(this.pendingOutputSlots);
        ListTag slotEntries = new ListTag();
        for (int slot : retainedSlots) {
            slotEntries.add(this.slots.get(slot).writeRetainedWorkToTag(registries));
        }
        data.put(SLOTS_TAG, slotEntries);
        data.put(REFUND_OUTBOX_TAG, writeRefundOutbox(registries));
    }

    @Override
    public void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries) {
        hydrateFromTagAndReportMigration(data, registries);
    }

    /**
     * Hydrates a pristine core and reports whether a validated legacy shape must be rewritten as the current schema.
     *
     * @param data       source block-entity state
     * @param registries item component registry access
     * @return whether the accepted state used a legacy persistence shape
     */
    public boolean hydrateFromTagAndReportMigration(CompoundTag data, HolderLookup.Provider registries) {
        if (this.stateRevision != 0L || this.revision != 0L || !persistentSlots().isEmpty()) {
            throw new IllegalStateException("Only a pristine Trinity pattern core may hydrate persisted state");
        }
        return applyPersistedState(data, registries, false);
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        readFromTagAndReportMigration(data, registries);
    }

    /**
     * Restores an already-live core and reports whether the accepted state used a legacy persistence shape.
     *
     * @param data       source block-entity state
     * @param registries item component registry access
     * @return whether the accepted state must be rewritten as the current schema
     */
    public boolean readFromTagAndReportMigration(CompoundTag data, HolderLookup.Provider registries) {
        return applyPersistedState(data, registries, true);
    }

    private boolean applyPersistedState(CompoundTag data,
                                        HolderLookup.Provider registries,
                                        boolean notifyChanges) {
        ensureNoActiveRefundTransaction();
        if (!data.hasUUID(CORE_ID_TAG)) {
            if (containsCoreState(data)) {
                throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its UUID");
            }
            return false;
        }
        validatePersistedCapacity(data);
        PersistedSchema schema = readPersistedSchema(data);
        UUID loadedId = data.getUUID(CORE_ID_TAG);
        boolean identityChanged = !this.coreId.equals(loadedId);
        Map<Integer, TrinityPatternSlotImpl> loadedSlots = readSlots(requiredCompoundList(data, SLOTS_TAG), registries, loadedId);
        RefundOutbox loadedOutbox = schema == PersistedSchema.LEGACY_V2 ? new RefundOutbox(List.of(), Map.of()) : readRefundOutbox(data, registries);

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
        this.patternRefundOutbox = loadedOutbox.patterns();
        this.retainedRefundOutboxByHost = loadedOutbox.retainedByHost();
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
        return schema.rewriteRequired();
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
        ArrayList<RetainedRefundOffer> offeredEntries = new ArrayList<>();
        appendRetainedOutboxOffers(routeHostId, offeredEntries);
        int existingOutboxEntryCount = offeredEntries.size();
        ArrayList<TrinityPatternSlotImpl.WorkState> capturedSlots = new ArrayList<>(this.patternCapacity);
        for (int slot = 0; slot < this.patternCapacity; slot++) {
            TrinityPatternSlotImpl.WorkState captured = this.slots.get(slot).captureWorkState();
            capturedSlots.add(captured);
            for (TrinityCraftingBatch batch : captured.batches()) {
                if (routeHostId == null || routeHostId.equals(batch.route().hostId())) {
                    for (ItemStack input : batch.inputs()) {
                        if (!input.isEmpty()) {
                            for (TrinityItemAmount item : TrinityItemAmount.multiply(input, batch.count())) {
                                offeredEntries.add(new RetainedRefundOffer(
                                        batch.route().hostId(), new RetainedRefundEntry(slot, item)));
                            }
                        }
                    }
                }
            }
            for (Map.Entry<PatternRoute, List<TrinityItemAmount>> entry : captured.pendingOutputs().entrySet()) {
                if (routeHostId == null || routeHostId.equals(entry.getKey().hostId())) {
                    for (TrinityItemAmount item : entry.getValue()) {
                        offeredEntries.add(new RetainedRefundOffer(
                                entry.getKey().hostId(), new RetainedRefundEntry(slot, item)));
                    }
                }
            }
        }
        return new RefundState(
                routeHostId,
                this.stateRevision,
                List.copyOf(capturedSlots),
                List.copyOf(offeredEntries),
                existingOutboxEntryCount);
    }

    private RefundTransaction beginRefund(RefundState captured) {
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
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

    private void appendRetainedOutboxOffers(@Nullable UUID hostId, List<RetainedRefundOffer> offers) {
        if (hostId != null) {
            ArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(hostId);
            if (entries != null) {
                for (RetainedRefundEntry entry : entries) {
                    offers.add(new RetainedRefundOffer(hostId, entry));
                }
            }
            return;
        }
        for (Map.Entry<UUID, ArrayList<RetainedRefundEntry>> group : this.retainedRefundOutboxByHost.entrySet()) {
            for (RetainedRefundEntry entry : group.getValue()) {
                offers.add(new RetainedRefundOffer(group.getKey(), entry));
            }
        }
    }

    private void appendRetainedRefundEntries(List<RetainedRefundOffer> offers) {
        for (RetainedRefundOffer offer : offers) {
            this.retainedRefundOutboxByHost
                    .computeIfAbsent(offer.hostId(), ignored -> new ArrayList<>())
                    .add(offer.entry());
            markPersistentChanged(offer.entry().slot());
        }
    }

    private void removeRetainedRefundEntriesFromTail(List<RetainedRefundOffer> offers) {
        for (int index = offers.size() - 1; index >= 0; index--) {
            RetainedRefundOffer offer = offers.get(index);
            ArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("Missing Trinity retained refund outbox entry for host " + offer.hostId());
            }
            int tailIndex = entries.size() - 1;
            RetainedRefundEntry actual = entries.get(tailIndex);
            if (!actual.matches(offer.entry())) {
                throw new IllegalStateException("Trinity retained refund outbox order changed for host " + offer.hostId());
            }
            entries.remove(tailIndex);
            if (entries.isEmpty()) {
                this.retainedRefundOutboxByHost.remove(offer.hostId(), entries);
            }
            markPersistentChanged(offer.entry().slot());
        }
    }

    private void completeRetainedRefund(List<RetainedRefundOffer> offers, List<TrinityItemAmount> undelivered) {
        int undeliveredStart = validateRetainedUndeliveredSuffix(offers, undelivered);
        for (int index = 0; index < undeliveredStart; index++) {
            removeDeliveredRetainedRefund(offers.get(index));
        }
        if (undelivered.isEmpty()) {
            return;
        }
        RetainedRefundOffer firstUndelivered = offers.get(undeliveredStart);
        TrinityItemAmount remaining = undelivered.get(0);
        if (!firstUndelivered.entry().item().equals(remaining)) {
            replaceFirstRetainedRefund(firstUndelivered, remaining);
        }
    }

    private void removeDeliveredRetainedRefund(RetainedRefundOffer offer) {
        ArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("Missing delivered Trinity retained refund for host " + offer.hostId());
        }
        RetainedRefundEntry actual = entries.get(0);
        if (!actual.matches(offer.entry())) {
            throw new IllegalStateException("Trinity retained refund order changed for host " + offer.hostId());
        }
        entries.remove(0);
        if (entries.isEmpty()) {
            this.retainedRefundOutboxByHost.remove(offer.hostId(), entries);
        }
        markPersistentChanged(offer.entry().slot());
    }

    private void replaceFirstRetainedRefund(RetainedRefundOffer offer, TrinityItemAmount remaining) {
        ArrayList<RetainedRefundEntry> entries = this.retainedRefundOutboxByHost.get(offer.hostId());
        if (entries == null || entries.isEmpty() || !entries.get(0).matches(offer.entry())) {
            throw new IllegalStateException("Trinity retained refund order changed for host " + offer.hostId());
        }
        entries.set(0, new RetainedRefundEntry(offer.entry().slot(), remaining));
        markPersistentChanged(offer.entry().slot());
    }

    private static int validateRetainedUndeliveredSuffix(List<RetainedRefundOffer> offers,
                                                         List<TrinityItemAmount> undelivered) {
        if (undelivered.size() > offers.size()) {
            throw new IllegalArgumentException("Trinity retained refund delivery returned more items than it received");
        }
        int start = offers.size() - undelivered.size();
        if (undelivered.isEmpty()) {
            return start;
        }
        TrinityItemAmount expectedFirst = offers.get(start).entry().item();
        TrinityItemAmount actualFirst = undelivered.get(0);
        if (!expectedFirst.key().equals(actualFirst.key()) || actualFirst.amount() > expectedFirst.amount()) {
            throw new IllegalArgumentException("Trinity retained refund delivery returned an invalid remaining suffix");
        }
        for (int index = 1; index < undelivered.size(); index++) {
            if (!offers.get(start + index).entry().item().equals(undelivered.get(index))) {
                throw new IllegalArgumentException("Trinity retained refund delivery changed remaining item order");
            }
        }
        return start;
    }

    private void appendPatternRefundEntries(List<PatternRefundEntry> entries) {
        for (PatternRefundEntry entry : entries) {
            this.patternRefundOutbox.add(entry);
            markPersistentChanged(entry.slot());
        }
    }

    private void removePatternRefundEntriesFromTail(List<PatternRefundEntry> entries) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            PatternRefundEntry expected = entries.get(index);
            if (this.patternRefundOutbox.isEmpty()) {
                throw new IllegalStateException("Missing Trinity installed-pattern refund outbox entry");
            }
            int tailIndex = this.patternRefundOutbox.size() - 1;
            PatternRefundEntry actual = this.patternRefundOutbox.get(tailIndex);
            if (!actual.matches(expected)) {
                throw new IllegalStateException("Trinity installed-pattern refund outbox order changed");
            }
            this.patternRefundOutbox.remove(tailIndex);
            markPersistentChanged(expected.slot());
        }
    }

    private void completePatternRefund(List<PatternRefundEntry> offers, List<ItemStack> undelivered) {
        int undeliveredStart = validatePatternUndeliveredSuffix(offers, undelivered);
        for (int index = 0; index < undeliveredStart; index++) {
            PatternRefundEntry expected = offers.get(index);
            if (this.patternRefundOutbox.isEmpty()) {
                throw new IllegalStateException("Missing delivered Trinity installed-pattern refund");
            }
            PatternRefundEntry actual = this.patternRefundOutbox.get(0);
            if (!actual.matches(expected)) {
                throw new IllegalStateException("Trinity installed-pattern refund order changed");
            }
            this.patternRefundOutbox.remove(0);
            markPersistentChanged(expected.slot());
        }
    }

    private static int validatePatternUndeliveredSuffix(List<PatternRefundEntry> offers, List<ItemStack> undelivered) {
        if (undelivered.size() > offers.size()) {
            throw new IllegalArgumentException("Trinity pattern refund delivery returned more patterns than it received");
        }
        int start = offers.size() - undelivered.size();
        for (int index = 0; index < undelivered.size(); index++) {
            if (!offers.get(start + index).matches(undelivered.get(index))) {
                throw new IllegalArgumentException("Trinity pattern refund delivery changed remaining pattern order");
            }
        }
        return start;
    }

    private Map<Integer, TrinityPatternSlotImpl> readSlots(
                                                           ListTag entries,
                                                           HolderLookup.Provider registries,
                                                           UUID loadedId) {
        HashMap<Integer, TrinityPatternSlotImpl> loaded = new HashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            TrinityPatternSlotImpl slot = TrinityPatternSlotImpl.readFromTag(
                    entries.getCompound(index), this.decoder, this.recipeIdResolvers, change -> {}, registries);
            checkSlot(slot.index());
            if (!slot.hasPersistentState()) {
                throw new IllegalArgumentException("Empty persisted Trinity pattern slot " + slot.index());
            }
            if (loaded.putIfAbsent(slot.index(), slot) != null) {
                throw new IllegalArgumentException("Duplicate persisted Trinity pattern slot " + slot.index());
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

    private static void writeCurrentSchemaHeader(CompoundTag data) {
        data.putInt(VERSION_TAG, CURRENT_STATE_VERSION);
        data.remove(LEGACY_PATTERNS_TAG);
        data.remove(LEGACY_QUEUES_TAG);
        data.remove(LEGACY_PENDING_OUTPUTS_TAG);
    }

    private static PersistedSchema readPersistedSchema(CompoundTag data) {
        boolean containsLegacyV1Lists = containsLegacyV1Lists(data);
        if (data.contains(VERSION_TAG)) {
            if (!data.contains(VERSION_TAG, Tag.TAG_INT)) {
                throw new IllegalArgumentException("Persisted Trinity pattern core state version must be an integer");
            }
            int version = data.getInt(VERSION_TAG);
            if (version == LEGACY_V2_STATE_VERSION) {
                if (containsLegacyV1Lists || data.contains(REFUND_OUTBOX_TAG)) {
                    throw new IllegalArgumentException(
                            "Persisted V2 Trinity pattern core state mixes incompatible schema fields");
                }
                requirePersistedSlots(data, "V2");
                return PersistedSchema.LEGACY_V2;
            }
            if (version == CURRENT_STATE_VERSION) {
                if (containsLegacyV1Lists) {
                    throw new IllegalArgumentException(
                            "Persisted current Trinity pattern core state contains legacy V1 fields");
                }
                requirePersistedSlots(data, "current");
                requireRefundOutbox(data, "current");
                return PersistedSchema.CURRENT;
            }
            throw new IllegalArgumentException("Unsupported Trinity pattern core state version " + version);
        }

        if (containsLegacyV1Lists) {
            if (data.contains(SLOTS_TAG) || data.contains(REFUND_OUTBOX_TAG)) {
                throw new IllegalArgumentException(
                        "Persisted unversioned Trinity pattern core state mixes legacy V1 and current fields");
            }
            throw new IllegalArgumentException(
                    "Unsupported unversioned V1 Trinity pattern core state; an explicit V1 decoder is required");
        }
        requirePersistedSlots(data, "unversioned current");
        requireRefundOutbox(data, "unversioned current");
        return PersistedSchema.CURRENT_UNVERSIONED;
    }

    private static void requirePersistedSlots(CompoundTag data, String schema) {
        if (!data.contains(SLOTS_TAG)) {
            throw new IllegalArgumentException("Persisted " + schema + " Trinity pattern core state is missing slots");
        }
    }

    private static void requireRefundOutbox(CompoundTag data, String schema) {
        if (!data.contains(REFUND_OUTBOX_TAG)) {
            throw new IllegalArgumentException(
                    "Persisted " + schema + " Trinity pattern core state is missing its refund outbox");
        }
    }

    private static boolean containsLegacyV1Lists(CompoundTag data) {
        return data.contains(LEGACY_PATTERNS_TAG) || data.contains(LEGACY_QUEUES_TAG) ||
                data.contains(LEGACY_PENDING_OUTPUTS_TAG);
    }

    private static boolean containsCoreState(CompoundTag data) {
        return data.contains(VERSION_TAG) || data.contains(PATTERN_CAPACITY_TAG) || data.contains(SLOTS_TAG) ||
                data.contains(REFUND_OUTBOX_TAG) || containsLegacyV1Lists(data);
    }

    private static ListTag requiredCompoundList(CompoundTag data, String tagName) {
        if (!(data.get(tagName) instanceof ListTag entries) ||
                !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "Persisted Trinity pattern core state requires compound list '" + tagName + "'");
        }
        return entries;
    }

    private CompoundTag writeRefundOutbox(HolderLookup.Provider registries) {
        CompoundTag outbox = new CompoundTag();
        ListTag patternEntries = new ListTag();
        for (PatternRefundEntry entry : this.patternRefundOutbox) {
            CompoundTag entryData = new CompoundTag();
            entryData.putInt(SLOT_TAG, entry.slot());
            entryData.put(STACK_TAG, entry.pattern().saveOptional(registries));
            patternEntries.add(entryData);
        }
        outbox.put(PATTERN_REFUNDS_TAG, patternEntries);

        ListTag retainedGroups = new ListTag();
        for (Map.Entry<UUID, ArrayList<RetainedRefundEntry>> group : this.retainedRefundOutboxByHost.entrySet()) {
            if (group.getValue().isEmpty()) {
                throw new IllegalStateException("Trinity retained refund outbox contains an empty host group");
            }
            CompoundTag groupData = new CompoundTag();
            groupData.putUUID(HOST_ID_TAG, group.getKey());
            ListTag items = new ListTag();
            for (RetainedRefundEntry entry : group.getValue()) {
                CompoundTag itemData = new CompoundTag();
                itemData.putInt(SLOT_TAG, entry.slot());
                itemData.put(PROTOTYPE_TAG, entry.item().key().toStack(1).saveOptional(registries));
                itemData.putLong(AMOUNT_TAG, entry.item().amount());
                items.add(itemData);
            }
            groupData.put(ITEMS_TAG, items);
            retainedGroups.add(groupData);
        }
        outbox.put(RETAINED_REFUNDS_TAG, retainedGroups);
        return outbox;
    }

    private RefundOutbox readRefundOutbox(CompoundTag data, HolderLookup.Provider registries) {
        if (!(data.get(REFUND_OUTBOX_TAG) instanceof CompoundTag outbox)) {
            throw new IllegalArgumentException("Persisted Trinity pattern core state is missing its refund outbox");
        }
        requireExactKeys(outbox, "Trinity refund outbox", PATTERN_REFUNDS_TAG, RETAINED_REFUNDS_TAG);

        ListTag patternEntries = requiredCompoundList(outbox, PATTERN_REFUNDS_TAG);
        ArrayList<PatternRefundEntry> patterns = new ArrayList<>(patternEntries.size());
        for (int index = 0; index < patternEntries.size(); index++) {
            patterns.add(readPatternRefundEntry(patternEntries.getCompound(index), registries));
        }

        ListTag retainedGroups = requiredCompoundList(outbox, RETAINED_REFUNDS_TAG);
        TreeMap<UUID, ArrayList<RetainedRefundEntry>> retainedByHost = new TreeMap<>();
        for (int index = 0; index < retainedGroups.size(); index++) {
            CompoundTag groupData = retainedGroups.getCompound(index);
            requireExactKeys(groupData, "Trinity retained refund group", HOST_ID_TAG, ITEMS_TAG);
            if (!groupData.hasUUID(HOST_ID_TAG)) {
                throw new IllegalArgumentException("Trinity retained refund group is missing its host UUID");
            }
            UUID hostId = groupData.getUUID(HOST_ID_TAG);
            ListTag items = requiredCompoundList(groupData, ITEMS_TAG);
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Trinity retained refund group must not be empty");
            }
            ArrayList<RetainedRefundEntry> entries = new ArrayList<>(items.size());
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                entries.add(readRetainedRefundEntry(items.getCompound(itemIndex), registries));
            }
            if (retainedByHost.putIfAbsent(hostId, entries) != null) {
                throw new IllegalArgumentException("Duplicate Trinity retained refund host " + hostId);
            }
        }
        return new RefundOutbox(patterns, retainedByHost);
    }

    private PatternRefundEntry readPatternRefundEntry(CompoundTag data, HolderLookup.Provider registries) {
        requireExactKeys(data, "Trinity pattern refund entry", SLOT_TAG, STACK_TAG);
        if (!data.contains(SLOT_TAG, Tag.TAG_INT) || !data.contains(STACK_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Trinity pattern refund entry is incomplete");
        }
        int slot = data.getInt(SLOT_TAG);
        checkSlot(slot);
        ItemStack pattern = ItemStack.parseOptional(registries, data.getCompound(STACK_TAG));
        if (pattern.isEmpty() || pattern.getCount() != 1) {
            throw new IllegalArgumentException("Trinity pattern refund entry requires one encoded pattern");
        }
        return new PatternRefundEntry(slot, pattern);
    }

    private RetainedRefundEntry readRetainedRefundEntry(CompoundTag data, HolderLookup.Provider registries) {
        requireExactKeys(data, "Trinity retained refund entry", SLOT_TAG, PROTOTYPE_TAG, AMOUNT_TAG);
        if (!data.contains(SLOT_TAG, Tag.TAG_INT) || !data.contains(PROTOTYPE_TAG, Tag.TAG_COMPOUND) ||
                !data.contains(AMOUNT_TAG, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Trinity retained refund entry is incomplete");
        }
        int slot = data.getInt(SLOT_TAG);
        checkSlot(slot);
        ItemStack prototype = ItemStack.parseOptional(registries, data.getCompound(PROTOTYPE_TAG));
        if (prototype.isEmpty() || prototype.getCount() != 1) {
            throw new IllegalArgumentException("Trinity retained refund entry requires one item prototype");
        }
        return new RetainedRefundEntry(slot, new TrinityItemAmount(AEItemKey.of(prototype), data.getLong(AMOUNT_TAG)));
    }

    private static void requireExactKeys(CompoundTag data, String description, String... requiredKeys) {
        Set<String> actualKeys = new HashSet<>(data.getAllKeys());
        Set<String> expectedKeys = Set.of(requiredKeys);
        if (!actualKeys.equals(expectedKeys)) {
            throw new IllegalArgumentException(description + " has unexpected fields " + actualKeys);
        }
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
        if (this.activeRefundTransaction != null || this.activePatternRefundTransaction != null) {
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
                               List<RetainedRefundOffer> offeredEntries,
                               int existingOutboxEntryCount) {}

    /** One installed pattern awaiting an acknowledged player/world delivery. */
    private record PatternRefundEntry(int slot, ItemStack pattern) {

        private PatternRefundEntry {
            if (slot < 0) {
                throw new IllegalArgumentException("Trinity pattern refund slot must not be negative: " + slot);
            }
            if (pattern.isEmpty() || pattern.getCount() != 1) {
                throw new IllegalArgumentException("Trinity pattern refund entry requires one encoded pattern");
            }
            pattern = pattern.copy();
        }

        private boolean matches(PatternRefundEntry other) {
            return this.slot == other.slot && ItemStack.matches(this.pattern, other.pattern);
        }

        private boolean matches(ItemStack other) {
            return ItemStack.matches(this.pattern, other);
        }
    }

    /** One host-owned input or pending output awaiting an acknowledged external delivery. */
    private record RetainedRefundEntry(int slot, TrinityItemAmount item) {

        private RetainedRefundEntry {
            if (slot < 0) {
                throw new IllegalArgumentException("Trinity retained refund slot must not be negative: " + slot);
            }
            if (item == null) {
                throw new IllegalArgumentException("Trinity retained refund entry requires an item amount");
            }
        }

        private boolean matches(RetainedRefundEntry other) {
            return this.slot == other.slot && this.item.equals(other.item);
        }
    }

    /** Associates a durable retained refund entry with the host that owns its retry queue. */
    private record RetainedRefundOffer(UUID hostId, RetainedRefundEntry entry) {

        private RetainedRefundOffer {
            if (hostId == null) {
                throw new IllegalArgumentException("Trinity retained refund offer requires a host UUID");
            }
            if (entry == null) {
                throw new IllegalArgumentException("Trinity retained refund offer requires an entry");
            }
        }
    }

    /** Fully parsed, isolated durable refund queues that can be atomically applied to a core. */
    private record RefundOutbox(List<PatternRefundEntry> patterns,
                                Map<UUID, ArrayList<RetainedRefundEntry>> retainedByHost) {

        private RefundOutbox {
            patterns = new ArrayList<>(patterns);
            TreeMap<UUID, ArrayList<RetainedRefundEntry>> copiedRetained = new TreeMap<>();
            for (Map.Entry<UUID, ArrayList<RetainedRefundEntry>> group : retainedByHost.entrySet()) {
                copiedRetained.put(group.getKey(), new ArrayList<>(group.getValue()));
            }
            retainedByHost = copiedRetained;
        }
    }

    /** Immutable pattern capture guarded by the core and exact physical-slot revisions. */
    private record PatternRefundSlot(int slot, long slotRevision, ItemStack pattern) {

        private PatternRefundSlot {
            pattern = pattern.copy();
        }
    }

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
            return this.captured.offeredEntries().stream().map(offer -> offer.entry().item()).toList();
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || stateChangedSincePreparation() || !matchesRefundState(this.captured)) {
                this.closed = true;
                release();
                return false;
            }
            try {
                appendRetainedRefundEntries(this.captured.offeredEntries().subList(
                        this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
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
                try {
                    removeRetainedRefundEntriesFromTail(this.captured.offeredEntries().subList(
                            this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to remove Trinity pattern core {} retained refund ledger after commit failure",
                            TrinityPatternCoreImpl.this.coreId,
                            rollbackFailure);
                }
                this.closed = true;
                release();
                throw exception;
            }
        }

        @Override
        public void complete(List<TrinityItemAmount> undeliveredItems) {
            if (this.closed || TrinityPatternCoreImpl.this.activeRefundTransaction != this) {
                return;
            }
            if (!this.committed) {
                this.closed = true;
                release();
                throw new IllegalStateException("Trinity retained refund was completed before commit");
            }
            try {
                completeRetainedRefund(this.captured.offeredEntries(), undeliveredItems);
            } finally {
                this.committed = false;
                this.closed = true;
                release();
            }
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
                        removeRetainedRefundEntriesFromTail(this.captured.offeredEntries().subList(
                                this.captured.existingOutboxEntryCount(), this.captured.offeredEntries().size()));
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

    /** Reversible all-or-nothing installed-pattern removal for one core. */
    private final class PatternRefundTransactionImpl implements PatternRefundTransaction {

        private final long capturedStateRevision;
        private final List<PatternRefundSlot> capturedSlots;
        private final List<PatternRefundEntry> offeredEntries;
        private final int existingOutboxEntryCount;
        private final boolean blockedByWork;
        private boolean committed;
        private boolean closed;
        private long committedStateRevision = -1L;

        private PatternRefundTransactionImpl(long capturedStateRevision,
                                             List<PatternRefundSlot> capturedSlots,
                                             List<PatternRefundEntry> offeredEntries,
                                             int existingOutboxEntryCount,
                                             boolean blockedByWork) {
            this.capturedStateRevision = capturedStateRevision;
            this.capturedSlots = capturedSlots;
            this.offeredEntries = offeredEntries;
            this.existingOutboxEntryCount = existingOutboxEntryCount;
            this.blockedByWork = blockedByWork;
        }

        @Override
        public List<ItemStack> patterns() {
            return this.offeredEntries.stream().map(entry -> entry.pattern().copy()).toList();
        }

        @Override
        public boolean isEmpty() {
            return !this.blockedByWork && this.offeredEntries.isEmpty();
        }

        @Override
        public boolean isBlockedByWork() {
            return this.blockedByWork;
        }

        @Override
        public boolean commit() {
            if (this.closed || this.committed || this.blockedByWork ||
                    TrinityPatternCoreImpl.this.stateRevision != this.capturedStateRevision || hasWork()) {
                close();
                return false;
            }
            for (PatternRefundSlot captured : this.capturedSlots) {
                TrinityPatternSlot current = TrinityPatternCoreImpl.this.slots.get(captured.slot());
                if (current.revision() != captured.slotRevision() ||
                        !ItemStack.matches(current.pattern(), captured.pattern())) {
                    close();
                    return false;
                }
            }
            List<PatternRefundSlot> clearedSlots = new ArrayList<>(this.capturedSlots.size());
            try {
                appendPatternRefundEntries(this.offeredEntries.subList(
                        this.existingOutboxEntryCount, this.offeredEntries.size()));
                for (PatternRefundSlot captured : this.capturedSlots) {
                    if (!TrinityPatternCoreImpl.this.slot(captured.slot()).trySetPattern(ItemStack.EMPTY)) {
                        throw new IllegalStateException("Failed to clear captured Trinity pattern slot " + captured.slot());
                    }
                    clearedSlots.add(captured);
                }
                this.committed = true;
                this.committedStateRevision = TrinityPatternCoreImpl.this.stateRevision;
                return true;
            } catch (RuntimeException exception) {
                try {
                    restorePatterns(clearedSlots);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to restore Trinity pattern core {} after pattern refund commit failure",
                            TrinityPatternCoreImpl.this.coreId,
                            rollbackFailure);
                }
                try {
                    removePatternRefundEntriesFromTail(this.offeredEntries.subList(
                            this.existingOutboxEntryCount, this.offeredEntries.size()));
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                    Data_Energistics.LOGGER.error(
                            "Failed to remove Trinity pattern core {} installed-pattern refund ledger after commit failure",
                            TrinityPatternCoreImpl.this.coreId,
                            rollbackFailure);
                }
                close();
                throw exception;
            }
        }

        @Override
        public void complete(List<ItemStack> undeliveredPatterns) {
            if (this.closed || TrinityPatternCoreImpl.this.activePatternRefundTransaction != this) {
                return;
            }
            if (!this.committed) {
                close();
                throw new IllegalStateException("Trinity pattern refund was completed before commit");
            }
            try {
                completePatternRefund(this.offeredEntries, undeliveredPatterns);
            } finally {
                this.committed = false;
                close();
            }
        }

        @Override
        public void rollback() {
            if (this.closed) {
                return;
            }
            try {
                if (this.committed) {
                    if (TrinityPatternCoreImpl.this.stateRevision == this.committedStateRevision) {
                        restorePatterns();
                        removePatternRefundEntriesFromTail(this.offeredEntries.subList(
                                this.existingOutboxEntryCount, this.offeredEntries.size()));
                    } else {
                        Data_Energistics.LOGGER.error(
                                "Cannot roll back Trinity pattern core {} pattern refund because core state changed after commit",
                                TrinityPatternCoreImpl.this.coreId);
                    }
                }
            } finally {
                this.committed = false;
                close();
            }
        }

        private void restorePatterns() {
            restorePatterns(this.capturedSlots);
        }

        private void restorePatterns(List<PatternRefundSlot> slotsToRestore) {
            for (PatternRefundSlot captured : slotsToRestore) {
                ItemStack current = TrinityPatternCoreImpl.this.pattern(captured.slot());
                if (!current.isEmpty()) {
                    throw new IllegalStateException("Cannot restore Trinity pattern slot " + captured.slot() +
                            " because it changed during a pattern refund");
                }
                if (!TrinityPatternCoreImpl.this.slot(captured.slot()).trySetPattern(captured.pattern())) {
                    throw new IllegalStateException("Failed to restore Trinity pattern slot " + captured.slot());
                }
            }
        }

        private void close() {
            this.closed = true;
            if (TrinityPatternCoreImpl.this.activePatternRefundTransaction == this) {
                TrinityPatternCoreImpl.this.activePatternRefundTransaction = null;
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
