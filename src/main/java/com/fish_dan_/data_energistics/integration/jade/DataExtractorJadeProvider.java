package com.fish_dan_.data_energistics.integration.jade;

import appeng.core.localization.InGameTooltip;
import appeng.util.Platform;
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
    private static final String TAG_CURRENT_POWER = "current_power";
    private static final String TAG_MAX_POWER = "max_power";

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

        if (serverData.contains(TAG_MAX_POWER)) {
            tooltip.add(InGameTooltip.Stored.text(
                    Platform.formatPower(serverData.getDouble(TAG_CURRENT_POWER), false),
                    Platform.formatPower(serverData.getDouble(TAG_MAX_POWER), false)
            ));
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
        if (extractor.getAEMaxPower() > 0) {
            data.putDouble(TAG_CURRENT_POWER, extractor.getAECurrentPower());
            data.putDouble(TAG_MAX_POWER, extractor.getAEMaxPower());
        }
    }

    private DataExtractorBlockEntity resolveExtractor(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataExtractorBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataExtractorBlockEntity extractor ? extractor : null;
    }
}
