package com.fish_dan_.data_energistics.api.crafting.dispatch;

import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;

/**
 * Public registration boundary for stateless dispatch-time virtual crafting outputs.
 */
public final class CraftingVirtualCompletionDispatch {

    private CraftingVirtualCompletionDispatch() {}

    /**
     * Registers one adapter by object identity.
     *
     * <p>
     * Registration is a mod-lifecycle operation. A single declared output may be claimed by at most one registered
     * adapter; ambiguous matches fail when that output is projected.
     * </p>
     *
     * @param adapter stateless output resolver
     * @return lifecycle handle for the exact registration
     */
    public static VirtualCraftingOutputRegistration registerAdapter(VirtualCraftingOutputAdapter adapter) {
        return VirtualCraftingOutputAdapters.register(adapter);
    }
}
