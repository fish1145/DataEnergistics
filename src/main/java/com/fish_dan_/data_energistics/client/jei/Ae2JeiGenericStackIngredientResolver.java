package com.fish_dan_.data_energistics.client.jei;

import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * Optional-classloading boundary for AE2 JEI Integration's converter registry.
 */
final class Ae2JeiGenericStackIngredientResolver {

    private Ae2JeiGenericStackIngredientResolver() {}

    static @Nullable JeiGenericStackIngredientResolver.ResolvedIngredient<?> resolve(
                                                                                     @NotNull GenericStack stack) {
        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            JeiGenericStackIngredientResolver.ResolvedIngredient<?> resolved = resolve(converter, stack);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static <T> @Nullable JeiGenericStackIngredientResolver.ResolvedIngredient<T> resolve(
                                                                                                 @NotNull IngredientConverter<T> converter,
                                                                                                 @NotNull GenericStack stack) {
        T ingredient = converter.getIngredientFromStack(stack);
        return ingredient == null ? null : new JeiGenericStackIngredientResolver.ResolvedIngredient<>(
                converter.getIngredientType(), ingredient);
    }
}
