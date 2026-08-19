package com.fish_dan_.data_energistics.integration.viewer.emi.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.stacks.AEFluidKey;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TankWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

/** Unified EMI presentation for every operation supported by the data integrated charger. */
public final class DataChargePressEmiRecipe extends BasicEmiRecipe {

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            Data_Energistics.id("data_charge_press"),
            EmiStack.of(DEBlocks.DATA_INTEGRATED_CHARGER.get())) {

        @Override
        public Component getName() {
            return Component.translatable("recipe.data_energistics.data_charge_press");
        }
    };

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

    private final DataChargePressRecipeView view;

    public DataChargePressEmiRecipe(DataChargePressRecipeView view) {
        super(CATEGORY, emiRecipeId(view), WIDTH, HEIGHT);
        this.view = view;
        if (view instanceof DataChargePressRecipeView.ChargerView chargerView) {
            this.catalysts.add(EmiIngredient.of(DataChargePressRecipeSupport.CHARGER_MODULES));
            this.inputs.add(EmiIngredient.of(chargerView.holder().value().getIngredient()));
            this.outputs.add(EmiStack.of(chargerView.holder().value().getResultItem()));
        } else if (view instanceof DataChargePressRecipeView.InscriberView inscriberView) {
            addInscriberRecipe(inscriberView.holder().value());
        } else if (view instanceof DataChargePressRecipeView.PowderView powderView) {
            addPowderRecipe(powderView.holder().value());
        } else if (view instanceof DataChargePressRecipeView.DataChargerView dataChargerView) {
            addDataChargerRecipe(dataChargerView);
        } else if (view instanceof DataChargePressRecipeView.CircuitBoardView circuitBoardView) {
            addCircuitBoardRecipe(circuitBoardView.holder().value());
        } else if (view instanceof DataChargePressRecipeView.CustomView customView) {
            addCustomRecipe(customView);
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        addMachineBackground(widgets);
        if (this.view instanceof DataChargePressRecipeView.ChargerView chargerView) {
            addChargerWidgets(widgets, chargerView);
        } else if (this.view instanceof DataChargePressRecipeView.InscriberView inscriberView) {
            addInscriberWidgets(widgets, inscriberView.holder().value());
        } else if (this.view instanceof DataChargePressRecipeView.PowderView powderView) {
            addPowderWidgets(widgets, powderView.holder().value());
        } else if (this.view instanceof DataChargePressRecipeView.DataChargerView dataChargerView) {
            addDataChargerWidgets(widgets, dataChargerView);
        } else if (this.view instanceof DataChargePressRecipeView.CircuitBoardView circuitBoardView) {
            addCircuitBoardWidgets(widgets, circuitBoardView.holder().value());
        } else if (this.view instanceof DataChargePressRecipeView.CustomView customView) {
            addCustomWidgets(widgets, customView);
        }
    }

    private void addInscriberRecipe(InscriberRecipe recipe) {
        this.catalysts.add(EmiIngredient.of(DataChargePressRecipeSupport.INSCRIBER_MODULES));
        addOptionalInscriberIngredient(recipe, recipe.getTopOptional());
        this.inputs.add(EmiIngredient.of(recipe.getMiddleInput()));
        addOptionalInscriberIngredient(recipe, recipe.getBottomOptional());
        this.outputs.add(EmiStack.of(recipe.getResultItem()));
    }

    private void addPowderRecipe(InscriberRecipe recipe) {
        this.inputs.add(EmiIngredient.of(recipe.getMiddleInput()));
        this.outputs.add(EmiStack.of(recipe.getResultItem()));
    }

    private void addDataChargerRecipe(DataChargePressRecipeView.DataChargerView view) {
        var recipe = view.holder().value();
        this.catalysts.add(EmiIngredient.of(DataChargePressRecipeSupport.DATA_CHARGER_MODULES));
        this.inputs.add(EmiIngredient.of(recipe.getIngredient()));
        this.outputs.add(EmiStack.of(recipe.getResult()));
    }

    private void addCircuitBoardRecipe(InscriberRecipe recipe) {
        this.catalysts.add(EmiIngredient.of(DataChargePressRecipeSupport.INSCRIBER_MODULES));
        if (DataChargePressRecipeSupport.hasCircuitBoardTemplate(recipe)) {
            this.catalysts.add(EmiIngredient.of(DataChargePressRecipeSupport.getTemplate(recipe)));
        }
        this.inputs.add(EmiIngredient.of(recipe.getMiddleInput(),
                DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT));
        if (DataChargePressRecipeSupport.getFluidInput().what() instanceof AEFluidKey fluidKey) {
            this.inputs.add(EmiStack.of(fluidKey.getFluid(), DataChargePressRecipeSupport.DATA_CORROSION_AMOUNT));
        }
        this.outputs.add(EmiStack.of(DataChargePressRecipeSupport.getTripleResult(recipe)));
    }

    private void addCustomRecipe(DataChargePressRecipeView.CustomView view) {
        var recipe = view.holder().value();
        this.catalysts.add(EmiIngredient.of(recipe.getModule()));
        recipe.getInputs().forEach(input -> this.inputs.add(EmiIngredient.of(input.ingredient(), input.count())));
        if (recipe.getFluidInput().what() instanceof AEFluidKey fluidKey) {
            this.inputs.add(EmiStack.of(fluidKey.getFluid(), recipe.getFluidInput().amount()));
        }
        this.outputs.add(EmiStack.of(recipe.getResult()));
    }

    private void addOptionalInscriberIngredient(InscriberRecipe recipe, Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return;
        }
        EmiIngredient emiIngredient = EmiIngredient.of(ingredient);
        if (recipe.getProcessType() == InscriberProcessType.INSCRIBE) {
            emiIngredient.getEmiStacks().forEach(stack -> stack.setRemainder(stack));
        }
        this.inputs.add(emiIngredient);
    }

    private void addChargerWidgets(WidgetHolder widgets, DataChargePressRecipeView.ChargerView view) {
        widgets.addSlot(EmiIngredient.of(view.holder().value().getIngredient()), FIRST_INPUT_X, FIRST_INPUT_Y)
                .drawBack(false);
        addModuleWidget(widgets, DataChargePressRecipeSupport.CHARGER_MODULES,
                "recipe.data_energistics.data_charge_press.charger_module");
        widgets.addSlot(EmiStack.of(view.holder().value().getResultItem()), OUTPUT_X, FIRST_INPUT_Y)
                .drawBack(false)
                .recipeContext(this);
    }

    private void addInscriberWidgets(WidgetHolder widgets, InscriberRecipe recipe) {
        addOptionalInscriberWidget(widgets, recipe, recipe.getTopOptional(), FIRST_INPUT_X, FIRST_INPUT_Y);
        widgets.addSlot(EmiIngredient.of(recipe.getMiddleInput()), SECOND_INPUT_X, SECOND_INPUT_Y).drawBack(false);
        addOptionalInscriberWidget(widgets, recipe, recipe.getBottomOptional(), THIRD_INPUT_X, THIRD_INPUT_Y);
        addModuleWidget(widgets, DataChargePressRecipeSupport.INSCRIBER_MODULES,
                "recipe.data_energistics.data_charge_press.inscriber_module");
        widgets.addSlot(EmiStack.of(recipe.getResultItem()), OUTPUT_X, FIRST_INPUT_Y)
                .drawBack(false)
                .recipeContext(this);
    }

    private void addPowderWidgets(WidgetHolder widgets, InscriberRecipe recipe) {
        widgets.addSlot(EmiIngredient.of(recipe.getMiddleInput()), SECOND_INPUT_X, SECOND_INPUT_Y).drawBack(false);
        widgets.addSlot(EmiStack.of(recipe.getResultItem()), OUTPUT_X, FIRST_INPUT_Y)
                .drawBack(false)
                .recipeContext(this);
    }

    private void addDataChargerWidgets(WidgetHolder widgets, DataChargePressRecipeView.DataChargerView view) {
        var recipe = view.holder().value();
        widgets.addSlot(EmiIngredient.of(recipe.getIngredient()), FIRST_INPUT_X, FIRST_INPUT_Y).drawBack(false);
        widgets.addSlot(EmiIngredient.of(DataChargePressRecipeSupport.DATA_CHARGER_MODULES), MODULE_X, MODULE_Y)
                .catalyst(true)
                .drawBack(false)
                .appendTooltip(Component.translatable("recipe.data_energistics.data_charge_press.data_charger_module"))
                .appendTooltip(Component.translatable("recipe.data_energistics.data_charger.cost", recipe.getDataFlow(),
                        formatPower(recipe.getPower())));
        widgets.addSlot(EmiStack.of(recipe.getResult()), OUTPUT_X, FIRST_INPUT_Y).drawBack(false).recipeContext(this);
    }

    private void addCircuitBoardWidgets(WidgetHolder widgets, InscriberRecipe recipe) {
        if (DataChargePressRecipeSupport.hasCircuitBoardTemplate(recipe)) {
            widgets.addSlot(EmiIngredient.of(DataChargePressRecipeSupport.getTemplate(recipe)), FIRST_INPUT_X,
                    FIRST_INPUT_Y)
                    .catalyst(true)
                    .drawBack(false)
                    .appendTooltip(Component.translatable("recipe.data_energistics.data_charge_press.template"));
        }
        widgets.addSlot(EmiIngredient.of(recipe.getMiddleInput(),
                DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT), SECOND_INPUT_X, SECOND_INPUT_Y)
                .drawBack(false);
        if (DataChargePressRecipeSupport.getFluidInput().what() instanceof AEFluidKey fluidKey) {
            addFluidTank(widgets, fluidKey, DataChargePressRecipeSupport.DATA_CORROSION_AMOUNT);
        }
        addModuleWidget(widgets, DataChargePressRecipeSupport.INSCRIBER_MODULES,
                "recipe.data_energistics.data_charge_press.inscriber_module");
        widgets.addSlot(EmiStack.of(DataChargePressRecipeSupport.getTripleResult(recipe)), OUTPUT_X, FIRST_INPUT_Y)
                .drawBack(false)
                .recipeContext(this);
    }

    private void addCustomWidgets(WidgetHolder widgets, DataChargePressRecipeView.CustomView view) {
        var recipe = view.holder().value();
        addCustomItemWidgets(widgets, recipe.getInputs());
        if (recipe.getFluidInput().what() instanceof AEFluidKey fluidKey) {
            addFluidTank(widgets, fluidKey, recipe.getFluidInput().amount());
        }
        addModuleWidget(widgets, recipe.getModule());
        widgets.addSlot(EmiStack.of(recipe.getResult()), OUTPUT_X, FIRST_INPUT_Y).drawBack(false).recipeContext(this);
    }

    private static void addCustomItemWidgets(WidgetHolder widgets, List<DataChargePressIngredient> inputs) {
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            switch (index) {
                case 0 -> widgets.addSlot(EmiIngredient.of(input.ingredient(), input.count()), FIRST_INPUT_X, FIRST_INPUT_Y)
                        .drawBack(false);
                case 1 -> widgets.addSlot(EmiIngredient.of(input.ingredient(), input.count()), SECOND_INPUT_X, SECOND_INPUT_Y)
                        .drawBack(false);
                case 2 -> widgets.addSlot(EmiIngredient.of(input.ingredient(), input.count()), THIRD_INPUT_X, THIRD_INPUT_Y)
                        .drawBack(false);
                default -> throw new IllegalArgumentException("Data charge press recipes support at most three item inputs");
            }
        }
    }

    private void addOptionalInscriberWidget(WidgetHolder widgets, InscriberRecipe recipe, Ingredient ingredient,
                                            int x, int y) {
        if (ingredient.isEmpty()) {
            return;
        }
        var slot = widgets.addSlot(EmiIngredient.of(ingredient), x, y).drawBack(false);
        if (recipe.getProcessType() == InscriberProcessType.INSCRIBE) {
            slot.catalyst(true);
        }
    }

    private void addModuleWidget(WidgetHolder widgets, Ingredient module, String tooltipKey) {
        widgets.addSlot(EmiIngredient.of(module), MODULE_X, MODULE_Y)
                .catalyst(true)
                .drawBack(false)
                .appendTooltip(Component.translatable(tooltipKey));
    }

    private void addModuleWidget(WidgetHolder widgets, Ingredient module) {
        widgets.addSlot(EmiIngredient.of(module), MODULE_X, MODULE_Y)
                .catalyst(true)
                .drawBack(false);
    }

    private static void addFluidTank(WidgetHolder widgets, AEFluidKey fluidKey, long amount) {
        // TankWidget reserves a one-pixel inset. Its 18x18 bounds therefore fill the GUI's 16x16 slot content.
        widgets.add(new FluidAmountTankWidget(
                EmiStack.of(fluidKey.getFluid(), amount),
                FLUID_X - 1,
                FLUID_Y - 1,
                amount)).drawBack(false);
    }

    /**
     * Non-custom entries re-present recipes owned by another recipe type. EMI requires slash-prefixed synthetic ids
     * for those views so they do not collide with the source recipe identity.
     */
    private static ResourceLocation emiRecipeId(DataChargePressRecipeView view) {
        ResourceLocation id = view.id();
        if (view instanceof DataChargePressRecipeView.CustomView) {
            return id;
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "/" + id.getPath());
    }

    private static void addMachineBackground(WidgetHolder widgets) {
        widgets.addTexture(TEXTURE, 0, 0, WIDTH, HEIGHT, LEFT_CROP, TOP_CROP);
        widgets.addAnimatedTexture(TEXTURE, PROGRESS_X, PROGRESS_Y, 6, 18, 176, 0,
                2_000, false, true, false);
    }

    private static String formatPower(double power) {
        return Math.rint(power) == power ? Long.toString((long) power) : Double.toString(power);
    }

    private static final class FluidAmountTankWidget extends TankWidget {

        private final String amountText;

        private FluidAmountTankWidget(EmiIngredient stack, int x, int y, long amount) {
            super(stack, x, y, 18, 18, Math.toIntExact(amount));
            this.amountText = GenericStackDisplayHelper.formatCompactFluidAmount(amount);
        }

        @Override
        public void drawOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            super.drawOverlay(guiGraphics, mouseX, mouseY, delta);
            var bounds = getBounds();
            GenericStackDisplayHelper.renderSmallOverlay(guiGraphics, bounds.x(), bounds.y(), this.amountText);
        }
    }
}
