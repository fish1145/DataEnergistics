package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityPatternSelector;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityBoundInputSnapshotCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import java.util.Map;
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableExactExecutionBindingGameTest {

    private ReusableExactExecutionBindingGameTest() {}

    @TestHolder("reusable_exact_binding_survives_execution_snapshot_without_ordinal_reinterpretation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exactBindingSurvivesExecutionSnapshot(GameTestHelper helper) {
        AEItemKey initial = tool(2);
        AEItemKey next = tool(3);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        ReusableInputRule rule = ReusableInputRule.fixedDamage(
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "exact_execution_test"),
                1L, initial, 1, 8, List.of());
        List<TrinityBoundPatternInput> bindings = List.of(new TrinityBoundPatternInput(
                0, 42, new GenericStack(initial, 1), 1L, next, rule, List.of()));
        var identity = new TrinityPatternIdentity("definition", "publication");
        var firing = new TrinityPlanPatternFiring(identity, output, 42, BigInteger.ONE,
                Map.of(initial, BigInteger.ONE), Map.of(output, BigInteger.ONE), Map.of(next, BigInteger.ONE), bindings);
        var delta = Map.<AEKey, BigInteger>of(initial, BigInteger.ONE.negate(), next, BigInteger.ONE, output, BigInteger.ONE);
        var stage = new TrinityPlanStage(0, false, Set.of(), List.of(firing), Map.of(initial, BigInteger.ONE), delta);
        var plan = TrinityCraftingPlan.builder().finalOutput(new GenericStack(output, 1L))
                .bytes(BigInteger.ZERO).catalogRevision(1L).quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(initial, BigInteger.ONE)).patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage)).stageOrder(List.of(0)).targetNetChange(delta).build();
        CompoundTag encoded = TrinityPlanExecution.create(plan, 10L).save(helper.getLevel().registryAccess(), 10L);
        helper.assertValueEqual(encoded.getInt("schema_version"), 8, "Exact assignments use a new explicit schema");
        var restored = TrinityPlanExecution.restore(encoded, helper.getLevel().registryAccess(), 20L);
        var work = restored.pollDispatchable(20L, Set.of(), ignored -> true, true).orElseThrow();
        helper.assertValueEqual(work.exactBindings(), bindings, "Rule, quantities and damaged keys survive restore");
        var selected = TrinityPatternSelector.create().selectExact(new ToolPattern(), work.plannedVariantOrdinal(),
                work.exactBindings(), 100L, key -> key.equals(initial) ? 1L : 0L, ignored -> 0L, helper.getLevel());
        helper.assertTrue(selected instanceof TrinityPatternSelector.Selected, "Expanded ordinal 42 must not index one legacy alternative");
        helper.assertValueEqual(((TrinityPatternSelector.Selected) selected).inputsPerCraft(), List.of(new GenericStack(initial, 1L)),
                "Execution retains the actual damaged tool rather than the encoded fresh template");
        CompoundTag old = encoded.copy();
        old.putInt("schema_version", 7);
        for (Tag storedStage : old.getList("stages", Tag.TAG_COMPOUND)) {
            for (Tag storedFiring : ((CompoundTag) storedStage).getList("firings", Tag.TAG_COMPOUND)) {
                ((CompoundTag) storedFiring).remove("exact_bindings");
            }
        }
        var legacy = TrinityPlanExecution.restore(old, helper.getLevel().registryAccess(), 30L);
        helper.assertTrue(legacy.pollDispatchable(30L, Set.of(), ignored -> true, true).orElseThrow().exactBindings().isEmpty(),
                "Schema 7 restores old ordinal behavior, not inferred reusable bindings");
        ListTag invalid = TrinityBoundInputSnapshotCodec.write(bindings, helper.getLevel().registryAccess());
        invalid.getCompound(0).remove("rule");
        try {
            TrinityBoundInputSnapshotCodec.read(invalid, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException expected) {
            helper.succeed();
            return;
        }
        helper.fail("Missing frozen rule must fail instead of silently restoring as an ordinary input");
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }

    private record ToolPattern() implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new ToolInput() };
        }
    }

    private record ToolInput() implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(tool(0), 1L) };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return input instanceof AEItemKey item && item.getItem() == Items.WOODEN_AXE;
        }

        @Override
        public AEKey getRemainingKey(AEKey input) {
            return tool(((AEItemKey) input).toStack().getDamageValue() + 1);
        }
    }
}
