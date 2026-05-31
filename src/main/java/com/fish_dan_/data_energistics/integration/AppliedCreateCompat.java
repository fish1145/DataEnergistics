package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;

public final class AppliedCreateCompat {

    private AppliedCreateCompat() {}

    public static boolean isMechanicalProviderSupportEnabled() {
        return Data_Energistics.Mods.isCreateLoaded() && Data_Energistics.Mods.isAppliedCreateLoaded();
    }
}
