package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.Settlement;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PersistentReusableCraftingEndpointGameTest {

    private static final String TARGET = "core:00000000-0000-0000-0000-000000000001/slot:2";
    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "endpoint_tool");
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey PRODUCT = AEItemKey.of(Items.IRON_NUGGET);
    private static final AEItemKey SCRAP = AEItemKey.of(Items.STICK);
    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private PersistentReusableCraftingEndpointGameTest() {}

    @TestHolder("reusable_endpoint_transfers_total_inputs_and_executes_actual_tool_states_across_appends")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void transfersTotalInputsAndExecutesActualToolStatesAcrossAppends(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest first = request(helper, sessionId, 0, 3, List.of(new SlotStack(0, stack(tool(0), 2))));
        ReusableCraftingAdmission admission = prepare(endpoint, first, 10, host);
        helper.assertValueEqual(amount(admission.physicalInputs(), 0, tool(0)), 2L, "Two real tools are transferred once");
        helper.assertValueEqual(amount(admission.physicalInputs(), 1, MATERIAL), 3L, "Ordinary materials are total three-operation quantities");
        helper.assertTrue(endpoint.query(sessionId).isEmpty(), "Read-only preparation does not create an owned session");
        KeyCounter[] delivery = delivery(admission);
        helper.assertTrue(admission.commit(delivery), "The complete exact delivery commits");
        helper.assertTrue(admission.hasTransferredInputOwnership(), "Successful admission crosses ownership boundary");
        helper.assertValueEqual(delivery[0].get(tool(0)), 0L, "Transferred tool counter is consumed");
        helper.assertValueEqual(delivery[1].get(MATERIAL), 0L, "Transferred total material counter is consumed");
        helper.assertValueEqual(endpoint.tick(10, 10, false, host), 0, "Native work starts on a later tick");
        helper.assertValueEqual(endpoint.tick(11, 2, false, host), 2, "Native operations obey the shared host budget");
        endpoint = reload(endpoint, helper);
        helper.assertValueEqual(endpoint.tick(12, 1, false, host), 1, "Restored endpoint executes the third actual tool use");
        ReusableCraftingAdmission second = prepare(endpoint, request(helper, sessionId, 1, 2, List.of()), 12, host);
        helper.assertValueEqual(amount(second.physicalInputs(), 0, tool(0)), 0L, "Resident spare tool is not transferred again");
        helper.assertTrue(second.commit(delivery(second)), "Second append uses already resident assets");
        helper.assertValueEqual(endpoint.tick(13, 9, false, host), 2, "Only two further actual operations were accepted");
        helper.assertValueEqual(host.amount(PRODUCT), 5L, "Actual native outputs follow ordinary persistent pending queue");
        helper.assertValueEqual(host.amount(SCRAP), 1L, "One actually exhausted tool yields one scrap");
        endpoint.close(sessionId, host);
        List<Settlement> received = new ObjectArrayList<>();
        helper.assertTrue(endpoint.settle(sessionId, settlement -> received.add(settlement), host), "Directed final settlement is accepted");
        helper.assertValueEqual(received.getFirst().returnedAssets(), List.of(stack(tool(2), 1)),
                "Five uses of two three-use tools return the actual final damaged tool");
        helper.assertValueEqual(received.getFirst().exhaustedTools(), 1L, "Settlement accounts for the physically exhausted tool");
        helper.assertValueEqual(received.getFirst().receipts().size(), 2, "Both append receipts accompany settlement");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_rejects_prototypes_stale_preparations_and_changed_binding_without_consumption")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsPrototypesStalePreparationsAndChangedBindingWithoutConsumption(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest request = request(helper, sessionId, 0, 3, List.of(new SlotStack(0, stack(tool(0), 1))));
        ReusableCraftingAdmission bad = prepare(endpoint, request, 0, host);
        KeyCounter[] prototype = delivery(bad);
        prototype[1].remove(MATERIAL, 2);
        helper.assertTrue(!bad.commit(prototype), "A one-operation prototype cannot satisfy total delivery");
        helper.assertTrue(!bad.hasTransferredInputOwnership(), "Rejected prototype retains CPU ownership");
        helper.assertValueEqual(prototype[0].get(tool(0)), 1L, "Rejected delivery preserves its real tool");
        helper.assertValueEqual(prototype[1].get(MATERIAL), 1L, "Rejected delivery preserves its material");
        ReusableCraftingAdmission first = prepare(endpoint, request, 0, host);
        ReusableCraftingAdmission stale = prepare(endpoint, request(helper, UUID.randomUUID(), 0, 1,
                List.of(new SlotStack(0, stack(tool(0), 1)))), 0, host);
        helper.assertTrue(first.commit(delivery(first)), "First prepared owner commits");
        KeyCounter[] staleDelivery = delivery(stale);
        helper.assertTrue(!stale.commit(staleDelivery), "Competing preparation cannot overwrite the live owner");
        helper.assertValueEqual(staleDelivery[0].get(tool(0)), 1L, "Stale admission takes no tool ownership");
        List<Input> changed = List.of(request.inputs().getFirst(),
                new Input(1, List.of(stack(AEItemKey.of(Items.GOLD_INGOT), 1)), Optional.empty()));
        ReusableCraftingRequest rebound = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 1, request.target(),
                request.pattern(), changed, List.of(), 1, request.recipeId(), request.actionSource(), request.level());
        helper.assertTrue(endpoint.prepare(rebound, 1, host) == null, "Append cannot silently change the fixed material binding");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_empty_asset_settlement_and_append_replays_survive_restart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void emptyAssetSettlementAndAppendReplaysSurviveRestart(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest request = request(helper, sessionId, 0, 3, List.of(new SlotStack(0, stack(tool(0), 1))));
        ReusableCraftingAdmission first = prepare(endpoint, request, 0, host);
        first.commit(delivery(first));
        endpoint.tick(1, 3, false, host);
        endpoint.close(sessionId, host);
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().state(), State.RETURN_PENDING,
                "No physical refund does not eliminate the CPU settlement obligation");
        List<Settlement> attempted = new ObjectArrayList<>();
        helper.assertTrue(!endpoint.settle(sessionId, settlement -> {
            attempted.add(settlement);
            return false;
        }, host),
                "Unreachable receiver leaves empty-asset settlement pending");
        endpoint = reload(endpoint, helper);
        ReusableCraftingAdmission replay = prepare(endpoint, request, 4, host);
        helper.assertTrue(replay.replay() && replay.physicalInputs().isEmpty(), "Persisted append replay requests no second transfer");
        helper.assertTrue(replay.commit(delivery(replay)), "Empty replay counters acknowledge old ownership");
        helper.assertValueEqual(endpoint.receipt(sessionId, 0).orElseThrow().completed(), 3L, "Replay keeps actual completed receipt");
        helper.assertTrue(endpoint.settle(sessionId, settlement -> {
            helper.assertValueEqual(settlement, attempted.getFirst(), "Retry uses the exact durable settlement identity and receipt");
            return true;
        }, host), "Empty physical settlement is acknowledged");
        PersistentReusableCraftingEndpoint settled = reload(endpoint, helper);
        helper.assertValueEqual(settled.query(sessionId).orElseThrow().state(), State.CLOSED, "Acknowledged empty settlement restores closed");
        helper.assertTrue(settled.settle(sessionId, settlement -> {
            helper.fail("Already acknowledged settlement must not transfer again");
            return false;
        }, host),
                "Duplicate settle is an acknowledged no-op");
        helper.assertTrue(attempted.getFirst().returnedAssets().isEmpty(), "Exhaustion does not recreate a tool refund");
        helper.assertValueEqual(attempted.getFirst().exhaustedTools(), 1L, "Exhausted unit is explicitly included in accounting");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_unknown_native_exception_quarantines_actual_execution_escrow")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unknownNativeExceptionQuarantinesActualExecutionEscrow(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingAdmission first = prepare(endpoint, request(helper, sessionId, 0, 2,
                List.of(new SlotStack(0, stack(tool(0), 1)))), 0, host);
        first.commit(delivery(first));
        host.failNative = true;
        helper.assertValueEqual(endpoint.tick(1, 9, false, host), 0, "Unknown native result cannot be counted as completed");
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().state(), State.FAULTED, "Unknown effects are explicitly faulted");
        endpoint.close(sessionId, host);
        helper.assertTrue(!endpoint.settle(sessionId, settlement -> {
            helper.fail("Unresolved active assets cannot be refunded");
            return true;
        }, host),
                "Close cannot refund pre-execution tools after an unknown exception");
        endpoint = reload(endpoint, helper);
        host.failNative = false;
        helper.assertValueEqual(endpoint.tick(2, 9, false, host), 0, "Restart cannot repeat quarantined native execution");
        endpoint.reconcile(sessionId, new NativeResult(true, List.of(new ToolOutcome(0, List.of(stack(tool(1), 1)), List.of())),
                List.of(stack(PRODUCT, 1)), Optional.empty()), host);
        List<Settlement> received = new ObjectArrayList<>();
        endpoint.settle(sessionId, settlement -> received.add(settlement), host);
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(1)), 1L, "Reconciliation returns only verified actual successor");
        helper.assertValueEqual(assetAmount(received.getFirst().returnedAssets(), tool(0)), 0L, "Old tool is not reconstructed");
        helper.assertValueEqual(received.getFirst().receipts().getFirst().cancelled(), 1L, "Only the remaining operation is cancelled");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_pause_and_current_host_mode_check_preserve_unexecuted_assets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void pauseAndCurrentHostModeCheckPreserveUnexecutedAssets(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest request = request(helper, sessionId, 0, 2, List.of(new SlotStack(0, stack(tool(1), 1))));
        ReusableCraftingAdmission staleMode = prepare(endpoint, request, 0, host);
        host.available = false;
        KeyCounter[] untouched = delivery(staleMode);
        helper.assertTrue(!staleMode.commit(untouched), "Live mode/pattern availability is checked again at commit");
        helper.assertValueEqual(untouched[0].get(tool(1)), 1L, "Changed mode does not consume a partially damaged tool");
        host.available = true;
        ReusableCraftingAdmission accepted = prepare(endpoint, request, 0, host);
        accepted.commit(delivery(accepted));
        host.paused = true;
        helper.assertValueEqual(endpoint.tick(1, 3, false, host), 0, "Explicit unexecuted native pause keeps the complete escrow");
        helper.assertValueEqual(endpoint.receipt(sessionId, 0).orElseThrow().completed(), 0L, "Pause cannot count completed work");
        host.paused = false;
        helper.assertValueEqual(endpoint.tick(2, 3, false, host), 2, "Two actual remaining uses are accepted from non-initial damage");
        endpoint.close(sessionId, host);
        endpoint.settle(sessionId, settlement -> {
            helper.assertTrue(settlement.returnedAssets().isEmpty(), "Both actual remaining uses exhaust the supplied damaged tool");
            return true;
        }, host);
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_ownership_marker_precedes_persist_failure_and_machine_claims_are_rejected")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ownershipMarkerPrecedesPersistFailureAndMachineClaimsAreRejected(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest request = request(helper, sessionId, 0, 1, List.of(new SlotStack(0, stack(tool(0), 1))));
        List<Input> machineClaim = List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.MACHINE_OWNED, rule(), Optional.empty()))), request.inputs().get(1));
        ReusableCraftingRequest unsupported = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 0, request.target(),
                request.pattern(), machineClaim, request.offeredTools(), 1, request.recipeId(), request.actionSource(), request.level());
        helper.assertTrue(endpoint.prepare(unsupported, 0, host) == null, "Native endpoint cannot claim a machine inventory it does not have");
        ReusableCraftingAdmission admission = prepare(endpoint, request, 0, host);
        KeyCounter[] physical = delivery(admission);
        host.failPersist = true;
        try {
            admission.commit(physical);
            helper.fail("Configured persistent-host failure was not reached");
        } catch (IllegalStateException expected) {
            helper.assertTrue(admission.hasTransferredInputOwnership(), "Post-transfer failure does not authorize CPU refund");
        }
        helper.assertValueEqual(physical[0].get(tool(0)), 0L, "Provider accepted physical tool before dirty callback failed");
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().accepted(), 1L, "Provider still owns the accepted durable entry");
        host.failPersist = false;
        PersistentReusableCraftingEndpoint restored = reload(endpoint, helper);
        helper.assertTrue(prepare(restored, request, 1, host).replay(), "Retry finds the already accepted ownership receipt");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_same_slot_consumed_and_held_totals_and_codec_validation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sameSlotConsumedAndHeldTotalsAndCodecValidation(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest base = request(helper, sessionId, 0, 2, List.of(new SlotStack(0, stack(tool(0), 1))));
        List<Input> mixed = List.of(new Input(0, List.of(stack(tool(0), 1)), base.inputs().getFirst().tool()), base.inputs().get(1));
        ReusableCraftingRequest request = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 0, base.target(),
                base.pattern(), mixed, base.offeredTools(), 2, base.recipeId(), base.actionSource(), base.level());
        ReusableCraftingAdmission admission = prepare(endpoint, request, 0, host);
        helper.assertValueEqual(amount(admission.physicalInputs(), 0, tool(0)), 3L, "Same-slot total is two consumed plus one held tool");
        admission.commit(delivery(admission));
        endpoint.tick(1, 1, false, host);
        endpoint.close(sessionId, host);
        endpoint.settle(sessionId, settlement -> {
            helper.assertValueEqual(assetAmount(settlement.returnedAssets(), tool(0)), 1L, "One unused same-key consumed material remains");
            helper.assertValueEqual(assetAmount(settlement.returnedAssets(), tool(1)), 1L, "Held tool follows its actual native successor");
            return true;
        }, host);
        CompoundTag missing = ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess());
        missing.getList("sessions", Tag.TAG_COMPOUND).getCompound(0).remove("acknowledged");
        try {
            ReusableCraftingEndpointNbtCodec.decode(missing, helper.getLevel().registryAccess());
            helper.fail("Missing settlement acknowledgement field must reject persisted state");
        } catch (IllegalArgumentException expected) {
            helper.succeed();
        }
    }

    @TestHolder("reusable_endpoint_later_damage_state_rule_appends_without_replacing_frozen_contract")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void laterDamageStateRuleAppendsWithoutReplacingFrozenContract(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest first = request(helper, sessionId, 0, 1, List.of(new SlotStack(0, stack(tool(0), 1))));
        ReusableCraftingAdmission opened = prepare(endpoint, first, 0, host);
        opened.commit(delivery(opened));
        endpoint.tick(1, 1, false, host);
        endpoint = reload(endpoint, helper);
        ReusableInputRule atDamageOne = ReusableInputRule.fixedDamage(RULE_ID, 1, tool(1), 1, 3, List.of(stack(SCRAP, 1)));
        List<Input> continuedInputs = List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, atDamageOne, Optional.empty()))),
                first.inputs().get(1));
        ReusableCraftingRequest continuation = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 1, first.target(), first.pattern(),
                continuedInputs, List.of(), 1, first.recipeId(), first.actionSource(), first.level());
        ReusableCraftingAdmission appended = prepare(endpoint, continuation, 1, host);
        helper.assertValueEqual(amount(appended.physicalInputs(), 0, tool(1)), 0L,
                "D1 stage reuses the actual resident successor without another physical transfer");
        helper.assertTrue(appended.commit(delivery(appended)), "D0-opened session accepts the same contract frozen from D1");
        helper.assertValueEqual(endpoint.tick(2, 1, false, host), 1, "Second native operation advances actual D1 to D2");
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().heldTools(), List.of(new SlotStack(0, stack(tool(2), 1))),
                "Continued session retains exactly one D2 tool");
        helper.assertValueEqual(endpoint.snapshot().getFirst().session().slotContracts().getFirst().rule().initialKey(), tool(0),
                "Continuation never replaces the original D0 rule or resets lifetime");
        ReusableInputRule changedLoss = ReusableInputRule.fixedDamage(RULE_ID, 1, tool(2), 2, 3, List.of(stack(SCRAP, 1)));
        List<Input> changedInputs = List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, changedLoss, Optional.empty()))), first.inputs().get(1));
        ReusableCraftingRequest incompatible = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 2, first.target(), first.pattern(),
                changedInputs, List.of(), 1, first.recipeId(), first.actionSource(), first.level());
        helper.assertTrue(endpoint.prepare(incompatible, 2, host) == null, "A changed per-use loss is not a continuation of the frozen contract");
        endpoint.close(sessionId, host);
        endpoint.settle(sessionId, settlement -> {
            helper.assertValueEqual(settlement.returnedAssets(), List.of(stack(tool(2), 1)), "Final refund uses the real D2 successor");
            return true;
        }, host);
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_freezes_publication_semantics_across_reload_and_prepared_commit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void freezesPublicationSemanticsAcrossReloadAndPreparedCommit(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest first = request(helper, sessionId, 0, 1, List.of(new SlotStack(0, stack(tool(0), 1))));
        ReusableCraftingAdmission opened = prepare(endpoint, first, 0, host);
        opened.commit(delivery(opened));
        endpoint.tick(1, 1, false, host);
        TrinityPatternIdentity frozen = endpoint.snapshot().getFirst().binding().publicationIdentity();
        endpoint = reload(endpoint, helper);
        helper.assertValueEqual(endpoint.snapshot().getFirst().binding().publicationIdentity(), frozen,
                "NBT preserves the complete publication encoding");
        IPatternDetails changedPattern = new TestPattern(2);
        ReusableCraftingRequest changed = new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", 1, first.target(), changedPattern,
                first.inputs(), List.of(), 1, first.recipeId(), first.actionSource(), first.level());
        helper.assertTrue(endpoint.prepare(changed, 1, host) == null, "Changed native output semantics reject append despite the same encoded key and recipe ID");
        ReusableCraftingRequest continuation = request(helper, sessionId, 1, 1, List.of());
        ReusableCraftingAdmission prepared = prepare(endpoint, continuation, 1, host);
        host.publication = Optional.of(TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(changedPattern), helper.getLevel().registryAccess()));
        KeyCounter[] untouched = delivery(prepared);
        helper.assertTrue(!prepared.commit(untouched), "Host semantic rebind after prepare invalidates commit");
        helper.assertTrue(!prepared.hasTransferredInputOwnership(), "Publication change is rejected before ownership transfer");
        helper.assertValueEqual(untouched[1].get(MATERIAL), 1L, "Rejected publication rebind preserves the transferred candidate material");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_append_before_each_tick_does_not_starve_existing_work")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void appendBeforeEachTickDoesNotStarveExistingWork(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingAdmission opened = prepare(endpoint, request(helper, sessionId, 0, 1,
                List.of(new SlotStack(0, stack(tool(0), 3)))), 0, host);
        opened.commit(delivery(opened));
        helper.assertValueEqual(endpoint.tick(0, 1, false, host), 0, "Initial open still defers native execution to the next tick");
        for (int tick = 1; tick <= 5; tick++) {
            ReusableCraftingAdmission appended = prepare(endpoint, request(helper, sessionId, tick, 1, List.of()), tick, host);
            helper.assertTrue(appended.commit(delivery(appended)), "Continuous per-tick material append is accepted");
            helper.assertValueEqual(endpoint.tick(tick, 1, false, host), 1,
                    "Appending before execution cannot defer already accepted work again");
            helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().completed(), (long) tick,
                    "Every tick advances native completion despite a preceding append");
        }
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().accepted(), 6L, "Initial and five subsequent appends remain accounted");
        helper.assertValueEqual(host.amount(PRODUCT), 5L, "Continuous replenishment still produces real native outputs");
        helper.succeed();
    }

    @TestHolder("reusable_endpoint_exact_firings_reserve_distinct_states_and_reuse_resident_successors")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactFiringsReserveDistinctStatesAndReuseResidentSuccessors(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID sessionId = UUID.randomUUID();
        ReusableCraftingRequest initial = exactRequest(helper, sessionId, 0, 3, tool(0), List.of(new SlotStack(0, stack(tool(0), 3))));
        ReusableCraftingAdmission opened = prepare(endpoint, initial, 0, host);
        helper.assertTrue(opened.commit(delivery(opened)), "Three D0 firings accept three real D0 tools");
        endpoint = reload(endpoint, helper);
        helper.assertValueEqual(endpoint.reservedToolUses(sessionId, 0, tool(0)), 3L, "Pending exact reservations survive reload");
        helper.assertTrue(endpoint.prepare(exactRequest(helper, sessionId, 1, 1, tool(0), List.of()), 1, host) == null,
                "The same D0 units cannot be promised to another pending append");
        endpoint.tick(1, 3, false, host);
        helper.assertValueEqual(endpoint.query(sessionId).orElseThrow().heldTools(), List.of(new SlotStack(0, stack(tool(1), 3))),
                "Three D0 to D1 firings cannot become one tool's consecutive D0 to D3 execution");
        helper.assertValueEqual(endpoint.reservedToolUses(sessionId, 0, tool(0)), 0L, "Completed exact uses release their reservations");
        ReusableCraftingAdmission next = prepare(endpoint, exactRequest(helper, sessionId, 1, 3, tool(1), List.of()), 2, host);
        helper.assertValueEqual(amount(next.physicalInputs(), 0, tool(1)), 0L, "The next D1 stage does not transport its resident tools again");
        helper.assertTrue(next.commit(delivery(next)), "Resident successors are reusable for the next exact stage");
        endpoint.tick(2, 3, false, host);
        endpoint.close(sessionId, host);
        endpoint.settle(sessionId, settlement -> {
            helper.assertValueEqual(settlement.returnedAssets(), List.of(stack(tool(2), 3)), "Final return contains all three actual D2 tools");
            return true;
        }, host);
        helper.assertValueEqual(host.amount(PRODUCT), 6L, "Both exact stages execute their actual six recipe operations");
        helper.succeed();
    }

    private static ReusableCraftingRequest exactRequest(GameTestHelper helper, UUID sessionId, long sequence, long operations,
                                                        AEItemKey state, List<SlotStack> offeredTools) {
        ReusableCraftingRequest base = request(helper, sessionId, sequence, operations, offeredTools);
        List<Input> inputs = List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, rule(), Optional.of(state)))),
                base.inputs().get(1));
        return new ReusableCraftingRequest(sessionId, JOB, base.cpuOwner(), sequence, base.target(), base.pattern(), inputs,
                offeredTools, operations, base.recipeId(), base.actionSource(), base.level());
    }

    private static ReusableCraftingRequest request(GameTestHelper helper, UUID sessionId, long sequence, long operations,
                                                   List<SlotStack> offeredTools) {
        return new ReusableCraftingRequest(sessionId, JOB, "cpu:owner", sequence,
                new Target(TARGET, CountedCraftingTarget.route("native-core-slot"), Optional.empty()), new TestPattern(),
                List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, rule(), Optional.empty()))),
                        new Input(1, List.of(stack(MATERIAL, 1)), Optional.empty())),
                offeredTools, operations, Optional.empty(), new BaseActionSource(), helper.getLevel());
    }

    private static ReusableCraftingAdmission prepare(PersistentReusableCraftingEndpoint endpoint, ReusableCraftingRequest request,
                                                     long tick, Host host) {
        ReusableCraftingAdmission result = endpoint.prepare(request, tick, host);
        if (result == null) {
            throw new IllegalStateException("Test admission unexpectedly rejected");
        }
        return result;
    }

    private static KeyCounter[] delivery(ReusableCraftingAdmission admission) {
        KeyCounter[] result = { new KeyCounter(), new KeyCounter() };
        admission.physicalInputs().forEach(input -> result[input.slot()].add(input.stack().what(), input.stack().amount()));
        return result;
    }

    private static ReusableInputRule rule() {
        return ReusableInputRule.fixedDamage(RULE_ID, 1, tool(0), 1, 3, List.of(stack(SCRAP, 1)));
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static long amount(List<SlotStack> assets, int slot, AEKey key) {
        return assets.stream().filter(asset -> asset.slot() == slot && asset.stack().what().equals(key)).mapToLong(asset -> asset.stack().amount()).sum();
    }

    private static long assetAmount(List<GenericStack> assets, AEKey key) {
        return assets.stream().filter(asset -> asset.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    private static PersistentReusableCraftingEndpoint reload(PersistentReusableCraftingEndpoint endpoint, GameTestHelper helper) {
        return ReusableCraftingEndpointNbtCodec.decode(ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
    }

    /** Independent fixed-three-use native fixture; actual damage is produced without calling rule.advance. */
    private static final class NativeHost implements Host {

        private boolean available = true;
        private boolean paused;
        private boolean failNative;
        private boolean failPersist;
        private Optional<TrinityPatternIdentity> publication = Optional.empty();
        private final List<GenericStack> pending = new ObjectArrayList<>();

        @Override
        public boolean isAvailable(Binding binding) {
            return available && (publication.isEmpty() || publication.orElseThrow().equals(binding.publicationIdentity()));
        }

        @Override
        public NativeResult execute(Binding binding, Operation operation) {
            if (failNative) {
                throw new IllegalStateException("Intentional unknown native effect");
            }
            if (paused) {
                return NativeResult.paused();
            }
            List<GenericStack> successors = new ObjectArrayList<>();
            List<GenericStack> byproducts = new ObjectArrayList<>();
            for (var held : operation.tools()) {
                ItemStack tool = ((AEItemKey) held.stack().what()).toStack();
                int nextDamage = tool.getDamageValue() + 1;
                if (nextDamage == 3) {
                    byproducts.add(stack(SCRAP, held.stack().amount()));
                } else {
                    tool.set(DataComponents.DAMAGE, nextDamage);
                    successors.add(stack(AEItemKey.of(tool), held.stack().amount()));
                }
            }
            return new NativeResult(true, List.of(new ToolOutcome(0, successors, byproducts)), List.of(stack(PRODUCT, 1)), Optional.empty());
        }

        @Override
        public void acceptOutputs(Identity identity, List<GenericStack> outputs) {
            pending.addAll(outputs);
        }

        @Override
        public void persistChanges() {
            if (failPersist) {
                throw new IllegalStateException("Intentional dirty callback failure after transfer");
            }
        }

        private long amount(AEKey key) {
            return assetAmount(pending, key);
        }
    }

    private static final class TestPattern implements IPatternDetails {

        private final long outputAmount;

        private TestPattern() {
            this(1);
        }

        private TestPattern(long outputAmount) {
            this.outputAmount = outputAmount;
        }

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new TestInput(tool(0)), new TestInput(MATERIAL) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(stack(PRODUCT, outputAmount));
        }
    }

    private record TestInput(AEItemKey key) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { stack(key, 1) };
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.equals(key);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return template;
        }
    }
}
