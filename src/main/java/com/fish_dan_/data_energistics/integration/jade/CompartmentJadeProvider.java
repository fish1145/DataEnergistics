package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class CompartmentJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "compartment");
    private static final String TAG_TYPE = "type";
    private static final String TAG_BOUND = "bound";
    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_UNLOCKED_SLOTS = "unlocked_slots";
    private static final String TAG_TOTAL_SLOTS = "total_slots";
    private static final String TAG_CAPACITY_CARDS = "capacity_cards";

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(TAG_TYPE)) {
            return;
        }

        String type = serverData.getString(TAG_TYPE);
        tooltip.add(Component.translatable(
                "jade.data_energistics.compartment.type",
                Component.translatable("jade.data_energistics.compartment.type." + type)));
        if (serverData.getBoolean(TAG_BOUND)) {
            tooltip.add(Component.translatable("jade.data_energistics.compartment.bound"));
            if (serverData.contains(TAG_STRUCTURE)) {
                tooltip.add(Component.translatable(
                        "jade.data_energistics.compartment.structure",
                        serverData.getString(TAG_STRUCTURE)));
            }
        } else {
            tooltip.add(Component.translatable("jade.data_energistics.compartment.unbound"));
        }
        tooltip.add(Component.translatable(
                "jade.data_energistics.compartment.slots",
                serverData.getInt(TAG_UNLOCKED_SLOTS),
                serverData.getInt(TAG_TOTAL_SLOTS)));
        if (serverData.contains(TAG_CAPACITY_CARDS)) {
            tooltip.add(Component.translatable(
                    "jade.data_energistics.compartment.capacity_cards",
                    serverData.getInt(TAG_CAPACITY_CARDS)));
        }
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof CompartmentBlock)) {
            return;
        }
        if (!(accessor.getBlockEntity() instanceof CompartmentBlockEntity compartment)) {
            return;
        }

        data.putString(TAG_TYPE, compartment.compartmentType().id());
        data.putBoolean(TAG_BOUND, compartment.isCompartmentBound());
        String structureName = compartment.compartmentStructureName();
        if (structureName != null && !structureName.isBlank()) {
            data.putString(TAG_STRUCTURE, structureName);
        }
        data.putInt(TAG_UNLOCKED_SLOTS, compartment.unlockedSlotCount());
        data.putInt(TAG_TOTAL_SLOTS, compartment.configurableSlotLimit());
        if (compartment instanceof CompositeWarehouseBlockEntity compositeWarehouse) {
            data.putInt(TAG_CAPACITY_CARDS, compositeWarehouse.installedCapacityCards());
        }
    }
}
