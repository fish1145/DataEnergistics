package com.fish_dan_.data_energistics.client.preferences;

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

import org.jetbrains.annotations.Nullable;

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
     * Persists workstation changes and publishes recipe context captured by a successful XEI transfer.
     */
    public static void captureTransferredRecipe(AbstractContainerMenu menu) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingPreferenceSession session = interfaces.preferenceMenu().data_energistics$getPreferenceSession();
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(
                interfaces.previewMenu().data_energistics$getEncodingMode());
        if (fixedWorkstation != null) {
            session.setRankingContext(PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                    interfaces.previewMenu().data_energistics$getEncodingMode(), fixedWorkstation));
        } else {
            PatternEncodingClientPreferences preferences = PatternEncodingClientPreferencesAccess.get();
            preferences.setLastWorkstation(interfaces.sourceAware().data_energistics$getLastEncodedPatternSource());
        }
        sendSnapshot(menu);
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
     * Persists and synchronizes the global last-workstation preference, including explicit null.
     */
    public static void setLastWorkstation(AbstractContainerMenu menu, @Nullable ResourceLocation workstation) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferencesAccess.get().setLastWorkstation(workstation);
        interfaces.sourceAware().data_energistics$setLastEncodedPatternSource(workstation);
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
     * Updates the exact ranking context and republishes counts for the currently visible provider leaves.
     */
    public static void setRankingContext(AbstractContainerMenu menu,
                                         @Nullable PatternEncodingRankingContext rankingContext) {
        Interfaces interfaces = Interfaces.require(menu);
        interfaces.preferenceMenu().data_energistics$getPreferenceSession().setRankingContext(rankingContext);
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

        private static Interfaces require(AbstractContainerMenu menu) {
            if (!(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) || !(menu instanceof PatternEncodingPreviewMenu previewMenu) || !(menu instanceof PatternEncodingSourceAware sourceAware) || !(menu instanceof PatternEncodingPreviewLayoutAware layoutAware)) {
                throw new IllegalArgumentException("Menu does not support pattern encoding preferences: " + menu);
            }
            return new Interfaces(preferenceMenu, previewMenu, sourceAware, layoutAware);
        }
    }
}
