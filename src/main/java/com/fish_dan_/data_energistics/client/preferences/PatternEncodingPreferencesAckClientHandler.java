package com.fish_dan_.data_energistics.client.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.network.patternencoding.PatternEncodingPreferencesAckPayload;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Records server acknowledgements and confirmed workstation state for the current client menu.
 */
public final class PatternEncodingPreferencesAckClientHandler {

    private PatternEncodingPreferencesAckClientHandler() {}

    /**
     * Accepts only acknowledgements of sent snapshots for the current compatible menu.
     */
    public static void handle(PatternEncodingPreferencesAckPayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId() || !(menu instanceof PatternEncodingPreferenceMenu preferenceMenu) || !(menu instanceof PatternEncodingSourceAware) || !(menu instanceof PatternEncodingPreviewLayoutAware)) {
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
    }
}
