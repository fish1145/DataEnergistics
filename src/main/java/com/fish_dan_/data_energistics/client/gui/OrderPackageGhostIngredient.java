package com.fish_dan_.data_energistics.client.gui;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

/**
 * Converts item-list ingredients into AE2's generic fake-slot transport representation.
 */
public final class OrderPackageGhostIngredient {

    private OrderPackageGhostIngredient() {}

    /**
     * Converts the standard and generic ingredient shapes exposed by JEI into a complete AE key.
     *
     * @param ingredient item, fluid, generic stack, or raw AE key
     * @return a type-only generic stack, or {@code null} when the ingredient cannot identify an AE key
     */
    public static @Nullable GenericStack toGenericStack(Object ingredient) {
        if (ingredient instanceof GenericStack genericStack) {
            return typeOnly(genericStack.what());
        }
        if (ingredient instanceof AEKey key) {
            return typeOnly(key);
        }
        if (ingredient instanceof FluidStack fluidStack) {
            AEFluidKey key = AEFluidKey.of(fluidStack);
            return key == null ? null : typeOnly(key);
        }
        if (ingredient instanceof ItemStack itemStack && !itemStack.isEmpty()) {
            GenericStack wrapped = GenericStack.unwrapItemStack(itemStack);
            if (wrapped != null) {
                return typeOnly(wrapped.what());
            }
            AEItemKey key = AEItemKey.of(itemStack);
            return key == null ? null : typeOnly(key);
        }
        return null;
    }

    /**
     * Wraps a key for AE2's fake-slot network packet without consuming a real ingredient.
     */
    public static ItemStack wrapFilter(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.toStack();
        }
        return GenericStack.wrapInItemStack(key, 1L);
    }

    private static GenericStack typeOnly(AEKey key) {
        return new GenericStack(key, 0L);
    }
}
