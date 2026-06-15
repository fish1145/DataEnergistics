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
 * @param height           current formed height, or {@code 0} when unformed
 * @param matchedPositions absolute positions matched by the last successful scan
 */
public record VerticalMultiBlockRuntimeState(boolean formed, String definitionId, int height,
                                             List<VerticalMultiBlockPos> matchedPositions) {

    public static VerticalMultiBlockRuntimeState unformed() {
        return new VerticalMultiBlockRuntimeState(false, "", 0, List.of());
    }

    public VerticalMultiBlockRuntimeState {
        if (formed) {
            if (definitionId == null || definitionId.isBlank()) {
                throw new IllegalArgumentException("Formed vertical multiblock state requires a definition id");
            }
            if (height < 2) {
                throw new IllegalArgumentException("Formed vertical multiblock state requires height >= 2");
            }
        } else {
            definitionId = "";
            height = 0;
        }
        matchedPositions = List.copyOf(matchedPositions);
    }
}
