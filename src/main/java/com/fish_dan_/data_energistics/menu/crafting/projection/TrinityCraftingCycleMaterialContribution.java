package com.fish_dan_.data_energistics.menu.crafting.projection;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * One material's role and exact balance data within a single repeat block.
 *
 * @param blockIndex     stable server-side repeat-block index
 * @param displayOrdinal one-based display ordinal of the owning cycle
 * @param key            material participating in the cycle
 * @param input          whether any cycle firing consumes this material
 * @param output         whether any cycle firing produces this material
 * @param minimumSeed    exact positive prefix reserve, or zero when none is required
 * @param netChange      exact signed change after all repetitions
 */
public record TrinityCraftingCycleMaterialContribution(int blockIndex,
                                                       int displayOrdinal,
                                                       AEKey key,
                                                       boolean input,
                                                       boolean output,
                                                       BigInteger minimumSeed,
                                                       BigInteger netChange) {

    /**
     * Rejects empty memberships and invalid exact values before transport or rendering.
     */
    public TrinityCraftingCycleMaterialContribution {
        int netSignum = netChange.signum();
        if (blockIndex < 0 || displayOrdinal <= 0 || minimumSeed.signum() < 0) {
            throw new IllegalArgumentException("A Trinity cycle contribution requires valid exact values");
        }
        if (!input && !output && minimumSeed.signum() == 0 && netSignum == 0) {
            throw new IllegalArgumentException("A Trinity cycle contribution must describe material participation");
        }
    }

    /**
     * @return whether the cycle both consumes and produces this material with no final balance change
     */
    public boolean reused() {
        return this.input && this.output && this.netChange.signum() == 0;
    }
}
