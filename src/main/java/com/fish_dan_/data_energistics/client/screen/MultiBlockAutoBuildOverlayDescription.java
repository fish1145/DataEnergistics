package com.fish_dan_.data_energistics.client.screen;

import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Immutable host-provided content for one shared multiblock auto-build overlay.
 *
 * <p>
 * Each host supplies only its selectable structures, tier meanings, localized labels, and confirmation action. The
 * overlay itself therefore remains independent from any individual machine, network payload, or multiblock library.
 * </p>
 *
 * @param title                localized overlay title
 * @param structures           ordered structures shown by the overlay
 * @param confirmationConsumer host action receiving the confirmed immutable selection
 */
public record MultiBlockAutoBuildOverlayDescription(Component title,
                                                    List<Structure> structures,
                                                    Consumer<MultiBlockAutoBuildSelection> confirmationConsumer) {

    /** Lowest icon slot exposed by the shared multiblock-builder texture. */
    public static final int MIN_STRUCTURE_ICON_INDEX = 0;
    /** Highest icon slot exposed by the shared multiblock-builder texture. */
    public static final int MAX_STRUCTURE_ICON_INDEX = 4;

    /** Copies and validates structure metadata before client interaction begins. */
    public MultiBlockAutoBuildOverlayDescription {
        structures = List.copyOf(structures);
        if (structures.isEmpty()) {
            throw new IllegalArgumentException("Multiblock auto-build overlay requires at least one structure");
        }
        if (structures.size() > MAX_STRUCTURE_ICON_INDEX - MIN_STRUCTURE_ICON_INDEX + 1) {
            throw new IllegalArgumentException("Multiblock auto-build overlay supports at most " +
                    (MAX_STRUCTURE_ICON_INDEX - MIN_STRUCTURE_ICON_INDEX + 1) + " structures");
        }

        Set<Integer> structureIds = new HashSet<>();
        for (Structure structure : structures) {
            if (!structureIds.add(structure.id())) {
                throw new IllegalArgumentException("Duplicate multiblock auto-build structure id: " + structure.id());
            }
        }
    }

    /**
     * Resolves one host structure by its stable identifier.
     *
     * @param structureId host-defined identifier
     * @return declared structure metadata
     */
    public Structure structure(int structureId) {
        for (Structure structure : this.structures) {
            if (structure.id() == structureId) {
                return structure;
            }
        }
        throw new IllegalArgumentException("Unknown multiblock auto-build structure id: " + structureId);
    }

    /**
     * Immutable UI metadata for one host-defined structure.
     *
     * @param id                      stable host-defined structure identifier
     * @param iconIndex               shared texture icon slot in the inclusive range 0 to 4
     * @param label                   localized structure tooltip label
     * @param tierLabel               localized tier field label
     * @param minimumRepeatCount      lowest valid repetition count
     * @param maximumRepeatCount      highest valid repetition count; equal bounds hide the repeat control
     * @param tierOptions             ordered host-defined tier choices
     * @param buildRequestedByDefault whether this structure submits an executable build until the user changes it
     */
    public record Structure(int id,
                            int iconIndex,
                            Component label,
                            Component tierLabel,
                            int minimumRepeatCount,
                            int maximumRepeatCount,
                            List<TierOption> tierOptions,
                            boolean buildRequestedByDefault) {

        /**
         * Creates a structure whose build confirmation defaults to enabled.
         *
         * <p>
         * Existing generic hosts retain their executable-default behavior while hosts with optional child structures
         * can explicitly opt those children out through the canonical constructor.
         * </p>
         */
        public Structure(int id,
                         int iconIndex,
                         Component label,
                         Component tierLabel,
                         int minimumRepeatCount,
                         int maximumRepeatCount,
                         List<TierOption> tierOptions) {
            this(id, iconIndex, label, tierLabel, minimumRepeatCount, maximumRepeatCount, tierOptions, true);
        }

        /** Copies and validates the selectable tier metadata for one structure. */
        public Structure {
            if (id < 0) {
                throw new IllegalArgumentException("Multiblock auto-build structure id cannot be negative: " + id);
            }
            if (iconIndex < MIN_STRUCTURE_ICON_INDEX || iconIndex > MAX_STRUCTURE_ICON_INDEX) {
                throw new IllegalArgumentException("Multiblock auto-build icon index must be between " +
                        MIN_STRUCTURE_ICON_INDEX + " and " + MAX_STRUCTURE_ICON_INDEX + ": " + iconIndex);
            }
            if (minimumRepeatCount < 1 || maximumRepeatCount < minimumRepeatCount) {
                throw new IllegalArgumentException("Invalid multiblock auto-build repeat bounds: " +
                        minimumRepeatCount + ".." + maximumRepeatCount);
            }

            tierOptions = List.copyOf(tierOptions);
            if (tierOptions.isEmpty()) {
                throw new IllegalArgumentException("Multiblock auto-build structure " + id + " requires at least one tier");
            }
            Set<Integer> tierValues = new HashSet<>();
            for (TierOption tierOption : tierOptions) {
                if (!tierValues.add(tierOption.value())) {
                    throw new IllegalArgumentException("Duplicate multiblock auto-build tier value " + tierOption.value() +
                            " for structure " + id);
                }
            }
        }

        /** Returns whether this structure exposes a configurable repeat count. */
        public boolean repeatable() {
            return this.minimumRepeatCount < this.maximumRepeatCount;
        }

        /**
         * Resolves one declared tier by its stable host-defined value.
         *
         * @param value host-defined tier value
         * @return declared tier metadata
         */
        public TierOption tier(int value) {
            for (TierOption tierOption : this.tierOptions) {
                if (tierOption.value() == value) {
                    return tierOption;
                }
            }
            throw new IllegalArgumentException("Unknown multiblock auto-build tier value " + value + " for structure " +
                    this.id);
        }
    }

    /**
     * Immutable host-defined tier entry shown by the overlay.
     *
     * @param value stable positive value returned in confirmed selections
     * @param label localized text rendered for this tier
     */
    public record TierOption(int value, Component label) {

        /** Rejects non-positive values that could not form a valid generic selection. */
        public TierOption {
            if (value < 1) {
                throw new IllegalArgumentException("Multiblock auto-build tier value must be positive: " + value);
            }
        }
    }
}
