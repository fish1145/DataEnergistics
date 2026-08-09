package com.fish_dan_.data_energistics.common.crafting.order;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.item.order.OrderPackageTarget;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Optional;

/**
 * Isolated order-package adapter for the generic virtual crafting output contract.
 */
@DataEnergisticsEntrypoint
public final class OrderPackageVirtualOutputAdapter implements VirtualCraftingOutputAdapter, DataEnergisticsPlugin {

    /** Public constructor required by the common entrypoint scanner. */
    public OrderPackageVirtualOutputAdapter() {}

    /** Stages this built-in virtual output adapter in the unified plugin registry. */
    @Override
    public void register(DataEnergisticsRegistry registry) {
        registry.virtualCrafting().registerOutputAdapter(this);
    }

    @Override
    public Optional<AEKey> resolveTarget(GenericStack declaredOutput) {
        return OrderPackageTarget.get().resolveMarkedTarget(declaredOutput);
    }

    @Override
    public VirtualCraftingCompletionMode completionMode(GenericStack declaredOutput) {
        return VirtualCraftingCompletionMode.COMPLETE_WITHOUT_OUTPUT;
    }
}
