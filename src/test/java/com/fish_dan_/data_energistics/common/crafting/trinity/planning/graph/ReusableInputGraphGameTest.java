package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityTransitionEffectCompactor;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress.TrinityPlanningProgressReporter;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Alternative;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Input;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputGraphGameTest {

    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "graph_tool");

    private ReusableInputGraphGameTest() {}

    @TestHolder("reusable_graph_preserves_complete_assignments_and_legacy_ordinals")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesCompleteAssignmentsAndLegacyOrdinals(GameTestHelper helper) {
        var publication = publication();
        var identity = TrinityPatternIdentity.capture(publication, helper.getLevel().registryAccess());
        ReusableInputRule rule = ReusableInputRule.unchanged(RULE_ID, 1L, tool(1));
        List<TrinityBoundPatternInput> first = List.of(
                new TrinityBoundPatternInput(0, 0, stack(tool(0)), 1L, null),
                new TrinityBoundPatternInput(1, 0, stack(AEItemKey.of(Items.REDSTONE)), 1L, null));
        List<TrinityBoundPatternInput> second = List.of(
                bound(0, 1, rule, 1L, 1L),
                new TrinityBoundPatternInput(1, 1, stack(AEItemKey.of(Items.COAL)), 1L, null));
        var pattern = new TrinityCraftingGraphPattern(identity, publication, List.of(first, second));
        var expander = TrinityPatternVariantExpander.create();
        var expanded = expander.expandPattern(pattern, 2, TrinityPlanningControl.unbounded());
        helper.assertTrue(expanded.successful(), "Complete assignments fit exactly into a two-binding limit");
        helper.assertValueEqual(expanded.value().size(), 2, "Frozen complete assignments must not form four Cartesian combinations");
        helper.assertTrue(expanded.value().getFirst().requiresExactBinding(),
                "Even a rule-free branch of an expanded pattern must retain its exact ordinal mapping");
        helper.assertValueEqual(expanded.value().get(1).bindings(), second, "Frozen binding identity is preserved");
        var legacy = expander.expandPattern(new TrinityCraftingGraphPattern(identity, publication), 4,
                TrinityPlanningControl.unbounded());
        helper.assertValueEqual(legacy.value().size(), 4, "Original publication still uses legacy Cartesian expansion");
        helper.assertFalse(legacy.value().getFirst().requiresExactBinding(), "Legacy dynamic selection remains enabled");
        helper.succeed();
    }

    @TestHolder("reusable_graph_indexes_exact_tool_transitions_and_scaled_byproducts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void indexesExactToolTransitionsAndScaledByproducts(GameTestHelper helper) {
        AEItemKey scrap = AEItemKey.of(Items.STICK);
        ReusableInputRule rule = ReusableInputRule.transitions(RULE_ID, 1L, tool(0), List.of(
                new Transition(tool(0), tool(1), List.of(new GenericStack(scrap, 2L))),
                new Transition(tool(1), null, List.of())));
        var publication = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE),
                List.of(new Input(3L, List.of(new Alternative(stack(tool(0)), null)))),
                List.of(stack(AEItemKey.of(Items.DIAMOND))), false);
        var pattern = new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(publication, helper.getLevel().registryAccess()),
                publication, List.of(List.of(bound(0, 0, rule, 2L, 3L))));
        var graph = new TrinityCraftingGraphSnapshot(1L, List.of(pattern));
        var variant = TrinityPatternVariantExpander.create().expand(graph, 1).value().getFirst();
        helper.assertValueEqual(variant.outputs().get(tool(1)), BigInteger.valueOf(6L), "Each physical tool unit has one successor");
        helper.assertValueEqual(variant.outputs().get(scrap), BigInteger.valueOf(12L), "Byproducts scale by tool units, not templates");
        helper.assertTrue(graph.keys().contains(tool(1)) && graph.keys().contains(scrap), "Graph includes new state and byproduct keys");
        helper.assertValueEqual(graph.reachableSubgraph(scrap).patterns(), List.of(pattern), "Byproduct targets retain their producer");
        var normalized = variant.normalized(TrinitySameItemPolicy.ofRepresentatives(List.of(tool(0))));
        helper.assertValueEqual(normalized.netChange().get(tool(0)), BigInteger.valueOf(-6L), "Damage input must remain a consumed exact state");
        helper.assertValueEqual(normalized.netChange().get(tool(1)), BigInteger.valueOf(6L), "Damage successor cannot collapse into a zero-net catalyst");
        helper.assertTrue(normalized.requiresExactBinding(), "Normalization preserves execution binding metadata");
        helper.succeed();
    }

    @TestHolder("reusable_graph_compaction_keeps_distinct_lifetime_contracts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void compactionKeepsDistinctLifetimeContracts(GameTestHelper helper) {
        ReusableInputRule finite = ReusableInputRule.transitions(RULE_ID, 1L, tool(0), List.of(
                new Transition(tool(0), tool(1), List.of()), new Transition(tool(1), null, List.of())));
        ReusableInputRule cyclic = ReusableInputRule.transitions(RULE_ID, 2L, tool(0), List.of(
                new Transition(tool(0), tool(1), List.of()), new Transition(tool(1), tool(0), List.of())));
        var identity = new TrinityPatternIdentity("tool", "same-first-step");
        var first = TrinityPatternVariant.create(identity, AEItemKey.of(Items.DIAMOND), 0, List.of(0),
                List.of(bound(0, 0, finite, 1L, 1L)), List.of(stack(AEItemKey.of(Items.DIAMOND))), true);
        var second = TrinityPatternVariant.create(identity, AEItemKey.of(Items.DIAMOND), 1, List.of(1),
                List.of(bound(0, 1, cyclic, 1L, 1L)), List.of(stack(AEItemKey.of(Items.DIAMOND))), true);
        helper.assertValueEqual(first.physicalOutputs(), second.physicalOutputs(), "First-use effects intentionally coincide");
        helper.assertValueEqual(TrinityTransitionEffectCompactor.create().compact(List.of(first, second)).size(), 2,
                "Equal one-step amounts do not make different frozen lifetimes interchangeable");
        helper.succeed();
    }

    @TestHolder("reusable_graph_cache_separates_rule_changes_with_same_publication")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cacheSeparatesRuleChangesWithSamePublication(GameTestHelper helper) throws Exception {
        AEItemKey tool = tool(0);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        var publication = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE),
                List.of(new Input(1L, List.of(new Alternative(stack(tool), null)))), List.of(stack(output)), false);
        var identity = TrinityPatternIdentity.capture(publication, helper.getLevel().registryAccess());
        try (TrinityComputationCache cache = TrinityComputationCache.create(Runnable::run)) {
            var computation = TrinityPlanningComputation.create(cache, TrinityGraphPlanner.pipeline());
            for (long revision = 1L; revision <= 2L; revision++) {
                ReusableInputRule rule = ReusableInputRule.transitions(RULE_ID, revision, tool,
                        List.of(new Transition(tool, null, List.of())));
                var pattern = new TrinityCraftingGraphPattern(identity, publication, List.of(List.of(bound(0, 0, rule, 1L, 1L))));
                var input = new TrinityPlanningInput(1L, new TrinityCraftingGraphSnapshot(1L, List.of(pattern)), output,
                        BigInteger.ONE, CraftingQuantityMode.NET_NEW,
                        TrinityPlanningInventory.finite(Map.of(tool, BigInteger.ONE)),
                        new TrinityPlanningLimits(16, 16, 128, 1000));
                var result = computation.calculate(input, TrinityPlanningProgressReporter.none());
                helper.assertTrue(result.result().successful(), "Single-use tool can produce one requested output");
                helper.assertValueEqual(result.cacheStatistics().patternExpansionMisses(), 1,
                        "Changed frozen rule must not reuse expansion under the original publication identity");
                helper.assertFalse(result.cacheStatistics().targetStructureHit(), "Compiled structures must include frozen rule values");
            }
        }
        helper.succeed();
    }

    private static TrinityPatternPublicationSignature publication() {
        return new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE), List.of(
                new Input(1L, List.of(new Alternative(stack(tool(0)), null), new Alternative(stack(tool(1)), null))),
                new Input(1L, List.of(new Alternative(stack(AEItemKey.of(Items.REDSTONE)), null),
                        new Alternative(stack(AEItemKey.of(Items.COAL)), null)))),
                List.of(stack(AEItemKey.of(Items.DIAMOND))), false);
    }

    private static TrinityBoundPatternInput bound(int slot, int alternative, ReusableInputRule rule, long amount, long multiplier) {
        var result = rule.advance(rule.initialKey(), 1L);
        return new TrinityBoundPatternInput(slot, alternative, new GenericStack(rule.initialKey(), amount), multiplier,
                result.successor(), rule, result.byproducts());
    }

    private static GenericStack stack(AEItemKey key) {
        return new GenericStack(key, 1L);
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }
}
