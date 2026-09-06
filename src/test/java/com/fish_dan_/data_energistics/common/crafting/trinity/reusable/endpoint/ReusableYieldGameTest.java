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
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
public final class ReusableYieldGameTest {

    private static final String TARGET = "yield-native-target";
    private static final AEItemKey TOOL = AEItemKey.of(Items.IRON_AXE);
    private static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey PRODUCT = AEItemKey.of(Items.IRON_NUGGET);
    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "yield_tool");

    private ReusableYieldGameTest() {}

    @TestHolder("reusable_yield_prepare_is_read_only_and_one_signal_closes_busy_owner_at_original_deadline")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void prepareIsReadOnlyAndOneSignalClosesBusyOwnerAtOriginalDeadline(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID owner = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        ReusableCraftingRequest opening = request(owner, job, "owner-cpu", 0, 100, true, TARGET, Optional.empty(), 1, helper);
        commit(endpoint, opening, 0, host);
        ReusableCraftingRequest contender = request(UUID.randomUUID(), UUID.randomUUID(), "other-cpu", 0, 1, true,
                TARGET, Optional.empty(), 1, helper);
        CompoundTag before = ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess());
        helper.assertTrue(endpoint.prepare(contender, 100, host) == null, "A competing preparation cannot replace the current resident");
        helper.assertValueEqual(ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess()), before,
                "Preparation cannot implicitly latch competition or mutate owned assets");
        helper.assertTrue(endpoint.requestYield(contender, 100, host), "An explicit different job/session latches a yield request");
        helper.assertValueEqual(endpoint.tick(100, 2, host), 2, "Owner continues under its normal work budget during the grace period");
        commit(endpoint, request(owner, job, "owner-cpu", 1, 4, false, TARGET, Optional.empty(), 1, helper), 110, host);
        helper.assertTrue(endpoint.requestYield(contender, 119, host), "Repeated valid signal remains acknowledged");
        helper.assertValueEqual(endpoint.tick(119, 2, host), 2, "Native work is not interrupted before the deadline");
        helper.assertValueEqual(endpoint.tick(120, 2, host), 0, "Repeated signal and owner append cannot extend the first deadline");
        helper.assertValueEqual(endpoint.query(owner).orElseThrow().state(), State.RETURN_PENDING, "Busy owner closes at its safe point");
        List<Settlement> received = new ObjectArrayList<>();
        helper.assertTrue(endpoint.settle(owner, settlement -> received.add(settlement), host), "Existing directed settlement handles the yielded assets");
        helper.assertValueEqual(amount(received.getFirst().returnedAssets(), MATERIAL), 100L,
                "Refund contains the 100 actual unexecuted materials from both accepted appends");
        helper.assertValueEqual(amount(received.getFirst().returnedAssets(), TOOL), 1L, "Yield returns exactly the one resident physical tool");
        helper.assertValueEqual(host.executed, 4L, "Only four actual operations ran before yield");
        helper.assertValueEqual(amount(host.outputs, PRODUCT), 4L, "Ordinary outputs remain on the original independent route");
        helper.assertTrue(endpoint.query(contender.sessionId()).isEmpty(), "Yield signal does not admit or take ownership of contender inputs");
        helper.succeed();
    }

    @TestHolder("reusable_yield_rejects_self_unrelated_target_mode_rule_and_unavailable_binding_without_mutation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsSelfUnrelatedTargetModeRuleAndUnavailableBindingWithoutMutation(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint endpoint = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID owner = UUID.randomUUID();
        ReusableCraftingRequest opening = request(owner, UUID.randomUUID(), "owner", 0, 100, true, TARGET, Optional.empty(), 1, helper);
        ReusableCraftingRequest contender = request(UUID.randomUUID(), UUID.randomUUID(), "other", 0, 1, true,
                TARGET, Optional.empty(), 1, helper);
        helper.assertTrue(!endpoint.requestYield(contender, 10, host), "Unoccupied target cannot yield a nonexistent owner");
        commit(endpoint, opening, 0, host);
        CompoundTag before = ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess());
        helper.assertTrue(!endpoint.requestYield(opening, 10, host), "Owner cannot signal competition against itself");
        helper.assertTrue(!endpoint.requestYield(request(UUID.randomUUID(), UUID.randomUUID(), "other", 0, 1, true,
                "different-native-target", Optional.empty(), 1, helper), 10, host), "Unknown target cannot close a different resident");
        helper.assertTrue(!endpoint.requestYield(request(UUID.randomUUID(), UUID.randomUUID(), "other", 0, 1, true,
                TARGET, Optional.of(ResourceLocation.withDefaultNamespace("different_mode")), 1, helper), 10, host),
                "Different machine mode is not a valid competing binding");
        helper.assertTrue(!endpoint.requestYield(request(UUID.randomUUID(), UUID.randomUUID(), "other", 0, 1, true,
                TARGET, Optional.empty(), 2, helper), 10, host), "Changed frozen rule revision cannot request yield");
        host.available = false;
        helper.assertTrue(!endpoint.requestYield(contender, 10, host), "Unavailable live binding cannot request yield");
        host.available = true;
        helper.assertValueEqual(ReusableCraftingEndpointNbtCodec.encode(endpoint, helper.getLevel().registryAccess()), before,
                "Rejected contenders do not alter the owner deadline or accounting");
        helper.assertValueEqual(endpoint.tick(1000, 1, host), 1, "Unrelated signals cannot close a still-busy owner later");
        helper.succeed();
    }

    @TestHolder("reusable_yield_deadline_survives_restart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void deadlineSurvivesRestart(GameTestHelper helper) {
        PersistentReusableCraftingEndpoint source = new PersistentReusableCraftingEndpoint(TARGET);
        NativeHost host = new NativeHost();
        UUID owner = UUID.randomUUID();
        commit(source, request(owner, UUID.randomUUID(), "owner", 0, 100, true, TARGET, Optional.empty(), 1, helper), 0, host);
        ReusableCraftingRequest contender = request(UUID.randomUUID(), UUID.randomUUID(), "other", 0, 1, true,
                TARGET, Optional.empty(), 1, helper);
        source.requestYield(contender, 100, host);
        CompoundTag saved = ReusableCraftingEndpointNbtCodec.encode(source, helper.getLevel().registryAccess());
        PersistentReusableCraftingEndpoint restored = ReusableCraftingEndpointNbtCodec.decode(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.requestYield(contender, 118, host), "Restored request remains acknowledged without changing its first tick");
        helper.assertValueEqual(restored.tick(119, 1, host), 1, "Restored owner remains runnable before its original deadline");
        helper.assertValueEqual(restored.tick(120, 1, host), 0, "Restoration does not restart the twenty-tick interval");
        helper.assertValueEqual(restored.query(owner).orElseThrow().cancelled(), 99L, "Restored deadline cancels only actual unexecuted work");
        helper.succeed();
    }

    private static ReusableCraftingRequest request(UUID id, UUID job, String cpu, long sequence, long count, boolean supplyTool,
                                                   String target, Optional<ResourceLocation> mode, long ruleRevision, GameTestHelper helper) {
        ReusableInputRule rule = ReusableInputRule.unchanged(RULE_ID, ruleRevision, TOOL);
        return new ReusableCraftingRequest(id, job, cpu, sequence, new Target(target, CountedCraftingTarget.route(target), mode), new TestPattern(),
                List.of(new Input(0, List.of(), Optional.of(new Tool(1, Ownership.CPU_SUPPLIED, rule, Optional.of(TOOL)))),
                        new Input(1, List.of(new GenericStack(MATERIAL, 1)), Optional.empty())),
                supplyTool ? List.of(new SlotStack(0, new GenericStack(TOOL, 1))) : List.of(), count,
                Optional.empty(), new BaseActionSource(), helper.getLevel());
    }

    private static void commit(PersistentReusableCraftingEndpoint endpoint, ReusableCraftingRequest request, long tick, Host host) {
        ReusableCraftingAdmission admission = endpoint.prepare(request, tick, host);
        if (admission == null) {
            throw new IllegalStateException("Yield fixture admission unexpectedly rejected");
        }
        KeyCounter[] inputs = { new KeyCounter(), new KeyCounter() };
        admission.physicalInputs().forEach(input -> inputs[input.slot()].add(input.stack().what(), input.stack().amount()));
        if (!admission.commit(inputs)) {
            throw new IllegalStateException("Yield fixture delivery unexpectedly rejected");
        }
    }

    private static long amount(List<GenericStack> stacks, AEKey key) {
        return stacks.stream().filter(stack -> stack.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    private static final class NativeHost implements Host {

        private boolean available = true;
        private long executed;
        private int saves;
        private final List<GenericStack> outputs = new ObjectArrayList<>();

        @Override
        public boolean isAvailable(Binding binding) {
            return available;
        }

        @Override
        public NativeResult execute(Binding binding, Operation operation) {
            executed++;
            return new NativeResult(true, List.of(new ToolOutcome(0, operation.tools().stream().map(tool -> tool.stack()).toList(), List.of())),
                    List.of(new GenericStack(PRODUCT, 1)), Optional.empty());
        }

        @Override
        public void acceptOutputs(Identity identity, List<GenericStack> produced) {
            outputs.addAll(produced);
        }

        @Override
        public void persistChanges() {
            saves++;
        }
    }

    private static final class TestPattern implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ExactInput(TOOL), new ExactInput(MATERIAL) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(PRODUCT, 1));
        }
    }

    private record ExactInput(AEKey key) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(key, 1) };
        }

        @Override
        public long getMultiplier() {
            return 1;
        }

        @Override
        public boolean isValid(AEKey candidate, Level level) {
            return key.equals(candidate);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return template;
        }
    }
}
