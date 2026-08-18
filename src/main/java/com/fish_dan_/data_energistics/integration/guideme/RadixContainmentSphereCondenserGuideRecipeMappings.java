package com.fish_dan_.data_energistics.integration.guideme;

import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.recipe.containmentsphere.RadixContainmentSphereCondenserRecipe;
import com.fish_dan_.data_energistics.registry.DERecipes;

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

public final class RadixContainmentSphereCondenserGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(DERecipes.RADIX_CONTAINMENT_SPHERE_CONDENSER_TYPE.get(),
                RadixContainmentSphereCondenserGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<RadixContainmentSphereCondenserRecipe> createRecipe(
                                                                                            RecipeHolder<RadixContainmentSphereCondenserRecipe> holder) {
        var recipe = holder.value();
        var details = LytParagraph.of(buildDetails(recipe));
        details.modifyStyle(style -> style.color(SymbolicColor.CRAFTING_RECIPE_TYPE));

        return LytStandardRecipeBox.builder()
                .title(Component.translatable("block.ae2.condenser").getString())
                .icon(AEBlocks.CONDENSER)
                .input(LytSlotGrid.row(List.of(recipe.getCatalyst()), true))
                .output(LytSlotGrid.rowFromStacks(List.of(RadixContainmentSphereItem.createChargedStack()), true))
                .addBottom(details)
                .build(holder);
    }

    private static String buildDetails(RadixContainmentSphereCondenserRecipe recipe) {
        return String.format(
                Locale.ROOT,
                "%s | %s",
                Component.translatable("item.data_energistics.radix_containment_sphere").getString(),
                Component.translatable("button.data_energistics.condenser_output.power", recipe.getRequiredPower()).getString());
    }
}
