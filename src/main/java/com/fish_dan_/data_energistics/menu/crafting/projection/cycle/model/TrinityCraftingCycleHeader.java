package com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model;

import java.math.BigInteger;

/**
 * One compact repeat-block header displayed as a colored cycle.
 *
 * @param blockIndex        stable server-side repeat-block index
 * @param displayOrdinal    one-based ordinal assigned after sorting by block index
 * @param repetitions       complete repeat count
 * @param patternExecutions exact sum of stage firing counts multiplied by repetitions
 * @param stageCount        number of stages in this repeat block
 * @param patternTypeCount  number of distinct published pattern identities in this repeat block
 */
public record TrinityCraftingCycleHeader(int blockIndex,
                                         int displayOrdinal,
                                         BigInteger repetitions,
                                         BigInteger patternExecutions,
                                         int stageCount,
                                         int patternTypeCount) {

    /**
     * Rejects incomplete headers before they can be encoded or rendered.
     */
    public TrinityCraftingCycleHeader {
        if (blockIndex < 0 || displayOrdinal <= 0 || repetitions.signum() <= 0 ||
                patternExecutions.signum() <= 0 || stageCount <= 0 || patternTypeCount <= 0) {
            throw new IllegalArgumentException("A Trinity cycle header requires positive exact statistics");
        }
    }
}
