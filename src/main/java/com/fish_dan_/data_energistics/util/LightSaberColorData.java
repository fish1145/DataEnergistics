package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import appeng.api.util.AEColor;
import appeng.items.tools.powered.ColorApplicatorItem;
import org.jetbrains.annotations.Nullable;

public final class LightSaberColorData {

    private static final String TAG_LIGHT_SABER_COLOR = "light_saber_color";
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

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_LIGHT_SABER_COLOR)) {
            return null;
        }

        if (tag.contains(TAG_LIGHT_SABER_COLOR, CompoundTag.TAG_STRING)) {
            String name = tag.getString(TAG_LIGHT_SABER_COLOR);
            for (DyeColor value : DyeColor.values()) {
                if (value.getName().equals(name)) {
                    return value;
                }
            }
        }

        if (tag.contains(TAG_LIGHT_SABER_COLOR, CompoundTag.TAG_INT)) {
            int legacyColor = tag.getInt(TAG_LIGHT_SABER_COLOR) & 0xFFFFFF;
            DyeColor nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (DyeColor value : DyeColor.values()) {
                int diffuse = value.getTextureDiffuseColor() & 0xFFFFFF;
                int distance = colorDistanceSquared(legacyColor, diffuse);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = value;
                }
            }
            return nearest;
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

    private static int colorDistanceSquared(int first, int second) {
        int redDelta = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int greenDelta = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int blueDelta = (first & 0xFF) - (second & 0xFF);
        return redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta;
    }
}
