package com.fish_dan_.data_energistics.ae2;

/**
 * Exposes a monotonic revision for AE2 pathing and controller-topology changes.
 *
 * <p>
 * The public AE2 pathing API exposes the current state but not a mutation generation. The PathingService Mixin
 * implements this bridge so grid-local scalar caches can avoid rescanning controller geometry on every server tick
 * while still invalidating immediately after a repath.
 * </p>
 */
public interface PathingTopologyRevision {

    /**
     * Returns the revision advanced after each pathing reset request.
     *
     * @return non-negative pathing/controller-topology revision
     */
    long dataEnergistics$pathingTopologyRevision();
}
