package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ChannelHubCapacity;
import com.fish_dan_.data_energistics.ae2.ChannelHubCapacityImpl;
import com.fish_dan_.data_energistics.ae2.ChannelHubHost;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridConnection;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.me.pathfinding.PathingCalculation;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Adds a controller-wide channel pool to AE2 pathing and compresses every hub's controller-facing route to one channel.
 */
@Mixin(value = PathingCalculation.class, priority = 2000)
public abstract class PathingCalculationMixin {

    /**
     * Logger used when AE2 supplies an invalid controller route instead of crashing the server tick.
     */
    @Unique
    private static final Logger DATA_ENERGISTICS_LOGGER = Data_Energistics.LOGGER;

    /**
     * Capacity calculator used once for each controller pathing pass that contains a hub.
     */
    @Unique
    private static final ChannelHubCapacity DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY = new ChannelHubCapacityImpl();

    /**
     * Grid being assigned channels by this pathing calculation.
     */
    @Final
    @Shadow
    private IGrid grid;

    /**
     * AE2's per-node allocation counts for the current breadth-first pass.
     */
    @Final
    @Shadow
    private Reference2IntOpenHashMap<GridNode> channelBottlenecks;

    /**
     * Nodes that were granted their own channel during the current pass.
     */
    @Final
    @Shadow
    private Set<GridNode> channelNodes;

    /**
     * Whether this calculation contains at least one channel hub and therefore needs shared-pool semantics.
     */
    @Unique
    private boolean dataEnergistics$hasChannelHub;

    /**
     * Maximum number of channels shared by ordinary controller branches and all hub subtrees.
     */
    @Unique
    private int dataEnergistics$sharedCapacity;

    /**
     * Number of channels already granted from the shared controller-wide pool.
     */
    @Unique
    private int dataEnergistics$allocatedChannels;

    /**
     * Detects hubs and snapshots controller geometry after AE2 initializes its routing queues.
     *
     * @param grid     grid passed to the target constructor
     * @param callback constructor injection callback
     */
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void dataEnergistics$initializeSharedCapacity(IGrid grid, CallbackInfo callback) {
        for (IGridNode node : this.grid.getNodes()) {
            if (node.getOwner() instanceof ChannelHubHost) {
                this.dataEnergistics$hasChannelHub = true;
                this.dataEnergistics$sharedCapacity = DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY.calculate(this.grid);
                return;
            }
        }
    }

    /**
     * Allocates from the shared pool while retaining AE2's local cable and compressed-channel checks.
     *
     * @param start    channel-consuming node being considered
     * @param callback return callback receiving the allocation result
     */
    @Inject(method = "tryUseChannel", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$tryUseSharedChannel(GridNode start,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (!this.dataEnergistics$hasChannelHub) {
            return;
        }
        callback.setReturnValue(dataEnergistics$allocateSharedChannel(start));
    }

    /**
     * Compresses the tree edge immediately above a hub while leaving all other propagation untouched.
     *
     * @param connection connection whose subtree count is being propagated
     * @return propagated channel count
     */
    @Redirect(
              method = "propagateAssignments",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/me/GridConnection;propagateChannelsUpwards()I"),
              require = 1)
    private int dataEnergistics$compressChannelHubUpstream(GridConnection connection) {
        GridNode downstream = connection.b();
        if (!(downstream.getOwner() instanceof ChannelHubHost)) {
            return connection.propagateChannelsUpwards();
        }
        int compressedChannels = this.channelNodes.contains(downstream) ? 1 : 0;
        connection.setAdHocChannels(compressedChannels);
        return compressedChannels;
    }

    /**
     * Performs one channel allocation against both the global pool and the relevant local route.
     *
     * @param start channel-consuming node
     * @return whether allocation succeeded
     */
    @Unique
    private boolean dataEnergistics$allocateSharedChannel(GridNode start) {
        if (this.dataEnergistics$allocatedChannels >= this.dataEnergistics$sharedCapacity) {
            return false;
        }
        if (start.hasFlag(GridFlags.COMPRESSED_CHANNEL) && !start.getSubtreeAllowsCompressedChannels()) {
            return false;
        }
        if (this.channelNodes.contains(start)) {
            DATA_ENERGISTICS_LOGGER.error("AE2 attempted to allocate a channel twice for grid node {}", start);
            return false;
        }

        List<GridNode> allocationPath = new ArrayList<>();
        Set<GridNode> visitedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!dataEnergistics$collectLocalPath(start, allocationPath, visitedNodes)) {
            return false;
        }
        if (start.getOwner() instanceof ChannelHubHost && !dataEnergistics$collectControllerSidePath(start, allocationPath, visitedNodes)) {
            return false;
        }

        for (GridNode node : allocationPath) {
            this.channelBottlenecks.addTo(node, 1);
        }
        this.channelNodes.add(start);
        this.dataEnergistics$allocatedChannels++;
        return true;
    }

    /**
     * Collects AE2's optimized local bottleneck chain and stops at the first active hub boundary.
     *
     * @param start          channel-consuming node
     * @param allocationPath validated nodes to increment after all checks pass
     * @param visitedNodes   identity set protecting against invalid cyclic routing metadata
     * @return whether the local route can accept another channel
     */
    @Unique
    private boolean dataEnergistics$collectLocalPath(GridNode start,
                                                     List<GridNode> allocationPath,
                                                     Set<GridNode> visitedNodes) {
        GridNode current = start;
        while (current != null) {
            if (!dataEnergistics$appendAvailableNode(current, start, allocationPath, visitedNodes)) {
                return false;
            }
            if (current != start && current.getOwner() instanceof ChannelHubHost) {
                return this.channelNodes.contains(current);
            }
            current = current.getHighestSimilarAncestor();
        }
        return true;
    }

    /**
     * Collects the one-channel physical route used by a hub until a controller or an already-active parent hub is
     * reached.
     *
     * @param hub            hub consuming its own controller-facing channel
     * @param allocationPath validated nodes to increment after all checks pass
     * @param visitedNodes   node identity set shared with the local route
     * @return whether the controller-facing route can accept the hub channel
     */
    @Unique
    private boolean dataEnergistics$collectControllerSidePath(GridNode hub,
                                                              List<GridNode> allocationPath,
                                                              Set<GridNode> visitedNodes) {
        Set<IPathItem> visitedItems = Collections.newSetFromMap(new IdentityHashMap<>());
        IPathItem current = dataEnergistics$getControllerRoute(hub, hub);
        while (current != null) {
            if (!visitedItems.add(current)) {
                DATA_ENERGISTICS_LOGGER.error("AE2 supplied a cyclic controller route for channel hub {}", hub);
                return false;
            }
            if (current instanceof GridConnection connection) {
                current = connection.getControllerRoute();
                continue;
            }
            if (!(current instanceof GridNode node)) {
                DATA_ENERGISTICS_LOGGER.error(
                        "AE2 supplied an unsupported controller-route item {} for channel hub {}",
                        current.getClass().getName(),
                        hub);
                return false;
            }
            if (node.getOwner() instanceof ControllerBlockEntity) {
                return true;
            }
            if (!dataEnergistics$appendAvailableNode(node, hub, allocationPath, visitedNodes)) {
                return false;
            }
            if (node.getOwner() instanceof ChannelHubHost) {
                return this.channelNodes.contains(node);
            }
            current = dataEnergistics$getControllerRoute(node, hub);
        }
        DATA_ENERGISTICS_LOGGER.error("AE2 supplied an incomplete controller route for channel hub {}", hub);
        return false;
    }

    /**
     * Validates and records one bottleneck node without mutating AE2 state before the full path succeeds.
     *
     * @param node           node to validate
     * @param start          allocation origin used in diagnostics
     * @param allocationPath validated node list
     * @param visitedNodes   identity set protecting against duplicate increments
     * @return whether the node has capacity and was not already present
     */
    @Unique
    private boolean dataEnergistics$appendAvailableNode(GridNode node,
                                                        GridNode start,
                                                        List<GridNode> allocationPath,
                                                        Set<GridNode> visitedNodes) {
        if (!visitedNodes.add(node)) {
            DATA_ENERGISTICS_LOGGER.error("AE2 supplied a repeated bottleneck node while routing channel from {}", start);
            return false;
        }
        if (this.channelBottlenecks.getOrDefault(node, 0) >= node.getMaxChannels()) {
            return false;
        }
        allocationPath.add(node);
        return true;
    }

    /**
     * Reads a node's controller route while turning invalid AE2 routing state into a logged allocation failure.
     *
     * @param node node whose route is requested
     * @param hub  hub being routed for diagnostic context
     * @return controller route, or {@code null} when AE2 rejects the request
     */
    @Unique
    private static IPathItem dataEnergistics$getControllerRoute(GridNode node, GridNode hub) {
        try {
            return node.getControllerRoute();
        } catch (IllegalStateException exception) {
            DATA_ENERGISTICS_LOGGER.error("AE2 channel hub {} has no valid controller route at node {}", hub, node,
                    exception);
            return null;
        }
    }
}
