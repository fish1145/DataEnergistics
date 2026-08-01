package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDeterministicCyclePlannerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void solvesSelfMultiplicationWithOneSeedAndOneCompressedBatch() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        BigInteger request = BigInteger.TEN.pow(100);

        TrinityCyclePlan plan = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(multiply, BigInteger.ONE)),
                        a,
                        request,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE),
                        32,
                        unlimitedControl())
                .value();

        assertEquals(request, plan.repetitions());
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(BigInteger.ONE, plan.initialInputs().get(a));
        assertEquals(request, plan.netChange().get(a));
        assertEquals(request, plan.aggregateFirings().get(multiply));
        assertEquals(List.of(new TrinityVariantFiring(multiply, request)), plan.schedule().batches());
        assertTrue(plan.schedule().statesVisited() <= 2);
    }

    @Test
    void schedulesMultiStepMultiplicationAtBalanceBreakpointsInsteadOfPerFiring() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant aToB = variant(
                "a-to-b",
                amounts(a, BigInteger.ONE),
                amounts(b, BigInteger.ONE));
        TrinityPatternVariant bToTwoA = variant(
                "b-to-two-a",
                amounts(b, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        BigInteger request = BigInteger.TWO.pow(30);

        TrinityCyclePlan plan = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(
                                new TrinityVariantFiring(aToB, BigInteger.ONE),
                                new TrinityVariantFiring(bToTwoA, BigInteger.ONE)),
                        a,
                        request,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE),
                        256,
                        unlimitedControl())
                .value();

        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(request, plan.aggregateFirings().get(aToB));
        assertEquals(request, plan.aggregateFirings().get(bToTwoA));
        assertEquals(request, plan.netChange().get(a));
        assertTrue(plan.schedule().batches().size() <= 62);
        assertTrue(plan.schedule().statesVisited() <= 63);
        assertEquals(request.add(BigInteger.ONE), plan.schedule().finalBalances().get(a));
    }

    @Test
    void includesExternalFuelAcrossEverySelfMultiplicationRepetition() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.valueOf(7L)),
                amounts(a, BigInteger.TWO));
        BigInteger request = BigInteger.valueOf(100L);
        BigInteger requiredFuel = BigInteger.valueOf(700L);

        TrinityCyclePlan plan = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(fuelled, BigInteger.ONE)),
                        a,
                        request,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE, fuel, requiredFuel),
                        16,
                        unlimitedControl())
                .value();

        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(requiredFuel, plan.minimumSeed().get(fuel));
        assertEquals(requiredFuel.negate(), plan.netChange().get(fuel));
        assertEquals(List.of(new TrinityVariantFiring(fuelled, request)), plan.schedule().batches());

        TrinityAlgorithmResult<TrinityCyclePlan> shortage = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(fuelled, BigInteger.ONE)),
                        a,
                        request,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE, fuel, BigInteger.valueOf(384L)),
                        16,
                        unlimitedControl());

        assertFalse(shortage.successful());
        assertEquals(TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT, shortage.diagnostic().code());
        assertEquals("net_consumed_external_input", shortage.diagnostic().metadata().get("input_role"));
        assertEquals("700", shortage.diagnostic().metadata().get("required"));
        assertEquals("384", shortage.diagnostic().metadata().get("available"));
        assertEquals("316", shortage.diagnostic().metadata().get("missing"));
        assertEquals("700", shortage.diagnostic().metadata().get("net_consumed"));
        assertEquals(
                "gui.data_energistics.trinity_planning.missing_external_input",
                assertInstanceOf(TranslatableContents.class, shortage.diagnostic().message().getContents()).getKey());
    }

    @Test
    void finalTotalRunsOneCycleAndReservesOnlyTheNeededTargetContribution() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityCyclePlan plan = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(multiply, BigInteger.ONE)),
                        a,
                        BigInteger.valueOf(5L),
                        CraftingQuantityMode.FINAL_TOTAL,
                        Map.of(a, BigInteger.TEN),
                        16,
                        unlimitedControl())
                .value();

        assertEquals(BigInteger.ONE, plan.repetitions());
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(BigInteger.valueOf(4L), plan.initialInputs().get(a));
        assertEquals(BigInteger.ONE, plan.netChange().get(a));
        assertEquals(BigInteger.valueOf(5L),
                plan.initialInputs().get(a).add(plan.netChange().get(a)));
    }

    @Test
    void rejectsMissingCycleSeedAndNonProductiveCycle() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityAlgorithmResult<TrinityCyclePlan> missingSeed = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(multiply, BigInteger.ONE)),
                        a,
                        BigInteger.TEN,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(),
                        16,
                        unlimitedControl());
        TrinityPatternVariant neutral = variant(
                "neutral",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.ONE));
        TrinityAlgorithmResult<TrinityCyclePlan> nonProductive = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(neutral, BigInteger.ONE)),
                        a,
                        BigInteger.TEN,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE),
                        16,
                        unlimitedControl());

        assertFalse(missingSeed.successful());
        assertEquals(TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT, missingSeed.diagnostic().code());
        assertEquals("target_cycle_seed", missingSeed.diagnostic().metadata().get("input_role"));
        assertEquals("1", missingSeed.diagnostic().metadata().get("required"));
        assertEquals("0", missingSeed.diagnostic().metadata().get("available"));
        assertEquals("1", missingSeed.diagnostic().metadata().get("missing"));
        assertEquals(
                "gui.data_energistics.trinity_planning.missing_target_cycle_seed",
                assertInstanceOf(TranslatableContents.class, missingSeed.diagnostic().message().getContents()).getKey());
        assertFalse(nonProductive.successful());
        assertEquals(TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE, nonProductive.diagnostic().code());
    }

    @Test
    void schedulerReportsStateLimitCancellationTimeoutAndNoOrderSeparately() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant aToB = variant(
                "a-to-b",
                amounts(a, BigInteger.ONE),
                amounts(b, BigInteger.ONE));
        TrinityPatternVariant bToTwoA = variant(
                "b-to-two-a",
                amounts(b, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        Map<TrinityPatternVariant, BigInteger> firings = Map.of(aToB, BigInteger.TEN, bToTwoA, BigInteger.TEN);
        TrinityCompressedScheduler scheduler = TrinityCompressedScheduler.create();

        TrinityAlgorithmResult<TrinityCompressedSchedule> limited = scheduler.schedule(firings, Map.of(a, BigInteger.ONE), 1, unlimitedControl());
        TrinityAlgorithmResult<TrinityCompressedSchedule> cancelled = scheduler.schedule(
                firings,
                Map.of(a, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> true, () -> 0L, 1L));
        AtomicLong clock = new AtomicLong();
        TrinityPlanningControl timeoutControl = TrinityPlanningControl.create(
                () -> false,
                () -> clock.getAndAdd(10L),
                5L);
        TrinityAlgorithmResult<TrinityCompressedSchedule> timedOut = scheduler.schedule(firings, Map.of(a, BigInteger.ONE), 100, timeoutControl);
        TrinityAlgorithmResult<TrinityCompressedSchedule> noOrder = scheduler.schedule(
                Map.of(aToB, BigInteger.ONE),
                Map.of(),
                100,
                unlimitedControl());

        assertEquals(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, limited.diagnostic().code());
        assertEquals(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, cancelled.diagnostic().code());
        assertEquals(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, timedOut.diagnostic().code());
        assertEquals("timeout", timedOut.diagnostic().metadata().get("reason"));
        assertEquals(TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER, noOrder.diagnostic().code());
    }

    @Test
    void cycleValueRejectsForgedNetChangeAndFinalBalance() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityCyclePlan valid = TrinityDeterministicCyclePlanner.create()
                .plan(
                        List.of(new TrinityVariantFiring(multiply, BigInteger.ONE)),
                        a,
                        BigInteger.TEN,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(a, BigInteger.ONE),
                        16,
                        unlimitedControl())
                .value();

        assertThrows(IllegalArgumentException.class, () -> new TrinityCyclePlan(
                valid.oneCycleOrder(),
                valid.repetitions(),
                valid.aggregateFirings(),
                valid.minimumSeed(),
                valid.initialInputs(),
                Map.of(a, BigInteger.valueOf(9L)),
                valid.schedule()));
        assertThrows(IllegalArgumentException.class, () -> new TrinityCyclePlan(
                valid.oneCycleOrder(),
                valid.repetitions(),
                valid.aggregateFirings(),
                valid.minimumSeed(),
                valid.initialInputs(),
                valid.netChange(),
                new TrinityCompressedSchedule(
                        valid.schedule().batches(),
                        Map.of(a, BigInteger.valueOf(99L)),
                        valid.schedule().statesVisited())));
    }

    private static TrinityPlanningControl unlimitedControl() {
        return TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE);
    }
}
