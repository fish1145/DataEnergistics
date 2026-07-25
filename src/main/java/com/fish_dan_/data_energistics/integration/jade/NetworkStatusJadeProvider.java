package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.block.DataSanctumInterfaceBlock;
import com.fish_dan_.data_energistics.block.DigitalStorageDepotBlock;
import com.fish_dan_.data_energistics.blockentity.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class NetworkStatusJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final NetworkStatusJadeProvider DIGITAL_STORAGE_DEPOT = new NetworkStatusJadeProvider("digital_storage_depot", DigitalStorageDepotBlock.class);
    public static final NetworkStatusJadeProvider DATA_SANCTUM_INTERFACE = new NetworkStatusJadeProvider("data_sanctum_interface", DataSanctumInterfaceBlock.class);
    public static final NetworkStatusJadeProvider ME_ACCESS_HATCH = new NetworkStatusJadeProvider("me_access_hatch", CompartmentBlock.class);

    private static final String TAG_ONLINE = "online";

    private final ResourceLocation id;
    private final Class<? extends Block> blockType;

    private NetworkStatusJadeProvider(String path, Class<? extends Block> blockType) {
        this.id = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, path);
        this.blockType = blockType;
    }

    @Override
    public ResourceLocation getUid() {
        return this.id;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_ONLINE)) {
            return;
        }

        tooltip.add(Component.translatable(
                serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline"));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!this.blockType.isInstance(accessor.getBlockState().getBlock())) {
            return;
        }

        Boolean online = resolveOnline(accessor);
        if (online != null) {
            data.putBoolean(TAG_ONLINE, online);
        }
    }

    private static Boolean resolveOnline(BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof DigitalStorageDepotBlockEntity depot) {
            return depot.isOnline();
        }
        if (accessor.getBlockEntity() instanceof DataSanctumInterfaceBlockEntity dataInterface) {
            return dataInterface.isOnline();
        }
        if (accessor.getBlockEntity() instanceof TrinityAccessHatchBlockEntity accessHatch) {
            return accessHatch.isAccessOnline();
        }
        return null;
    }
}
