package com.fish_dan_.data_energistics.recipe.containmentsphere;

import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class RadixContainmentSphereRightClickRecipe implements Recipe<RadixContainmentSphereRightClickRecipeInput> {

    private static final Codec<Block> BLOCK_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> BuiltInRegistries.BLOCK.getOptional(id)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown block: " + id)),
            block -> DataResult.success(BuiltInRegistries.BLOCK.getKey(block)));
    private static final Codec<Long> DATA_COST_CODEC = Codec.LONG.flatXmap(
            amount -> amount < 0L ? DataResult.error(() -> "data_cost must be >= 0") : DataResult.success(amount),
            DataResult::success);
    private static final Codec<Double> ENERGY_COST_CODEC = Codec.DOUBLE.flatXmap(
            amount -> amount < 0.0D ? DataResult.error(() -> "energy_cost must be >= 0") : DataResult.success(amount),
            DataResult::success);
    private static final Codec<ResourceLocation> ITEM_ID_CODEC = ResourceLocation.CODEC.flatXmap(
            id -> {
                var item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
                return item == Items.AIR ? DataResult.error(() -> "Unknown item: " + id) : DataResult.success(id);
            },
            DataResult::success);
    private static final Codec<Ingredient> ITEM_INGREDIENT_CODEC = Codec.either(
            ITEM_ID_CODEC,
            Ingredient.CODEC_NONEMPTY).xmap(
                    either -> either.map(id -> Ingredient.of(BuiltInRegistries.ITEM.get(id)), ingredient -> ingredient),
                    ingredient -> {
                        ItemStack[] stacks = ingredient.getItems();
                        if (stacks.length == 1) {
                            return Either.left(BuiltInRegistries.ITEM.getKey(stacks[0].getItem()));
                        }
                        return Either.right(ingredient);
                    });
    private static final Codec<List<IngredientEntry>> INGREDIENTS_CODEC = IngredientEntry.CODEC.listOf().flatXmap(
            RadixContainmentSphereRightClickRecipe::validateIngredientEntries,
            DataResult::success);
    private static final Codec<List<ResultEntry>> RESULTS_CODEC = ResultEntry.CODEC.listOf().flatXmap(
            results -> results.isEmpty() ? DataResult.error(() -> "results must contain at least one entry") : DataResult.success(results),
            DataResult::success);

    public static final MapCodec<RadixContainmentSphereRightClickRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INGREDIENTS_CODEC.fieldOf("ingredients").forGetter(RadixContainmentSphereRightClickRecipe::toIngredientEntries),
            RESULTS_CODEC.fieldOf("results").forGetter(RadixContainmentSphereRightClickRecipe::toResultEntries),
            DATA_COST_CODEC.optionalFieldOf("data_cost").forGetter(recipe -> Optional.empty()),
            ENERGY_COST_CODEC.optionalFieldOf("energy_cost").forGetter(recipe -> Optional.empty())).apply(instance, RadixContainmentSphereRightClickRecipe::fromEntries));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadixContainmentSphereRightClickRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC,
            RadixContainmentSphereRightClickRecipe::getItemIngredient,
            ResourceLocation.STREAM_CODEC.map(
                    id -> BuiltInRegistries.BLOCK.get(id),
                    block -> BuiltInRegistries.BLOCK.getKey(block)),
            RadixContainmentSphereRightClickRecipe::getInputBlock,
            ResourceLocation.STREAM_CODEC.map(
                    id -> BuiltInRegistries.BLOCK.get(id),
                    block -> BuiltInRegistries.BLOCK.getKey(block)),
            RadixContainmentSphereRightClickRecipe::getResultBlock,
            ByteBufCodecs.VAR_LONG,
            RadixContainmentSphereRightClickRecipe::getDataCost,
            ByteBufCodecs.DOUBLE,
            RadixContainmentSphereRightClickRecipe::getEnergyCost,
            RadixContainmentSphereRightClickRecipe::new);

    private final Ingredient itemIngredient;
    private final Block inputBlock;
    private final Block resultBlock;
    private final long dataCost;
    private final double energyCost;

    public RadixContainmentSphereRightClickRecipe(Ingredient itemIngredient, Block inputBlock, Block resultBlock, long dataCost,
                                                  double energyCost) {
        this.itemIngredient = itemIngredient;
        this.inputBlock = inputBlock;
        this.resultBlock = resultBlock;
        this.dataCost = dataCost;
        this.energyCost = energyCost;
    }

    @Override
    public boolean matches(RadixContainmentSphereRightClickRecipeInput input, Level level) {
        return this.matches(input.stack(), input.state());
    }

    public boolean matches(ItemStack stack, BlockState state) {
        return this.itemIngredient.test(stack) && state.is(this.inputBlock);
    }

    @Override
    public ItemStack assemble(RadixContainmentSphereRightClickRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        if (this.resultBlock.asItem() instanceof BlockItem blockItem) {
            return new ItemStack(blockItem);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient getItemIngredient() {
        return this.itemIngredient;
    }

    public Block getInputBlock() {
        return this.inputBlock;
    }

    public Block getResultBlock() {
        return this.resultBlock;
    }

    public long getDataCost() {
        return this.dataCost;
    }

    public double getEnergyCost() {
        return this.energyCost;
    }

    private static RadixContainmentSphereRightClickRecipe fromEntries(List<IngredientEntry> ingredients, List<ResultEntry> results,
                                                                      Optional<Long> topLevelDataCost, Optional<Double> topLevelEnergyCost) {
        ItemIngredientEntry itemEntry = findItemEntry(ingredients);
        return new RadixContainmentSphereRightClickRecipe(
                itemEntry.ingredient(),
                findInputBlock(ingredients),
                results.getFirst().block(),
                itemEntry.dataCost().orElseGet(() -> topLevelDataCost.orElse(0L)),
                itemEntry.energyCost().orElseGet(() -> topLevelEnergyCost.orElse(0.0D)));
    }

    private List<IngredientEntry> toIngredientEntries() {
        return List.of(
                new IngredientEntry(new ItemIngredientEntry(this.itemIngredient,
                        Optional.of(this.dataCost), Optional.of(this.energyCost)), null),
                new IngredientEntry(null, this.inputBlock));
    }

    private List<ResultEntry> toResultEntries() {
        return List.of(new ResultEntry(this.resultBlock));
    }

    private static DataResult<List<IngredientEntry>> validateIngredientEntries(List<IngredientEntry> ingredients) {
        int itemCount = 0;
        int blockCount = 0;
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient.item() != null) {
                itemCount++;
            }
            if (ingredient.block() != null) {
                blockCount++;
            }
        }

        if (itemCount != 1) {
            return DataResult.error(() -> "ingredients must contain exactly one item entry");
        }
        if (blockCount != 1) {
            return DataResult.error(() -> "ingredients must contain exactly one block entry");
        }
        return DataResult.success(ingredients);
    }

    private static ItemIngredientEntry findItemEntry(List<IngredientEntry> ingredients) {
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient.item() != null) {
                return ingredient.item();
            }
        }
        throw new IllegalArgumentException("Missing item ingredient");
    }

    private static Block findInputBlock(List<IngredientEntry> ingredients) {
        for (IngredientEntry ingredient : ingredients) {
            if (ingredient.block() != null) {
                return ingredient.block();
            }
        }
        throw new IllegalArgumentException("Missing block ingredient");
    }

    private record IngredientEntry(@Nullable ItemIngredientEntry item, @Nullable Block block) {

        private static final Codec<IngredientEntry> CODEC = Codec.either(
                ItemIngredientEntry.CODEC.fieldOf("item").codec(),
                BLOCK_CODEC.fieldOf("block").codec()).xmap(
                        either -> either.map(item -> new IngredientEntry(item, null), block -> new IngredientEntry(null, block)),
                        entry -> entry.item() != null ? Either.left(entry.item()) : Either.right(entry.block()));
    }

    private record ItemIngredientEntry(Ingredient ingredient, Optional<Long> dataCost, Optional<Double> energyCost) {

        private static final Codec<ItemIngredientEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ITEM_INGREDIENT_CODEC.fieldOf("item").forGetter(ItemIngredientEntry::ingredient),
                DATA_COST_CODEC.optionalFieldOf("data_cost").forGetter(ItemIngredientEntry::dataCost),
                ENERGY_COST_CODEC.optionalFieldOf("energy_cost").forGetter(ItemIngredientEntry::energyCost)).apply(instance, ItemIngredientEntry::new));
    }

    private record ResultEntry(Block block) {

        private static final Codec<ResultEntry> CODEC = BLOCK_CODEC.fieldOf("id").codec()
                .xmap(ResultEntry::new, ResultEntry::block);
    }
}
