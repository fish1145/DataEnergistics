package com.fish_dan_.data_energistics.client.jei.ingredient;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies JEI search, identity, amount, and cheat-in behavior for Data Energistics resources.
 */
public final class DataResourceJeiIngredientHelper implements IIngredientHelper<DataResourceJeiIngredient> {

    /**
     * Shared stateless helper instance registered with JEI.
     */
    public static final DataResourceJeiIngredientHelper INSTANCE = new DataResourceJeiIngredientHelper();

    private DataResourceJeiIngredientHelper() {}

    @Override
    public IIngredientType<DataResourceJeiIngredient> getIngredientType() {
        return DataResourceJeiIngredient.TYPE;
    }

    @Override
    public String getDisplayName(DataResourceJeiIngredient ingredient) {
        return ingredient.key().aeKey().getDisplayName().getString();
    }

    @Override
    public Object getUid(DataResourceJeiIngredient ingredient, UidContext context) {
        return ingredient.key().id();
    }

    @SuppressWarnings({ "deprecation", "removal" })
    @Override
    public String getUniqueId(DataResourceJeiIngredient ingredient, UidContext context) {
        return ingredient.key().id().toString();
    }

    @Override
    public long getAmount(DataResourceJeiIngredient ingredient) {
        return ingredient.amount();
    }

    @Override
    public DataResourceJeiIngredient copyWithAmount(DataResourceJeiIngredient ingredient, long amount) {
        return new DataResourceJeiIngredient(ingredient.key(), amount);
    }

    @Override
    public ResourceLocation getResourceLocation(DataResourceJeiIngredient ingredient) {
        return ingredient.key().id();
    }

    @Override
    public ItemStack getCheatItemStack(DataResourceJeiIngredient ingredient) {
        return ingredient.key().aeKey().wrapForDisplayOrFilter();
    }

    @Override
    public DataResourceJeiIngredient copyIngredient(DataResourceJeiIngredient ingredient) {
        return new DataResourceJeiIngredient(ingredient.key(), ingredient.amount());
    }

    @Override
    public DataResourceJeiIngredient normalizeIngredient(DataResourceJeiIngredient ingredient) {
        return new DataResourceJeiIngredient(ingredient.key(), 1L);
    }

    @Override
    public String getErrorInfo(@Nullable DataResourceJeiIngredient ingredient) {
        return String.valueOf(ingredient);
    }
}
