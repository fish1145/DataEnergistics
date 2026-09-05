package com.fish_dan_.data_energistics.orbital.endpoint;

/**
 * Expected placement rejection raised when an orbital weapon has exhausted an endpoint capacity limit.
 */
public final class OrbitalEndpointLimitException extends IllegalStateException {

    public OrbitalEndpointLimitException(String message) {
        super(message);
    }
}
