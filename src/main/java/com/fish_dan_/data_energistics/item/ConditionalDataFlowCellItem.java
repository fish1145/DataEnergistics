package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKeyType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.util.ConfigInventory;

import java.util.List;
import java.util.Optional;

public interface ConditionalDataFlowCellItem extends PoweredEnergyItem, IBasicCellItem, ICellWorkbenchItem {

    int DATA_FLOW_BYTES = 256;
    int DATA_FLOW_BYTES_PER_TYPE = 1;
    int DATA_FLOW_TOTAL_TYPES = 1;

    default boolean hasDataFlowCellSupport(ItemStack stack) {
        return this.getSaberEnergyCardCount(stack) > 0;
    }

    default void appendConditionalCellInformationToTooltip(ItemStack stack, List<Component> lines) {
        if (this.hasDataFlowCellSupport(stack)) {
            this.addCellInformationToTooltip(stack, lines);
        }
    }

    default Optional<TooltipComponent> getConditionalCellTooltipImage(ItemStack stack) {
        return this.hasDataFlowCellSupport(stack) ? this.getCellTooltipImage(stack) : Optional.empty();
    }

    @Override
    default AEKeyType getKeyType() {
        return DataFlowKeyType.TYPE;
    }

    @Override
    default int getBytes(ItemStack stack) {
        return this.hasDataFlowCellSupport(stack) ? DATA_FLOW_BYTES : 0;
    }

    @Override
    default int getBytesPerType(ItemStack stack) {
        return DATA_FLOW_BYTES_PER_TYPE;
    }

    @Override
    default int getTotalTypes(ItemStack stack) {
        return DATA_FLOW_TOTAL_TYPES;
    }

    @Override
    default boolean isBlackListed(ItemStack stack, AEKey requestedAddition) {
        return !this.hasDataFlowCellSupport(stack) || requestedAddition != DataFlowKey.of();
    }

    @Override
    default double getIdleDrain() {
        return 0.0D;
    }

    @Override
    default ConfigInventory getConfigInventory(ItemStack stack) {
        return ConfigInventory.emptyTypes();
    }

    @Override
    default FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    default void setFuzzyMode(ItemStack stack, FuzzyMode fuzzyMode) {}
}
