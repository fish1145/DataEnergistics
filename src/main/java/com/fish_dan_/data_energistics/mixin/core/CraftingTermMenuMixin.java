package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.CraftingMatrixSlot;
import appeng.menu.slot.CraftingTermSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CraftingTermMenu.class)
public abstract class CraftingTermMenuMixin {

    @Shadow(remap = false)
    private RecipeHolder<CraftingRecipe> currentRecipe;

    @Final
    @Shadow(remap = false)
    private CraftingTermSlot outputSlot;

    @Final
    @Shadow(remap = false)
    private CraftingMatrixSlot[] craftingSlots;

    @Inject(method = "updateCurrentRecipeAndOutput", at = @At("TAIL"), remap = false)
    private void dataEnergistics$clearTerminalCraftResultWithoutEnergy(boolean forceUpdate, CallbackInfo ci) {
        if (this.currentRecipe == null || !this.currentRecipe.value().getResultItem(null).is(ModItems.DATA_RIPPER_REASSEMBLER.get())) {
            return;
        }

        ItemStack[] stacks = new ItemStack[this.craftingSlots.length];
        for (int i = 0; i < this.craftingSlots.length; i++) {
            stacks[i] = this.craftingSlots[i].getItem().copy();
        }

        if (!DataCaptureBallCraftingRemainderHelper.canCraftDataReassembler(CraftingInput.of(3, 3, List.of(stacks)))) {
            this.outputSlot.set(ItemStack.EMPTY);
        }
    }
}
