package com.fish_dan_.data_energistics.ae2;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class AdaptivePatternProviderDisplayHelper {

    private AdaptivePatternProviderDisplayHelper() {
    }

    public static Component getGuiProviderName(ItemStack providerStack, String fallbackTranslationKey, String variantTranslationKey) {
        Component baseName = getInternalProviderName(providerStack, fallbackTranslationKey);
        if (providerStack.isEmpty()) {
            return baseName;
        }

        return AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack) != null
                ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(variantTranslationKey, baseName)
                : baseName;
    }

    public static Component getTerminalProviderName(ItemStack providerStack, String fallbackTranslationKey) {
        Component baseName = getInternalProviderName(providerStack, fallbackTranslationKey);
        if (providerStack.isEmpty()) {
            return baseName;
        }

        return AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack) != null
                ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(baseName)
                : baseName;
    }

    public static Component getInternalProviderName(ItemStack providerStack, String fallbackTranslationKey) {
        if (providerStack.isEmpty()) {
            return Component.translatable(fallbackTranslationKey);
        }

        Component displayName = AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack);
        return displayName != null ? displayName : Component.translatable(fallbackTranslationKey);
    }

    public static int getConfiguredPatternSlotCount(ItemStack providerStack, int providerSlotLimit) {
        int slotsPerProvider = AdaptivePatternProviderResolver.getResolvedSlotsPerProvider(providerStack);
        if (slotsPerProvider <= 0) {
            return 0;
        }

        int providerCount = Math.min(providerStack.getCount(), providerSlotLimit);
        return slotsPerProvider * providerCount;
    }

    public static int getMaxPatternCapacity(ItemStack providerStack, int providerSlotLimit, int maxPatternSlots) {
        int slotsPerProvider = AdaptivePatternProviderResolver.getResolvedSlotsPerProvider(providerStack);
        if (slotsPerProvider <= 0) {
            return 0;
        }

        return Math.min(maxPatternSlots, slotsPerProvider * providerSlotLimit);
    }
}
