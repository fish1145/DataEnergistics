package com.fish_dan_.data_energistics.integration.viewer.xei.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;
import com.fish_dan_.data_energistics.integration.recipe.EaeCircuitCutterRecipeCatalog;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipe;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.recipe.charger.DataIntegratedChargerRecipe;
import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/** One entry in the data integrated charger's unified recipe viewer category. */
public sealed interface DataChargePressRecipeView permits DataChargePressRecipeView.ChargerView,
                                                  DataChargePressRecipeView.InscriberView, DataChargePressRecipeView.CircuitBoardView,
                                                  DataChargePressRecipeView.PowderView, DataChargePressRecipeView.DataChargerView,
                                                  DataChargePressRecipeView.IntegratedChargerView, DataChargePressRecipeView.CustomView,
                                                  DataChargePressRecipeView.EaeCircuitCutterView {

    ResourceLocation id();

    /** Returns the machine mode required to execute this exact viewer recipe variant. */
    default MachineMode machineMode() {
        return switch (this) {
            case PowderView ignored -> MachineMode.POWDER;
            case CustomView ignored -> MachineMode.CRYSTAL_GROWTH;
            case ChargerView ignored -> MachineMode.CHARGER;
            case DataChargerView ignored -> MachineMode.CHARGER;
            case InscriberView ignored -> MachineMode.INSCRIBER;
            case IntegratedChargerView ignored -> MachineMode.INSCRIBER;
            case CircuitBoardView ignored -> MachineMode.INSCRIBER;
            case EaeCircuitCutterView ignored -> MachineMode.INSCRIBER;
        };
    }

    /**
     * Returns a stable, globally unique processing-pattern identity including this unified view's recipe family.
     */
    default ResourceLocation patternRecipeId() {
        ResourceLocation sourceId;
        String family;
        switch (this) {
            case ChargerView view -> {
                sourceId = view.holder().id();
                family = "charger";
            }
            case InscriberView view -> {
                sourceId = view.holder().id();
                family = "inscriber";
            }
            case PowderView view -> {
                sourceId = view.holder().id();
                family = "powder";
            }
            case DataChargerView view -> {
                sourceId = view.holder().id();
                family = "data_charger";
            }
            case IntegratedChargerView view -> {
                sourceId = view.holder().id();
                family = "integrated_charger";
            }
            case CircuitBoardView view -> {
                sourceId = view.holder().id();
                family = "circuit_board";
            }
            case CustomView view -> {
                sourceId = view.holder().id();
                family = "crystal_growth";
            }
            case EaeCircuitCutterView view -> {
                sourceId = view.recipe().id();
                family = "eae_circuit_cutter";
            }
        }
        return Data_Energistics.id(
                "data_charge_press/" + family + "/" + sourceId.getNamespace() + "/" + sourceId.getPath());
    }

    /**
     * Builds the viewer entries from exactly the recipe families that the data integrated charger can execute.
     * Circuit boards and powders have dedicated machine modes, so they must not be presented as normal inscriber
     * operations.
     */
    static List<DataChargePressRecipeView> fromRecipeManager(RecipeManager recipeManager) {
        List<DataChargePressRecipeView> views = new ArrayList<>();

        recipeManager.getAllRecipesFor(AERecipeTypes.CHARGER).stream()
                .map(ChargerView::new)
                .forEach(views::add);

        for (RecipeHolder<InscriberRecipe> holder : recipeManager.getAllRecipesFor(AERecipeTypes.INSCRIBER)) {
            InscriberRecipe recipe = holder.value();
            if (DataChargePressRecipeSupport.isCircuitBoardRecipe(recipe)) {
                views.add(new CircuitBoardView(holder));
            } else if (DataChargePressRecipeSupport.isPowderRecipe(recipe)) {
                views.add(new PowderView(holder));
            } else {
                views.add(new InscriberView(holder));
            }
        }

        recipeManager.getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get()).stream()
                .map(DataChargerView::new)
                .forEach(views::add);

        recipeManager.getAllRecipesFor(DERecipes.DATA_INTEGRATED_CHARGER_TYPE.get()).stream()
                .map(IntegratedChargerView::new)
                .forEach(views::add);
        recipeManager.getAllRecipesFor(DERecipes.DATA_CHARGE_PRESS_TYPE.get()).stream()
                .map(CustomView::new)
                .forEach(views::add);

        EaeCircuitCutterRecipeCatalog cutterCatalog = new EaeCircuitCutterRecipeCatalog();
        cutterCatalog.recipes(recipeManager).stream()
                .map(EaeCircuitCutterView::new)
                .forEach(views::add);

        return views;
    }

    record ChargerView(RecipeHolder<ChargerRecipe> holder) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    record InscriberView(RecipeHolder<InscriberRecipe> holder) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    /** An inscriber powder recipe that runs while the machine module slot is empty. */
    record PowderView(RecipeHolder<InscriberRecipe> holder) implements DataChargePressRecipeView {

        public PowderView {
            if (!DataChargePressRecipeSupport.isPowderRecipe(holder.value())) {
                throw new IllegalArgumentException("Data charge press views require a powder inscriber recipe");
            }
        }

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    /** A data charger recipe performed by a data charger module. */
    record DataChargerView(RecipeHolder<DataChargerRecipe> holder) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.holder.id().withSuffix("/data_integrated_charger");
        }
    }

    /** A multi-input recipe performed by the integrated charger's inscribing mode. */
    record IntegratedChargerView(RecipeHolder<DataIntegratedChargerRecipe> holder) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.holder.id().withSuffix("/data_integrated_charger");
        }
    }

    /** The fluid-backed, three-board form automatically derived from an inscriber JSON recipe. */
    record CircuitBoardView(RecipeHolder<InscriberRecipe> holder) implements DataChargePressRecipeView {

        public CircuitBoardView {
            if (!DataChargePressRecipeSupport.isCircuitBoardRecipe(holder.value())) {
                throw new IllegalArgumentException("Data charge press views require a circuit board inscriber recipe");
            }
        }

        @Override
        public ResourceLocation id() {
            return this.holder.id().withSuffix("/data_charge_press");
        }
    }

    /** A data-driven fluid-backed operation performed by the data integrated charger. */
    record CustomView(RecipeHolder<DataChargePressRecipe> holder) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.holder.id();
        }
    }

    /** An ExtendedAE circuit-cutter recipe adapted to the integrated charger's inscriber mode. */
    record EaeCircuitCutterView(EaeCircuitCutterRecipeCatalog.CutterRecipe recipe) implements DataChargePressRecipeView {

        @Override
        public ResourceLocation id() {
            return this.recipe.id().withSuffix("/eae_circuit_cutter");
        }

        public Ingredient input() {
            return this.recipe.input();
        }

        public ItemStack output() {
            return this.recipe.output().copyWithCount(
                    EaeCircuitCutterRecipeCatalog.getIntegratedResultCount(this.recipe.output().getCount()));
        }

        public int fluidAmount() {
            return EaeCircuitCutterRecipeCatalog.getIntegratedFluidAmount(this.recipe.output().getCount());
        }
    }
}
