package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

/**
 * Signals an exact bound contradiction discovered before invoking ojAlgo.
 */
public final class TrinityRadixInfeasibleException extends RuntimeException {

    public TrinityRadixInfeasibleException(String constraint) {
        super(constraint);
    }
}
