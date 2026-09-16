package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.Data_Energistics;

public final class ModFlags {

    private ModFlags() {}

    public static boolean isJechLoaded() {
        return isLoaded("jecharacters");
    }

    /**
     * Reports whether EMI is present and therefore owns Data Energistics's recipe-viewer integration.
     */
    public static boolean isEmiLoaded() {
        return isLoaded("emi");
    }

    public static boolean isAe2WtLibLoaded() {
        return isLoaded("ae2wtlib");
    }

    public static boolean isAe2WtLibWirelessPatternEncodingSupportLoaded() {
        return isAe2WtLibLoaded();
    }

    public static boolean isCuriosLoaded() {
        return isLoaded("curios");
    }

    public static boolean isXaeroWorldMapLoaded() {
        return isLoaded("xaeroworldmap") && isLoaded("xaerolib");
    }

    public static boolean isFtbChunksLoaded() {
        return isLoaded("ftbchunks");
    }

    public static boolean isAppFluxLoaded() {
        return isLoaded("appflux");
    }

    public static boolean isAppFluxEnergySupportLoaded() {
        return isAppFluxLoaded();
    }

    public static boolean isDraconicEvolutionLoaded() {
        return isLoaded("draconicevolution");
    }

    public static boolean isNeoEcoAeLoaded() {
        return isLoaded("neoecoae");
    }

    private static boolean isLoaded(String modId) {
        return Data_Energistics.isModLoaded(modId);
    }
}
