package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * AE2-visible virtual crafting CPU partition owned by a Trinity Data Core host.
 *
 * <p>
 * Each partition accepts one crafting job and lets the host expose a large formed structure as multiple selectable
 * CPUs.
 */
public final class TrinityDataCoreVirtualCpu implements ICraftingCPU {

    private final TrinityDataCoreBlockEntity host;
    private final TrinityDataCoreCraftingRuntime runtime;
    private final TrinityDataCoreCpuLogic logic = new TrinityDataCoreCpuLogic(this);
    private TrinityDataCoreCpuPartitionProfile profile;

    TrinityDataCoreVirtualCpu(TrinityDataCoreBlockEntity host,
                              TrinityDataCoreCraftingRuntime runtime,
                              TrinityDataCoreCpuPartitionProfile profile) {
        this.host = host;
        this.runtime = runtime;
        this.profile = profile;
        this.logic.addListener(this::craftingVisibleChanged);
    }

    /**
     * Updates resources for this partition after a structure profile rebuild.
     *
     * @param profile new immutable partition profile
     */
    void updateProfile(TrinityDataCoreCpuPartitionProfile profile) {
        this.profile = profile;
    }

    /**
     * Attempts to start a job on this partition.
     *
     * @param grid      AE2 grid
     * @param plan      crafting plan
     * @param source    action source
     * @param requester optional requester
     * @return submit result
     */
    public ICraftingSubmitResult submitJob(IGrid grid,
                                           ICraftingPlan plan,
                                           IActionSource source,
                                           @Nullable ICraftingRequester requester) {
        if (number() == 0) {
            return this.runtime.submitJob(grid, plan, source, requester);
        }
        return CraftingSubmitResult.CPU_BUSY;
    }

    ICraftingSubmitResult submitWorkerJob(IGrid grid,
                                          ICraftingPlan plan,
                                          IActionSource source,
                                          @Nullable ICraftingRequester requester) {
        if (number() == 0) {
            throw new IllegalStateException("Reserved Trinity CPU cannot own a crafting job");
        }
        return this.logic.trySubmitJob(grid, plan, source, requester);
    }

    /**
     * Advances this CPU from AE2's crafting service tick.
     *
     * @param energyService   AE2 energy service
     * @param craftingService AE2 crafting service
     */
    public void tick(IEnergyService energyService, CraftingService craftingService) {
        boolean wasBusy = isBusy();
        this.logic.tickCraftingLogic(energyService, craftingService);
        this.runtime.workerOperationCompleted(this, wasBusy);
    }

    /**
     * Inserts a returned crafting output into this CPU.
     *
     * @param what   returned key
     * @param amount returned amount
     * @param mode   simulation or mutation
     * @return accepted amount
     */
    public long insert(AEKey what, long amount, Actionable mode) {
        boolean wasBusy = isBusy();
        long inserted = this.logic.insert(what, amount, mode);
        this.runtime.workerOperationCompleted(this, wasBusy);
        return inserted;
    }

    /**
     * Adds all currently awaited keys to AE2's request set.
     *
     * @param waitingFor output set
     */
    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        this.logic.getAllWaitingFor(waitingFor);
    }

    /**
     * @param what requested key
     * @return amount this CPU is waiting for
     */
    public long getWaitingFor(AEKey what) {
        return this.logic.getWaitingFor(what);
    }

    /**
     * @param what displayed key
     * @return amount currently stored inside the virtual CPU inventory
     */
    public long getStored(AEKey what) {
        return this.logic.getStored(what);
    }

    /**
     * @param what displayed key
     * @return amount still scheduled by unpushed crafting tasks
     */
    public long getPendingOutputs(AEKey what) {
        return this.logic.getPendingOutputs(what);
    }

    /**
     * Adds every key currently visible in AE2's CPU status table.
     *
     * @param out destination counter
     */
    public void getAllItems(KeyCounter out) {
        this.logic.getAllItems(out);
    }

    /**
     * Registers a CPU status table listener.
     *
     * @param listener changed key listener
     */
    public void addListener(Consumer<AEKey> listener) {
        this.logic.addListener(listener);
    }

    /**
     * Unregisters a CPU status table listener.
     *
     * @param listener changed key listener
     */
    public void removeListener(Consumer<AEKey> listener) {
        this.logic.removeListener(listener);
    }

    /**
     * @return true when idle inventory could not be returned to the network
     */
    public boolean isCantStoreItems() {
        return this.logic.isCantStoreItems();
    }

    /** Delegates durable removal recovery to this partition's concrete CPU inventory. */
    boolean recoverIdleInventory(BiFunction<AEKey, Long, Long> recovery) {
        return this.logic.recoverIdleInventory(recovery);
    }

    /**
     * @return last tick where this CPU changed crafting-visible state
     */
    public long getLastModifiedOnTick() {
        return this.logic.getLastModifiedOnTick();
    }

    /**
     * @return elapsed nanoseconds for the active job
     */
    public long getElapsedTimeNanos() {
        return this.logic.elapsedTimeTracker().elapsedTimeNanos();
    }

    /**
     * @return AE2-compatible remaining work count for GUI progress
     */
    public long getRemainingItemCount() {
        return this.logic.elapsedTimeTracker().remainingItemCount();
    }

    /**
     * @return AE2-compatible total work count for GUI progress
     */
    public long getStartItemCount() {
        return this.logic.elapsedTimeTracker().startItemCount();
    }

    /**
     * @return true only while the CPU child publishes this current partition on an active lease grid
     */
    public boolean isActive() {
        return isOnline() && this.runtime.isCurrentCpu(this);
    }

    /**
     * Returns whether this partition is still published by its host on the exact grid submitting a job.
     *
     * @param grid grid attempting to submit the job
     * @return true only while this remains a current CPU partition on the host's active lease grid
     */
    boolean isActiveOnGrid(IGrid grid) {
        return this.host.isCpuProviderAvailable() && this.runtime.isCurrentCpu(this) && this.host.accessGrid() == grid;
    }

    boolean isOnline() {
        return this.host.isCpuProviderAvailable() && this.host.accessGrid() != null;
    }

    @Override
    public boolean isBusy() {
        return number() != 0 && this.logic.hasJob();
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        GenericStack finalOutput = this.logic.getFinalJobOutput();
        if (finalOutput == null) {
            return null;
        }
        TrinityDataCoreElapsedTimeTracker tracker = this.logic.elapsedTimeTracker();
        long progress = Math.max(0L, tracker.startItemCount() - tracker.remainingItemCount());
        return new CraftingJobStatus(finalOutput, tracker.startItemCount(), progress, tracker.elapsedTimeNanos());
    }

    @Override
    public void cancelJob() {
        if (number() != 0) {
            boolean wasBusy = isBusy();
            this.logic.cancel();
            this.runtime.workerOperationCompleted(this, wasBusy);
        }
    }

    @Override
    public long getAvailableStorage() {
        return this.profile.storageBytes();
    }

    @Override
    public int getCoProcessors() {
        return this.profile.coProcessors();
    }

    @Nullable
    @Override
    public Component getName() {
        return Component.translatable("block.data_energistics.trinity_data_core")
                .append(" #")
                .append(Integer.toString(number()));
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return this.profile.selectionMode();
    }

    /**
     * @param source submit source
     * @return true when AE2 may auto-select this CPU
     */
    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    /**
     * @param source submit source
     * @return true when this CPU should be preferred for the source type
     */
    public boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    /**
     * @return host grid, or null when not attached
     */
    @Nullable
    IGrid grid() {
        return this.host.accessGrid();
    }

    /**
     * @return host level, or null before the block entity is attached
     */
    @Nullable
    Level level() {
        return this.host.getLevel();
    }

    /**
     * @return action source for storing idle inventory back into the network
     */
    IActionSource actionSource() {
        return this.host.accessActionSource();
    }

    /**
     * Marks the host block entity dirty after CPU state changes.
     */
    void markDirty() {
        this.host.setChanged();
    }

    TrinityDataCoreCpuLogic logic() {
        return this.logic;
    }

    /** Returns the stable runtime number: zero for the reserve and one through the worker capacity for workers. */
    public int number() {
        return this.profile.index();
    }

    int workerCapacity() {
        return this.profile.totalPartitions();
    }

    boolean hasRetainedState() {
        return this.logic.hasRetainedState();
    }

    boolean isReleasable() {
        return this.logic.isReleasable();
    }

    /** Forwards one logic-level AEKey change into the runtime's aggregate waiting index. */
    private void craftingVisibleChanged(AEKey what) {
        this.runtime.workerCraftingVisibleChanged(this, what);
    }
}
