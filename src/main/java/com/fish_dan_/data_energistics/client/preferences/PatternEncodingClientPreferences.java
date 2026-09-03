package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderClickStatistic;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Owns the local pattern-encoding preferences and per-server provider history used by client menus.
 *
 * <p>
 * Implementations are client-main-thread confined so UI changes and network acknowledgements cannot race file
 * persistence.
 * </p>
 */
public interface PatternEncodingClientPreferences {

    /**
     * Returns the local upload preference, defaulting to enabled when absent.
     */
    boolean uploadEnabled();

    /**
     * Persists the local upload preference immediately.
     */
    void setUploadEnabled(boolean enabled);

    /**
     * Returns the local source-writing preference, defaulting to enabled when absent.
     */
    boolean patternSourceEnabled();

    /**
     * Persists the local source-writing preference immediately.
     */
    void setPatternSourceEnabled(boolean enabled);

    /**
     * Returns the last selected workstation, or {@code null} when cleared or absent.
     */
    @Nullable
    ResourceLocation lastWorkstation();

    /**
     * Persists an explicit workstation value, including an explicit {@code null}.
     */
    void setLastWorkstation(@Nullable ResourceLocation workstation);

    /**
     * Returns the shared preview panel horizontal offset.
     */
    int previewPanelOffsetX();

    /**
     * Returns the shared preview panel vertical offset.
     */
    int previewPanelOffsetY();

    /**
     * Persists the shared preview panel offset immediately.
     */
    void setPreviewPanelOffset(int offsetX, int offsetY);

    /**
     * Returns the optional screen-local provider-detail panel position shared by encoding terminals.
     */
    Optional<ProviderDetailPanelPosition> providerDetailPanelPosition();

    /**
     * Persists the provider-detail panel position without synchronizing it to the server.
     */
    void setProviderDetailPanelPosition(int relativeX, int relativeY);

    /**
     * Selects the isolated server profile that subsequent statistic operations use.
     */
    void activateServerProfile(String profileDigest);

    /**
     * Clears connection-scoped state so the next server cannot inherit statistics.
     */
    void deactivateServerProfile();

    /**
     * Returns statistics for the requested context and currently synchronized provider digests.
     */
    ObjectList<PatternProviderClickStatistic> statistics(PatternEncodingRankingContext context,
                                                         ObjectCollection<String> providerDigests);

    /**
     * Records one authoritative absolute count from the server and persists it idempotently.
     */
    void recordUpload(PatternEncodingRankingContext context, String providerDigest,
                      long absoluteCount, long successEpochMillis);

    record ProviderDetailPanelPosition(int relativeX, int relativeY) {}
}
