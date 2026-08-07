package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.network.PatternUploadSource;
import com.fish_dan_.data_energistics.network.PatternUploadSucceededPayload;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.parts.encoding.EncodingMode;

/**
 * Publishes one authoritative history update after a provider inventory has actually accepted a pattern.
 */
public final class PatternUploadRecorder {

    private PatternUploadRecorder() {}

    /**
     * Records exactly one successful operation against its first committed provider leaf.
     */
    public static void record(ServerPlayer player, Object menu,
                              PatternProviderSyncHelper.PatternUploadTarget target,
                              PatternUploadSource source) {
        if (player == null || menu == null || target == null || source == null) {
            throw new IllegalArgumentException("Pattern upload recording requires a player, menu, target, and source");
        }
        if (!(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) ||
                !(menu instanceof PatternEncodingPreviewMenu previewMenu)) {
            throw new IllegalArgumentException("Pattern upload menu does not expose preference context: " + menu);
        }

        try {
            PatternEncodingPreferenceSession session = preferenceMenu.data_energistics$getPreferenceSession();
            PatternEncodingRankingContext rankingContext = resolveRankingContext(previewMenu, session);
            record(player, session, rankingContext, target, source);
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Failed to record committed pattern upload to {}",
                    target.targetName().getString(), exception);
        }
    }

    /**
     * Records a committed upload against the context captured when an asynchronous handoff began.
     */
    public static void record(ServerPlayer player,
                              PatternEncodingPreferenceSession session,
                              PatternEncodingRankingContext rankingContext,
                              PatternProviderSyncHelper.PatternUploadTarget target,
                              PatternUploadSource source) {
        if (player == null || session == null || target == null || source == null) {
            throw new IllegalArgumentException("Pattern upload recording requires a player, session, target, and source");
        }

        try {
            long newCount = rankingContext == null ? 0L : session.incrementLeafCount(rankingContext, target.providerDigest());
            PacketDistributor.sendToPlayer(player, new PatternUploadSucceededPayload(
                    source,
                    rankingContext,
                    target.providerDigest(),
                    newCount,
                    target.targetName(),
                    target.dimensionId(),
                    target.position(),
                    System.currentTimeMillis()));
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error("Failed to record committed pattern upload to {}",
                    target.targetName().getString(), exception);
        }
    }

    private static PatternEncodingRankingContext resolveRankingContext(
                                                                         PatternEncodingPreviewMenu previewMenu,
                                                                        PatternEncodingPreferenceSession session) {
        EncodingMode mode = previewMenu.data_energistics$getEncodingMode();
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(mode);
        if (fixedWorkstation != null) {
            PatternEncodingRankingContext fixedContext = PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                    mode, fixedWorkstation);
            session.setRankingContext(fixedContext);
            return fixedContext;
        }

        PatternEncodingRankingContext rankingContext = session.rankingContext();
        if (PatternEncodingSourceHelper.isRankingContextValid(previewMenu, rankingContext)) {
            return rankingContext;
        }
        Data_Energistics.LOGGER.warn(
                "Committed a processing pattern without a validated recipe/workstation context; history was not learned");
        return null;
    }
}
