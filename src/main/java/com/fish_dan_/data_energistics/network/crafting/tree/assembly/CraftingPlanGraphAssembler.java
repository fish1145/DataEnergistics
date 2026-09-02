package com.fish_dan_.data_energistics.network.crafting.tree.assembly;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphPayload;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphCycle;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphEdge;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphHeader;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphRecord.GraphNode;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Game-thread, menu-owned assembler. Only complete validated revisions are published. */
public final class CraftingPlanGraphAssembler {
    private @Nullable UUID session;
    private int container = -1;
    private long revision = -1;
    private @Nullable CraftingPlanGraphPayload metadata;
    private final Int2ObjectMap<List<CraftingPlanGraphRecord>> batches = new Int2ObjectOpenHashMap<>();
    private int records;
    private int bytes;
    private boolean terminal;

    /** Ignores old revisions/foreign sessions; invalid current transfers are discarded and explicitly rejected. */
    public Optional<CraftingPlanGraph> accept(CraftingPlanGraphPayload payload) {
        if (this.session != null && (!this.session.equals(payload.sessionId()) || this.container != payload.containerId())) {
            return Optional.empty();
        }
        if (payload.revision() < this.revision) return Optional.empty();
        if (payload.revision() > this.revision) {
            discard();
            this.session = payload.sessionId();
            this.container = payload.containerId();
            this.revision = payload.revision();
            this.metadata = payload;
            this.terminal = false;
        }
        try {
            if (this.terminal) throw new IllegalArgumentException("Graph revision already completed or rejected");
            CraftingPlanGraphPayload expected = this.metadata;
            if (expected == null || expected.batchCount() != payload.batchCount()
                    || expected.totalBytes() != payload.totalBytes() || expected.totalRecords() != payload.totalRecords()) {
                throw new IllegalArgumentException("Conflicting graph batch metadata");
            }
            if (this.batches.putIfAbsent(payload.batchIndex(), payload.records()) != null) {
                throw new IllegalArgumentException("Duplicate graph batch");
            }
            this.records = Math.addExact(this.records, payload.records().size());
            this.bytes = Math.addExact(this.bytes, payload.encodedBytes());
            if (this.records > expected.totalRecords() || this.bytes > expected.totalBytes()) {
                throw new IllegalArgumentException("Graph assembly exceeded declared limits");
            }
            if (this.batches.size() != expected.batchCount()) return Optional.empty();
            if (this.records != expected.totalRecords() || this.bytes != expected.totalBytes()) {
                throw new IllegalArgumentException("Graph assembly totals do not match metadata");
            }
            List<Node> nodes = new ObjectArrayList<>();
            List<Edge> edges = new ObjectArrayList<>();
            List<Cycle> cycles = new ObjectArrayList<>();
            GraphHeader header = null;
            for (int index = 0; index < expected.batchCount(); index++) {
                for (CraftingPlanGraphRecord record : this.batches.get(index)) {
                    switch (record) {
                        case GraphHeader value -> {
                            if (header != null) throw new IllegalArgumentException("Duplicate graph header");
                            header = value;
                        }
                        case GraphNode value -> nodes.add(value.node());
                        case GraphEdge value -> edges.add(value.edge());
                        case GraphCycle value -> cycles.add(value.cycle());
                    }
                }
            }
            if (header == null) throw new IllegalArgumentException("Missing graph header");
            CraftingPlanGraph graph = new CraftingPlanGraph(header.header(), header.rootId(), nodes, edges, cycles);
            discard();
            this.terminal = true;
            return Optional.of(graph);
        } catch (IllegalArgumentException exception) {
            discard();
            this.terminal = true;
            throw exception;
        }
    }

    /** Drops partial and completed revision identity when its owning menu closes. */
    public void clear() {
        discard();
        this.session = null;
        this.container = -1;
        this.revision = -1;
        this.terminal = false;
    }

    private void discard() {
        this.batches.clear();
        this.records = 0;
        this.bytes = 0;
        this.metadata = null;
    }
}
