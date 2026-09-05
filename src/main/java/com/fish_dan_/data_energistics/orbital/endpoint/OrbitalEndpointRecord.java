package com.fish_dan_.data_energistics.orbital.endpoint;

/**
 * Persisted endpoint kind and owner-controlled failover priority.
 */
public record OrbitalEndpointRecord(
                                    OrbitalEndpointLocation location,
                                    OrbitalEndpointKind kind,
                                    int priority) {

    public OrbitalEndpointRecord {
        if (priority < 0) {
            throw new IllegalArgumentException("Endpoint priority must not be negative");
        }
    }
}
