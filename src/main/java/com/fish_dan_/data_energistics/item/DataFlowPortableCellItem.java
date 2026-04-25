package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.registry.ModItems;
import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.items.tools.powered.PortableCellItem;
import appeng.items.storage.StorageTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;

public class DataFlowPortableCellItem extends PortableCellItem {
    public DataFlowPortableCellItem(StorageTier tier, Item.Properties properties, int color) {
        super(DataFlowKeyType.TYPE, 1, portableItemCellMenu(), tier, properties.stacksTo(1), color);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        var stack = player.getItemInHand(usedHand);
        var storageComponent = getStorageComponent(stack);
        var meChest = getAe2Ingredient("chest");
        var energyCell = getReturnedEnergyCell(stack);
        if (!player.isShiftKeyDown() || storageComponent.isEmpty() || meChest.isEmpty() || energyCell.isEmpty()
                || !isEmptyCell(stack)) {
            return super.use(level, player, usedHand);
        }

        if (!level.isClientSide) {
            player.setItemInHand(usedHand, ItemStack.EMPTY);
            giveBack(player, meChest);
            giveBack(player, storageComponent);
            giveBack(player, energyCell);
            giveBack(player, ModItems.DATA_FLOW_COMPONENT_HOUSING.toStack());
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
    }

    @Override
    public int getBytes(ItemStack stack) {
        return super.getBytes(stack);
    }

    private static MenuType<?> portableItemCellMenu() {
        try {
            Field field = AbstractPortableCell.class.getDeclaredField("menuType");
            field.setAccessible(true);
            return (MenuType<?>) field.get(AEItems.PORTABLE_ITEM_CELL1K.get());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access AE2 portable item cell menu type", e);
        }
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
        if (item == ModItems.PORTABLE_DATA_FLOW_CELL_1K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_1K.toStack();
        }
        if (item == ModItems.PORTABLE_DATA_FLOW_CELL_4K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_4K.toStack();
        }
        if (item == ModItems.PORTABLE_DATA_FLOW_CELL_16K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_16K.toStack();
        }
        if (item == ModItems.PORTABLE_DATA_FLOW_CELL_64K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_64K.toStack();
        }
        if (item == ModItems.PORTABLE_DATA_FLOW_CELL_256K.get()) {
            return ModItems.DATA_STORAGE_COMPONENT_256K.toStack();
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getAe2Ingredient(String path) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", path));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ItemStack getReturnedEnergyCell(ItemStack portableCell) {
        var energyCell = getAe2Ingredient("energy_cell");
        if (energyCell.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var storedPower = this.getAECurrentPower(portableCell);
        if (storedPower <= 0) {
            return energyCell;
        }

        if (energyCell.getItem() instanceof IAEItemPowerStorage powerStorage) {
            powerStorage.injectAEPower(energyCell, storedPower, Actionable.MODULATE);
        }

        return energyCell;
    }
}
