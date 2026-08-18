package com.fish_dan_.data_energistics.integration.jei.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.GenericStack;
import appeng.items.misc.WrappedGenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;

/**
 * Resolves one AE generic stack to the native JEI identity selected by the installed converter registry.
 */
final class JeiGenericStackIngredientResolver {

    private static final String AE2_JEI_INTEGRATION_MOD_ID = "ae2jeiintegration";

    private JeiGenericStackIngredientResolver() {}

    /**
     * Uses AE2 JEI Integration converters first and retains one wrapped-key fallback for installations without it.
     */
    static ResolvedIngredient<?> resolve(GenericStack stack) {
        if (stack.amount() < 0L) {
            throw invalidAmount(stack.amount());
        }
        GenericStack normalized = stack.amount() == 0L ? new GenericStack(stack.what(), 1L) : stack;
        if (Data_Energistics.isModLoaded(AE2_JEI_INTEGRATION_MOD_ID)) {
            ResolvedIngredient<?> converted = Ae2JeiGenericStackIngredientResolver.resolve(normalized);
            if (converted != null) {
                return converted;
            }
        }

        return new ResolvedIngredient<>(VanillaTypes.ITEM_STACK, WrappedGenericStack.wrap(normalized));
    }

    private static IllegalArgumentException invalidAmount(long amount) {
        String message = "Cannot resolve a negative AE2 stack amount for JEI: " + amount;
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }

    /**
     * A type-safe JEI ingredient pair passed to each consumer without raw types.
     */
    record ResolvedIngredient<T>(IIngredientType<T> type, T ingredient) {}
}
