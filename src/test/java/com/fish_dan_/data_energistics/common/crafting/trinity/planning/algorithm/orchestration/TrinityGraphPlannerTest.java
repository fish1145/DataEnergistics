package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityGraphPlannerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void composesDagProducedSeedAndFuelCycleBeforeDownstreamDag() {
        AEKey rawSeed = AEItemKey.of(Items.REDSTONE);
        AEKey rawFuel = AEItemKey.of(Items.STICK);
        AEKey seed = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern seedPattern = pattern(
                "a-seed",
                Items.PAPER,
                List.of(stack(rawSeed, 1L)),
                List.of(stack(seed, 1L)));
        TrinityCraftingGraphPattern fuelPattern = pattern(
                "b-fuel",
                Items.MAP,
                List.of(stack(rawFuel, 1L)),
                List.of(stack(fuel, 1L)));
        TrinityCraftingGraphPattern cyclePattern = pattern(
                "c-cycle",
                Items.BOOK,
                List.of(stack(seed, 1L), stack(fuel, 1L)),
                List.of(stack(seed, 2L)));
        TrinityCraftingGraphPattern downstreamPattern = pattern(
                "d-downstream",
                Items.COMPASS,
                List.of(stack(seed, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                41L,
                List.of(seedPattern, fuelPattern, cyclePattern, downstreamPattern));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(100L),
                CraftingQuantityMode.NET_NEW,
                Map.of(rawSeed, BigInteger.ONE, rawFuel, BigInteger.valueOf(100L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful());
        TrinityCraftingPlan plan = result.value();
        assertEquals(BigInteger.ONE, plan.initialExpectedInputs().get(rawSeed));
        assertEquals(BigInteger.valueOf(100L), plan.initialExpectedInputs().get(rawFuel));
        assertFalse(plan.initialExpectedInputs().containsKey(seed));
        assertFalse(plan.initialExpectedInputs().containsKey(fuel));
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(seed));
        assertEquals(BigInteger.valueOf(100L), plan.minimumSeed().get(fuel));
        assertEquals(BigInteger.valueOf(100L), plan.targetNetChange().get(target));
        assertEquals(BigInteger.valueOf(100L), plan.patternFirings().get(cyclePattern.identity()));
        assertEquals(BigInteger.valueOf(100L), plan.patternFirings().get(downstreamPattern.identity()));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(plan.statistics().scheduleStates() < 32);

        int seedStage = stageIndex(plan, seedPattern.identity());
        int fuelStage = stageIndex(plan, fuelPattern.identity());
        int cycleStage = stageIndex(plan, cyclePattern.identity());
        int downstreamStage = stageIndex(plan, downstreamPattern.identity());
        assertEquals(
                target,
                plan.stages().stream()
                        .filter(stage -> stage.index() == downstreamStage)
                        .findFirst()
                        .orElseThrow()
                        .firings()
                        .getFirst()
                        .primaryOutput());
        assertTrue(seedStage < cycleStage);
        assertTrue(fuelStage < cycleStage);
        assertTrue(cycleStage < downstreamStage);
        assertTrue(plan.stages().get(cycleStage).cycleStage());
    }

    @Test
    void plansLargeAcyclicRequestWithGraphBoundedStateCount() {
        AEKey raw = AEItemKey.of(Items.REDSTONE);
        AEKey intermediate = AEItemKey.of(Items.IRON_INGOT);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern upstream = pattern(
                "large-upstream",
                Items.PAPER,
                List.of(stack(raw, 3L)),
                List.of(stack(intermediate, 2L)));
        TrinityCraftingGraphPattern downstream = pattern(
                "large-downstream",
                Items.MAP,
                List.of(stack(intermediate, 5L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                42L,
                List.of(upstream, downstream));
        BigInteger requested = BigInteger.valueOf(1_000_000L);

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(raw, BigInteger.valueOf(7_500_000L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful());
        TrinityCraftingPlan plan = result.value();
        assertEquals(BigInteger.valueOf(2_500_000L), plan.patternFirings().get(upstream.identity()));
        assertEquals(requested, plan.patternFirings().get(downstream.identity()));
        assertEquals(BigInteger.valueOf(7_500_000L), plan.initialExpectedInputs().get(raw));
        assertEquals(requested, plan.targetNetChange().get(target));
        assertTrue(plan.statistics().scheduleStates() < 16);
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 10_000L, 1_256_000_000L, Integer.MAX_VALUE })
    void plansDeterministicMultiStepGrowthAcrossThePlayerRequestDomain(long requestedAmount) {
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        BigInteger repetitions = requested.add(BigInteger.valueOf(127L)).divide(BigInteger.valueOf(128L));
        AEKey certus = AEItemKey.of(Items.QUARTZ);
        AEKey chargedCertus = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey certusDust = AEItemKey.of(Items.REDSTONE);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);
        TrinityCraftingGraphPattern charge = pattern(
                "growth-charge",
                Items.PAPER,
                List.of(stack(certus, 64L), stack(water, 1_000L)),
                List.of(stack(chargedCertus, 64L)));
        TrinityCraftingGraphPattern pulverize = pattern(
                "growth-pulverize",
                Items.MAP,
                List.of(stack(certus, 1L)),
                List.of(stack(certusDust, 1L)));
        TrinityCraftingGraphPattern react = pattern(
                "growth-react",
                Items.BOOK,
                List.of(stack(chargedCertus, 16L), stack(certusDust, 16L), stack(water, 500L)),
                List.of(stack(certus, 64L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                43L,
                List.of(charge, pulverize, react));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                certus,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        chargedCertus, BigInteger.valueOf(256L),
                        certusDust, BigInteger.valueOf(320L),
                        water, BigInteger.valueOf(2_147_483_647_000L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(repetitions, plan.patternFirings().get(charge.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(64L)), plan.patternFirings().get(pulverize.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(4L)), plan.patternFirings().get(react.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(128L)), plan.targetNetChange().get(certus));
        assertEquals(repetitions.multiply(BigInteger.valueOf(3_000L)), plan.initialExpectedInputs().get(water));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(chargedCertus));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(certusDust));
        assertFalse(plan.targetNetChange().containsKey(chargedCertus));
        assertFalse(plan.targetNetChange().containsKey(certusDust));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(
                plan.statistics().scheduleStates() <= 128,
                () -> "compressed states=" + plan.statistics().scheduleStates());
    }

    @Test
    void reportsArithmeticOverflowBeforeCrossingAnAe2LongBoundary() {
        AEKey raw = AEItemKey.of(Items.REDSTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                42L,
                List.of(pattern(
                        "linear",
                        Items.PAPER,
                        List.of(stack(raw, 1L)),
                        List.of(stack(target, 1L)))));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                CraftingQuantityMode.NET_NEW,
                Map.of(raw, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW, result.diagnostic().code());
    }

    private static int stageIndex(TrinityCraftingPlan plan, TrinityPatternIdentity identity) {
        for (TrinityPlanStage stage : plan.stages()) {
            if (stage.firings().stream().anyMatch(firing -> firing.patternIdentity().equals(identity))) {
                return stage.index();
            }
        }
        throw new AssertionError("Pattern stage is absent: " + identity);
    }

    private static TrinityCraftingGraphPattern pattern(
                                                       String name,
                                                       Item definition,
                                                       List<GenericStack> inputs,
                                                       List<GenericStack> outputs) {
        ArrayList<TrinityPatternPublicationSignature.Input> capturedInputs = new ArrayList<>();
        for (GenericStack input : inputs) {
            capturedInputs.add(new TrinityPatternPublicationSignature.Input(
                    1L,
                    List.of(new TrinityPatternPublicationSignature.Alternative(input, null))));
        }
        TrinityPatternPublicationSignature publication = new TrinityPatternPublicationSignature(
                AEItemKey.of(definition),
                capturedInputs,
                outputs,
                false);
        return new TrinityCraftingGraphPattern(
                new TrinityPatternIdentity("definition-" + name, "publication-" + name),
                publication);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static TrinityPlanningControl unlimitedControl() {
        return TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE);
    }
}
