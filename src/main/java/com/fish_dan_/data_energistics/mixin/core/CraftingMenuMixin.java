package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void dataEnergistics$clearCraftResultWithoutEnergy(AbstractContainerMenu menu, Level level,
                                                                      Player player, CraftingContainer craftSlots,
                                                                      ResultContainer resultSlots,
                                                                      RecipeHolder<CraftingRecipe> recipe,
                                                                      CallbackInfo ci) {
        if (recipe == null || !recipe.value().getResultItem(level.registryAccess()).is(ModItems.DATA_RIPPER_REASSEMBLER.get())) {
            return;
        }

        if (!DataCaptureBallCraftingRemainderHelper.canCraftDataReassembler(craftSlots.asCraftInput())) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }
}
