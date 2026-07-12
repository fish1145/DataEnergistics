package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.ChannelHubCapacity;
import com.fish_dan_.data_energistics.ae2.ChannelHubCapacityImpl;
import com.fish_dan_.data_energistics.ae2.ChannelHubHost;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a channel-hub node the controller-wide capacity and starts a fresh local bottleneck chain below that node.
 */
@Mixin(GridNode.class)
public abstract class GridNodeMixin {

    /**
     * Capacity calculator used to snapshot the current grid budget before controller routing.
     */
    @Unique
    private static final ChannelHubCapacity DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY = new ChannelHubCapacityImpl();

    /**
     * Logical node owner used to identify a channel hub.
     */
    @Final
    @Shadow
    private Object owner;

    /**
     * Highest ancestor that AE2 needs to inspect for a distinct channel bottleneck.
     */
    @Nullable
    @Shadow
    private GridNode highestSimilarAncestor;

    /**
     * Effective channel limit inherited by descendants during the current pathing pass.
     */
    @Shadow
    private int subtreeMaxChannels;

    /**
     * Whether compressed channels can travel through the current subtree.
     */
    @Shadow
    private boolean subtreeAllowsCompressedChannels;

    /**
     * Controller-wide capacity cached for this hub during the current routing pass.
     */
    @Unique
    private int dataEnergistics$channelHubCapacity = -1;

    /**
     * Grid identity associated with the cached controller-wide capacity.
     */
    @Nullable
    @Unique
    private IGrid dataEnergistics$channelHubCapacityGrid;

    /**
     * Channel mode associated with the cached controller-wide capacity.
     */
    @Nullable
    @Unique
    private ChannelMode dataEnergistics$channelHubCapacityMode;

    /**
     * Returns the grid currently owning this node.
     *
     * @return owning grid
     */
    @Shadow
    public abstract IGrid getGrid();

    /**
     * Tests a pathing flag without exposing AE2's internal flag set.
     *
     * @param flag flag to test
     * @return whether the node carries the flag
     */
    @Shadow
    public abstract boolean hasFlag(GridFlags flag);

    /**
     * Reads the effective maximum after the hub-specific override.
     *
     * @return effective node capacity
     */
    @Shadow
    public abstract int getMaxChannels();

    /**
     * Snapshots controller geometry before AE2 derives descendant routing metadata from the node capacity.
     *
     * @param fast     selected controller route
     * @param callback injection callback
     */
    @Inject(method = "setControllerRoute", at = @At("HEAD"), require = 1)
    private void dataEnergistics$captureChannelHubCapacity(IPathItem fast, CallbackInfo callback) {
        if (this.owner instanceof ChannelHubHost) {
            IGrid grid = getGrid();
            this.dataEnergistics$channelHubCapacity = DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY.calculate(grid);
            this.dataEnergistics$channelHubCapacityGrid = grid;
            this.dataEnergistics$channelHubCapacityMode = grid.getPathingService().getChannelMode();
        }
    }

    /**
     * Makes the hub the top of a new local bottleneck chain so downstream channels do not inherit the controller-facing
     * cable limit.
     *
     * @param fast     selected controller route
     * @param callback injection callback
     */
    @Inject(method = "setControllerRoute", at = @At("TAIL"), require = 1)
    private void dataEnergistics$startChannelHubSubtree(IPathItem fast, CallbackInfo callback) {
        if (!(this.owner instanceof ChannelHubHost)) {
            return;
        }
        this.highestSimilarAncestor = null;
        this.subtreeMaxChannels = getMaxChannels();
        this.subtreeAllowsCompressedChannels = !hasFlag(GridFlags.CANNOT_CARRY_COMPRESSED);
    }

    /**
     * Replaces the ordinary dense-node ceiling with the shared controller-wide budget for channel hubs.
     *
     * @param callback return callback receiving the hub capacity
     */
    @Inject(method = "getMaxChannels", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$getChannelHubCapacity(CallbackInfoReturnable<Integer> callback) {
        if (!(this.owner instanceof ChannelHubHost)) {
            return;
        }
        if (hasFlag(GridFlags.CANNOT_CARRY)) {
            callback.setReturnValue(0);
            return;
        }
        IGrid grid = getGrid();
        ChannelMode channelMode = grid.getPathingService().getChannelMode();
        ControllerState controllerState = grid.getPathingService().getControllerState();
        int capacity = this.dataEnergistics$channelHubCapacity;
        if (controllerState != ControllerState.CONTROLLER_ONLINE || capacity < 0 || this.dataEnergistics$channelHubCapacityGrid != grid || this.dataEnergistics$channelHubCapacityMode != channelMode) {
            capacity = DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY.calculate(grid);
            this.dataEnergistics$channelHubCapacity = capacity;
            this.dataEnergistics$channelHubCapacityGrid = grid;
            this.dataEnergistics$channelHubCapacityMode = channelMode;
        }
        callback.setReturnValue(capacity);
    }
}
