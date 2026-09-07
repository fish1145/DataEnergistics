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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @TestHolder("reusable_graph_fixed_lifetime_is_arithmetic_capacity_not_damage_nodes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void fixedLifetimeIsArithmeticCapacityNotDamageNodes(GameTestHelper helper) throws Exception {
        AEItemKey material = AEItemKey.of(Items.REDSTONE);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        AEItemKey shard = AEItemKey.of(Items.PRISMARINE_SHARD);
        ItemStack toolStack = new ItemStack(Items.DIAMOND_HOE);
        AEItemKey fresh = AEItemKey.of(toolStack);
        toolStack.set(DataComponents.DAMAGE, 200);
        AEItemKey worn = AEItemKey.of(toolStack);
        var rule = ReusableInputRule.fixedDamage(RULE_ID, 1L, fresh, 1, 1000, List.of());
        var upgrade = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE), List.of(
                new Input(1L, List.of(new Alternative(stack(fresh), rule.advance(fresh, 1).successor()))),
                new Input(4L, List.of(new Alternative(stack(material), null)))), List.of(stack(output)), false);
        var reverse = new TrinityPatternPublicationSignature(AEItemKey.of(Items.FURNACE),
                List.of(new Input(1L, List.of(new Alternative(stack(output), null)))), List.of(new GenericStack(material, 4)), false);
        var toolRecipe = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CHEST),
                List.of(new Input(8L, List.of(new Alternative(stack(shard), null)))), List.of(stack(fresh)), false);
        var upgradePattern = new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(upgrade, helper.getLevel().registryAccess()), upgrade,
                List.of(List.of(bound(0, 0, rule, 1, 1), new TrinityBoundPatternInput(1, 0, stack(material), 4, null))));
        var factory = new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(toolRecipe, helper.getLevel().registryAccess()), toolRecipe);
        var graph = new TrinityCraftingGraphSnapshot(1L, List.of(upgradePattern,
                new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(reverse, helper.getLevel().registryAccess()), reverse), factory));
        var expanded = TrinityPatternVariantExpander.create().expand(graph, 16);
        helper.assertTrue(expanded.successful(), "A thousand-use tool must fit in the small original recipe graph");
        helper.assertValueEqual(expanded.value().size(), 3, "Durability must not multiply recipe nodes");
        try (TrinityComputationCache cache = TrinityComputationCache.create(Runnable::run)) {
            var computation = TrinityPlanningComputation.create(cache, TrinityGraphPlanner.pipeline());
            var request = new TrinityPlanningInput(1L, graph, output, BigInteger.valueOf(250), CraftingQuantityMode.NET_NEW,
                    new TrinityPlanningInventory(Map.of(worn, BigInteger.ONE), Set.of(material, shard)),
                    new TrinityPlanningLimits(64, 128, 500000, 10000));
            var result = computation.calculate(request, TrinityPlanningProgressReporter.none());
            if (!result.result().successful()) helper.fail("Existing worn tool must cover 250 uses: " + result.result().diagnostic());
            helper.assertValueEqual(result.result().value().initialExpectedInputs().get(worn), BigInteger.ONE,
                    "The CPU must withdraw the actual damaged tool, not its pristine planning prototype");
            var larger = computation.calculate(new TrinityPlanningInput(1L, graph, output, BigInteger.valueOf(2000),
                    CraftingQuantityMode.NET_NEW, new TrinityPlanningInventory(Map.of(), Set.of(material, shard)), request.limits()),
                    TrinityPlanningProgressReporter.none());
            if (!larger.result().successful()) helper.fail("Fresh tool production must follow the use budget: " + larger.result().diagnostic());
            helper.assertValueEqual(larger.result().value().patternFirings().get(factory.identity()), BigInteger.valueOf(2),
                    "2000 uses require two 1000-use tools, not 2000 tool crafts");
            helper.assertValueEqual(larger.result().value().initialExpectedInputs().get(shard), BigInteger.valueOf(16),
                    "An unlimited raw material remains available and is charged for two tool crafts only");

            var tiers = List.of(material, AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.GOLD_INGOT),
                    AEItemKey.of(Items.EMERALD), output);
            var tierPatterns = new ObjectArrayList<TrinityCraftingGraphPattern>();
            tierPatterns.add(factory);
            for (int tier = 1; tier < tiers.size(); tier++) {
                AEItemKey lower = tiers.get(tier - 1);
                AEItemKey higher = tiers.get(tier);
                var upgradeTier = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE), List.of(
                        new Input(1L, List.of(new Alternative(stack(fresh), rule.advance(fresh, 1).successor()))),
                        new Input(4L, List.of(new Alternative(stack(lower), null)))), List.of(stack(higher)), false);
                tierPatterns.add(new TrinityCraftingGraphPattern(
                        TrinityPatternIdentity.capture(upgradeTier, helper.getLevel().registryAccess()), upgradeTier,
                        List.of(List.of(bound(0, 0, rule, 1, 1), new TrinityBoundPatternInput(1, 0, stack(lower), 4, null)))));
                var reverseTier = new TrinityPatternPublicationSignature(AEItemKey.of(Items.FURNACE),
                        List.of(new Input(1L, List.of(new Alternative(stack(higher), null)))), List.of(new GenericStack(lower, 4)), false);
                tierPatterns.add(new TrinityCraftingGraphPattern(
                        TrinityPatternIdentity.capture(reverseTier, helper.getLevel().registryAccess()), reverseTier));
            }
            var tieredGraph = new TrinityCraftingGraphSnapshot(2L, tierPatterns);
            helper.assertValueEqual(TrinityPatternVariantExpander.create().expand(tieredGraph, 16).value().size(), 9,
                    "Four reversible tiers retain nine recipes regardless of tool lifetime");
            var tiered = computation.calculate(new TrinityPlanningInput(1L, tieredGraph, output, BigInteger.valueOf(1000),
                    CraftingQuantityMode.NET_NEW, new TrinityPlanningInventory(Map.of(), Set.of(material, shard)), request.limits()),
                    TrinityPlanningProgressReporter.none());
            if (!tiered.result().successful()) helper.fail("Four-tier request must use arithmetic tool capacity: " + tiered.result().diagnostic());
            helper.assertValueEqual(tiered.result().value().patternFirings().get(factory.identity()), BigInteger.valueOf(85),
                    "1000 final items need 85000 tier upgrades and exactly 85 thousand-use tools");
            helper.assertValueEqual(tiered.result().value().initialExpectedInputs().get(material), BigInteger.valueOf(256000),
                    "All four tiers still consume their full raw material requirement");
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
                result.successor(), rule, result.byproducts(),
                rule.kind() == ReusableInputRule.Kind.FIXED_DAMAGE && rule.exhaustionByproducts().isEmpty());
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
