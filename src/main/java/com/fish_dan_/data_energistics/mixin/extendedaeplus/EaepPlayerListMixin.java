package com.fish_dan_.data_energistics.mixin.extendedaeplus;

import com.fish_dan_.data_energistics.integration.ae.extendedaeplus.EaepPatternUploadScope;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class EaepPlayerListMixin {

    @Inject(method = "remove", at = @At("HEAD"), require = 1)
    private void dataEnergistics$clearEaepPatternUploadScope(ServerPlayer player, CallbackInfo ci) {
        EaepPatternUploadScope.clearForPlayer(player);
    }
}
