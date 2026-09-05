package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityCancelledDispatchReplanGameTest {

    private TrinityCancelledDispatchReplanGameTest() {}

    @TestHolder("cancelled_dispatch_replan_invalidates_leases_and_restores_completed_stage_planning")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidatesLeasesAndRestoresCompletedStagePlanning(GameTestHelper helper) {
        TrinityPlanExecution execution = TrinityPlanExecution.create(plan(2L, false), 1L);
        var first = execution.pollDispatchable(1L, Set.of(), ignored -> true, true).orElseThrow();
        var replayed = TrinityPlanExecution.restore(execution.save(helper.getLevel().registryAccess(), 1L),
                helper.getLevel().registryAccess(), 2L);
        var beforeRecovery = replayed.pendingOutputs();
        var recovered = replayed.recoverAcceptedWork(first, 2L).orElseThrow();
        helper.assertValueEqual(recovered, first, "A persisted unaccounted acceptance recovers the same firing lease");
        helper.assertValueEqual(replayed.pendingOutputs(), beforeRecovery, "Lease recovery must not count an acceptance or alter pending output");
        helper.assertTrue(replayed.recoverAcceptedWork(first, 2L).orElseThrow() == recovered,
                "Repeated recovery returns the existing lease rather than allocating another");
        replayed.recordAccepted(recovered, 1L, 2L);
        helper.assertValueEqual(replayed.pendingOutputs().get(first.primaryOutput()), BigInteger.ONE,
                "Only the explicit accounting call deducts the accepted operation");
        execution.recordAccepted(first, 1L, 2L);
        var outstanding = execution.pollDispatchable(2L, Set.of(), ignored -> true, true).orElseThrow();
        var oldPending = execution.pendingOutputs();
        execution.replanAfterCancelledDispatch(2L);
        helper.assertTrue(execution.recoverAcceptedWork(outstanding, 2L).isEmpty(), "Old-generation provider work cannot recover after replanning");
        helper.assertValueEqual(execution.pendingOutputs(), oldPending, "Cancellation must not rewind or invent old firing counts");
        helper.assertTrue(execution.pollDispatchable(2L, Set.of(), ignored -> true, true).isEmpty(),
                "Replanning suspends all old leased and ready work");
        expectStateFailure(helper, () -> execution.recordAccepted(outstanding, 1L, 1L));
        execution.replaceRemainingPlan(plan(4L, false), 3L);
        var replacement = execution.pollDispatchable(3L, Set.of(), ignored -> true, true).orElseThrow();
        helper.assertTrue(replacement.generation() > outstanding.generation(), "Replacement cannot reuse a stale work generation");
        helper.assertValueEqual(replacement.maximumLogicalFirings(), 4L, "Replacement production may exceed remaining target delivery");
        helper.assertValueEqual(execution.deliveryRemaining(), 2L, "More replacement production does not increase user delivery responsibility");
        execution.recordAccepted(replacement, 4L, 4L);
        helper.assertTrue(execution.productionComplete(), "Old stage can be fully dispatched before cancellation arrives");
        execution.replanAfterCancelledDispatch(4L);
        var restored = TrinityPlanExecution.restore(execution.save(helper.getLevel().registryAccess(), 4L),
                helper.getLevel().registryAccess(), 5L);
        helper.assertValueEqual(restored.status(), TrinityPlanExecution.Status.PLANNING,
                "PLANNING with all old stages completed is a valid durable recovery state");
        helper.assertTrue(restored.productionComplete(), "Restore preserves completed dispatch history rather than reopening it");
        helper.assertTrue(restored.pollDispatchable(5L, Set.of(), ignored -> true, true).isEmpty(), "Restored recovery cannot dispatch old work");
        helper.assertValueEqual(restored.deliveryRemaining(), 2L, "Restore retains original delivery responsibility");
        helper.succeed();
    }

    @TestHolder("cancelled_dispatch_no_production_finish_preserves_assets_and_pending_delivery")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void noProductionFinishPreservesAssetsAndPendingDelivery(GameTestHelper helper) {
        TrinityPlanExecution execution = TrinityPlanExecution.create(plan(2L, true), 10L);
        AEItemKey paper = AEItemKey.of(Items.PAPER);
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("completed earlier"));
        AEItemKey actual = AEItemKey.of(named);
        execution.borrowingLedger().reserve(paper, 1L);
        var borrowing = execution.borrowingLedger().entries();
        execution.recordActualFinalOutput(actual, 1L);
        expectStateFailure(helper, () -> execution.finishReplanningWithoutProduction(10L));
        execution.replanAfterCancelledDispatch(11L);
        execution.finishReplanningWithoutProduction(11L);
        helper.assertValueEqual(execution.status(), TrinityPlanExecution.Status.COMPLETED, "Only production is retired");
        helper.assertValueEqual(execution.deliveryRemaining(), 2L, "Unsent output is still owed to the requester");
        helper.assertValueEqual(execution.actualFinalOutputAmount(), 1L, "Actual previously produced output survives");
        helper.assertTrue(execution.completionOffer().isEmpty(), "The method must not fabricate or seal a delivery");
        helper.assertValueEqual(execution.borrowingLedger().entries(), borrowing, "Borrowing ownership is independent of discarded production");
        CompoundTag retired = execution.save(helper.getLevel().registryAccess(), 11L);
        var snapshot = TrinityExecutionNbtCodec.decode(retired, helper.getLevel().registryAccess());
        helper.assertTrue(snapshot.productionRetired() && snapshot.stages().isEmpty() && snapshot.repeatBlocks().isEmpty() && snapshot.seedReserve().isEmpty(),
                "Old cycle, stage and seed reservations must not survive a no-production recovery");
        CompoundTag running = retired.copy();
        running.putString("status", "READY");
        expectInvalidSnapshot(helper, running);
        CompoundTag unmarked = retired.copy();
        unmarked.putBoolean("production_retired", false);
        expectInvalidSnapshot(helper, unmarked);
        CompoundTag legacyEmpty = retired.copy();
        legacyEmpty.putInt("schema_version", 8);
        legacyEmpty.remove("production_retired");
        expectInvalidSnapshot(helper, legacyEmpty);
        execution.sealCompletion(1L);
        execution.recordDelivered(actual, 1L);
        var pendingDelivery = execution.completionOffer().orElseThrow();
        execution.replanAfterCancelledDispatch(12L);
        var restored = TrinityPlanExecution.restore(execution.save(helper.getLevel().registryAccess(), 12L),
                helper.getLevel().registryAccess(), 13L);
        restored.finishReplanningWithoutProduction(13L);
        helper.assertValueEqual(restored.completionOffer().orElseThrow(), pendingDelivery,
                "A previously sealed undelivered output is preserved exactly through recovery and reload");
        helper.assertValueEqual(restored.deliveryRemaining(), 1L, "Already delivered output must never become owed again");
        restored.recordDelivered(target, 1L);
        helper.assertValueEqual(restored.deliveryRemaining(), 0L, "Only the actual delivery receipt finishes the responsibility");
        helper.succeed();
    }

    private static TrinityCraftingPlan plan(long count, boolean cycle) {
        AEItemKey input = AEItemKey.of(Items.PAPER);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        BigInteger total = BigInteger.valueOf(count);
        BigInteger perStage = cycle ? BigInteger.ONE : total;
        var identity = new TrinityPatternIdentity("cancelled-dispatch", "recipe");
        var firing = new TrinityPlanPatternFiring(identity, output, 0, perStage,
                Map.of(input, BigInteger.ONE), Map.of(output, BigInteger.ONE),
                cycle ? Map.of(input, BigInteger.ONE) : Map.of(), List.of());
        Map<AEKey, BigInteger> stageDelta = cycle ? Map.of(output, BigInteger.ONE) :
                Map.of(input, total.negate(), output, total);
        Map<AEKey, BigInteger> net = cycle ? Map.of(output, total) : stageDelta;
        Map<AEKey, BigInteger> initial = Map.of(input, cycle ? BigInteger.ONE : total);
        var stage = new TrinityPlanStage(0, cycle, Set.of(), List.of(firing), initial, stageDelta);
        var builder = TrinityCraftingPlan.builder().finalOutput(new GenericStack(output, count))
                .bytes(BigInteger.ZERO).catalogRevision(1L).quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(initial).patternFirings(Map.of(identity, total)).stages(List.of(stage))
                .stageOrder(List.of(0)).targetNetChange(net);
        if (cycle) {
            builder.cycleRepeatBlocks(List.of(new TrinityCycleRepeatBlock(0, List.of(0), total, initial, net)))
                    .minimumSeed(initial);
        }
        return builder.build();
    }

    private static void expectStateFailure(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail("Invalid recovery transition or obsolete work must be rejected");
    }

    private static void expectInvalidSnapshot(GameTestHelper helper, CompoundTag tag) {
        try {
            TrinityPlanExecution.restore(tag, helper.getLevel().registryAccess(), 11L);
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail("Empty production requires an explicit current-schema retirement marker and non-running status");
    }
}
