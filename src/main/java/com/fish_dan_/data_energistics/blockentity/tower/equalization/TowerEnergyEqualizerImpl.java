package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import com.fish_dan_.data_energistics.blockentity.tower.TowerEnergyDirection;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact integer water-filling planner for tower FE equalization.
 *
 * <p>
 * Receive-only endpoints contribute an immovable lower bound, bidirectional endpoints may move only their surplus
 * above the resulting target, and source-only energy is consumed before bidirectional surplus. Proportional math uses
 * checked primitive {@code long} arithmetic whenever every intermediate value fits, with the exact {@link BigInteger}
 * implementation retained as an overflow fallback. Both paths use largest-remainder apportionment and snapshot-order
 * tie breaking for deterministic FE rounding.
 * </p>
 */
public final class TowerEnergyEqualizerImpl implements TowerEnergyEqualizer {

    /** Numeric zero reused by the exact aggregate calculations. */
    private static final BigInteger ZERO = BigInteger.ZERO;

    /**
     * Computes target receiver states, then converts their deficits and surpluses into a conserved two-phase plan.
     *
     * @param snapshot ordered endpoint state captured before any transfer begins
     * @return immutable source and sink allocations
     */
    @Override
    public TowerEnergyEqualizationPlan plan(TowerEnergyEqualizationSnapshot snapshot) {
        try {
            return planLong(snapshot);
        } catch (ArithmeticException overflow) {
            return planExact(snapshot);
        }
    }

    /**
     * Attempts the complete equalization calculation with primitive non-negative {@code long} values.
     *
     * <p>
     * Every aggregate and proportional product is checked before it is used. An arithmetic overflow means that
     * this path is not applicable to the snapshot; the caller then retries the unchanged exact calculation.
     * </p>
     *
     * @param snapshot ordered endpoint state captured before any transfer begins
     * @return immutable source and sink allocations when all intermediate values fit in {@code long}
     * @throws ArithmeticException when an intermediate aggregate or product cannot be represented exactly
     */
    private static TowerEnergyEqualizationPlan planLong(TowerEnergyEqualizationSnapshot snapshot) {
        List<TowerEnergyEndpointSnapshot> endpoints = snapshot.endpoints();
        List<ReceiverState> receivers = collectReceivers(endpoints);
        if (receivers.isEmpty()) {
            return TowerEnergyEqualizationPlan.empty();
        }

        long receiverCapacity = sumReceiverCapacityLong(receivers);
        long receiverStored = sumReceiverStoredLong(receivers);
        long sourceStored = sumSourceStoredLong(endpoints);
        long desiredReceiverStored = Math.min(Math.addExact(receiverStored, sourceStored), receiverCapacity);
        Map<TowerEnergyEndpointId, Long> targets = apportionReceiverTargetsLong(receivers, desiredReceiverStored);

        List<TowerEnergySinkAllocation> sinks = collectSinkAllocationsLong(endpoints, targets);
        long amountNeeded = sumSinkAmountsLong(sinks);
        if (amountNeeded == 0) {
            return TowerEnergyEqualizationPlan.empty();
        }

        List<TowerEnergySourceAllocation> sources = new ArrayList<>();
        long unavailable = collectSourceOnlyAllocationsLong(endpoints, amountNeeded, sources);
        unavailable = collectBidirectionalAllocationsLong(endpoints, targets, unavailable, sources);
        long transferAmount = Math.subtractExact(amountNeeded, unavailable);
        return transferAmount == 0 ? TowerEnergyEqualizationPlan.empty() : new TowerEnergyEqualizationPlan(
                sources, TowerEnergyAllocationLimiter.limitSinks(sinks, BigInteger.valueOf(transferAmount)));
    }

    /**
     * Runs the original arbitrary-precision equalization calculation.
     *
     * @param snapshot ordered endpoint state captured before any transfer begins
     * @return immutable source and sink allocations
     */
    private static TowerEnergyEqualizationPlan planExact(TowerEnergyEqualizationSnapshot snapshot) {
        List<TowerEnergyEndpointSnapshot> endpoints = snapshot.endpoints();
        List<ReceiverState> receivers = collectReceivers(endpoints);
        if (receivers.isEmpty()) {
            return TowerEnergyEqualizationPlan.empty();
        }

        BigInteger receiverCapacity = sumReceiverCapacity(receivers);
        BigInteger receiverStored = sumReceiverStored(receivers);
        BigInteger sourceStored = sumSourceStored(endpoints);
        BigInteger desiredReceiverStored = receiverStored.add(sourceStored).min(receiverCapacity);
        Map<TowerEnergyEndpointId, Long> targets = apportionReceiverTargets(receivers, desiredReceiverStored);

        List<TowerEnergySinkAllocation> sinks = collectSinkAllocations(endpoints, targets);
        BigInteger amountNeeded = sumSinkAmounts(sinks);
        if (amountNeeded.signum() == 0) {
            return TowerEnergyEqualizationPlan.empty();
        }

        List<TowerEnergySourceAllocation> sources = new ArrayList<>();
        BigInteger unavailable = collectSourceOnlyAllocations(endpoints, amountNeeded, sources);
        unavailable = collectBidirectionalAllocations(endpoints, targets, unavailable, sources);
        BigInteger transferAmount = amountNeeded.subtract(unavailable);
        return transferAmount.signum() == 0 ? TowerEnergyEqualizationPlan.empty() : new TowerEnergyEqualizationPlan(sources, TowerEnergyAllocationLimiter.limitSinks(sinks, transferAmount));
    }

    /**
     * Adds receiver capacities with checked primitive arithmetic.
     *
     * @param receivers receive-capable endpoint states
     * @return total capacity when representable as a non-negative {@code long}
     */
    private static long sumReceiverCapacityLong(List<ReceiverState> receivers) {
        long total = 0;
        for (ReceiverState receiver : receivers) {
            total = Math.addExact(total, receiver.endpoint().capacity());
        }
        return total;
    }

    /**
     * Adds receiver contents with checked primitive arithmetic.
     *
     * @param receivers receive-capable endpoint states
     * @return total stored FE when representable as a non-negative {@code long}
     */
    private static long sumReceiverStoredLong(List<ReceiverState> receivers) {
        long total = 0;
        for (ReceiverState receiver : receivers) {
            total = Math.addExact(total, receiver.endpoint().stored());
        }
        return total;
    }

    /**
     * Adds source-only contents with checked primitive arithmetic.
     *
     * @param endpoints complete ordered snapshot
     * @return total source-only FE when representable as a non-negative {@code long}
     */
    private static long sumSourceStoredLong(List<TowerEnergyEndpointSnapshot> endpoints) {
        long total = 0;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (endpoint.direction() == TowerEnergyDirection.SOURCE) {
                total = Math.addExact(total, endpoint.stored());
            }
        }
        return total;
    }

    /**
     * Applies proportional water filling with checked primitive arithmetic.
     *
     * @param receivers    ordered receiver states
     * @param desiredTotal total FE that receivers may hold after equalization
     * @return target stored FE keyed by receiver identity
     */
    private static Map<TowerEnergyEndpointId, Long> apportionReceiverTargetsLong(List<ReceiverState> receivers,
                                                                                 long desiredTotal) {
        Map<TowerEnergyEndpointId, Long> targets = new HashMap<>();
        List<ReceiverState> active = new ArrayList<>();
        for (ReceiverState receiver : receivers) {
            if (receiver.endpoint().capacity() == 0) {
                targets.put(receiver.endpoint().endpoint(), 0L);
            } else {
                active.add(receiver);
            }
        }

        long remainingEnergy = desiredTotal;
        while (!active.isEmpty()) {
            long activeCapacity = sumReceiverCapacityLong(active);
            List<ReceiverState> clamped = findLowerBoundViolationsLong(active, remainingEnergy, activeCapacity);
            if (clamped.isEmpty()) {
                apportionActiveTargetsLong(active, remainingEnergy, activeCapacity, targets);
                remainingEnergy = 0;
                break;
            }
            for (ReceiverState receiver : clamped) {
                targets.put(receiver.endpoint().endpoint(), receiver.lowerBound());
                remainingEnergy = Math.subtractExact(remainingEnergy, receiver.lowerBound());
                if (remainingEnergy < 0) {
                    throw new ArithmeticException("Receiver lower bounds exceed the desired energy total");
                }
            }
            active.removeAll(clamped);
        }
        if (remainingEnergy != 0) {
            throw new ArithmeticException("Receiver water filling did not allocate the desired energy total");
        }
        return targets;
    }

    /**
     * Finds lower-bound violations without allowing either comparison product to wrap.
     *
     * @param active          active positive-capacity receivers
     * @param remainingEnergy FE still to distribute among active receivers
     * @param activeCapacity  capacity sum of active receivers
     * @return stable-order receivers that must be fixed at their lower bound
     */
    private static List<ReceiverState> findLowerBoundViolationsLong(List<ReceiverState> active,
                                                                    long remainingEnergy,
                                                                    long activeCapacity) {
        List<ReceiverState> clamped = new ArrayList<>();
        for (ReceiverState receiver : active) {
            long lowerShare = Math.multiplyExact(receiver.lowerBound(), activeCapacity);
            long proportionalShare = Math.multiplyExact(remainingEnergy, receiver.endpoint().capacity());
            if (lowerShare > proportionalShare) {
                clamped.add(receiver);
            }
        }
        return clamped;
    }

    /**
     * Floors proportional shares, then applies the exact largest-remainder tie order using primitive remainders.
     *
     * @param active          active receivers sharing one water line
     * @param remainingEnergy FE assigned to the active receivers
     * @param activeCapacity  capacity sum of the active receivers
     * @param targets         mutable target result map
     */
    private static void apportionActiveTargetsLong(List<ReceiverState> active,
                                                   long remainingEnergy,
                                                   long activeCapacity,
                                                   Map<TowerEnergyEndpointId, Long> targets) {
        List<LongFractionalShare> shares = new ArrayList<>();
        long floorTotal = 0;
        for (ReceiverState receiver : active) {
            long numerator = Math.multiplyExact(remainingEnergy, receiver.endpoint().capacity());
            long target = numerator / activeCapacity;
            long remainder = numerator % activeCapacity;
            targets.put(receiver.endpoint().endpoint(), target);
            floorTotal = Math.addExact(floorTotal, target);
            shares.add(new LongFractionalShare(receiver, remainder));
        }

        long leftover = Math.subtractExact(remainingEnergy, floorTotal);
        if (leftover < 0) {
            throw new ArithmeticException("Proportional floors exceed the remaining energy total");
        }
        int leftoverCount = Math.toIntExact(leftover);
        shares.sort(Comparator.comparingLong(LongFractionalShare::remainder).reversed()
                .thenComparingInt(share -> share.receiver().order()));
        for (int index = 0; index < leftoverCount; index++) {
            ReceiverState receiver = shares.get(index).receiver();
            TowerEnergyEndpointId endpoint = receiver.endpoint().endpoint();
            targets.compute(endpoint, (ignored, target) -> incrementTarget(target));
        }
    }

    /**
     * Creates positive deposits for receiver targets with checked subtraction.
     *
     * @param endpoints complete ordered snapshot
     * @param targets   calculated receiver target contents
     * @return stable-order sink allocations
     */
    private static List<TowerEnergySinkAllocation> collectSinkAllocationsLong(
                                                                              List<TowerEnergyEndpointSnapshot> endpoints, Map<TowerEnergyEndpointId, Long> targets) {
        List<TowerEnergySinkAllocation> sinks = new ArrayList<>();
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            Long target = targets.get(endpoint.endpoint());
            if (target != null && target > endpoint.stored()) {
                long amount = Math.min(
                        Math.subtractExact(target, endpoint.stored()), endpoint.receivable());
                if (amount > 0) {
                    sinks.add(new TowerEnergySinkAllocation(endpoint.endpoint(), amount));
                }
            }
        }
        return sinks;
    }

    /**
     * Adds planned sink deposits with checked primitive arithmetic.
     *
     * @param sinks planned positive deposits
     * @return total FE required by all sinks
     */
    private static long sumSinkAmountsLong(List<TowerEnergySinkAllocation> sinks) {
        long total = 0;
        for (TowerEnergySinkAllocation sink : sinks) {
            total = Math.addExact(total, sink.amount());
        }
        return total;
    }

    /**
     * Consumes source-only energy first in snapshot order using primitive remaining demand.
     *
     * @param endpoints    complete ordered snapshot
     * @param amountNeeded positive FE still required by sinks
     * @param sources      mutable source allocation result
     * @return FE still required after source-only allocations
     */
    private static long collectSourceOnlyAllocationsLong(List<TowerEnergyEndpointSnapshot> endpoints,
                                                         long amountNeeded,
                                                         List<TowerEnergySourceAllocation> sources) {
        long remaining = amountNeeded;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (remaining == 0) {
                break;
            }
            if (endpoint.direction() != TowerEnergyDirection.SOURCE || endpoint.stored() == 0) {
                continue;
            }
            long amount = Math.min(Math.min(endpoint.stored(), endpoint.extractable()), remaining);
            if (amount > 0) {
                sources.add(new TowerEnergySourceAllocation(endpoint.endpoint(), amount));
                remaining = Math.subtractExact(remaining, amount);
            }
        }
        return remaining;
    }

    /**
     * Consumes bidirectional surplus after source-only energy, preserving snapshot order.
     *
     * @param endpoints    complete ordered snapshot
     * @param targets      calculated receiver target contents
     * @param amountNeeded positive FE still required by sinks
     * @param sources      mutable source allocation result
     * @return FE still required after bidirectional allocations
     */
    private static long collectBidirectionalAllocationsLong(List<TowerEnergyEndpointSnapshot> endpoints,
                                                            Map<TowerEnergyEndpointId, Long> targets,
                                                            long amountNeeded,
                                                            List<TowerEnergySourceAllocation> sources) {
        long remaining = amountNeeded;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (remaining == 0) {
                break;
            }
            if (endpoint.direction() != TowerEnergyDirection.BIDIRECTIONAL) {
                continue;
            }
            long target = targets.get(endpoint.endpoint());
            long surplus = Math.subtractExact(endpoint.stored(), target);
            if (surplus <= 0) {
                continue;
            }
            long amount = Math.min(Math.min(surplus, endpoint.extractable()), remaining);
            if (amount > 0) {
                sources.add(new TowerEnergySourceAllocation(endpoint.endpoint(), amount));
                remaining = Math.subtractExact(remaining, amount);
            }
        }
        return remaining;
    }

    /**
     * Selects receive-capable endpoints while retaining their snapshot positions for stable rounding.
     *
     * @param endpoints complete ordered snapshot
     * @return ordered receiver calculation states
     */
    private static List<ReceiverState> collectReceivers(List<TowerEnergyEndpointSnapshot> endpoints) {
        List<ReceiverState> receivers = new ArrayList<>();
        for (int index = 0; index < endpoints.size(); index++) {
            TowerEnergyEndpointSnapshot endpoint = endpoints.get(index);
            if (endpoint.direction().allowsReceive()) {
                long lowerBound = endpoint.direction() == TowerEnergyDirection.SINK ? endpoint.stored() : 0;
                receivers.add(new ReceiverState(endpoint, index, lowerBound));
            }
        }
        return receivers;
    }

    /**
     * Adds receiver capacities without aggregate overflow.
     *
     * @param receivers receive-capable endpoint states
     * @return exact total capacity
     */
    private static BigInteger sumReceiverCapacity(List<ReceiverState> receivers) {
        BigInteger total = ZERO;
        for (ReceiverState receiver : receivers) {
            total = total.add(BigInteger.valueOf(receiver.endpoint().capacity()));
        }
        return total;
    }

    /**
     * Adds current receiver contents without aggregate overflow.
     *
     * @param receivers receive-capable endpoint states
     * @return exact total stored FE
     */
    private static BigInteger sumReceiverStored(List<ReceiverState> receivers) {
        BigInteger total = ZERO;
        for (ReceiverState receiver : receivers) {
            total = total.add(BigInteger.valueOf(receiver.endpoint().stored()));
        }
        return total;
    }

    /**
     * Adds the external energy available from source-only endpoints.
     *
     * @param endpoints complete ordered snapshot
     * @return exact source-only FE total
     */
    private static BigInteger sumSourceStored(List<TowerEnergyEndpointSnapshot> endpoints) {
        BigInteger total = ZERO;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (endpoint.direction() == TowerEnergyDirection.SOURCE) {
                total = total.add(BigInteger.valueOf(endpoint.stored()));
            }
        }
        return total;
    }

    /**
     * Applies proportional water filling under immutable receive-only lower bounds.
     *
     * @param receivers    ordered receiver states
     * @param desiredTotal exact total FE that receivers can hold after equalization
     * @return target stored FE keyed by receiver identity
     */
    private static Map<TowerEnergyEndpointId, Long> apportionReceiverTargets(
                                                                             List<ReceiverState> receivers, BigInteger desiredTotal) {
        Map<TowerEnergyEndpointId, Long> targets = new HashMap<>();
        List<ReceiverState> active = new ArrayList<>();
        for (ReceiverState receiver : receivers) {
            if (receiver.endpoint().capacity() == 0) {
                targets.put(receiver.endpoint().endpoint(), 0L);
            } else {
                active.add(receiver);
            }
        }

        BigInteger remainingEnergy = desiredTotal;
        while (!active.isEmpty()) {
            BigInteger activeCapacity = sumReceiverCapacity(active);
            List<ReceiverState> clamped = findLowerBoundViolations(active, remainingEnergy, activeCapacity);
            if (clamped.isEmpty()) {
                apportionActiveTargets(active, remainingEnergy, activeCapacity, targets);
                remainingEnergy = ZERO;
                break;
            }
            for (ReceiverState receiver : clamped) {
                targets.put(receiver.endpoint().endpoint(), receiver.lowerBound());
                remainingEnergy = remainingEnergy.subtract(BigInteger.valueOf(receiver.lowerBound()));
            }
            active.removeAll(clamped);
        }
        if (remainingEnergy.signum() != 0) {
            throw new IllegalStateException("Receiver water filling did not allocate the desired energy total");
        }
        return targets;
    }

    /**
     * Finds endpoints whose non-extractable contents exceed the current proportional water line.
     *
     * @param active          active positive-capacity receivers
     * @param remainingEnergy FE still to distribute among active receivers
     * @param activeCapacity  capacity sum of active receivers
     * @return stable-order receivers that must be fixed at their lower bound
     */
    private static List<ReceiverState> findLowerBoundViolations(List<ReceiverState> active,
                                                                BigInteger remainingEnergy,
                                                                BigInteger activeCapacity) {
        List<ReceiverState> clamped = new ArrayList<>();
        for (ReceiverState receiver : active) {
            BigInteger lowerShare = BigInteger.valueOf(receiver.lowerBound()).multiply(activeCapacity);
            BigInteger proportionalShare = remainingEnergy.multiply(BigInteger.valueOf(receiver.endpoint().capacity()));
            if (lowerShare.compareTo(proportionalShare) > 0) {
                clamped.add(receiver);
            }
        }
        return clamped;
    }

    /**
     * Floors exact proportional shares, then assigns leftover indivisible FE by descending fractional remainder.
     *
     * @param active          active receivers that share one water line
     * @param remainingEnergy FE assigned to the active receivers
     * @param activeCapacity  exact capacity sum of the active receivers
     * @param targets         mutable target result map
     */
    private static void apportionActiveTargets(List<ReceiverState> active,
                                               BigInteger remainingEnergy,
                                               BigInteger activeCapacity,
                                               Map<TowerEnergyEndpointId, Long> targets) {
        List<FractionalShare> shares = new ArrayList<>();
        BigInteger floorTotal = ZERO;
        for (ReceiverState receiver : active) {
            BigInteger numerator = remainingEnergy.multiply(BigInteger.valueOf(receiver.endpoint().capacity()));
            BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(activeCapacity);
            long target = quotientAndRemainder[0].longValueExact();
            targets.put(receiver.endpoint().endpoint(), target);
            floorTotal = floorTotal.add(quotientAndRemainder[0]);
            shares.add(new FractionalShare(receiver, quotientAndRemainder[1]));
        }

        int leftover = remainingEnergy.subtract(floorTotal).intValueExact();
        shares.sort(Comparator.comparing(FractionalShare::remainder).reversed()
                .thenComparingInt(share -> share.receiver().order()));
        for (int index = 0; index < leftover; index++) {
            ReceiverState receiver = shares.get(index).receiver();
            TowerEnergyEndpointId endpoint = receiver.endpoint().endpoint();
            targets.compute(endpoint, (ignored, target) -> incrementTarget(target));
        }
    }

    /**
     * Increments a target that was already installed for an active receiver.
     *
     * @param target current target value, never null for a valid water-filling map
     * @return incremented target value
     */
    private static long incrementTarget(Long target) {
        if (target == null) {
            throw new IllegalStateException("Missing active receiver target during largest-remainder apportionment");
        }
        return Math.addExact(target, 1);
    }

    /**
     * Creates positive deposits for receiver targets above their frozen contents.
     *
     * @param endpoints complete ordered snapshot
     * @param targets   calculated receiver target contents
     * @return stable-order sink allocations
     */
    private static List<TowerEnergySinkAllocation> collectSinkAllocations(
                                                                          List<TowerEnergyEndpointSnapshot> endpoints, Map<TowerEnergyEndpointId, Long> targets) {
        List<TowerEnergySinkAllocation> sinks = new ArrayList<>();
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            Long target = targets.get(endpoint.endpoint());
            if (target != null && target > endpoint.stored()) {
                long amount = Math.min(target - endpoint.stored(), endpoint.receivable());
                if (amount > 0) {
                    sinks.add(new TowerEnergySinkAllocation(endpoint.endpoint(), amount));
                }
            }
        }
        return sinks;
    }

    /**
     * Adds planned sink deposits without aggregate overflow.
     *
     * @param sinks planned positive deposits
     * @return exact FE required by all sinks
     */
    private static BigInteger sumSinkAmounts(List<TowerEnergySinkAllocation> sinks) {
        BigInteger total = ZERO;
        for (TowerEnergySinkAllocation sink : sinks) {
            total = total.add(BigInteger.valueOf(sink.amount()));
        }
        return total;
    }

    /**
     * Consumes source-only energy first in snapshot order, leaving unused energy untouched when sinks are full.
     *
     * @param endpoints    complete ordered snapshot
     * @param amountNeeded exact FE still required by sinks
     * @param sources      mutable source allocation result
     * @return exact FE still required after source-only allocations
     */
    private static BigInteger collectSourceOnlyAllocations(List<TowerEnergyEndpointSnapshot> endpoints,
                                                           BigInteger amountNeeded,
                                                           List<TowerEnergySourceAllocation> sources) {
        BigInteger remaining = amountNeeded;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (remaining.signum() == 0) {
                break;
            }
            if (endpoint.direction() != TowerEnergyDirection.SOURCE || endpoint.stored() == 0) {
                continue;
            }
            long amount = takeAvailable(Math.min(endpoint.stored(), endpoint.extractable()), remaining);
            if (amount > 0) {
                sources.add(new TowerEnergySourceAllocation(endpoint.endpoint(), amount));
                remaining = remaining.subtract(BigInteger.valueOf(amount));
            }
        }
        return remaining;
    }

    /**
     * Adds only bidirectional energy above each proportional target after all source-only energy is exhausted.
     *
     * @param endpoints    complete ordered snapshot
     * @param targets      calculated receiver target contents
     * @param amountNeeded exact FE still required by sinks
     * @param sources      mutable source allocation result
     * @return exact FE still required after bidirectional allocations
     */
    private static BigInteger collectBidirectionalAllocations(List<TowerEnergyEndpointSnapshot> endpoints,
                                                              Map<TowerEnergyEndpointId, Long> targets,
                                                              BigInteger amountNeeded,
                                                              List<TowerEnergySourceAllocation> sources) {
        BigInteger remaining = amountNeeded;
        for (TowerEnergyEndpointSnapshot endpoint : endpoints) {
            if (remaining.signum() == 0) {
                break;
            }
            if (endpoint.direction() != TowerEnergyDirection.BIDIRECTIONAL) {
                continue;
            }
            long target = targets.get(endpoint.endpoint());
            long surplus = endpoint.stored() - target;
            if (surplus <= 0) {
                continue;
            }
            long amount = takeAvailable(Math.min(surplus, endpoint.extractable()), remaining);
            if (amount > 0) {
                sources.add(new TowerEnergySourceAllocation(endpoint.endpoint(), amount));
                remaining = remaining.subtract(BigInteger.valueOf(amount));
            }
        }
        return remaining;
    }

    /**
     * Selects the lesser of one endpoint's bounded availability and an exact aggregate requirement.
     *
     * @param available    non-negative FE available from one endpoint
     * @param amountNeeded positive exact aggregate requirement
     * @return positive FE amount to allocate from the endpoint
     */
    private static long takeAvailable(long available, BigInteger amountNeeded) {
        BigInteger availableAmount = BigInteger.valueOf(available);
        if (availableAmount.compareTo(amountNeeded) <= 0) {
            return available;
        }
        return amountNeeded.longValueExact();
    }

    /**
     * Retains receiver metadata needed for lower-bound water filling and deterministic tie breaking.
     *
     * @param endpoint   frozen receiver state
     * @param order      endpoint position in the complete snapshot
     * @param lowerBound minimum reachable stored FE because receive-only energy cannot be extracted
     */
    private record ReceiverState(TowerEnergyEndpointSnapshot endpoint, int order, long lowerBound) {}

    /**
     * Associates one floored proportional share with its exact fractional remainder.
     *
     * @param receiver  receiver that owns the share
     * @param remainder numerator remainder used for largest-remainder ordering
     */
    private record FractionalShare(ReceiverState receiver, BigInteger remainder) {}

    /**
     * Associates one floored primitive proportional share with its exact fractional remainder.
     *
     * @param receiver  receiver that owns the share
     * @param remainder numerator remainder used for largest-remainder ordering
     */
    private record LongFractionalShare(ReceiverState receiver, long remainder) {}
}
