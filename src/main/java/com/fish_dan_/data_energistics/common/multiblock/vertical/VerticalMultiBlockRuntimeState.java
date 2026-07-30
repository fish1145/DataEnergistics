package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.List;

/**
 * Immutable runtime snapshot for one vertical multiblock controller.
 *
 * <p>
 * The runtime state stores the formed flag, the active structure id, the current height, and the absolute positions
 * matched by the last successful scan. This keeps controller implementations simple and makes state transitions easy
 * to test.
 *
 * @param formed           whether the structure is currently valid
 * @param definitionId     formed structure id, or blank when unformed
 * @param structureName    formed structure name, or blank when unformed
 * @param height           current formed height, or {@code 0} when unformed
 * @param matchedPositions absolute positions matched by the last successful scan
 * @param bindingEpoch     monotonic runtime identity for the matching formation callback set
 */
public record VerticalMultiBlockRuntimeState(boolean formed, String definitionId, String structureName, int height,
                                             List<VerticalMultiBlockPos> matchedPositions,
                                             long bindingEpoch) {

    public static VerticalMultiBlockRuntimeState unformed() {
        return unformed(0L);
    }

    /**
     * Creates an unformed state while retaining the last callback epoch for the next formation.
     */
    public static VerticalMultiBlockRuntimeState unformed(long bindingEpoch) {
        return new VerticalMultiBlockRuntimeState(false, "", "", 0, List.of(), bindingEpoch);
    }

    public String sectionName() {
        return this.structureName;
    }

    public VerticalMultiBlockRuntimeState(boolean formed,
                                          String definitionId,
                                          int height,
                                          List<VerticalMultiBlockPos> matchedPositions) {
        this(formed, definitionId, VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME, height, matchedPositions, 0L);
    }

    /**
     * Creates a named runtime state with an explicit callback identity.
     */
    public VerticalMultiBlockRuntimeState(boolean formed,
                                          String definitionId,
                                          String structureName,
                                          int height,
                                          List<VerticalMultiBlockPos> matchedPositions) {
        this(formed, definitionId, structureName, height, matchedPositions, 0L);
    }

    public VerticalMultiBlockRuntimeState {
        if (bindingEpoch < 0L) {
            throw new IllegalArgumentException("Vertical multiblock binding epoch must not be negative");
        }
        if (formed) {
            if (definitionId == null || definitionId.isBlank()) {
                throw new IllegalArgumentException("Formed vertical multiblock state requires a definition id");
            }
            if (structureName == null || structureName.isBlank()) {
                throw new IllegalArgumentException("Formed vertical multiblock state requires a structure name");
            }
            if (height < 2) {
                throw new IllegalArgumentException("Formed vertical multiblock state requires height >= 2");
            }
        } else {
            definitionId = "";
            structureName = "";
            height = 0;
        }
        matchedPositions = List.copyOf(matchedPositions);
    }
}
