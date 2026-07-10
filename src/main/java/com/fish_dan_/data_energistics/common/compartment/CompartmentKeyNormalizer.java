package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

/**
 * Normalizes keys written into compartment configuration and storage slots.
 *
 * <p>
 * AE2 represents arbitrary {@link AEKey} values in item slots by wrapping a
 * {@link GenericStack} into an {@link AEItemKey}. Compartments store the real key,
 * so wrapped item keys are unwrapped before validation or persistence.
 */
public final class CompartmentKeyNormalizer {

    private CompartmentKeyNormalizer() {}

    /**
     * Returns the real key represented by {@code key}.
     *
     * <p>
     * Plain keys are returned unchanged. Item keys containing wrapped generic
     * stacks are replaced by the wrapped stack key.
     */
    @Nullable
    public static AEKey normalize(@Nullable AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return key;
        }
        GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
        return wrapped != null ? wrapped.what() : key;
    }

    /**
     * Returns a stack using {@link #normalize(AEKey)} while preserving amount.
     */
    @Nullable
    public static GenericStack normalize(@Nullable GenericStack stack) {
        if (stack == null) {
            return null;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
            if (wrapped != null) {
                return wrapped;
            }
        }
        AEKey normalized = normalize(stack.what());
        return normalized == stack.what() ? stack : new GenericStack(normalized, stack.amount());
    }
}
