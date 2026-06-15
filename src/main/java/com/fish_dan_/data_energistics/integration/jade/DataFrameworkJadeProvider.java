package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.blockentity.DataFrameworkBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class DataFrameworkJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_framework");
    private static final String TAG_ONLINE = "online";
    private static final String TAG_FORMED = "formed";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_CONTROLLER = "controller";

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

        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline"));
        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_FORMED) ? "jade.data_energistics.vertical_multiblock.formed" : "jade.data_energistics.vertical_multiblock.unformed"));
        if (serverData.getBoolean(TAG_FORMED)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.vertical_multiblock.height",
                    serverData.getInt(TAG_HEIGHT)));
            tooltip.add(Component.translatable(
                    serverData.getBoolean(TAG_CONTROLLER) ? "jade.data_energistics.vertical_multiblock.role.controller" : "jade.data_energistics.vertical_multiblock.role.part"));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataFrameworkBlockEntity framework = resolveFramework(accessor);
        if (framework == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, framework.isOnline());
        data.putBoolean(TAG_FORMED, framework.isVerticalMultiBlockFormed());
        data.putInt(TAG_HEIGHT, framework.getVerticalMultiBlockHeight());
        data.putBoolean(TAG_CONTROLLER, framework.isVerticalMultiBlockController());
    }

    private DataFrameworkBlockEntity resolveFramework(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataFrameworkBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataFrameworkBlockEntity framework ? framework : null;
    }
}
