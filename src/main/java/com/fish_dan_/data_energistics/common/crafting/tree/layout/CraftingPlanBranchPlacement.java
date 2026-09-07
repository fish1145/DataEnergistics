package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleComparators;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Keeps dependency branches contiguous, then packs each column around its neighbours' median.
 * A shared block has one ordering owner, not a duplicated subtree or a permanently reserved empty band.
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
        roots.sort((left, right) -> Integer.compare(order.get(left), order.get(right)));
        for (IntList children : owned.values()) children.sort((left, right) -> Integer.compare(order.get(left), order.get(right)));

        Int2ObjectMap<IntList> parents = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<List<Block>> layers = new Int2ObjectAVLTreeMap<>();
        for (Block block : topological) {
            parents.put(block.id(), new IntArrayList());
            layers.computeIfAbsent(block.rank(), unused -> new ObjectArrayList<>()).add(block);
        }
        for (Block block : topological) {
            for (int child : block.dependencies()) {
                if (byId.get(child).rank() > block.rank()) parents.get(child).add(block.id());
            }
        }
        order.clear();
        for (int root : roots) {
            IntArrayList pending = new IntArrayList();
            pending.push(root);
            while (!pending.isEmpty()) {
                int id = pending.popInt();
                order.put(id, order.size());
                IntList children = owned.get(id);
                for (int index = children.size() - 1; index >= 0; index--) pending.push(children.getInt(index));
            }
        }
        List<List<Block>> columns = new ObjectArrayList<>(layers.values());
        for (List<Block> column : columns) column.sort(Comparator.comparingInt(block -> order.get(block.id())));
        Int2DoubleMap centers = new Int2DoubleOpenHashMap();
        for (List<Block> column : columns) pack(column, parents, centers, gap);
        // Anchor leaves once, then center their ancestors. Re-pulling siblings toward their common parent
        // after this pass would undo single-chain alignment and make the entire tree drift on every sweep.
        for (int index = columns.size() - 1; index >= 0; index--) pack(columns.get(index), owned, centers, gap);
        return centers;
    }

    /** Linear isotonic packing: the closest median positions that preserve order and non-overlap. */
    private static void pack(List<Block> column, Int2ObjectMap<IntList> neighbours, Int2DoubleMap centers, double gap) {
        checkInterrupted();
        int count = column.size();
        double[] offsets = new double[count];
        double[] sums = new double[count];
        int[] sizes = new int[count];
        int groups = 0;
        DoubleArrayList positions = new DoubleArrayList();
        for (int index = 0; index < count; index++) {
            Block block = column.get(index);
            if (index > 0) offsets[index] = offsets[index - 1] + (column.get(index - 1).height() + block.height()) / 2 + gap;
            positions.clear();
            for (int neighbour : neighbours.get(block.id())) positions.add(centers.get(neighbour));
            positions.sort(DoubleComparators.NATURAL_COMPARATOR);
            double desired = positions.isEmpty() ? centers.get(block.id()) :
                    (positions.getDouble((positions.size() - 1) / 2) + positions.getDouble(positions.size() / 2)) / 2;
            sums[groups] = desired - offsets[index];
            sizes[groups++] = 1;
            while (groups > 1 && sums[groups - 2] / sizes[groups - 2] > sums[groups - 1] / sizes[groups - 1]) {
                sums[groups - 2] += sums[groups - 1];
                sizes[groups - 2] += sizes[groups - 1];
                groups--;
            }
        }
        int index = 0;
        for (int group = 0; group < groups; group++) {
            double position = sums[group] / sizes[group];
            for (int member = 0; member < sizes[group]; member++, index++) centers.put(column.get(index).id(), position + offsets[index]);
        }
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
