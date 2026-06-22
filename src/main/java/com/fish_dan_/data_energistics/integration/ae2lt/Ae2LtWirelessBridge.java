package com.fish_dan_.data_energistics.integration.ae2lt;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Ae2LtWirelessBridge {

    private Ae2LtWirelessBridge() {}

    public static boolean isConnectorItem(ItemStack stack) {
        return stack.getItem() instanceof OverloadedWirelessConnectorItem;
    }

    public static String hostProviderType() {
        return OverloadedWirelessConnectorItem.HOST_PROVIDER;
    }

    public static boolean hasSelection(ItemStack stack) {
        return OverloadedWirelessConnectorItem.hasSelection(stack);
    }

    public static @Nullable String getSelectedHostType(ItemStack stack) {
        return OverloadedWirelessConnectorItem.getSelectedHostType(stack);
    }

    public static void selectHost(ItemStack stack, Level level, BlockPos pos, String hostType) {
        OverloadedWirelessConnectorItem.selectHost(stack, level, pos, hostType);
    }

    public static boolean isSelectionInCurrentDimension(Level level, ItemStack stack) {
        return OverloadedWirelessConnectorItem.isSelectionInCurrentDimension(level, stack);
    }

    public static @Nullable BlockEntity getSelectedProvider(Level level, ItemStack stack) {
        return OverloadedWirelessConnectorItem.getSelectedProvider(level, stack);
    }

    public static List<BlockPos> collectTargets(Level level, BlockPos pos, boolean contiguous) {
        try {
            return new ArrayList<>(WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous));
        } catch (RuntimeException | LinkageError e) {
            Data_Energistics.LOGGER.debug("Failed to collect AE2LT wireless connector targets", e);
            return List.of();
        }
    }

    public static boolean isVanillaOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof OverloadedPatternProviderBlockEntity;
    }

    public static List<AdaptiveWirelessConnection> getConnectionsFromVanilla(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof OverloadedPatternProviderBlockEntity provider)) {
            return List.of();
        }
        try {
            return convertConnections(provider.getConnections());
        } catch (RuntimeException | LinkageError e) {
            Data_Energistics.LOGGER.debug("Failed to read AE2LT wireless connections", e);
            return List.of();
        }
    }

    public static boolean removeConnection(@Nullable BlockEntity blockEntity, ResourceKey<Level> dimension, BlockPos pos) {
        if (!(blockEntity instanceof OverloadedPatternProviderBlockEntity provider)) {
            return false;
        }
        try {
            return provider.removeConnection(dimension, pos);
        } catch (RuntimeException | LinkageError e) {
            Data_Energistics.LOGGER.debug("Failed to remove AE2LT wireless connection", e);
            return false;
        }
    }

    public static void addOrUpdateConnection(@Nullable BlockEntity blockEntity,
                                             ResourceKey<Level> dimension,
                                             BlockPos pos,
                                             Direction face) {
        if (!(blockEntity instanceof OverloadedPatternProviderBlockEntity provider)) {
            return;
        }
        try {
            provider.addOrUpdateConnection(dimension, pos, face);
        } catch (RuntimeException | LinkageError e) {
            Data_Energistics.LOGGER.debug("Failed to add or update AE2LT wireless connection", e);
        }
    }

    private static List<AdaptiveWirelessConnection> convertConnections(List<WirelessConnection> list) {
        List<AdaptiveWirelessConnection> converted = new ArrayList<>(list.size());
        for (WirelessConnection connection : list) {
            converted.add(new AdaptiveWirelessConnection(
                    connection.dimension(),
                    connection.pos(),
                    connection.boundFace()));
        }
        return converted;
    }
}
