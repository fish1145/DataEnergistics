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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.configuration.snapshot.TrinityCraftingSettings;

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
import java.util.LinkedHashMap;
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
                TrinityCraftingSettings.defaults(4),
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
    void treatsUpstreamMultiStepCycleDemandAsFinalBalanceForADagTarget() {
        AEKey material = AEItemKey.of(Items.IRON_INGOT);
        AEKey charged = AEItemKey.of(Items.GOLD_INGOT);
        AEKey dust = AEItemKey.of(Items.REDSTONE);
        AEKey fuel = AEItemKey.of(Items.STICK);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        AEKey residualTarget = AEItemKey.of(Items.EMERALD);
        TrinityCraftingGraphPattern charge = pattern(
                "upstream-charge",
                Items.PAPER,
                List.of(stack(material, 1L)),
                List.of(stack(charged, 1L)));
        TrinityCraftingGraphPattern pulverize = pattern(
                "upstream-pulverize",
                Items.MAP,
                List.of(stack(material, 1L)),
                List.of(stack(dust, 1L)));
        TrinityCraftingGraphPattern grow = pattern(
                "upstream-grow",
                Items.BOOK,
                List.of(stack(charged, 1L), stack(dust, 1L), stack(fuel, 1L)),
                List.of(stack(material, 4L)));
        TrinityCraftingGraphPattern finish = pattern(
                "downstream-finish",
                Items.COMPASS,
                List.of(stack(material, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphPattern residualFinish = pattern(
                "downstream-residual-finish",
                Items.CLOCK,
                List.of(stack(material, 1L), stack(charged, 1L), stack(dust, 1L)),
                List.of(stack(residualTarget, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                42L,
                List.of(charge, pulverize, grow, finish, residualFinish));

        TrinityAlgorithmResult<TrinityCraftingPlan> fullyStocked = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(2L),
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.valueOf(2L)),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(fullyStocked.successful(), () -> fullyStocked.diagnostic().message().getString());
        assertTrue(fullyStocked.value().cycleRepeatBlocks().isEmpty());
        assertEquals(BigInteger.valueOf(2L), fullyStocked.value().initialExpectedInputs().get(material));
        assertEquals(BigInteger.valueOf(2L), fullyStocked.value().patternFirings().get(finish.identity()));
        assertFalse(fullyStocked.value().patternFirings().containsKey(grow.identity()));

        TrinityAlgorithmResult<TrinityCraftingPlan> partiallyStocked = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(4L),
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.valueOf(2L), fuel, BigInteger.ONE),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(partiallyStocked.successful(), () -> partiallyStocked.diagnostic().message().getString());
        TrinityCraftingPlan partialPlan = partiallyStocked.value();
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(charge.identity()));
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(pulverize.identity()));
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(grow.identity()));
        assertEquals(BigInteger.valueOf(4L), partialPlan.patternFirings().get(finish.identity()));
        assertEquals(BigInteger.valueOf(2L), partialPlan.initialExpectedInputs().get(material));
        assertEquals(BigInteger.ONE, partialPlan.initialExpectedInputs().get(fuel));
        assertEquals(1, partialPlan.cycleRepeatBlocks().size());
        assertTrue(stageIndex(partialPlan, grow.identity()) < stageIndex(partialPlan, finish.identity()));

        TrinityAlgorithmResult<TrinityCraftingPlan> residualOutputs = TrinityGraphPlanner.create().plan(
                snapshot,
                residualTarget,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.TWO, fuel, BigInteger.ONE),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(residualOutputs.successful(), () -> residualOutputs.diagnostic().message().getString());
        TrinityCraftingPlan residualPlan = residualOutputs.value();
        assertEquals(BigInteger.TWO, residualPlan.patternFirings().get(charge.identity()));
        assertEquals(BigInteger.TWO, residualPlan.patternFirings().get(pulverize.identity()));
        assertEquals(BigInteger.ONE, residualPlan.patternFirings().get(grow.identity()));
        assertEquals(BigInteger.ONE, residualPlan.patternFirings().get(residualFinish.identity()));
        assertEquals(BigInteger.TWO, residualPlan.initialExpectedInputs().get(material));
        assertEquals(BigInteger.ONE, residualPlan.initialExpectedInputs().get(fuel));
        assertEquals(1, residualPlan.cycleRepeatBlocks().size());
        TrinityCycleRepeatBlock repeatBlock = residualPlan.cycleRepeatBlocks().getFirst();
        assertEquals(BigInteger.ONE, repeatBlock.repetitions());
        assertEquals(Map.of(
                material, BigInteger.TWO,
                fuel, BigInteger.ONE.negate()), repeatBlock.netChange());
        assertEquals(residualPlan.patternFirings(), expandedPatternFirings(residualPlan));
        int terminalStage = stageIndex(residualPlan, residualFinish.identity());
        assertTrue(repeatBlock.stageOrder().getLast() < terminalStage);
    }

    @Test
    void composesSerialAndParallelCycleComponentsWithoutConsumingAnUpstreamSeedEarly() {
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey firstFuel = AEItemKey.of(Items.STICK);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        AEKey parallel = AEItemKey.of(Items.COPPER_INGOT);
        AEKey parallelFuel = AEItemKey.of(Items.COAL);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern firstGrowth = pattern(
                "first-growth",
                Items.PAPER,
                List.of(stack(first, 1L), stack(firstFuel, 1L)),
                List.of(stack(first, 2L)));
        TrinityCraftingGraphPattern secondGrowth = pattern(
                "second-growth",
                Items.MAP,
                List.of(stack(second, 1L), stack(first, 1L)),
                List.of(stack(second, 2L)));
        TrinityCraftingGraphPattern parallelGrowth = pattern(
                "parallel-growth",
                Items.BOOK,
                List.of(stack(parallel, 1L), stack(parallelFuel, 1L)),
                List.of(stack(parallel, 2L)));
        TrinityCraftingGraphPattern finish = pattern(
                "multi-cycle-finish",
                Items.COMPASS,
                List.of(stack(second, 1L), stack(parallel, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                43L,
                List.of(firstGrowth, secondGrowth, parallelGrowth, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(3L),
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        first, BigInteger.ONE,
                        firstFuel, BigInteger.ONE,
                        second, BigInteger.ONE,
                        parallel, BigInteger.ONE,
                        parallelFuel, BigInteger.TWO),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(3, plan.cycleRepeatBlocks().size());
        assertEquals(BigInteger.ONE, plan.patternFirings().get(firstGrowth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(secondGrowth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(parallelGrowth.identity()));
        assertEquals(BigInteger.valueOf(3L), plan.patternFirings().get(finish.identity()));
        assertTrue(stageIndex(plan, firstGrowth.identity()) < stageIndex(plan, secondGrowth.identity()));
        assertTrue(stageIndex(plan, secondGrowth.identity()) < stageIndex(plan, finish.identity()));
        assertTrue(stageIndex(plan, parallelGrowth.identity()) < stageIndex(plan, finish.identity()));
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 100_000_000L })
    void jointlySolvesMultipleSccAxesAndBoundaryOutputsForADagTarget(long requestedAmount) {
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        AEKey boundary = AEItemKey.of(Items.REDSTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern firstToSecond = pattern(
                "joint-first-to-second",
                Items.PAPER,
                List.of(stack(first, 1L)),
                List.of(stack(second, 2L), stack(boundary, 1L)));
        TrinityCraftingGraphPattern secondToFirst = pattern(
                "joint-second-to-first",
                Items.MAP,
                List.of(stack(second, 1L)),
                List.of(stack(first, 2L)));
        TrinityCraftingGraphPattern finish = pattern(
                "joint-boundary-finish",
                Items.COMPASS,
                List.of(stack(first, 1L), stack(second, 3L), stack(boundary, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                44L,
                List.of(firstToSecond, secondToFirst, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(first, BigInteger.ONE),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        BigInteger firstFirings = requested.multiply(BigInteger.valueOf(7L))
                .subtract(BigInteger.ONE)
                .add(BigInteger.TWO)
                .divide(BigInteger.valueOf(3L));
        BigInteger secondFirings = firstFirings.add(requested)
                .divide(BigInteger.TWO);
        assertEquals(firstFirings, plan.patternFirings().get(firstToSecond.identity()));
        assertEquals(secondFirings, plan.patternFirings().get(secondToFirst.identity()));
        assertEquals(requested, plan.patternFirings().get(finish.identity()));
        assertEquals(BigInteger.ONE, plan.initialExpectedInputs().get(first));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(stageIndex(plan, firstToSecond.identity()) < stageIndex(plan, finish.identity()));
    }

    @Test
    void backtracksFromAnUnavailableMixedRouteToACycleBackedRoute() {
        AEKey unavailable = AEItemKey.of(Items.EMERALD);
        AEKey material = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.STICK);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern unavailableRoute = pattern(
                "a-unavailable-route",
                Items.PAPER,
                List.of(stack(unavailable, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphPattern growth = pattern(
                "material-growth",
                Items.MAP,
                List.of(stack(material, 1L), stack(fuel, 1L)),
                List.of(stack(material, 2L)));
        TrinityCraftingGraphPattern cycleBackedRoute = pattern(
                "z-cycle-backed-route",
                Items.COMPASS,
                List.of(stack(material, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                45L,
                List.of(unavailableRoute, growth, cycleBackedRoute));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.TWO,
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.ONE, fuel, BigInteger.ONE),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertFalse(plan.patternFirings().containsKey(unavailableRoute.identity()));
        assertEquals(BigInteger.ONE, plan.patternFirings().get(growth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(cycleBackedRoute.identity()));
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
        List<TrinityCraftingGraphPattern> graphPatterns = new ArrayList<>(List.of(upstream, downstream));
        for (int index = 0; index < 600; index++) {
            graphPatterns.add(pattern(
                    "unrelated-" + index,
                    Items.STICK,
                    List.of(stack(AEItemKey.of(Items.DIRT), 1L)),
                    List.of(stack(AEItemKey.of(Items.COAL), 1L))));
        }
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(42L, graphPatterns);
        BigInteger requested = BigInteger.valueOf(1_000_000L);

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(raw, BigInteger.valueOf(7_500_000L)),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful());
        TrinityCraftingPlan plan = result.value();
        assertEquals(BigInteger.valueOf(2_500_000L), plan.patternFirings().get(upstream.identity()));
        assertEquals(requested, plan.patternFirings().get(downstream.identity()));
        assertEquals(BigInteger.valueOf(7_500_000L), plan.initialExpectedInputs().get(raw));
        assertEquals(requested, plan.targetNetChange().get(target));
        assertEquals(2, plan.statistics().variantCount());
        assertTrue(plan.statistics().scheduleStates() < 16);
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 10_000L, 1_256_000_000L, Integer.MAX_VALUE })
    void plansAnAcyclicTargetOverMultiStepGrowthAcrossThePlayerRequestDomain(long requestedAmount) {
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        BigInteger repetitions = requested.add(BigInteger.valueOf(127L)).divide(BigInteger.valueOf(128L));
        AEKey certus = AEItemKey.of(Items.QUARTZ);
        AEKey chargedCertus = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey certusDust = AEItemKey.of(Items.REDSTONE);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);
        AEKey target = AEItemKey.of(Items.DIAMOND);
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
        TrinityCraftingGraphPattern finish = pattern(
                "growth-finish",
                Items.COMPASS,
                List.of(stack(certus, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                43L,
                List.of(charge, pulverize, react, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        chargedCertus, BigInteger.valueOf(256L),
                        certusDust, BigInteger.valueOf(320L),
                        water, BigInteger.valueOf(2_147_483_647_000L)),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(repetitions, plan.patternFirings().get(charge.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(64L)), plan.patternFirings().get(pulverize.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(4L)), plan.patternFirings().get(react.identity()));
        assertEquals(requested, plan.patternFirings().get(finish.identity()));
        assertEquals(requested, plan.targetNetChange().get(target));
        assertEquals(
                repetitions.multiply(BigInteger.valueOf(128L)).subtract(requested),
                plan.targetNetChange().getOrDefault(certus, BigInteger.ZERO));
        assertEquals(repetitions.multiply(BigInteger.valueOf(3_000L)), plan.initialExpectedInputs().get(water));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(chargedCertus));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(certusDust));
        assertFalse(plan.targetNetChange().containsKey(chargedCertus));
        assertFalse(plan.targetNetChange().containsKey(certusDust));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertEquals(repetitions, plan.cycleRepeatBlocks().getFirst().repetitions());
        assertEquals(3, plan.cycleRepeatBlocks().getFirst().stageOrder().size());
        assertTrue(stageIndex(plan, react.identity()) < stageIndex(plan, finish.identity()));
        assertTrue(
                plan.statistics().scheduleStates() <= 128,
                () -> "compressed states=" + plan.statistics().scheduleStates());
    }

    @Test
    void plansACompleteTwoHundredFiftySixMegabyteChainFromFiniteRawInventory() {
        AEKey rawCharged = AEItemKey.of(Items.IRON_INGOT);
        AEKey rawDust = AEItemKey.of(Items.GOLD_INGOT);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);
        AEKey casing = AEItemKey.of(Items.COPPER_INGOT);
        AEKey crystal = AEItemKey.of(Items.QUARTZ);
        AEKey charged = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey dust = AEItemKey.of(Items.REDSTONE);
        AEKey growthByproduct = AEItemKey.of(Items.GLOWSTONE_DUST);
        AEKey oneMegabyte = AEItemKey.of(Items.PAPER);
        AEKey fourMegabyte = AEItemKey.of(Items.MAP);
        AEKey sixteenMegabyte = AEItemKey.of(Items.BOOK);
        AEKey sixtyFourMegabyte = AEItemKey.of(Items.CLOCK);
        AEKey target = AEItemKey.of(Items.DIAMOND);

        TrinityCraftingGraphPattern chargedSeed = pattern(
                "full-256m-charged-seed",
                Items.IRON_NUGGET,
                List.of(stack(rawCharged, 64L)),
                List.of(stack(charged, 64L)));
        TrinityCraftingGraphPattern dustSeed = pattern(
                "full-256m-dust-seed",
                Items.GOLD_NUGGET,
                List.of(stack(rawDust, 64L)),
                List.of(stack(dust, 64L)));
        TrinityCraftingGraphPattern charge = pattern(
                "full-256m-charge",
                Items.AMETHYST_BLOCK,
                List.of(stack(crystal, 64L), stack(water, 1_000L)),
                List.of(stack(charged, 64L)));
        TrinityCraftingGraphPattern pulverize = pattern(
                "full-256m-pulverize",
                Items.REDSTONE_BLOCK,
                List.of(stack(crystal, 1L)),
                List.of(stack(dust, 1L)));
        TrinityCraftingGraphPattern grow = pattern(
                "full-256m-grow",
                Items.QUARTZ_BLOCK,
                List.of(stack(charged, 16L), stack(dust, 16L), stack(water, 500L)),
                List.of(stack(crystal, 64L), stack(growthByproduct, 1L)));
        TrinityCraftingGraphPattern craftOneMegabyte = pattern(
                "full-256m-1m",
                Items.PAPER,
                List.of(
                        stack(crystal, 128L),
                        stack(charged, 1L),
                        stack(dust, 1L),
                        stack(growthByproduct, 4L),
                        stack(casing, 1L)),
                List.of(stack(oneMegabyte, 1L)));
        TrinityCraftingGraphPattern craftFourMegabyte = pattern(
                "full-256m-4m",
                Items.MAP,
                List.of(stack(oneMegabyte, 3L), stack(crystal, 8L), stack(casing, 1L)),
                List.of(stack(fourMegabyte, 1L)));
        TrinityCraftingGraphPattern craftSixteenMegabyte = pattern(
                "full-256m-16m",
                Items.BOOK,
                List.of(stack(fourMegabyte, 3L), stack(crystal, 8L), stack(casing, 1L)),
                List.of(stack(sixteenMegabyte, 1L)));
        TrinityCraftingGraphPattern craftSixtyFourMegabyte = pattern(
                "full-256m-64m",
                Items.CLOCK,
                List.of(stack(sixteenMegabyte, 3L), stack(crystal, 8L), stack(casing, 1L)),
                List.of(stack(sixtyFourMegabyte, 1L)));
        TrinityCraftingGraphPattern craftTarget = pattern(
                "full-256m-256m",
                Items.COMPASS,
                List.of(stack(sixtyFourMegabyte, 3L), stack(crystal, 8L), stack(casing, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                256L,
                List.of(
                        chargedSeed,
                        dustSeed,
                        charge,
                        pulverize,
                        grow,
                        craftOneMegabyte,
                        craftFourMegabyte,
                        craftSixteenMegabyte,
                        craftSixtyFourMegabyte,
                        craftTarget));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        rawCharged, BigInteger.valueOf(64L),
                        rawDust, BigInteger.valueOf(64L),
                        water, BigInteger.valueOf(260_000L),
                        casing, BigInteger.valueOf(121L)),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(Map.of(
                rawCharged, BigInteger.valueOf(64L),
                rawDust, BigInteger.valueOf(64L),
                water, BigInteger.valueOf(257_500L),
                casing, BigInteger.valueOf(121L)), plan.initialExpectedInputs());
        assertEquals(Map.of(
                chargedSeed.identity(), BigInteger.ONE,
                dustSeed.identity(), BigInteger.ONE,
                charge.identity(), BigInteger.valueOf(87L),
                pulverize.identity(), BigInteger.valueOf(5_537L),
                grow.identity(), BigInteger.valueOf(341L),
                craftOneMegabyte.identity(), BigInteger.valueOf(81L),
                craftFourMegabyte.identity(), BigInteger.valueOf(27L),
                craftSixteenMegabyte.identity(), BigInteger.valueOf(9L),
                craftSixtyFourMegabyte.identity(), BigInteger.valueOf(3L),
                craftTarget.identity(), BigInteger.ONE), plan.patternFirings());
        assertEquals(Map.of(
                charged, BigInteger.valueOf(32L),
                dust, BigInteger.valueOf(16L)), plan.minimumSeed());
        assertEquals(Map.of(
                rawCharged, BigInteger.valueOf(-64L),
                rawDust, BigInteger.valueOf(-64L),
                water, BigInteger.valueOf(-257_500L),
                casing, BigInteger.valueOf(-121L),
                charged, BigInteger.valueOf(95L),
                dust, BigInteger.valueOf(64L),
                crystal, BigInteger.valueOf(31L),
                growthByproduct, BigInteger.valueOf(17L),
                target, BigInteger.ONE), plan.targetNetChange());
        LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>(plan.initialExpectedInputs());
        plan.targetNetChange().forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        assertEquals(Map.of(
                charged, BigInteger.valueOf(95L),
                dust, BigInteger.valueOf(64L),
                crystal, BigInteger.valueOf(31L),
                growthByproduct, BigInteger.valueOf(17L),
                target, BigInteger.ONE), finalBalances);
        assertEquals(1, plan.cycleRepeatBlocks().size());
        var repeatBlock = plan.cycleRepeatBlocks().getFirst();
        assertTrue(repeatBlock.repetitions().signum() > 0);
        assertTrue(repeatBlock.stageOrder().size() <= 6);
        assertEquals(plan.patternFirings(), expandedPatternFirings(plan));
        assertTrue(
                plan.statistics().scheduleStates() <= 256,
                () -> "Compressed 256M schedule visited " + plan.statistics().scheduleStates() + " states");
    }

    @Test
    void plansDeepAcyclicTargetsThatConsumeMultipleOutputsOfOneGrowthCycle() {
        AEKey crystal = AEItemKey.of(Items.QUARTZ);
        AEKey charged = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey dust = AEItemKey.of(Items.SUGAR);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);
        AEKey glass = AEItemKey.of(Items.GLASS);
        AEKey redstone = AEItemKey.of(Items.REDSTONE);
        AEKey glowstone = AEItemKey.of(Items.GLOWSTONE_DUST);
        AEKey skyDust = AEItemKey.of(Items.LAPIS_LAZULI);
        AEKey enderDust = AEItemKey.of(Items.BLAZE_POWDER);
        AEKey matter = AEItemKey.of(Items.ENDER_PEARL);
        AEKey logic = AEItemKey.of(Items.COMPARATOR);
        AEKey calculation = AEItemKey.of(Items.REPEATER);
        AEKey accumulation = AEItemKey.of(Items.NETHER_STAR);
        AEKey quartzGlass = AEItemKey.of(Items.WHITE_STAINED_GLASS);
        AEKey vibrantGlass = AEItemKey.of(Items.TINTED_GLASS);
        AEKey component1k = AEItemKey.of(Items.IRON_NUGGET);
        AEKey component4k = AEItemKey.of(Items.GOLD_NUGGET);
        AEKey component16k = AEItemKey.of(Items.COPPER_INGOT);
        AEKey component64k = AEItemKey.of(Items.IRON_INGOT);
        AEKey component256k = AEItemKey.of(Items.GOLD_INGOT);
        AEKey component1m = AEItemKey.of(Items.COPPER_BLOCK);
        AEKey component4m = AEItemKey.of(Items.IRON_BLOCK);
        AEKey component16m = AEItemKey.of(Items.GOLD_BLOCK);
        AEKey component64m = AEItemKey.of(Items.EMERALD);
        AEKey component256m = AEItemKey.of(Items.DIAMOND);

        List<TrinityCraftingGraphPattern> patterns = List.of(
                pattern("deep-charge", Items.PAPER,
                        List.of(stack(crystal, 64L), stack(water, 1_000L)),
                        List.of(stack(charged, 64L))),
                pattern("deep-pulverize", Items.MAP,
                        List.of(stack(crystal, 1L)),
                        List.of(stack(dust, 1L))),
                pattern("deep-grow", Items.BOOK,
                        List.of(stack(charged, 16L), stack(dust, 16L), stack(water, 500L)),
                        List.of(stack(crystal, 64L))),
                pattern("deep-quartz-glass", Items.GLASS_PANE,
                        List.of(stack(dust, 5L), stack(glass, 4L)),
                        List.of(stack(quartzGlass, 4L))),
                pattern("deep-vibrant-glass", Items.GLOWSTONE,
                        List.of(stack(glowstone, 2L), stack(quartzGlass, 1L)),
                        List.of(stack(vibrantGlass, 1L))),
                pattern("deep-1k", Items.IRON_NUGGET,
                        List.of(stack(crystal, 4L), stack(redstone, 4L), stack(logic, 1L)),
                        List.of(stack(component1k, 1L))),
                pattern("deep-4k", Items.GOLD_NUGGET,
                        List.of(stack(component1k, 3L), stack(redstone, 4L), stack(calculation, 1L),
                                stack(quartzGlass, 1L)),
                        List.of(stack(component4k, 1L))),
                pattern("deep-16k", Items.COPPER_INGOT,
                        List.of(stack(component4k, 3L), stack(glowstone, 4L), stack(calculation, 1L),
                                stack(quartzGlass, 1L)),
                        List.of(stack(component16k, 1L))),
                pattern("deep-64k", Items.IRON_INGOT,
                        List.of(stack(component16k, 3L), stack(glowstone, 4L), stack(calculation, 1L),
                                stack(quartzGlass, 1L)),
                        List.of(stack(component64k, 1L))),
                pattern("deep-256k", Items.GOLD_INGOT,
                        List.of(stack(component64k, 3L), stack(skyDust, 4L), stack(calculation, 1L),
                                stack(quartzGlass, 1L)),
                        List.of(stack(component256k, 1L))),
                pattern("deep-1m", Items.COPPER_BLOCK,
                        List.of(stack(component256k, 3L), stack(skyDust, 4L), stack(accumulation, 1L),
                                stack(vibrantGlass, 1L)),
                        List.of(stack(component1m, 1L))),
                pattern("deep-4m", Items.IRON_BLOCK,
                        List.of(stack(component1m, 3L), stack(enderDust, 4L), stack(accumulation, 1L),
                                stack(vibrantGlass, 1L)),
                        List.of(stack(component4m, 1L))),
                pattern("deep-16m", Items.GOLD_BLOCK,
                        List.of(stack(component4m, 3L), stack(enderDust, 4L), stack(accumulation, 1L),
                                stack(vibrantGlass, 1L)),
                        List.of(stack(component16m, 1L))),
                pattern("deep-64m", Items.EMERALD,
                        List.of(stack(component16m, 3L), stack(matter, 4L), stack(accumulation, 1L),
                                stack(vibrantGlass, 1L)),
                        List.of(stack(component64m, 1L))),
                pattern("deep-256m", Items.DIAMOND,
                        List.of(stack(component64m, 3L), stack(matter, 4L), stack(accumulation, 1L),
                                stack(vibrantGlass, 1L)),
                        List.of(stack(component256m, 1L))));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(257L, patterns);
        Map<AEKey, BigInteger> available = Map.ofEntries(
                Map.entry(charged, BigInteger.valueOf(256L)),
                Map.entry(dust, BigInteger.valueOf(320L)),
                Map.entry(water, BigInteger.valueOf(20_000_000L)),
                Map.entry(glass, BigInteger.valueOf(20_000_000L)),
                Map.entry(redstone, BigInteger.valueOf(20_000_000L)),
                Map.entry(glowstone, BigInteger.valueOf(20_000_000L)),
                Map.entry(skyDust, BigInteger.valueOf(20_000_000L)),
                Map.entry(enderDust, BigInteger.valueOf(20_000_000L)),
                Map.entry(matter, BigInteger.valueOf(20_000_000L)),
                Map.entry(logic, BigInteger.valueOf(20_000_000L)),
                Map.entry(calculation, BigInteger.valueOf(20_000_000L)),
                Map.entry(accumulation, BigInteger.valueOf(20_000_000L)));

        for (AEKey target : List.of(component64m, component256m)) {
            TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                    snapshot,
                    target,
                    BigInteger.ONE,
                    CraftingQuantityMode.NET_NEW,
                    available,
                    TrinityCraftingSettings.defaults(4),
                    unlimitedControl());

            assertTrue(result.successful(), () -> result.diagnostic().code() + " " + result.diagnostic().metadata());
            assertEquals(BigInteger.ONE, result.value().targetNetChange().get(target));
            assertEquals(1, result.value().cycleRepeatBlocks().size());
            assertTrue(result.value().statistics().scheduleStates() <= 256);
        }
    }

    @Test
    void doesNotExportAnUnsettledIntermediateFromInsideACycle() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        AEKey intermediate = AEItemKey.of(Items.REDSTONE);
        AEKey settledByproduct = AEItemKey.of(Items.GLOWSTONE_DUST);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern expand = pattern(
                "settlement-expand",
                Items.PAPER,
                List.of(stack(a, 1L)),
                List.of(stack(b, 1L), stack(intermediate, 1L)));
        TrinityCraftingGraphPattern close = pattern(
                "settlement-close",
                Items.MAP,
                List.of(stack(b, 1L), stack(intermediate, 1L)),
                List.of(stack(a, 2L), stack(settledByproduct, 1L)));
        TrinityCraftingGraphPattern finish = pattern(
                "settlement-finish",
                Items.COMPASS,
                List.of(stack(intermediate, 1L)),
                List.of(stack(target, 1L)));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                new TrinityCraftingGraphSnapshot(257L, List.of(expand, close, finish)),
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.TWO),
                TrinityCraftingSettings.defaults(4),
                unlimitedControl());

        assertFalse(result.successful(), "A partial firing vector must not expose an internal cycle intermediate");
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
                TrinityCraftingSettings.defaults(4),
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

    private static Map<TrinityPatternIdentity, BigInteger> expandedPatternFirings(TrinityCraftingPlan plan) {
        LinkedHashMap<Integer, BigInteger> stageMultipliers = new LinkedHashMap<>();
        for (TrinityCycleRepeatBlock repeatBlock : plan.cycleRepeatBlocks()) {
            for (Integer stageIndex : repeatBlock.stageOrder()) {
                if (stageMultipliers.put(stageIndex, repeatBlock.repetitions()) != null) {
                    throw new AssertionError("A plan stage cannot belong to multiple repeat blocks");
                }
            }
        }
        LinkedHashMap<TrinityPatternIdentity, BigInteger> firings = new LinkedHashMap<>();
        for (TrinityPlanStage stage : plan.stages()) {
            BigInteger multiplier = stageMultipliers.getOrDefault(stage.index(), BigInteger.ONE);
            stage.firings().forEach(firing -> firings.merge(
                    firing.patternIdentity(),
                    firing.count().multiply(multiplier),
                    BigInteger::add));
        }
        return Map.copyOf(firings);
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
