package com.fish_dan_.data_energistics.ae2.patternprovider;

import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import org.jetbrains.annotations.Nullable;

public final class RedstoneTuningInventoryHelper {

    private RedstoneTuningInventoryHelper() {}

    public static boolean hasRedstoneTuningCard(Object host, @Nullable IUpgradeInventory fallbackInventory) {
        if (containsCard(fallbackInventory)) {
            return true;
        }

        IUpgradeInventory hostInventory = resolveHostUpgradeInventory(host);
        return hostInventory != fallbackInventory && containsCard(hostInventory);
    }

    public static @Nullable IUpgradeInventory resolveHostUpgradeInventory(Object host) {
        if (host instanceof IUpgradeableObject upgradeableObject) {
            return upgradeableObject.getUpgrades();
        }

        IUpgradeInventory directInventory = invokeUpgradeInventoryMethod(host, "getUpgrades");
        if (directInventory != null) {
            return directInventory;
        }

        Object logic = invokeNoArg(host, "getLogic");
        if (logic == null) {
            return null;
        }

        if (logic instanceof IUpgradeableObject upgradeableLogic) {
            return upgradeableLogic.getUpgrades();
        }

        return invokeUpgradeInventoryMethod(logic, "getUpgrades");
    }

    private static @Nullable IUpgradeInventory invokeUpgradeInventoryMethod(Object target, String methodName) {
        Object result = invokeNoArg(target, methodName);
        return result instanceof IUpgradeInventory upgradeInventory ? upgradeInventory : null;
    }

    private static @Nullable Object invokeNoArg(Object target, String methodName) {
        return ReflectionAccess.invokeNoArg(target, methodName);
    }

    private static boolean containsCard(@Nullable IUpgradeInventory inventory) {
        return inventory != null && inventory.getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }
}
