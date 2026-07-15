package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Resolves native EMI stacks first and creates a codec-backed stack only when no AE2 converter accepts the key.
 */
final class GenericAeKeyEmiStacks {

    private GenericAeKeyEmiStacks() {}

    static EmiStack toEmiStack(GenericStack stack) {
        EmiStack specialized = EmiStackHelper.toEmiStack(stack);
        if (specialized != null) {
            return specialized;
        }
        if (stack.amount() < 0L) {
            String message = "Cannot convert a negative AE2 stack amount to EMI: " + stack.amount();
            Data_Energistics.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        long amount = stack.amount() == 0L ? 1L : stack.amount();
        return new GenericAeKeyEmiStack(stack.what(), amount);
    }
}
