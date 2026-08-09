package com.fish_dan_.data_energistics.common.trinity.autobuild;

/**
 * Immutable request model shared by the Trinity auto-build UI and future server-side build planner.
 *
 * @param structureIndex selected generic structure button index
 * @param options        user-selected build behavior and predicate category tiers
 */
public record TrinityAutoBuildRequest(int structureIndex, TrinityAutoBuildOptions options) {

    /**
     * Main structure selector index; it is displayable even when main auto-build is not enabled by a server handler.
     */
    public static final int MAIN_STRUCTURE_INDEX = 0;
    /** CPU child structure selector index. */
    public static final int CPU_STRUCTURE_INDEX = 1;
    /** Crafting child structure selector index. */
    public static final int CRAFTING_STRUCTURE_INDEX = 2;
    /** Highest structure index currently declared by the Trinity host. */
    public static final int MAX_STRUCTURE_INDEX = CRAFTING_STRUCTURE_INDEX;

    /**
     * Rejects selector indexes that do not resolve to one of the host's declared structures.
     */
    public TrinityAutoBuildRequest {
        if (structureIndex < MAIN_STRUCTURE_INDEX || structureIndex > MAX_STRUCTURE_INDEX) {
            throw new IllegalArgumentException("Trinity auto-build structure index must be between " +
                    MAIN_STRUCTURE_INDEX + " and " + MAX_STRUCTURE_INDEX + ": " + structureIndex);
        }
    }
}
