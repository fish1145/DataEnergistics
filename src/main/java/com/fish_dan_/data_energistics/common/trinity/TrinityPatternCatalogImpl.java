package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityCraftingBatch.InputSignature;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore.CachedPattern;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore.PatternCacheSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Default event-driven implementation of {@link TrinityPatternCatalog}.
 */
public final class TrinityPatternCatalogImpl implements TrinityPatternCatalog {

    private static final int CRAFTING_GRID_SLOT_COUNT = 9;
    private static final LayoutSnapshot EMPTY_LAYOUT = new LayoutSnapshot(0L, false, 0, List.of(), List.of());

    private final UUID hostId;
    private final Map<TrinityPatternCore, CoreRuntime> runtimesByCore = new IdentityHashMap<>();
    private final Map<PatternRoute, SlotBinding> routeBindings = new HashMap<>();
    private final Map<TrinityPatternCore, Set<Integer>> dirtySlotsByCore = new IdentityHashMap<>();
    private final TreeMap<Integer, ActiveSlot> activeSlotsByGlobalIndex = new TreeMap<>();

    private LayoutSnapshot layout = EMPTY_LAYOUT;
    private List<CoreRuntime> orderedRuntimes = List.of();
    private List<IPatternDetails> availablePatterns = List.of();
    private List<ActiveSlot> activeSlots = List.of();
    private long publicationRevision;
    private boolean retainedWork;

    /**
     * Creates a catalog whose published routes remain independent from the host's storage identity.
     *
     * @param hostId stable crafting host identity
     */
    public TrinityPatternCatalogImpl(UUID hostId) {
        this.hostId = hostId;
    }

    @Override
    public UUID hostId() {
        return this.hostId;
    }

    @Override
    public LayoutSnapshot layoutSnapshot() {
        return this.layout;
    }

    @Override
    public long publicationRevision() {
        return this.publicationRevision;
    }

    @Override
    public boolean isMountCurrent(long expectedRevision, CoreMount mount) {
        if (!this.layout.active() || this.layout.revision() != expectedRevision) {
            return false;
        }
        CoreRuntime runtime = this.runtimesByCore.get(mount.core());
        return runtime != null && runtime.matches(mount);
    }

    @Nullable
    @Override
    public GlobalSlot resolveGlobalSlot(long expectedRevision, int globalIndex) {
        LayoutSnapshot currentLayout = this.layout;
        if (!currentLayout.active() || currentLayout.revision() != expectedRevision ||
                globalIndex < 0 || globalIndex >= currentLayout.slotCount()) {
            return null;
        }
        int low = 0;
        int high = currentLayout.ranges().size() - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            CoreRange range = currentLayout.ranges().get(middle);
            if (globalIndex < range.firstGlobalIndex()) {
                high = middle - 1;
            } else if (globalIndex >= range.lastGlobalIndexExclusive()) {
                low = middle + 1;
            } else {
                CoreRuntime runtime = this.runtimesByCore.get(range.mount().core());
                if (runtime == null || runtime.range != range || !runtime.matches(range.mount())) {
                    return null;
                }
                return new GlobalSlot(
                        expectedRevision,
                        globalIndex,
                        range,
                        globalIndex - range.firstGlobalIndex());
            }
        }
        throw new IllegalStateException("Active Trinity pattern layout did not resolve global slot " + globalIndex);
    }

    @Nullable
    @Override
    public GlobalSlot resolveCoreSlot(long expectedRevision, CoreMount mount, int coreSlot) {
        if (!this.layout.active() || this.layout.revision() != expectedRevision ||
                coreSlot < 0 || coreSlot >= mount.blockCapacity()) {
            return null;
        }
        CoreRuntime runtime = this.runtimesByCore.get(mount.core());
        if (runtime == null || !runtime.matches(mount)) {
            return null;
        }
        int globalIndex = Math.addExact(runtime.range.firstGlobalIndex(), coreSlot);
        return new GlobalSlot(expectedRevision, globalIndex, runtime.range, coreSlot);
    }

    @Override
    public boolean isCoreMounted(TrinityPatternCore core) {
        return this.layout.active() && this.runtimesByCore.containsKey(core);
    }

    @Override
    public RebuildResult rebuild(List<CoreMount> mounts) {
        ArrayList<CoreMount> sorted = new ArrayList<>(mounts);
        sorted.sort((left, right) -> left.position().compareTo(right.position()));

        Set<BlockPos> positions = new HashSet<>();
        Map<UUID, CoreMount> mountsByCoreId = new HashMap<>();
        for (CoreMount mount : sorted) {
            if (!positions.add(mount.position())) {
                return rejectScan(mount.position(), "Duplicate Trinity pattern core position " + mount.position());
            }
            if (mount.blockCapacity() != mount.core().patternCapacity()) {
                return rejectScan(
                        mount.position(),
                        "Trinity pattern core capacity mismatch at " + mount.position() + ": block declares " +
                                mount.blockCapacity() + " slots but block entity owns " +
                                mount.core().patternCapacity());
            }
            CoreMount previous = mountsByCoreId.putIfAbsent(mount.core().coreId(), mount);
            if (previous != null) {
                return rejectScan(
                        mount.position(),
                        "Duplicate Trinity pattern core UUID " + mount.core().coreId() + " at " +
                                previous.position() + " and " + mount.position());
            }
        }

        List<CoreMount> nextMounts = List.copyOf(sorted);
        List<CoreRange> nextRanges = createRanges(nextMounts);
        if (hasSameActiveLayout(nextRanges)) {
            return new RebuildResult(true, false, null, "");
        }

        Map<TrinityPatternCore, CoreRuntime> nextRuntimesByCore = new IdentityHashMap<>();
        HashMap<PatternRoute, SlotBinding> nextRouteBindings = new HashMap<>();
        TreeMap<Integer, ActiveSlot> nextActiveSlots = new TreeMap<>();
        ArrayList<CoreRuntime> nextRuntimes = new ArrayList<>(nextRanges.size());
        ArrayList<IPatternDetails> nextPatterns = new ArrayList<>();

        for (CoreRange range : nextRanges) {
            CoreRuntime runtime = new CoreRuntime(range);
            PatternCacheSnapshot snapshot = range.mount().core().patternCacheSnapshot();
            PreparedPublication publication = runtime.preparePublication(snapshot);
            for (SlotBinding binding : publication.bindings()) {
                if (nextRouteBindings.put(binding.route, binding) != null) {
                    throw new IllegalStateException("Duplicate Trinity route while rebuilding catalog: " + binding.route);
                }
            }
            nextPatterns.addAll(publication.patterns());

            List<Integer> workingSlots = range.mount().core().workingSlots(this.hostId);
            int previousSlot = -1;
            for (int coreSlot : workingSlots) {
                if (coreSlot <= previousSlot || coreSlot >= range.mount().blockCapacity()) {
                    throw new IllegalStateException(
                            "Trinity core " + range.coreId() + " returned invalid working slot " + coreSlot);
                }
                previousSlot = coreSlot;
                SlotBinding binding = runtime.binding(coreSlot);
                nextActiveSlots.put(binding.globalIndex, binding.activeSlot);
            }

            nextRuntimes.add(runtime);
            nextRuntimesByCore.put(range.mount().core(), runtime);
        }

        long nextRevision = Math.incrementExact(this.layout.revision());
        int slotCount = nextRanges.isEmpty() ? 0 : nextRanges.getLast().lastGlobalIndexExclusive();
        this.layout = new LayoutSnapshot(nextRevision, true, slotCount, nextMounts, nextRanges);
        this.runtimesByCore.clear();
        this.runtimesByCore.putAll(nextRuntimesByCore);
        this.routeBindings.clear();
        this.routeBindings.putAll(nextRouteBindings);
        this.activeSlotsByGlobalIndex.clear();
        this.activeSlotsByGlobalIndex.putAll(nextActiveSlots);
        this.orderedRuntimes = List.copyOf(nextRuntimes);
        this.availablePatterns = List.copyOf(nextPatterns);
        rebuildActiveSlotSnapshot();
        this.dirtySlotsByCore.clear();
        this.retainedWork = false;
        advancePublicationRevision();
        return new RebuildResult(true, true, null, "");
    }

    @Override
    public boolean refreshChangedPatterns() {
        if (!this.layout.active() || this.dirtySlotsByCore.isEmpty()) {
            return false;
        }

        Map<TrinityPatternCore, Set<Integer>> changedSlots = new IdentityHashMap<>(this.dirtySlotsByCore);
        this.dirtySlotsByCore.clear();
        boolean publicationChanged = false;
        for (Map.Entry<TrinityPatternCore, Set<Integer>> changedCore : changedSlots.entrySet()) {
            CoreRuntime runtime = this.runtimesByCore.get(changedCore.getKey());
            if (!runtime.matches(runtime.range.mount())) {
                Data_Energistics.LOGGER.warn(
                        "Invalidating Trinity catalog {} because mounted core identity changed at {}",
                        this.hostId,
                        runtime.range.mount().position());
                invalidateLayout();
                return true;
            }
            publicationChanged |= runtime.applyChangedPublication(changedCore.getValue());
        }
        if (publicationChanged) {
            rebuildAvailablePatterns();
            advancePublicationRevision();
        }
        return publicationChanged;
    }

    @Override
    public void onCoreChanged(TrinityPatternCore core, TrinityPatternSlot.Change change) {
        if (!this.layout.active()) {
            return;
        }
        CoreRuntime runtime = this.runtimesByCore.get(core);
        if (runtime == null) {
            return;
        }
        if (change.slot() >= runtime.range.mount().blockCapacity()) {
            throw new IllegalArgumentException(
                    "Mounted Trinity core " + runtime.range.coreId() + " reported out-of-range slot " + change.slot());
        }
        switch (change.kind()) {
            case CATALOG, RUNTIME_BINDING -> this.dirtySlotsByCore
                    .computeIfAbsent(core, ignored -> new HashSet<>())
                    .add(change.slot());
            case WORK -> updateActiveSlot(runtime, change.slot());
            case PERSISTENT -> throw new IllegalArgumentException(
                    "Persistent-only Trinity slot changes must not reach the host catalog");
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return this.availablePatterns;
    }

    @Override
    public List<ActiveSlot> activeSlots() {
        return this.activeSlots;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails,
                               KeyCounter[] inputHolder,
                               long queuedTick,
                               long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("Trinity pattern dispatch count must be positive: " + count);
        }
        if (!this.layout.active() || !(patternDetails instanceof RoutedCraftingPatternDetails routed) || queuedTick < 0L) {
            return false;
        }
        SlotBinding binding = this.routeBindings.get(routed.route());
        if (binding == null) {
            return false;
        }
        TrinityPatternCore core = binding.core;
        CachedPattern cachedPattern = binding.cachedPattern;
        long runtimeBindingRevision = binding.runtimeBindingRevision;
        if (!binding.runtime.matches(binding.runtime.range.mount()) ||
                !core.runtimeBindingsCurrent() ||
                core.revision() != binding.runtime.directoryRevision || cachedPattern == null ||
                cachedPattern.runtimeBindingRevision() != runtimeBindingRevision ||
                binding.routedDetails != routed) {
            return false;
        }

        IMolecularAssemblerSupportedPattern currentPattern = cachedPattern.details();
        if (currentPattern == null) {
            return false;
        }
        if (!routed.getDefinition().equals(cachedPattern.encodedDefinition())) {
            return false;
        }

        KeyCounter[] workingInputs = copyInputCounters(inputHolder);
        if (workingInputs == null) {
            return false;
        }
        InputSignature craftingGrid = createCraftingGridSnapshot(currentPattern, workingInputs);
        if (craftingGrid == null || !allInputsConsumed(workingInputs) ||
                !core.enqueueBatch(
                        binding.route,
                        cachedPattern,
                        runtimeBindingRevision,
                        craftingGrid,
                        queuedTick,
                        count)) {
            return false;
        }
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        if (!this.activeSlotsByGlobalIndex.containsKey(binding.globalIndex)) {
            updateActiveSlot(binding.runtime, binding.route.slot());
        }
        return true;
    }

    @Override
    public List<CoreMount> mountedCores() {
        return this.layout.mounts();
    }

    @Override
    public boolean hasWork() {
        return this.retainedWork || !this.activeSlotsByGlobalIndex.isEmpty();
    }

    @Override
    public boolean hasRefundableState() {
        if (!this.layout.active()) {
            return false;
        }
        for (CoreRange range : this.layout.ranges()) {
            TrinityPatternCore core = range.mount().core();
            if (core.hasWork(this.hostId) || core.hasPendingRefund(this.hostId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PatternRefundResult tryRefundPatterns(TrinityPatternRefundDelivery delivery) {
        LayoutSnapshot capturedLayout = this.layout;
        if (capturedLayout.active() && capturedLayout.mounts().isEmpty()) {
            return PatternRefundResult.NO_PATTERNS;
        }
        if (!isCurrentPatternRefundLayout(capturedLayout)) {
            return PatternRefundResult.STALE;
        }

        ArrayList<PatternRefundCapture> captures = new ArrayList<>(capturedLayout.ranges().size());
        ArrayList<ItemStack> patterns = new ArrayList<>();
        List<ItemStack> undelivered = List.of();
        boolean committed = false;
        try {
            for (CoreRange range : capturedLayout.ranges()) {
                if (!isCurrentPatternRefundLayout(capturedLayout)) {
                    return PatternRefundResult.STALE;
                }
                if (range.mount().core().hasWork()) {
                    return PatternRefundResult.BLOCKED_BY_WORK;
                }
            }
            for (CoreRange range : capturedLayout.ranges()) {
                if (!isCurrentPatternRefundLayout(capturedLayout)) {
                    return PatternRefundResult.STALE;
                }
                TrinityPatternCore.PatternRefundTransaction transaction = range.mount().core().preparePatternRefund();
                captures.add(new PatternRefundCapture(range, transaction, List.of(), List.of()));
                if (transaction.isBlockedByWork()) {
                    return PatternRefundResult.BLOCKED_BY_WORK;
                }
                List<Integer> slots = capturePatternRefundSlots(range);
                List<ItemStack> capturedPatterns = copyPatternStacks(transaction.patterns());
                if (capturedPatterns.size() < slots.size()) {
                    throw new IllegalStateException(
                            "Trinity pattern refund capture is missing installed patterns for core " + range.coreId());
                }
                captures.set(captures.size() - 1, new PatternRefundCapture(
                        range, transaction, slots, capturedPatterns));
                patterns.addAll(capturedPatterns);
            }
            if (patterns.isEmpty()) {
                return PatternRefundResult.NO_PATTERNS;
            }
            if (!isCurrentPatternRefundLayout(capturedLayout)) {
                return PatternRefundResult.STALE;
            }
            boolean deliveryPrepared;
            try {
                deliveryPrepared = delivery.prepare(copyPatternStacks(patterns));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern refund delivery preparation failed for catalog {}",
                        this.hostId,
                        exception);
                return PatternRefundResult.DELIVERY_FAILED;
            }
            if (!deliveryPrepared) {
                return PatternRefundResult.DELIVERY_REJECTED;
            }
            for (PatternRefundCapture capture : captures) {
                if (!isCurrentPatternRefundLayout(capturedLayout) || !capture.transaction().commit()) {
                    return PatternRefundResult.STALE;
                }
            }
            if (!isCurrentPatternRefundLayout(capturedLayout)) {
                return PatternRefundResult.STALE;
            }
            markPatternRefundSlotsDirty(captures);
            refreshChangedPatterns();
            if (!isCurrentPatternRefundLayout(capturedLayout)) {
                return PatternRefundResult.STALE;
            }
            committed = true;
            try {
                undelivered = copyPatternStacks(delivery.deliver(copyPatternStacks(patterns)));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern refund delivery failed after catalog {} committed installed patterns",
                        this.hostId,
                        exception);
                undelivered = copyPatternStacks(patterns);
                return PatternRefundResult.DELIVERY_FAILED;
            }
            return undelivered.isEmpty() ? PatternRefundResult.COMPLETED : PatternRefundResult.DELIVERY_FAILED;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to atomically refund installed Trinity patterns from catalog {}",
                    this.hostId,
                    exception);
            return PatternRefundResult.INTERNAL_ERROR;
        } finally {
            if (committed) {
                completePatternRefundTransactions(captures, copyPatternStacks(patterns), undelivered);
            } else {
                rollbackPatternRefundTransactions(captures);
                refreshRolledBackPatternRefundPublication(capturedLayout, captures);
            }
        }
    }

    @Override
    public boolean tryRefundAll(TrinityRefundDelivery delivery) {
        LayoutSnapshot capturedLayout = this.layout;
        if (!capturedLayout.active() || capturedLayout.mounts().isEmpty()) {
            return false;
        }

        ArrayList<TrinityPatternCore.RefundTransaction> transactions = new ArrayList<>(capturedLayout.mounts().size());
        ArrayList<TrinityItemAmount> refundable = new ArrayList<>();
        List<TrinityItemAmount> offered = List.of();
        List<TrinityItemAmount> undelivered = List.of();
        boolean committed = false;
        try {
            for (CoreRange range : capturedLayout.ranges()) {
                CoreMount mount = range.mount();
                if (!matchesMount(range, mount)) {
                    return false;
                }
                TrinityPatternCore.RefundTransaction transaction = mount.core().prepareRefund(this.hostId);
                transactions.add(transaction);
                refundable.addAll(transaction.refundableItems());
            }
            offered = List.copyOf(refundable);
            if (offered.isEmpty() || this.layout != capturedLayout || !delivery.prepare(offered)) {
                return false;
            }
            for (TrinityPatternCore.RefundTransaction transaction : transactions) {
                if (!transaction.commit()) {
                    return false;
                }
            }
            if (this.layout != capturedLayout) {
                return false;
            }
            committed = true;
            try {
                undelivered = List.copyOf(delivery.deliver(offered));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity refund delivery failed after catalog {} committed queued state",
                        this.hostId,
                        exception);
                undelivered = offered;
                return false;
            }
            return undelivered.isEmpty();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Failed to atomically refund Trinity pattern catalog {}", this.hostId, exception);
            return false;
        } finally {
            if (committed) {
                completeRefundTransactions(transactions, offered, undelivered);
                rebuildActiveSlotsFromMountedWork();
            } else {
                rollbackRefundTransactions(transactions);
            }
        }
    }

    @Override
    public void invalidateLayout() {
        if (this.layout.active()) {
            this.retainedWork |= !this.activeSlotsByGlobalIndex.isEmpty();
            this.layout = new LayoutSnapshot(
                    Math.incrementExact(this.layout.revision()),
                    false,
                    0,
                    List.of(),
                    List.of());
            advancePublicationRevision();
        }
        clearRuntimeIndexes();
    }

    @Override
    public void clear() {
        invalidateLayout();
        this.retainedWork = false;
    }

    private void updateActiveSlot(CoreRuntime runtime, int coreSlot) {
        boolean working = runtime.core().isSlotWorking(this.hostId, coreSlot);
        SlotBinding binding = runtime.bindings.get(coreSlot);
        if (working) {
            if (binding == null) {
                binding = runtime.binding(coreSlot);
            }
            if (this.activeSlotsByGlobalIndex.put(binding.globalIndex, binding.activeSlot) == null) {
                rebuildActiveSlotSnapshot();
            }
        } else if (binding != null && this.activeSlotsByGlobalIndex.remove(binding.globalIndex) != null) {
            rebuildActiveSlotSnapshot();
        }
    }

    private void rebuildActiveSlotSnapshot() {
        this.activeSlots = List.copyOf(this.activeSlotsByGlobalIndex.values());
    }

    private void rebuildActiveSlotsFromMountedWork() {
        this.activeSlotsByGlobalIndex.clear();
        for (CoreRuntime runtime : this.orderedRuntimes) {
            for (int coreSlot : runtime.core().workingSlots(this.hostId)) {
                SlotBinding binding = runtime.binding(coreSlot);
                this.activeSlotsByGlobalIndex.put(binding.globalIndex, binding.activeSlot);
            }
        }
        rebuildActiveSlotSnapshot();
    }

    private void rebuildAvailablePatterns() {
        ArrayList<IPatternDetails> patterns = new ArrayList<>();
        for (CoreRuntime runtime : this.orderedRuntimes) {
            patterns.addAll(runtime.patterns);
        }
        this.availablePatterns = List.copyOf(patterns);
    }

    private void advancePublicationRevision() {
        this.publicationRevision = Math.incrementExact(this.publicationRevision);
    }

    private void clearRuntimeIndexes() {
        this.runtimesByCore.clear();
        this.routeBindings.clear();
        this.dirtySlotsByCore.clear();
        this.activeSlotsByGlobalIndex.clear();
        this.orderedRuntimes = List.of();
        this.availablePatterns = List.of();
        this.activeSlots = List.of();
    }

    private boolean isCurrentPatternRefundLayout(LayoutSnapshot capturedLayout) {
        if (this.layout != capturedLayout || !capturedLayout.active() || capturedLayout.mounts().isEmpty()) {
            return false;
        }
        for (CoreRange range : capturedLayout.ranges()) {
            CoreRuntime runtime = this.runtimesByCore.get(range.mount().core());
            if (runtime == null || runtime.range != range || !runtime.matches(range.mount())) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> capturePatternRefundSlots(CoreRange range) {
        List<Integer> occupiedSlots = range.mount().core().occupiedPatternSlots();
        ArrayList<Integer> capturedSlots = new ArrayList<>(occupiedSlots.size());
        int previousSlot = -1;
        for (int slot : occupiedSlots) {
            if (slot <= previousSlot || slot >= range.mount().blockCapacity()) {
                throw new IllegalStateException(
                        "Trinity pattern refund received invalid occupied slot " + slot + " from core " + range.coreId());
            }
            capturedSlots.add(slot);
            previousSlot = slot;
        }
        return List.copyOf(capturedSlots);
    }

    private static List<ItemStack> copyPatternStacks(List<ItemStack> patterns) {
        ArrayList<ItemStack> copies = new ArrayList<>(patterns.size());
        for (ItemStack pattern : patterns) {
            if (pattern.isEmpty()) {
                throw new IllegalStateException("Trinity pattern refund cannot deliver an empty installed pattern");
            }
            copies.add(pattern.copy());
        }
        return List.copyOf(copies);
    }

    private void markPatternRefundSlotsDirty(List<PatternRefundCapture> captures) {
        for (PatternRefundCapture capture : captures) {
            if (!capture.slots().isEmpty()) {
                this.dirtySlotsByCore
                        .computeIfAbsent(capture.range().mount().core(), ignored -> new HashSet<>())
                        .addAll(capture.slots());
            }
        }
    }

    private void refreshRolledBackPatternRefundPublication(LayoutSnapshot capturedLayout,
                                                           List<PatternRefundCapture> captures) {
        if (!isCurrentPatternRefundLayout(capturedLayout)) {
            return;
        }
        try {
            markPatternRefundSlotsDirty(captures);
            refreshChangedPatterns();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to refresh Trinity pattern publication after catalog {} rolled back a pattern refund",
                    this.hostId,
                    exception);
        }
    }

    private RebuildResult rejectScan(BlockPos position, String reason) {
        boolean changed = this.layout.active();
        invalidateLayout();
        return new RebuildResult(false, changed, position, reason);
    }

    private void rollbackRefundTransactions(List<TrinityPatternCore.RefundTransaction> transactions) {
        for (int index = transactions.size() - 1; index >= 0; index--) {
            try {
                transactions.get(index).rollback();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to roll back Trinity pattern refund transaction in catalog {}",
                        this.hostId,
                        exception);
            }
        }
    }

    private void completeRefundTransactions(List<TrinityPatternCore.RefundTransaction> transactions,
                                            List<TrinityItemAmount> offered,
                                            List<TrinityItemAmount> undelivered) {
        List<TrinityItemAmount> remaining = isValidRetainedUndeliveredSuffix(offered, undelivered) ? undelivered : offered;
        int offeredCount = 0;
        for (TrinityPatternCore.RefundTransaction transaction : transactions) {
            offeredCount = Math.addExact(offeredCount, transaction.refundableItems().size());
        }
        if (offeredCount != offered.size()) {
            Data_Energistics.LOGGER.error(
                    "Trinity retained refund transaction captures changed before catalog {} could finalize delivery",
                    this.hostId);
            for (TrinityPatternCore.RefundTransaction transaction : transactions) {
                try {
                    transaction.complete(transaction.refundableItems());
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "Failed to preserve Trinity retained refund transaction in catalog {}",
                            this.hostId,
                            exception);
                }
            }
            return;
        }

        int undeliveredStart = offered.size() - remaining.size();
        int cursor = 0;
        for (TrinityPatternCore.RefundTransaction transaction : transactions) {
            int nextCursor = Math.addExact(cursor, transaction.refundableItems().size());
            List<TrinityItemAmount> transactionUndelivered = List.of();
            if (undeliveredStart < nextCursor) {
                int first = Math.max(cursor, undeliveredStart);
                transactionUndelivered = List.copyOf(remaining.subList(first - undeliveredStart,
                        nextCursor - undeliveredStart));
            }
            try {
                transaction.complete(transactionUndelivered);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to finalize Trinity pattern refund transaction in catalog {}",
                        this.hostId,
                        exception);
            }
            cursor = nextCursor;
        }
    }

    private void rollbackPatternRefundTransactions(List<PatternRefundCapture> captures) {
        for (int index = captures.size() - 1; index >= 0; index--) {
            try {
                captures.get(index).transaction().rollback();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to roll back Trinity installed-pattern refund transaction in catalog {}",
                        this.hostId,
                        exception);
            }
        }
    }

    private void completePatternRefundTransactions(List<PatternRefundCapture> captures,
                                                   List<ItemStack> offered,
                                                   List<ItemStack> undelivered) {
        List<ItemStack> remaining = isValidPatternUndeliveredSuffix(offered, undelivered) ? undelivered : offered;
        int offeredCount = 0;
        for (PatternRefundCapture capture : captures) {
            offeredCount = Math.addExact(offeredCount, capture.offeredPatterns().size());
        }
        if (offeredCount != offered.size()) {
            Data_Energistics.LOGGER.error(
                    "Trinity installed-pattern refund captures changed before catalog {} could finalize delivery",
                    this.hostId);
            for (PatternRefundCapture capture : captures) {
                try {
                    capture.transaction().complete(capture.offeredPatterns());
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "Failed to preserve Trinity installed-pattern refund transaction in catalog {}",
                            this.hostId,
                            exception);
                }
            }
            return;
        }

        int undeliveredStart = offered.size() - remaining.size();
        int cursor = 0;
        for (PatternRefundCapture capture : captures) {
            int nextCursor = Math.addExact(cursor, capture.offeredPatterns().size());
            List<ItemStack> captureUndelivered = List.of();
            if (undeliveredStart < nextCursor) {
                int first = Math.max(cursor, undeliveredStart);
                captureUndelivered = copyPatternStacks(remaining.subList(first - undeliveredStart,
                        nextCursor - undeliveredStart));
            }
            try {
                capture.transaction().complete(captureUndelivered);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to finalize Trinity installed-pattern refund transaction in catalog {}",
                        this.hostId,
                        exception);
            }
            cursor = nextCursor;
        }
    }

    private boolean isValidRetainedUndeliveredSuffix(List<TrinityItemAmount> offered,
                                                     List<TrinityItemAmount> undelivered) {
        if (undelivered.size() > offered.size()) {
            Data_Energistics.LOGGER.error(
                    "Trinity retained refund delivery returned more items than catalog {} offered",
                    this.hostId);
            return false;
        }
        int start = offered.size() - undelivered.size();
        if (undelivered.isEmpty()) {
            return true;
        }
        TrinityItemAmount offeredFirst = offered.get(start);
        TrinityItemAmount remainingFirst = undelivered.getFirst();
        if (!offeredFirst.key().equals(remainingFirst.key()) || remainingFirst.amount() > offeredFirst.amount()) {
            Data_Energistics.LOGGER.error(
                    "Trinity retained refund delivery returned an invalid remaining prefix for catalog {}",
                    this.hostId);
            return false;
        }
        for (int index = 1; index < undelivered.size(); index++) {
            if (!offered.get(start + index).equals(undelivered.get(index))) {
                Data_Energistics.LOGGER.error(
                        "Trinity retained refund delivery changed remaining item order for catalog {}",
                        this.hostId);
                return false;
            }
        }
        return true;
    }

    private boolean isValidPatternUndeliveredSuffix(List<ItemStack> offered, List<ItemStack> undelivered) {
        if (undelivered.size() > offered.size()) {
            Data_Energistics.LOGGER.error(
                    "Trinity installed-pattern refund delivery returned more patterns than catalog {} offered",
                    this.hostId);
            return false;
        }
        int start = offered.size() - undelivered.size();
        for (int index = 0; index < undelivered.size(); index++) {
            if (!ItemStack.matches(offered.get(start + index), undelivered.get(index))) {
                Data_Energistics.LOGGER.error(
                        "Trinity installed-pattern refund delivery changed remaining pattern order for catalog {}",
                        this.hostId);
                return false;
            }
        }
        return true;
    }

    private static List<CoreRange> createRanges(List<CoreMount> mounts) {
        ArrayList<CoreRange> ranges = new ArrayList<>(mounts.size());
        int firstGlobalIndex = 0;
        for (CoreMount mount : mounts) {
            int lastGlobalIndexExclusive = Math.addExact(firstGlobalIndex, mount.blockCapacity());
            ranges.add(new CoreRange(
                    mount,
                    mount.core().coreId(),
                    firstGlobalIndex,
                    lastGlobalIndexExclusive));
            firstGlobalIndex = lastGlobalIndexExclusive;
        }
        return List.copyOf(ranges);
    }

    private boolean hasSameActiveLayout(List<CoreRange> nextRanges) {
        if (!this.layout.active() || this.layout.ranges().size() != nextRanges.size()) {
            return false;
        }
        for (int index = 0; index < nextRanges.size(); index++) {
            CoreRange current = this.layout.ranges().get(index);
            CoreRange next = nextRanges.get(index);
            if (current.mount().core() != next.mount().core() ||
                    !current.coreId().equals(next.coreId()) ||
                    !current.mount().position().equals(next.mount().position()) ||
                    current.mount().blockCapacity() != next.mount().blockCapacity() ||
                    current.firstGlobalIndex() != next.firstGlobalIndex() ||
                    current.lastGlobalIndexExclusive() != next.lastGlobalIndexExclusive()) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesMount(CoreRange range, CoreMount mount) {
        TrinityPatternCore core = mount.core();
        return range.mount().core() == core &&
                range.coreId().equals(core.coreId()) &&
                range.mount().position().equals(mount.position()) &&
                range.mount().blockCapacity() == mount.blockCapacity() &&
                mount.blockCapacity() == core.patternCapacity();
    }

    @Nullable
    private static KeyCounter[] copyInputCounters(KeyCounter[] inputHolder) {
        KeyCounter[] copies = new KeyCounter[inputHolder.length];
        for (int index = 0; index < inputHolder.length; index++) {
            KeyCounter source = inputHolder[index];
            KeyCounter copy = new KeyCounter();
            for (var entry : source) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0L) {
                    return null;
                }
                copy.add(key, amount);
            }
            copies[index] = copy;
        }
        return copies;
    }

    @Nullable
    private static InputSignature createCraftingGridSnapshot(IMolecularAssemblerSupportedPattern pattern,
                                                             KeyCounter[] workingInputs) {
        ArrayList<ItemStack> craftingGrid = new ArrayList<>(CRAFTING_GRID_SLOT_COUNT);
        boolean[] populated = new boolean[CRAFTING_GRID_SLOT_COUNT];
        for (int slot = 0; slot < CRAFTING_GRID_SLOT_COUNT; slot++) {
            craftingGrid.add(ItemStack.EMPTY);
        }
        try {
            pattern.fillCraftingGrid(workingInputs, (slot, stack) -> {
                if (slot < 0 || slot >= CRAFTING_GRID_SLOT_COUNT) {
                    throw new IllegalArgumentException("Crafting pattern wrote out-of-range grid slot " + slot);
                }
                if (populated[slot]) {
                    throw new IllegalArgumentException("Crafting pattern wrote grid slot " + slot + " more than once");
                }
                if (!stack.isEmpty() && (stack.getCount() <= 0 || stack.getCount() > stack.getMaxStackSize())) {
                    throw new IllegalArgumentException(
                            "Crafting pattern wrote an invalid stack count to grid slot " + slot);
                }
                populated[slot] = true;
                craftingGrid.set(slot, stack.copy());
            });
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Failed to materialize a Trinity crafting pattern input grid", exception);
            return null;
        }
        if (craftingGrid.stream().allMatch(ItemStack::isEmpty)) {
            return null;
        }
        return InputSignature.takeOwnership(craftingGrid);
    }

    private static boolean allInputsConsumed(KeyCounter[] inputs) {
        for (KeyCounter input : inputs) {
            input.removeZeros();
            if (!input.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private final class CoreRuntime {

        private final CoreRange range;
        private final Map<Integer, SlotBinding> bindings = new HashMap<>();
        private final TreeMap<Integer, SlotBinding> publishedBindingsBySlot = new TreeMap<>();
        private long directoryRevision;
        private List<IPatternDetails> patterns = List.of();

        private CoreRuntime(CoreRange range) {
            this.range = range;
        }

        private TrinityPatternCore core() {
            return this.range.mount().core();
        }

        private boolean matches(CoreMount mount) {
            return matchesMount(this.range, mount);
        }

        private SlotBinding binding(int coreSlot) {
            if (coreSlot < 0 || coreSlot >= this.range.mount().blockCapacity()) {
                throw new IllegalArgumentException(
                        "Trinity pattern slot out of range for mounted core " + this.range.coreId() + ": " + coreSlot);
            }
            SlotBinding existing = this.bindings.get(coreSlot);
            if (existing != null) {
                return existing;
            }
            TrinityPatternSlot slot = core().patternSlot(coreSlot);
            if (slot.index() != coreSlot) {
                throw new IllegalStateException(
                        "Trinity core " + this.range.coreId() + " returned mismatched stable slot " + slot.index());
            }
            int globalIndex = Math.addExact(this.range.firstGlobalIndex(), coreSlot);
            SlotBinding created = new SlotBinding(
                    this,
                    globalIndex,
                    new PatternRoute(hostId, this.range.coreId(), coreSlot),
                    slot);
            this.bindings.put(coreSlot, created);
            return created;
        }

        private PreparedPublication preparePublication(PatternCacheSnapshot snapshot) {
            ArrayList<SlotBinding> nextBindings = new ArrayList<>(snapshot.patterns().size());
            ArrayList<IPatternDetails> nextPatterns = new ArrayList<>(snapshot.patterns().size());
            for (CachedPattern cachedPattern : snapshot.patterns()) {
                SlotBinding binding = binding(cachedPattern.slot());
                binding.cachedPattern = cachedPattern;
                binding.runtimeBindingRevision = cachedPattern.runtimeBindingRevision();
                IMolecularAssemblerSupportedPattern details = cachedPattern.details();
                if (details == null) {
                    continue;
                }
                RoutedCraftingPatternDetails routed = new RoutedCraftingPatternDetails(binding.route, details);
                binding.routedDetails = routed;
                this.publishedBindingsBySlot.put(cachedPattern.slot(), binding);
                nextBindings.add(binding);
                nextPatterns.add(routed);
            }
            this.directoryRevision = snapshot.revision();
            this.patterns = List.copyOf(nextPatterns);
            return new PreparedPublication(List.copyOf(nextBindings), this.patterns);
        }

        private boolean applyChangedPublication(Set<Integer> dirtySlots) {
            ArrayList<SlotPublication> changedPublications = new ArrayList<>(dirtySlots.size());
            for (int slot : new TreeSet<>(dirtySlots)) {
                SlotBinding binding = binding(slot);
                CachedPattern cachedPattern = core().cachedPattern(slot);
                RoutedCraftingPatternDetails current = binding.routedDetails;
                RoutedCraftingPatternDetails next = current;
                boolean bindingChanged = cachedPattern != null &&
                        (binding.cachedPattern != cachedPattern ||
                                binding.runtimeBindingRevision != cachedPattern.runtimeBindingRevision());
                if (cachedPattern == null || cachedPattern.details() == null) {
                    next = null;
                } else if (bindingChanged || current == null) {
                    next = new RoutedCraftingPatternDetails(binding.route, cachedPattern.details());
                }
                binding.cachedPattern = cachedPattern;
                binding.runtimeBindingRevision = cachedPattern == null ? -1L :
                        cachedPattern.runtimeBindingRevision();
                if (current != next) {
                    changedPublications.add(new SlotPublication(binding, next));
                }
            }
            for (SlotPublication publication : changedPublications) {
                SlotBinding binding = publication.binding();
                SlotBinding existing = routeBindings.get(binding.route);
                if (publication.details() == null) {
                    if (existing != binding) {
                        throw new IllegalStateException(
                                "Missing published Trinity route during slot refresh: " + binding.route);
                    }
                } else if (existing != null && existing != binding) {
                    throw new IllegalStateException(
                            "Duplicate Trinity route while refreshing catalog slot: " + binding.route);
                }
            }

            for (SlotPublication publication : changedPublications) {
                SlotBinding binding = publication.binding();
                RoutedCraftingPatternDetails details = publication.details();
                if (details == null) {
                    routeBindings.remove(binding.route, binding);
                    this.publishedBindingsBySlot.remove(binding.route.slot());
                } else {
                    routeBindings.put(binding.route, binding);
                    this.publishedBindingsBySlot.put(binding.route.slot(), binding);
                }
                binding.routedDetails = details;
            }
            this.directoryRevision = core().revision();
            if (!changedPublications.isEmpty()) {
                this.patterns = this.publishedBindingsBySlot.values().stream()
                        .map(binding -> (IPatternDetails) binding.routedDetails)
                        .toList();
            }
            return !changedPublications.isEmpty();
        }
    }

    private static final class SlotBinding {

        private final CoreRuntime runtime;
        private final TrinityPatternCore core;
        private final int globalIndex;
        private final PatternRoute route;
        private final TrinityPatternSlot slot;
        private final ActiveSlot activeSlot;
        @Nullable
        private RoutedCraftingPatternDetails routedDetails;
        @Nullable
        private CachedPattern cachedPattern;
        private long runtimeBindingRevision = -1L;

        private SlotBinding(CoreRuntime runtime, int globalIndex, PatternRoute route, TrinityPatternSlot slot) {
            this.runtime = runtime;
            this.core = runtime.core();
            this.globalIndex = globalIndex;
            this.route = route;
            this.slot = slot;
            this.activeSlot = new ActiveSlot(globalIndex, runtime.range.mount(), route, slot);
        }
    }

    private record PreparedPublication(List<SlotBinding> bindings,
                                       List<IPatternDetails> patterns) {}

    private record SlotPublication(SlotBinding binding, @Nullable RoutedCraftingPatternDetails details) {}

    private record PatternRefundCapture(CoreRange range,
                                        TrinityPatternCore.PatternRefundTransaction transaction,
                                        List<Integer> slots,
                                        List<ItemStack> offeredPatterns) {

        private PatternRefundCapture {
            slots = List.copyOf(slots);
            offeredPatterns = copyPatternStacks(offeredPatterns);
        }
    }
}
