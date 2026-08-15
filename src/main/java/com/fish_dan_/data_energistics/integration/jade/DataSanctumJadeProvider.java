package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import appeng.core.localization.InGameTooltip;
import appeng.util.Platform;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class DataSanctumJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_sanctum");
    public static final ResourceLocation MODE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_sanctum.mode");
    public static final ResourceLocation PART_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_sanctum.part");
    private static final String TAG_ONLINE = "online";
    private static final String TAG_MODE = "mode";
    private static final String TAG_PART = "part";
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

        tooltip.add(Component.translatable(
                "jade.data_energistics.data_sanctum.network",
                Component.translatable(serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.data_sanctum.network.online" : "jade.data_energistics.data_sanctum.network.offline")));
        tooltip.add(InGameTooltip.Stored.text(
                Platform.formatPower(serverData.getDouble(TAG_CURRENT_POWER), false),
                Platform.formatPower(serverData.getDouble(TAG_MAX_POWER), false)));
        if (config.get(MODE_ID)) {
            tooltip.add(Component.translatable(
                    "screen.data_energistics.data_sanctum_status.mode",
                    Component.translatable("screen.data_energistics.data_sanctum_status.mode." + serverData.getInt(TAG_MODE))));
        }
        if (config.get(PART_ID)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.data_sanctum.part",
                    Component.translatable("jade.data_energistics.data_sanctum.part." + serverData.getInt(TAG_PART))));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataSanctumBlockEntity sanctum = resolveSanctum(accessor);
        if (sanctum == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, sanctum.isOnline());
        data.putInt(TAG_MODE, getMode(sanctum));
        data.putInt(TAG_PART, getPartKind(accessor.getBlockState()));
        data.putDouble(TAG_CURRENT_POWER, sanctum.getAECurrentPower());
        data.putDouble(TAG_MAX_POWER, sanctum.getAEMaxPower());
    }

    private DataSanctumBlockEntity resolveSanctum(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataSanctumBlock)) {
            return null;
        }

        return DataSanctumBlock.getMainBlockEntity(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
    }

    private static int getMode(DataSanctumBlockEntity sanctum) {
        var state = sanctum.getBlockState();
        return state.hasProperty(DataSanctumBlock.MODE) ? state.getValue(DataSanctumBlock.MODE) : 0;
    }

    private static int getPartKind(BlockState state) {
        if (DataSanctumBlockEntity.isNetworkPortPart(state)) {
            return 1;
        }
        if (DataSanctumBlockEntity.isScreenPart(state)) {
            return 2;
        }
        if (DataSanctumBlockEntity.isMainPart(state)) {
            return 3;
        }
        return 0;
    }
}
