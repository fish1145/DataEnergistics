package com.fish_dan_.data_energistics.integration.viewer.emi.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

public final class CondenserOutputEmiRecipe extends BasicEmiRecipe {

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            Data_Energistics.id("condenser_output"),
            EmiStack.of(AEBlocks.CONDENSER.asItem())) {

        @Override
        public Component getName() {
            return Component.translatable("recipe.data_energistics.condenser_output");
        }
    };

    private static final int WIDTH = 132;
    private static final int HEIGHT = 52;
    private static final int STORAGE_X = 8;
    private static final int STORAGE_Y = 8;
    private static final int MATTER_X = 44;
    private static final int MATTER_Y = 10;
    private static final int ARROW_X = 70;
    private static final int ARROW_Y = 9;
    private static final int OUTPUT_X = 104;
    private static final int OUTPUT_Y = 8;

    private final CondenserOutputRecipeView recipe;

    public CondenserOutputEmiRecipe(RecipeHolder<CondenserOutputRecipe> holder) {
        this(CondenserOutputRecipeView.from(holder));
    }

    private CondenserOutputEmiRecipe(CondenserOutputRecipeView recipe) {
        super(CATEGORY, recipe.id(), WIDTH, HEIGHT);
        this.recipe = recipe;
        this.catalysts.add(EmiIngredient.of(recipe.storageCandidates().stream().map(EmiStack::of).toList()));
        this.outputs.add(EmiStack.of(recipe.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.catalysts.getFirst(), STORAGE_X, STORAGE_Y).catalyst(true);
        widgets.addText(Component.literal("+"), 34, 13, 0x404040, false);
        widgets.addTexture(AppEng.makeId("textures/guis/states.png"), MATTER_X, MATTER_Y, 14, 14, 241, 81);
        widgets.addTooltipText(
                List.of(Component.translatable("recipe.data_energistics.condenser_output.any_matter")),
                MATTER_X,
                MATTER_Y,
                14,
                14);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, ARROW_X, ARROW_Y);
        widgets.addSlot(this.outputs.getFirst(), OUTPUT_X, OUTPUT_Y).recipeContext(this);
        widgets.addText(
                Component.translatable("button.data_energistics.condenser_output.power", this.recipe.requiredPower()),
                0,
                34,
                0x404040,
                false);
    }

    @Override
    public ResourceLocation getId() {
        return this.recipe.id();
    }
}
