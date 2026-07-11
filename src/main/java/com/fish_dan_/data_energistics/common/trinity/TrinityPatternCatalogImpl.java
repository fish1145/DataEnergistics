package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Default validated and revision-aware implementation of {@link TrinityPatternCatalog}. */
public final class TrinityPatternCatalogImpl implements TrinityPatternCatalog {

    private static final int CRAFTING_GRID_SLOT_COUNT = 9;

    /** Initial inactive topology supplied before the first successful structure scan. */
    private static final LayoutSnapshot EMPTY_LAYOUT = new LayoutSnapshot(0L, false, 0, List.of(), List.of());

    private final UUID hostId;
    private final Map<UUID, CorePatternCache> coreCaches = new HashMap<>();
    private LayoutSnapshot layout = EMPTY_LAYOUT;
    private List<CoreMount> retainedWorkCores = List.of();
    private Map<UUID, CoreMount> coresById = Map.of();
    private List<IPatternDetails> availablePatterns = List.of();

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
    public boolean isMountCurrent(long expectedRevision, CoreMount mount) {
        LayoutSnapshot currentLayout = this.layout;
        if (!currentLayout.active() || currentLayout.revision() != expectedRevision) {
            return false;
        }
        for (CoreRange range : currentLayout.ranges()) {
            if (matchesMount(range, mount)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public GlobalSlot resolveGlobalSlot(long expectedRevision, int globalIndex) {
        LayoutSnapshot currentLayout = this.layout;
        if (!currentLayout.active() || currentLayout.revision() != expectedRevision ||
                globalIndex < 0 || globalIndex >= currentLayout.slotCount()) {
            return null;
        }
        for (CoreRange range : currentLayout.ranges()) {
            if (!range.contains(globalIndex)) {
                continue;
            }
            CoreMount mount = range.mount();
            if (!matchesMount(range, mount)) {
                return null;
            }
            return new GlobalSlot(expectedRevision, globalIndex, range,
                    globalIndex - range.firstGlobalIndex());
        }
        throw new IllegalStateException("Active Trinity pattern layout did not resolve global slot " + globalIndex);
    }

    @Override
    public RebuildResult rebuild(List<CoreMount> mounts) {
        ArrayList<CoreMount> sorted = new ArrayList<>(mounts);
        sorted.sort((left, right) -> left.position().compareTo(right.position()));

        Set<BlockPos> positions = new HashSet<>();
        Map<UUID, CoreMount> scannedById = new HashMap<>();
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
            CoreMount previous = scannedById.putIfAbsent(mount.core().coreId(), mount);
            if (previous != null) {
                return rejectScan(
                        mount.position(),
                        "Duplicate Trinity pattern core UUID " + mount.core().coreId() + " at " +
                                previous.position() + " and " + mount.position());
            }
        }

        List<CoreMount> nextMounts = List.copyOf(sorted);
        List<CoreRange> nextRanges = createRanges(nextMounts);
        boolean layoutChanged = !hasSameActiveLayout(nextMounts, nextRanges);
        long nextRevision = layoutChanged ? Math.incrementExact(this.layout.revision()) : this.layout.revision();
        int slotCount = nextRanges.isEmpty() ? 0 : nextRanges.getLast().lastGlobalIndexExclusive();
        this.layout = new LayoutSnapshot(nextRevision, true, slotCount, nextMounts, nextRanges);
        this.retainedWorkCores = nextMounts;
        this.coresById = Map.copyOf(scannedById);
        this.coreCaches.keySet().retainAll(scannedById.keySet());
        boolean patternChanged = refreshChangedPatterns();
        if (layoutChanged && !patternChanged) {
            rebuildAvailablePatterns();
        }
        return new RebuildResult(true, layoutChanged || patternChanged, null, "");
    }

    @Override
    public boolean refreshChangedPatterns() {
        LayoutSnapshot currentLayout = this.layout;
        if (!currentLayout.active()) {
            return false;
        }
        for (int index = 0; index < currentLayout.ranges().size(); index++) {
            CoreRange range = currentLayout.ranges().get(index);
            CoreMount mount = currentLayout.mounts().get(index);
            if (!matchesMount(range, mount)) {
                TrinityPatternCore core = mount.core();
                Data_Energistics.LOGGER.warn(
                        "Invalidating Trinity pattern catalog {} after mounted core identity changed at {}: " +
                                "captured UUID {}, current UUID {}, block capacity {}, core capacity {}",
                        this.hostId,
                        mount.position(),
                        range.coreId(),
                        core.coreId(),
                        mount.blockCapacity(),
                        core.patternCapacity());
                invalidateLayout();
                return true;
            }
        }

        boolean changed = false;
        for (CoreMount mount : currentLayout.mounts()) {
            TrinityPatternCore core = mount.core();
            CorePatternCache cache = this.coreCaches.get(core.coreId());
            if (cache == null || cache.core() != core || cache.revision() != core.revision()) {
                this.coreCaches.put(core.coreId(), createCoreCache(core));
                changed = true;
            }
        }
        if (changed) {
            rebuildAvailablePatterns();
        }
        return changed;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        refreshChangedPatterns();
        return this.layout.active() ? this.availablePatterns : List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, long queuedTick) {
        if (!this.layout.active() || !(patternDetails instanceof RoutedCraftingPatternDetails routed) || queuedTick < 0L) {
            return false;
        }
        PatternRoute route = routed.route();
        if (!this.hostId.equals(route.hostId())) {
            return false;
        }

        CoreMount mount = this.coresById.get(route.coreId());
        if (mount == null || route.slot() < 0 || route.slot() >= mount.core().patternCapacity()) {
            return false;
        }
        TrinityPatternCore core = mount.core();
        IMolecularAssemblerSupportedPattern currentPattern = core.decodedPattern(route.slot());
        if (currentPattern == null || !(routed.delegate() instanceof IMolecularAssemblerSupportedPattern supported)) {
            return false;
        }

        AEItemKey routedDefinition = routed.getDefinition();
        AEItemKey currentDefinition = currentPattern.getDefinition();
        if (!routedDefinition.equals(currentDefinition) ||
                !routedDefinition.equals(supported.getDefinition())) {
            return false;
        }
        ItemStack patternSnapshot = core.pattern(route.slot());
        if (patternSnapshot.isEmpty() ||
                !ItemStack.isSameItemSameComponents(patternSnapshot, currentDefinition.toStack())) {
            return false;
        }

        KeyCounter[] workingInputs = copyInputCounters(inputHolder);
        if (workingInputs == null) {
            return false;
        }
        List<ItemStack> craftingGrid = createCraftingGridSnapshot(currentPattern, workingInputs);
        if (craftingGrid == null || !allInputsConsumed(workingInputs)) {
            return false;
        }

        if (!core.enqueueBatch(route, patternSnapshot, craftingGrid, queuedTick)) {
            return false;
        }
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        return true;
    }

    @Override
    public List<CoreMount> mountedCores() {
        return this.layout.mounts();
    }

    @Override
    public boolean hasWork() {
        for (CoreMount mount : this.retainedWorkCores) {
            TrinityPatternCore core = mount.core();
            for (int slot = 0; slot < core.patternCapacity(); slot++) {
                PatternRoute route = new PatternRoute(this.hostId, core.coreId(), slot);
                if (!core.pendingOutputs(route).isEmpty()) {
                    return true;
                }
                for (TrinityCraftingBatch batch : core.queuedBatches(slot)) {
                    if (this.hostId.equals(batch.route().hostId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasRefundableState() {
        LayoutSnapshot currentLayout = this.layout;
        if (!currentLayout.active()) {
            return false;
        }
        for (CoreRange range : currentLayout.ranges()) {
            CoreMount mount = range.mount();
            if (!matchesMount(range, mount)) {
                return false;
            }
            TrinityPatternCore core = mount.core();
            if (hasRefundableStateForHost(core)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean tryRefundAll(TrinityRefundDelivery delivery) {
        LayoutSnapshot capturedLayout = this.layout;
        if (!capturedLayout.active() || capturedLayout.mounts().isEmpty()) {
            return false;
        }

        ArrayList<TrinityPatternCore.RefundTransaction> transactions = new ArrayList<>(capturedLayout.mounts().size());
        ArrayList<ItemStack> refundable = new ArrayList<>();
        boolean committed = false;
        try {
            for (CoreRange range : capturedLayout.ranges()) {
                CoreMount mount = range.mount();
                if (!matchesMount(range, mount)) {
                    return false;
                }
                TrinityPatternCore.RefundTransaction transaction = mount.core().prepareRefund(this.hostId);
                transactions.add(transaction);
                for (ItemStack stack : transaction.refundableStacks()) {
                    if (stack.isEmpty()) {
                        throw new IllegalStateException("Trinity pattern core " + mount.core().coreId() +
                                " exposed an empty refund stack");
                    }
                    refundable.add(stack.copy());
                }
            }
            List<ItemStack> offered = List.copyOf(refundable);
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
                delivery.deliver(offered);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity refund delivery failed after catalog {} committed queued state",
                        this.hostId,
                        exception);
                // Every core transaction already committed, so callers must refresh rather than advertise a retry.
                return true;
            }
            return true;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Failed to atomically refund Trinity pattern catalog {}", this.hostId, exception);
            return false;
        } finally {
            if (committed) {
                completeRefundTransactions(transactions);
            } else {
                rollbackRefundTransactions(transactions);
            }
        }
    }

    @Override
    public void invalidateLayout() {
        if (this.layout.active()) {
            LayoutSnapshot currentLayout = this.layout;
            LayoutSnapshot invalidLayout = new LayoutSnapshot(
                    Math.incrementExact(currentLayout.revision()),
                    false,
                    0,
                    List.of(),
                    List.of());
            this.retainedWorkCores = currentLayout.mounts();
            this.layout = invalidLayout;
        }
        this.coresById = Map.of();
        this.coreCaches.clear();
        this.availablePatterns = List.of();
    }

    @Override
    public void clear() {
        invalidateLayout();
        this.retainedWorkCores = List.of();
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

    private void completeRefundTransactions(List<TrinityPatternCore.RefundTransaction> transactions) {
        for (TrinityPatternCore.RefundTransaction transaction : transactions) {
            try {
                transaction.complete();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to finalize Trinity pattern refund transaction in catalog {}",
                        this.hostId,
                        exception);
            }
        }
    }

    private boolean hasRefundableStateForHost(TrinityPatternCore core) {
        for (int slot = 0; slot < core.patternCapacity(); slot++) {
            PatternRoute route = new PatternRoute(this.hostId, core.coreId(), slot);
            if (!core.pendingOutputs(route).isEmpty()) {
                return true;
            }
            for (TrinityCraftingBatch batch : core.queuedBatches(slot)) {
                if (this.hostId.equals(batch.route().hostId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CoreRange> createRanges(List<CoreMount> mounts) {
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

    private boolean hasSameActiveLayout(List<CoreMount> nextMounts, List<CoreRange> nextRanges) {
        if (!this.layout.active() || this.layout.ranges().size() != nextRanges.size()) {
            return false;
        }
        for (int index = 0; index < nextRanges.size(); index++) {
            CoreRange current = this.layout.ranges().get(index);
            CoreRange next = nextRanges.get(index);
            CoreMount currentMount = current.mount();
            CoreMount nextMount = nextMounts.get(index);
            if (currentMount.core() != nextMount.core() ||
                    !current.coreId().equals(next.coreId()) ||
                    !currentMount.position().equals(nextMount.position()) ||
                    currentMount.blockCapacity() != nextMount.blockCapacity() ||
                    current.firstGlobalIndex() != next.firstGlobalIndex() ||
                    current.lastGlobalIndexExclusive() != next.lastGlobalIndexExclusive()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesMount(CoreRange range, CoreMount mount) {
        CoreMount capturedMount = range.mount();
        TrinityPatternCore core = mount.core();
        return capturedMount.core() == core &&
                range.coreId().equals(core.coreId()) &&
                capturedMount.position().equals(mount.position()) &&
                capturedMount.blockCapacity() == mount.blockCapacity() &&
                capturedMount.blockCapacity() == core.patternCapacity();
    }

    private CorePatternCache createCoreCache(TrinityPatternCore core) {
        ArrayList<IPatternDetails> patterns = new ArrayList<>();
        for (int slot = 0; slot < core.patternCapacity(); slot++) {
            IMolecularAssemblerSupportedPattern details = core.decodedPattern(slot);
            if (details != null) {
                patterns.add(new RoutedCraftingPatternDetails(
                        new PatternRoute(this.hostId, core.coreId(), slot),
                        details));
            }
        }
        return new CorePatternCache(core, core.revision(), List.copyOf(patterns));
    }

    private void rebuildAvailablePatterns() {
        ArrayList<IPatternDetails> patterns = new ArrayList<>();
        for (CoreMount mount : this.layout.mounts()) {
            CorePatternCache cache = this.coreCaches.get(mount.core().coreId());
            if (cache == null) {
                throw new IllegalStateException("Missing pattern cache for mounted Trinity core " + mount.core().coreId());
            }
            patterns.addAll(cache.patterns());
        }
        this.availablePatterns = List.copyOf(patterns);
    }

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

    private static List<ItemStack> createCraftingGridSnapshot(IMolecularAssemblerSupportedPattern pattern,
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
                    throw new IllegalArgumentException("Crafting pattern wrote an invalid stack count to grid slot " + slot);
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
        return List.copyOf(craftingGrid);
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

    private record CorePatternCache(TrinityPatternCore core, long revision, List<IPatternDetails> patterns) {}
}
