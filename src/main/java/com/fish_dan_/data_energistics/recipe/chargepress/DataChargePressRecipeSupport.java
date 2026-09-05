package com.fish_dan_.data_energistics.recipe.chargepress;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEFluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

/** Derives three-board data charge press operations directly from AE2 inscriber recipes. */
public final class DataChargePressRecipeSupport {

    public static final int DATA_CORROSION_AMOUNT = 250;
    public static final int CIRCUIT_BOARD_MATERIAL_COUNT = 2;
    public static final Ingredient CHARGER_MODULES = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("integrated_charger/charger_modules")));
    public static final Ingredient INSCRIBER_MODULES = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("integrated_charger/inscriber_modules")));
    public static final Ingredient DATA_CHARGER_MODULES = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("integrated_charger/data_charger_modules")));
    public static final Ingredient CRYSTAL_GROWTH_MODULES = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("integrated_charger/crystal_growth_modules")));
    private static final Ingredient CIRCUIT_BOARD_RESULTS = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("charge_press/circuit_boards")));
    private static final Ingredient POWDER_RESULTS = Ingredient.of(TagKey.create(
            Registries.ITEM, Data_Energistics.id("integrated_charger/powder_results")));

    private DataChargePressRecipeSupport() {}

    /**
     * Circuit board recipes use two consumed copies of their middle ingredient. Most also retain one reusable press,
     * while the few template-less circuit boards intentionally have no press. The dedicated result tag keeps powders
     * and mold-copying recipes out of the three-board process.
     */
    public static boolean isCircuitBoardRecipe(InscriberRecipe recipe) {
        if (recipe.getMiddleInput().isEmpty() || recipe.getResultItem().isEmpty() ||
                !CIRCUIT_BOARD_RESULTS.test(recipe.getResultItem())) {
            return false;
        }

        if (hasCircuitBoardTemplate(recipe)) {
            return recipe.getProcessType() == InscriberProcessType.INSCRIBE &&
                    !getTemplateUnchecked(recipe).test(recipe.getResultItem());
        }
        return (recipe.getProcessType() == InscriberProcessType.PRESS ||
                recipe.getProcessType() == InscriberProcessType.INSCRIBE) && recipe.getTopOptional().isEmpty() &&
                recipe.getBottomOptional().isEmpty();
    }

    public static boolean hasCircuitBoardTemplate(InscriberRecipe recipe) {
        return recipe.getTopOptional().isEmpty() != recipe.getBottomOptional().isEmpty();
    }

    /**
     * Powder recipes use the inscriber's center input alone. The integrated machine exposes them only in powder mode.
     */
    public static boolean isPowderRecipe(InscriberRecipe recipe) {
        return recipe.getProcessType() == InscriberProcessType.INSCRIBE && !recipe.getMiddleInput().isEmpty() &&
                recipe.getTopOptional().isEmpty() && recipe.getBottomOptional().isEmpty() &&
                !recipe.getResultItem().isEmpty() && POWDER_RESULTS.test(recipe.getResultItem());
    }

    public static Ingredient getTemplate(InscriberRecipe recipe) {
        if (!isCircuitBoardRecipe(recipe) || !hasCircuitBoardTemplate(recipe)) {
            throw new IllegalArgumentException("Expected an inscribe recipe with exactly one reusable press");
        }
        return getTemplateUnchecked(recipe);
    }

    public static GenericStack getFluidInput() {
        return getFluidInput(DATA_CORROSION_AMOUNT);
    }

    public static GenericStack getFluidInput(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Data corrosion fluid amount must be positive: " + amount);
        }
        return new GenericStack(AEFluidKey.of(DEFluids.DATA_CORROSION_LIQUID.get()), amount);
    }

    public static boolean matchesFluid(FluidStack fluid) {
        return matchesFluid(fluid, DATA_CORROSION_AMOUNT);
    }

    public static boolean matchesFluid(FluidStack fluid, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Data corrosion fluid amount must be positive: " + amount);
        }
        return !fluid.isEmpty() && fluid.getAmount() >= amount &&
                AEFluidKey.of(DEFluids.DATA_CORROSION_LIQUID.get()).equals(AEFluidKey.of(fluid));
    }

    public static ItemStack getTripleResult(InscriberRecipe recipe) {
        return recipe.getResultItem().copyWithCount(3);
    }

    private static Ingredient getTemplateUnchecked(InscriberRecipe recipe) {
        return recipe.getTopOptional().isEmpty() ? recipe.getBottomOptional() : recipe.getTopOptional();
    }
}
