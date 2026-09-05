package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputPlanningExpansionGameTest {

    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "planning_tool");

    private ReusableInputPlanningExpansionGameTest() {}

    @TestHolder("reusable_planning_keeps_rules_bound_to_complete_input_assignment")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsRulesBoundToCompleteInputAssignment(GameTestHelper helper) {
        TestPattern pattern = new TestPattern(3, true);
        AEItemKey redstone = AEItemKey.of(Items.REDSTONE);
        ReusableInputRules rules = context -> {
            helper.assertTrue(helper.getLevel().getServer().isSameThread(), "World callbacks stay on the server thread");
            if (context.inputSlot() != 0) {
                return Optional.empty();
            }
            AEItemKey key = (AEItemKey) context.actualInput().what();
            return Optional.of(context.exactInputs().get(1).what().equals(redstone) ?
                    ReusableInputRule.fixedDamage(RULE_ID, 1L, key, 1, 4, List.of()) :
                    ReusableInputRule.unchanged(RULE_ID, 2L, key));
        };
        var result = ReusableInputPlanningExpansion.capture(context(helper, pattern), List.of(), rules, 8,
                TrinityPlanningControl.unbounded());
        helper.assertTrue(result instanceof ReusableInputPlanningExpansion.Captured, "Complete contextual capture should fit");
        helper.assertValueEqual(((ReusableInputPlanningExpansion.Captured) result).bindings().size(), 8,
                "Every reached tool state is reconsidered with each legal material assignment");
        for (List<TrinityBoundPatternInput> assignment : ((ReusableInputPlanningExpansion.Captured) result).bindings()) {
            ReusableInputRule rule = assignment.getFirst().reusableRule();
            helper.assertTrue(rule != null, "Tool slot retains its explicit rule");
            ReusableInputRule.Kind expected = assignment.get(1).template().what().equals(redstone) ?
                    ReusableInputRule.Kind.FIXED_DAMAGE : ReusableInputRule.Kind.UNCHANGED;
            helper.assertValueEqual(rule.kind(), expected, "No Cartesian cross-assignment rule reuse is permitted");
        }
        helper.succeed();
    }

    @TestHolder("reusable_planning_unknown_rules_and_invalid_successors_preserve_ae_contract")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unknownRulesAndInvalidSuccessorsPreserveAeContract(GameTestHelper helper) {
        TestPattern pattern = new TestPattern(1, false);
        var unknown = ReusableInputPlanningExpansion.capture(context(helper, pattern), List.of(tool(1)),
                ignored -> Optional.empty(), 4, TrinityPlanningControl.unbounded());
        helper.assertTrue(unknown instanceof ReusableInputPlanningExpansion.Captured, "Unknown rules retain normal capture");
        var legacy = (ReusableInputPlanningExpansion.Captured) unknown;
        helper.assertFalse(legacy.hasReusableInputs(), "Observed unchanged remainder must not imply a reusable rule");
        helper.assertValueEqual(legacy.bindings().size(), 1, "Unknown inventory-only variants are not guessed");
        var known = ReusableInputPlanningExpansion.capture(context(helper, pattern), List.of(),
                input -> input.inputSlot() == 0 ? Optional.of(ReusableInputRule.fixedDamage(
                        RULE_ID, 1L, (AEItemKey) input.actualInput().what(), 1, 4, List.of())) : Optional.empty(),
                4, TrinityPlanningControl.unbounded());
        helper.assertTrue(known instanceof ReusableInputPlanningExpansion.Captured, "Known states should be captured");
        var bounded = (ReusableInputPlanningExpansion.Captured) known;
        helper.assertValueEqual(bounded.bindings().size(), 2, "Original IInput rejects Damage 2 as a future input");
        helper.assertValueEqual(binding(bounded, tool(1)).remainingKey(), tool(2),
                "Rejected future input is still retained as the physical one-use output");
        helper.succeed();
    }

    @TestHolder("reusable_planning_limits_never_publish_partial_state_graphs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void limitsNeverPublishPartialStateGraphs(GameTestHelper helper) {
        TestPattern pattern = new TestPattern(3, false);
        ReusableInputRules rules = input -> input.inputSlot() == 0 ? Optional.of(ReusableInputRule.fixedDamage(
                RULE_ID, 1L, (AEItemKey) input.actualInput().what(), 1, 4, List.of())) : Optional.empty();
        var limit = ReusableInputPlanningExpansion.capture(context(helper, pattern), List.of(), rules, 3,
                TrinityPlanningControl.unbounded());
        helper.assertTrue(limit instanceof ReusableInputPlanningExpansion.Stopped, "One state beyond the limit rejects capture");
        helper.assertValueEqual(((ReusableInputPlanningExpansion.Stopped) limit).reason(),
                ReusableInputPlanningExpansion.Reason.BINDING_LIMIT, "Configured binding bound is explicit");
        AtomicLong clock = new AtomicLong();
        var timed = ReusableInputPlanningExpansion.capture(context(helper, pattern), List.of(), rules, 10,
                TrinityPlanningControl.create(() -> false, clock::getAndIncrement, 3L));
        helper.assertTrue(timed instanceof ReusableInputPlanningExpansion.Stopped, "Expired server capture must stop");
        helper.assertValueEqual(((ReusableInputPlanningExpansion.Stopped) timed).reason(),
                ReusableInputPlanningExpansion.Reason.DEADLINE, "Deadline is separate from missing materials");
        helper.succeed();
    }

    private static TrinityBoundPatternInput binding(ReusableInputPlanningExpansion.Captured captured, AEItemKey key) {
        return captured.bindings().stream().map(List::getFirst)
                .filter(input -> input.template().what().equals(key)).findFirst().orElseThrow();
    }

    @TestHolder("reusable_planning_cursor_preserves_exact_states_without_restarting_rule_callbacks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cursorPreservesExactStatesWithoutRestartingRuleCallbacks(GameTestHelper helper) {
        TestPattern pattern = new TestPattern(3, false);
        AtomicLong callbacks = new AtomicLong();
        ReusableInputRules rules = input -> {
            callbacks.incrementAndGet();
            return input.inputSlot() == 0 ? Optional.of(ReusableInputRule.fixedDamage(
                    RULE_ID, 1L, (AEItemKey) input.actualInput().what(), 1, 4,
                    List.of(new GenericStack(AEItemKey.of(Items.STICK), 3L)))) : Optional.empty();
        };
        ReusableInputPlanningCursor cursor = new ReusableInputPlanningCursor(context(helper, pattern), List.of(tool(2)),
                rules, 4, TrinityPlanningControl.unbounded());
        AtomicLong clock = new AtomicLong();
        ReusableInputPlanningExpansion.Result result = null;
        int slices = 0;
        while (result == null && slices < 1000) {
            result = cursor.advance(1L, clock::getAndIncrement);
            slices++;
        }
        helper.assertTrue(result instanceof ReusableInputPlanningExpansion.Captured,
                "Small server tick slices must eventually publish the complete graph");
        helper.assertTrue(slices > 1, "Capture must suspend and resume across ticks");
        var captured = (ReusableInputPlanningExpansion.Captured) result;
        helper.assertTrue(captured.hasReusableInputs(), "Registered rules must be retained");
        helper.assertValueEqual(captured.bindings().size(), 4,
                "All inventory and successor states survive cursor suspension");
        TrinityBoundPatternInput damaged = binding(captured, tool(2));
        helper.assertValueEqual(damaged.remainingKey(), tool(3), "Inventory tool predicts its actual next Damage");
        helper.assertValueEqual(damaged.remainingAmount(), BigInteger.valueOf(2L), "Two slot tool units each return once");
        TrinityBoundPatternInput exhausted = binding(captured, tool(3));
        helper.assertTrue(exhausted.remainingKey() == null, "Exhaustion must not invent another tool state");
        helper.assertValueEqual(exhausted.byproducts().getFirst().amount(), 3L, "Byproducts remain per-tool-unit metadata");
        helper.assertValueEqual(exhausted.consumedAmount(), BigInteger.valueOf(2L), "Slot quantity remains exact");
        helper.assertValueEqual(callbacks.get(), 8L, "Each of four complete assignments resolves its two slots once");
        helper.assertValueEqual(cursor.advance(1L, clock::getAndIncrement), result, "Completed cursor is idempotent");
        helper.succeed();
    }

    @TestHolder("reusable_planning_cursor_cancel_stops_pending_capture_without_partial_graph")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cursorCancelStopsPendingCaptureWithoutPartialGraph(GameTestHelper helper) {
        ReusableInputPlanningCursor cursor = new ReusableInputPlanningCursor(context(helper, new TestPattern(3, false)),
                List.of(tool(2)), ignored -> Optional.empty(), 4, TrinityPlanningControl.unbounded());
        AtomicLong clock = new AtomicLong();
        helper.assertTrue(cursor.advance(1L, clock::getAndIncrement) == null, "First small slice should retain pending work");
        cursor.cancel();
        var result = cursor.advance(1L, clock::getAndIncrement);
        helper.assertTrue(result instanceof ReusableInputPlanningExpansion.Stopped, "Cancellation publishes no partial graph");
        helper.assertValueEqual(((ReusableInputPlanningExpansion.Stopped) result).reason(),
                ReusableInputPlanningExpansion.Reason.CANCELLED, "Cancellation remains distinct from tick budget exhaustion");
        helper.succeed();
    }

    private static ReusableInputContext context(GameTestHelper helper, TestPattern pattern) {
        GenericStack tool = new GenericStack(tool(0), 2L);
        return ReusableInputContext.builder().pattern(pattern).actualInput(tool)
                .exactInputs(List.of(tool, new GenericStack(AEItemKey.of(Items.REDSTONE), 1L))).inputSlot(0)
                .ownership(ReusableInputContext.Ownership.CPU_SUPPLIED).actionSource(new BaseActionSource())
                .level(helper.getLevel()).recipeId(Optional.empty()).machineMode(Optional.empty())
                .target(CountedCraftingTarget.provider()).build();
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }

    private record TestPattern(int maximumAcceptedDamage, boolean hasAlternativeMaterial) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ToolInput(maximumAcceptedDamage), new MaterialInput(hasAlternativeMaterial) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }
    }

    private record ToolInput(int maximumAcceptedDamage) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(tool(0), 1L) };
        }

        @Override
        public long getMultiplier() {
            return 2L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input instanceof AEItemKey item && item.getItem() == Items.WOODEN_AXE &&
                    item.toStack().getDamageValue() <= maximumAcceptedDamage;
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return template;
        }
    }

    private record MaterialInput(boolean alternative) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            GenericStack redstone = new GenericStack(AEItemKey.of(Items.REDSTONE), 1L);
            return alternative ? new GenericStack[] { redstone, new GenericStack(AEItemKey.of(Items.COAL), 1L) } :
                    new GenericStack[] { redstone };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input.equals(AEItemKey.of(Items.REDSTONE)) || alternative && input.equals(AEItemKey.of(Items.COAL));
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
