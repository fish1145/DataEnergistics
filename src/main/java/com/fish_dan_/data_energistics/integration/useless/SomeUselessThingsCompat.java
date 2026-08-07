package com.fish_dan_.data_energistics.integration.useless;

import net.minecraft.world.item.ItemStack;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.core.component.UComponents;

public final class SomeUselessThingsCompat {

    private SomeUselessThingsCompat() {}

    public static boolean isSilkTouchMode(ItemStack tool) {
        return tool.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) == EnchantMode.SILK_TOUCH;
    }
}
