package com.fish_dan_.data_energistics.blockentity.tower.topology;

import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph.TargetLinkFailure;
import com.fish_dan_.data_energistics.blockentity.tower.topology.TowerLinkStateGraph.TargetLinkState;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TowerLinkStateGraphTest {

    private static final BlockPos FIRST_TARGET = new BlockPos(4, 7, 9);
    private static final BlockPos SECOND_TARGET = new BlockPos(-2, 5, 13);

    @Test
    void bindingsKeepInsertionOrderWithoutRetainingGridConnections() {
        TowerLinkStateGraph graph = new TowerLinkStateGraph();

        graph.addLinked(FIRST_TARGET);
        graph.addLinked(SECOND_TARGET);
        graph.addLinked(FIRST_TARGET);

        assertEquals(List.of(FIRST_TARGET, SECOND_TARGET), graph.trackedPositions());
        assertEquals(Set.of(FIRST_TARGET, SECOND_TARGET), graph.linkedPositions());
        assertEquals(TargetLinkState.BOUND, graph.status(FIRST_TARGET).state());

        graph.removeLinked(FIRST_TARGET);

        assertFalse(graph.containsLinked(FIRST_TARGET));
        assertEquals(TargetLinkState.INVALID, graph.status(FIRST_TARGET).state());
        assertEquals(List.of(SECOND_TARGET), graph.trackedPositions());
    }

    @Test
    void retryStateUsesBoundedExponentialBackoffAndCanBeResetByLifecycleEvent() {
        TowerLinkStateGraph graph = new TowerLinkStateGraph();
        graph.addLinked(FIRST_TARGET);

        assertTrue(graph.scheduleRetry(
                FIRST_TARGET, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(3, graph.status(FIRST_TARGET).retryTicks());
        assertTrue(graph.advanceRetryClock(2).isEmpty());
        assertEquals(List.of(FIRST_TARGET), graph.advanceRetryClock(1));

        assertTrue(graph.scheduleRetry(
                FIRST_TARGET, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(6, graph.status(FIRST_TARGET).retryTicks());
        assertEquals(List.of(FIRST_TARGET), graph.advanceRetryClock(6));

        assertTrue(graph.scheduleRetry(
                FIRST_TARGET, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(12, graph.status(FIRST_TARGET).retryTicks());
        assertEquals(List.of(FIRST_TARGET), graph.advanceRetryClock(12));

        assertTrue(graph.transition(FIRST_TARGET, TargetLinkState.ALLOCATED, TargetLinkFailure.NONE, 0));
        assertFalse(graph.hasRetryableTargets());
        assertEquals(TargetLinkState.ALLOCATED, graph.status(FIRST_TARGET).state());
    }

    @Test
    void runtimeResetPreservesBindingsAndRestoresBoundState() {
        TowerLinkStateGraph graph = new TowerLinkStateGraph();
        graph.addLinkedAll(List.of(FIRST_TARGET, SECOND_TARGET));
        graph.transition(FIRST_TARGET, TargetLinkState.DISABLED, TargetLinkFailure.NONE, 0);
        graph.scheduleRetry(
                SECOND_TARGET, TargetLinkState.BRIDGE_ERROR,
                TargetLinkFailure.GRID_SERVICE_REGISTRATION, 2, 8);

        graph.resetRuntimeState();

        assertEquals(List.of(FIRST_TARGET, SECOND_TARGET), graph.trackedPositions());
        assertEquals(TargetLinkState.BOUND, graph.status(FIRST_TARGET).state());
        assertEquals(TargetLinkState.BOUND, graph.status(SECOND_TARGET).state());
        assertFalse(graph.hasRetryableTargets());
    }
}
