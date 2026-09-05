package com.fish_dan_.data_energistics.orbital.control.ui;

import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Dist-safe bridge installed by client bootstrap before any orbital control menu can open. */
public final class OrbitalControlClientBridge {

    private static @Nullable Binder binder;

    private OrbitalControlClientBridge() {}

    /** Installs the sole physical-client binding factory during client bootstrap. */
    public static void install(Binder clientBinder) {
        if (binder != null) {
            throw new IllegalStateException("Orbital control client bindings are already installed");
        }
        binder = Objects.requireNonNull(clientBinder);
    }

    /** Creates one logical-client binding without exposing client-only types to the common UI tree. */
    static OrbitalControlClientBinding bind(
                                            OrbitalControlDashboard dashboard,
                                            OrbitalControlUiSource source,
                                            RPCEmitter commandEmitter) {
        return Objects.requireNonNull(binder, "Orbital control client bindings were not installed")
                .bind(dashboard, source, commandEmitter);
    }

    /**
     * Creates a menu-scoped client binding. Called only on the Minecraft client thread; the returned binding must be
     * closed when its screen is replaced.
     */
    @FunctionalInterface
    public interface Binder {

        OrbitalControlClientBinding bind(
                                         OrbitalControlDashboard dashboard,
                                         OrbitalControlUiSource source,
                                         RPCEmitter commandEmitter);
    }
}
