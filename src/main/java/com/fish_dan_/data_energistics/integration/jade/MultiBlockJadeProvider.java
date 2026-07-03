package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class MultiBlockJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock");
    public static final ResourceLocation BLOCKS_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock.blocks");
    public static final ResourceLocation ROLE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "multiblock.role");
    private static final String TAG_FORMED = "formed";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_MATCHED_BLOCK_COUNT = "matched_block_count";
    private static final String TAG_CONTROLLER = "controller";
    private static final String TAG_FAILURE_REASON = "failure_reason";
    private static final String TAG_FAILURE_X = "failure_x";
    private static final String TAG_FAILURE_Y = "failure_y";
    private static final String TAG_FAILURE_Z = "failure_z";

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_FORMED)) {
            return;
        }

        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_FORMED) ? "jade.data_energistics.multiblock.formed" : "jade.data_energistics.multiblock.unformed"));
        if (serverData.getBoolean(TAG_FORMED)) {
            appendFormedTooltip(tooltip, serverData, config);
        } else {
            appendFailureTooltip(tooltip, serverData);
        }
    }

    private static void appendFormedTooltip(ITooltip tooltip, CompoundTag serverData, IPluginConfig config) {
        int height = serverData.getInt(TAG_HEIGHT);
        if (height > 0) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.height",
                    height));
        }
        int matchedBlockCount = serverData.getInt(TAG_MATCHED_BLOCK_COUNT);
        if (config.get(BLOCKS_ID) && matchedBlockCount > 0) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.blocks",
                    matchedBlockCount));
        }
        if (config.get(ROLE_ID)) {
            tooltip.add(Component.translatable(
                    serverData.getBoolean(TAG_CONTROLLER) ? "jade.data_energistics.multiblock.role.controller" : "jade.data_energistics.multiblock.role.part"));
        }
    }

    private static void appendFailureTooltip(ITooltip tooltip, CompoundTag serverData) {
        if (serverData.contains(TAG_FAILURE_REASON)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.failure",
                    serverData.getString(TAG_FAILURE_REASON)));
        }
        if (serverData.contains(TAG_FAILURE_X) && serverData.contains(TAG_FAILURE_Y) && serverData.contains(TAG_FAILURE_Z)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.multiblock.failure_position",
                    serverData.getInt(TAG_FAILURE_X),
                    serverData.getInt(TAG_FAILURE_Y),
                    serverData.getInt(TAG_FAILURE_Z)));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        MultiBlockStatusProvider multiBlock = resolveMultiBlock(accessor);
        if (multiBlock == null) {
            return;
        }

        data.putBoolean(TAG_FORMED, multiBlock.multiBlock$isFormed());
        data.putInt(TAG_HEIGHT, multiBlock.multiBlock$getHeight());
        data.putInt(TAG_MATCHED_BLOCK_COUNT, multiBlock.multiBlock$getMatchedBlockCount());
        data.putBoolean(TAG_CONTROLLER, multiBlock.multiBlock$isController());
        String failureReason = multiBlock.multiBlock$getLastFailureReason();
        if (!failureReason.isBlank()) {
            data.putString(TAG_FAILURE_REASON, failureReason);
        }
        BlockPos failurePosition = multiBlock.multiBlock$getLastFailurePosition();
        if (failurePosition != null) {
            data.putInt(TAG_FAILURE_X, failurePosition.getX());
            data.putInt(TAG_FAILURE_Y, failurePosition.getY());
            data.putInt(TAG_FAILURE_Z, failurePosition.getZ());
        }
    }

    private static MultiBlockStatusProvider resolveMultiBlock(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof MultiBlockStatusProvider multiBlock ? multiBlock : null;
    }
}
