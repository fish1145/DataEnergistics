package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.events.GridEvent;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityAccessLeaseTest {

    @Test
    void electCapturesTheExactSingleCandidateIdentity() {
        BlockPos.MutableBlockPos candidatePosition = new BlockPos.MutableBlockPos(8, 12, -4);
        BlockPos capturedPosition = candidatePosition.immutable();
        IGrid candidateGrid = new StubGrid();

        TrinityAccessLease lease = TrinityAccessLease.elect(candidatePosition, candidateGrid, 7L);
        candidatePosition.set(40, 50, 60);

        assertEquals(capturedPosition, lease.hatchPosition());
        assertSame(candidateGrid, lease.grid());
        assertEquals(7L, lease.epoch());
        assertTrue(lease.identifies(capturedPosition));
        assertFalse(lease.identifies(candidatePosition));
        assertTrue(lease.matches(capturedPosition, candidateGrid));
        assertFalse(lease.matches(capturedPosition, new StubGrid()));
        assertFalse(lease.matches(candidatePosition, candidateGrid));
        assertSame(lease, lease.bind(candidateGrid));
        assertThrows(IllegalArgumentException.class,
                () -> TrinityAccessLease.elect(BlockPos.ZERO, candidateGrid, -1L));
    }

    @Test
    void restoreAndRebindKeepTheStickyLeaseIdentity() {
        BlockPos.MutableBlockPos persistedPosition = new BlockPos.MutableBlockPos(-3, 9, 14);
        BlockPos capturedPosition = persistedPosition.immutable();
        TrinityAccessLease restored = TrinityAccessLease.restore(persistedPosition, 19L);
        persistedPosition.move(1, 2, 3);

        assertEquals(capturedPosition, restored.hatchPosition());
        assertEquals(19L, restored.epoch());
        assertTrue(restored.identifies(capturedPosition));
        assertNull(restored.grid());
        assertFalse(restored.matches(capturedPosition, null));
        assertSame(restored, restored.unbind());

        IGrid recoveredGrid = new StubGrid();
        TrinityAccessLease rebound = restored.bind(recoveredGrid);
        assertNotSame(restored, rebound);
        assertEquals(capturedPosition, rebound.hatchPosition());
        assertEquals(restored.epoch(), rebound.epoch());
        assertTrue(rebound.matches(capturedPosition, recoveredGrid));
        assertSame(rebound, rebound.bind(recoveredGrid));

        TrinityAccessLease offline = rebound.unbind();
        assertNotSame(rebound, offline);
        assertEquals(capturedPosition, offline.hatchPosition());
        assertEquals(rebound.epoch(), offline.epoch());
        assertTrue(offline.identifies(capturedPosition));
        assertNull(offline.grid());
        assertSame(offline, offline.unbind());
        assertThrows(IllegalArgumentException.class,
                () -> TrinityAccessLease.restore(BlockPos.ZERO, -1L));
    }

    private static final class StubGrid implements IGrid {

        @Override
        public <C extends IGridService> C getService(Class<C> serviceType) {
            throw new IllegalArgumentException("Lease test grid has no services: " + serviceType.getName());
        }

        @Override
        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> machineClass) {
            return Set.of();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
            return Set.of();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            return Set.of();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public IGridNode getPivot() {
            throw new IllegalStateException("Lease test grid has no pivot node");
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void export(JsonWriter jsonWriter) throws IOException {
            jsonWriter.beginObject();
            jsonWriter.endObject();
        }
    }
}
