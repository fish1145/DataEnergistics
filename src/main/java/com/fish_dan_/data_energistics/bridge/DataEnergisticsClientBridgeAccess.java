package com.fish_dan_.data_energistics.bridge;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Objects;

public final class DataEnergisticsClientBridgeAccess {

    private static volatile DataEnergisticsClientBridge bridge;

    private DataEnergisticsClientBridgeAccess() {}

    public static void register(DataEnergisticsClientBridge clientBridge) {
        bridge = Objects.requireNonNull(clientBridge, "clientBridge");
    }

    public static DataEnergisticsClientBridge get() {
        if (bridge == null) {
            String message = FMLEnvironment.dist.isClient() ? "Data Energistics client bridge has not been registered" : "Data Energistics client bridge was requested on a non-client distribution";
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        return bridge;
    }
}
