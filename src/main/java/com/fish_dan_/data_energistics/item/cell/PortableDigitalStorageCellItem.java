package com.fish_dan_.data_energistics.item.cell;

import com.fish_dan_.data_energistics.ae2.dataflow.DigitalStorageCellTooltip;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.core.definitions.AEItems;
import appeng.items.contents.CellConfig;
import appeng.items.storage.StorageTier;
import appeng.items.tools.powered.PortableCellItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;
import appeng.util.InteractionUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PortableDigitalStorageCellItem extends PortableCellItem {

    public PortableDigitalStorageCellItem(StorageTier tier, Item.Properties properties, int color) {
        super(DigitalizationKeyType.TYPE, 2, null, tier, properties.stacksTo(1), color);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(DigitalizationKeyType.TYPE), stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
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
        var meChest = getAe2Ingredient("chest");
        var energyCell = getReturnedEnergyCell(stack);
        if (!player.isShiftKeyDown() || storageComponent.isEmpty() || meChest.isEmpty() || energyCell.isEmpty() || !isEmptyCell(stack)) {
            return openPortableCell(level, player, usedHand);
        }

        if (!level.isClientSide) {
            player.setItemInHand(usedHand, ItemStack.EMPTY);
            giveBack(player, meChest);
            giveBack(player, storageComponent);
            giveBack(player, energyCell);
            giveBack(player, DEItems.DATA_FLOW_COMPONENT_HOUSING.toStack());
            getUpgrades(stack).forEach(upgrade -> giveBack(player, upgrade));
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
    }

    @Override
    protected boolean openFromInventory(Player player, ItemMenuHostLocator locator, boolean returningFromSubmenu) {
        ItemStack stack = locator.locateItem(player);
        if (stack.getItem() != this) {
            return false;
        }

        return MenuOpener.open(resolvePortableItemCellMenu(), player, locator, returningFromSubmenu);
    }

    @Override
    public int getBytes(ItemStack stack) {
        return super.getBytes(stack);
    }

    private InteractionResultHolder<ItemStack> openPortableCell(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide && !InteractionUtil.isInAlternateUseMode(player)) {
            MenuOpener.open(resolvePortableItemCellMenu(), player, MenuLocators.forHand(player, usedHand));
        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide), player.getItemInHand(usedHand));
    }

    private static MenuType<?> resolvePortableItemCellMenu() {
        return AEItems.PORTABLE_ITEM_CELL1K.get().menuType;
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
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_1K.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_4K.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_16K.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_64K.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256K.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_256K.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_1M.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_4M.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_16M.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_64M.toStack();
        }
        if (item == DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256M.get()) {
            return DEItems.DATA_STORAGE_COMPONENT_256M.toStack();
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
