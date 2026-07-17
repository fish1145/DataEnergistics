package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import java.util.List;

/**
 * Stable production identity and registration order of Trinity's automatic-build child UI.
 */
public final class TrinityDataCoreHostUiKeys {

    /**
     * Automatic-build configuration and structure-preview window.
     */
    public static final HostUiKey AUTO_BUILD = new HostUiKey(
            Data_Energistics.id("trinity_data_core/auto_build"));

    private static final List<HostUiKey> REGISTRATION_ORDER = List.of(AUTO_BUILD);

    private TrinityDataCoreHostUiKeys() {}

    /**
     * Returns the immutable client/server provider registration order sealed by the host coordinator.
     *
     * @return the sole automatic-build identity
     */
    public static List<HostUiKey> registrationOrder() {
        return REGISTRATION_ORDER;
    }
}
