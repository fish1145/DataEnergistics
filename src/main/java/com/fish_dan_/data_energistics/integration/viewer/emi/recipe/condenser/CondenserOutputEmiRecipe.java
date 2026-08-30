package com.fish_dan_.data_energistics.integration.viewer.emi.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.core.AppEng;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

public final class CondenserOutputEmiRecipe extends BasicEmiRecipe {

    private static final ResourceLocation CONDENSER_TEXTURE = AppEng.makeId("textures/guis/condenser.png");
    private static final ResourceLocation STATES_TEXTURE = AppEng.makeId("textures/guis/states.png");
    private static final ResourceLocation DATA_STATES_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID, "textures/guis/states.png");
    private static final int WIDTH = 96;
    private static final int HEIGHT = 48;
    private static final int STORAGE_X = 52;
    private static final int STORAGE_Y = 0;
    private static final int OUTPUT_X = 56;
    private static final int OUTPUT_Y = 26;
    private static final int MODE_X = 80;
    private static final int MODE_Y = 28;

    private final CondenserOutputRecipeView recipe;

    public CondenserOutputEmiRecipe(EmiRecipeCategory category, RecipeHolder<CondenserOutputRecipe> holder) {
        this(category, CondenserOutputRecipeView.from(holder));
    }

    private CondenserOutputEmiRecipe(EmiRecipeCategory category, CondenserOutputRecipeView recipe) {
        super(category, recipe.id(), WIDTH, HEIGHT);
        this.recipe = recipe;
        this.catalysts.add(EmiIngredient.of(recipe.storageCandidates().stream().map(EmiStack::of).toList()));
        this.outputs.add(EmiStack.of(recipe.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(CONDENSER_TEXTURE, 0, 0, WIDTH, HEIGHT, 48, 25);
        widgets.addTexture(STATES_TEXTURE, 4, 28, 14, 14, 241, 81);
        widgets.addTexture(STATES_TEXTURE, MODE_X, MODE_Y, 16, 16, 240, 240);
        widgets.addTexture(DATA_STATES_TEXTURE, MODE_X, MODE_Y, 16, 16, 48, 48,
                16, 16, 128, 128);
        widgets.addAnimatedTexture(CONDENSER_TEXTURE, 72, 0, 6, 18, 176, 0,
                2000, false, true, false);
        widgets.addSlot(this.catalysts.getFirst(), STORAGE_X, STORAGE_Y).catalyst(true).drawBack(false);
        widgets.addSlot(this.outputs.getFirst(), OUTPUT_X, OUTPUT_Y).drawBack(false).recipeContext(this);
        widgets.addTooltipText(
                List.of(
                        Component.translatable(
                                "recipe.data_energistics.condenser_output.item_aggregation",
                                this.recipe.requiredPower(),
                                this.recipe.result().getHoverName())),
                MODE_X,
                MODE_Y,
                16,
                16);
    }

    @Override
    public ResourceLocation getId() {
        return this.recipe.id();
    }
}
