package com.fish_dan_.data_energistics.ae2;

/**
 * Marks an AE grid-node owner as a data distribution channel hub.
 * <p>
 * Pathing uses this identity to share the controller-wide channel budget while treating the host as a compression
 * boundary: channels below the hub remain subject to their local cable limits, while the controller-facing route only
 * carries the hub's own channel.
 */
public interface ChannelHubHost {}
