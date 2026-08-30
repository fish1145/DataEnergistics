package com.fish_dan_.data_energistics.integration.viewer.jei.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.jei.ui.JeiIconDrawable;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.core.definitions.AEBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

public final class CondenserOutputRecipeCategory extends AbstractRecipeCategory<CondenserOutputRecipeView> {

    public static final RecipeType<CondenserOutputRecipeView> RECIPE_TYPE = RecipeType.create(
            "ae2",
            "condenser",
            CondenserOutputRecipeView.class);

    private static final ResourceLocation CONDENSER_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/condenser.png");
    private static final ResourceLocation DATA_STATES_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID, "textures/guis/states.png");
    private static final int WIDTH = 96;
    private static final int HEIGHT = 48;
    private static final int STORAGE_X = 53;
    private static final int STORAGE_Y = 1;
    private static final int OUTPUT_X = 57;
    private static final int OUTPUT_Y = 27;
    private static final int MODE_X = 80;
    private static final int MODE_Y = 26;
    private static final int MODE_ICON_X = 81;
    private static final int MODE_ICON_Y = 27;

    private final IDrawable background;
    private final IDrawableAnimated progress;
    private final IDrawable trash;
    private final IDrawable modeBackground;
    private final IDrawable modeIcon;

    public CondenserOutputRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("gui.ae2.Condenser"),
                guiHelper.createDrawableItemLike(AEBlocks.CONDENSER),
                WIDTH,
                HEIGHT);
        this.background = guiHelper.createDrawable(CONDENSER_TEXTURE, 48, 25, WIDTH, HEIGHT);
        this.progress = guiHelper.drawableBuilder(CONDENSER_TEXTURE, 176, 0, 6, 18)
                .addPadding(0, 0, 72, 0)
                .buildAnimated(40, IDrawableAnimated.StartDirection.BOTTOM, false);
        this.trash = new JeiIconDrawable(Icon.BACKGROUND_TRASH);
        this.modeBackground = new JeiIconDrawable(Icon.TOOLBAR_BUTTON_BACKGROUND);
        this.modeIcon = guiHelper.drawableBuilder(DATA_STATES_TEXTURE, 48, 48, 16, 16)
                .setTextureSize(128, 128)
                .build();
    }

    @Override
    public void draw(CondenserOutputRecipeView recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.progress.draw(guiGraphics);
        this.trash.draw(guiGraphics, 3, 27);
        this.modeBackground.draw(guiGraphics, MODE_X, MODE_Y);
        this.modeIcon.draw(guiGraphics, MODE_ICON_X, MODE_ICON_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CondenserOutputRecipeView recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, STORAGE_X, STORAGE_Y)
                .addItemStacks(recipe.storageCandidates());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(recipe.result());
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CondenserOutputRecipeView recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= MODE_X && mouseX < MODE_X + 16 && mouseY >= MODE_Y && mouseY < MODE_Y + 16) {
            tooltip.add(Component.translatable(
                    "recipe.data_energistics.condenser_output.item_aggregation",
                    recipe.requiredPower(),
                    recipe.result().getHoverName()));
        }
    }

    @Override
    public ResourceLocation getRegistryName(CondenserOutputRecipeView recipe) {
        return recipe.id();
    }
}
