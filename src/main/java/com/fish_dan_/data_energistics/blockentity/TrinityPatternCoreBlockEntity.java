package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityCraftingBatch;
import com.fish_dan_.data_energistics.common.trinity.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreBinding;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreReleaseRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost.PatternCoreReleaseResult;
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
    private boolean stagedInitialHydration;
    /** Current transient host together with the exact catalog range that authorized this binding. */
    @Nullable
    private BoundPatternHost patternHostBinding;
    private boolean patternHostChangeFailed;
    /** Prevents stale-host cleanup while a host has locked publication but still owes release confirmation. */
    private boolean patternHostReleasePending;

    /** Couples one host reference to its immutable catalog authority token. */
    private record BoundPatternHost(TrinityPatternCoreHost host, PatternCoreBinding binding) {}

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
        boolean hydratedStagedState = false;
        super.setLevel(level);
        if (this.coreLoadState == CoreLoadState.STAGED) {
            if (tryLoadCoreState(this.stagedCoreState, level.registryAccess(), this.stagedInitialHydration)) {
                this.stagedCoreState = null;
                this.coreLoadState = CoreLoadState.READY;
                hydratedStagedState = true;
            } else {
                this.coreLoadState = CoreLoadState.REJECTED;
            }
        }
        this.observedReloadEpoch = TrinityPatternCoreReloadEpoch.current();
        if (isCoreStateReady() && levelChanged && !hydratedStagedState) {
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
        if (this.patternHostChangeFailed || this.patternHostReleasePending) {
            this.patternHostChangeFailed = false;
            releasePatternHost();
        }
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

    @Override
    public boolean runtimeBindingsCurrent() {
        return isCoreStateReady() && this.observedReloadEpoch == TrinityPatternCoreReloadEpoch.current();
    }

    /**
     * Executes batches owned by the active host. Batches and pending outputs belonging to another host remain asleep.
     *
     * @param hostId      stable identity of the host currently authorized to execute this core
     * @param currentTick current server tick
     * @return number of completed batches
     */
    public int executeOwnedBatches(UUID hostId, long currentTick) {
        if (!runtimeBindingsCurrent()) {
            return 0;
        }
        return this.core.executeReadyBatches(
                currentTick,
                (slot, batch) -> hostId.equals(batch.route().hostId()) ? executeBatch(slot, batch) : BatchExecutionResult.paused());
    }

    /**
     * Executes eligible groups in one host-owned physical slot without scanning this core's other work indexes.
     *
     * @param hostId      stable identity of the host currently authorized to execute this core
     * @param slot        exact physical slot selected by the host runtime cache
     * @param currentTick current server tick
     * @return number of completed queue groups
     */
    public int executeOwnedSlot(UUID hostId, int slot, long currentTick) {
        if (!runtimeBindingsCurrent()) {
            return 0;
        }
        return this.core.executeReadyBatches(
                slot,
                currentTick,
                (physicalSlot, batch) -> hostId.equals(batch.route().hostId()) ? executeBatch(physicalSlot, batch) : BatchExecutionResult.paused());
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
        BoundPatternHost current = this.patternHostBinding;
        return current == null ||
                (!this.patternHostChangeFailed && !this.patternHostReleasePending && current.host() == host);
    }

    /**
     * Binds the core to the host that successfully published it in a structure catalog.
     *
     * @param host authoritative formed host
     * @return false when another active host still owns this physical core
     */
    public boolean bindPatternHost(TrinityPatternCoreHost host, PatternCoreBinding binding) {
        if (!binding.coreId().equals(coreId()) || !binding.mountPosition().equals(this.worldPosition) ||
                binding.blockCapacity() != patternCapacity() || !canBindPatternHost(host)) {
            return false;
        }
        this.patternHostBinding = new BoundPatternHost(host, binding);
        this.patternHostChangeFailed = false;
        this.patternHostReleasePending = false;
        return true;
    }

    /**
     * Releases a transient binding only when the supplied host still owns it.
     *
     * @param host host withdrawing its catalog
     */
    public void unbindPatternHost(TrinityPatternCoreHost host, PatternCoreBinding binding) {
        BoundPatternHost current = this.patternHostBinding;
        if (current != null && current.host() == host && current.binding().equals(binding)) {
            clearPatternHostBinding(current);
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
        BoundPatternHost current = currentPatternHost();
        if (current != null) {
            try {
                return current.host().tryRefundPatternCore(this, current.binding(), player);
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
        loadCoreState(data, registries, this.coreLoadState != CoreLoadState.READY);
    }

    private void loadCoreState(CompoundTag data, HolderLookup.Provider registries, boolean initialHydration) {
        if (this.level == null) {
            this.stagedCoreState = data.copy();
            this.stagedInitialHydration = initialHydration;
            this.coreLoadState = CoreLoadState.STAGED;
            return;
        }
        if (tryLoadCoreState(data, registries, initialHydration)) {
            this.stagedCoreState = null;
            this.coreLoadState = CoreLoadState.READY;
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

    @Nullable
    @Override
    public CachedPattern cachedPattern(int slot) {
        return readyCore().cachedPattern(slot);
    }

    @Override
    public List<Integer> occupiedPatternSlots() {
        return readyCore().occupiedPatternSlots();
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
    public boolean enqueueBatch(PatternRoute route,
                                CachedPattern expectedPattern,
                                long expectedRuntimeBindingRevision,
                                TrinityCraftingBatch.InputSignature inputs,
                                long queuedTick,
                                long count) {
        return readyCore().enqueueBatch(
                route,
                expectedPattern,
                expectedRuntimeBindingRevision,
                inputs,
                queuedTick,
                count);
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
    public int executeReadyBatches(int slot, long currentTick, BatchExecutor executor) {
        return readyCore().executeReadyBatches(slot, currentTick, executor);
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
    public boolean isSlotWorking(UUID hostId, int slot) {
        return readyCore().isSlotWorking(hostId, slot);
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
    public void hydrateFromTag(CompoundTag data, HolderLookup.Provider registries) {
        loadCoreState(data, registries, true);
    }

    @Override
    public void readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        loadCoreState(data, registries, false);
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
                    outputs.add(remaining);
                }
            }
            outputs.add(output);
            return BatchExecutionResult.completed(batch, outputs);
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
        for (int slot : this.core.occupiedPatternSlots()) {
            logInvalidRetainedPattern(slot);
        }
    }

    private boolean tryLoadCoreState(CompoundTag data,
                                     HolderLookup.Provider registries,
                                     boolean initialHydration) {
        UUID previousCoreId = this.core.coreId();
        try {
            if (initialHydration) {
                this.core.hydrateFromTag(data, registries);
            } else {
                this.core.readFromTag(data, registries);
            }
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
        try {
            if (change.kind() == TrinityPatternSlot.ChangeKind.PERSISTENT) {
                setChanged();
                return;
            }
            if (change.kind() == TrinityPatternSlot.ChangeKind.CATALOG &&
                    this.level != null && !this.level.isClientSide()) {
                markForClientUpdate();
            }
            if (!this.patternHostChangeFailed && !this.patternHostReleasePending) {
                BoundPatternHost current = currentPatternHost();
                if (current != null) {
                    current.host().onPatternCoreChanged(this, current.binding(), change);
                }
            }
        } catch (RuntimeException exception) {
            this.patternHostChangeFailed = true;
            Data_Energistics.LOGGER.error(
                    "Trinity pattern core {} at {} retained committed slot {} {} state after its host callback failed",
                    coreId(),
                    this.worldPosition,
                    change.slot(),
                    change.kind(),
                    exception);
        }
    }

    @Nullable
    private BoundPatternHost currentPatternHost() {
        return this.patternHostBinding;
    }

    private void releasePatternHost() {
        BoundPatternHost current = this.patternHostBinding;
        if (current == null) {
            this.patternHostChangeFailed = false;
            this.patternHostReleasePending = false;
            return;
        }
        try {
            PatternCoreReleaseResult result = current.host().onPatternCoreUnavailable(
                    new PatternCoreReleaseRequest(this, current.binding()));
            if (result.confirmsRelease()) {
                clearPatternHostBinding(current);
            } else if (result == PatternCoreReleaseResult.RETRY_REQUIRED) {
                this.patternHostReleasePending = true;
            } else {
                this.patternHostReleasePending = false;
                Data_Energistics.LOGGER.warn(
                        "Trinity pattern core {} at {} retained its binding after host {} rejected stale release token {}",
                        coreId(),
                        this.worldPosition,
                        current.binding().hostId(),
                        current.binding().layoutRevision());
            }
        } catch (RuntimeException exception) {
            this.patternHostReleasePending = true;
            Data_Energistics.LOGGER.error(
                    "Trinity pattern core {} at {} retained host {} binding from layout {} after release confirmation failed",
                    current.binding().coreId(),
                    this.worldPosition,
                    current.binding().hostId(),
                    current.binding().layoutRevision(),
                    exception);
        }
    }

    private void clearPatternHostBinding(BoundPatternHost expected) {
        if (this.patternHostBinding == expected) {
            this.patternHostBinding = null;
            this.patternHostChangeFailed = false;
            this.patternHostReleasePending = false;
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
