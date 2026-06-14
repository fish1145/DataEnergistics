package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

public final class DataCaptureBallCraftingRemainderHelper {

    private static final long DATA_REASSEMBLER_DATA_COST = 8L;
    private static final TagKey<Item> OBSIDIAN_INGOTS = commonTag("ingots/obsidian");

    private DataCaptureBallCraftingRemainderHelper() {}

    public static boolean canCraftDataReassembler(CraftingInput input) {
        return isDataReassemblerInput(input) && findDataCaptureBallSlot(input) >= 0;
    }

    public static void applyDataReassemblerRemainder(CraftingInput input, NonNullList<ItemStack> remainders) {
        if (!canCraftDataReassembler(input)) {
            return;
        }

        int slot = findDataCaptureBallSlot(input);
        if (slot < 0 || slot >= remainders.size()) {
            return;
        }

        ItemStack returned = input.getItem(slot).copy();
        var cellInventory = StorageCells.getCellInventory(returned, null);
        if (cellInventory == null) {
            return;
        }

        long extracted = cellInventory.extract(DataKey.of(), DATA_REASSEMBLER_DATA_COST, Actionable.MODULATE, IActionSource.empty());
        if (extracted == DATA_REASSEMBLER_DATA_COST) {
            remainders.set(slot, returned);
        }
    }

    private static boolean isDataReassemblerInput(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        return input.getItem(0, 0).is(ModItems.DATA_PROCESSOR.get()) && input.getItem(1, 0).is(ModItems.DATA_FRAMEWORK.get()) && input.getItem(2, 0).is(ModItems.DATA_CAPTURE_BALL.get()) && input.getItem(0, 1).is(AEBlocks.ENERGY_CELL.asItem()) && input.getItem(1, 1).is(AEParts.TERMINAL.asItem()) && input.getItem(2, 1).is(AEBlocks.QUARTZ_GLASS.asItem()) && input.getItem(0, 2).is(OBSIDIAN_INGOTS) && input.getItem(1, 2).is(OBSIDIAN_INGOTS) && input.getItem(2, 2).is(OBSIDIAN_INGOTS);
    }

    private static int findDataCaptureBallSlot(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.DATA_CAPTURE_BALL.get()) && hasStoredData(stack, DATA_REASSEMBLER_DATA_COST)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasStoredData(ItemStack stack, long amount) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && cellInventory.getAvailableStacks().get(DataKey.of()) >= amount;
    }

    private static TagKey<Item> commonTag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
