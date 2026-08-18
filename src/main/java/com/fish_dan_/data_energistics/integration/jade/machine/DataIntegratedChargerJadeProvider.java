package com.fish_dan_.data_energistics.integration.jade.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.machine.DataIntegratedChargerBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.core.localization.InGameTooltip;
import appeng.util.Platform;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

public class DataIntegratedChargerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_integrated_charger");
    private static final String TAG_ONLINE = "online";
    private static final String TAG_CURRENT_POWER = "current_power";
    private static final String TAG_MAX_POWER = "max_power";

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public int getDefaultPriority() {
        // Run after Jade's universal fluid provider so this machine's input-only tank stays off the HUD.
        return 1001;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE);
        tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE_DETAILED);

        CompoundTag serverData = accessor.getServerData();
        if (serverData.isEmpty()) {
            return;
        }

        if (serverData.contains(TAG_MAX_POWER)) {
            tooltip.add(InGameTooltip.Stored.text(
                    Platform.formatPower(serverData.getDouble(TAG_CURRENT_POWER), false),
                    Platform.formatPower(serverData.getDouble(TAG_MAX_POWER), false)));
        }
        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline"));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataIntegratedChargerBlockEntity charger = resolveCharger(accessor);
        if (charger == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, charger.isOnline());
        data.putDouble(TAG_CURRENT_POWER, charger.getCurrentAEPower());
        data.putDouble(TAG_MAX_POWER, charger.getMaxAEPower());
    }

    private DataIntegratedChargerBlockEntity resolveCharger(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataIntegratedChargerBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataIntegratedChargerBlockEntity charger ? charger : null;
    }
}
