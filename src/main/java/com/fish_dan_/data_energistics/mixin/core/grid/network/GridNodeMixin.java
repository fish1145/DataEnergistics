package com.fish_dan_.data_energistics.mixin.core.grid.network;

import com.fish_dan_.data_energistics.ae2.grid.VirtualGridNode;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.me.GridNode;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a subordinate node observe primary-grid power and its virtual lease without changing {@code getGrid()}.
 */
@Mixin(GridNode.class)
public abstract class GridNodeMixin implements VirtualGridNode {

    @Nullable
    @Unique
    private IGrid dataEnergistics$virtualPrimaryGrid;

    @Unique
    private boolean dataEnergistics$virtualMemberActive;

    @Unique
    private long dataEnergistics$virtualMembershipGeneration;

    @Override
    @Nullable
    public IGrid virtualPrimaryGrid() {
        return this.dataEnergistics$virtualPrimaryGrid;
    }

    @Override
    public boolean isVirtualMemberActive() {
        return this.dataEnergistics$virtualMemberActive;
    }

    @Override
    public long virtualMembershipGeneration() {
        return this.dataEnergistics$virtualMembershipGeneration;
    }

    @Override
    public void updateVirtualMembership(@Nullable IGrid primaryGrid, boolean active) {
        if (primaryGrid == null && active) {
            throw new IllegalArgumentException("A released virtual node cannot remain active");
        }
        if (this.dataEnergistics$virtualPrimaryGrid == primaryGrid && this.dataEnergistics$virtualMemberActive == active) {
            return;
        }
        this.dataEnergistics$virtualPrimaryGrid = primaryGrid;
        this.dataEnergistics$virtualMemberActive = active;
        this.dataEnergistics$virtualMembershipGeneration = Math.incrementExact(
                this.dataEnergistics$virtualMembershipGeneration);
        notifyStatusChange(IGridNodeListener.State.POWER);
        notifyStatusChange(IGridNodeListener.State.CHANNEL);
        notifyStatusChange(IGridNodeListener.State.GRID_BOOT);
    }

    /**
     * Uses primary-grid power only for enabled virtual members.
     */
    @Inject(method = "isPowered", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$useVirtualPower(CallbackInfoReturnable<Boolean> callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.setReturnValue(this.dataEnergistics$virtualMemberActive && this.dataEnergistics$virtualPrimaryGrid.getEnergyService().isNetworkPowered());
        }
    }

    /**
     * Uses the primary pathing boot state while keeping local pathing independent.
     */
    @Inject(method = "hasGridBooted", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$useVirtualBootState(CallbackInfoReturnable<Boolean> callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.setReturnValue(this.dataEnergistics$virtualMemberActive && !this.dataEnergistics$virtualPrimaryGrid.getPathingService().isNetworkBooting());
        }
    }

    /**
     * Treats the virtual-member enablement as the authoritative channel/disable state.
     */
    @Inject(method = "meetsChannelRequirements", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$useVirtualLease(CallbackInfoReturnable<Boolean> callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.setReturnValue(this.dataEnergistics$virtualMemberActive);
        }
    }

    /**
     * Reports one leased channel for enabled channel-consuming virtual nodes and zero otherwise.
     */
    @Inject(method = "getUsedChannels", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$reportVirtualChannel(CallbackInfoReturnable<Integer> callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.setReturnValue(this.dataEnergistics$virtualMemberActive && hasFlag(GridFlags.REQUIRE_CHANNEL) ? 1 : 0);
        }
    }

    @Shadow
    public abstract boolean hasFlag(GridFlags flag);

    @Shadow
    public abstract void notifyStatusChange(IGridNodeListener.State reason);
}
