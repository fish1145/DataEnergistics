package com.fish_dan_.data_energistics.common.trinity.host;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot;

/**
 * Atomic server-authoritative status exposed by one information exchange depot.
 *
 * @param patternMaintenance     progress and maintenance-only performance measured by the bound Data Core
 * @param coreTickNanos          complete elapsed time of the bound Data Core's latest server tick
 * @param exchangeDepotTickNanos complete elapsed time of this depot's latest server tick
 */
public record TrinityInformationExchangeDepotStatus(
                                                    TrinityPatternMaintenanceSnapshot patternMaintenance,
                                                    long coreTickNanos,
                                                    long exchangeDepotTickNanos) {

    public TrinityInformationExchangeDepotStatus {
        if (coreTickNanos < 0L || exchangeDepotTickNanos < 0L) {
            throw new IllegalArgumentException("Trinity information exchange tick times must not be negative");
        }
    }

    /** Returns an unbound status while preserving this physical depot's own latest tick measurement. */
    public static TrinityInformationExchangeDepotStatus unbound(long exchangeDepotTickNanos) {
        return new TrinityInformationExchangeDepotStatus(
                TrinityPatternMaintenanceSnapshot.idle(0, 0),
                0L,
                exchangeDepotTickNanos);
    }
}
