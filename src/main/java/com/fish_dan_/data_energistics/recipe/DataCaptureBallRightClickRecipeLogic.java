package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class DataCaptureBallRightClickRecipeLogic {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof DataCaptureBallItem dataCaptureBallItem)) {
            return;
        }

        BlockState state = level.getBlockState(event.getPos());
        var input = new DataCaptureBallRightClickRecipeInput(stack, state);
        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE.get())) {
            var recipe = holder.value();
            if (!recipe.matches(input, level) || !dataCaptureBallItem.canRunRightClickRecipe(stack, recipe)) {
                continue;
            }

            if (!level.isClientSide) {
                if (!dataCaptureBallItem.runRightClickRecipe(stack, player, recipe)) {
                    return;
                }
                level.setBlockAndUpdate(event.getPos(), recipe.getResultBlock().defaultBlockState());
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }
    }
}
