package com.fish_dan_.data_energistics.integration;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class AE2FluxIntegration {
    private static final boolean APPFLUX_LOADED = ModList.get().isLoaded("appflux");

    private static Class<?> fluxKeyClass;
    private static Class<?> energyTypeClass;
    private static Method fluxKeyOfMethod;
    private static Object energyTypeFE;

    static {
        if (APPFLUX_LOADED) {
            try {
                initializeReflection();
            } catch (Exception ignored) {
            }
        }
    }

    private AE2FluxIntegration() {
    }

    private static void initializeReflection() throws Exception {
        fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
        energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
        fluxKeyOfMethod = fluxKeyClass.getMethod("of", energyTypeClass);
        energyTypeFE = energyTypeClass.getField("FE").get(null);
    }

    public static boolean isAvailable() {
        return APPFLUX_LOADED && fluxKeyClass != null;
    }

    public static long extractEnergyFromOwnNetwork(AENetworkedBlockEntity blockEntity, long amount, boolean simulate) {
        if (!isAvailable() || amount <= 0) {
            return 0;
        }

        try {
            IManagedGridNode mainNode = blockEntity.getMainNode();
            if (mainNode == null || !mainNode.isReady()) {
                return 0;
            }

            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return 0;
            }

            IStorageService storageService = grid.getStorageService();
            if (storageService == null) {
                return 0;
            }

            MEStorage inventory = storageService.getInventory();
            if (inventory == null) {
                return 0;
            }

            Object fluxKeyObj = fluxKeyOfMethod.invoke(null, energyTypeFE);
            if (!(fluxKeyObj instanceof AEKey fluxKey)) {
                return 0;
            }

            Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            return inventory.extract(fluxKey, amount, actionable, IActionSource.ofMachine(blockEntity));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
