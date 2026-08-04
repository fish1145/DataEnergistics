package com.fish_dan_.data_energistics.common.crafting.order;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CraftingVirtualCompletionDispatch;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputRegistration;
import com.fish_dan_.data_energistics.item.OrderPackageTarget;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Optional;

/**
 * Isolated order-package adapter for the generic virtual crafting output contract.
 */
public final class OrderPackageVirtualOutputAdapter implements VirtualCraftingOutputAdapter {

    private static final OrderPackageVirtualOutputAdapter INSTANCE = new OrderPackageVirtualOutputAdapter();
    private static VirtualCraftingOutputRegistration registration;

    private OrderPackageVirtualOutputAdapter() {}

    /**
     * Registers the built-in adapter exactly once during common bootstrap.
     */
    public static synchronized void init() {
        if (registration != null) {
            throw new IllegalStateException("Order package virtual output adapter was initialized more than once");
        }
        registration = CraftingVirtualCompletionDispatch.registerAdapter(INSTANCE);
    }

    @Override
    public Optional<AEKey> resolveTarget(GenericStack declaredOutput) {
        return OrderPackageTarget.get().resolveMarkedTarget(declaredOutput);
    }
}
