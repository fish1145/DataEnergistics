package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;

import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;

/** Registers the immutable orbital menu protocol with LDLib2 before any control surface can open. */
final class OrbitalControlUiSyncAccessors {

    private OrbitalControlUiSyncAccessors() {}

    static void register() {
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(OrbitalControlMenuSnapshot.class)
                .codec(OrbitalControlMenuSnapshot.CODEC)
                .streamCodec(OrbitalControlMenuSnapshot.STREAM_CODEC)
                .build(), 100);
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(OrbitalControlIntent.class)
                .codec(OrbitalControlIntent.CODEC)
                .streamCodec(OrbitalControlIntent.STREAM_CODEC)
                .build(), 100);
    }
}
