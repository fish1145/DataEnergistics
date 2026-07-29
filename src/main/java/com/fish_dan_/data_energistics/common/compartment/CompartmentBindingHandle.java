package com.fish_dan_.data_energistics.common.compartment;

import org.jetbrains.annotations.ApiStatus;

/**
 * Opaque runtime identity for one compartment registration.
 *
 * <p>
 * A lifecycle callback must return the exact handle captured when it bound the part. Implementations can then reject
 * a late callback without allowing it to affect a newer registration with the same host and structure name.
 * </p>
 */
@ApiStatus.Internal
public interface CompartmentBindingHandle {}
