package com.fish_dan_.data_energistics.recipe.containmentsphere;

import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class RadixContainmentSphereRightClickRecipeLogic {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        BlockState state = level.getBlockState(event.getPos());
        var input = new RadixContainmentSphereRightClickRecipeInput(stack, state);
        for (var holder : level.getRecipeManager().getAllRecipesFor(DERecipes.RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_TYPE.get())) {
            var recipe = holder.value();
            if (!recipe.matches(input, level)) {
                continue;
            }

            RadixContainmentSphereItem radixContainmentSphereItem = stack.getItem() instanceof RadixContainmentSphereItem item ? item : null;
            if (radixContainmentSphereItem == null && !canRunOrdinaryItem(recipe.getDataCost(), recipe.getEnergyCost())) {
                continue;
            }
            if (radixContainmentSphereItem != null && !radixContainmentSphereItem.canRunRightClickRecipe(stack, recipe)) {
                continue;
            }

            if (!level.isClientSide) {
                if (radixContainmentSphereItem != null && !radixContainmentSphereItem.runRightClickRecipe(stack, player, recipe)) {
                    return;
                }
                level.setBlockAndUpdate(event.getPos(), recipe.getResultBlock().defaultBlockState());
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }
    }

    static boolean canRunOrdinaryItem(long dataCost, double energyCost) {
        return dataCost == 0L && energyCost == 0.0D;
    }
}
