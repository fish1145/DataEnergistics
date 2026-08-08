package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reduces immutable source or sink allocations to a smaller conserved transfer total.
 */
public final class TowerEnergyAllocationLimiter {

    private TowerEnergyAllocationLimiter() {}

    /**
     * Preserves the planner's source priority while reducing withdrawals to a smaller executable total.
     *
     * @param sources        ordered positive source allocations
     * @param transferAmount non-negative FE total no greater than the original source total
     * @return ordered positive allocations whose exact total is {@code transferAmount}
     */
    public static List<TowerEnergySourceAllocation> limitSources(
                                                                 List<TowerEnergySourceAllocation> sources,
                                                                 BigInteger transferAmount) {
        BigInteger requestedTotal = sumSourceAmounts(sources);
        validateTransferAmount(transferAmount, requestedTotal);
        if (transferAmount.equals(requestedTotal)) {
            return sources;
        }
        if (transferAmount.signum() == 0) {
            return List.of();
        }

        List<TowerEnergySourceAllocation> limited = new ArrayList<>(sources.size());
        BigInteger remaining = transferAmount;
        for (TowerEnergySourceAllocation source : sources) {
            if (remaining.signum() == 0) {
                break;
            }
            long amount = BigInteger.valueOf(source.amount()).min(remaining).longValueExact();
            limited.add(new TowerEnergySourceAllocation(source.endpoint(), amount));
            remaining = remaining.subtract(BigInteger.valueOf(amount));
        }
        return List.copyOf(limited);
    }

    /**
     * Applies largest-remainder apportionment to preserve deterministic sink fairness after a source short write.
     *
     * @param sinks          ordered positive sink allocations
     * @param transferAmount non-negative FE total no greater than the original sink total
     * @return ordered positive allocations whose exact total is {@code transferAmount}
     */
    public static List<TowerEnergySinkAllocation> limitSinks(
                                                             List<TowerEnergySinkAllocation> sinks,
                                                             BigInteger transferAmount) {
        BigInteger requestedTotal = sumSinkAmounts(sinks);
        validateTransferAmount(transferAmount, requestedTotal);
        if (transferAmount.equals(requestedTotal)) {
            return sinks;
        }
        if (transferAmount.signum() == 0) {
            return List.of();
        }

        long[] amounts = new long[sinks.size()];
        List<FractionalShare> shares = new ArrayList<>(sinks.size());
        BigInteger floorTotal = BigInteger.ZERO;
        for (int index = 0; index < sinks.size(); index++) {
            TowerEnergySinkAllocation sink = sinks.get(index);
            BigInteger numerator = transferAmount.multiply(BigInteger.valueOf(sink.amount()));
            BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(requestedTotal);
            amounts[index] = quotientAndRemainder[0].longValueExact();
            floorTotal = floorTotal.add(quotientAndRemainder[0]);
            shares.add(new FractionalShare(index, quotientAndRemainder[1]));
        }

        int leftover = transferAmount.subtract(floorTotal).intValueExact();
        shares.sort(Comparator.comparing(FractionalShare::remainder).reversed()
                .thenComparingInt(FractionalShare::order));
        for (int index = 0; index < leftover; index++) {
            int sinkIndex = shares.get(index).order();
            amounts[sinkIndex] = Math.addExact(amounts[sinkIndex], 1);
        }

        List<TowerEnergySinkAllocation> limited = new ArrayList<>(sinks.size());
        for (int index = 0; index < sinks.size(); index++) {
            if (amounts[index] > 0) {
                limited.add(new TowerEnergySinkAllocation(sinks.get(index).endpoint(), amounts[index]));
            }
        }
        return List.copyOf(limited);
    }

    /**
     * Verifies that a reduced total remains inside the original conserved allocation.
     */
    private static void validateTransferAmount(BigInteger transferAmount, BigInteger requestedTotal) {
        if (transferAmount.signum() < 0 || transferAmount.compareTo(requestedTotal) > 0) {
            throw new IllegalArgumentException("Transfer amount must remain within the original allocation");
        }
    }

    /**
     * Adds positive source requests without aggregate overflow.
     */
    private static BigInteger sumSourceAmounts(List<TowerEnergySourceAllocation> sources) {
        BigInteger total = BigInteger.ZERO;
        for (TowerEnergySourceAllocation source : sources) {
            total = total.add(BigInteger.valueOf(source.amount()));
        }
        return total;
    }

    /**
     * Adds positive sink requests without aggregate overflow.
     */
    private static BigInteger sumSinkAmounts(List<TowerEnergySinkAllocation> sinks) {
        BigInteger total = BigInteger.ZERO;
        for (TowerEnergySinkAllocation sink : sinks) {
            total = total.add(BigInteger.valueOf(sink.amount()));
        }
        return total;
    }

    /**
     * Associates a sink position with its exact fractional allocation remainder.
     */
    private record FractionalShare(int order, BigInteger remainder) {}
}
