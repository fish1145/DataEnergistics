package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/**
 * Client-thread lifecycle attached to a common orbital dashboard component tree.
 *
 * <p>
 * The common UI factory invokes these callbacks only for a logical-client player. Implementations may bind input,
 * optional map APIs and screen lifecycle, but must never mutate server state except through the supplied menu RPC.
 * </p>
 */
public interface OrbitalControlClientBinding {

    /** Called once after the dashboard has been attached to its final ModularUI. */
    void attach(ModularUI modularUI);

    /** Receives each atomic S2C menu snapshot after LDLib2 has decoded it on the client thread. */
    void acceptSnapshot(OrbitalControlMenuSnapshot snapshot);

    /**
     * Releases map listeners and cancels any local pointer hold when the screen is replaced or the client disconnects.
     */
    void close();
}
