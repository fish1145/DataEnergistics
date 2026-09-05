package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule.Transition;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputRuleGameTest {

    private static final ResourceLocation RULE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "test_tool");

    private ReusableInputRuleGameTest() {}

    @TestHolder("reusable_input_unchanged_preserves_one_exact_tool_at_long_limit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unchangedPreservesOneExactToolAtLongLimit(GameTestHelper helper) {
        AEItemKey tool = tool(4);
        ReusableInputRule rule = ReusableInputRule.unchanged(RULE_ID, 1L, tool);
        helper.assertValueEqual(rule.guaranteedUses(tool), Long.MAX_VALUE, "Explicit unchanged rule is unbounded");
        helper.assertValueEqual(rule.advance(tool, Long.MAX_VALUE).successor(), tool,
                "Even the largest batch retains exactly the original physical key");
        helper.assertTrue(rule.advance(tool, 0L).byproducts().isEmpty(), "Zero uses cannot produce anything");
        expectIllegal(helper, () -> rule.advance(tool(5), 1L), "Changed Damage cannot match an unchanged rule");
        expectIllegal(helper, () -> rule.advance(tool, -1L), "Negative use count must be rejected");
        helper.succeed();
    }

    @TestHolder("reusable_input_fixed_damage_preserves_components_and_exhausts_exactly")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void fixedDamagePreservesComponentsAndExhaustsExactly(GameTestHelper helper) {
        AEItemKey initial = tool(1);
        GenericStack scrap = new GenericStack(AEItemKey.of(Items.STICK), 2L);
        ReusableInputRule rule = ReusableInputRule.fixedDamage(RULE_ID, 2L, initial, 2, 6, List.of(scrap));
        helper.assertValueEqual(rule.guaranteedUses(initial), 3L, "Final exhausting use is included");
        helper.assertValueEqual(rule.advance(initial, 2L).successor(), tool(5), "Damage advances but name is retained");
        helper.assertValueEqual(rule.guaranteedUses(tool(5)), 1L, "Resumed state has only one remaining use");
        helper.assertTrue(rule.advance(initial, 3L).successor() == null, "Last legal use consumes the tool");
        helper.assertValueEqual(rule.advance(initial, 3L).byproducts(), List.of(scrap), "Exhaustion yields scrap once");
        expectIllegal(helper, () -> rule.advance(initial, 4L), "Overdrawing durability must fail");
        expectIllegal(helper, () -> rule.guaranteedUses(tool(6)), "An already exhausted state must fail");
        ItemStack renamed = initial.toStack();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        expectIllegal(helper, () -> rule.guaranteedUses(AEItemKey.of(renamed)), "Non-Damage components are exact");
        expectIllegal(helper, () -> ReusableInputRule.fixedDamage(RULE_ID, 1L, initial, 0, 6, List.of()),
                "Zero loss must use the explicit unchanged contract");
        helper.succeed();
    }

    @TestHolder("reusable_input_state_table_tracks_successor_exhaustion_and_cycle_outputs")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void stateTableTracksSuccessorExhaustionAndCycleOutputs(GameTestHelper helper) {
        AEItemKey first = tool(1);
        AEItemKey second = tool(2);
        GenericStack scrap = new GenericStack(AEItemKey.of(Items.STICK), 1L);
        ReusableInputRule finite = ReusableInputRule.transitions(RULE_ID, 1L, first, List.of(
                new Transition(first, second, List.of(scrap)), new Transition(second, null, List.of(scrap))));
        helper.assertValueEqual(finite.guaranteedUses(first), 2L, "Both explicit transitions are usable");
        helper.assertValueEqual(finite.advance(first, 1L).successor(), second, "First transition retains exact successor");
        helper.assertTrue(finite.advance(first, 2L).successor() == null, "Final transition legally exhausts");
        helper.assertValueEqual(finite.advance(first, 2L).byproducts().getFirst().amount(), 2L, "Outputs aggregate");
        expectIllegal(helper, () -> finite.advance(first, 3L), "Finite table cannot continue after exhaustion");
        ReusableInputRule cycle = ReusableInputRule.transitions(RULE_ID, 2L, first, List.of(
                new Transition(first, second, List.of(scrap)), new Transition(second, first, List.of())));
        helper.assertValueEqual(cycle.guaranteedUses(first), Long.MAX_VALUE, "Complete deterministic cycle is unbounded");
        ReusableInputRule.Result result = cycle.advance(first, Long.MAX_VALUE);
        helper.assertValueEqual(result.successor(), second, "Odd cycle count retains the second state");
        helper.assertValueEqual(result.byproducts().getFirst().amount(), Long.MAX_VALUE / 2L + 1L,
                "Cycle acceleration preserves exact per-step byproducts at long boundary");
        expectIllegal(helper, () -> ReusableInputRule.transitions(RULE_ID, 1L, first,
                List.of(new Transition(first, second, List.of()))), "Unknown successor must reject the rule");
        ReusableInputRule overflowing = ReusableInputRule.transitions(RULE_ID, 3L, first, List.of(
                new Transition(first, first, List.of(new GenericStack(scrap.what(), 2L)))));
        try {
            overflowing.advance(first, Long.MAX_VALUE);
        } catch (ArithmeticException expected) {
            helper.succeed();
            return;
        }
        helper.fail("Byproduct overflow must be rejected instead of wrapping into an invalid amount");
    }

    @TestHolder("reusable_input_rule_nbt_roundtrip_preserves_all_semantics")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ruleNbtRoundtripPreservesAllSemantics(GameTestHelper helper) {
        AEItemKey key = tool(1);
        List<ReusableInputRule> rules = List.of(
                ReusableInputRule.unchanged(RULE_ID, 3L, key),
                ReusableInputRule.fixedDamage(RULE_ID, 4L, key, 2, 6,
                        List.of(new GenericStack(AEItemKey.of(Items.STICK), 2L))),
                ReusableInputRule.transitions(RULE_ID, 5L, key, List.of(new Transition(key, null, List.of()))));
        for (ReusableInputRule rule : rules) {
            CompoundTag tag = ReusableInputRuleNbtCodec.encode(rule, helper.getLevel().registryAccess());
            ReusableInputRule restored = ReusableInputRuleNbtCodec.decode(tag, helper.getLevel().registryAccess());
            helper.assertValueEqual(restored, rule, "Complete frozen rule must survive reload without adapter callbacks");
            helper.assertValueEqual(restored.advance(key, 1L), rule.advance(key, 1L), "Behavior must survive reload");
            tag.remove("revision");
            expectIllegal(helper, () -> ReusableInputRuleNbtCodec.decode(tag, helper.getLevel().registryAccess()),
                    "Missing rule revision must not silently become zero");
        }
        helper.succeed();
    }

    @TestHolder("reusable_input_lookup_is_explicit_and_rejects_conflicts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void lookupIsExplicitAndRejectsConflicts(GameTestHelper helper) {
        AEItemKey key = tool(2);
        GenericStack slot = new GenericStack(key, 2L);
        ReusableInputContext context = ReusableInputContext.builder()
                .pattern(new SingleInputPattern(slot)).actualInput(slot).exactInputs(List.of(slot)).inputSlot(0)
                .ownership(ReusableInputContext.Ownership.CPU_SUPPLIED).actionSource(new BaseActionSource())
                .level(helper.getLevel()).recipeId(Optional.empty()).machineMode(Optional.empty())
                .target(CountedCraftingTarget.route("test")).build();
        helper.assertTrue(new FrozenReusableInputRules(List.of()).resolve(context).isEmpty(),
                "No rule means legacy behavior, even though the test pattern returns its tool unchanged");
        ReusableInputRule unchanged = ReusableInputRule.unchanged(RULE_ID, 1L, key);
        ObjectArrayList<ReusableInputRuleAdapter> mutable = new ObjectArrayList<>();
        mutable.add(new FixedAdapter(RULE_ID, unchanged));
        FrozenReusableInputRules lookup = new FrozenReusableInputRules(mutable);
        mutable.clear();
        helper.assertValueEqual(lookup.resolve(context).orElseThrow(), unchanged, "Lookup copies frozen registration list");
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "other_tool");
        FrozenReusableInputRules conflict = new FrozenReusableInputRules(List.of(new FixedAdapter(RULE_ID, unchanged),
                new FixedAdapter(other, ReusableInputRule.unchanged(other, 1L, key))));
        expectState(helper, () -> conflict.resolve(context), "Two explicit claimants must not silently choose a rule");
        expectState(helper, () -> new FrozenReusableInputRules(List.of(new FixedAdapter(RULE_ID, unchanged),
                new FixedAdapter(RULE_ID, unchanged))), "Duplicate adapter IDs must fail at snapshot creation");
        helper.succeed();
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("exact reusable tool"));
        return AEItemKey.of(stack);
    }

    private static void expectIllegal(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        helper.fail(message);
    }

    private static void expectState(GameTestHelper helper, Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail(message);
    }

    private record FixedAdapter(ResourceLocation id, ReusableInputRule rule) implements ReusableInputRuleAdapter {

        @Override
        public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
            return Optional.of(rule);
        }
    }

    private record SingleInputPattern(GenericStack input) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ExactInput(input) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.STICK), 1L));
        }
    }

    private record ExactInput(GenericStack input) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { input };
        }

        @Override
        public long getMultiplier() {
            return input.amount();
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return input.what().equals(key);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return template;
        }
    }
}
