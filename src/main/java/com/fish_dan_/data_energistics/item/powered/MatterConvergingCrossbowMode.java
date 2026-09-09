package com.fish_dan_.data_energistics.item.powered;

import org.jspecify.annotations.Nullable;

/** Persistent firing configuration for the Darkstring Data Settlement Tool. */
public enum MatterConvergingCrossbowMode {

    GRENADE(0, "star_shard"),
    RAIL(1, "crystal_rift_crusher"),
    CROSSBOW(2, "entropy_thread_bow");

    private final int id;
    private final String name;

    MatterConvergingCrossbowMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int id() {
        return this.id;
    }

    public String nameKey() {
        return this.name;
    }

    public static MatterConvergingCrossbowMode fromId(int id) {
        for (MatterConvergingCrossbowMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return GRENADE;
    }

    public static @Nullable MatterConvergingCrossbowMode tryParse(String name) {
        for (MatterConvergingCrossbowMode mode : values()) {
            if (mode.name.equals(name)) return mode;
        }
        return null;
    }
}
