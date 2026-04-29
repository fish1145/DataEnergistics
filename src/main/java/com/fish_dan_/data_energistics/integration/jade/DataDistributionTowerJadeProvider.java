package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
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

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (serverData.isEmpty()) {
            return;
        }

        tooltip.add(Component.translatable(
                "jade.data_energistics.data_distribution_tower.ae",
                serverData.getInt(TAG_AE_USED),
                serverData.getInt(TAG_AE_MAX)
        ));
        tooltip.add(Component.translatable(
                "jade.data_energistics.data_distribution_tower.fe",
                serverData.getString(TAG_FE)
        ));
        tooltip.add(Component.translatable(
                "jade.data_energistics.data_distribution_tower.range",
                formatRangeText(serverData.getInt(TAG_RANGE))
        ));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataDistributionTowerBlockEntity tower = resolveTower(accessor);
        if (tower == null) {
            return;
        }

        data.putInt(TAG_AE_USED, tower.getUsedChannelCount());
        data.putInt(TAG_AE_MAX, tower.getMaxChannelCount());
        data.putString(TAG_FE, tower.getEnergyDisplayText());
        data.putInt(TAG_RANGE, tower.getConfiguredChunkRadius());
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
                DataDistributionTowerBlock.getBasePos(accessor.getPosition(), accessor.getBlockState())
        );
        return blockEntity instanceof DataDistributionTowerBlockEntity tower ? tower : null;
    }
}
