package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import appeng.items.storage.BasicStorageCell;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Optional;

public class DataFlowStorageCellItem extends BasicStorageCell {
    public DataFlowStorageCellItem(Item.Properties properties, double idleDrain, int totalBytes) {
        super(properties.stacksTo(1), idleDrain, totalBytes, 8, 1, DataFlowKeyType.TYPE);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.empty();
    }
}
