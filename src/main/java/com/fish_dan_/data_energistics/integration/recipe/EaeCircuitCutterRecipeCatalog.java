package com.fish_dan_.data_energistics.integration.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Caches block inputs accepted by ExtendedAE's circuit cutter without loading ExtendedAE recipe classes.
 *
 * <p>
 * The circuit cutter's recipe implementation uses its own execution context instead of the usual recipe-matching
 * method. Encoding its registered recipe codec lets this catalog honor data-pack changes while keeping the integrated
 * charger loadable when ExtendedAE is absent.
 * </p>
 */
public final class EaeCircuitCutterRecipeCatalog {

    private static final ResourceLocation RECIPE_TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            "extendedae", "circuit_cutter");
    private static final Set<ResourceLocation> EXCLUDED_RECIPE_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("extendedae", "fishbig_destroy"),
            ResourceLocation.fromNamespaceAndPath("extendedae", "mddyue_destroy"));
    private static final Set<ResourceLocation> EXCLUDED_INPUT_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("extendedae", "fishbig"),
            ResourceLocation.fromNamespaceAndPath("extendedae", "mddyue"));
    private static final int CRYSTAL_CUTTER_OUTPUT_COUNT = 4;
    private static final int CRYSTAL_CUTTER_RESULT_COUNT = 5;
    private static final int CRYSTAL_CUTTER_FLUID_AMOUNT = 750;
    private static final int BLOCK_CUTTER_RESULT_COUNT = 14;
    private static final int BLOCK_CUTTER_FLUID_AMOUNT = 1_050;

    private long reloadEpoch = Long.MIN_VALUE;
    private @Nullable RecipeManager recipeManager;
    private List<CutterRecipe> recipes = List.of();

    /**
     * Returns the output of the non-excluded EAE circuit-cutter recipe accepting the supplied block item.
     *
     * <p>
     * The catalog is refreshed only after the recipe manager or recipe-reload epoch changes.
     * </p>
     */
    public @Nullable ItemStack findOutput(Level level, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem) ||
                EXCLUDED_INPUT_IDS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return null;
        }

        refresh(level.getRecipeManager());
        for (CutterRecipe recipe : this.recipes) {
            if (recipe.input().test(stack)) {
                return recipe.output().copy();
            }
        }
        return null;
    }

    /** Returns the cached, non-excluded cutter recipes for recipe-viewer integration. */
    public List<CutterRecipe> recipes(RecipeManager recipeManager) {
        refresh(recipeManager);
        return this.recipes;
    }

    /** Releases recipe-manager and decoded-recipe references when the owning cache leaves server scope. */
    public void clearCache() {
        this.reloadEpoch = Long.MIN_VALUE;
        this.recipeManager = null;
        this.recipes = List.of();
    }

    /** Returns the integrated charger's output count for the cutter recipe's original output count. */
    public static int getIntegratedResultCount(int originalOutputCount) {
        return originalOutputCount == CRYSTAL_CUTTER_OUTPUT_COUNT ?
                CRYSTAL_CUTTER_RESULT_COUNT : BLOCK_CUTTER_RESULT_COUNT;
    }

    /** Returns the data-corrosion cost for the cutter recipe's original output count. */
    public static int getIntegratedFluidAmount(int originalOutputCount) {
        return originalOutputCount == CRYSTAL_CUTTER_OUTPUT_COUNT ?
                CRYSTAL_CUTTER_FLUID_AMOUNT : BLOCK_CUTTER_FLUID_AMOUNT;
    }

    private void refresh(RecipeManager currentRecipeManager) {
        long currentReloadEpoch = RecipeReloadEpoch.current();
        if (this.recipeManager == currentRecipeManager && this.reloadEpoch == currentReloadEpoch) {
            return;
        }

        List<CutterRecipe> rebuiltRecipes = new ArrayList<>();
        RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.get(RECIPE_TYPE_ID);
        if (recipeType != null) {
            for (RecipeHolder<?> holder : getRecipes(currentRecipeManager, recipeType)) {
                if (EXCLUDED_RECIPE_IDS.contains(holder.id())) {
                    continue;
                }

                CutterRecipe recipe = parseRecipe(holder.value(), holder.id());
                if (recipe != null && hasBlockInput(recipe.input())) {
                    rebuiltRecipes.add(recipe);
                }
            }
        }

        this.recipeManager = currentRecipeManager;
        this.reloadEpoch = currentReloadEpoch;
        this.recipes = List.copyOf(rebuiltRecipes);
    }

    private static boolean hasBlockInput(Ingredient ingredient) {
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.getItem() instanceof BlockItem) {
                return true;
            }
        }
        return false;
    }

    // The recipe manager exposes an erased RecipeType<?> for optional third-party recipes.
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<RecipeHolder<?>> getRecipes(RecipeManager recipeManager, RecipeType<?> recipeType) {
        return (List) recipeManager.getAllRecipesFor((RecipeType) recipeType);
    }

    private static @Nullable CutterRecipe parseRecipe(Recipe<?> recipe, ResourceLocation recipeId) {
        JsonObject serialized = encodeRecipe(recipe, recipeId);
        if (serialized == null) {
            return null;
        }

        JsonElement rawInput = serialized.get("input");
        if (!(rawInput instanceof JsonObject inputObject)) {
            Data_Energistics.LOGGER.warn("Skipped EAE circuit-cutter recipe {} because it has no input object", recipeId);
            return null;
        }
        JsonElement rawIngredient = inputObject.get("ingredient");
        if (rawIngredient == null) {
            Data_Energistics.LOGGER.warn("Skipped EAE circuit-cutter recipe {} because its input has no ingredient", recipeId);
            return null;
        }

        Ingredient ingredient = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, rawIngredient)
                .resultOrPartial(error -> Data_Energistics.LOGGER.warn(
                        "Skipped EAE circuit-cutter recipe {} because its input ingredient is invalid: {}", recipeId, error))
                .orElse(null);
        if (ingredient == null) {
            return null;
        }

        JsonElement rawOutput = serialized.get("output");
        if (rawOutput == null) {
            Data_Energistics.LOGGER.warn("Skipped EAE circuit-cutter recipe {} because it has no output", recipeId);
            return null;
        }
        ItemStack output = ItemStack.CODEC.parse(JsonOps.INSTANCE, rawOutput)
                .resultOrPartial(error -> Data_Energistics.LOGGER.warn(
                        "Skipped EAE circuit-cutter recipe {} because its output is invalid: {}", recipeId, error))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) {
            Data_Energistics.LOGGER.warn("Skipped EAE circuit-cutter recipe {} because its output is empty", recipeId);
            return null;
        }
        return new CutterRecipe(recipeId, ingredient, output.copy());
    }

    // Optional recipe serializers expose their codec through erased generic types.
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @Nullable JsonObject encodeRecipe(Recipe<?> recipe, ResourceLocation recipeId) {
        RecipeSerializer serializer = recipe.getSerializer();
        MapCodec codec = serializer.codec();
        DataResult<JsonElement> encoded = (DataResult<JsonElement>) codec.codec().encodeStart(JsonOps.INSTANCE, recipe);
        return encoded.resultOrPartial(error -> Data_Energistics.LOGGER.warn(
                "Skipped EAE circuit-cutter recipe {} because its codec could not encode it: {}", recipeId, error))
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .orElse(null);
    }

    /** One EAE cutter input/output pair together with its source recipe id. */
    public record CutterRecipe(ResourceLocation id, Ingredient input, ItemStack output) {

        public CutterRecipe {
            output = output.copy();
        }

        @Override
        public ItemStack output() {
            return this.output.copy();
        }
    }
}
