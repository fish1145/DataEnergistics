package com.fish_dan_.data_energistics.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.me.service.PathingService;
import com.fish_dan_.data_energistics.ae2.CustomAdHocChannelHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(PathingService.class)
public abstract class PathingServiceMixin {
    @Shadow
    private Set<IGridNode> nodesNeedingChannels;

    @Redirect(
            method = "calculateAdHocChannels",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/pathing/ChannelMode;getAdHocNetworkChannels()I"
            )
    )
    private int dataEnergistics$expandAdHocChannels(ChannelMode mode) {
        int maxChannels = mode.getAdHocNetworkChannels();
        for (IGridNode node : this.nodesNeedingChannels) {
            Object owner = node.getOwner();
            if (owner instanceof CustomAdHocChannelHost host) {
                maxChannels = Math.max(maxChannels, host.getCustomAdHocChannels());
            }
        }
        return maxChannels;
    }
}
