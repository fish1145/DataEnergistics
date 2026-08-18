package com.fish_dan_.data_energistics.item.decor;

import com.fish_dan_.data_energistics.block.decor.DollVariant;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DollBlockItem extends BlockItem {

    public DollBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        DollVariant.normalizeLegacyState(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        if (DollVariant.fromStack(stack) == 5) {
            lines.add(Component.translatable("item.data_energistics.fish_dan_variant_5.tooltip"));
        }
    }
}
