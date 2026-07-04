package com.fish_dan_.data_energistics.common.compartment;

import java.util.Locale;
import java.util.Optional;

/**
 * Describes the role a compartment block can provide inside a formed multiblock.
 *
 * <p>The id is the stable JSON metadata value used by multiblock definitions.
 */
public enum CompartmentType {

    INPUT("input", false),
    OUTPUT("output", false),
    ME_INPUT("me_input", true),
    ME_OUTPUT("me_output", true),
    PATTERN_BUFFER("pattern_buffer", false);

    private final String id;
    private final boolean aeCapable;

    CompartmentType(String id, boolean aeCapable) {
        this.id = id;
        this.aeCapable = aeCapable;
    }

    /**
     * Returns the JSON metadata id for this compartment role.
     */
    public String id() {
        return this.id;
    }

    /**
     * Returns whether this compartment type is allowed to expose AE network connectivity.
     */
    public boolean aeCapable() {
        return this.aeCapable;
    }

    /**
     * Resolves a JSON metadata id into a compartment type.
     */
    public static Optional<CompartmentType> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (CompartmentType type : values()) {
            if (type.id.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
