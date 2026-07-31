package com.fish_dan_.data_energistics.ae2;

import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;

import java.util.Objects;

/**
 * Defines which allocator owns a grid when Channel Hub compression and an external max-flow implementation coexist.
 */
public final class ChannelHubAllocationPolicy {

    /**
     * Authoritative allocator for one pathing calculation.
     */
    public enum Allocator {
        AE2,
        EXTERNAL_MAX_FLOW,
        DATA_ENERGISTICS_SHARED_POOL
    }

    /**
     * Immutable topology facts needed to select an allocator without constructing a live AE grid in unit tests.
     */
    public record Topology(boolean hasHub,
                           int normalControllerCount,
                           int overloadedControllerCount,
                           ControllerState controllerState,
                           ChannelMode channelMode,
                           boolean externalMaxFlowAvailable) {

        public Topology {
            if (normalControllerCount < 0 || overloadedControllerCount < 0) {
                throw new IllegalArgumentException("Controller counts must not be negative");
            }
            Objects.requireNonNull(controllerState, "controllerState");
            Objects.requireNonNull(channelMode, "channelMode");
        }

        /**
         * @return total number of normal and overloaded controller blocks
         */
        public int controllerCount() {
            return Math.addExact(this.normalControllerCount, this.overloadedControllerCount);
        }
    }

    /**
     * Stable ownership decision consumed by pathing and max-flow guards.
     */
    public record Decision(Allocator allocator,
                           boolean bypassExternalMaxFlow,
                           int hubUpstreamChannels) {}

    private ChannelHubAllocationPolicy() {}

    /**
     * Selects one allocator. A Hub always keeps DataE shared-pool/compression semantics; finite controller grids then
     * suppress the external phase-three flow result so it cannot overwrite that allocation.
     *
     * @param topology immutable grid facts
     * @return allocator ownership decision
     */
    public static Decision decide(Topology topology) {
        Objects.requireNonNull(topology, "topology");
        if (topology.hasHub()) {
            boolean externalWouldRun = topology.externalMaxFlowAvailable() && topology.controllerCount() > 0 && topology.channelMode() != ChannelMode.INFINITE;
            int upstreamChannels = topology.controllerState() == ControllerState.CONTROLLER_CONFLICT ? 0 : 1;
            return new Decision(Allocator.DATA_ENERGISTICS_SHARED_POOL, externalWouldRun, upstreamChannels);
        }

        boolean externalOwnsGrid = topology.externalMaxFlowAvailable() && topology.controllerCount() > 0 && topology.channelMode() != ChannelMode.INFINITE;
        return new Decision(
                externalOwnsGrid ? Allocator.EXTERNAL_MAX_FLOW : Allocator.AE2,
                false,
                0);
    }
}
