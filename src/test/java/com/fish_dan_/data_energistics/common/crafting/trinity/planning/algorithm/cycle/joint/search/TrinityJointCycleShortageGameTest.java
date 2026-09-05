package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut.TrinityExternalPrefixCut;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation.TrinityJointCandidateEvaluator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityModel;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityCycleDiagnosticEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
@NullMarked
public final class TrinityJointCycleShortageGameTest {

    private static final int MAX_STATES = 256;

    private TrinityJointCycleShortageGameTest() {}

    @TestHolder("trinity_joint_cycle_stopped_root_retains_exact_large_request_shortage")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stoppedRootRetainsExactLargeRequestShortage(GameTestHelper helper) {
        for (TrinityPlanningDiagnosticCode stop : List.of(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT)) {
            CycleFixture fixture = cycle(100);
            StoppedRootFeasibility model = new StoppedRootFeasibility(stop);
            TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(
                    fixture, model, MAX_STATES, TrinityPlanningControl.unbounded());

            helper.assertFalse(result.successful(), "A proved route with missing coal must remain non-executable");
            TrinityPlanningDiagnostic diagnostic = result.diagnostic();
            helper.assertValueEqual(diagnostic.code(), stop, "A route shortage must not claim global infeasibility");
            helper.assertValueEqual(diagnostic.metadata().get("state"), "FAILED", "The original stop must remain visible");
            helper.assertValueEqual(model.diagnosticCalls, 1, "Only one diagnostic solve request is needed");
            helper.assertValueEqual(model.diagnosticStateLimit, MAX_STATES - 1,
                    "Diagnosis must use the remaining budget after the root solve");
            helper.assertValueEqual(diagnostic.cycleEvidence().size(), 1, "The real evaluator must prove one cycle");

            TrinityCycleDiagnosticEvidence evidence = diagnostic.cycleEvidence().getFirst();
            TrinityPlanningDiagnostic.PartialPlan partial = diagnostic.partialPlan().orElseThrow();
            TrinityPlanningDiagnostic.InputRequirement shortage = Objects.requireNonNull(
                    partial.inputRequirements().get(fixture.fuel()),
                    "The proved schedule must report coal as an exact shortage");
            helper.assertValueEqual(evidence.demand(), fixture.demand(), "Evidence must match this exact request");
            helper.assertValueEqual(evidence.quality(), TrinityPlanQuality.VERIFIED_FEASIBLE,
                    "A diagnostic witness must not claim optimality");
            helper.assertFalse(evidence.localOrder().isEmpty(), "Evidence must contain an actual compressed order");
            helper.assertTrue(evidence.netChange().get(fixture.target()).compareTo(BigInteger.valueOf(100)) >= 0,
                    "The verified schedule must satisfy the full large request");
            helper.assertValueEqual(shortage.required(), evidence.initialInputs().get(fixture.fuel()),
                    "Shortage requirements must come from the proved schedule, not the relaxed model seed");
            helper.assertValueEqual(shortage.available(), BigInteger.TEN, "Real coal inventory must be preserved");
            helper.assertValueEqual(shortage.missing(), shortage.required().subtract(BigInteger.TEN),
                    "Missing coal must be the exact proved requirement minus real stock");
            helper.assertValueEqual(evidence.netChange().get(fixture.fuel()).negate(), shortage.required(),
                    "All required coal must be consumed by the actual firing vector");
            helper.assertValueEqual(partial.missingItems().keySet(), partial.inputRequirements().keySet(),
                    "Every published missing material must have an exact requirement, not unresolved demand");
            partial.inputRequirements().forEach((key, requirement) -> helper.assertValueEqual(
                    partial.missingItems().get(key), requirement.missing(), "Exact material counters must agree"));
            helper.assertTrue(evidence.scheduleStates() <= MAX_STATES, "Diagnosis must not reset the shared budget");
        }
        helper.succeed();
    }

    @TestHolder("trinity_joint_cycle_stopped_root_recovers_one_item_without_virtual_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stoppedRootRecoversOneItemWithoutVirtualInventory(GameTestHelper helper) {
        CycleFixture fixture = cycle(1);
        StoppedRootFeasibility model = new StoppedRootFeasibility(TrinityPlanningDiagnosticCode.MIP_TIMEOUT);
        TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(
                fixture, model, MAX_STATES, TrinityPlanningControl.unbounded());

        helper.assertTrue(result.successful(), "A fully verified zero-shortage witness may recover executable planning");
        TrinityJointCyclePlan plan = result.value();
        helper.assertValueEqual(plan.quality(), TrinityPlanQuality.VERIFIED_FEASIBLE,
                "Recovery must not manufacture an optimality proof");
        helper.assertValueEqual(model.diagnosticCalls, 1, "Recovery must use the real diagnostic model");
        helper.assertFalse(plan.schedule().batches().isEmpty(), "Recovery requires a real executable schedule");
        helper.assertTrue(plan.netChange().get(fixture.target()).compareTo(BigInteger.ONE) >= 0,
                "Recovery must produce the requested net-new item");
        plan.initialInputs().forEach((key, amount) -> helper.assertTrue(
                amount.compareTo(fixture.available().getOrDefault(key, BigInteger.ZERO)) <= 0,
                "An executable recovered plan must never use diagnostic-only virtual stock"));
        helper.succeed();
    }

    @TestHolder("trinity_joint_cycle_cancelled_root_does_not_start_shortage_diagnosis")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cancelledRootDoesNotStartShortageDiagnosis(GameTestHelper helper) {
        AtomicBoolean cancelled = new AtomicBoolean();
        StoppedRootFeasibility model = new StoppedRootFeasibility(
                TrinityPlanningDiagnosticCode.MIP_TIMEOUT, null, () -> cancelled.set(true));
        TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(
                cycle(100), model, MAX_STATES, TrinityPlanningControl.unbounded(cancelled::get));

        helper.assertFalse(result.successful(), "A cancelled request cannot publish a recovered plan");
        helper.assertValueEqual(result.diagnostic().code(), TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                "Cancellation takes precedence over an earlier solver stop");
        helper.assertValueEqual(model.diagnosticCalls, 0, "Cancellation must prevent a second solver request");
        helper.succeed();
    }

    @TestHolder("trinity_joint_cycle_expired_deadline_does_not_start_shortage_diagnosis")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void expiredDeadlineDoesNotStartShortageDiagnosis(GameTestHelper helper) {
        AtomicLong clock = new AtomicLong();
        TrinityPlanningControl control = TrinityPlanningControl.create(() -> false, clock::get, 1L);
        StoppedRootFeasibility model = new StoppedRootFeasibility(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, null, () -> clock.set(1L));
        TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(cycle(100), model, MAX_STATES, control);

        helper.assertFalse(result.successful(), "An expired global deadline cannot recover a plan");
        helper.assertValueEqual(result.diagnostic().code(), TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                "The real deadline must remain terminal");
        helper.assertValueEqual(model.diagnosticCalls, 0, "Diagnosis must not replace an expired control");
        helper.succeed();
    }

    @TestHolder("trinity_joint_cycle_exhausted_root_budget_does_not_start_shortage_diagnosis")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exhaustedRootBudgetDoesNotStartShortageDiagnosis(GameTestHelper helper) {
        StoppedRootFeasibility model = new StoppedRootFeasibility(TrinityPlanningDiagnosticCode.MIP_TIMEOUT);
        TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(
                cycle(100), model, 1, TrinityPlanningControl.unbounded());

        helper.assertFalse(result.successful(), "A consumed root budget cannot grant diagnostic work");
        helper.assertValueEqual(result.diagnostic().code(), TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "The exhausted shared state budget must remain terminal");
        helper.assertValueEqual(model.diagnosticCalls, 0, "Diagnosis must not create a fresh state budget");
        helper.assertTrue(result.diagnostic().cycleEvidence().isEmpty(), "An unexecuted diagnosis has no cycle proof");
        helper.succeed();
    }

    @TestHolder("trinity_joint_cycle_secondary_diagnostic_failure_retains_root_stop")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void secondaryDiagnosticFailureRetainsRootStop(GameTestHelper helper) {
        StoppedRootFeasibility model = new StoppedRootFeasibility(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, TrinityPlanningDiagnosticCode.MIP_TIMEOUT, () -> {});
        TrinityAlgorithmResult<TrinityJointCyclePlan> result = search(
                cycle(100), model, MAX_STATES, TrinityPlanningControl.unbounded());

        helper.assertFalse(result.successful(), "A stopped diagnostic solve cannot produce a plan");
        helper.assertValueEqual(result.diagnostic().code(), TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "A secondary diagnostic failure must not replace the original root stop");
        helper.assertValueEqual(result.diagnostic().metadata().get("state"), "FAILED",
                "The root solver state must remain available for diagnosis");
        helper.assertValueEqual(model.diagnosticCalls, 1, "A secondary failure must not cause unlimited retries");
        helper.assertTrue(result.diagnostic().cycleEvidence().isEmpty(), "A failed diagnostic solve must not fabricate evidence");
        helper.succeed();
    }

    private static TrinityAlgorithmResult<TrinityJointCyclePlan> search(
                                                                        CycleFixture fixture,
                                                                        StoppedRootFeasibility model,
                                                                        int maxStates,
                                                                        TrinityPlanningControl control) {
        TrinityJointCycleSearch search = new TrinityJointCycleSearch(
                model, TrinityJointCandidateEvaluator.create(), TrinityExternalPrefixCut.create());
        return search.search(fixture.component(), fixture.demand(), fixture.available(), Set.of(),
                maxStates, TrinityPlanningMode.FIRST_FEASIBLE, control);
    }

    private static CycleFixture cycle(long requested) {
        AEKey target = AEItemKey.of(Items.DIAMOND);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant variant = TrinityPatternVariant.create(
                new TrinityPatternIdentity("shortage_cycle", "diamond_and_coal_to_two_diamonds"), target, 0,
                List.of(0, 0), List.of(
                        new TrinityBoundPatternInput(0, 0, new GenericStack(target, 1), 1, null),
                        new TrinityBoundPatternInput(1, 0, new GenericStack(fuel, 1), 1, null)),
                List.of(new GenericStack(target, 2)));
        TrinityStronglyConnectedComponent component = new TrinityStronglyConnectedComponent(
                0, List.of(target), true, List.of(variant), List.of(), List.of());
        TrinityCycleDemand demand = new TrinityCycleDemand(
                Map.of(), Map.of(), Map.of(target, BigInteger.valueOf(requested)), Set.of(target));
        return new CycleFixture(target, fuel, component, demand, Map.of(target, BigInteger.ONE, fuel, BigInteger.TEN));
    }

    private record CycleFixture(
                                AEKey target,
                                AEKey fuel,
                                TrinityStronglyConnectedComponent component,
                                TrinityCycleDemand demand,
                                Map<AEKey, BigInteger> available) {}

    /** Controls only terminal solver boundaries; successful diagnosis and scheduling use production algorithms. */
    private static final class StoppedRootFeasibility implements TrinityCycleFeasibilityModel {

        private final TrinityCycleFeasibilityModel delegate = TrinityCycleFeasibilityModel.create();
        private final TrinityPlanningDiagnosticCode rootStop;
        private final @Nullable TrinityPlanningDiagnosticCode diagnosticStop;
        private final Runnable rootFinished;
        private int diagnosticCalls;
        private int diagnosticStateLimit;

        private StoppedRootFeasibility(TrinityPlanningDiagnosticCode rootStop) {
            this(rootStop, null, () -> {});
        }

        private StoppedRootFeasibility(TrinityPlanningDiagnosticCode rootStop,
                                       @Nullable TrinityPlanningDiagnosticCode diagnosticStop,
                                       Runnable rootFinished) {
            this.rootStop = rootStop;
            this.diagnosticStop = diagnosticStop;
            this.rootFinished = rootFinished;
        }

        @Override
        public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                             TrinityCycleFeasibilityRequest request,
                                                                             TrinityPlanningMode mode,
                                                                             TrinityPlanningControl control) {
            if (!request.shortageDiagnostic()) {
                this.rootFinished.run();
                return failure(this.rootStop, "FAILED");
            }
            this.diagnosticCalls++;
            this.diagnosticStateLimit = request.shortageStateLimit();
            if (this.diagnosticStop != null) {
                return failure(this.diagnosticStop, "DIAGNOSTIC_STOP");
            }
            return this.delegate.solve(request, mode, control);
        }

        private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> failure(
                                                                                       TrinityPlanningDiagnosticCode code,
                                                                                       String state) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    code, Component.literal("Controlled solver stop"), Map.of("state", state, "states", "0")));
        }
    }
}
