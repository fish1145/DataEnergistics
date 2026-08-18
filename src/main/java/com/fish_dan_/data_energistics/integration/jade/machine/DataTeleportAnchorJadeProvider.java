package com.fish_dan_.data_energistics.integration.jade.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.machine.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.core.localization.InGameTooltip;
import appeng.util.Platform;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class DataTeleportAnchorJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_teleport_anchor");
    private static final String TAG_ONLINE = "online";
    private static final String TAG_CURRENT_POWER = "current_power";
    private static final String TAG_MAX_POWER = "max_power";
    private static final String TAG_COLOR = "color";

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
                    Platform.formatPower(serverData.getDouble(TAG_MAX_POWER), false)));
        }

        if (serverData.contains(TAG_COLOR)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.data_teleport_anchor.color",
                    Component.translatable("color.minecraft." + serverData.getString(TAG_COLOR))));
        }

        tooltip.add(Component.translatable(serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline"));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataTeleportAnchorBlockEntity anchor = resolveAnchor(accessor);
        if (anchor == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, anchor.isOnline());
        if (anchor.getAEMaxPower() > 0) {
            data.putDouble(TAG_CURRENT_POWER, anchor.getAECurrentPower());
            data.putDouble(TAG_MAX_POWER, anchor.getAEMaxPower());
        }
        data.putString(TAG_COLOR, anchor.getChannelId());
    }

    private DataTeleportAnchorBlockEntity resolveAnchor(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataTeleportAnchorBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataTeleportAnchorBlockEntity anchor ? anchor : null;
    }
}
