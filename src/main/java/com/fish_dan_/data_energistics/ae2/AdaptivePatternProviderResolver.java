package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.Nullable;

public final class AdaptivePatternProviderResolver {

    private AdaptivePatternProviderResolver() {}

    public static int getResolvedSlotsPerProvider(ItemStack stack) {
        return AdaptivePatternProviderBlockEntity.getResolvedSlotsPerProvider(stack);
    }

    @Nullable
    public static Component getResolvedProviderDisplayName(ItemStack stack) {
        return AdaptivePatternProviderBlockEntity.getResolvedProviderDisplayName(stack);
    }

    @Nullable
    public static ItemStack getResolvedProviderMainMenuIcon(ItemStack stack) {
        return AdaptivePatternProviderBlockEntity.getResolvedProviderMainMenuIcon(stack);
    }

    @Nullable
    public static AEItemKey getResolvedProviderTerminalIcon(ItemStack stack) {
        return AdaptivePatternProviderBlockEntity.getResolvedProviderTerminalIcon(stack);
    }

    @Nullable
    public static PatternContainerGroup resolveSpecialAdjacentMachineGroup(Level level, BlockPos pos) {
        return AdaptivePatternProviderBlockEntity.resolveSpecialAdjacentMachineGroup(level, pos);
    }

    public static boolean isPatternProviderAttachment(Level level, BlockPos pos, @Nullable Direction side) {
        return AdaptivePatternProviderBlockEntity.isPatternProviderAttachment(level, pos, side);
    }

    public static Component decorateAdaptiveProviderName(Component providerName) {
        return AdaptivePatternProviderBlockEntity.decorateAdaptiveProviderName(providerName);
    }

    public static Component decorateAdaptiveProviderName(String translationKey, Component providerName) {
        return AdaptivePatternProviderBlockEntity.decorateAdaptiveProviderName(translationKey, providerName);
    }
}
