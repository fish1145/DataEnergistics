package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import java.util.List;

/** Stable production identities and deterministic registration order of Trinity's four hosted child UIs. */
public final class TrinityDataCoreHostUiKeys {

    /** Main structure status and preview window. */
    public static final HostUiKey MAIN = key("main");
    /** CPU structure status and preview window. */
    public static final HostUiKey CPU = key("cpu");
    /** Crafting structure status, preview, and refund window. */
    public static final HostUiKey CRAFTING = key("crafting");
    /** Independent automatic-build configuration window. */
    public static final HostUiKey AUTO_BUILD = key("auto_build");

    private static final List<HostUiKey> REGISTRATION_ORDER = List.of(MAIN, CPU, CRAFTING, AUTO_BUILD);

    private TrinityDataCoreHostUiKeys() {}

    /**
     * Returns the immutable client/server provider registration order sealed by the host coordinator.
     *
     * @return main, CPU, crafting, then automatic-build identities
     */
    public static List<HostUiKey> registrationOrder() {
        return REGISTRATION_ORDER;
    }

    private static HostUiKey key(String structure) {
        return new HostUiKey(Data_Energistics.id("trinity_data_core/" + structure));
    }
}
