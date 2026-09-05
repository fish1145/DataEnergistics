package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.mojang.serialization.Codec;

/** Stable server result codes presented by the orbital control surface without synchronizing formatted text. */
public enum OrbitalControlFeedback {

    NONE,
    WEAPON_SELECTED,
    PREVIEW_REQUESTED,
    HOLD_STARTED,
    HOLD_CANCELLED,
    ATTACK_CONFIRMED,
    TASK_STOPPED,
    SOURCE_INVALID,
    PREVIEW_STALE,
    ACTION_REJECTED,
    INTERNAL_FAILURE;

    public static final Codec<OrbitalControlFeedback> CODEC = Codec.STRING.xmap(
            OrbitalControlFeedback::valueOf,
            OrbitalControlFeedback::name);

    /** Decodes a bounded wire ordinal. */
    public static OrbitalControlFeedback fromOrdinal(int ordinal) {
        OrbitalControlFeedback[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown orbital control feedback ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
