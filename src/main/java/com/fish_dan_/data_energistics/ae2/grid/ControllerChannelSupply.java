package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.pathing.ChannelMode;

/**
 * Supplies the authoritative total channel budget of one controller owner when a controller cannot express that budget
 * through its AE grid node.
 *
 * <p>
 * This contract exists for controller designs whose total supply is not six identical face capacities. It is queried on
 * the server thread while the tower domain snapshots AE pathing state. Implementations must be side-effect free and
 * keep
 * the returned value stable until they trigger an AE pathing topology revision.
 */
public interface ControllerChannelSupply {

    /**
     * Returns this controller's complete channel supply for the active channel mode.
     *
     * <p>
     * The result must already include every face or controller-wide rule and the supplied {@code channelMode} factor.
     * It must be non-negative. The tower adds the value exactly once per controller owner and throws when the contract
     * is
     * violated.
     *
     * @param channelMode active AE channel mode
     * @return authoritative non-negative total supply for this controller
     */
    int totalChannelSupply(ChannelMode channelMode);
}
