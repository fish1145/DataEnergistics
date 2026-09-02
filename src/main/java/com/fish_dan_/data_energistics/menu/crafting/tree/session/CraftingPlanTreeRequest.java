package com.fish_dan_.data_energistics.menu.crafting.tree.session;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.helpers.ICraftingGridMenu.AutoCraftEntry;
import appeng.menu.locator.MenuHostLocator;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Server-owned request context; the host and queue are never serialized into the display graph. */
public record CraftingPlanTreeRequest(UUID playerId, AEKey target, long amount,
                                      CraftingQuantityMode quantityMode, MenuHostLocator locator,
                                      ISubMenuHost host, @Nullable List<AutoCraftEntry> queue,
                                      @Nullable List<Integer> requestedSlots) {

    public CraftingPlanTreeRequest {
        if (amount <= 0) {
            throw new IllegalArgumentException("A plan-tree request requires a positive amount");
        }
        queue = queue == null ? null : List.copyOf(queue);
        requestedSlots = requestedSlots == null ? null : List.copyOf(requestedSlots);
    }
}
