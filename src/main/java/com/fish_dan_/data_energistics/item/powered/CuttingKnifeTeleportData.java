package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;

import net.minecraft.world.item.ItemStack;

public final class CuttingKnifeTeleportData {

    public static final long DATA_FLOW_COST = 20L;
    public static final double AE_POWER_COST = 400.0D;

    private CuttingKnifeTeleportData() {}

    public static boolean hasEnoughDataFlow(ItemStack stack) {
        return getStoredDataFlow(stack) >= DATA_FLOW_COST;
    }

    public static long getStoredDataFlow(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory == null ? 0L : cellInventory.getAvailableStacks().get(DataFlowKey.of());
    }

    public static boolean canConsumeDataFlow(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }
        long extracted = cellInventory.extract(DataFlowKey.of(), DATA_FLOW_COST, Actionable.SIMULATE,
                IActionSource.empty());
        return extracted >= DATA_FLOW_COST;
    }

    public static boolean consumeDataFlow(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }
        long extracted = cellInventory.extract(DataFlowKey.of(), DATA_FLOW_COST, Actionable.MODULATE,
                IActionSource.empty());
        return extracted >= DATA_FLOW_COST;
    }
}
