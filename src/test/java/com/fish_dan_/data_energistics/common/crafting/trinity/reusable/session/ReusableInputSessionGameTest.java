package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Append;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ReturnBatch;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolDelivery;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputSessionGameTest {

    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "session_tool");
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.IRON_NUGGET);
    private static final AEItemKey SCRAP = AEItemKey.of(Items.STICK);

    private ReusableInputSessionGameTest() {}

    @TestHolder("reusable_session_unchanged_tool_resides_across_one_thousand_operations")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unchangedToolResidesAcrossOneThousandOperations(GameTestHelper helper) {
        ReusableInputSession session = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        for (int sequence = 0; sequence < 10; sequence++) {
            Append request = append(sequence, 100, sequence == 0 ? List.of(delivery(0, 1)) : List.of());
            session.acceptAppend(new Append(sequence, request.operations(), request.consumedPerOperation(),
                    request.deliveredMaterials(), request.deliveredTools(), Int2ObjectMaps.singleton(0, tool(0))));
        }
        helper.assertValueEqual(session.reservedToolUses(0, tool(0)), 1000L, "Exact unchanged tool covers all sequentially reserved appends");
        for (int sequence = 0; sequence < 10; sequence++) {
            execute(session, 100);
            helper.assertTrue(session.returnOutbox().isEmpty(), "Completing an append cannot return its resident tool");
            helper.assertValueEqual(amount(session.heldTools().get(0), tool(0)), 1L, "There is still one actual tool");
            session = reload(session, helper);
        }
        helper.assertValueEqual(session.accepted(), 1000L, "All appends are accepted exactly once");
        helper.assertValueEqual(session.completed(), 1000L, "Every real operation is accounted");
        helper.assertValueEqual(amount(session.drainOutputs(), OUTPUT), 1000L, "Ordinary outputs leave independently");
        session.close();
        helper.assertValueEqual(session.returnOutbox().getFirst().assets(), List.of(stack(tool(0), 1)),
                "Closing one thousand operations returns one physical unchanged tool");
        helper.succeed();
    }

    @TestHolder("reusable_session_three_hundred_tool_uses_leave_fifty_after_two_hundred_fifty")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void threeHundredToolUsesLeaveFiftyAfterTwoHundredFifty(GameTestHelper helper) {
        ReusableInputSession session = session(finite(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 100, List.of(delivery(0, 3))));
        execute(session, 100);
        session.acceptAppend(append(2, 100, List.of()));
        execute(session, 100);
        session = reload(session, helper);
        session.acceptAppend(append(3, 50, List.of()));
        execute(session, 50);
        helper.assertValueEqual(session.heldTools().get(0), List.of(stack(tool(50), 1)),
                "Single-tool slot exhausts each physical tool before using its spare");
        helper.assertValueEqual(session.exhaustedTools(), 2L, "Only two tools exhausted");
        helper.assertValueEqual(amount(session.pendingOutputs(), SCRAP), 4L, "Exhaustion scrap is produced twice");
        session.close();
        helper.assertValueEqual(session.returnOutbox().getFirst().assets(), List.of(stack(tool(50), 1)),
                "Refund retains actual damage rather than recreating original tools");
        helper.succeed();
    }

    @TestHolder("reusable_session_reservations_reject_partial_multislot_admission")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reservationsRejectPartialMultislotAdmission(GameTestHelper helper) {
        ReusableInputSession session = new ReusableInputSession(identity(), List.of(
                new SlotContract(0, 1, Ownership.CPU_SUPPLIED, finite()),
                new SlotContract(2, 1, Ownership.CPU_SUPPLIED, finite())));
        var before = session.snapshot();
        expectIllegal(helper, () -> session.acceptAppend(append(1, 50, List.of(delivery(0, 1)))),
                "A missing second tool slot must reject the complete admission");
        helper.assertValueEqual(session.snapshot(), before, "Rejected append takes neither tools nor materials");
        session.acceptAppend(append(1, 50, List.of(delivery(0, 1), new ToolDelivery(2, stack(tool(0), 1)))));
        before = session.snapshot();
        expectIllegal(helper, () -> session.acceptAppend(append(2, 51, List.of())),
                "Unexecuted accepted operations already reserve lifetime");
        helper.assertValueEqual(session.snapshot(), before, "Over-reservation cannot partially mutate the session");
        session.acceptAppend(append(2, 50, List.of()));
        execute(session, 100);
        helper.assertValueEqual(session.exhaustedTools(), 2L, "Both slots exhaust at exactly their reserved boundary");
        helper.assertValueEqual(session.completed(), 100L, "The accepted capacity is actually executable");
        helper.succeed();
    }

    @TestHolder("reusable_session_multiple_tools_per_slot_count_each_physical_unit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void multipleToolsPerSlotCountEachPhysicalUnit(GameTestHelper helper) {
        ReusableInputSession missing = session(unchanged(), 2, Ownership.CPU_SUPPLIED);
        expectIllegal(helper, () -> missing.acceptAppend(append(1, 1000, List.of(delivery(0, 1)))),
                "One immortal tool cannot occupy two held units in the same operation");
        ReusableInputSession session = session(finite(), 2, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 125, List.of(delivery(0, 3))));
        execute(session, 125);
        long remaining = session.heldTools().get(0).stream()
                .mapToLong(stack -> finite().guaranteedUses((AEItemKey) stack.what()) * stack.amount()).sum();
        helper.assertValueEqual(remaining, 50L, "Two real units per operation consume 250 of 300 uses");
        helper.assertValueEqual(session.completed(), 125L, "Spare tools cover every simultaneous two-tool operation");
        ReusableInputSession uneven = session(finite(), 2, Ownership.CPU_SUPPLIED);
        uneven.acceptAppend(append(1, 2, List.of(new ToolDelivery(0, stack(tool(97), 1)),
                new ToolDelivery(0, stack(tool(99), 2)))));
        execute(uneven, 2);
        helper.assertValueEqual(uneven.completed(), 2L, "Different remaining lifetimes cannot strand the second held unit");
        helper.succeed();
    }

    @TestHolder("reusable_session_same_slot_same_key_held_and_consumed_stay_separate")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sameSlotSameKeyHeldAndConsumedStaySeparate(GameTestHelper helper) {
        ReusableInputSession session = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(new Append(1, 10, List.of(new SlotInput(0, stack(tool(0), 1))),
                List.of(stack(tool(0), 10)), List.of(delivery(0, 1)), Int2ObjectMaps.emptyMap()));
        Operation active = session.beginOperation().orElseThrow();
        helper.assertValueEqual(active.consumed().getFirst().slot(), 0, "Consumed portion keeps the native slot");
        helper.assertValueEqual(active.tools().getFirst().slot(), 0, "Held portion keeps the same native slot");
        session.completeOperation(active.id(), session.predictedOutcomes(active), List.of(stack(OUTPUT, 1)));
        session.close();
        helper.assertValueEqual(amount(session.returnOutbox().getFirst().assets(), tool(0)), 10L,
                "Refund contains nine unused material units plus exactly one held tool");
        helper.assertValueEqual(session.cancelled(), 9L, "Only unexecuted operations are cancelled");
        helper.succeed();
    }

    @TestHolder("reusable_session_machine_owned_tools_never_enter_cpu_refunds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void machineOwnedToolsNeverEnterCpuRefunds(GameTestHelper helper) {
        ReusableInputSession session = session(finite(), 1, Ownership.MACHINE_OWNED);
        session.acceptAppend(append(1, 10, List.of(delivery(0, 1))));
        execute(session, 2);
        session.close();
        session = reload(session, helper);
        helper.assertValueEqual(session.returnOutbox().getFirst().assets(), List.of(stack(MATERIAL, 8)),
                "The CPU receives only its unused materials");
        helper.assertValueEqual(session.drainMachineOwnedReleased(), List.of(new ToolDelivery(0, stack(tool(2), 1))),
                "The exact surviving machine tool is released separately");
        helper.assertTrue(session.drainMachineOwnedReleased().isEmpty(), "Machine release transfers ownership once");
        helper.assertValueEqual(amount(session.pendingOutputs(), OUTPUT), 2L, "Produced outputs remain independently available");
        helper.succeed();
    }

    @TestHolder("reusable_session_append_and_return_acknowledgments_are_idempotent_after_reload")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void appendAndReturnAcknowledgmentsAreIdempotentAfterReload(GameTestHelper helper) {
        ReusableInputSession session = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        Append request = append(7, 3, List.of(delivery(0, 1)));
        session.validateAppend(request);
        helper.assertValueEqual(session.accepted(), 0L, "Read-only admission validation takes no ownership");
        helper.assertTrue(session.acceptAppend(request), "First delivery is accepted");
        helper.assertTrue(!session.acceptAppend(request), "Identical sequence replay is a no-op");
        execute(session, 1);
        ReusableInputSession restored = reload(session, helper);
        helper.assertTrue(!restored.acceptAppend(request), "Accepted sequence survives reload");
        helper.assertValueEqual(restored.appendSnapshot(7).orElseThrow().completed(), 1L, "Per-append receipt has actual progress");
        expectIllegal(helper, () -> restored.acceptAppend(append(7, 4, List.of(delivery(0, 1)))),
                "Reusing a sequence with a changed payload must fail");
        restored.close();
        ReturnBatch refund = restored.returnOutbox().getFirst();
        expectIllegal(helper, () -> restored.acknowledgeReturn(refund.sequence(), List.of(stack(MATERIAL, 1))),
                "A partial acknowledgment cannot discard the rest of the refund");
        helper.assertTrue(restored.acknowledgeReturn(refund.sequence(), refund.assets()), "The exact batch is acknowledged once");
        ReusableInputSession closed = reload(restored, helper);
        helper.assertValueEqual(closed.status(), State.CLOSED, "Fully acknowledged session restores closed");
        helper.assertTrue(!closed.acknowledgeReturn(refund.sequence(), refund.assets()), "Duplicate acknowledgment is harmless");
        helper.assertTrue(!closed.acceptAppend(request), "Closed session still recognizes its old append receipt");
        expectIllegal(helper, () -> closed.acknowledgeReturn(99, refund.assets()), "Unknown acknowledgment is rejected");
        helper.succeed();
    }

    @TestHolder("reusable_session_interrupted_operation_quarantines_old_assets_until_actual_result")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void interruptedOperationQuarantinesOldAssetsUntilActualResult(GameTestHelper helper) {
        ReusableInputSession session = session(finite(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 3, List.of(delivery(0, 1))));
        Operation active = session.beginOperation().orElseThrow();
        session.close();
        helper.assertValueEqual(session.status(), State.CLOSING, "Close waits for active native execution");
        ReusableInputSession restored = reload(session, helper);
        helper.assertValueEqual(restored.status(), State.FAULTED, "Restart cannot assume whether native execution happened");
        restored.close();
        helper.assertTrue(restored.returnOutbox().isEmpty(), "Old escrow tools cannot be refunded before reconciliation");
        helper.assertTrue(restored.beginOperation().isEmpty(), "A quarantined operation cannot execute twice");
        restored.completeOperation(active.id(), restored.predictedOutcomes(active), List.of(stack(OUTPUT, 1)));
        helper.assertValueEqual(restored.status(), State.FAULTED, "Reconciliation does not silently restart execution");
        restored.close();
        ReusableInputSession settled = reload(restored, helper);
        helper.assertValueEqual(settled.status(), State.RETURN_PENDING, "Settled actual assets await directed refund");
        List<GenericStack> refund = settled.returnOutbox().getFirst().assets();
        helper.assertValueEqual(amount(refund, tool(1)), 1L, "Only the actual successor is returned");
        helper.assertValueEqual(amount(refund, tool(0)), 0L, "The pre-execution tool is not reconstructed");
        helper.assertValueEqual(amount(refund, MATERIAL), 2L, "Only materials for unexecuted operations are returned");
        helper.succeed();
    }

    @TestHolder("reusable_session_unexpected_native_remainder_preserves_actual_assets_and_faults")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unexpectedNativeRemainderPreservesActualAssetsAndFaults(GameTestHelper helper) {
        ReusableInputSession session = session(finite(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 2, List.of(delivery(0, 1))));
        Operation active = session.beginOperation().orElseThrow();
        boolean matched = session.completeOperation(active.id(), List.of(new ToolOutcome(0,
                List.of(stack(tool(9), 1)), List.of(stack(SCRAP, 3)))), List.of(stack(OUTPUT, 1)));
        helper.assertTrue(!matched, "Unexpected loss and byproducts differ from frozen prediction");
        helper.assertValueEqual(session.status(), State.FAULTED, "Prediction mismatch closes admission");
        helper.assertValueEqual(session.heldTools().get(0), List.of(stack(tool(9), 1)), "Unexpected actual damage is retained");
        ReusableInputSession restored = reload(session, helper);
        restored.close();
        helper.assertValueEqual(amount(restored.returnOutbox().getFirst().assets(), tool(9)), 1L,
                "Faulted refund cannot create the expected or initial tool");
        helper.assertValueEqual(amount(restored.pendingOutputs(), SCRAP), 3L, "Actual byproducts survive fault and reload");
        helper.succeed();
    }

    @TestHolder("reusable_session_invalid_native_reports_leave_execution_escrow_intact")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidNativeReportsLeaveExecutionEscrowIntact(GameTestHelper helper) {
        ReusableInputSession session = session(finite(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 1, List.of(delivery(0, 1))));
        Operation active = session.beginOperation().orElseThrow();
        var before = session.snapshot();
        List<ToolOutcome> predicted = session.predictedOutcomes(active);
        ToolOutcome outcome = predicted.getFirst();
        expectIllegal(helper, () -> session.completeOperation(active.id(), List.of(outcome, outcome), List.of()),
                "Duplicate tool slots must be rejected at the report boundary");
        expectIllegal(helper, () -> session.completeOperation(active.id(),
                List.of(new ToolOutcome(9, outcome.successors(), outcome.byproducts())), List.of()),
                "A foreign slot cannot replace the required tool outcome");
        expectIllegal(helper, () -> session.completeOperation(active.id(), predicted, List.of(stack(OUTPUT, -1))),
                "Negative ordinary output cannot enter trusted asset arithmetic");
        helper.assertValueEqual(session.snapshot(), before, "Invalid reports cannot mutate execution escrow");
        helper.assertTrue(session.completeOperation(active.id(), predicted, List.of(stack(OUTPUT, 1))),
                "A complete corrected report can settle the same operation once");
        helper.assertValueEqual(session.completed(), 1L, "Only the valid report advances completed work");
        helper.succeed();
    }

    @TestHolder("reusable_session_native_failure_and_abort_keep_distinct_asset_ownership")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nativeFailureAndAbortKeepDistinctAssetOwnership(GameTestHelper helper) {
        ReusableInputSession aborted = session(finite(), 1, Ownership.CPU_SUPPLIED);
        aborted.acceptAppend(append(1, 2, List.of(delivery(0, 1))));
        Operation unexecuted = aborted.beginOperation().orElseThrow();
        aborted.close();
        aborted.abortOperation(unexecuted.id());
        helper.assertValueEqual(amount(aborted.returnOutbox().getFirst().assets(), MATERIAL), 2L,
                "Proven unexecuted abort restores its real material escrow");
        helper.assertValueEqual(aborted.completed(), 0L, "Abort is not a completed operation");
        ReusableInputSession failed = session(finite(), 1, Ownership.CPU_SUPPLIED);
        failed.acceptAppend(append(1, 2, List.of(delivery(0, 1))));
        Operation executed = failed.beginOperation().orElseThrow();
        failed.faultOperation(executed.id(), List.of(new ToolOutcome(0, List.of(), List.of(stack(SCRAP, 1)))),
                List.of(), "Native inventory callback failed after tool exhaustion");
        failed.close();
        helper.assertValueEqual(amount(failed.returnOutbox().getFirst().assets(), tool(0)), 0L,
                "A native exception after exhaustion cannot restore its old tool");
        helper.assertValueEqual(failed.exhaustedTools(), 1L, "Actual exhaustion remains accounted after failure");
        helper.succeed();
    }

    @TestHolder("reusable_session_idle_and_competition_release_at_twenty_tick_boundary")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void idleAndCompetitionReleaseAtTwentyTickBoundary(GameTestHelper helper) {
        ReusableInputSession idle = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        idle.acceptAppend(append(1, 1, List.of(delivery(0, 1))));
        execute(idle, 1);
        helper.assertTrue(!idle.tick(100), "First idle tick starts grace period");
        idle = reload(idle, helper);
        helper.assertTrue(!idle.tick(119), "Nineteen idle ticks keep the resident session");
        helper.assertTrue(idle.tick(120), "Twenty idle ticks release the resident tool");
        ReusableInputSession contested = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        contested.acceptAppend(append(1, 100, List.of(delivery(0, 1))));
        helper.assertTrue(contested.requestYield(100), "Explicit competition signal latches its deadline while busy");
        helper.assertTrue(!contested.tick(100), "Competition starts its own grace period while busy");
        Operation active = contested.beginOperation().orElseThrow();
        helper.assertTrue(!contested.requestYield(119), "A repeat signal cannot restart the active operation's grace period");
        helper.assertTrue(!contested.tick(119), "Competition before boundary keeps native operation active");
        helper.assertTrue(contested.tick(120), "Twenty competing ticks request safe-point closure");
        helper.assertValueEqual(contested.status(), State.CLOSING, "Native operation is allowed to finish before release");
        contested.completeOperation(active.id(), contested.predictedOutcomes(active), List.of());
        helper.assertValueEqual(contested.status(), State.RETURN_PENDING, "Completed native operation settles pending closure");
        helper.assertValueEqual(contested.cancelled(), 99L, "Competition cancels precisely the unexecuted work");
        helper.succeed();
    }

    @TestHolder("reusable_session_persistence_rejects_missing_fields_and_inconsistent_materials")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void persistenceRejectsMissingFieldsAndInconsistentMaterials(GameTestHelper helper) {
        ReusableInputSession session = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 2, List.of(delivery(0, 1))));
        CompoundTag missing = ReusableInputSessionNbtCodec.encode(session, helper.getLevel().registryAccess());
        missing.remove("next_operation");
        expectIllegal(helper, () -> ReusableInputSessionNbtCodec.decode(missing, helper.getLevel().registryAccess()),
                "Missing sequence counter cannot silently reset to zero");
        CompoundTag inconsistent = ReusableInputSessionNbtCodec.encode(session, helper.getLevel().registryAccess());
        inconsistent.getList("appends", Tag.TAG_COMPOUND).getCompound(0).putLong("completed", 1L);
        expectIllegal(helper, () -> ReusableInputSessionNbtCodec.decode(inconsistent, helper.getLevel().registryAccess()),
                "Persisted material cannot exceed the actual unexecuted operation escrow");
        CompoundTag future = ReusableInputSessionNbtCodec.encode(session, helper.getLevel().registryAccess());
        future.putInt("schema", 99);
        expectIllegal(helper, () -> ReusableInputSessionNbtCodec.decode(future, helper.getLevel().registryAccess()),
                "An unknown schema cannot reinterpret ownership fields");
        helper.succeed();
    }

    @TestHolder("reusable_session_cyclic_state_rule_advances_components_across_append_and_restart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cyclicStateRuleAdvancesComponentsAcrossAppendAndRestart(GameTestHelper helper) {
        ReusableInputRule cycle = ReusableInputRule.transitions(RULE_ID, 2, tool(0), List.of(
                new Transition(tool(0), tool(1), List.of(stack(SCRAP, 1))),
                new Transition(tool(1), tool(0), List.of())));
        ReusableInputSession session = session(cycle, 1, Ownership.CPU_SUPPLIED);
        session.acceptAppend(append(1, 3, List.of(delivery(0, 1))));
        execute(session, 3);
        session = reload(session, helper);
        session.acceptAppend(append(2, 2, List.of()));
        execute(session, 2);
        helper.assertValueEqual(session.heldTools().get(0), List.of(stack(tool(1), 1)),
                "Unbounded lifetime does not imply an unchanged tool state");
        helper.assertValueEqual(amount(session.pendingOutputs(), SCRAP), 3L,
                "State transition outputs are emitted on the actual three applicable operations");
        session.close();
        helper.assertValueEqual(session.returnOutbox().getFirst().assets(), List.of(stack(tool(1), 1)),
                "Cyclic rule returns the actual final state rather than its original template");
        helper.succeed();
    }

    @TestHolder("reusable_session_many_append_counters_and_pending_cursor_survive_restart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void manyAppendCountersAndPendingCursorSurviveRestart(GameTestHelper helper) {
        ReusableInputSession session = session(unchanged(), 1, Ownership.CPU_SUPPLIED);
        for (int sequence = 0; sequence < 100; sequence++) {
            session.acceptAppend(append(sequence, 3, sequence == 0 ? List.of(delivery(0, 1)) : List.of()));
        }
        execute(session, 137);
        session = reload(session, helper);
        helper.assertValueEqual(session.accepted(), 300L, "Accepted cache is rebuilt from all append receipts");
        helper.assertValueEqual(session.completed(), 137L, "Completed cache includes partial current append");
        helper.assertValueEqual(session.cancelled(), 0L, "Open append receipts cannot invent cancellation");
        helper.assertValueEqual(session.pending(), 163L, "Pending cache includes the current partial append");
        helper.assertValueEqual(session.appendSnapshot(45).orElseThrow().completed(), 2L,
                "Reload selects the partial append after forty-five completed predecessors");
        Operation current = session.beginOperation().orElseThrow();
        helper.assertValueEqual(current.appendSequence(), 45L, "Execution cursor resumes at the first unfinished append");
        session.completeOperation(current.id(), session.predictedOutcomes(current), List.of());
        execute(session, 22);
        helper.assertValueEqual(session.completed(), 160L, "Continuation advances cached progress across later appends");
        session.close();
        session = reload(session, helper);
        helper.assertValueEqual(session.accepted(), 300L, "Settled history preserves total accepted count");
        helper.assertValueEqual(session.completed(), 160L, "Settled history preserves actual completed count");
        helper.assertValueEqual(session.cancelled(), 140L, "Closing cancels every remaining append exactly once");
        helper.assertValueEqual(session.pending(), 0L, "Settled queue has no pending operation");
        helper.assertValueEqual(amount(session.returnOutbox().getFirst().assets(), MATERIAL), 140L,
                "Restored cancellation count agrees with exact material escrow refund");
        helper.succeed();
    }

    private static ReusableInputSession session(ReusableInputRule rule, long held, Ownership ownership) {
        return new ReusableInputSession(identity(), List.of(new SlotContract(0, held, ownership, rule)));
    }

    private static Identity identity() {
        return new Identity(UUID.randomUUID(), UUID.randomUUID(), "cpu:stable-owner", "executor:stable-target",
                AEItemKey.of(Items.CRAFTING_TABLE), Optional.of("data_energistics:test-mode"));
    }

    private static ReusableInputRule unchanged() {
        return ReusableInputRule.unchanged(RULE_ID, 1, tool(0));
    }

    private static ReusableInputRule finite() {
        return ReusableInputRule.fixedDamage(RULE_ID, 1, tool(0), 1, 100, List.of(stack(SCRAP, 2)));
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static ToolDelivery delivery(int slot, long amount) {
        return new ToolDelivery(slot, stack(tool(0), amount));
    }

    private static Append append(long sequence, long operations, List<ToolDelivery> tools) {
        return new Append(sequence, operations, List.of(new SlotInput(1, stack(MATERIAL, 1))),
                List.of(stack(MATERIAL, operations)), tools, Int2ObjectMaps.emptyMap());
    }

    private static void execute(ReusableInputSession session, long operations) {
        for (long index = 0; index < operations; index++) {
            Operation operation = session.beginOperation().orElseThrow();
            if (!session.completeOperation(operation.id(), session.predictedOutcomes(operation), List.of(stack(OUTPUT, 1)))) {
                throw new IllegalStateException("Predicted test execution failed its own frozen contract");
            }
        }
    }

    private static long amount(List<GenericStack> assets, AEKey key) {
        return assets.stream().filter(stack -> stack.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    private static ReusableInputSession reload(ReusableInputSession session, GameTestHelper helper) {
        CompoundTag encoded = ReusableInputSessionNbtCodec.encode(session, helper.getLevel().registryAccess());
        ReusableInputSession restored = ReusableInputSessionNbtCodec.decode(encoded, helper.getLevel().registryAccess());
        if (session.activeOperation() == null) {
            helper.assertValueEqual(restored.snapshot(), session.snapshot(), "Complete safe-point session state survives restart");
        }
        return restored;
    }

    private static void expectIllegal(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail(message);
    }
}
