package com.fish_dan_.data_energistics.integration.viewer.jei.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.jei.ui.JeiIconDrawable;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.core.definitions.AEBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

public final class CondenserOutputRecipeCategory extends AbstractRecipeCategory<CondenserOutputRecipeView> {

    public static final RecipeType<CondenserOutputRecipeView> RECIPE_TYPE = RecipeType.create(
            Data_Energistics.MODID,
            "condenser_output",
            CondenserOutputRecipeView.class);

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
    private static final int TEXT_Y = 34;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable background;
    private final IDrawableStatic slot;
    private final IDrawableStatic arrow;
    private final IDrawable trash;

    public CondenserOutputRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.condenser_output"),
                guiHelper.createDrawableItemLike(AEBlocks.CONDENSER),
                WIDTH,
                HEIGHT);
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.trash = new JeiIconDrawable(Icon.BACKGROUND_TRASH);
    }

    @Override
    public void draw(CondenserOutputRecipeView recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.slot.draw(guiGraphics, STORAGE_X - 1, STORAGE_Y - 1);
        this.slot.draw(guiGraphics, OUTPUT_X - 1, OUTPUT_Y - 1);
        this.trash.draw(guiGraphics, MATTER_X, MATTER_Y);
        this.arrow.draw(guiGraphics, ARROW_X, ARROW_Y);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, Component.literal("+"), 34, 13, TEXT_COLOR, false);
        Component power = Component.translatable(
                "button.data_energistics.condenser_output.power",
                recipe.requiredPower());
        guiGraphics.drawString(font, power, WIDTH / 2 - font.width(power) / 2, TEXT_Y, TEXT_COLOR, false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CondenserOutputRecipeView recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, STORAGE_X, STORAGE_Y)
                .addItemStacks(recipe.storageCandidates())
                .addRichTooltipCallback((slotView, tooltip) -> tooltip.add(Component.translatable(
                        "button.data_energistics.condenser_output.storage",
                        recipe.requiredPower())));
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(recipe.result());
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, CondenserOutputRecipeView recipe,
                           IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX >= MATTER_X && mouseX < MATTER_X + 14 && mouseY >= MATTER_Y && mouseY < MATTER_Y + 14) {
            tooltip.add(Component.translatable("recipe.data_energistics.condenser_output.any_matter"));
        }
    }

    @Override
    public ResourceLocation getRegistryName(CondenserOutputRecipeView recipe) {
        return recipe.id();
    }
}
