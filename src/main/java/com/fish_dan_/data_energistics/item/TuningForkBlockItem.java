package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.block.TuningForkBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Preserves a tuning fork's standard damage component while moving it into and out of the world.
 */
public class TuningForkBlockItem extends BlockItem {

    public TuningForkBlockItem(TuningForkBlock block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            return null;
        }
        return super.getPlacementState(context);
    }
}
