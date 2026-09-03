package com.fish_dan_.data_energistics.mixin.core.network;

import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderMenuReturnTracker;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean dataEnergistics$returnAfterContainerClose;

    @Inject(method = "handleContainerClose", at = @At("HEAD"))
    private void dataEnergistics$capturePatternEncodingReturn(
                                                              ServerboundContainerClosePacket packet,
                                                              CallbackInfo ci) {
        this.dataEnergistics$returnAfterContainerClose = PatternProviderMenuReturnTracker.isTrackedClientClose(this.player, packet.getContainerId());
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void dataEnergistics$returnToPatternEncodingMenu(
                                                             ServerboundContainerClosePacket packet,
                                                             CallbackInfo ci) {
        boolean shouldReturn = this.dataEnergistics$returnAfterContainerClose;
        this.dataEnergistics$returnAfterContainerClose = false;
        if (shouldReturn) {
            PatternProviderMenuReturnTracker.returnAfterClientClose(this.player, packet.getContainerId());
        }
    }
}
