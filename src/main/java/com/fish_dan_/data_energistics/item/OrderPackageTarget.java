package com.fish_dan_.data_energistics.item;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Optional;

/**
 * Owns the server-safe target identity stored by an order package.
 *
 * <p>
 * The target is an {@link AEKey} without an amount so item, fluid, and addon key types share the same contract.
 * This interface is the sole mutation entry point used by menus, pattern transfer, and later crafting integration.
 * It also provides the CPU-independent boundary for resolving a marked package from a complete pattern output key.
 * </p>
 */
public interface OrderPackageTarget {

    /**
     * Returns the process-wide implementation without loading client-only classes.
     */
    static OrderPackageTarget get() {
        return OrderPackageTargetImpl.INSTANCE;
    }

    /**
     * Creates one order package whose target is already marked.
     *
     * @param target non-null target identity; its amount is intentionally not represented
     * @return a new marked order package
     */
    ItemStack createMarkedPackage(AEKey target);

    /**
     * Resolves the target carried by a complete pattern output key.
     *
     * <p>
     * This method only identifies marked order packages. It does not apply output amounts, mutate crafting state, or
     * participate in provider admission.
     * </p>
     *
     * @param outputKey complete output identity to inspect
     * @return the package target, or empty for an unmarked package or any ordinary key
     */
    Optional<AEKey> resolveMarkedTarget(AEKey outputKey);

    /**
     * Resolves the target carried by a complete generic pattern output while deliberately ignoring its amount.
     *
     * @param output complete output identity and amount to inspect
     * @return the package target, or empty for an unmarked package or any ordinary output
     */
    Optional<AEKey> resolveMarkedTarget(GenericStack output);

    /**
     * Tests whether the stack is an order package, independently of whether it is marked.
     *
     * @param stack stack to inspect
     * @return whether the registered item is an order package
     */
    boolean isOrderPackage(ItemStack stack);

    /**
     * Reads the target only from an order package.
     *
     * @param stack stack to inspect
     * @return the complete target identity, or empty for an unmarked/non-package stack
     */
    Optional<AEKey> getTarget(ItemStack stack);

    /**
     * Marks an existing order package with a complete target identity.
     *
     * @param stack  order package to mutate
     * @param target non-null target identity
     * @throws IllegalArgumentException if {@code stack} is not an order package
     */
    void setTarget(ItemStack stack, AEKey target);

    /**
     * Removes the target marker from an existing order package.
     *
     * @param stack order package to mutate
     * @return the target that was removed, or empty when the package was already unmarked
     * @throws IllegalArgumentException if {@code stack} is not an order package
     */
    Optional<AEKey> clearTarget(ItemStack stack);
}
