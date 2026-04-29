package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataExtractorBlock;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class DataExtractorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_extractor");
    private static final String TAG_ONLINE = "online";

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

        tooltip.add(Component.translatable(serverData.getBoolean(TAG_ONLINE)
                ? "jade.data_energistics.data_extractor.status.online"
                : "jade.data_energistics.data_extractor.status.offline"));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataExtractorBlockEntity extractor = resolveExtractor(accessor);
        if (extractor == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, extractor.isOnline());
    }

    private DataExtractorBlockEntity resolveExtractor(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataExtractorBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataExtractorBlockEntity extractor ? extractor : null;
    }
}
