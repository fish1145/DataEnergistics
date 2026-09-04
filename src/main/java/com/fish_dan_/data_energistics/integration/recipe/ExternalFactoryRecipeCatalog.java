package com.fish_dan_.data_energistics.integration.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.recipe.reassembler.DataReassemblerItemOutput;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalizes supported third-party machine recipes for the asynchronous factory without loading their classes.
 *
 * <p>
 * The external recipe codec is used only after its recipe type has been registered. This keeps the factory
 * loadable when either optional mod is absent while still respecting recipes added by data packs.
 * </p>
 */
public final class ExternalFactoryRecipeCatalog {

    private static final int EXTERNAL_PROCESS_TICKS = DataRipperReassemblerRecipe.PROCESS_TICKS;
    private long reloadEpoch = Long.MIN_VALUE;
    @Nullable
    private RecipeManager recipeManager;
    private List<RecipeHolder<DataRipperReassemblerRecipe>> recipes = List.of();
    private Map<ResourceLocation, RecipeHolder<DataRipperReassemblerRecipe>> recipesById = Map.of();

    public Iterable<RecipeHolder<DataRipperReassemblerRecipe>> recipes(Level level) {
        refresh(level);
        return this.recipes;
    }

    public @Nullable RecipeHolder<DataRipperReassemblerRecipe> recipeById(Level level, ResourceLocation recipeId) {
        refresh(level);
        return this.recipesById.get(recipeId);
    }

    private void refresh(Level level) {
        RecipeManager currentRecipeManager = level.getRecipeManager();
        long currentReloadEpoch = RecipeReloadEpoch.current();
        if (this.recipeManager == currentRecipeManager && this.reloadEpoch == currentReloadEpoch) {
            return;
        }

        List<RecipeHolder<DataRipperReassemblerRecipe>> rebuiltRecipes = new ArrayList<>();
        appendRecipes(currentRecipeManager, ExternalRecipeSource.EAE_CRYSTAL_ASSEMBLER, rebuiltRecipes);
        appendRecipes(currentRecipeManager, ExternalRecipeSource.AAE_REACTION_CHAMBER, rebuiltRecipes);
        Map<ResourceLocation, RecipeHolder<DataRipperReassemblerRecipe>> rebuiltById = new LinkedHashMap<>();
        for (RecipeHolder<DataRipperReassemblerRecipe> recipe : rebuiltRecipes) {
            rebuiltById.put(recipe.id(), recipe);
        }
        this.recipeManager = currentRecipeManager;
        this.reloadEpoch = currentReloadEpoch;
        this.recipes = List.copyOf(rebuiltRecipes);
        this.recipesById = Map.copyOf(rebuiltById);
    }

    private static void appendRecipes(RecipeManager recipeManager, ExternalRecipeSource source,
                                      List<RecipeHolder<DataRipperReassemblerRecipe>> destination) {
        RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.get(source.recipeTypeId());
        if (recipeType == null) {
            return;
        }

        for (RecipeHolder<?> holder : getRecipes(recipeManager, recipeType)) {
            JsonObject encodedRecipe = encodeRecipe(holder.value(), holder.id());
            if (encodedRecipe == null) {
                continue;
            }
            destination.addAll(source.adapt(holder.id(), encodedRecipe));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<RecipeHolder<?>> getRecipes(RecipeManager recipeManager, RecipeType<?> recipeType) {
        return (List) recipeManager.getAllRecipesFor((RecipeType) recipeType);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @Nullable JsonObject encodeRecipe(Recipe<?> recipe, ResourceLocation recipeId) {
        RecipeSerializer serializer = recipe.getSerializer();
        MapCodec codec = serializer.codec();
        DataResult<JsonElement> encoded = (DataResult<JsonElement>) codec.codec().encodeStart(JsonOps.INSTANCE, recipe);
        return encoded.resultOrPartial(error -> Data_Energistics.LOGGER.warn(
                "Skipped external factory recipe {} because its codec could not encode it: {}", recipeId, error))
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .orElse(null);
    }

    private static @Nullable List<DataRipperReassemblerIngredient> parseItemInputs(JsonObject serialized,
                                                                                   ResourceLocation recipeId) {
        JsonElement rawInputs = serialized.get("input_items");
        if (!(rawInputs instanceof JsonArray inputArray)) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it has no input_items array", recipeId);
            return null;
        }

        List<DataRipperReassemblerIngredient> inputs = new ArrayList<>(inputArray.size());
        for (JsonElement rawInput : inputArray) {
            if (!(rawInput instanceof JsonObject input)) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because an item input is not an object", recipeId);
                return null;
            }
            JsonElement rawIngredient = input.get("ingredient");
            if (rawIngredient == null) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because an item input has no ingredient", recipeId);
                return null;
            }
            Ingredient ingredient = decode(Ingredient.CODEC_NONEMPTY, rawIngredient, recipeId, "item ingredient");
            if (ingredient == null) {
                return null;
            }
            int amount;
            try {
                amount = input.get("amount").getAsInt();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because an item input has no positive amount", recipeId);
                return null;
            }
            if (amount <= 0) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because an item input has non-positive amount {}", recipeId, amount);
                return null;
            }
            inputs.add(new DataRipperReassemblerIngredient(ingredient, amount));
        }
        return inputs;
    }

    private static @Nullable List<List<GenericStack>> parseFluidVariants(JsonObject serialized,
                                                                         ResourceLocation recipeId) {
        JsonElement rawFluidInput = serialized.get("input_fluid");
        if (rawFluidInput == null || rawFluidInput.isJsonNull()) {
            return List.of(List.of());
        }
        if (!(rawFluidInput instanceof JsonObject fluidInput)) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because input_fluid is not an object", recipeId);
            return null;
        }
        JsonElement rawIngredient = fluidInput.get("ingredient");
        if (rawIngredient == null) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because input_fluid has no ingredient", recipeId);
            return null;
        }
        FluidIngredient ingredient = decode(FluidIngredient.CODEC_NON_EMPTY, rawIngredient, recipeId, "fluid ingredient");
        if (ingredient == null) {
            return null;
        }
        int amount;
        try {
            amount = fluidInput.get("amount").getAsInt();
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because input_fluid has no positive amount", recipeId);
            return null;
        }
        if (amount <= 0) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because input_fluid has non-positive amount {}", recipeId, amount);
            return null;
        }

        List<List<GenericStack>> variants = new ArrayList<>();
        for (FluidStack candidate : ingredient.getStacks()) {
            if (!candidate.isEmpty()) {
                variants.add(List.of(new GenericStack(AEFluidKey.of(candidate), amount)));
            }
        }
        if (variants.isEmpty()) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because its fluid ingredient has no registered fluids", recipeId);
            return null;
        }
        return variants;
    }

    private static @Nullable OutputDefinition parseCrystalAssemblerOutput(JsonObject serialized,
                                                                          ResourceLocation recipeId) {
        JsonElement rawOutput = serialized.get("output");
        if (rawOutput == null) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it has no output", recipeId);
            return null;
        }
        ItemStack output = decode(ItemStack.CODEC, rawOutput, recipeId, "item output");
        if (output == null || output.isEmpty()) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it has an empty item output", recipeId);
            return null;
        }
        return new OutputDefinition(List.of(new DataReassemblerItemOutput(output, null)), List.of(), null);
    }

    private static @Nullable OutputDefinition parseReactionChamberOutput(JsonObject serialized,
                                                                         ResourceLocation recipeId) {
        JsonElement rawOutput = serialized.get("output");
        if (rawOutput == null) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it has no output", recipeId);
            return null;
        }
        GenericStack output = decode(GenericStack.CODEC, rawOutput, recipeId, "generic output");
        if (output == null || output.what() == null || output.amount() <= 0L) {
            Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it has an invalid generic output", recipeId);
            return null;
        }
        AEKey key = output.what();
        if (key instanceof AEItemKey itemKey) {
            if (output.amount() > Integer.MAX_VALUE) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because its item output exceeds integer capacity", recipeId);
                return null;
            }
            ItemStack itemOutput = itemKey.toStack((int) output.amount());
            return new OutputDefinition(List.of(new DataReassemblerItemOutput(itemOutput, null)), List.of(), null);
        }
        if (key instanceof AEFluidKey) {
            return new OutputDefinition(List.of(), List.of(output), null);
        }
        return new OutputDefinition(List.of(), List.of(), output);
    }

    private static List<RecipeHolder<DataRipperReassemblerRecipe>> createVariants(ResourceLocation sourceRecipeId,
                                                                                  ExternalRecipeSource source,
                                                                                  List<DataRipperReassemblerIngredient> itemInputs,
                                                                                  List<List<GenericStack>> fluidVariants,
                                                                                  OutputDefinition outputs) {
        List<RecipeHolder<DataRipperReassemblerRecipe>> variants = new ArrayList<>(fluidVariants.size());
        for (int variantIndex = 0; variantIndex < fluidVariants.size(); variantIndex++) {
            try {
                DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                        itemInputs,
                        fluidVariants.get(variantIndex),
                        outputs.itemOutputs(),
                        outputs.fluidOutputs(),
                        EXTERNAL_PROCESS_TICKS,
                        null,
                        outputs.keyOutput());
                variants.add(new RecipeHolder<>(source.adaptedRecipeId(sourceRecipeId, variantIndex), recipe));
            } catch (IllegalArgumentException exception) {
                Data_Energistics.LOGGER.warn("Skipped external factory recipe {} because it cannot be represented by the factory: {}",
                        sourceRecipeId, exception.getMessage());
            }
        }
        return variants;
    }

    private static <T> @Nullable T decode(Codec<T> codec, JsonElement value, ResourceLocation recipeId, String field) {
        return codec.parse(JsonOps.INSTANCE, value)
                .resultOrPartial(error -> Data_Energistics.LOGGER.warn(
                        "Skipped external factory recipe {} because its {} is invalid: {}", recipeId, field, error))
                .orElse(null);
    }

    private enum ExternalRecipeSource {

        EAE_CRYSTAL_ASSEMBLER(ResourceLocation.fromNamespaceAndPath("extendedae", "crystal_assembler"), "eae_crystal") {

            @Override
            List<RecipeHolder<DataRipperReassemblerRecipe>> adapt(ResourceLocation recipeId, JsonObject serialized) {
                List<DataRipperReassemblerIngredient> itemInputs = parseItemInputs(serialized, recipeId);
                List<List<GenericStack>> fluidVariants = parseFluidVariants(serialized, recipeId);
                OutputDefinition output = parseCrystalAssemblerOutput(serialized, recipeId);
                return itemInputs == null || fluidVariants == null || output == null ? List.of() :
                        createVariants(recipeId, this, itemInputs, fluidVariants, output);
            }
        },
        AAE_REACTION_CHAMBER(ResourceLocation.fromNamespaceAndPath("advanced_ae", "reaction"), "aae_reaction") {

            @Override
            List<RecipeHolder<DataRipperReassemblerRecipe>> adapt(ResourceLocation recipeId, JsonObject serialized) {
                List<DataRipperReassemblerIngredient> itemInputs = parseItemInputs(serialized, recipeId);
                List<List<GenericStack>> fluidVariants = parseFluidVariants(serialized, recipeId);
                OutputDefinition output = parseReactionChamberOutput(serialized, recipeId);
                return itemInputs == null || fluidVariants == null || output == null ? List.of() :
                        createVariants(recipeId, this, itemInputs, fluidVariants, output);
            }
        };

        private final ResourceLocation recipeTypeId;
        private final String idSegment;

        ExternalRecipeSource(ResourceLocation recipeTypeId, String idSegment) {
            this.recipeTypeId = recipeTypeId;
            this.idSegment = idSegment;
        }

        ResourceLocation recipeTypeId() {
            return this.recipeTypeId;
        }

        ResourceLocation adaptedRecipeId(ResourceLocation sourceRecipeId, int variantIndex) {
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "asynchronous_factory/" + this.idSegment + "/" +
                    sourceRecipeId.getNamespace() + "/" + sourceRecipeId.getPath() + "/" + variantIndex);
        }

        abstract List<RecipeHolder<DataRipperReassemblerRecipe>> adapt(ResourceLocation recipeId, JsonObject serialized);
    }

    private record OutputDefinition(List<DataReassemblerItemOutput> itemOutputs, List<GenericStack> fluidOutputs,
                                    @Nullable GenericStack keyOutput) {}
}
