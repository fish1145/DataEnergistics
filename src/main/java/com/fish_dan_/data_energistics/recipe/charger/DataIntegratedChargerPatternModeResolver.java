package com.fish_dan_.data_energistics.recipe.charger;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataIntegratedChargerBlockEntity.MachineMode;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.integration.recipe.EaeCircuitCutterRecipeCatalog;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeSupport;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Resolves the unique integrated-charger mode represented by exact server-decoded processing-pattern contents. */
public final class DataIntegratedChargerPatternModeResolver {

    private static final String STABLE_RECIPE_PATH_PREFIX = "data_charge_press/";
    private static final int MAX_CACHED_PATTERNS = 2_048;
    private static final Object2IntMap<AEItemKey> MODE_MASKS = new Object2IntOpenHashMap<>();
    private static final Object2ObjectMap<ResourceLocation, DataChargePressRecipeView> RECIPE_VIEWS = new Object2ObjectLinkedOpenHashMap<>();
    private static @Nullable RecipeManager cachedRecipeManager;
    private static long cachedReloadEpoch = Long.MIN_VALUE;

    static {
        MODE_MASKS.defaultReturnValue(-1);
    }

    private DataIntegratedChargerPatternModeResolver() {}

    /** Returns whether an ID belongs to the stable unified Data Integrated Charger recipe namespace. */
    public static boolean isStableRecipeId(@Nullable ResourceLocation recipeId) {
        return recipeId != null && recipeId.getNamespace().equals(Data_Energistics.MODID) &&
                recipeId.getPath().startsWith(STABLE_RECIPE_PATH_PREFIX);
    }

    /** Releases server-owned recipe and pattern references when the server stops. */
    public static void clearCache() {
        MODE_MASKS.clear();
        RECIPE_VIEWS.clear();
        cachedRecipeManager = null;
        cachedReloadEpoch = Long.MIN_VALUE;
    }

    /**
     * Returns a primitive bit mask containing every mode whose current server recipes exactly match the pattern.
     *
     * <p>
     * Multiple recipes within the same mode retain one bit. Matches spanning different modes retain multiple bits so
     * the caller can reject the ambiguous upload instead of guessing from viewer metadata or current machine state.
     * A stable recipe ID may select one exact current recipe only after its contents also match. Results are cached by
     * immutable pattern definition until the recipe manager or reload epoch changes.
     * </p>
     */
    public static int resolveModeMask(ServerLevel level,
                                      IPatternDetails patternDetails,
                                      @Nullable ResourceLocation recipeId) {
        if (patternDetails.getDefinition().get(AEComponents.ENCODED_PROCESSING_PATTERN) == null) {
            return 0;
        }
        RecipeManager recipeManager = level.getRecipeManager();
        long reloadEpoch = RecipeReloadEpoch.current();
        if (cachedRecipeManager != recipeManager || cachedReloadEpoch != reloadEpoch) {
            MODE_MASKS.clear();
            rebuildRecipeViews(recipeManager);
            cachedRecipeManager = recipeManager;
            cachedReloadEpoch = reloadEpoch;
        }
        AEItemKey definition = patternDetails.getDefinition();
        int cached = MODE_MASKS.getInt(definition);
        if (cached >= 0) {
            return cached;
        }
        PatternSignature signature = PatternSignature.capture(patternDetails);
        if (signature == null) {
            return 0;
        }

        int resolved = 0;
        if (recipeId != null) {
            DataChargePressRecipeView view = RECIPE_VIEWS.get(recipeId);
            if (view != null && matchesView(signature, view)) {
                resolved = mask(view.machineMode());
            }
        }
        if (resolved == 0) {
            resolved = computeModeMask(signature);
        }

        if (MODE_MASKS.size() >= MAX_CACHED_PATTERNS) {
            MODE_MASKS.clear();
        }
        MODE_MASKS.put(definition, resolved);
        return resolved;
    }

    private static void rebuildRecipeViews(RecipeManager recipeManager) {
        RECIPE_VIEWS.clear();
        for (DataChargePressRecipeView view : DataChargePressRecipeView.fromRecipeManager(recipeManager)) {
            ResourceLocation recipeId = view.patternRecipeId();
            DataChargePressRecipeView existing = RECIPE_VIEWS.putIfAbsent(recipeId, view);
            if (existing != null) {
                throw new IllegalStateException("Duplicate Data Integrated Charger stable recipe ID: " + recipeId);
            }
        }
    }

    private static int computeModeMask(PatternSignature signature) {
        int modeMask = 0;
        for (DataChargePressRecipeView view : RECIPE_VIEWS.values()) {
            if (matchesView(signature, view)) {
                modeMask |= mask(view.machineMode());
                if (ambiguous(modeMask)) {
                    return modeMask;
                }
            }
        }
        return modeMask;
    }

    private static boolean matchesView(PatternSignature signature, DataChargePressRecipeView view) {
        return switch (view) {
            case DataChargePressRecipeView.ChargerView charger -> {
                var recipe = charger.holder().value();
                yield matchesSingleItem(signature, recipe.getIngredient(), 1L, null, recipe.getResultItem());
            }
            case DataChargePressRecipeView.InscriberView inscriber -> resolveInscriberMode(signature, inscriber.holder().value()) == mask(MachineMode.INSCRIBER);
            case DataChargePressRecipeView.PowderView powder -> resolveInscriberMode(signature, powder.holder().value()) == mask(MachineMode.POWDER);
            case DataChargePressRecipeView.DataChargerView dataCharger -> {
                var recipe = dataCharger.holder().value();
                yield matchesSingleItem(signature, recipe.getIngredient(), 1L, null, recipe.getResult());
            }
            case DataChargePressRecipeView.IntegratedChargerView integrated -> {
                var recipe = integrated.holder().value();
                yield matches(signature, requirements(recipe.getInputs()), null, recipe.getResult());
            }
            case DataChargePressRecipeView.CircuitBoardView circuitBoard -> {
                InscriberRecipe recipe = circuitBoard.holder().value();
                yield matchesSingleItem(
                        signature,
                        recipe.getMiddleInput(),
                        DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT,
                        DataChargePressRecipeSupport.getFluidInput(),
                        DataChargePressRecipeSupport.getTripleResult(recipe));
            }
            case DataChargePressRecipeView.CustomView custom -> {
                var recipe = custom.holder().value();
                yield matches(signature, requirements(recipe.getInputs()), recipe.getFluidInput(), recipe.getResult());
            }
            case DataChargePressRecipeView.EaeCircuitCutterView circuitCutter -> {
                ItemStack sourceOutput = circuitCutter.recipe().output();
                ItemStack integratedOutput = sourceOutput.copyWithCount(
                        EaeCircuitCutterRecipeCatalog.getIntegratedResultCount(sourceOutput.getCount()));
                GenericStack fluid = DataChargePressRecipeSupport.getFluidInput(
                        EaeCircuitCutterRecipeCatalog.getIntegratedFluidAmount(sourceOutput.getCount()));
                yield matchesSingleItem(signature, circuitCutter.recipe().input(), 1L, fluid, integratedOutput);
            }
        };
    }

    private static int resolveInscriberMode(PatternSignature signature, InscriberRecipe recipe) {
        if (DataChargePressRecipeSupport.isCircuitBoardRecipe(recipe)) {
            return matchesSingleItem(
                    signature,
                    recipe.getMiddleInput(),
                    DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT,
                    DataChargePressRecipeSupport.getFluidInput(),
                    DataChargePressRecipeSupport.getTripleResult(recipe)) ? mask(MachineMode.INSCRIBER) : 0;
        }

        if (recipe.getProcessType() != InscriberProcessType.PRESS) {
            MachineMode mode = DataChargePressRecipeSupport.isPowderRecipe(recipe) ?
                    MachineMode.POWDER : MachineMode.INSCRIBER;
            if (matchesSingleItem(signature, recipe.getMiddleInput(), 1L, null, recipe.getResultItem())) {
                return mask(mode);
            }
            if (mode == MachineMode.POWDER) {
                return 0;
            }

            ObjectList<ItemRequirement> catalystInputs = new ObjectArrayList<>(3);
            catalystInputs.add(new ItemRequirement(recipe.getMiddleInput(), 1L));
            addIngredient(catalystInputs, recipe.getTopOptional());
            addIngredient(catalystInputs, recipe.getBottomOptional());
            return catalystInputs.size() > 1 && matches(signature, catalystInputs, null, recipe.getResultItem()) ?
                    mask(MachineMode.INSCRIBER) :
                    0;
        }

        ObjectList<ItemRequirement> requirements = new ObjectArrayList<>(3);
        requirements.add(new ItemRequirement(recipe.getMiddleInput(), 1L));
        addIngredient(requirements, recipe.getTopOptional());
        addIngredient(requirements, recipe.getBottomOptional());
        if (matches(signature, requirements, null, recipe.getResultItem())) {
            return mask(MachineMode.INSCRIBER);
        }
        return 0;
    }

    private static int mask(MachineMode mode) {
        return 1 << mode.ordinal();
    }

    private static boolean ambiguous(int modeMask) {
        return Integer.bitCount(modeMask) > 1;
    }

    private static void addIngredient(ObjectList<ItemRequirement> requirements, Ingredient ingredient) {
        if (!ingredient.isEmpty()) {
            requirements.add(new ItemRequirement(ingredient, 1L));
        }
    }

    private static ObjectList<ItemRequirement> requirements(List<DataChargePressIngredient> ingredients) {
        ObjectList<ItemRequirement> requirements = new ObjectArrayList<>(ingredients.size());
        for (DataChargePressIngredient ingredient : ingredients) {
            requirements.add(new ItemRequirement(ingredient.ingredient(), ingredient.count()));
        }
        return requirements;
    }

    private static boolean matches(PatternSignature signature,
                                   ObjectList<ItemRequirement> itemRequirements,
                                   @Nullable GenericStack fluidRequirement,
                                   ItemStack output) {
        return matchesOutput(signature.outputs(), output) &&
                matchesInputs(signature, itemRequirements, fluidRequirement);
    }

    private static boolean matchesSingleItem(PatternSignature signature,
                                             Ingredient itemRequirement,
                                             long itemAmount,
                                             @Nullable GenericStack fluidRequirement,
                                             ItemStack output) {
        if (!matchesOutput(signature.outputs(), output) || signature.itemKeys().size() != 1 ||
                signature.itemAmounts()[0] != itemAmount ||
                !itemRequirement.test(signature.itemKeys().getFirst().toStack())) {
            return false;
        }
        return matchesFluid(signature, fluidRequirement);
    }

    private static boolean matchesOutput(Object2LongMap<AEKey> outputs, ItemStack expectedOutput) {
        AEItemKey expectedKey = AEItemKey.of(expectedOutput);
        return expectedKey != null && outputs.size() == 1 &&
                outputs.getLong(expectedKey) == expectedOutput.getCount();
    }

    private static boolean matchesInputs(PatternSignature signature,
                                         ObjectList<ItemRequirement> itemRequirements,
                                         @Nullable GenericStack fluidRequirement) {
        return matchesFluid(signature, fluidRequirement) && consumeRequirements(
                itemRequirements,
                0,
                signature.itemKeys(),
                signature.itemAmounts().clone());
    }

    private static boolean matchesFluid(PatternSignature signature,
                                        @Nullable GenericStack fluidRequirement) {
        if (fluidRequirement == null) {
            return signature.auxiliaryInput() == null;
        }
        return fluidRequirement.what().equals(signature.auxiliaryInput()) &&
                fluidRequirement.amount() == signature.auxiliaryAmount();
    }

    private static boolean consumeRequirements(ObjectList<ItemRequirement> requirements,
                                               int requirementIndex,
                                               ObjectList<AEItemKey> itemKeys,
                                               long[] remainingAmounts) {
        if (requirementIndex == requirements.size()) {
            for (long remaining : remainingAmounts) {
                if (remaining != 0L) {
                    return false;
                }
            }
            return true;
        }

        ItemRequirement requirement = requirements.get(requirementIndex);
        for (int itemIndex = 0; itemIndex < itemKeys.size(); itemIndex++) {
            if (remainingAmounts[itemIndex] < requirement.amount() ||
                    !requirement.ingredient().test(itemKeys.get(itemIndex).toStack())) {
                continue;
            }
            remainingAmounts[itemIndex] -= requirement.amount();
            if (consumeRequirements(requirements, requirementIndex + 1, itemKeys, remainingAmounts)) {
                return true;
            }
            remainingAmounts[itemIndex] += requirement.amount();
        }
        return false;
    }

    private record ItemRequirement(Ingredient ingredient, long amount) {}

    private record PatternSignature(ObjectList<AEItemKey> itemKeys,
                                    long[] itemAmounts,
                                    @Nullable AEKey auxiliaryInput,
                                    long auxiliaryAmount,
                                    Object2LongMap<AEKey> outputs) {

        private static @Nullable PatternSignature capture(IPatternDetails patternDetails) {
            IPatternDetails.IInput[] patternInputs = patternDetails.getInputs();
            Object2LongMap<AEKey> inputs = new Object2LongOpenHashMap<>(patternInputs.length);
            for (IPatternDetails.IInput input : patternInputs) {
                GenericStack[] alternatives = input.getPossibleInputs();
                if (alternatives.length != 1 || alternatives[0] == null ||
                        alternatives[0].amount() <= 0L || input.getMultiplier() <= 0L) {
                    return null;
                }
                GenericStack alternative = alternatives[0];
                long amount;
                try {
                    amount = Math.multiplyExact(alternative.amount(), input.getMultiplier());
                    inputs.put(
                            alternative.what(),
                            Math.addExact(inputs.getLong(alternative.what()), amount));
                } catch (ArithmeticException exception) {
                    return null;
                }
            }

            ObjectList<AEItemKey> itemKeys = new ObjectArrayList<>(inputs.size());
            LongList itemAmounts = new LongArrayList(inputs.size());
            @Nullable
            AEKey auxiliaryInput = null;
            long auxiliaryAmount = 0L;
            for (Object2LongMap.Entry<AEKey> entry : inputs.object2LongEntrySet()) {
                if (entry.getKey() instanceof AEItemKey itemKey) {
                    itemKeys.add(itemKey);
                    itemAmounts.add(entry.getLongValue());
                } else if (auxiliaryInput == null) {
                    auxiliaryInput = entry.getKey();
                    auxiliaryAmount = entry.getLongValue();
                } else {
                    return null;
                }
            }

            List<GenericStack> patternOutputs = patternDetails.getOutputs();
            Object2LongMap<AEKey> outputs = new Object2LongOpenHashMap<>(patternOutputs.size());
            for (GenericStack output : patternOutputs) {
                if (output == null || output.amount() <= 0L) {
                    return null;
                }
                try {
                    outputs.put(
                            output.what(),
                            Math.addExact(outputs.getLong(output.what()), output.amount()));
                } catch (ArithmeticException exception) {
                    return null;
                }
            }
            return inputs.isEmpty() || outputs.isEmpty() ? null : new PatternSignature(
                    ObjectLists.unmodifiable(itemKeys),
                    itemAmounts.toLongArray(),
                    auxiliaryInput,
                    auxiliaryAmount,
                    outputs);
        }
    }
}
