package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.patternprovider.PatternProviderClickStatistic;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Owns the local pattern-encoding preferences and per-server provider history used by client menus.
 *
 * <p>
 * Implementations are client-main-thread confined so UI changes and network acknowledgements cannot race file
 * persistence.
 * </p>
 */
public interface PatternEncodingClientPreferences {

    int PRESENT_UPLOAD_ENABLED = 1;
    int PRESENT_PATTERN_SOURCE_ENABLED = 1 << 1;
    int PRESENT_LAST_WORKSTATION = 1 << 2;
    int PRESENT_PREVIEW_PANEL = 1 << 3;

    /**
     * Returns a bit mask describing which global values already exist in the local file.
     */
    int presentMask();

    /**
     * Returns the local upload preference, defaulting to enabled before migration.
     */
    boolean uploadEnabled();

    /**
     * Persists the local upload preference immediately.
     */
    void setUploadEnabled(boolean enabled);

    /**
     * Returns the local source-writing preference, defaulting to enabled before migration.
     */
    boolean patternSourceEnabled();

    /**
     * Persists the local source-writing preference immediately.
     */
    void setPatternSourceEnabled(boolean enabled);

    /**
     * Returns the last selected workstation, or {@code null} when explicitly cleared or not migrated.
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
     * Fills only fields that are still absent locally from one validated legacy server snapshot.
     *
     * @return a mask containing the fields that were filled
     */
    int applyMissingLegacyValues(int fieldMask, boolean uploadEnabled, boolean patternSourceEnabled,
                                 @Nullable ResourceLocation lastWorkstation, int offsetX, int offsetY);

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
    List<PatternProviderClickStatistic> statistics(PatternEncodingRankingContext context,
                                                   Collection<String> providerDigests);

    /**
     * Records one authoritative absolute count from the server and persists it idempotently.
     */
    void recordUpload(PatternEncodingRankingContext context, String providerDigest,
                      long absoluteCount, long successEpochMillis);
}
