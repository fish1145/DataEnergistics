package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Optional;

public final class AE2FluxIntegration {

    private static Class<?> fluxKeyClass;
    private static Class<?> energyTypeClass;
    private static boolean initialized;
    private static Object energyTypeFE;
    private static MethodHandle fluxKeyOfMethod;

    static {
        if (ModFlags.isAppFluxLoaded()) {
            try {
                initializeReflection();
            } catch (Exception ignored) {}
        }
    }

    private AE2FluxIntegration() {}

    private static void initializeReflection() throws Exception {
        fluxKeyClass = Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey");
        energyTypeClass = Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
        Optional<VarHandle> energyTypeFeField = ReflectionAccess.findStaticField(energyTypeClass, "FE");
        energyTypeFE = ReflectionAccess.getField(energyTypeFeField, null);
        fluxKeyOfMethod = MethodHandles.publicLookup().findStatic(
                fluxKeyClass,
                "of",
                MethodType.methodType(fluxKeyClass, energyTypeClass));
        initialized = energyTypeFE != null && fluxKeyOfMethod != null;
    }

    public static boolean isAvailable() {
        return ModFlags.isAppFluxLoaded() && initialized;
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

            Object fluxKeyObj = fluxKeyOfMethod.invoke(energyTypeFE);
            if (!(fluxKeyObj instanceof AEKey fluxKey)) {
                return 0;
            }

            Actionable actionable = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            return inventory.extract(fluxKey, amount, actionable, IActionSource.ofMachine(blockEntity));
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
