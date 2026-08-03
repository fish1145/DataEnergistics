package com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding;

import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import java.math.BigInteger;
import java.util.List;

/**
 * Enumerates semantically distinct pattern bindings while retaining the first legal Cartesian representative.
 */
public interface TrinityPatternBindingEnumerator {

    /**
     * @return stateless exact binding enumerator shared by planning and runtime selection
     */
    static TrinityPatternBindingEnumerator create() {
        return new TrinityPatternBindingEnumeratorImpl();
    }

    /**
     * Collapses assignments with identical aggregate consumption and remainder effects.
     *
     * @param inputs      ordered immutable pattern inputs
     * @param maxBindings maximum distinct effects accepted before returning a limit result
     * @return complete enumeration or an exact failure boundary
     */
    Result enumerate(List<TrinityPatternPublicationSignature.Input> inputs, int maxBindings);

    /** Result of one bounded exact enumeration. */
    sealed interface Result permits Enumerated, LimitExceeded, ArithmeticOverflow {}

    /**
     * @param bindings distinct bindings in first-representative Cartesian order
     */
    record Enumerated(List<Binding> bindings) implements Result {

        /** Freezes the canonical binding sequence. */
        public Enumerated {
            bindings = List.copyOf(bindings);
        }
    }

    /**
     * @param required proven distinct binding count already reached
     * @param limit    configured distinct binding limit
     */
    record LimitExceeded(BigInteger required, int limit) implements Result {

        /** Rejects incomplete limit diagnostics. */
        public LimitExceeded {
            if (required == null || required.signum() <= 0 || limit <= 0 ||
                    required.compareTo(BigInteger.valueOf(limit)) <= 0) {
                throw new IllegalArgumentException("A Trinity binding limit result requires an exceeded positive bound");
            }
        }
    }

    /**
     * @param axis exact internal representation that exceeded the executable plan domain
     */
    record ArithmeticOverflow(String axis) implements Result {

        /** Rejects an empty overflow diagnostic. */
        public ArithmeticOverflow {
            if (axis == null || axis.isBlank()) {
                throw new IllegalArgumentException("A Trinity binding overflow requires its arithmetic axis");
            }
        }
    }

    /**
     * @param cartesianOrdinal    first raw Cartesian ordinal with this aggregate effect
     * @param alternativeOrdinals selected alternative index for every ordered input slot
     */
    record Binding(int cartesianOrdinal, List<Integer> alternativeOrdinals) {

        /** Freezes and validates the representative choice vector. */
        public Binding {
            if (cartesianOrdinal < 0 || alternativeOrdinals == null ||
                    alternativeOrdinals.stream().anyMatch(index -> index == null || index < 0)) {
                throw new IllegalArgumentException("A Trinity pattern binding requires a legal representative");
            }
            alternativeOrdinals = List.copyOf(alternativeOrdinals);
        }
    }
}
