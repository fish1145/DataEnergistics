package com.fish_dan_.data_energistics.client.jei.ingredient;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;

/**
 * Converts native Data Energistics JEI ingredients to and from AE2 generic stacks.
 */
public final class DataResourceAe2JeiIngredientConverter
                                                         implements IngredientConverter<DataResourceJeiIngredient> {

    public static final @NotNull DataResourceAe2JeiIngredientConverter INSTANCE = new DataResourceAe2JeiIngredientConverter();

    private DataResourceAe2JeiIngredientConverter() {}

    @Override
    public @NotNull IIngredientType<DataResourceJeiIngredient> getIngredientType() {
        return DataResourceJeiIngredient.TYPE;
    }

    @Override
    public @Nullable DataResourceJeiIngredient getIngredientFromStack(@NotNull GenericStack stack) {
        return DataResourceJeiIngredient.from(stack);
    }

    @Override
    public @NotNull GenericStack getStackFromIngredient(@NotNull DataResourceJeiIngredient ingredient) {
        return ingredient.asGenericStack();
    }
}
