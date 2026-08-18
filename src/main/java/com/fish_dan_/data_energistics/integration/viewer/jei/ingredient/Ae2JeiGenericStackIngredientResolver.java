package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * Optional-classloading boundary for AE2 JEI Integration's converter registry.
 */
final class Ae2JeiGenericStackIngredientResolver {

    private Ae2JeiGenericStackIngredientResolver() {}

    static JeiGenericStackIngredientResolver.@Nullable ResolvedIngredient<?> resolve(
                                                                                     GenericStack stack) {
        for (IngredientConverter<?> converter : IngredientConverters.getConverters()) {
            JeiGenericStackIngredientResolver.ResolvedIngredient<?> resolved = resolve(converter, stack);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private static <T> JeiGenericStackIngredientResolver.@Nullable ResolvedIngredient<T> resolve(
                                                                                                 IngredientConverter<T> converter,
                                                                                                 GenericStack stack) {
        T ingredient = converter.getIngredientFromStack(stack);
        return ingredient == null ? null : new JeiGenericStackIngredientResolver.ResolvedIngredient<>(
                converter.getIngredientType(), ingredient);
    }
}
