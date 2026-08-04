package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One non-negative logical value represented by least-significant-first base-2^15 integer digits.
 *
 * @param name       stable logical axis name
 * @param digits     ojAlgo digit variables
 * @param upperBound exact logical upper bound
 */
public record TrinityRadixVariable(String name, List<Variable> digits, BigInteger upperBound) {

    /**
     * Reconstructs and bounds-checks the exact logical value from decoded digit assignments.
     */
    public BigInteger decode(Map<Variable, BigInteger> values) {
        ArrayList<Integer> decoded = new ArrayList<>(digits.size());
        for (Variable digit : digits) {
            decoded.add(values.get(digit).intValueExact());
        }
        BigInteger value = new TrinityRadixDigits(decoded).value();
        if (value.compareTo(upperBound) > 0) {
            throw new IllegalStateException("A verified Trinity radix value exceeds its exact upper bound");
        }
        return value;
    }
}
