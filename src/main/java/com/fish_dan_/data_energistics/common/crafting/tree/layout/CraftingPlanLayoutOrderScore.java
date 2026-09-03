package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.view.CraftingPlanGraphView.ViewEdge;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** Local ordering objective over visible edges, with one rank interval per edge rather than per-layer dummy nodes. */
final class CraftingPlanLayoutOrderScore {

    private static final double EPSILON = 0.000001;
    private static final int MAXIMUM_PROBES = 32768;

    private final List<ViewEdge> edges;
    private final Int2ObjectMap<PlacedNode> nodes;
    private final Int2IntMap ranks;
    private final Int2ObjectMap<IntList> incident = new Int2ObjectOpenHashMap<>();
    private final Chord[] chords;
    private final int[] ordered;
    private final int[] positionByEdge;
    private final int[] parents;
    private final int[] maximumRanks;
    private final double[] minXs;
    private final double[] minYs;
    private final double[] maxXs;
    private final double[] maxYs;

    CraftingPlanLayoutOrderScore(List<ViewEdge> edges, Int2ObjectMap<PlacedNode> nodes, Int2IntMap ranks) {
        this.edges = edges;
        this.nodes = nodes;
        this.ranks = ranks;
        chords = new Chord[edges.size()];
        IntList order = new IntArrayList(edges.size());
        for (int id = 0; id < edges.size(); id++) {
            ViewEdge edge = edges.get(id);
            incident.computeIfAbsent(edge.source(), unused -> new IntArrayList()).add(id);
            if (edge.source() != edge.target()) incident.computeIfAbsent(edge.target(), unused -> new IntArrayList()).add(id);
            chords[id] = chord(edge);
            order.add(id);
        }
        order.sort((left, right) -> {
            int rank = Integer.compare(chords[left].firstRank(), chords[right].firstRank());
            if (rank != 0) return rank;
            int transverse = Double.compare(chords[left].minY(), chords[right].minY());
            return transverse != 0 ? transverse : Integer.compare(left, right);
        });
        ordered = order.toIntArray();
        positionByEdge = new int[edges.size()];
        parents = new int[edges.size()];
        Arrays.fill(parents, -1);
        maximumRanks = new int[edges.size()];
        minXs = new double[edges.size()];
        minYs = new double[edges.size()];
        maxXs = new double[edges.size()];
        maxYs = new double[edges.size()];
        build(0, ordered.length, -1);
    }

    /** Applies only strict improvements; restoring the caller's positions also restores the affected index branches. */
    boolean improve(IntList movedNodes, Runnable change, Runnable restore) {
        IntSet affected = new IntOpenHashSet();
        for (int node : movedNodes) {
            IntList ids = incident.get(node);
            if (ids != null) affected.addAll(ids);
        }
        if (affected.isEmpty()) return false;
        Score previous = score(affected);
        if (previous == null) return false;
        change.run();
        refresh(affected);
        Score candidate = score(affected);
        if (candidate != null && (candidate.crossings() < previous.crossings() || candidate.crossings() == previous.crossings() && candidate.length() < previous.length() - EPSILON)) return true;
        restore.run();
        refresh(affected);
        return false;
    }

    /** Dense overlapping edges may defeat spatial pruning; an incomplete comparison never accepts a move. */
    private @Nullable Score score(IntSet affected) {
        long crossings = 0;
        double length = 0;
        var probes = new ProbeBudget((int) Math.min(MAXIMUM_PROBES, 512L + 16L * affected.size()));
        for (int edge : affected) {
            Chord chord = chords[edge];
            length += Math.abs(chord.x2() - chord.x1()) + Math.abs(chord.y2() - chord.y1());
            crossings += crossings(edge, chord, affected, 0, ordered.length, probes);
            if (probes.exhausted) return null;
        }
        return new Score(crossings, length);
    }

    private long crossings(int edgeId, Chord chord, IntSet affected, int first, int last, ProbeBudget probes) {
        if (first == last) return 0;
        if (!probes.take()) return 0;
        int middle = (first + last) >>> 1;
        if (maximumRanks[middle] < chord.firstRank() || chords[ordered[first]].firstRank() > chord.lastRank() || maxXs[middle] < chord.minX() || minXs[middle] > chord.maxX() || maxYs[middle] < chord.minY() || minYs[middle] > chord.maxY()) return 0;
        int otherId = ordered[middle];
        long count = 0;
        if (edgeId != otherId && (!affected.contains(otherId) || edgeId < otherId)) {
            ViewEdge edge = edges.get(edgeId);
            ViewEdge other = edges.get(otherId);
            if (edge.source() != other.source() && edge.source() != other.target() && edge.target() != other.source() && edge.target() != other.target() && chord.crosses(chords[otherId])) count++;
        }
        return count + crossings(edgeId, chord, affected, first, middle, probes) + crossings(edgeId, chord, affected, middle + 1, last, probes);
    }

    private void build(int first, int last, int parent) {
        if (first == last) return;
        int middle = (first + last) >>> 1;
        parents[middle] = parent;
        positionByEdge[ordered[middle]] = middle;
        build(first, middle, middle);
        build(middle + 1, last, middle);
        maximumRanks[middle] = chords[ordered[middle]].lastRank();
        if (first < middle) maximumRanks[middle] = Math.max(maximumRanks[middle], maximumRanks[(first + middle) >>> 1]);
        if (middle + 1 < last) maximumRanks[middle] = Math.max(maximumRanks[middle], maximumRanks[(middle + 1 + last) >>> 1]);
        aggregate(first, last);
    }

    private void refresh(IntSet affected) {
        for (int edge : affected) chords[edge] = chord(edges.get(edge));
        // Only ancestors of moved endpoints can change. Rank intervals and tree topology remain fixed.
        IntSet changed = new IntOpenHashSet();
        for (int edge : affected) {
            for (int position = positionByEdge[edge]; position >= 0; position = parents[position]) changed.add(position);
        }
        refresh(0, ordered.length, changed);
    }

    private void refresh(int first, int last, IntSet changed) {
        if (first == last) return;
        int middle = (first + last) >>> 1;
        if (!changed.contains(middle)) return;
        refresh(first, middle, changed);
        refresh(middle + 1, last, changed);
        aggregate(first, last);
    }

    private void aggregate(int first, int last) {
        int middle = (first + last) >>> 1;
        Chord chord = chords[ordered[middle]];
        minXs[middle] = chord.minX();
        minYs[middle] = chord.minY();
        maxXs[middle] = chord.maxX();
        maxYs[middle] = chord.maxY();
        if (first < middle) include(middle, (first + middle) >>> 1);
        if (middle + 1 < last) include(middle, (middle + 1 + last) >>> 1);
    }

    private void include(int parent, int child) {
        minXs[parent] = Math.min(minXs[parent], minXs[child]);
        minYs[parent] = Math.min(minYs[parent], minYs[child]);
        maxXs[parent] = Math.max(maxXs[parent], maxXs[child]);
        maxYs[parent] = Math.max(maxYs[parent], maxYs[child]);
    }

    private Chord chord(ViewEdge edge) {
        PlacedNode source = nodes.get(edge.source());
        PlacedNode target = nodes.get(edge.target());
        return new Chord(source.x() + source.width() / 2, source.y() + source.height() / 2,
                target.x() + target.width() / 2, target.y() + target.height() / 2,
                Math.min(ranks.get(edge.source()), ranks.get(edge.target())),
                Math.max(ranks.get(edge.source()), ranks.get(edge.target())));
    }

    private record Score(long crossings, double length) {}

    private static final class ProbeBudget {

        private int remaining;
        private boolean exhausted;

        private ProbeBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean take() {
            if (remaining == 0) {
                exhausted = true;
                return false;
            }
            remaining--;
            return true;
        }
    }

    private record Chord(double x1, double y1, double x2, double y2, int firstRank, int lastRank) {

        private double minX() {
            return Math.min(x1, x2);
        }

        private double minY() {
            return Math.min(y1, y2);
        }

        private double maxX() {
            return Math.max(x1, x2);
        }

        private double maxY() {
            return Math.max(y1, y2);
        }

        private boolean crosses(Chord other) {
            double first = orientation(x1, y1, x2, y2, other.x1(), other.y1());
            double second = orientation(x1, y1, x2, y2, other.x2(), other.y2());
            double third = orientation(other.x1(), other.y1(), other.x2(), other.y2(), x1, y1);
            double fourth = orientation(other.x1(), other.y1(), other.x2(), other.y2(), x2, y2);
            return opposite(first, second) && opposite(third, fourth);
        }

        private static boolean opposite(double first, double second) {
            return first < -EPSILON && second > EPSILON || first > EPSILON && second < -EPSILON;
        }

        private static double orientation(double ax, double ay, double bx, double by, double cx, double cy) {
            return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        }
    }
}
