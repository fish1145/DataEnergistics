package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;

import java.util.Objects;
import java.util.Optional;

/**
 * Data-component-backed implementation kept package-private so callers depend on {@link OrderPackageTarget}.
 */
final class OrderPackageTargetImpl implements OrderPackageTarget {

    /** Shared stateless implementation used by the public interface factory. */
    static final OrderPackageTargetImpl INSTANCE = new OrderPackageTargetImpl();

    private OrderPackageTargetImpl() {}

    @Override
    public ItemStack createMarkedPackage(AEKey target) {
        ItemStack stack = ModItems.ORDER_PACKAGE.toStack();
        setTarget(stack, target);
        return stack;
    }

    @Override
    public boolean isOrderPackage(ItemStack stack) {
        return stack != null && stack.is(ModItems.ORDER_PACKAGE.get());
    }

    @Override
    public Optional<AEKey> getTarget(ItemStack stack) {
        if (!isOrderPackage(stack)) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(ModDataComponents.ORDER_PACKAGE_TARGET.get()));
    }

    @Override
    public void setTarget(ItemStack stack, AEKey target) {
        requireOrderPackage(stack);
        stack.set(ModDataComponents.ORDER_PACKAGE_TARGET.get(), Objects.requireNonNull(target, "target"));
    }

    @Override
    public Optional<AEKey> clearTarget(ItemStack stack) {
        requireOrderPackage(stack);
        return Optional.ofNullable(stack.remove(ModDataComponents.ORDER_PACKAGE_TARGET.get()));
    }

    private void requireOrderPackage(ItemStack stack) {
        if (!isOrderPackage(stack)) {
            throw new IllegalArgumentException("Target data can only be changed on an order package");
        }
    }
}
