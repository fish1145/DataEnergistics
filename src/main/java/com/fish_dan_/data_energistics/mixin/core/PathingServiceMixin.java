package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.ChannelHubCapacity;
import com.fish_dan_.data_energistics.ae2.ChannelHubCapacityImpl;
import com.fish_dan_.data_energistics.ae2.ChannelHubHost;

import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.service.PathingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/**
 * Extends AE2's controller-less channel count when a channel hub is present while preserving the dense ad-hoc limit.
 */
@Mixin(PathingService.class)
public abstract class PathingServiceMixin {

    /**
     * Capacity calculator shared by all invocations of the ad-hoc redirect.
     */
    @Unique
    private static final ChannelHubCapacity DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY = new ChannelHubCapacityImpl();

    /**
     * Nodes participating in AE2's ad-hoc channel-count validation.
     */
    @Final
    @Shadow
    private Set<IGridNode> nodesNeedingChannels;

    /**
     * Replaces the ordinary eight-channel ad-hoc ceiling with dense capacity when the grid contains a hub.
     *
     * @param mode active AE channel mode
     * @return ordinary ad-hoc capacity or dense hub capacity
     */
    @Redirect(
              method = "calculateAdHocChannels",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/networking/pathing/ChannelMode;getAdHocNetworkChannels()I"),
              require = 1)
    private int dataEnergistics$expandAdHocChannels(ChannelMode mode) {
        int maxChannels = mode.getAdHocNetworkChannels();
        for (IGridNode node : this.nodesNeedingChannels) {
            if (node.getOwner() instanceof ChannelHubHost) {
                return Math.max(
                        maxChannels,
                        DATA_ENERGISTICS_CHANNEL_HUB_CAPACITY.calculate(
                                ControllerState.NO_CONTROLLER,
                                mode,
                                Set.of()));
            }
        }
        return maxChannels;
    }
}
