package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import java.math.BigInteger;
import java.util.Map;

/**
 * Exact logical equation retained for the finite overflow-proof domain derivation.
 *
 * @param terms         signed logical-variable coefficients
 * @param rightHandSide exact logical constant
 */
public record TrinityRadixLogicalEquation(
                                          Map<TrinityRadixVariable, BigInteger> terms,
                                          BigInteger rightHandSide) {}
