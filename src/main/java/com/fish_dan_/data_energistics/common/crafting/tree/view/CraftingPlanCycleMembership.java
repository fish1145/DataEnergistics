package com.fish_dan_.data_energistics.common.crafting.tree.view;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Visual cycle membership comes from executed stage instances, not the SCC of globally pooled item types.
 * Seeds and boundary materials stay outside: sharing an item or pattern must not pull its supplier into a loop.
 */
public final class CraftingPlanCycleMembership {

    private CraftingPlanCycleMembership() {}

    /** Builds caller-owned display memberships from an immutable plan snapshot; no recipe or world access. */
    public static Int2ObjectMap<IntSet> collect(CraftingPlanGraph graph) {
        Int2ObjectMap<IntSet> members = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntSet> inputs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntSet> outputs = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntSet> materialCycles = new Int2ObjectOpenHashMap<>();
        IntSet external = new IntOpenHashSet();
        external.add(graph.rootId());
        for (var cycle : graph.cycles()) {
            members.put(cycle.id(), new IntAVLTreeSet());
            inputs.put(cycle.id(), new IntOpenHashSet());
            outputs.put(cycle.id(), new IntOpenHashSet());
        }
        for (var node : graph.nodes()) {
            if (node instanceof Process process) {
                for (int cycle : process.cycleIds()) members.get(cycle).add(process.id());
            }
        }
        for (var edge : graph.edges()) {
            if (edge.role() == Role.DIAGNOSTIC) continue;
            int material = edge.role() == Role.INPUT ? edge.target() : edge.source();
            Process process = (Process) graph.node(edge.role() == Role.INPUT ? edge.source() : edge.target());
            if (process.cycleIds().isEmpty()) external.add(material);
            for (int cycle : process.cycleIds()) {
                materialCycles.computeIfAbsent(material, unused -> new IntOpenHashSet()).add(cycle);
                (edge.role() == Role.INPUT ? inputs : outputs).get(cycle).add(material);
            }
        }
        for (var cycle : graph.cycles()) {
            for (int id : inputs.get(cycle.id())) {
                Material material = (Material) graph.node(id);
                if (!external.contains(id) && outputs.get(cycle.id()).contains(id) &&
                        materialCycles.get(id).size() == 1 && !cycle.minimumSeed().containsKey(material.key())) {
                    members.get(cycle.id()).add(id);
                }
            }
        }
        return members;
    }
}
