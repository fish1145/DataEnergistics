package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityGraphTopologyAnalyzerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void partitionsCyclesAndBuildsInputToOutputCondensationOrder() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        AEKey c = AEItemKey.of(Items.DIAMOND);
        AEKey d = AEItemKey.of(Items.EMERALD);
        List<TrinityPatternVariant> variants = List.of(
                variant("a-b", amounts(a, BigInteger.ONE), amounts(b, BigInteger.ONE)),
                variant("b-a", amounts(b, BigInteger.ONE), amounts(a, BigInteger.ONE)),
                variant("b-c", amounts(b, BigInteger.ONE), amounts(c, BigInteger.ONE)),
                variant("c-d", amounts(c, BigInteger.ONE), amounts(d, BigInteger.ONE)));

        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 4)
                .value();

        assertEquals(3, topology.components().size());
        int cycle = topology.componentByKey().get(a);
        assertEquals(cycle, topology.componentByKey().get(b));
        assertTrue(topology.components().get(cycle).cyclic());
        assertEquals(List.of(a, b), topology.components().get(cycle).keys());
        int cComponent = topology.componentByKey().get(c);
        int dComponent = topology.componentByKey().get(d);
        assertFalse(topology.components().get(cComponent).cyclic());
        assertEquals(List.of(cycle, cComponent, dComponent), topology.topologicalOrder());
        assertEquals(List.of(cComponent), topology.components().get(cycle).successorIndexes());
        assertEquals(List.of(cycle), topology.components().get(cComponent).predecessorIndexes());
    }

    @Test
    void recognizesSingleKeySelfCycleAndReturnsStablePartition() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant self = variant(
                "self",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityGraphTopologyAnalyzer analyzer = TrinityGraphTopologyAnalyzer.create();

        TrinityCraftingTopology first = analyzer
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), List.of(self), 1)
                .value();
        TrinityCraftingTopology second = analyzer
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), List.of(self), 1)
                .value();

        assertTrue(first.components().getFirst().cyclic());
        assertEquals(first, second);
    }

    @Test
    void rejectsOnlyTheComponentThatCrossesConfiguredKeyLimit() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        List<TrinityPatternVariant> variants = List.of(
                variant("a-b", amounts(a, BigInteger.ONE), amounts(b, BigInteger.ONE)),
                variant("b-a", amounts(b, BigInteger.ONE), amounts(a, BigInteger.ONE)));

        TrinityAlgorithmResult<TrinityCraftingTopology> result = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 1);

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.SCC_KEY_LIMIT, result.diagnostic().code());
        assertEquals(Map.of("limit", "1", "required", "2"), result.diagnostic().metadata());
    }

    @Test
    void assignsFuelledSelfMultiplicationToCycleWhileIndexingExternalSurface() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        AEKey byproduct = AEItemKey.of(Items.BUCKET);
        TrinityPatternVariant fuelledCycle = variant(
                "fuelled-cycle",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO, byproduct, BigInteger.ONE));

        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), List.of(fuelledCycle), 4)
                .value();

        int cycle = topology.componentByKey().get(a);
        int fuelComponent = topology.componentByKey().get(fuel);
        int byproductComponent = topology.componentByKey().get(byproduct);
        assertTrue(topology.components().get(cycle).cyclic());
        assertEquals(List.of(fuelledCycle), topology.components().get(cycle).cycleVariants());
        assertTrue(topology.components().get(cycle).predecessorIndexes().contains(fuelComponent));
        assertTrue(topology.components().get(cycle).successorIndexes().contains(byproductComponent));
        assertEquals(List.of(fuelledCycle), topology.variantsByOutputComponent().get(cycle));
        assertEquals(List.of(fuelledCycle), topology.variantsByOutputComponent().get(byproductComponent));
    }
}
