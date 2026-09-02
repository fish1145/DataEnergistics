package com.fish_dan_.data_energistics.common.crafting.tree.projection;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Cycle;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Header;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Kind;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Projects only retained planning facts; native AE2 pattern/input binding remains explicitly estimated. */
public final class CraftingPlanGraphProjection {
    private CraftingPlanGraphProjection() {}

    public static CraftingPlanGraph create(ICraftingPlan plan, CraftingQuantityMode quantityMode,
                                          KeyCounter available, long planningNanos) {
        Kind kind = plan instanceof TrinityCraftingPlan ? Kind.EXACT : Kind.ESTIMATE;
        Component diagnostic = Component.empty();
        if (plan instanceof TrinityDiagnosedCraftingPlan diagnosed) {
            kind = diagnosed.ae2FallbackEstimate() ? Kind.ESTIMATE : Kind.DIAGNOSTIC;
            diagnostic = diagnosed.diagnostic().message();
        } else if (plan instanceof TrinityCraftingPlan exact) {
            MutableComponent messages = Component.empty();
            for (TrinityPlanningDiagnostic detail : exact.diagnostics()) {
                if (!messages.getSiblings().isEmpty()) messages.append("\n");
                messages.append(detail.message());
            }
            diagnostic = messages;
        }
        Header header = new Header(plan.finalOutput().what(), BigInteger.valueOf(plan.finalOutput().amount()),
                plan instanceof TrinityCraftingPlan exact ? exact.exactBytes() : BigInteger.valueOf(plan.bytes()),
                kind, quantityMode, planningNanos, diagnostic);
        Projection projection = new Projection(header, available);
        if (plan instanceof TrinityCraftingPlan exact) {
            projection.exact(exact);
        } else if (plan instanceof TrinityDiagnosedCraftingPlan diagnosed && !diagnosed.ae2FallbackEstimate()) {
            projection.diagnostic(diagnosed.diagnostic());
        } else {
            projection.estimate(plan);
        }
        return projection.build();
    }

    private static final class Projection {
        private final Header header;
        private final KeyCounter available;
        private final Map<AEKey, Amounts> materials = new LinkedHashMap<>();
        private final List<Process> processes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<Cycle> cycles = new ArrayList<>();
        private int nextId;

        private Projection(Header header, KeyCounter available) {
            this.header = header;
            this.available = available;
            material(header.target()).required = header.requested();
        }

        private Amounts material(AEKey key) {
            return this.materials.computeIfAbsent(key, ignored -> new Amounts(this.nextId++));
        }

        private void exact(TrinityCraftingPlan plan) {
            plan.initialExpectedInputs().forEach((key, amount) -> material(key).stored = amount);
            Map<Integer, TrinityCycleRepeatBlock> blocks = new HashMap<>();
            plan.cycleRepeatBlocks().forEach(block -> block.stageOrder().forEach(stage -> blocks.put(stage, block)));
            for (TrinityPlanStage stage : plan.stages()) {
                TrinityCycleRepeatBlock block = blocks.get(stage.index());
                BigInteger repetitions = block == null ? BigInteger.ONE : block.repetitions();
                List<Integer> memberships = block == null ? List.of() : List.of(block.index());
                for (TrinityPlanPatternFiring firing : stage.firings()) {
                    firing(stage.index(), firing.patternIdentity().publicationEncoding(), firing.variantOrdinal(),
                            firing.primaryOutput(), firing.count().multiply(repetitions), false, memberships,
                            firing.inputs(), firing.outputs(), firing.remainingOutputs());
                }
            }
            for (TrinityCycleRepeatBlock block : plan.cycleRepeatBlocks()) {
                cycle(block.index(), this.cycles.size() + 1, block.stageOrder(), block.repetitions(),
                        block.minimumSeed(), block.netChange());
            }
        }

        private void firing(int stage, String identity, int variant, AEKey primary, BigInteger count,
                            boolean estimated, List<Integer> memberships, Map<AEKey, BigInteger> inputs,
                            Map<AEKey, BigInteger> outputs, Map<AEKey, BigInteger> remainders) {
            int processId = this.nextId++;
            this.processes.add(new Process(processId, stage, identity, variant, primary, count, estimated, memberships));
            inputs.forEach((key, value) -> {
                BigInteger amount = value.multiply(count);
                Amounts material = material(key);
                material.input = material.input.add(amount);
                this.edges.add(new Edge(this.edges.size(), processId, material.id, Role.INPUT, amount));
            });
            outputEdges(processId, count, outputs, Role.OUTPUT);
            outputEdges(processId, count, remainders, Role.REMAINDER);
        }

        private void outputEdges(int process, BigInteger count, Map<AEKey, BigInteger> outputs, Role role) {
            outputs.forEach((key, value) -> {
                BigInteger amount = value.multiply(count);
                Amounts material = material(key);
                material.crafting = material.crafting.add(amount);
                this.edges.add(new Edge(this.edges.size(), material.id, process, role, amount));
            });
        }

        private void cycle(int id, int ordinal, List<Integer> stages, BigInteger repetitions,
                           Map<AEKey, BigInteger> seed, Map<AEKey, BigInteger> net) {
            LinkedHashSet<Integer> ids = new LinkedHashSet<>();
            LinkedHashSet<Integer> processIds = new LinkedHashSet<>();
            for (Process process : this.processes) {
                if (process.cycleIds().contains(id)) processIds.add(process.id());
            }
            ids.addAll(processIds);
            for (Edge edge : this.edges) {
                if (processIds.contains(edge.source()) || processIds.contains(edge.target())) {
                    ids.add(edge.source());
                    ids.add(edge.target());
                }
            }
            seed.keySet().forEach(key -> ids.add(material(key).id));
            net.keySet().forEach(key -> ids.add(material(key).id));
            this.cycles.add(new Cycle(id, ordinal, List.copyOf(ids), stages, repetitions, seed, net));
        }

        private void diagnostic(TrinityPlanningDiagnostic diagnostic) {
            diagnostic.inputShortage().ifPresent(shortage -> {
                Amounts material = material(shortage.key());
                material.required = shortage.required();
                material.stored = shortage.available();
                material.missing = shortage.missing();
            });
            diagnostic.partialPlan().ifPresent(partial -> {
                partial.usedItems().forEach((key, amount) -> material(key).stored = amount);
                partial.missingItems().forEach((key, amount) -> material(key).unresolved = amount);
                partial.inputRequirements().forEach((key, requirement) -> {
                    Amounts material = material(key);
                    material.required = requirement.required();
                    material.stored = requirement.available();
                    material.missing = requirement.missing();
                    material.unresolved = BigInteger.ZERO;
                });
            });
            // Proven local cycle schedules may be displayed as evidence, never as an executable complete route.
            int stage = 0;
            for (var evidence : diagnostic.cycleEvidence()) {
                for (TrinityVariantFiring firing : evidence.prefixOrder()) {
                    evidenceFiring(stage++, firing, BigInteger.ONE, List.of());
                }
                List<Integer> stages = new ArrayList<>();
                for (TrinityVariantFiring firing : evidence.localOrder()) {
                    int index = stage++;
                    stages.add(index);
                    evidenceFiring(index, firing, evidence.repetitions(), List.of(evidence.componentIndex()));
                }
                for (TrinityVariantFiring firing : evidence.suffixOrder()) {
                    evidenceFiring(stage++, firing, BigInteger.ONE, List.of());
                }
                cycle(evidence.componentIndex(), this.cycles.size() + 1, stages, evidence.repetitions(),
                        evidence.minimumSeed(), evidence.netChange());
            }
            diagnostic.partialPlan().ifPresent(partial -> partial.emittedItems().forEach((key, amount) -> {
                Amounts material = material(key);
                material.crafting = material.crafting.max(amount);
            }));
            if (diagnostic.inputShortage().isEmpty() && diagnostic.partialPlan().isEmpty()) {
                material(this.header.target()).unresolved = this.header.requested();
            }
            evidenceEdges();
        }

        private void evidenceFiring(int stage, TrinityVariantFiring firing, BigInteger repetitions, List<Integer> memberships) {
            var variant = firing.variant();
            Map<AEKey, BigInteger> remainders = new LinkedHashMap<>(variant.outputs());
            variant.declaredOutputs().forEach((key, amount) -> remainders.merge(key, amount.negate(), BigInteger::add));
            remainders.values().removeIf(amount -> amount.signum() == 0);
            firing(stage, variant.patternIdentity().publicationEncoding(), variant.ordinal(), variant.primaryOutput(),
                    firing.count().multiply(repetitions), false, memberships,
                    variant.inputs(), variant.declaredOutputs(), remainders);
        }

        private void estimate(ICraftingPlan plan) {
            plan.usedItems().forEach(entry -> material(entry.getKey()).stored = BigInteger.valueOf(entry.getLongValue()));
            plan.missingItems().forEach(entry -> material(entry.getKey()).missing = BigInteger.valueOf(entry.getLongValue()));
            int stage = 0;
            for (Map.Entry<IPatternDetails, Long> entry : plan.patternTimes().entrySet()) {
                IPatternDetails pattern = entry.getKey();
                BigInteger count = BigInteger.valueOf(entry.getValue());
                Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();
                pattern.getOutputs().forEach(output -> outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add));
                Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
                for (var input : pattern.getInputs()) {
                    GenericStack[] candidates = input.getPossibleInputs();
                    if (candidates.length == 1) {
                        inputs.merge(candidates[0].what(), BigInteger.valueOf(candidates[0].amount())
                                .multiply(BigInteger.valueOf(input.getMultiplier())), BigInteger::add);
                    } else {
                        // Native summary does not retain which substitute was selected. Do not fabricate an input edge.
                        for (GenericStack candidate : candidates) {
                            Amounts material = material(candidate.what());
                            material.unresolved = material.unresolved.add(BigInteger.valueOf(candidate.amount())
                                    .multiply(BigInteger.valueOf(input.getMultiplier())).multiply(count));
                        }
                    }
                }
                firing(stage, "ae2-estimate:" + stage, 0, pattern.getPrimaryOutput().what(), count,
                        true, List.of(), inputs, outputs, Map.of());
                stage++;
            }
            plan.emittedItems().forEach(entry -> {
                Amounts material = material(entry.getKey());
                material.crafting = material.crafting.max(BigInteger.valueOf(entry.getLongValue()));
            });
            evidenceEdges();
        }

        private void evidenceEdges() {
            int root = material(this.header.target()).id;
            for (Amounts material : this.materials.values()) {
                if (material.id == root) continue;
                BigInteger evidence = material.required.max(material.stored.add(material.missing).add(material.unresolved));
                if (evidence.signum() == 0) evidence = material.crafting;
                if (evidence.signum() > 0) this.edges.add(new Edge(this.edges.size(), root, material.id, Role.DIAGNOSTIC, evidence));
            }
        }

        private CraftingPlanGraph build() {
            List<Node> nodes = new ArrayList<>();
            this.materials.forEach((key, amounts) -> {
                BigInteger required = amounts.required.max(amounts.input).max(amounts.stored.add(amounts.missing).add(amounts.unresolved));
                long stock = this.available.get(key);
                int basis = stock <= 0 ? 0 : amounts.stored.multiply(BigInteger.valueOf(10000))
                        .divide(BigInteger.valueOf(stock)).min(BigInteger.valueOf(10000)).intValueExact();
                nodes.add(new Material(amounts.id, key, required, amounts.stored, amounts.crafting,
                        amounts.missing, amounts.unresolved, basis));
            });
            nodes.addAll(this.processes);
            return new CraftingPlanGraph(this.header, material(this.header.target()).id, nodes, this.edges, this.cycles);
        }
    }

    private static final class Amounts {
        private final int id;
        private BigInteger required = BigInteger.ZERO;
        private BigInteger input = BigInteger.ZERO;
        private BigInteger stored = BigInteger.ZERO;
        private BigInteger crafting = BigInteger.ZERO;
        private BigInteger missing = BigInteger.ZERO;
        private BigInteger unresolved = BigInteger.ZERO;
        private Amounts(int id) { this.id = id; }
    }
}
