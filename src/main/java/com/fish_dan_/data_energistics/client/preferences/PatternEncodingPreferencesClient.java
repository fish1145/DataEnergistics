package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.common.PatternProviderClickStatistic;
import com.fish_dan_.data_energistics.network.PatternEncodingPreferencesSyncPayload;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies local JSON preferences to client menus and publishes one bounded authoritative snapshot.
 */
public final class PatternEncodingPreferencesClient {

    private PatternEncodingPreferencesClient() {}

    /**
     * Applies only already-present local fields, preserving server legacy values for first-run migration.
     */
    public static void initializeMenu(AbstractContainerMenu menu) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferences preferences = PatternEncodingClientPreferencesAccess.get();
        int presentMask = preferences.presentMask();
        if ((presentMask & PatternEncodingClientPreferences.PRESENT_UPLOAD_ENABLED) != 0) {
            interfaces.sourceAware().data_energistics$setUploadEnabled(preferences.uploadEnabled());
        }
        if ((presentMask & PatternEncodingClientPreferences.PRESENT_PATTERN_SOURCE_ENABLED) != 0) {
            interfaces.sourceAware().data_energistics$setPatternSourceEnabled(preferences.patternSourceEnabled());
        }
        if ((presentMask & PatternEncodingClientPreferences.PRESENT_LAST_WORKSTATION) != 0) {
            interfaces.sourceAware().data_energistics$setLastEncodedPatternSource(preferences.lastWorkstation());
        }
        if ((presentMask & PatternEncodingClientPreferences.PRESENT_PREVIEW_PANEL) != 0) {
            interfaces.layoutAware().data_energistics$setPreviewPanelOffset(
                    preferences.previewPanelOffsetX(), preferences.previewPanelOffsetY());
        }
        PatternEncodingPreferenceSession session = interfaces.preferenceMenu().data_energistics$getPreferenceSession();
        if (session.rankingContext() == null) {
            ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(
                    interfaces.previewMenu().data_energistics$getEncodingMode());
            session.setRankingContext(PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                    interfaces.previewMenu().data_energistics$getEncodingMode(), fixedWorkstation));
        }
        sendSnapshot(menu);
    }

    /**
     * Persists the fixed vanilla context captured by a successful viewer transfer.
     */
    public static void captureTransferredRecipe(AbstractContainerMenu menu) {
        Interfaces interfaces = Interfaces.require(menu);
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(
                interfaces.previewMenu().data_energistics$getEncodingMode());
        if (fixedWorkstation == null) {
            throw new IllegalStateException("Processing transfers require an exact viewer context");
        }
        interfaces.preferenceMenu().data_energistics$getPreferenceSession().setRankingContext(
                PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                        interfaces.previewMenu().data_energistics$getEncodingMode(), fixedWorkstation));
        sendSnapshot(menu);
    }

    /**
     * Persists a successful processing transfer with its exact recipe-type context.
     */
    public static void captureTransferredProcessingRecipe(@NotNull AbstractContainerMenu menu,
                                                          @NotNull PatternEncodingRankingContext transferredContext) {
        Interfaces interfaces = Interfaces.require(menu);
        interfaces.preferenceMenu().data_energistics$getPreferenceSession().setRankingContext(transferredContext);
        sendSnapshot(menu);
    }

    /**
     * Removes stale recipe-viewer context after category or workstation lookup fails.
     */
    public static void clearTransferredRecipeContext(@NotNull PatternEncodingTermMenu menu) {
        try {
            Interfaces.require(menu);
            PatternEncodingSourceHelper.clearViewerTransferContext(menu);
            sendSnapshot(menu);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to clear stale pattern recipe-viewer context after a transfer lookup error",
                    exception);
        }
    }

    /**
     * Persists and synchronizes the global upload-owner preference.
     */
    public static void setUploadEnabled(AbstractContainerMenu menu, boolean enabled) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferencesAccess.get().setUploadEnabled(enabled);
        interfaces.sourceAware().data_energistics$setUploadEnabled(enabled);
        sendSnapshot(menu);
    }

    /**
     * Persists and synchronizes the global source-writing preference.
     */
    public static void setPatternSourceEnabled(AbstractContainerMenu menu, boolean enabled) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferencesAccess.get().setPatternSourceEnabled(enabled);
        interfaces.sourceAware().data_energistics$setPatternSourceEnabled(enabled);
        sendSnapshot(menu);
    }

    /**
     * Persists and synchronizes the shared preview-panel offset.
     */
    public static void setPreviewPanelOffset(AbstractContainerMenu menu, int offsetX, int offsetY) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferencesAccess.get().setPreviewPanelOffset(offsetX, offsetY);
        interfaces.layoutAware().data_energistics$setPreviewPanelOffset(offsetX, offsetY);
        sendSnapshot(menu);
    }

    /**
     * Sends one monotonic snapshot for the exact current menu.
     */
    public static void sendSnapshot(AbstractContainerMenu menu) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferences preferences = PatternEncodingClientPreferencesAccess.get();
        PatternEncodingPreferenceSession session = interfaces.preferenceMenu().data_energistics$getPreferenceSession();
        int presentMask = preferences.presentMask();

        boolean uploadEnabled = (presentMask & PatternEncodingClientPreferences.PRESENT_UPLOAD_ENABLED) != 0 ? preferences.uploadEnabled() : interfaces.sourceAware().data_energistics$isUploadEnabled();
        boolean patternSourceEnabled = (presentMask & PatternEncodingClientPreferences.PRESENT_PATTERN_SOURCE_ENABLED) != 0 ? preferences.patternSourceEnabled() : interfaces.sourceAware().data_energistics$isPatternSourceEnabled();
        ResourceLocation lastWorkstation = (presentMask & PatternEncodingClientPreferences.PRESENT_LAST_WORKSTATION) != 0 ? preferences.lastWorkstation() : interfaces.sourceAware().data_energistics$getLastEncodedPatternSource();
        int offsetX = (presentMask & PatternEncodingClientPreferences.PRESENT_PREVIEW_PANEL) != 0 ? preferences.previewPanelOffsetX() : interfaces.layoutAware().data_energistics$getPreviewPanelOffsetX();
        int offsetY = (presentMask & PatternEncodingClientPreferences.PRESENT_PREVIEW_PANEL) != 0 ? preferences.previewPanelOffsetY() : interfaces.layoutAware().data_energistics$getPreviewPanelOffsetY();

        Set<String> leafDigests = new LinkedHashSet<>();
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : interfaces.previewMenu().data_energistics$getSyncedPatternProviders()) {
            leafDigests.addAll(provider.leafDigests());
        }
        PatternEncodingRankingContext rankingContext = session.rankingContext();
        List<PatternEncodingPreferencesSyncPayload.LeafStatistic> statistics = rankingContext == null ? List.of() : preferences.statistics(rankingContext, leafDigests).stream()
                .map(PatternEncodingPreferencesClient::toPayloadStatistic)
                .toList();

        PacketDistributor.sendToServer(new PatternEncodingPreferencesSyncPayload(
                menu.containerId,
                session.nextOutgoingSequence(),
                presentMask,
                uploadEnabled,
                patternSourceEnabled,
                lastWorkstation,
                offsetX,
                offsetY,
                rankingContext,
                statistics));
    }

    private static PatternEncodingPreferencesSyncPayload.LeafStatistic toPayloadStatistic(
                                                                                          PatternProviderClickStatistic statistic) {
        return new PatternEncodingPreferencesSyncPayload.LeafStatistic(
                statistic.providerDigest(), statistic.count());
    }

    private record Interfaces(PatternEncodingPreferenceMenu preferenceMenu,
                              PatternEncodingPreviewMenu previewMenu,
                              PatternEncodingSourceAware sourceAware,
                              PatternEncodingPreviewLayoutAware layoutAware) {

        private static Interfaces require(@NotNull AbstractContainerMenu menu) {
            if (!(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) || !(menu instanceof PatternEncodingPreviewMenu previewMenu) || !(menu instanceof PatternEncodingSourceAware sourceAware) || !(menu instanceof PatternEncodingPreviewLayoutAware layoutAware)) {
                throw new IllegalArgumentException("Menu does not support pattern encoding preferences: " + menu);
            }
            return new Interfaces(preferenceMenu, previewMenu, sourceAware, layoutAware);
        }
    }
}
