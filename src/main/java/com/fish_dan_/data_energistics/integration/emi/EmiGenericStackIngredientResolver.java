package com.fish_dan_.data_energistics.integration.emi;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.items.misc.WrappedGenericStack;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Resolves AE generic stacks through AE2's EMI converters with one consistent wrapped-key fallback.
 */
final class EmiGenericStackIngredientResolver {

    private EmiGenericStackIngredientResolver() {}

    static EmiStack resolve(GenericStack stack) {
        if (stack.amount() < 0L) {
            String message = "Cannot resolve a negative AE2 stack amount for EMI: " + stack.amount();
            Data_Energistics.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        GenericStack normalized = stack.amount() == 0L ? new GenericStack(stack.what(), 1L) : stack;
        EmiStack converted = EmiStackHelper.toEmiStack(normalized);
        if (converted != null) {
            return converted;
        }

        ItemStack wrappedIdentity = WrappedGenericStack.wrap(normalized.what(), 1L);
        return EmiStack.of(wrappedIdentity).setAmount(normalized.amount());
    }
}
