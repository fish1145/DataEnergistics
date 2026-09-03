package com.fish_dan_.data_energistics.integration.viewer.jei.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.jei.ui.JeiIconDrawable;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import java.util.Arrays;
import java.util.List;

/** Unified recipe viewer category for every operation supported by the data integrated charger. */
public final class DataChargePressRecipeCategory extends AbstractRecipeCategory<DataChargePressRecipeView> {

    public static final RecipeType<DataChargePressRecipeView> RECIPE_TYPE = RecipeType.create(
            Data_Energistics.MODID, "data_charge_press", DataChargePressRecipeView.class);
    // Keep recipe content in machine coordinates while shifting only the background five pixels forward.
    private static final int LEFT_CROP = 5;
    private static final int TOP_CROP = 15;
    private static final int BACKGROUND_WIDTH = 160;
    private static final int WIDTH = BACKGROUND_WIDTH + 2;
    private static final int HEIGHT = 64;
    // Map top/middle/bottom recipe inputs to the first vertical column of the machine's 3x3 input grid.
    private static final int FIRST_INPUT_X = 11;
    private static final int SECOND_INPUT_X = 11;
    private static final int THIRD_INPUT_X = 11;
    private static final int FLUID_X = 66;
    private static final int FLUID_Y = 7;
    private static final int OUTPUT_X = 111;
    private static final int OUTPUT_Y = 6;
    private static final int MODE_ICON_X = OUTPUT_X - 18;
    private static final int MODE_ICON_Y = OUTPUT_Y + 18;
    private static final int PROGRESS_X = 151;
    private static final int FIRST_INPUT_Y = 6;
    private static final int SECOND_INPUT_Y = 24;
    private static final int THIRD_INPUT_Y = 42;
    private static final int PROGRESS_Y = 24;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/data_integrated_charger.png");
    private static final ResourceLocation DATA_STATES_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID, "textures/guis/states.png");

    private final IDrawable background;
    private final IDrawableAnimated progress;
    private final IDrawable powderModeIcon;
    private final IDrawable crystalGrowthModeIcon;
    private final IDrawable inscriberModeIcon;
    private final IDrawable chargerModeIcon;

    public DataChargePressRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.data_charge_press"),
                guiHelper.createDrawableItemLike(DEBlocks.DATA_INTEGRATED_CHARGER.get()),
                WIDTH,
                HEIGHT);
        this.background = guiHelper.createDrawable(TEXTURE, LEFT_CROP, TOP_CROP, BACKGROUND_WIDTH, HEIGHT);
        this.progress = guiHelper.drawableBuilder(TEXTURE, 176, 0, 6, 18)
                .buildAnimated(200, IDrawableAnimated.StartDirection.BOTTOM, false);
        this.powderModeIcon = new JeiIconDrawable(Icon.PLACEMENT_ITEM);
        this.crystalGrowthModeIcon = guiHelper.drawableBuilder(DATA_STATES_TEXTURE, 80, 80, 16, 16)
                .setTextureSize(128, 128)
                .build();
        this.inscriberModeIcon = guiHelper.drawableBuilder(DATA_STATES_TEXTURE, 96, 80, 16, 16)
                .setTextureSize(128, 128)
                .build();
        this.chargerModeIcon = guiHelper.drawableBuilder(DATA_STATES_TEXTURE, 112, 80, 16, 16)
                .setTextureSize(128, 128)
                .build();
    }

    @Override
    public void draw(DataChargePressRecipeView recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.progress.draw(guiGraphics, PROGRESS_X, PROGRESS_Y);
        getModeIcon(recipe).draw(guiGraphics, MODE_ICON_X, MODE_ICON_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView view, IFocusGroup focuses) {
        if (view instanceof DataChargePressRecipeView.ChargerView chargerView) {
            setChargerRecipe(builder, chargerView);
        } else if (view instanceof DataChargePressRecipeView.InscriberView inscriberView) {
            setInscriberRecipe(builder, inscriberView);
        } else if (view instanceof DataChargePressRecipeView.PowderView powderView) {
            setPowderRecipe(builder, powderView);
        } else if (view instanceof DataChargePressRecipeView.DataChargerView dataChargerView) {
            setDataChargerRecipe(builder, dataChargerView);
        } else if (view instanceof DataChargePressRecipeView.CircuitBoardView circuitBoardView) {
            setCircuitBoardRecipe(builder, circuitBoardView);
        } else if (view instanceof DataChargePressRecipeView.CustomView customView) {
            setCustomRecipe(builder, customView);
        }
    }

    private static void setChargerRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.ChargerView view) {
        builder.addInputSlot(FIRST_INPUT_X, FIRST_INPUT_Y).addIngredients(view.holder().value().getIngredient());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(view.holder().value().getResultItem());
    }

    private static void setInscriberRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.InscriberView view) {
        InscriberRecipe recipe = view.holder().value();
        addOptionalInscriberIngredient(builder, recipe.getTopOptional(), FIRST_INPUT_X, FIRST_INPUT_Y,
                recipe.getProcessType());
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addIngredients(recipe.getMiddleInput());
        addOptionalInscriberIngredient(builder, recipe.getBottomOptional(), THIRD_INPUT_X, THIRD_INPUT_Y,
                recipe.getProcessType());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(recipe.getResultItem());
    }

    private static void setPowderRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.PowderView view) {
        InscriberRecipe recipe = view.holder().value();
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addIngredients(recipe.getMiddleInput());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(recipe.getResultItem());
    }

    private static void setDataChargerRecipe(IRecipeLayoutBuilder builder,
                                             DataChargePressRecipeView.DataChargerView view) {
        var recipe = view.holder().value();
        builder.addInputSlot(FIRST_INPUT_X, FIRST_INPUT_Y).addIngredients(recipe.getIngredient());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y).addItemStack(recipe.getResult());
    }

    private static void setCircuitBoardRecipe(IRecipeLayoutBuilder builder,
                                              DataChargePressRecipeView.CircuitBoardView view) {
        InscriberRecipe recipe = view.holder().value();
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addItemStacks(withCount(recipe.getMiddleInput(),
                DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT));
        addFluidInput(builder, DataChargePressRecipeSupport.getFluidInput());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .addItemStack(DataChargePressRecipeSupport.getTripleResult(recipe));
    }

    private static void setCustomRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.CustomView view) {
        var recipe = view.holder().value();
        addCustomItemInputs(builder, recipe.getInputs());
        addFluidInput(builder, recipe.getFluidInput());
        builder.addOutputSlot(OUTPUT_X, OUTPUT_Y)
                .addItemStack(recipe.getResult());
    }

    private static void addCustomItemInputs(IRecipeLayoutBuilder builder, List<DataChargePressIngredient> inputs) {
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            switch (index) {
                case 0 -> builder.addInputSlot(FIRST_INPUT_X, FIRST_INPUT_Y)
                        .addItemStacks(withCount(input.ingredient(), input.count()));
                case 1 -> builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y)
                        .addItemStacks(withCount(input.ingredient(), input.count()));
                case 2 -> builder.addInputSlot(THIRD_INPUT_X, THIRD_INPUT_Y)
                        .addItemStacks(withCount(input.ingredient(), input.count()));
                default -> throw new IllegalArgumentException("Data charge press recipes support at most three item inputs");
            }
        }
    }

    private static void addOptionalInscriberIngredient(IRecipeLayoutBuilder builder, Ingredient ingredient, int x, int y,
                                                       InscriberProcessType processType) {
        if (ingredient.isEmpty()) {
            return;
        }
        RecipeIngredientRole role = processType == InscriberProcessType.INSCRIBE ? RecipeIngredientRole.CATALYST : RecipeIngredientRole.INPUT;
        builder.addSlot(role, x, y).addIngredients(ingredient);
    }

    private static void addFluidInput(IRecipeLayoutBuilder builder, GenericStack fluidInput) {
        if (fluidInput.what() instanceof AEFluidKey fluidKey) {
            builder.addInputSlot(FLUID_X, FLUID_Y)
                    .setFluidRenderer(fluidInput.amount(), false, 16, 16)
                    .addFluidStack(fluidKey.getFluid(), fluidInput.amount());
        }
    }

    private static List<ItemStack> withCount(Ingredient ingredient, int count) {
        return Arrays.stream(ingredient.getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(count);
            return copy;
        }).toList();
    }

    private IDrawable getModeIcon(DataChargePressRecipeView recipe) {
        if (recipe instanceof DataChargePressRecipeView.PowderView) {
            return this.powderModeIcon;
        }
        if (recipe instanceof DataChargePressRecipeView.CustomView) {
            return this.crystalGrowthModeIcon;
        }
        if (recipe instanceof DataChargePressRecipeView.InscriberView ||
                recipe instanceof DataChargePressRecipeView.CircuitBoardView) {
            return this.inscriberModeIcon;
        }
        return this.chargerModeIcon;
    }

}
