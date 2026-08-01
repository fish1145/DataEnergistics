package com.fish_dan_.data_energistics.blockentity.tower.network;

import appeng.api.networking.GridServices;

/**
 * Registers Data Energistics grid-domain services during mod loading.
 */
public final class TowerGridServices {

    private static boolean initialized;

    private TowerGridServices() {}

    /**
     * Registers the tower domain exactly once before any runtime grid is created.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        GridServices.register(TowerNetworkDomain.class, TowerNetworkDomainImpl.class);
        initialized = true;
    }
}
