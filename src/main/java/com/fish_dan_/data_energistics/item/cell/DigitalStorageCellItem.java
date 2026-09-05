package com.fish_dan_.data_energistics.item.cell;

import com.fish_dan_.data_energistics.ae2.dataflow.DigitalStorageCellTooltip;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.items.contents.CellConfig;
import appeng.items.storage.BasicStorageCell;
import appeng.util.ConfigInventory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DigitalStorageCellItem extends BasicStorageCell {

    private static final int TOTAL_TYPES = 2;

    public DigitalStorageCellItem(Item.Properties properties, double idleDrain, int totalBytes) {
        super(properties.stacksTo(1), idleDrain, totalBytes, 8, TOTAL_TYPES, DigitalizationKeyType.TYPE);
    }

    @Override
    public int getTotalTypes(ItemStack stack) {
        return TOTAL_TYPES;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(DigitalizationKeyType.TYPE), stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        DigitalStorageCellTooltip.addCellInformation(stack, lines);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return DigitalStorageCellTooltip.getTooltipImage(stack);
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
            giveBack(player, DEItems.DATA_FLOW_COMPONENT_HOUSING.toStack());
            giveBack(player, storageComponent);
            getUpgrades(stack).forEach(upgrade -> giveBack(player, upgrade));
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
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
        if (item == DEItems.DIGITAL_STORAGE_CELL_1K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_1K.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_4K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_4K.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_16K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_16K.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_64K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_64K.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_256K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_256K.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_1M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_1M.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_4M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_4M.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_16M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_16M.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_64M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_64M.toStack();
        }
        if (item == DEItems.DIGITAL_STORAGE_CELL_256M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_256M.toStack();
        }

        return ItemStack.EMPTY;
    }
}
