package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallCondenserRecipe;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.core.definitions.AEBlocks;
import guideme.color.SymbolicColor;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;

import java.util.List;
import java.util.Locale;

public final class DataCaptureBallCondenserGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(ModRecipes.DATA_CAPTURE_BALL_CONDENSER_TYPE.get(),
                DataCaptureBallCondenserGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<DataCaptureBallCondenserRecipe> createRecipe(
                                                                                     RecipeHolder<DataCaptureBallCondenserRecipe> holder) {
        var recipe = holder.value();
        var details = LytParagraph.of(buildDetails(recipe));
        details.modifyStyle(style -> style.color(SymbolicColor.CRAFTING_RECIPE_TYPE));

        return LytStandardRecipeBox.builder()
                .title(Component.translatable("block.ae2.condenser").getString())
                .icon(AEBlocks.CONDENSER)
                .input(LytSlotGrid.row(List.of(recipe.getCatalyst()), true))
                .output(LytSlotGrid.rowFromStacks(List.of(DataCaptureBallItem.createChargedStack()), true))
                .addBottom(details)
                .build(holder);
    }

    private static String buildDetails(DataCaptureBallCondenserRecipe recipe) {
        return String.format(
                Locale.ROOT,
                "%s | %s",
                Component.translatable("button.data_energistics.condenser_output.data_capture_ball").getString(),
                Component.translatable("button.data_energistics.condenser_output.power", recipe.getRequiredPower()).getString());
    }
}
