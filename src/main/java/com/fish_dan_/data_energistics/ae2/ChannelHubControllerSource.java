package com.fish_dan_.data_energistics.ae2;

/**
 * Supplies controller capacity that is defined per controller rather than by ordinary exposed-face geometry.
 */
public interface ChannelHubControllerSource {

    /**
     * Returns this controller's configured supply for the active cable-capacity multiplier.
     *
     * @param cableCapacityFactor AE2 channel-mode multiplier
     * @return non-negative channel supply
     */
    int getChannelHubSupply(int cableCapacityFactor);
}
