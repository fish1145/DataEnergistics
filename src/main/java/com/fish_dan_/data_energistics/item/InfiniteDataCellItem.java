package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.ae2.cell.InfiniteDataCellInventory;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.util.ConfigInventory;

import java.util.List;
import java.util.Optional;

public class InfiniteDataCellItem extends Item implements ICellWorkbenchItem {

    private static final String TAG_OBTAINED_CHECKED = "ObtainedChecked";
    private static final double DEATH_CHANCE = 0.05D;

    public InfiniteDataCellItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        lines.add(Component.translatable("item.data_energistics.data_cell_infinity.tooltip"));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new StorageCellTooltipComponent(
                List.of(),
                DEAE2Keys.keys().stream()
                        .map(key -> new GenericStack(key, InfiniteDataCellInventory.STORED_AMOUNT))
                        .toList(),
                false,
                false));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL || wasObtainedChecked(stack)) {
            return;
        }

        markObtainedChecked(stack);
        if (player.getRandom().nextDouble() >= DEATH_CHANCE) {
            return;
        }

        player.displayClientMessage(Component.translatable("item.data_energistics.data_cell_infinity.overload"), true);
        player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return ConfigInventory.emptyTypes();
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode fuzzyMode) {}

    private static boolean wasObtainedChecked(ItemStack stack) {
        Boolean checked = stack.get(DEDataComponents.INFINITE_DATA_CELL_OBTAINED_CHECKED.get());
        if (checked != null) {
            return checked;
        }
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG_OBTAINED_CHECKED);
    }

    private static void markObtainedChecked(ItemStack stack) {
        stack.set(DEDataComponents.INFINITE_DATA_CELL_OBTAINED_CHECKED.get(), true);
    }
}
