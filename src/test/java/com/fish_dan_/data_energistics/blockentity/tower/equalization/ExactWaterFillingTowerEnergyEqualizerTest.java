package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExactWaterFillingTowerEnergyEqualizerTest {

    @Test
    void apportionsLongSafePlanWithStableLargestRemainderAndSourcePriority() {
        TowerEnergyEndpointId source = id(0);
        TowerEnergyEndpointId bidirectional = id(1);
        TowerEnergyEndpointId sink = id(2);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(new TowerEnergyEqualizationSnapshot(List.of(
                endpoint(source, 2, 2, TowerEnergyDirection.SOURCE),
                endpoint(bidirectional, 5, 10, TowerEnergyDirection.BIDIRECTIONAL),
                endpoint(sink, 0, 10, TowerEnergyDirection.SINK))));

        assertFalse(plan.isEmpty());
        assertEquals(List.of(
                new TowerEnergySourceAllocation(source, 2),
                new TowerEnergySourceAllocation(bidirectional, 1)), plan.sources());
        assertEquals(List.of(new TowerEnergySinkAllocation(sink, 3)), plan.sinks());
        assertEquals(BigInteger.valueOf(3), plan.totalAmount());
    }

    @Test
    void preservesLongMaxWidthSourceStateWithoutNarrowingTheTransfer() {
        TowerEnergyEndpointId source = id(3);
        TowerEnergyEndpointId sink = id(4);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(new TowerEnergyEqualizationSnapshot(List.of(
                endpoint(source, Long.MAX_VALUE, Long.MAX_VALUE, TowerEnergyDirection.SOURCE),
                endpoint(sink, 0, 1, TowerEnergyDirection.SINK))));

        assertEquals(List.of(new TowerEnergySourceAllocation(source, 1)), plan.sources());
        assertEquals(List.of(new TowerEnergySinkAllocation(sink, 1)), plan.sinks());
    }

    @Test
    void fallsBackToExactAggregatesWhenSourceTotalExceedsLongWidth() {
        TowerEnergyEndpointId firstSource = id(5);
        TowerEnergyEndpointId secondSource = id(6);
        TowerEnergyEndpointId sink = id(7);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(new TowerEnergyEqualizationSnapshot(List.of(
                endpoint(firstSource, Long.MAX_VALUE, Long.MAX_VALUE, TowerEnergyDirection.SOURCE),
                endpoint(secondSource, Long.MAX_VALUE, Long.MAX_VALUE, TowerEnergyDirection.SOURCE),
                endpoint(sink, 0, 1, TowerEnergyDirection.SINK))));

        assertEquals(List.of(new TowerEnergySourceAllocation(firstSource, 1)), plan.sources());
        assertEquals(List.of(new TowerEnergySinkAllocation(sink, 1)), plan.sinks());
    }

    @Test
    void fallsBackToExactProportionalMathWhenShareProductExceedsLongWidth() {
        long width = 4_000_000_000_000_000_000L;
        TowerEnergyEndpointId source = id(8);
        TowerEnergyEndpointId firstSink = id(9);
        TowerEnergyEndpointId secondSink = id(10);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(new TowerEnergyEqualizationSnapshot(List.of(
                endpoint(source, width, width, TowerEnergyDirection.SOURCE),
                endpoint(firstSink, 0, width, TowerEnergyDirection.SINK),
                endpoint(secondSink, 0, width, TowerEnergyDirection.SINK))));

        assertEquals(List.of(new TowerEnergySourceAllocation(source, width)), plan.sources());
        assertEquals(List.of(
                new TowerEnergySinkAllocation(firstSink, width / 2),
                new TowerEnergySinkAllocation(secondSink, width / 2)), plan.sinks());
    }

    @Test
    void fallsBackToExactLowerBoundComparisonWhenItsProductExceedsLongWidth() {
        long lowerBound = 4_000_000_000_000_000_000L;
        long externalEnergy = 1_000_000_000_000_000_000L;
        TowerEnergyEndpointId source = id(11);
        TowerEnergyEndpointId fixedSink = id(12);
        TowerEnergyEndpointId activeSink = id(13);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(new TowerEnergyEqualizationSnapshot(List.of(
                endpoint(source, externalEnergy, externalEnergy, TowerEnergyDirection.SOURCE),
                endpoint(fixedSink, lowerBound, lowerBound, TowerEnergyDirection.SINK),
                endpoint(activeSink, 0, lowerBound, TowerEnergyDirection.SINK))));

        assertEquals(List.of(new TowerEnergySourceAllocation(source, externalEnergy)), plan.sources());
        assertEquals(List.of(new TowerEnergySinkAllocation(activeSink, externalEnergy)), plan.sinks());
    }

    @Test
    void excludesNearLongMaxNetworkBufferCapacityFromMachineProportions() {
        TowerEnergyEndpointId networkBuffer = id(14);
        TowerEnergyEndpointId smallMachine = id(15);
        TowerEnergyEndpointId largeMachine = id(16);
        TowerEnergyEqualizationPlan plan = new ExactWaterFillingTowerEnergyEqualizer().plan(
                new TowerEnergyEqualizationSnapshot(List.of(
                        new TowerEnergyEndpointSnapshot(
                                networkBuffer,
                                100,
                                Long.MAX_VALUE,
                                100,
                                Long.MAX_VALUE - 100,
                                TowerEnergyDirection.BIDIRECTIONAL,
                                TowerEnergyEndpointRole.BUFFER),
                        endpoint(smallMachine, 0, 100, TowerEnergyDirection.BIDIRECTIONAL),
                        endpoint(largeMachine, 0, 300, TowerEnergyDirection.BIDIRECTIONAL))));

        assertEquals(List.of(new TowerEnergySourceAllocation(networkBuffer, 100)), plan.sources());
        assertEquals(List.of(
                new TowerEnergySinkAllocation(smallMachine, 25),
                new TowerEnergySinkAllocation(largeMachine, 75)), plan.sinks());
    }

    private static TowerEnergyEndpointSnapshot endpoint(TowerEnergyEndpointId id,
                                                        long stored,
                                                        long capacity,
                                                        TowerEnergyDirection direction) {
        return new TowerEnergyEndpointSnapshot(id, stored, capacity, direction);
    }

    private static TowerEnergyEndpointId id(int x) {
        return new TowerEnergyEndpointId(new BlockPos(x, 0, 0), Direction.NORTH);
    }
}
