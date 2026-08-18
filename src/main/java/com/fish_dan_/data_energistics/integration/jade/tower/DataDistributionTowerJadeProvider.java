package com.fish_dan_.data_energistics.integration.jade.tower;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class DataDistributionTowerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_distribution_tower");
    private static final String TAG_AE_USED = "ae_used";
    private static final String TAG_AE_MAX = "ae_max";
    private static final String TAG_FE = "fe";
    private static final String TAG_RANGE = "range";
    private static final String TAG_ONLINE = "online";

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_ONLINE)) {
            return;
        }

        Component statusLine = Component.translatable(
                serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline");
        tooltip.add(Math.min(1, tooltip.size()), statusLine);
        tooltip.add(Component.translatable(
                "screen.data_energistics.ae_channels",
                serverData.getInt(TAG_AE_USED),
                serverData.getInt(TAG_AE_MAX)));
        tooltip.add(Component.translatable(
                "screen.data_energistics.network_fe",
                TrinityAmountFormatter.format(serverData.getLong(TAG_FE))));
        tooltip.add(Component.translatable(
                "screen.data_energistics.range",
                formatRangeText(serverData.getInt(TAG_RANGE))));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataDistributionTowerBlockEntity tower = resolveTower(accessor);
        if (tower == null) {
            return;
        }

        data.putInt(TAG_AE_USED, tower.getUsedChannelCount());
        data.putInt(TAG_AE_MAX, tower.getMaxChannelCount());
        data.putLong(TAG_FE, tower.getAvailableFeForUi());
        data.putInt(TAG_RANGE, tower.getConfiguredChunkRadius());
        data.putBoolean(TAG_ONLINE, tower.isNetworkNodeOnline());
    }

    private static Component formatRangeText(int chunkRadius) {
        int diameter = chunkRadius * 2 + 1;
        return Component.translatable("text.data_energistics.data_distribution_tower.range.chunk_square", diameter, diameter);
    }

    private DataDistributionTowerBlockEntity resolveTower(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataDistributionTowerBlock)) {
            return null;
        }

        BlockEntity blockEntity = accessor.getLevel().getBlockEntity(
                DataDistributionTowerBlock.getBasePos(accessor.getPosition(), accessor.getBlockState()));
        return blockEntity instanceof DataDistributionTowerBlockEntity tower ? tower : null;
    }
}
