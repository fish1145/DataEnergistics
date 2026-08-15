package com.fish_dan_.data_energistics.item.decor;

import com.fish_dan_.data_energistics.block.decor.DollVariant;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class DollBlockItem extends BlockItem {

    public DollBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        DollVariant.normalizeLegacyState(stack);
    }
}
