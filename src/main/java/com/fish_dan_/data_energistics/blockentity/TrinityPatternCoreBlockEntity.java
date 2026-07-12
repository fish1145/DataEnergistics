package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityCraftingBatch;
import com.fish_dan_.data_energistics.common.trinity.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreReloadEpoch;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouter.PendingOutputCursor;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternRecipeIdResolvers;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternSlot;
import com.fish_dan_.data_energistics.common.trinity.TrinityRefundDelivery;
import com.fish_dan_.data_energistics.common.trinity.TrinityRefundDeliveryImpl;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.menu.AutoCraftingMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent block entity for one independently movable Trinity P core.
 *
 * <p>
 * Network publication and host output routing intentionally live outside this class. The block entity owns only
 * local validation, recipe execution, movable state, and the public {@link TrinityPatternCore} contract.
 */
public final class TrinityPatternCoreBlockEntity extends AEBaseBlockEntity implements TrinityPatternCore {

    /** Tracks whether level-dependent pattern state has been committed or must remain quarantined. */
    private enum CoreLoadState {
        NEW,
        READY,
        STAGED,
        REJECTED
    }

    private final TrinityPatternCoreImpl core;
    private long observedReloadEpoch = TrinityPatternCoreReloadEpoch.current();
    private CoreLoadState coreLoadState = CoreLoadState.NEW;
    @Nullable
    private CompoundTag stagedCoreState;
    @Nullable
    private TrinityPatternCoreHost patternHost;

    /**
     * Creates a core and derives its fixed inventory size directly from the placed block metadata.
     *
     * @param pos   block position
     * @param state P-core block state
     */
    public TrinityPatternCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRINITY_PATTERN_CORE_BLOCK_ENTITY.get(), pos, state);
        this.core = new TrinityPatternCoreImpl(
                patternCapacityFromState(state),
                this::decodeSupportedPattern,
                TrinityPatternRecipeIdResolvers.global(),
                this::onCoreChanged);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    @Override
    public void setLevel(Level level) {
        boolean levelChanged = this.level != level;
        super.setLevel(level);
        if (this.coreLoadState == CoreLoadState.STAGED) {
            if (tryLoadCoreState(this.stagedCoreState, level.registryAccess())) {
                this.stagedCoreState = null;
                this.coreLoadState = CoreLoadState.READY;
            } else {
                this.coreLoadState = CoreLoadState.REJECTED;
            }
        }
        this.observedReloadEpoch = TrinityPatternCoreReloadEpoch.current();
        if (isCoreStateReady() && levelChanged) {
            refreshPatternCachesWithDiagnostics();
        }
    }

    /**
     * Reports whether persisted state passed level-aware recipe identity validation.
     *
     * @return whether this core may publish, execute, bind, or open its inventory
     */
    public boolean isCoreStateReady() {
        return this.coreLoadState == CoreLoadState.NEW || this.coreLoadState == CoreLoadState.READY;
    }

    /**
     * Refreshes retained recipes after data reloads. Crafting execution is owned by the active Trinity host so an
     * unbound, offline, or structurally invalid core cannot craft independently.
     */
    public void serverTick() {
        if (isCoreStateReady()) {
            this.coreLoadState = CoreLoadState.READY;
            ensurePatternCachesCurrent();
        }
    }

    @Override
    public void ensurePatternCachesCurrent() {
        if (!isCoreStateReady()) {
            return;
        }
        long reloadEpoch = TrinityPatternCoreReloadEpoch.current();
        if (this.observedReloadEpoch != reloadEpoch) {
            this.observedReloadEpoch = reloadEpoch;
            refreshPatternCachesWithDiagnostics();
        }
    }

    /**
     * Executes batches owned by the active host. Batches and pending outputs belonging to another host remain asleep.
     *
     * @param hostId      stable identity of the host currently authorized to execute this core
     * @param currentTick current server tick
     * @return number of completed batches
     */
    public int executeOwnedBatches(UUID hostId, long currentTick) {
        if (!isCoreStateReady()) {
            return 0;
        }
        ensurePatternCachesCurrent();
        return this.core.executeReadyBatches(currentTick, (slot, batch) -> hostId.equals(batch.route().hostId()) ? executeBatch(slot, batch) : BatchExecutionResult.paused());
    }

    /**
     * Reports whether this physical core can join a host without stealing an active catalog binding.
     *
     * @param host host attempting to mount this core
     * @return whether the existing binding is absent, stale, or already owned by that host
     */
    public boolean canBindPatternHost(TrinityPatternCoreHost host) {
        if (!isCoreStateReady()) {
            return false;
        }
        TrinityPatternCoreHost current = currentPatternHost();
        return current == null || current == host;
    }

    /**
     * Binds the core to the host that successfully published it in a structure catalog.
     *
     * @param host authoritative formed host
     * @return false when another active host still owns this physical core
     */
    public boolean bindPatternHost(TrinityPatternCoreHost host) {
        if (!canBindPatternHost(host)) {
            return false;
        }
        this.patternHost = host;
        return true;
    }

    /**
     * Releases a transient binding only when the supplied host still owns it.
     *
     * @param host host withdrawing its catalog
     */
    public void unbindPatternHost(TrinityPatternCoreHost host) {
        if (this.patternHost == host) {
            this.patternHost = null;
        }
    }

    /**
     * Refunds local queued state through the mounted host's AE lease when available.
     *
     * @param player player requesting the refund
     * @return whether queued inputs or pending outputs were returned
     */
    public boolean tryRefundAll(Player player) {
        if (!isCoreStateReady()) {
            return false;
        }
        TrinityPatternCoreHost host = currentPatternHost();
        if (host != null) {
            try {
                return host.tryRefundPatternCore(this, player);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to refund Trinity pattern core {} through its mounted host; using player fallbacks",
                        coreId(),
                        exception);
            }
        }
        return this.core.tryRefundAll(new TrinityRefundDeliveryImpl(player, null, null));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        writeCoreState(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        loadCoreState(data, registries);
    }

    private void loadCoreState(CompoundTag data, HolderLookup.Provider registries) {
        if (this.level == null) {
            this.stagedCoreState = data.copy();
            this.coreLoadState = CoreLoadState.STAGED;
            return;
        }
        if (tryLoadCoreState(data, registries)) {
            this.stagedCoreState = null;
            this.coreLoadState = CoreLoadState.READY;
            refreshPatternCachesWithDiagnostics();
        } else if (this.coreLoadState != CoreLoadState.READY) {
            this.stagedCoreState = data.copy();
            this.coreLoadState = CoreLoadState.REJECTED;
        }
    }

    @Override
    public UUID coreId() {
        return this.core.coreId();
    }

    @Override
    public int patternCapacity() {
        return this.core.patternCapacity();
    }

    @Override
    public TrinityPatternSlot patternSlot(int slot) {
        return readyCore().patternSlot(slot);
    }

    @Override
    public long revision() {
        return this.core.revision();
    }

    @Override
    public PatternCacheSnapshot patternCacheSnapshot() {
        return readyCore().patternCacheSnapshot();
    }

    @Override
    public InternalInventory patternInventory() {
        return readyCore().patternInventory();
    }

    @Override
    public ItemStack pattern(int slot) {
        return readyCore().pattern(slot);
    }

    @Override
    public boolean trySetPattern(int slot, ItemStack pattern) {
        return readyCore().trySetPattern(slot, pattern);
    }

    @Nullable
    @Override
    public IMolecularAssemblerSupportedPattern decodedPattern(int slot) {
        return readyCore().decodedPattern(slot);
    }

    @Override
    public void refreshPatternCache(int slot) {
        readyCore().refreshPatternCache(slot);
        logInvalidRetainedPattern(slot);
    }

    @Override
    public void refreshAllPatternCaches() {
        readyCore();
        refreshPatternCachesWithDiagnostics();
    }

    @Override
    public boolean enqueueBatch(PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs, long queuedTick) {
        return readyCore().enqueueBatch(route, patternSnapshot, inputs, queuedTick);
    }

    @Override
    public List<TrinityCraftingBatch> queuedBatches(int slot) {
        return readyCore().queuedBatches(slot);
    }

    @Override
    public int queuedBatchCount(int slot) {
        return readyCore().queuedBatchCount(slot);
    }

    @Override
    public int queuedBatchCount() {
        return readyCore().queuedBatchCount();
    }

    @Override
    public int executeReadyBatches(long currentTick, BatchExecutor executor) {
        return readyCore().executeReadyBatches(currentTick, executor);
    }

    @Override
    public List<TrinityItemAmount> pendingOutputs(PatternRoute route) {
        return readyCore().pendingOutputs(route);
    }

    @Override
    public void appendPendingOutputs(PatternRoute route, List<TrinityItemAmount> outputs) {
        readyCore().appendPendingOutputs(route, outputs);
    }

    @Override
    public PendingOutputCursor openPendingOutputCursor(PatternRoute route) {
        return readyCore().openPendingOutputCursor(route);
    }

    @Override
    public List<Integer> pendingOutputSlots(UUID hostId) {
        return readyCore().pendingOutputSlots(hostId);
    }

    @Override
    public List<Integer> workingSlots(UUID hostId) {
        return readyCore().workingSlots(hostId);
    }

    @Override
    public boolean hasWork() {
        return readyCore().hasWork();
    }

    @Override
    public boolean hasWork(UUID hostId) {
        return readyCore().hasWork(hostId);
    }

    @Override
    public RefundTransaction prepareRefund() {
        return readyCore().prepareRefund();
    }

    @Override
    public RefundTransaction prepareRefund(UUID hostId) {
        return readyCore().prepareRefund(hostId);
    }

    @Override
    public boolean tryRefundAll(TrinityRefundDelivery delivery) {
        if (!isCoreStateReady()) {
            return false;
        }
        return this.core.tryRefundAll(delivery);
    }

    @Override
    public void writeToTag(CompoundTag data, HolderLookup.Provider registries) {
        writeCoreState(data, registries);
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        loadCoreState(data, registries);
    }

    @Override
    public void onChunkUnloaded() {
        releasePatternHost();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        releasePatternHost();
        super.setRemoved();
    }

    @Nullable
    private IMolecularAssemblerSupportedPattern decodeSupportedPattern(ItemStack pattern) {
        if (pattern.isEmpty() || this.level == null) {
            return null;
        }
        try {
            IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, this.level);
            return details instanceof IMolecularAssemblerSupportedPattern supported ? supported : null;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to decode Trinity pattern core {} slot item {} at {}",
                    this.core.coreId(),
                    pattern,
                    this.worldPosition,
                    exception);
            return null;
        }
    }

    private BatchExecutionResult executeBatch(int slot, TrinityCraftingBatch batch) {
        IMolecularAssemblerSupportedPattern pattern = this.core.decodedPattern(slot);
        try {
            List<ItemStack> inputs = batch.inputs();
            TransientCraftingContainer container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
            for (int inputSlot = 0; inputSlot < inputs.size(); inputSlot++) {
                container.setItem(inputSlot, inputs.get(inputSlot));
            }
            CraftingInput.Positioned positionedInput = CraftingInput.ofPositioned(3, 3, inputs);
            CraftingInput craftingInput = positionedInput.input();
            ItemStack output = pattern.assemble(craftingInput, this.level);
            if (output.isEmpty()) {
                Data_Energistics.LOGGER.error(
                        "Trinity pattern core {} slot {} produced no output for a persisted batch at {}",
                        coreId(),
                        slot,
                        this.worldPosition);
                return BatchExecutionResult.paused();
            }

            output.onCraftedBySystem(this.level);
            CraftingEvent.fireAutoCraftingEvent(this.level, pattern, output, container);
            NonNullList<ItemStack> remainingItems = pattern.getRemainingItems(craftingInput);
            ArrayList<ItemStack> outputs = new ArrayList<>(remainingItems.size() + 1);
            for (ItemStack remaining : remainingItems) {
                if (!remaining.isEmpty()) {
                    outputs.add(remaining.copy());
                }
            }
            outputs.add(output.copy());
            return BatchExecutionResult.completed(outputs);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to execute Trinity pattern core {} slot {} batch at {}",
                    coreId(),
                    slot,
                    this.worldPosition,
                    exception);
            return BatchExecutionResult.paused();
        }
    }

    private void refreshPatternCachesWithDiagnostics() {
        this.core.refreshAllPatternCaches();
        for (int slot = 0; slot < this.core.patternCapacity(); slot++) {
            logInvalidRetainedPattern(slot);
        }
    }

    private boolean tryLoadCoreState(CompoundTag data, HolderLookup.Provider registries) {
        UUID previousCoreId = this.core.coreId();
        try {
            this.core.readFromTag(data, registries);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected Trinity pattern core state at {} because its persisted definitions are invalid",
                    this.worldPosition,
                    exception);
            return false;
        }
        if (!previousCoreId.equals(this.core.coreId())) {
            releasePatternHost();
        }
        return true;
    }

    private void writeCoreState(CompoundTag data, HolderLookup.Provider registries) {
        if (isCoreStateReady()) {
            this.core.writeToTag(data, registries);
        } else {
            data.merge(this.stagedCoreState.copy());
        }
    }

    private TrinityPatternCoreImpl readyCore() {
        if (!isCoreStateReady()) {
            throw new IllegalStateException(
                    "Trinity pattern core state at " + this.worldPosition + " has not passed validation");
        }
        return this.core;
    }

    private void logInvalidRetainedPattern(int slot) {
        ItemStack pattern = this.core.pattern(slot);
        if (!pattern.isEmpty() && this.core.decodedPattern(slot) == null) {
            Data_Energistics.LOGGER.warn(
                    "Retaining unpublished pattern {} in Trinity pattern core {} slot {} at {} because it no longer " +
                            "decodes to an IMolecularAssemblerSupportedPattern",
                    pattern,
                    coreId(),
                    slot,
                    this.worldPosition);
        }
    }

    private void onCoreChanged(TrinityPatternSlot.Change change) {
        if (change.kind() == TrinityPatternSlot.ChangeKind.PERSISTENT) {
            setChanged();
        } else if (change.kind() == TrinityPatternSlot.ChangeKind.CATALOG &&
                this.level != null && !this.level.isClientSide()) {
                    markForClientUpdate();
                }
    }

    @Nullable
    private TrinityPatternCoreHost currentPatternHost() {
        TrinityPatternCoreHost current = this.patternHost;
        if (current != null && !current.isPatternCoreMounted(this)) {
            this.patternHost = null;
            return null;
        }
        return current;
    }

    private void releasePatternHost() {
        TrinityPatternCoreHost current = this.patternHost;
        this.patternHost = null;
        if (current != null) {
            current.onPatternCoreUnavailable(this);
        }
    }

    private static int patternCapacityFromState(BlockState state) {
        if (!(state.getBlock() instanceof TrinityCoreComponent component) ||
                component.kind() != TrinityCoreKind.PATTERN_PROCESSING) {
            throw new IllegalArgumentException("Trinity pattern core block entity requires a pattern processing block");
        }
        return component.patternCapacity();
    }
}
