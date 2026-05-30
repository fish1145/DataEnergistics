package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.item.PoweredEnergyItem;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

public final class PoweredCraftingEnergyHelper {

    private PoweredCraftingEnergyHelper() {}

    public static void consumeEnergyFromCraftingRemainders(CraftingInput input, NonNullList<ItemStack> remainders) {
        int limit = Math.min(input.size(), remainders.size());
        for (int i = 0; i < limit; i++) {
            ItemStack original = input.getItem(i);
            ItemStack remainder = remainders.get(i);
            if (!(original.getItem() instanceof PoweredEnergyItem poweredItem) || remainder.isEmpty()) {
                continue;
            }

            if (!remainder.is(original.getItem())) {
                continue;
            }

            poweredItem.consumeActionEnergy(remainder);
        }
    }
}
