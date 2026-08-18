package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.xei.ingredient.DataResourceKey;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.stack.EmiStack;
import org.jspecify.annotations.Nullable;

/**
 * Converts the native EMI identities to and from AE2 custom keys without using wrapped item stacks.
 */
public final class DataResourceEmiStackConverter implements EmiStackConverter {

    public static final DataResourceEmiStackConverter INSTANCE = new DataResourceEmiStackConverter();

    private DataResourceEmiStackConverter() {}

    @Override
    public Class<?> getKeyType() {
        return DataResourceKey.class;
    }

    @Override
    public @Nullable EmiStack toEmiStack(GenericStack stack) {
        DataResourceKey key = DataResourceKey.fromAeKey(stack.what());
        if (key == null) {
            return null;
        }
        if (stack.amount() < 0L) {
            String message = "Cannot convert a negative AE2 stack amount to EMI: " + stack.amount();
            Data_Energistics.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        long amount = stack.amount() == 0L ? 1L : stack.amount();
        return new DataResourceEmiStack(key, amount);
    }

    @Override
    public @Nullable GenericStack toGenericStack(EmiStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        DataResourceKey key = stack.getKeyOfType(DataResourceKey.class);
        if (key == null) {
            return null;
        }
        return new GenericStack(key.aeKey(), stack.getAmount());
    }
}
