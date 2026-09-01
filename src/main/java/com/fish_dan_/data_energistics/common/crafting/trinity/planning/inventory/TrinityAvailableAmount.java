package com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory;

import java.math.BigInteger;

/**
 * Exact availability of one AE key at the server-thread inventory boundary.
 *
 * <p>
 * Finite amounts remain exact {@link BigInteger} values. Unlimited is a distinct algebraic state and is never
 * encoded as {@link Long#MAX_VALUE}; callers must instead cap it by the amount useful to the current constraint.
 * </p>
 */
public sealed interface TrinityAvailableAmount
                                               permits TrinityAvailableAmount.Finite, TrinityAvailableAmount.Unlimited {

    /** Returns the amount usable by a constraint that cannot benefit from more than {@code usefulUpper}. */
    BigInteger availableUpTo(BigInteger usefulUpper);

    /** Returns whether extraction is non-consuming for this exact key. */
    boolean unlimited();

    /** Exact finite availability, including zero. */
    record Finite(BigInteger amount) implements TrinityAvailableAmount {

        @Override
        public BigInteger availableUpTo(BigInteger usefulUpper) {
            return this.amount.min(usefulUpper);
        }

        @Override
        public boolean unlimited() {
            return false;
        }
    }

    /** Non-consuming extraction source. */
    enum Unlimited implements TrinityAvailableAmount {

        INSTANCE;

        @Override
        public BigInteger availableUpTo(BigInteger usefulUpper) {
            return usefulUpper;
        }

        @Override
        public boolean unlimited() {
            return true;
        }
    }
}
