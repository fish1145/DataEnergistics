package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.tower.TowerLinkGraph.TargetLinkFailure;
import com.fish_dan_.data_energistics.blockentity.tower.TowerLinkGraph.TargetLinkState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.me.GridNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TowerLinkGraphImplTest {

    private static final BlockPos TARGET_POS = new BlockPos(4, 7, 9);

    @Test
    void reconcileRetainsExistingFacesAndDestroysOnlyRemovedFaces() {
        TowerLinkGraph graph = new TowerLinkGraphImpl();
        IGridNode northNode = new TestGridNode();
        IGridNode southNode = new TestGridNode();
        IGridNode upNode = new TestGridNode();
        TestGridConnection northConnection = new TestGridConnection(northNode);
        TestGridConnection southConnection = new TestGridConnection(southNode);
        TestGridConnection upConnection = new TestGridConnection(upNode);

        graph.reconcileConnections(TARGET_POS, Map.of(
                northNode, northConnection,
                southNode, southConnection));
        graph.reconcileConnections(TARGET_POS, Map.of(
                southNode, southConnection,
                upNode, upConnection));

        assertEquals(1, northConnection.destroyCalls());
        assertEquals(0, southConnection.destroyCalls());
        assertEquals(0, upConnection.destroyCalls());
        assertFalse(graph.hasConnection(TARGET_POS, northNode));
        assertTrue(graph.hasConnection(TARGET_POS, southNode));
        assertTrue(graph.hasConnection(TARGET_POS, upNode));
        assertEquals(2, graph.connectionCount(TARGET_POS));
        assertSame(southConnection, graph.connections(TARGET_POS).get(southNode));
        assertSame(upConnection, graph.connections(TARGET_POS).get(upNode));

        graph.reconcileConnections(TARGET_POS, Map.of(
                southNode, southConnection,
                upNode, upConnection));

        assertEquals(0, southConnection.destroyCalls());
        assertEquals(0, upConnection.destroyCalls());
    }

    @Test
    void reconcileDestroysAReplacedConnectionForTheSameNode() {
        TowerLinkGraph graph = new TowerLinkGraphImpl();
        IGridNode node = new TestGridNode();
        TestGridConnection oldConnection = new TestGridConnection(node);
        TestGridConnection replacement = new TestGridConnection(node);
        graph.reconcileConnections(TARGET_POS, Map.of(node, oldConnection));

        graph.reconcileConnections(TARGET_POS, Map.of(node, replacement));

        assertEquals(1, oldConnection.destroyCalls());
        assertEquals(0, replacement.destroyCalls());
        assertSame(replacement, graph.connections(TARGET_POS).get(node));
    }

    @Test
    void destroyingLiveConnectionsPreservesTheExplicitTargetBinding() {
        TowerLinkGraph graph = new TowerLinkGraphImpl();
        IGridNode node = new TestGridNode();
        TestGridConnection connection = new TestGridConnection(node);
        graph.addLinked(TARGET_POS);
        graph.reconcileConnections(TARGET_POS, Map.of(node, connection));

        graph.destroyTargetConnections(TARGET_POS);

        assertTrue(graph.containsLinked(TARGET_POS));
        assertFalse(graph.hasConnections(TARGET_POS));
        assertEquals(1, connection.destroyCalls());

        assertTrue(graph.scheduleRetry(
                TARGET_POS, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(3, graph.status(TARGET_POS).retryTicks());
        assertTrue(graph.advanceRetryClock(2).isEmpty());
        assertEquals(Set.of(TARGET_POS), Set.copyOf(graph.advanceRetryClock(1)));
        assertTrue(graph.scheduleRetry(
                TARGET_POS, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(6, graph.status(TARGET_POS).retryTicks());
        assertEquals(Set.of(TARGET_POS), Set.copyOf(graph.advanceRetryClock(6)));
        assertTrue(graph.scheduleRetry(
                TARGET_POS, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(12, graph.status(TARGET_POS).retryTicks());
        assertEquals(Set.of(TARGET_POS), Set.copyOf(graph.advanceRetryClock(12)));
        assertTrue(graph.scheduleRetry(
                TARGET_POS, TargetLinkState.WAITING_TARGET, TargetLinkFailure.TARGET_UNAVAILABLE, 3, 12));
        assertEquals(12, graph.status(TARGET_POS).retryTicks());
        assertTrue(graph.transition(TARGET_POS, TargetLinkState.DISABLED, TargetLinkFailure.NONE, 0));
        assertTrue(graph.containsLinked(TARGET_POS));
        assertFalse(graph.hasRetryableTargets());

        graph.resetRuntimeState();

        assertTrue(graph.containsLinked(TARGET_POS));
        assertEquals(TargetLinkState.BOUND, graph.status(TARGET_POS).state());
        assertFalse(graph.hasConnections(TARGET_POS));
    }

    private static final class TestGridNode extends GridNode {

        private static final IGridNodeListener<Object> LISTENER = (owner, node) -> {};

        private TestGridNode() {
            super(null, new Object(), LISTENER, Set.of());
        }
    }

    private static final class TestGridConnection implements IGridConnection {

        private final IGridNode node;
        private int destroyCalls;

        private TestGridConnection(IGridNode node) {
            this.node = node;
        }

        @Override
        public IGridNode getOtherSide(IGridNode gridNode) {
            return this.node;
        }

        @Override
        public boolean isInWorld() {
            return false;
        }

        @Override
        public Direction getDirection(IGridNode gridNode) {
            return null;
        }

        @Override
        public void destroy() {
            this.destroyCalls++;
        }

        @Override
        public IGridNode a() {
            return this.node;
        }

        @Override
        public IGridNode b() {
            return this.node;
        }

        @Override
        public int getUsedChannels() {
            return 0;
        }

        private int destroyCalls() {
            return this.destroyCalls;
        }
    }
}
