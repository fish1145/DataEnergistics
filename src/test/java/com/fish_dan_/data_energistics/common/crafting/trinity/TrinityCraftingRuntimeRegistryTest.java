package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridService;
import appeng.api.networking.events.GridEvent;
import appeng.me.GridNode;
import com.google.gson.stream.JsonWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCraftingRuntimeRegistryTest {

    private final TrinityCraftingRuntimeRegistry.Local registry = TrinityCraftingRuntimeRegistry.createLocal();

    @Test
    void publicationUsesNodeIdentityAndDeduplicatesRuntimeIdentity() {
        IGrid grid = new StubGrid();
        IGridNode firstNode = new EqualGridNode(grid);
        IGridNode secondNode = new EqualGridNode(grid);
        TrinityDataCoreCraftingRuntime runtime = runtime();

        assertTrue(this.registry.publish(firstNode, runtime));
        assertTrue(this.registry.publish(secondNode, runtime));

        List<TrinityDataCoreCraftingRuntime> published = this.registry.snapshot();
        assertEquals(1, published.size());
        assertSame(runtime, published.getFirst());
        assertThrows(UnsupportedOperationException.class, () -> published.add(runtime()));

        assertTrue(this.registry.withdraw(firstNode));
        assertEquals(List.of(runtime), this.registry.snapshot());
        assertTrue(this.registry.withdraw(secondNode));
        assertFalse(this.registry.withdraw(secondNode));
        assertTrue(this.registry.snapshot().isEmpty());
    }

    @Test
    void duplicatePublicationIsIdempotentAndReplacementFailsFast() {
        IGrid grid = new StubGrid();
        IGridNode node = new EqualGridNode(grid);
        TrinityDataCoreCraftingRuntime firstRuntime = runtime();
        TrinityDataCoreCraftingRuntime replacementRuntime = runtime();

        assertTrue(this.registry.publish(node, firstRuntime));
        assertFalse(this.registry.publish(node, firstRuntime));

        assertThrows(
                IllegalStateException.class,
                () -> this.registry.publish(node, replacementRuntime));
        assertSame(firstRuntime, this.registry.snapshot().getFirst());

        assertTrue(this.registry.withdraw(node));
        assertTrue(this.registry.publish(node, replacementRuntime));
        assertSame(replacementRuntime, this.registry.snapshot().getFirst());
    }

    @Test
    void reconciliationAtomicallyReplacesOnlyThisRegistrySnapshot() {
        IGrid grid = new StubGrid();
        IGridNode oldNode = new EqualGridNode(grid);
        IGridNode firstReplacementNode = new EqualGridNode(grid);
        IGridNode secondReplacementNode = new EqualGridNode(grid);
        TrinityDataCoreCraftingRuntime oldRuntime = runtime();
        TrinityDataCoreCraftingRuntime replacementRuntime = runtime();
        TrinityCraftingRuntimeRegistry.Local otherRegistry = TrinityCraftingRuntimeRegistry.createLocal();
        this.registry.publish(oldNode, oldRuntime);
        List<TrinityDataCoreCraftingRuntime> oldSnapshot = this.registry.snapshot();

        Map<IGridNode, TrinityDataCoreCraftingRuntime> replacementScan = new IdentityHashMap<>();
        replacementScan.put(firstReplacementNode, replacementRuntime);
        replacementScan.put(secondReplacementNode, replacementRuntime);
        List<TrinityDataCoreCraftingRuntime> reconciled = this.registry.reconcile(replacementScan);

        assertEquals(List.of(oldRuntime), oldSnapshot);
        assertEquals(List.of(replacementRuntime), reconciled);
        assertSame(reconciled, this.registry.snapshot());
        assertTrue(otherRegistry.snapshot().isEmpty());
        this.registry.withdraw(firstReplacementNode);
        assertEquals(List.of(replacementRuntime), this.registry.snapshot());
        this.registry.withdraw(secondReplacementNode);
        assertTrue(this.registry.snapshot().isEmpty());
    }

    private static TrinityDataCoreCraftingRuntime runtime() {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(
                BlockPos.ZERO,
                ModBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        return host.getCraftingRuntime();
    }

    private static final class EqualGridNode extends GridNode {

        private static final IGridNodeListener<Object> LISTENER = (owner, node) -> {};

        private final IGrid grid;

        private EqualGridNode(IGrid grid) {
            super(null, new Object(), LISTENER, Set.of());
            this.grid = grid;
        }

        @Override
        public IGrid getGrid() {
            return this.grid;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof EqualGridNode;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class StubGrid implements IGrid {

        @Override
        public <C extends IGridService> C getService(Class<C> serviceType) {
            throw new IllegalArgumentException("Registry test grid has no services: " + serviceType.getName());
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
            throw new IllegalStateException("Registry test grid has no pivot node");
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
