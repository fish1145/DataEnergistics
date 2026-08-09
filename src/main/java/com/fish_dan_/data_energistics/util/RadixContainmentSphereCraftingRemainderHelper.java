package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;

public final class RadixContainmentSphereCraftingRemainderHelper {

    public static final long DATA_REASSEMBLER_DATA_COST = 8L;

    private RadixContainmentSphereCraftingRemainderHelper() {}

    public static void applyDataReassemblerRemainder(CraftingInput input, NonNullList<ItemStack> remainders) {
        int slot = findRadixContainmentSphereSlot(input);
        if (slot < 0 || slot >= remainders.size()) {
            return;
        }

        ItemStack returned = input.getItem(slot).copy();
        var cellInventory = StorageCells.getCellInventory(returned, null);
        if (cellInventory == null) {
            return;
        }

        long extracted = cellInventory.extract(DataKey.of(), DATA_REASSEMBLER_DATA_COST, Actionable.MODULATE, IActionSource.empty());
        if (extracted == DATA_REASSEMBLER_DATA_COST) {
            remainders.set(slot, returned);
        }
    }

    private static int findRadixContainmentSphereSlot(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(DEItems.RADIX_CONTAINMENT_SPHERE.get()) && hasRequiredData(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasRequiredData(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && cellInventory.getAvailableStacks().get(DataKey.of()) >= DATA_REASSEMBLER_DATA_COST;
    }
}
