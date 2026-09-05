package com.fish_dan_.data_energistics.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.api.implementations.items.IStorageComponent;
import appeng.blockentity.misc.CondenserBlockEntity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A data-driven output mode for the AE2 matter condenser.
 *
 * <p>
 * The recipe is the authoritative source for the exact output stack, required matter and allowed storage
 * components. Storage items are catalysts: they are never consumed, but their capacity must be large enough for the
 * configured matter requirement.
 * </p>
 */
public final class CondenserOutputRecipe implements Recipe<RecipeInput> {

    public static final TagKey<Item> DEFAULT_STORAGE_COMPONENTS = TagKey.create(
            Registries.ITEM,
            Data_Energistics.id("condenser/storage_components"));

    private static final Ingredient DEFAULT_STORAGE = Ingredient.of(DEFAULT_STORAGE_COMPONENTS);
    private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.flatXmap(
            stack -> stack.isEmpty() ? DataResult.error(() -> "Matter condenser output must not be empty") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<Long> POWER_CODEC = Codec.LONG.flatXmap(
            power -> power <= 0L ? DataResult.error(() -> "Matter condenser power must be greater than 0") : DataResult.success(power),
            DataResult::success);

    public static final MapCodec<CondenserOutputRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("storage", DEFAULT_STORAGE)
                    .forGetter(CondenserOutputRecipe::getStorageIngredient),
            RESULT_CODEC.fieldOf("result").forGetter(CondenserOutputRecipe::getResult),
            POWER_CODEC.fieldOf("power").forGetter(CondenserOutputRecipe::getRequiredPower))
            .apply(instance, CondenserOutputRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CondenserOutputRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            CondenserOutputRecipe::getStorageIngredient,
            ItemStack.STREAM_CODEC,
            CondenserOutputRecipe::getResult,
            ByteBufCodecs.VAR_LONG,
            CondenserOutputRecipe::getRequiredPower,
            CondenserOutputRecipe::new);

    private final Ingredient storageIngredient;
    private final ItemStack result;
    private final long requiredPower;

    public CondenserOutputRecipe(Ingredient storageIngredient, ItemStack result, long requiredPower) {
        this.storageIngredient = storageIngredient;
        this.result = result.copy();
        this.requiredPower = requiredPower;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return getResult();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return getResult();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.storageIngredient);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.CONDENSER_OUTPUT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.CONDENSER_OUTPUT_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** Returns whether the stack is an allowed AE2 storage component with enough capacity for this output. */
    public boolean acceptsStorage(ItemStack stack) {
        return this.storageIngredient.test(stack) && getStorageCapacity(stack) >= this.requiredPower;
    }

    /** Returns the condenser matter capacity contributed by the stack, or zero when it is not a storage component. */
    public static double getStorageCapacity(ItemStack stack) {
        if (!(stack.getItem() instanceof IStorageComponent component) || !component.isStorageComponent(stack)) {
            return 0.0D;
        }
        return (double) component.getBytes(stack) * CondenserBlockEntity.BYTE_MULTIPLIER;
    }

    public Ingredient getStorageIngredient() {
        return this.storageIngredient;
    }

    public ItemStack getResult() {
        return this.result.copy();
    }

    public long getRequiredPower() {
        return this.requiredPower;
    }
}
