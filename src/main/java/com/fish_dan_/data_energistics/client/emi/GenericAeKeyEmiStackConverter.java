package com.fish_dan_.data_energistics.client.emi;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.Nullable;

/**
 * Restores codec-backed generic EMI identities to AE2 without claiming forward conversion of custom keys.
 */
public final class GenericAeKeyEmiStackConverter implements EmiStackConverter {

    public static final GenericAeKeyEmiStackConverter INSTANCE = new GenericAeKeyEmiStackConverter();

    private GenericAeKeyEmiStackConverter() {}

    @Override
    public Class<?> getKeyType() {
        return GenericAeKeyEmiKey.class;
    }

    @Override
    public @Nullable EmiStack toEmiStack(GenericStack stack) {
        return null;
    }

    @Override
    public @Nullable GenericStack toGenericStack(EmiStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        GenericAeKeyEmiKey key = stack.getKeyOfType(GenericAeKeyEmiKey.class);
        if (key == null || !GenericAeKeyEmiStack.isSupportedKey(key.aeKey())) {
            return null;
        }
        return new GenericStack(key.aeKey(), stack.getAmount());
    }
}
