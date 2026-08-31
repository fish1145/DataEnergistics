package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.diagnostic;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * One exact finite-input shortage displayed by the crafting confirmation screen.
 *
 * @param key       exact AE key
 * @param required  complete selected-route requirement
 * @param available real inventory allocated to the requirement
 * @param missing   positive difference between required and available
 */
public record TrinityCraftingExactShortage(
                                           AEKey key,
                                           BigInteger required,
                                           BigInteger available,
                                           BigInteger missing) {

    public TrinityCraftingExactShortage {
        if (key == null || required == null || available == null || missing == null ||
                required.signum() <= 0 || available.signum() < 0 || missing.signum() <= 0 ||
                !required.equals(available.add(missing))) {
            throw new IllegalArgumentException("A Trinity crafting shortage must be exact and positive");
        }
    }
}
