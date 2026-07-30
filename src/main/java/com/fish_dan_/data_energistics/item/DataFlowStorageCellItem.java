package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.items.storage.BasicStorageCell;

import java.util.Optional;

public class DataFlowStorageCellItem extends BasicStorageCell {

    public DataFlowStorageCellItem(Item.Properties properties, double idleDrain, int totalBytes) {
        super(properties.stacksTo(1), idleDrain, totalBytes, 8, 1, DataFlowKeyType.TYPE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        var stack = player.getItemInHand(usedHand);
        var storageComponent = getStorageComponent(stack);
        if (!player.isShiftKeyDown() || storageComponent.isEmpty() || !isEmptyCell(stack)) {
            return super.use(level, player, usedHand);
        }

        if (!level.isClientSide) {
            player.setItemInHand(usedHand, ItemStack.EMPTY);
            giveBack(player, ModItems.DATA_FLOW_COMPONENT_HOUSING.toStack());
            giveBack(player, storageComponent);
            getUpgrades(stack).forEach(upgrade -> giveBack(player, upgrade));
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.empty();
    }

    private static boolean isEmptyCell(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && cellInventory.getStatus() == CellState.EMPTY;
    }

    private static void giveBack(Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static ItemStack getStorageComponent(ItemStack stack) {
        var item = stack.getItem();
        if (item == ModItems.DATA_FLOW_CELL_1K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_1K.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_4K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_4K.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_16K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_16K.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_64K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_64K.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_256K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_256K.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_1M.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_1M.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_4M.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_4M.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_16M.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_16M.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_64M.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_64M.toStack();
        }
        if (item == ModItems.DATA_FLOW_CELL_256M.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_256M.toStack();
        }

        return ItemStack.EMPTY;
    }
}
