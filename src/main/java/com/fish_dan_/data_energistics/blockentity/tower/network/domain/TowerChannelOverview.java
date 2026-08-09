package com.fish_dan_.data_energistics.blockentity.tower.network.domain;

import java.util.OptionalLong;

/**
 * Splits primary-grid channel capacity into physical use, virtual leases, and remaining supply.
 *
 * @param totalCapacity     finite total capacity, or empty for Infinite mode
 * @param physicalUsage     native physical pathing usage
 * @param virtualUsage      charged virtual leases
 * @param remainingCapacity finite remaining supply, or empty for Infinite mode
 */
public record TowerChannelOverview(OptionalLong totalCapacity,
                                   long physicalUsage,
                                   long virtualUsage,
                                   OptionalLong remainingCapacity) {

    /** Validates one non-negative channel overview. */
    public TowerChannelOverview {
        if (physicalUsage < 0 || virtualUsage < 0 || totalCapacity.stream().anyMatch(value -> value < 0) || remainingCapacity.stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("Tower channel overview values are invalid");
        }
    }
}
