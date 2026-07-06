package com.fish_dan_.data_energistics.common.crafting.flower;

import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;

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
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * AE2-visible virtual crafting CPU partition owned by a Digital Construct Flower host.
 *
 * <p>
 * Each partition accepts one crafting job and lets the host expose a large formed structure as multiple selectable
 * CPUs.
 */
public final class DigitalConstructFlowerVirtualCpu implements ICraftingCPU {

    private final DigitalConstructFlowerBlockEntity host;
    private final DigitalConstructFlowerCpuLogic logic = new DigitalConstructFlowerCpuLogic(this);
    private DigitalConstructFlowerCpuPartitionProfile profile;

    DigitalConstructFlowerVirtualCpu(DigitalConstructFlowerBlockEntity host,
                                     DigitalConstructFlowerCpuPartitionProfile profile) {
        this.host = host;
        this.profile = profile;
    }

    /**
     * Updates resources for this partition after a structure profile rebuild.
     *
     * @param profile new immutable partition profile
     */
    void updateProfile(DigitalConstructFlowerCpuPartitionProfile profile) {
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
        return this.logic.trySubmitJob(grid, plan, source, requester);
    }

    /**
     * Advances this CPU from AE2's crafting service tick.
     *
     * @param energyService   AE2 energy service
     * @param craftingService AE2 crafting service
     */
    public void tick(IEnergyService energyService, CraftingService craftingService) {
        this.logic.tickCraftingLogic(energyService, craftingService);
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
        return this.logic.insert(what, amount, mode);
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
     * @return last tick where this CPU changed crafting-visible state
     */
    public long getLastModifiedOnTick() {
        return this.logic.getLastModifiedOnTick();
    }

    /**
     * @return true when the host is formed and at least one bound access hatch is online
     */
    public boolean isActive() {
        return this.host.isStructureFormed() && this.host.hasActiveAccessHatch();
    }

    @Override
    public boolean isBusy() {
        return this.logic.hasJob();
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        GenericStack finalOutput = this.logic.getFinalJobOutput();
        if (finalOutput == null) {
            return null;
        }
        DigitalConstructFlowerElapsedTimeTracker tracker = this.logic.elapsedTimeTracker();
        long progress = Math.max(0L, tracker.startItemCount() - tracker.remainingItemCount());
        return new CraftingJobStatus(finalOutput, tracker.startItemCount(), progress, tracker.elapsedTimeNanos());
    }

    @Override
    public void cancelJob() {
        this.logic.cancel();
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
        return Component.translatable("block.data_energistics.digital_construct_flower")
                .append(" #")
                .append(Integer.toString(this.profile.index() + 1));
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

    DigitalConstructFlowerCpuLogic logic() {
        return this.logic;
    }

    int index() {
        return this.profile.index();
    }
}
