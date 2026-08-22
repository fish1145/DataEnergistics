package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;

import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;

/** Registers the immutable orbital menu snapshot with LDLib2's typed synchronization system. */
final class OrbitalControlUiSyncAccessors {

    private static boolean initialized;

    private OrbitalControlUiSyncAccessors() {}

    static synchronized void init() {
        if (initialized) {
            return;
        }
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(OrbitalControlTerminalSnapshot.class)
                .codec(OrbitalControlTerminalSnapshot.CODEC)
                .streamCodec(OrbitalControlTerminalSnapshot.STREAM_CODEC)
                .build(), 100);
        initialized = true;
    }
}
