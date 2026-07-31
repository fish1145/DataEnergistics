package com.fish_dan_.data_energistics.integration.ae2lt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Ae2LtWirelessBridge {

    private static final String SELECTED_PROVIDER_TAG = "SelectedProvider";
    private static final String DIMENSION_TAG = "Dim";
    private static final String POSITION_TAG = "Pos";

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

    public static @Nullable BlockEntity resolveSelectedBlockEntity(Level level, ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(SELECTED_PROVIDER_TAG, CompoundTag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag selected = tag.getCompound(SELECTED_PROVIDER_TAG);
        ResourceLocation dimensionId = ResourceLocation.tryParse(selected.getString(DIMENSION_TAG));
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos position = BlockPos.of(selected.getLong(POSITION_TAG));
        if (!level.dimension().equals(dimension) || !level.isLoaded(position)) {
            return null;
        }
        return level.getBlockEntity(position);
    }

    public static List<BlockPos> collectTargets(Level level, BlockPos pos, boolean contiguous) {
        return new ArrayList<>(WirelessConnectorTargetHelper.collectTargets(level, pos, contiguous));
    }

    public static boolean isVanillaOverloadedProvider(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof OverloadedPatternProviderBlockEntity;
    }
}
