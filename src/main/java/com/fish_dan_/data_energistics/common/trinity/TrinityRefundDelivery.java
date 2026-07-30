package com.fish_dan_.data_energistics.common.trinity;

import java.util.List;

/**
 * Two-phase external delivery contract for an already-collected Trinity refund.
 *
 * <p>
 * {@link #prepare(List)} is side-effect free and runs before any P core is mutated. {@link #deliver(List)} runs only
 * after every participating core committed its reversible refund transaction. Delivery implementations must take
 * ownership of every supplied stack, including a durable final fallback for any destination that rejects a remainder.
 * </p>
 */
public interface TrinityRefundDelivery {

    /**
     * Captures and validates the delivery context without inserting, dropping, or mutating any offered stack.
     *
     * @param items immutable counted entries for every queued input and pending output in the aggregate
     * @return true when {@link #deliver(List)} may be invoked for this exact aggregate
     */
    boolean prepare(List<TrinityItemAmount> items);

    /**
     * Delivers supplied stacks after the core transaction has committed.
     *
     * <p>
     * Implementations must first use their preferred destination and must handle every remainder through their final
     * fallback instead of silently discarding it. A rejected final fallback is returned as the exact ordered suffix
     * that has not reached any external destination.
     * </p>
     *
     * @param items immutable counted entries for every queued input and pending output in the aggregate
     * @return ordered remaining suffix; empty only when every offered amount was delivered
     */
    List<TrinityItemAmount> deliver(List<TrinityItemAmount> items);
}
