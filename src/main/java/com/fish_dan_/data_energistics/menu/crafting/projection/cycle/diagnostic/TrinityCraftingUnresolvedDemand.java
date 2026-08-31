package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * One positive intermediate demand that could not be proved to be an external shortage.
 *
 * @param key    exact AE key
 * @param amount unresolved positive amount
 */
public record TrinityCraftingUnresolvedDemand(AEKey key, BigInteger amount) {

    public TrinityCraftingUnresolvedDemand {
        if (key == null || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity unresolved crafting demand must be positive");
        }
    }
}
