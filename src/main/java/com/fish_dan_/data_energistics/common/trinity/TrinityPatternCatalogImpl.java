package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

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

    private final UUID hostId;
    private final Map<UUID, CorePatternCache> coreCaches = new HashMap<>();
    private List<CoreMount> mountedCores = List.of();
    private Map<UUID, CoreMount> coresById = Map.of();
    private List<IPatternDetails> availablePatterns = List.of();
    private boolean valid;

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
        boolean mountSetChanged = !this.mountedCores.equals(nextMounts);
        boolean validityChanged = !this.valid;
        this.mountedCores = nextMounts;
        this.coresById = Map.copyOf(scannedById);
        this.coreCaches.keySet().retainAll(scannedById.keySet());
        this.valid = true;
        boolean patternChanged = refreshChangedPatterns();
        if (mountSetChanged && !patternChanged) {
            rebuildAvailablePatterns();
        }
        return new RebuildResult(true, validityChanged || mountSetChanged || patternChanged, null, "");
    }

    @Override
    public boolean refreshChangedPatterns() {
        boolean changed = false;
        for (CoreMount mount : this.mountedCores) {
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
        return this.valid ? this.availablePatterns : List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, long queuedTick) {
        if (!this.valid || !(patternDetails instanceof RoutedCraftingPatternDetails routed) || queuedTick < 0L) {
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
        return this.mountedCores;
    }

    @Override
    public boolean hasWork() {
        for (CoreMount mount : this.mountedCores) {
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
    public void clear() {
        this.valid = false;
        this.mountedCores = List.of();
        this.coresById = Map.of();
        this.coreCaches.clear();
        this.availablePatterns = List.of();
    }

    private RebuildResult rejectScan(BlockPos position, String reason) {
        boolean changed = this.valid;
        this.valid = false;
        return new RebuildResult(false, changed, position, reason);
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
        for (CoreMount mount : this.mountedCores) {
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
