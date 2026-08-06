package com.fish_dan_.data_energistics.mixin.extendedaeplus;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.extendedaeplus.EaepPatternEncodingHandoff;
import com.fish_dan_.data_energistics.integration.extendedaeplus.EaepPatternUploadScope;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.extendedae_plus.api.upload.IPatternEncodingShiftUploadSync;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PatternEncodingTermMenu.class)
public abstract class EaepPatternEncodingTermMenuMixin implements EaepPatternEncodingHandoff {

    @Unique
    private boolean dataEnergistics$eaepUploadPending;

    @Override
    public void beginEaepEncodeHandoff(boolean dataEnergisticsUploadEnabled) {
        this.dataEnergistics$eaepUploadPending = false;
        boolean shiftUpload = ((IPatternEncodingShiftUploadSync) (Object) this).eap$consumeShiftUploadFlag();
        this.dataEnergistics$eaepUploadPending = !dataEnergisticsUploadEnabled && !shiftUpload;
    }

    @Override
    public void finishEaepEncodeHandoff(boolean encodedSuccessfully) {
        boolean shouldUpload = this.dataEnergistics$eaepUploadPending;
        this.dataEnergistics$eaepUploadPending = false;
        if (!shouldUpload || !encodedSuccessfully) {
            return;
        }

        try {
            PatternEncodingTermMenu menu = (PatternEncodingTermMenu) (Object) this;
            EncodingMode mode = menu.getMode();
            if (mode != EncodingMode.CRAFTING && mode != EncodingMode.SMITHING_TABLE && mode != EncodingMode.STONECUTTING) {
                return;
            }

            Player player = menu.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            PatternEncodingRankingContext rankingContext = PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                    mode, PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode));
            EaepPatternUploadScope.UploadSnapshot snapshot = EaepPatternUploadScope.capture(
                    serverPlayer, menu, rankingContext);
            serverPlayer.server.execute(() -> {
                try (EaepPatternUploadScope.ScopeToken ignored = EaepPatternUploadScope.open(snapshot)) {
                    ExtendedAEPatternUploadUtil.uploadFromEncodingMenuToMatrix(serverPlayer, menu);
                } catch (RuntimeException | LinkageError exception) {
                    this.dataEnergistics$eaepUploadPending = false;
                    Data_Energistics.LOGGER.error(
                            "ExtendedAE-Plus matrix upload failed after pattern encoding", exception);
                }
            });
        } catch (RuntimeException | LinkageError exception) {
            this.dataEnergistics$eaepUploadPending = false;
            Data_Energistics.LOGGER.error(
                    "Could not schedule ExtendedAE-Plus matrix upload after pattern encoding", exception);
        }
    }
}
