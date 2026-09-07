package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Packs dependency branches into disjoint vertical bands. A shared block has one placement owner,
 * but all its original edges remain in the graph. No recursion or iterative force/crossing search is needed.
 */
final class CraftingPlanBranchPlacement {

    private CraftingPlanBranchPlacement() {}

    static Int2DoubleMap centers(List<Block> blocks, int rootId, double gap) {
        Int2ObjectMap<Block> byId = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntList> owned = new Int2ObjectOpenHashMap<>();
        for (Block block : blocks) {
            byId.put(block.id(), block);
            owned.put(block.id(), new IntArrayList());
        }
        Int2IntMap order = new Int2IntOpenHashMap();
        Int2IntMap trees = new Int2IntOpenHashMap();
        visit(rootId, byId, order, trees);
        List<Block> topological = new ObjectArrayList<>(blocks);
        topological.sort(Comparator.comparingInt(Block::rank).thenComparingInt(Block::id));
        for (Block block : topological) {
            if (!order.containsKey(block.id())) visit(block.id(), byId, order, trees);
        }

        Int2IntMap owners = new Int2IntOpenHashMap();
        owners.defaultReturnValue(-1);
        for (Block block : topological) {
            checkInterrupted();
            for (int target : block.dependencies()) {
                Block child = byId.get(target);
                if (child.rank() <= block.rank() || target == rootId) continue;
                int previous = owners.get(target);
                if (previous < 0 || preferredOwner(block, byId.get(previous), order, trees)) {
                    owners.put(target, block.id());
                }
            }
        }
        IntList roots = new IntArrayList();
        for (Block block : topological) {
            int owner = owners.get(block.id());
            if (owner < 0) roots.add(block.id());
            else owned.get(owner).add(block.id());
        }
        roots.sort(Comparator.comparingInt(order::get));
        for (IntList children : owned.values()) children.sort(Comparator.comparingInt(order::get));

        Int2DoubleMap spans = new Int2DoubleOpenHashMap();
        Int2DoubleMap childSpans = new Int2DoubleOpenHashMap();
        for (int index = topological.size() - 1; index >= 0; index--) {
            checkInterrupted();
            Block block = topological.get(index);
            IntList children = owned.get(block.id());
            double childSpan = 0;
            for (int child : children) childSpan += spans.get(child);
            if (!children.isEmpty()) childSpan += (children.size() - 1) * gap;
            childSpans.put(block.id(), childSpan);
            spans.put(block.id(), Math.max(block.height(), childSpan));
        }

        Int2DoubleMap centers = new Int2DoubleOpenHashMap();
        double top = 0;
        for (int root : roots) {
            centers.put(root, top + spans.get(root) / 2);
            top += spans.get(root) + gap;
        }
        // Owners always precede their children in rank order, including edges that skip columns.
        for (Block block : topological) {
            checkInterrupted();
            double childTop = centers.get(block.id()) - childSpans.get(block.id()) / 2;
            for (int child : owned.get(block.id())) {
                centers.put(child, childTop + spans.get(child) / 2);
                childTop += spans.get(child) + gap;
            }
        }
        return centers;
    }

    private static boolean preferredOwner(Block candidate, Block previous, Int2IntMap order, Int2IntMap trees) {
        // Keep the requested item's branches together before attaching disconnected co-product explanations.
        int tree = Integer.compare(trees.get(candidate.id()), trees.get(previous.id()));
        if (tree != 0) return tree < 0;
        if (candidate.rank() != previous.rank()) return candidate.rank() > previous.rank();
        return order.get(candidate.id()) < order.get(previous.id());
    }

    private static void visit(int rootId, Int2ObjectMap<Block> blocks, Int2IntMap order, Int2IntMap trees) {
        int tree = order.size();
        IntArrayList pending = new IntArrayList();
        pending.push(rootId);
        while (!pending.isEmpty()) {
            checkInterrupted();
            int id = pending.popInt();
            if (order.containsKey(id)) continue;
            order.put(id, order.size());
            trees.put(id, tree);
            Block block = blocks.get(id);
            IntList children = block.dependencies();
            for (int index = children.size() - 1; index >= 0; index--) {
                int child = children.getInt(index);
                if (blocks.get(child).rank() > block.rank()) pending.push(child);
            }
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    /** A condensed dependency component; height is its complete vertical envelope, including local cycles. */
    record Block(int id, int rank, double height, IntList dependencies) {}
}
