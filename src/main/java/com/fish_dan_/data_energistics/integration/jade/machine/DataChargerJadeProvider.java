package com.fish_dan_.data_energistics.integration.jade.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.machine.DataChargerBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataChargerBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.core.localization.InGameTooltip;
import appeng.util.Platform;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

public class DataChargerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_charger");
    private static final String TAG_ONLINE = "online";
    private static final String TAG_CURRENT_POWER = "current_power";
    private static final String TAG_MAX_POWER = "max_power";
    private static final String TAG_DATA_FLOW = "data_flow";
    private static final String TAG_MAX_DATA_FLOW = "max_data_flow";
    private static final String TAG_SLOT_COUNT = "slot_count";
    private static final String TAG_STACK_PREFIX = "stack_";

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

        tooltip.add(InGameTooltip.Stored.text(
                Platform.formatPower(serverData.getDouble(TAG_CURRENT_POWER), false),
                Platform.formatPower(serverData.getDouble(TAG_MAX_POWER), false)));
        tooltip.add(Component.translatable("jade.data_energistics.data_charger.data_flow",
                serverData.getLong(TAG_DATA_FLOW),
                serverData.getLong(TAG_MAX_DATA_FLOW)));
        tooltip.add(Component.translatable(serverData.getBoolean(TAG_ONLINE) ? "jade.data_energistics.status.online" : "jade.data_energistics.status.offline"));

        List<ItemStack> stacks = readStacks(accessor, serverData);
        appendStoredItems(tooltip, stacks);

        tooltip.add(Component.translatable("jade.data_energistics.data_charger.accepts",
                Component.translatable(hasPowerItem(stacks) ? "jade.data_energistics.yes" : "jade.data_energistics.no"),
                Component.translatable(hasDataFlowItem(stacks) ? "jade.data_energistics.yes" : "jade.data_energistics.no")));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        DataChargerBlockEntity charger = resolveCharger(accessor);
        if (charger == null) {
            return;
        }

        data.putBoolean(TAG_ONLINE, charger.isOnline());
        data.putDouble(TAG_CURRENT_POWER, charger.getAECurrentPower());
        data.putDouble(TAG_MAX_POWER, charger.getAEMaxPower());
        data.putLong(TAG_DATA_FLOW, charger.getStoredDataFlow());
        data.putLong(TAG_MAX_DATA_FLOW, charger.getDataFlowCapacity());
        data.putInt(TAG_SLOT_COUNT, charger.getActiveSlotCount());
        for (int slot = 0; slot < charger.getActiveSlotCount(); slot++) {
            data.put(TAG_STACK_PREFIX + slot, accessor.encodeAsNbt(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    charger.getDisplayStack(slot)));
        }
    }

    private DataChargerBlockEntity resolveCharger(BlockAccessor accessor) {
        if (!(accessor.getBlockState().getBlock() instanceof DataChargerBlock)) {
            return null;
        }

        return accessor.getBlockEntity() instanceof DataChargerBlockEntity charger ? charger : null;
    }

    private static List<ItemStack> readStacks(BlockAccessor accessor, CompoundTag serverData) {
        int slotCount = Math.min(serverData.getInt(TAG_SLOT_COUNT), DataChargerBlockEntity.EXTENDED_SLOT_COUNT);
        List<ItemStack> stacks = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            Tag stackTag = serverData.get(TAG_STACK_PREFIX + slot);
            ItemStack stack = stackTag == null ? ItemStack.EMPTY : accessor.decodeFromNbt(ItemStack.OPTIONAL_STREAM_CODEC, stackTag).orElse(ItemStack.EMPTY);
            stacks.add(stack);
        }
        return stacks;
    }

    private static boolean hasPowerItem(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (DataChargerBlockEntity.supportsAePower(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDataFlowItem(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (DataChargerBlockEntity.supportsDataFlow(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void appendStoredItems(ITooltip tooltip, List<ItemStack> stacks) {
        IElementHelper elements = IElementHelper.get();
        List<IElement> line = new ArrayList<>();
        line.add(elements.text(Component.translatable("jade.data_energistics.data_charger.stored_items")));
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                line.add(elements.smallItem(stack));
            }
        }
        if (line.size() > 1) {
            tooltip.add(line);
        }
    }
}
