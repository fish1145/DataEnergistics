package com.fish_dan_.data_energistics.item.order;

import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Owns the server-safe target identity stored by an order package.
 *
 * <p>
 * The target is an {@link AEKey} without an amount so item, fluid, and addon key types share the same contract.
 * This type is the sole mutation entry point used by menus, pattern transfer, and later crafting integration. It also
 * provides the CPU-independent boundary for resolving a marked package from a complete pattern output key.
 * </p>
 */
public final class OrderPackageTarget {

    /** Shared stateless target codec and mutation entry point. */
    private static final OrderPackageTarget INSTANCE = new OrderPackageTarget();

    private OrderPackageTarget() {}

    /**
     * Returns the process-wide implementation without loading client-only classes.
     */
    public static OrderPackageTarget get() {
        return INSTANCE;
    }

    /**
     * Creates one order package whose target is already marked.
     *
     * @param target non-null target identity; its amount is intentionally not represented
     * @return a new marked order package
     */
    public ItemStack createMarkedPackage(AEKey target) {
        ItemStack stack = DEItems.ORDER_PACKAGE.toStack();
        setTarget(stack, target);
        return stack;
    }

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
    public Optional<AEKey> resolveMarkedTarget(AEKey outputKey) {
        if (!(outputKey instanceof AEItemKey itemKey) || !itemKey.is(DEItems.ORDER_PACKAGE.get())) {
            return Optional.empty();
        }
        return getTarget(itemKey.toStack());
    }

    /**
     * Resolves the target carried by a complete generic pattern output while deliberately ignoring its amount.
     *
     * @param output complete output identity and amount to inspect
     * @return the package target, or empty for an unmarked package or any ordinary output
     */
    public Optional<AEKey> resolveMarkedTarget(GenericStack output) {
        return resolveMarkedTarget(output.what());
    }

    /**
     * Tests whether the stack is an order package, independently of whether it is marked.
     *
     * @param stack stack to inspect
     * @return whether the registered item is an order package
     */
    public boolean isOrderPackage(ItemStack stack) {
        return stack.is(DEItems.ORDER_PACKAGE.get());
    }

    /**
     * Reads the target only from an order package.
     *
     * @param stack stack to inspect
     * @return the complete target identity, or empty for an unmarked/non-package stack
     */
    public Optional<AEKey> getTarget(ItemStack stack) {
        if (!isOrderPackage(stack)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(DEDataComponents.ORDER_PACKAGE_TARGET.get()));
    }

    /**
     * Marks an existing order package with a complete target identity.
     *
     * @param stack  order package to mutate
     * @param target non-null target identity
     * @throws IllegalArgumentException if {@code stack} is not an order package
     */
    public void setTarget(ItemStack stack, AEKey target) {
        requireOrderPackage(stack);
        stack.set(DEDataComponents.ORDER_PACKAGE_TARGET.get(), target);
    }

    /**
     * Removes the target marker from an existing order package.
     *
     * @param stack order package to mutate
     * @return the target that was removed, or empty when the package was already unmarked
     * @throws IllegalArgumentException if {@code stack} is not an order package
     */
    public Optional<AEKey> clearTarget(ItemStack stack) {
        requireOrderPackage(stack);
        return Optional.ofNullable(stack.remove(DEDataComponents.ORDER_PACKAGE_TARGET.get()));
    }

    /**
     * Rejects attempts to attach target metadata to arbitrary items.
     */
    private void requireOrderPackage(ItemStack stack) {
        if (!isOrderPackage(stack)) {
            throw new IllegalArgumentException("Target data can only be changed on an order package");
        }
    }
}
