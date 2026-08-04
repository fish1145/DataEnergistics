package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityPatternSelectorTest {

    private static AEKey iron;
    private static AEKey gold;
    private static AEKey redstone;

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
        iron = AEItemKey.of(Items.IRON_INGOT);
        gold = AEItemKey.of(Items.GOLD_INGOT);
        redstone = AEItemKey.of(Items.REDSTONE);
    }

    @Test
    void retainsPlannedBindingForNonCycleStage() {
        IPatternDetails pattern = pattern(input(2L, stack(iron, 1L), stack(gold, 1L)));

        TrinityPatternSelector.Result result = TrinityPatternSelector.create().select(
                pattern,
                0,
                false,
                10L,
                availability(Map.of(iron, 2L, gold, 20L)),
                ignored -> 0L,
                16);

        TrinityPatternSelector.Selected selected = assertInstanceOf(
                TrinityPatternSelector.Selected.class,
                result);
        assertEquals(0, selected.variantOrdinal());
        assertEquals(1L, selected.maximumCrafts());
        assertEquals(List.of(stack(iron, 2L)), selected.inputsPerCraft());
        assertEquals(iron, selected.extractionPattern().getInputs()[0].getPossibleInputs()[0].what());
    }

    @Test
    void interpretsPlannedOrdinalAfterDeduplicatingEquivalentAlternatives() {
        IPatternDetails pattern = pattern(input(
                1L,
                stack(iron, 1L),
                stack(iron, 1L),
                stack(gold, 1L)));

        TrinityPatternSelector.Selected selected = assertInstanceOf(
                TrinityPatternSelector.Selected.class,
                TrinityPatternSelector.create().select(
                        pattern,
                        1,
                        false,
                        4L,
                        availability(Map.of(gold, 4L)),
                        ignored -> 0L,
                        2));

        assertEquals(1, selected.variantOrdinal());
        assertEquals(List.of(stack(gold, 1L)), selected.inputsPerCraft());
    }

    @Test
    void dynamicallyChoosesLargestExecutableCycleBatch() {
        IPatternDetails pattern = pattern(input(1L, stack(iron, 1L), stack(gold, 1L)));

        TrinityPatternSelector.Result result = TrinityPatternSelector.create().select(
                pattern,
                0,
                true,
                10L,
                availability(Map.of(iron, 1L, gold, 2L)),
                availability(Map.of(iron, 2L, gold, 6L)),
                16);

        TrinityPatternSelector.Selected selected = assertInstanceOf(
                TrinityPatternSelector.Selected.class,
                result);
        assertEquals(1, selected.variantOrdinal());
        assertEquals(8L, selected.maximumCrafts());
        assertEquals(List.of(stack(gold, 1L)), selected.inputsPerCraft());
        assertTrue(selected.observedKeys().containsAll(List.of(iron, gold)));
    }

    @Test
    void usesStableOrdinalWhenAvailabilityAndBorrowingTie() {
        IPatternDetails pattern = pattern(input(1L, stack(iron, 1L), stack(gold, 1L)));

        TrinityPatternSelector.Selected selected = assertInstanceOf(
                TrinityPatternSelector.Selected.class,
                TrinityPatternSelector.create().select(
                        pattern,
                        1,
                        true,
                        4L,
                        ignored -> 4L,
                        ignored -> 0L,
                        16));

        assertEquals(0, selected.variantOrdinal());
        assertEquals(iron, selected.inputsPerCraft().getFirst().what());
    }

    @Test
    void minimizesBorrowingForTheSelectedBatchRatherThanOneCraft() {
        IPatternDetails pattern = pattern(input(1L, stack(iron, 1L), stack(gold, 1L)));

        TrinityPatternSelector.Selected selected = assertInstanceOf(
                TrinityPatternSelector.Selected.class,
                TrinityPatternSelector.create().select(
                        pattern,
                        0,
                        true,
                        10L,
                        availability(Map.of(iron, 1L, gold, 9L)),
                        availability(Map.of(iron, 9L, gold, 1L)),
                        16));

        assertEquals(1, selected.variantOrdinal());
        assertEquals(gold, selected.inputsPerCraft().getFirst().what());
        assertEquals(10L, selected.maximumCrafts());
    }

    @Test
    void reportsDistinctVariantLimitAfterEquivalentBindingsAreCollapsed() {
        IPatternDetails pattern = pattern(
                input(1L, stack(iron, 1L), stack(gold, 1L)),
                input(1L, stack(redstone, 1L), stack(iron, 1L), stack(gold, 1L)));

        TrinityPatternSelector.VariantLimit result = assertInstanceOf(
                TrinityPatternSelector.VariantLimit.class,
                TrinityPatternSelector.create().select(
                        pattern,
                        0,
                        true,
                        1L,
                        ignored -> 1L,
                        ignored -> 0L,
                        4));

        assertEquals(BigInteger.valueOf(5L), result.required());
        assertEquals(4, result.limit());
    }

    @Test
    void reportsExactLongOverflowInsteadOfWrappingRuntimeAmounts() {
        IPatternDetails pattern = pattern(input(Long.MAX_VALUE, stack(iron, 2L)));

        assertInstanceOf(
                TrinityPatternSelector.ArithmeticOverflow.class,
                TrinityPatternSelector.create().select(
                        pattern,
                        0,
                        false,
                        1L,
                        ignored -> Long.MAX_VALUE,
                        ignored -> 0L,
                        1));
    }

    private static ToLongFunction<AEKey> availability(Map<AEKey, Long> amounts) {
        return key -> amounts.getOrDefault(key, 0L);
    }

    private static IPatternDetails pattern(IPatternDetails.IInput... inputs) {
        return new TestPattern(inputs);
    }

    private static IPatternDetails.IInput input(long multiplier, GenericStack... alternatives) {
        return new TestInput(multiplier, alternatives);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private record TestPattern(IInput[] inputs) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.PAPER);
        }

        @Override
        public IInput[] getInputs() {
            return this.inputs.clone();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(stack(AEItemKey.of(Items.DIAMOND), 1L));
        }
    }

    private record TestInput(long multiplier, GenericStack[] alternatives) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.alternatives.clone();
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            for (GenericStack alternative : this.alternatives) {
                if (alternative.what().equals(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
