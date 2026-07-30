package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import java.util.List;

/**
 * Stable production identities for Trinity Data Core actions and child UI.
 */
public final class TrinityDataCoreHostUiKeys {

    /**
     * Automatic-build configuration and structure-preview window.
     */
    public static final HostUiKey AUTO_BUILD = new HostUiKey(
            Data_Energistics.id("trinity_data_core/auto_build"));

    /**
     * Static menu action that returns only installed patterns from the current Trinity catalog.
     */
    public static final HostUiKey REFUND_PATTERNS = new HostUiKey(
            Data_Energistics.id("trinity_data_core/refund_patterns"));

    /**
     * Static menu action that returns queued inputs and pending outputs without touching installed patterns.
     */
    public static final HostUiKey REFUND_RETAINED_ITEMS = new HostUiKey(
            Data_Energistics.id("trinity_data_core/refund_retained_items"));

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
