package com.fish_dan_.data_energistics.block.decor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class DollVariant {

    private static final String FISH_DAN_PREFIX = "fishdan";

    private DollVariant() {}

    public static int fromStack(ItemStack stack) {
        int namedVariant = fromName(stack.get(DataComponents.CUSTOM_NAME));
        if (namedVariant != 0) {
            return namedVariant;
        }

        BlockItemStateProperties stateProperties = stack.getOrDefault(
                DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY);
        Integer storedVariant = stateProperties.get(DollBlock.VARIANT);
        return storedVariant == null ? 0 : storedVariant;
    }

    public static void normalizeLegacyState(ItemStack stack) {
        BlockItemStateProperties stateProperties = stack.get(DataComponents.BLOCK_STATE);
        if (stateProperties == null) {
            return;
        }

        Integer storedVariant = stateProperties.get(DollBlock.VARIANT);
        if (storedVariant != null && storedVariant > 0 && !stack.has(DataComponents.CUSTOM_NAME)) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.translatable("item.data_energistics.fish_dan_variant_" + storedVariant));
        }
        stack.remove(DataComponents.BLOCK_STATE);
    }

    public static int fromName(@Nullable Component name) {
        if (name == null) {
            return 0;
        }

        String normalized = name.getString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        if (!normalized.startsWith(FISH_DAN_PREFIX)) {
            return 0;
        }

        String style = normalized.substring(FISH_DAN_PREFIX.length());
        if (containsAny(style, "新年", "newyear", "chinesenewyear", "springfestival")) {
            return 1;
        }
        if (containsAny(style, "修女", "nun", "sister")) {
            return 2;
        }
        if (containsAny(style, "和服", "kimono", "yukata")) {
            return 3;
        }
        if (containsAny(style, "泳装", "swimsuit", "bathinguit", "bikini")) {
            return 4;
        }
        return 0;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
