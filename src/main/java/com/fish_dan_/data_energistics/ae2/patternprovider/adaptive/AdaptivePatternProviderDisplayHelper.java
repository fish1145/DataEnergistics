package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.core.localization.GuiText;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class AdaptivePatternProviderDisplayHelper {

    private AdaptivePatternProviderDisplayHelper() {}

    public static Component getGuiProviderName(ItemStack providerStack, String fallbackTranslationKey, String variantTranslationKey) {
        Component baseName = getInternalProviderName(providerStack, fallbackTranslationKey);
        if (providerStack.isEmpty()) {
            return baseName;
        }

        return AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack) != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(variantTranslationKey, baseName) : baseName;
    }

    public static Component getTerminalProviderName(ItemStack providerStack, String fallbackTranslationKey) {
        Component baseName = getInternalProviderName(providerStack, fallbackTranslationKey);
        if (providerStack.isEmpty()) {
            return baseName;
        }

        return AdaptivePatternProviderResolver.getResolvedProviderDisplayName(providerStack) != null ? AdaptivePatternProviderResolver.decorateAdaptiveProviderName(baseName) : baseName;
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

    public static Component decorateAttachedMachineName(Component machineName, Component providerName) {
        return Component.translatable(
                "screen.data_energistics.adaptive_pattern_provider.attached_machine",
                machineName,
                providerName);
    }

    public static ItemStack resolveMainMenuIcon(PatternContainerGroup adjacentGroup, ItemStack providerIcon, ItemStack fallbackIcon) {
        if (adjacentGroup != null && adjacentGroup.icon() != null) {
            ItemStack adjacentIcon = adjacentGroup.icon().toStack();
            if (!adjacentIcon.isEmpty()) {
                return adjacentIcon;
            }
        }

        return providerIcon != null && !providerIcon.isEmpty() ? providerIcon.copy() : fallbackIcon;
    }

    public static List<Component> appendLockedSlotsTooltip(List<Component> baseTooltip, String translationKey, int unlockedSlots, int totalSlots) {
        if (unlockedSlots >= totalSlots) {
            return List.copyOf(baseTooltip);
        }

        var tooltip = new ArrayList<>(baseTooltip);
        tooltip.add(Component.translatable(translationKey, unlockedSlots, totalSlots));
        return List.copyOf(tooltip);
    }

    public static PatternContainerGroup createTerminalFallbackGroup(AEItemKey icon, List<PatternContainerGroup> groups) {
        if (groups.size() == 1) {
            return groups.getFirst();
        }

        List<Component> tooltip = List.of();
        if (groups.size() > 1) {
            var builtTooltip = new ArrayList<Component>();
            builtTooltip.add(GuiText.AdjacentToDifferentMachines.text());
            for (var group : groups) {
                builtTooltip.add(group.name());
                for (var line : group.tooltip()) {
                    builtTooltip.add(Component.literal("  ").append(line));
                }
            }
            tooltip = List.copyOf(builtTooltip);
        }

        return new PatternContainerGroup(icon, icon.getDisplayName(), tooltip);
    }

    public static PatternContainerGroup resolveAdjacentMachineGroup(Level level, BlockPos adjacentPos, Direction attachmentSide) {
        if (AdaptivePatternProviderResolver.isPatternProviderAttachment(level, adjacentPos, attachmentSide)) {
            return null;
        }

        return PatternContainerGroup.fromMachine(level, adjacentPos, attachmentSide);
    }
}
