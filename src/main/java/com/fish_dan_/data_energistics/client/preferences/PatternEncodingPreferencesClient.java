package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingViewerRecipeScope;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderClickStatistic;
import com.fish_dan_.data_energistics.network.patternencoding.PatternEncodingPreferencesSyncPayload;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Optional;

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
     * Persists the fixed vanilla context using the mode resolved for this exact successful viewer transfer.
     */
    public static void captureTransferredRecipe(AbstractContainerMenu menu, EncodingMode transferMode) {
        Interfaces interfaces = Interfaces.require(menu);
        ResourceLocation fixedWorkstation = PatternEncodingSourceHelper.resolveFallbackWorkstationForMode(
                transferMode);
        if (fixedWorkstation == null) {
            throw new IllegalStateException("Processing transfers require an exact viewer context");
        }
        interfaces.preferenceMenu().data_energistics$getPreferenceSession().setRankingContext(
                PatternEncodingSourceHelper.resolveFixedModeRankingContext(
                        transferMode, fixedWorkstation));
        interfaces.preferenceMenu().data_energistics$getPreferenceSession().deferSnapshotUntil(transferMode);
    }

    /**
     * Persists a successful processing transfer with its exact recipe-type context.
     */
    public static void captureTransferredProcessingRecipe(AbstractContainerMenu menu,
                                                          PatternEncodingViewerRecipeScope transferredScope) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingPreferenceSession session = interfaces.preferenceMenu().data_energistics$getPreferenceSession();
        if (interfaces.sourceAware().data_energistics$isPatternSourceEnabled()) {
            session.setViewerRecipeScope(
                    transferredScope.rankingContext(), transferredScope.workstationIds());
        } else {
            session.setRankingContext(null);
        }
        session.deferSnapshotUntil(EncodingMode.PROCESSING);
    }

    /**
     * Publishes one transfer snapshot after the target menu mode has been synchronized for at least one client tick.
     */
    public static void flushDeferredSnapshot(AbstractContainerMenu menu) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingPreferenceSession session = interfaces.preferenceMenu().data_energistics$getPreferenceSession();
        if (session.consumeDeferredSnapshotIfReady(interfaces.previewMenu().data_energistics$getEncodingMode())) {
            sendSnapshot(menu);
        }
    }

    /**
     * Removes stale recipe-viewer context after category or workstation lookup fails.
     */
    public static void clearTransferredRecipeContext(PatternEncodingTermMenu menu) {
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
     * Persists and synchronizes recipe-type recording while retaining the existing preference key.
     */
    public static void setPatternSourceEnabled(AbstractContainerMenu menu, boolean enabled) {
        Interfaces interfaces = Interfaces.require(menu);
        PatternEncodingClientPreferencesAccess.get().setPatternSourceEnabled(enabled);
        interfaces.sourceAware().data_energistics$setPatternSourceEnabled(enabled);
        if (!enabled) {
            interfaces.preferenceMenu().data_energistics$getPreferenceSession().setRankingContext(null);
            interfaces.sourceAware().data_energistics$setPendingPatternSource(null);
        }
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

    public static Optional<PatternEncodingClientPreferences.ProviderDetailPanelPosition> providerDetailPanelPosition() {
        return PatternEncodingClientPreferencesAccess.get().providerDetailPanelPosition();
    }

    public static void setProviderDetailPanelPosition(int relativeX, int relativeY) {
        PatternEncodingClientPreferencesAccess.get().setProviderDetailPanelPosition(relativeX, relativeY);
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

        ObjectSet<String> leafDigests = new ObjectLinkedOpenHashSet<>();
        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : interfaces.previewMenu().data_energistics$getSyncedPatternProviders()) {
            for (PatternEncodingPreviewMenu.SyncedPatternProviderLeaf leaf : provider.leaves()) {
                leafDigests.add(leaf.providerDigest());
            }
        }
        PatternEncodingRankingContext rankingContext = session.rankingContext();
        ObjectList<PatternEncodingPreferencesSyncPayload.LeafStatistic> statistics = ObjectLists.emptyList();
        if (rankingContext != null) {
            statistics = new ObjectArrayList<>();
            for (PatternProviderClickStatistic statistic : preferences.statistics(rankingContext, leafDigests)) {
                statistics.add(toPayloadStatistic(statistic));
            }
        }

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
                session.viewerWorkstationIds(),
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
