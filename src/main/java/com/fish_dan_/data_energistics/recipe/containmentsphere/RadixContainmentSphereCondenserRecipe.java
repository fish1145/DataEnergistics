package com.fish_dan_.data_energistics.recipe.containmentsphere;

import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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

public final class RadixContainmentSphereCondenserRecipe implements Recipe<RecipeInput> {

    private static final Codec<Integer> REQUIRED_POWER_CODEC = Codec.INT.flatXmap(
            power -> power < 0 ? DataResult.error(() -> "required_power must be >= 0") : DataResult.success(power),
            DataResult::success);

    public static final MapCodec<RadixContainmentSphereCondenserRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(RadixContainmentSphereCondenserRecipe::getCatalyst),
            ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            REQUIRED_POWER_CODEC.fieldOf("required_power").forGetter(RadixContainmentSphereCondenserRecipe::getRequiredPower)).apply(instance, RadixContainmentSphereCondenserRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadixContainmentSphereCondenserRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            RadixContainmentSphereCondenserRecipe::getCatalyst,
            ItemStack.STREAM_CODEC,
            recipe -> recipe.result,
            ByteBufCodecs.VAR_INT,
            RadixContainmentSphereCondenserRecipe::getRequiredPower,
            RadixContainmentSphereCondenserRecipe::new);

    private final Ingredient catalyst;
    private final ItemStack result;
    private final int requiredPower;

    public RadixContainmentSphereCondenserRecipe(Ingredient catalyst, ItemStack result, int requiredPower) {
        this.catalyst = catalyst;
        this.result = result.copy();
        this.requiredPower = requiredPower;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return RadixContainmentSphereItem.createChargedStack();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return RadixContainmentSphereItem.createChargedStack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.catalyst);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.RADIX_CONTAINMENT_SPHERE_CONDENSER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.RADIX_CONTAINMENT_SPHERE_CONDENSER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient getCatalyst() {
        return this.catalyst;
    }

    public int getRequiredPower() {
        return this.requiredPower;
    }
}
