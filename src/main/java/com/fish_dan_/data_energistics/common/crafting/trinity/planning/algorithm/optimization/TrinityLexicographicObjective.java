package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import java.math.BigInteger;

/**
 * Exact four-pass objective ordered by external input, seed, firing count and stable firing identity. The identity
 * level prefers more firings on the earliest stable variant, matching the sequential solver passes without encoding
 * the vector as text or a weighted scalar.
 *
 * @param externalInput exact external-input total
 * @param seed          exact minimum-seed total
 * @param firings       exact logical firing total
 * @param identity      complete stable firing vector
 */
public record TrinityLexicographicObjective(
                                            BigInteger externalInput,
                                            BigInteger seed,
                                            BigInteger firings,
                                            TrinityFiringVector identity)
        implements Comparable<TrinityLexicographicObjective> {

    public TrinityLexicographicObjective {
        if (externalInput == null || seed == null || firings == null || identity == null ||
                externalInput.signum() < 0 || seed.signum() < 0 || firings.signum() < 0) {
            throw new IllegalArgumentException("A Trinity lexicographic objective requires non-negative exact values");
        }
    }

    @Override
    public int compareTo(TrinityLexicographicObjective other) {
        int compared = this.externalInput.compareTo(other.externalInput);
        if (compared != 0) {
            return compared;
        }
        compared = this.seed.compareTo(other.seed);
        if (compared != 0) {
            return compared;
        }
        compared = this.firings.compareTo(other.firings);
        return compared != 0 ? compared : other.identity.compareTo(this.identity);
    }
}
