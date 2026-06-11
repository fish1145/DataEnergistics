package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;

public final class ModFlags {

    private ModFlags() {}

    public static boolean isAnyRecipeViewerLoaded() {
        return isLoaded("jei") || isLoaded("emi");
    }

    public static boolean isJEILoaded() {
        return !isLoaded("emi") && isLoaded("jei");
    }

    public static boolean isEMILoaded() {
        return isLoaded("emi");
    }

    public static boolean isAE2Loaded() {
        return isLoaded("ae2");
    }

    public static boolean isSodiumLoaded() {
        return isLoaded("sodium");
    }

    public static boolean isIrisLoaded() {
        return isLoaded("iris");
    }

    public static boolean isJechLoaded() {
        return isLoaded("jecharacters");
    }

    public static boolean isExtendedAePlusLoaded() {
        return isLoaded("extendedae_plus");
    }

    public static boolean isAe2WtLibLoaded() {
        return isLoaded("ae2wtlib");
    }

    public static boolean isAe2LtLoaded() {
        return isLoaded("ae2lt");
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

    public static boolean isMekanismLoaded() {
        return isLoaded("mekanism");
    }

    public static boolean isAppMekLoaded() {
        return isLoaded("appmek");
    }

    public static boolean isAppFluxLoaded() {
        return isLoaded("appflux");
    }

    public static boolean isOritechLoaded() {
        return isLoaded("oritech");
    }

    private static boolean isLoaded(String modId) {
        return Data_Energistics.isModLoaded(modId);
    }
}
