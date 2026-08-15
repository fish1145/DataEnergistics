package com.fish_dan_.data_energistics.common.crafting.trinity.execution.route;

import org.jspecify.annotations.Nullable;

/**
 * Thread-safe scalar identity of a Trinity execution route.
 *
 * <p>
 * The token intentionally omits both {@code IGrid} references. It may cross into proposal workers, while the live
 * route is resolved and compared only on the server thread.
 * </p>
 *
 * @param leaseEpoch           information exchange depot lease epoch
 * @param membershipGeneration VirtualGrid membership generation
 */
public record TrinityCraftingExecutionRouteToken(long leaseEpoch, long membershipGeneration) {

    public TrinityCraftingExecutionRouteToken {
        if (leaseEpoch < 0L) {
            throw new IllegalArgumentException("Trinity crafting route token lease epoch must not be negative");
        }
        if (membershipGeneration < 0L) {
            throw new IllegalArgumentException("Trinity crafting route token membership generation must not be negative");
        }
    }

    /**
     * Captures only the scalar generations from a server-thread route.
     *
     * @param route current executable route
     * @return immutable asynchronous token
     */
    public static TrinityCraftingExecutionRouteToken capture(TrinityCraftingExecutionRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("Trinity crafting execution route must not be null");
        }
        return new TrinityCraftingExecutionRouteToken(route.leaseEpoch(), route.membershipGeneration());
    }

    /**
     * Revalidates scalar generations against the current server-thread route.
     *
     * @param route currently resolved route
     * @return whether the route still belongs to the uninterrupted lease and membership
     */
    public boolean isCurrent(@Nullable TrinityCraftingExecutionRoute route) {
        return route != null &&
                this.leaseEpoch == route.leaseEpoch() &&
                this.membershipGeneration == route.membershipGeneration();
    }
}
