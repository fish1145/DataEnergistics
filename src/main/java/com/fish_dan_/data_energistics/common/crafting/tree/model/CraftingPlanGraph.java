package com.fish_dan_.data_energistics.common.crafting.tree.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Immutable dependency graph; edges point from requested outputs towards their production inputs. */
public final class CraftingPlanGraph {

    private final Header header;
    private final int rootId;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Cycle> cycles;
    private final Int2ObjectMap<Node> byId;

    public CraftingPlanGraph(Header header, int rootId, List<Node> nodes, List<Edge> edges, List<Cycle> cycles) {
        this.header = header;
        this.rootId = rootId;
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.cycles = List.copyOf(cycles);
        Int2ObjectMap<Node> indexed = new Int2ObjectOpenHashMap<>();
        ObjectSet<AEKey> keys = new ObjectOpenHashSet<>();
        ObjectSet<ProcessIdentity> processes = new ObjectOpenHashSet<>();
        for (Node node : this.nodes) {
            if (indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate graph node id");
            }
            if (node instanceof Material material && !keys.add(material.key())) {
                throw new IllegalArgumentException("Materials must be globally normalized by complete AEKey");
            }
            if (node instanceof Process process && !processes.add(new ProcessIdentity(
                    process.stageIndex(), process.patternIdentity(), process.variantOrdinal()))) {
                throw new IllegalArgumentException("Duplicate stage pattern binding");
            }
        }
        this.byId = Int2ObjectMaps.unmodifiable(indexed);
        if (!(indexed.get(rootId) instanceof Material root) || !root.key().equals(header.target())) {
            throw new IllegalArgumentException("Graph root must be the requested material");
        }
        IntSet edgeIds = new IntOpenHashSet();
        for (Edge edge : this.edges) {
            Node source = indexed.get(edge.source());
            Node target = indexed.get(edge.target());
            boolean validRole = switch (edge.role()) {
                case INPUT -> source instanceof Process && target instanceof Material;
                case OUTPUT, REMAINDER -> source instanceof Material && target instanceof Process;
                case DIAGNOSTIC -> source instanceof Material && target instanceof Material && edge.source() == rootId && header.kind() != Kind.EXACT;
            };
            if (!edgeIds.add(edge.id()) || !validRole) {
                throw new IllegalArgumentException("Invalid graph edge id, endpoints or role");
            }
        }
        Int2ObjectMap<Cycle> cycleById = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntSet> cycleMembers = new Int2ObjectOpenHashMap<>();
        IntSet cycleOrdinals = new IntOpenHashSet();
        for (Cycle cycle : this.cycles) {
            if (cycleById.putIfAbsent(cycle.id(), cycle) != null || !cycleOrdinals.add(cycle.ordinal())) {
                throw new IllegalArgumentException("Duplicate graph cycle id or display ordinal");
            }
            IntSet members = new IntOpenHashSet(cycle.nodeIds());
            IntSet stages = new IntOpenHashSet(cycle.stageOrder());
            IntSet memberStages = new IntOpenHashSet();
            cycleMembers.put(cycle.id(), members);
            for (int nodeId : cycle.nodeIds()) {
                Node member = indexed.get(nodeId);
                if (member == null) {
                    throw new IllegalArgumentException("Cycle references an absent node");
                }
                if (member instanceof Process process) {
                    if (!process.cycleIds().contains(cycle.id())) {
                        throw new IllegalArgumentException("Cycle contains a process without matching membership");
                    }
                    memberStages.add(process.stageIndex());
                }
            }
            if (!memberStages.equals(stages)) throw new IllegalArgumentException("Cycle stages differ from member processes");
        }
        for (Node node : this.nodes) {
            if (node instanceof Process process) {
                if (!keys.contains(process.primaryOutput())) {
                    throw new IllegalArgumentException("Process primary output material is absent");
                }
                for (int cycleId : process.cycleIds()) {
                    Cycle cycle = cycleById.get(cycleId);
                    if (cycle == null || !cycleMembers.get(cycleId).contains(process.id())) {
                        throw new IllegalArgumentException("Process cycle membership is inconsistent");
                    }
                }
            }
        }
    }

    public Header header() {
        return this.header;
    }

    public int rootId() {
        return this.rootId;
    }

    public List<Node> nodes() {
        return this.nodes;
    }

    public List<Edge> edges() {
        return this.edges;
    }

    public List<Cycle> cycles() {
        return this.cycles;
    }

    /** Resolves a validated graph id; absent ids indicate caller misuse. */
    public Node node(int id) {
        Node node = this.byId.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown graph node " + id);
        }
        return node;
    }

    public enum Kind {
        EXACT,
        DIAGNOSTIC,
        ESTIMATE
    }

    public enum Role {
        INPUT,
        OUTPUT,
        REMAINDER,
        DIAGNOSTIC
    }

    public record Header(AEKey target, BigInteger requested, BigInteger bytes, Kind kind,
                         CraftingQuantityMode quantityMode, long planningNanos, Component diagnostic) {

        public Header {
            positive(requested);
            nonnegative(bytes);
            if (planningNanos < 0) throw new IllegalArgumentException("Negative planning time");
            diagnostic = diagnostic.copy();
        }

        @Override
        public Component diagnostic() {
            return this.diagnostic.copy();
        }
    }

    /**
     * Closed immutable display-node contract, independent of executable recipes and client widgets.
     * Instances are safe to retain across threads; ids are nonnegative and unique within their owning graph.
     */
    public sealed interface Node permits Material, Process {

        /** Stable local graph identifier; never null, never mutates and performs no external lookup. */
        int id();
    }

    public record Material(int id, AEKey key, BigInteger required, BigInteger stored, BigInteger crafting,
                           BigInteger missing, BigInteger unresolved, int inventoryUsageBasisPoints)
            implements Node {

        public Material {
            checkId(id);
            nonnegative(required);
            nonnegative(stored);
            nonnegative(crafting);
            nonnegative(missing);
            nonnegative(unresolved);
            if (inventoryUsageBasisPoints < 0 || inventoryUsageBasisPoints > 10000) {
                throw new IllegalArgumentException("Inventory usage must be between zero and 10000 basis points");
            }
        }
    }

    public record Process(int id, int stageIndex, String patternIdentity, int variantOrdinal, AEKey primaryOutput,
                          BigInteger executions, boolean estimated, List<Integer> cycleIds)
            implements Node {

        public Process {
            checkId(id);
            checkId(stageIndex);
            checkId(variantOrdinal);
            if (patternIdentity.isBlank()) throw new IllegalArgumentException("Empty pattern identity");
            positive(executions);
            cycleIds = uniqueIds(cycleIds);
        }
    }

    public record Edge(int id, int source, int target, Role role, BigInteger amount) {

        public Edge {
            checkId(id);
            checkId(source);
            checkId(target);
            positive(amount);
        }
    }

    public record Cycle(int id, int ordinal, List<Integer> nodeIds, List<Integer> stageOrder,
                        BigInteger repetitions, Map<AEKey, BigInteger> minimumSeed, Map<AEKey, BigInteger> netChange) {

        public Cycle {
            checkId(id);
            checkId(ordinal);
            nodeIds = uniqueIds(nodeIds);
            stageOrder = uniqueIds(stageOrder);
            if (nodeIds.isEmpty() || stageOrder.isEmpty()) throw new IllegalArgumentException("Empty cycle");
            positive(repetitions);
            minimumSeed = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(minimumSeed));
            minimumSeed.values().forEach(CraftingPlanGraph::positive);
            netChange = Object2ObjectMaps.unmodifiable(new Object2ObjectLinkedOpenHashMap<>(netChange));
            if (netChange.values().stream().anyMatch(amount -> amount.signum() == 0)) {
                throw new IllegalArgumentException("Zero cycle net change entry");
            }
        }
    }

    private record ProcessIdentity(int stage, String pattern, int variant) {}

    private static List<Integer> uniqueIds(List<Integer> values) {
        List<Integer> result = List.copyOf(values);
        result.forEach(CraftingPlanGraph::checkId);
        if (new IntOpenHashSet(result).size() != result.size()) throw new IllegalArgumentException("Duplicate id");
        return result;
    }

    private static void checkId(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative graph id");
    }

    private static void nonnegative(BigInteger value) {
        if (value.signum() < 0) throw new IllegalArgumentException("Negative graph amount");
    }

    private static void positive(BigInteger value) {
        if (value.signum() <= 0) throw new IllegalArgumentException("Nonpositive graph amount");
    }
}
