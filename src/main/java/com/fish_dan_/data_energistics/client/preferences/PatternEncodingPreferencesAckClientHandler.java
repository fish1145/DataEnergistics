package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.network.PatternEncodingPreferencesAckPayload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Applies server-approved legacy preference migration values to the current client menu and JSON preferences.
 */
public final class PatternEncodingPreferencesAckClientHandler {

    private PatternEncodingPreferencesAckClientHandler() {}

    /**
     * Applies only acknowledged fields that are still absent from the client preference file.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) || !(menu instanceof PatternEncodingSourceAware sourceAware) || !(menu instanceof PatternEncodingPreviewLayoutAware layoutAware)) {
            Data_Energistics.LOGGER.warn("Ignored pattern preference acknowledgement for stale container {}",
                    payload.containerId());
            return;
        }
        PatternEncodingPreferenceSession session = preferenceMenu.data_energistics$getPreferenceSession();
        if (!session.acceptAcknowledgement(payload.sequence())) {
            Data_Energistics.LOGGER.warn("Ignored unsent, repeated, or out-of-order pattern preference acknowledgement {}",
                    payload.sequence());
            return;
        }
        session.initializeConfirmedWorkstation(payload.lastWorkstation());
        PatternEncodingClientPreferences preferences = PatternEncodingClientPreferencesAccess.get();
        int presentMask = preferences.presentMask();
        int missingMigration = payload.migratedMask() & ~presentMask;
        if (missingMigration == 0) {
            return;
        }
        int appliedMask = preferences.applyMissingLegacyValues(
                missingMigration, payload.uploadEnabled(), payload.patternSourceEnabled(), payload.lastWorkstation(),
                payload.previewPanelOffsetX(), payload.previewPanelOffsetY());
        if ((appliedMask & PatternEncodingClientPreferences.PRESENT_UPLOAD_ENABLED) != 0) {
            sourceAware.data_energistics$setUploadEnabled(payload.uploadEnabled());
        }
        if ((appliedMask & PatternEncodingClientPreferences.PRESENT_PATTERN_SOURCE_ENABLED) != 0) {
            sourceAware.data_energistics$setPatternSourceEnabled(payload.patternSourceEnabled());
        }
        if ((appliedMask & PatternEncodingClientPreferences.PRESENT_LAST_WORKSTATION) != 0) {
            sourceAware.data_energistics$setLastEncodedPatternSource(payload.lastWorkstation());
        }
        if ((appliedMask & PatternEncodingClientPreferences.PRESENT_PREVIEW_PANEL) != 0) {
            layoutAware.data_energistics$setPreviewPanelOffset(
                    payload.previewPanelOffsetX(), payload.previewPanelOffsetY());
        }
    }
}
