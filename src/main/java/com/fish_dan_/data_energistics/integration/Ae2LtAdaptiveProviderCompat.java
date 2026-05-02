package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Ae2LtAdaptiveProviderCompat {
    private Ae2LtAdaptiveProviderCompat() {
    }

    public static boolean isAdaptiveOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof AdaptivePatternProviderBlockEntity adaptive
                && adaptive.isAe2LightningTechOverloadedProviderSelected();
    }

    @Nullable
    public static AdaptivePatternProviderBlockEntity asAdaptiveOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return isAdaptiveOverloadedProvider(blockEntity) ? (AdaptivePatternProviderBlockEntity) blockEntity : null;
    }

    public static boolean isWirelessMode(@Nullable BlockEntity blockEntity) {
        AdaptivePatternProviderBlockEntity adaptive = asAdaptiveOverloadedProvider(blockEntity);
        return adaptive != null && adaptive.isAe2LtWirelessMode();
    }

    public static List<OverloadedPatternProviderBlockEntity.WirelessConnection> getConnections(@Nullable BlockEntity blockEntity) {
        AdaptivePatternProviderBlockEntity adaptive = asAdaptiveOverloadedProvider(blockEntity);
        return adaptive != null ? adaptive.getConnections() : List.of();
    }

    public static void addOrUpdateConnection(@Nullable BlockEntity blockEntity, ResourceKey<Level> dimension, BlockPos pos, Direction face) {
        AdaptivePatternProviderBlockEntity adaptive = asAdaptiveOverloadedProvider(blockEntity);
        if (adaptive != null) {
            adaptive.addOrUpdateConnection(dimension, pos, face);
        }
    }

    public static boolean removeConnection(@Nullable BlockEntity blockEntity, ResourceKey<Level> dimension, BlockPos pos) {
        AdaptivePatternProviderBlockEntity adaptive = asAdaptiveOverloadedProvider(blockEntity);
        return adaptive != null && adaptive.removeConnection(dimension, pos);
    }
}
