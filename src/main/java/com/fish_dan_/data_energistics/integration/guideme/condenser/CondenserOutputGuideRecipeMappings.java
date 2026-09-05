package com.fish_dan_.data_energistics.integration.guideme.condenser;

import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;
import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.core.definitions.AEBlocks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import guideme.color.SymbolicColor;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;

import java.util.Arrays;
import java.util.List;

public final class CondenserOutputGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(DERecipes.CONDENSER_OUTPUT_TYPE.get(), CondenserOutputGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<CondenserOutputRecipe> createRecipe(
                                                                            RecipeHolder<CondenserOutputRecipe> holder) {
        CondenserOutputRecipe recipe = holder.value();
        List<ItemStack> storageCandidates = Arrays.stream(recipe.getStorageIngredient().getItems())
                .filter(recipe::acceptsStorage)
                .map(ItemStack::copy)
                .toList();
        var details = LytParagraph.of(Component.translatable(
                "button.data_energistics.condenser_output.power",
                recipe.getRequiredPower()).getString());
        details.modifyStyle(style -> style.color(SymbolicColor.CRAFTING_RECIPE_TYPE));

        return LytStandardRecipeBox.builder()
                .title(Component.translatable("recipe.data_energistics.condenser_output").getString())
                .icon(AEBlocks.CONDENSER)
                .input(LytSlotGrid.rowFromStacks(storageCandidates, true))
                .output(LytSlotGrid.rowFromStacks(List.of(recipe.getResult()), true))
                .addBottom(details)
                .build(holder);
    }
}
