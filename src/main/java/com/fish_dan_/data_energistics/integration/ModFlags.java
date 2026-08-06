package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;

public final class ModFlags {

    private ModFlags() {}

    public static boolean isJechLoaded() {
        return isLoaded("jecharacters");
    }

    public static boolean isAe2WtLibLoaded() {
        return isLoaded("ae2wtlib");
    }

    public static boolean isAe2WtLibWirelessPatternEncodingSupportLoaded() {
        return isAe2WtLibLoaded();
    }

    public static boolean isCreateLoaded() {
        return isLoaded("create");
    }

    public static boolean isCuriosLoaded() {
        return isLoaded("curios");
    }

    public static boolean isAppliedCreateLoaded() {
        return isLoaded("appliedcreate");
    }

    public static boolean isAppliedCreateMechanicalProviderSupportLoaded() {
        return isCreateLoaded() && isAppliedCreateLoaded();
    }

    public static boolean isMekanismLoaded() {
        return isLoaded("mekanism");
    }

    public static boolean isAppMekLoaded() {
        return isLoaded("appmek");
    }

    public static boolean isAppMekChemicalSupportLoaded() {
        return isMekanismLoaded() && isAppMekLoaded();
    }

    public static boolean isAppFluxLoaded() {
        return isLoaded("appflux");
    }

    public static boolean isAppFluxEnergySupportLoaded() {
        return isAppFluxLoaded();
    }

    public static boolean isOritechLoaded() {
        return isLoaded("oritech");
    }

    public static boolean isOritechEnergySupportLoaded() {
        return isOritechLoaded();
    }

    public static boolean isBrandonsCoreLoaded() {
        return isLoaded("brandonscore");
    }

    public static boolean isNeoEcoAeLoaded() {
        return isLoaded("neoecoae");
    }

    public static boolean isNeoEcoAeTowerSupportLoaded() {
        return isNeoEcoAeLoaded();
    }

    private static boolean isLoaded(String modId) {
        return Data_Energistics.isModLoaded(modId);
    }
}
