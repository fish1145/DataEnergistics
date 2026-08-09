package com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding;

import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerates semantically distinct pattern bindings while retaining the first legal Cartesian representative.
 *
 */
public final class TrinityPatternBindingEnumerator {

    /**
     * @return stateless exact binding enumerator shared by planning and runtime selection
     */
    public static TrinityPatternBindingEnumerator create() {
        return new TrinityPatternBindingEnumerator();
    }

    /**
     * Result of one bounded exact enumeration.
     */
    public sealed interface Result permits Enumerated, LimitExceeded, ArithmeticOverflow {}

    /**
     * @param bindings distinct bindings in first-representative Cartesian order
     */
    public record Enumerated(List<Binding> bindings) implements Result {

        /**
         * Freezes the canonical binding sequence.
         */
        public Enumerated {
            bindings = List.copyOf(bindings);
        }
    }

    /**
     * @param required proven distinct binding count already reached
     * @param limit    configured distinct binding limit
     */
    public record LimitExceeded(BigInteger required, int limit) implements Result {

        /**
         * Rejects incomplete limit diagnostics.
         */
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
    public record ArithmeticOverflow(String axis) implements Result {

        /**
         * Rejects an empty overflow diagnostic.
         */
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
    public record Binding(int cartesianOrdinal, List<Integer> alternativeOrdinals) {

        /**
         * Freezes and validates the representative choice vector.
         */
        public Binding {
            if (cartesianOrdinal < 0 || alternativeOrdinals == null ||
                    alternativeOrdinals.stream().anyMatch(index -> index == null || index < 0)) {
                throw new IllegalArgumentException("A Trinity pattern binding requires a legal representative");
            }
            alternativeOrdinals = List.copyOf(alternativeOrdinals);
        }
    }

    /**
     * Collapses assignments with identical aggregate consumption and remainder effects.
     *
     * @param inputs      ordered immutable pattern inputs
     * @param maxBindings maximum distinct effects accepted before returning a limit result
     * @return complete enumeration or an exact failure boundary
     */
    public Result enumerate(List<TrinityPatternPublicationSignature.Input> inputs, int maxBindings) {
        if (inputs == null || maxBindings <= 0) {
            throw new IllegalArgumentException("A Trinity binding enumeration requires inputs and a positive limit");
        }
        for (TrinityPatternPublicationSignature.Input input : inputs) {
            if (input == null) {
                throw new IllegalArgumentException("A Trinity binding enumeration cannot contain an empty input");
            }
        }
        List<TrinityPatternPublicationSignature.Input> orderedInputs = List.copyOf(inputs);
        BigInteger[] strides = cartesianStrides(orderedInputs);
        List<PartialBinding> current = List.of(PartialBinding.empty());
        for (int slot = 0; slot < orderedInputs.size(); slot++) {
            TrinityPatternPublicationSignature.Input input = orderedInputs.get(slot);
            LinkedHashMap<BindingEffect, PartialBinding> distinct = new LinkedHashMap<>();
            for (PartialBinding prefix : current) {
                for (int alternativeIndex = 0; alternativeIndex < input.alternatives().size(); alternativeIndex++) {
                    PartialBinding candidate = prefix.append(
                            alternativeIndex,
                            strides[slot],
                            input);
                    distinct.putIfAbsent(candidate.effect(), candidate);
                    if (distinct.size() > maxBindings) {
                        return new LimitExceeded(BigInteger.valueOf(distinct.size()), maxBindings);
                    }
                }
            }
            current = List.copyOf(distinct.values());
        }

        ArrayList<Binding> bindings = new ArrayList<>(current.size());
        try {
            for (PartialBinding binding : current) {
                bindings.add(new Binding(binding.cartesianOrdinal().intValueExact(), binding.alternatives()));
            }
        } catch (ArithmeticException overflow) {
            return new ArithmeticOverflow("cartesian_ordinal");
        }
        return new Enumerated(bindings);
    }

    private static BigInteger[] cartesianStrides(List<TrinityPatternPublicationSignature.Input> inputs) {
        BigInteger[] strides = new BigInteger[inputs.size()];
        BigInteger stride = BigInteger.ONE;
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            strides[slot] = stride;
            stride = stride.multiply(BigInteger.valueOf(inputs.get(slot).alternatives().size()));
        }
        return strides;
    }

    /**
     * One first-representative prefix and its aggregate transition effect.
     */
    private record PartialBinding(
                                  BigInteger cartesianOrdinal,
                                  List<Integer> alternatives,
                                  BindingEffect effect) {

        private static PartialBinding empty() {
            return new PartialBinding(BigInteger.ZERO, List.of(), BindingEffect.empty());
        }

        private PartialBinding append(
                                      int alternativeIndex,
                                      BigInteger stride,
                                      TrinityPatternPublicationSignature.Input input) {
            TrinityPatternPublicationSignature.Alternative alternative = input.alternatives().get(alternativeIndex);
            ArrayList<Integer> selected = new ArrayList<>(this.alternatives);
            selected.add(alternativeIndex);
            LinkedHashMap<AEKey, BigInteger> consumed = new LinkedHashMap<>(this.effect.consumed());
            consumed.merge(
                    alternative.stack().what(),
                    BigInteger.valueOf(alternative.stack().amount())
                            .multiply(BigInteger.valueOf(input.multiplier())),
                    BigInteger::add);
            LinkedHashMap<AEKey, BigInteger> remainders = new LinkedHashMap<>(this.effect.remainders());
            if (alternative.remainingKey() != null) {
                remainders.merge(
                        alternative.remainingKey(),
                        BigInteger.valueOf(input.multiplier()),
                        BigInteger::add);
            }
            return new PartialBinding(
                    this.cartesianOrdinal.add(stride.multiply(BigInteger.valueOf(alternativeIndex))),
                    List.copyOf(selected),
                    new BindingEffect(immutable(consumed), immutable(remainders)));
        }
    }

    /**
     * Exact aggregate effects used as the semantic equivalence key.
     */
    private record BindingEffect(Map<AEKey, BigInteger> consumed, Map<AEKey, BigInteger> remainders) {

        private static BindingEffect empty() {
            return new BindingEffect(Map.of(), Map.of());
        }
    }

    private static Map<AEKey, BigInteger> immutable(Map<AEKey, BigInteger> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
