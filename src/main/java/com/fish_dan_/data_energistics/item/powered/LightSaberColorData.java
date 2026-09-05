package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.util.AEColor;
import appeng.items.tools.powered.ColorApplicatorItem;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

public final class LightSaberColorData {

    private static final int DEFAULT_LIGHT_SABER_BLADE_COLOR = 0x31F7D3;
    private static final int SANCTIFIER_BLADE_COLOR = 0xFFE359;
    private static final int SANCTIFIER_FRAME_TIME = 25;
    private static final int[] SANCTIFIER_FRAME_COLORS = new int[] {
            0x8ADF81,
            0xF9E07F,
            0xE79C5F,
            0xA48A53,
            0xDF5F5F,
            0xFEB2D3,
            0xEF8FBF,
            0xB58ADE,
            0x6BA3F5,
            0xA0DDFF,
            0x76E2D0,
            0x60D988
    };

    private LightSaberColorData() {}

    public static boolean isColorableLightSaber(ItemStack stack) {
        return stack.is(DEItems.DATA_LIGHT_SABER.get());
    }

    public static float getModelValue(ItemStack stack) {
        DyeColor color = getStoredColor(stack);
        return color == null ? 0.0F : (color.getId() + 1) / 16.0F;
    }

    public static @Nullable DyeColor getStoredColor(ItemStack stack) {
        String storedName = stack.get(DEDataComponents.LIGHT_SABER_COLOR.get());
        if (storedName != null && !storedName.isEmpty()) {
            for (DyeColor value : DyeColor.values()) {
                if (value.getName().equals(storedName)) {
                    return value;
                }
            }
        }

        return null;
    }

    public static ItemStack withColor(ItemStack stack, DyeColor color) {
        ItemStack result = stack.copy();
        result.set(DEDataComponents.LIGHT_SABER_COLOR.get(), color.getName());
        return result;
    }

    public static @Nullable DyeColor getColorFromIngredient(ItemStack stack) {
        if (stack.getItem() instanceof DyeItem dyeItem) {
            return dyeItem.getDyeColor();
        }
        if (stack.getItem() instanceof ColorApplicatorItem colorApplicatorItem) {
            AEColor aeColor = colorApplicatorItem.getActiveColor(stack);
            if (aeColor != null && aeColor != AEColor.TRANSPARENT && aeColor.dye != null) {
                return aeColor.dye;
            }
        }
        return null;
    }

    public static int getBladeColor(ItemStack stack) {
        if (stack.is(DEItems.DATA_SANCTIFIER.get())) {
            return SANCTIFIER_BLADE_COLOR;
        }

        DyeColor color = getStoredColor(stack);
        return color == null ? DEFAULT_LIGHT_SABER_BLADE_COLOR : color.getTextureDiffuseColor();
    }

    public static int getSanctifierAnimatedColor(long gameTime) {
        int frame = (int) ((gameTime / SANCTIFIER_FRAME_TIME) % SANCTIFIER_FRAME_COLORS.length);
        return SANCTIFIER_FRAME_COLORS[Math.floorMod(frame, SANCTIFIER_FRAME_COLORS.length)];
    }
}
