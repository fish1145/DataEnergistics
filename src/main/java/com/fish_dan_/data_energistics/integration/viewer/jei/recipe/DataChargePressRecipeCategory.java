package com.fish_dan_.data_energistics.integration.viewer.jei.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
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
    private static final int LEFT_CROP = 25;
    private static final int TOP_CROP = 15;
    private static final int WIDTH = 131;
    private static final int HEIGHT = 64;
    // Map the AE2 top/middle/bottom inputs to the left input column: 0, 1, then 2.
    private static final int FIRST_INPUT_X = 9;
    private static final int SECOND_INPUT_X = 9;
    private static final int THIRD_INPUT_X = 9;
    private static final int FLUID_X = 46;
    private static final int FLUID_Y = 7;
    private static final int MODULE_X = 45;
    private static final int OUTPUT_X = 82;
    private static final int PROGRESS_X = 122;
    private static final int FIRST_INPUT_Y = 6;
    private static final int SECOND_INPUT_Y = 24;
    private static final int THIRD_INPUT_Y = 42;
    private static final int MODULE_Y = 42;
    private static final int PROGRESS_Y = 24;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "ae2", "textures/guis/data_integrated_charger.png");

    private final IDrawable background;
    private final IDrawableAnimated progress;

    public DataChargePressRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("recipe.data_energistics.data_charge_press"),
                guiHelper.createDrawableItemLike(DEBlocks.DATA_INTEGRATED_CHARGER.get()),
                WIDTH,
                HEIGHT);
        this.background = guiHelper.createDrawable(TEXTURE, LEFT_CROP, TOP_CROP, WIDTH, HEIGHT);
        this.progress = guiHelper.drawableBuilder(TEXTURE, 176, 0, 6, 18)
                .buildAnimated(200, IDrawableAnimated.StartDirection.BOTTOM, false);
    }

    @Override
    public void draw(DataChargePressRecipeView recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        this.background.draw(guiGraphics);
        this.progress.draw(guiGraphics, PROGRESS_X, PROGRESS_Y);
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
        addModuleCatalyst(builder, DataChargePressRecipeSupport.CHARGER_MODULES,
                "recipe.data_energistics.data_charge_press.charger_module");
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y).addItemStack(view.holder().value().getResultItem());
    }

    private static void setInscriberRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.InscriberView view) {
        InscriberRecipe recipe = view.holder().value();
        addOptionalInscriberIngredient(builder, recipe.getTopOptional(), FIRST_INPUT_X, FIRST_INPUT_Y,
                recipe.getProcessType());
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addIngredients(recipe.getMiddleInput());
        addOptionalInscriberIngredient(builder, recipe.getBottomOptional(), THIRD_INPUT_X, THIRD_INPUT_Y,
                recipe.getProcessType());
        addModuleCatalyst(builder, DataChargePressRecipeSupport.INSCRIBER_MODULES,
                "recipe.data_energistics.data_charge_press.inscriber_module");
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y).addItemStack(recipe.getResultItem());
    }

    private static void setPowderRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.PowderView view) {
        InscriberRecipe recipe = view.holder().value();
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addIngredients(recipe.getMiddleInput());
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y).addItemStack(recipe.getResultItem());
    }

    private static void setDataChargerRecipe(IRecipeLayoutBuilder builder,
                                             DataChargePressRecipeView.DataChargerView view) {
        var recipe = view.holder().value();
        builder.addInputSlot(FIRST_INPUT_X, FIRST_INPUT_Y).addIngredients(recipe.getIngredient());
        builder.addSlot(RecipeIngredientRole.CATALYST, MODULE_X, MODULE_Y)
                .addIngredients(DataChargePressRecipeSupport.DATA_CHARGER_MODULES).addRichTooltipCallback((slotView, tooltip) -> {
                    tooltip.add(Component.translatable("recipe.data_energistics.data_charge_press.data_charger_module"));
                    tooltip.add(Component.translatable("recipe.data_energistics.data_charger.cost", recipe.getDataFlow(),
                            formatPower(recipe.getPower())));
                });
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y).addItemStack(recipe.getResult());
    }

    private static void setCircuitBoardRecipe(IRecipeLayoutBuilder builder,
                                              DataChargePressRecipeView.CircuitBoardView view) {
        InscriberRecipe recipe = view.holder().value();
        builder.addInputSlot(SECOND_INPUT_X, SECOND_INPUT_Y).addItemStacks(withCount(recipe.getMiddleInput(),
                DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT));
        addFluidInput(builder, DataChargePressRecipeSupport.getFluidInput());
        addModuleCatalyst(builder, DataChargePressRecipeSupport.INSCRIBER_MODULES,
                "recipe.data_energistics.data_charge_press.inscriber_module");
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y)
                .addItemStack(DataChargePressRecipeSupport.getTripleResult(recipe));
    }

    private static void setCustomRecipe(IRecipeLayoutBuilder builder, DataChargePressRecipeView.CustomView view) {
        var recipe = view.holder().value();
        addCustomItemInputs(builder, recipe.getInputs());
        addFluidInput(builder, recipe.getFluidInput());
        addModuleCatalyst(builder, recipe.getModule());
        builder.addOutputSlot(OUTPUT_X, FIRST_INPUT_Y)
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

    private static void addModuleCatalyst(IRecipeLayoutBuilder builder, Ingredient module, String tooltipKey) {
        builder.addSlot(RecipeIngredientRole.CATALYST, MODULE_X, MODULE_Y)
                .addIngredients(module).addRichTooltipCallback((slotView, tooltip) -> tooltip.add(Component.translatable(tooltipKey)));
    }

    private static void addModuleCatalyst(IRecipeLayoutBuilder builder, Ingredient module) {
        builder.addSlot(RecipeIngredientRole.CATALYST, MODULE_X, MODULE_Y).addIngredients(module);
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

    private static String formatPower(double power) {
        return Math.rint(power) == power ? Long.toString((long) power) : Double.toString(power);
    }
}
