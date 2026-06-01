package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.common.DataReassemblerGuideLayout;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.world.item.crafting.RecipeHolder;

import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.recipes.LytStandardRecipeBox;

public final class DataRipperReassemblerGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(
                ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get(),
                DataRipperReassemblerGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<DataRipperReassemblerRecipe> createRecipe(
                                                                                  RecipeHolder<DataRipperReassemblerRecipe> holder) {
        var recipe = holder.value();

        return LytStandardRecipeBox.builder()
                .title("Data Reassembler")
                .icon(ModBlocks.DATA_RIPPER_REASSEMBLER.get())
                .customBody(DataRipperReassemblerGuideRecipeBodyFactory.create(recipe))
                .build(holder);
    }
}
