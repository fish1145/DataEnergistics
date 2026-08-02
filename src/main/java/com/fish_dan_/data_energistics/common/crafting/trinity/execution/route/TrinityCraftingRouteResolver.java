package com.fish_dan_.data_energistics.common.crafting.trinity.execution.route;

import appeng.api.networking.IGridNode;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the physical and effective-service identities of one Trinity crafting node.
 */
public interface TrinityCraftingRouteResolver {

    /**
     * Resolves one immutable route without changing node or grid state.
     *
     * @param node       access-hatch grid node, or {@code null} before attachment
     * @param leaseEpoch current host lease epoch
     * @return resolved route, or {@code null} while the node has no owning grid or remains an inactive subordinate
     */
    @Nullable
    TrinityCraftingExecutionRoute resolve(@Nullable IGridNode node, long leaseEpoch);
}
