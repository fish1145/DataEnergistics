package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.Map;

/**
 * Exact normalized carry-column equation replayed after every ojAlgo result.
 *
 * @param terms         signed integer solver-variable coefficients
 * @param rightHandSide exact column constant
 */
public record TrinityRadixColumnEquation(
                                         Map<Variable, BigInteger> terms,
                                         BigInteger rightHandSide) {}
