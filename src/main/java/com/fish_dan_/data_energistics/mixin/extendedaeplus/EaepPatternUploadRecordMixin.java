package com.fish_dan_.data_energistics.mixin.extendedaeplus;

import com.fish_dan_.data_energistics.integration.extendedaeplus.EaepPatternUploadScope;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import appeng.helpers.patternprovider.PatternContainer;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExtendedAEPatternUploadUtil.class, remap = false)
public abstract class EaepPatternUploadRecordMixin {

    @Inject(method = "recordProviderUpload", at = @At("RETURN"), remap = false, require = 1)
    private static void dataEnergistics$recordProviderUpload(ServerPlayer player, long providerId,
                                                              PatternContainer container, int slot, CallbackInfo ci) {
        EaepPatternUploadScope.recordProviderUpload(player, container);
    }

    @Inject(method = "recordMatrixUpload", at = @At("RETURN"), remap = false, require = 1)
    private static void dataEnergistics$recordMatrixUpload(ServerPlayer player, BlockPos position, String dimension,
                                                            boolean plus, int slot, CallbackInfo ci) {
        EaepPatternUploadScope.recordMatrixUpload(player, position, dimension, plus);
    }
}
