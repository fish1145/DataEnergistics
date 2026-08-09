package com.fish_dan_.data_energistics.ae2.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import java.util.Arrays;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PatternProviderBatchingGameTest {

    private PatternProviderBatchingGameTest() {}

    @TestHolder("pattern_provider_batching_scales_real_sparse_processing_pattern")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void scalesRealSparseProcessingPattern(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        IPatternDetails decoded = PatternDetailsHelper.decodePattern(
                PatternDetailsHelper.encodeProcessingPattern(
                        Arrays.asList(
                                new GenericStack(iron, 1L),
                                null,
                                new GenericStack(iron, 1L),
                                new GenericStack(gold, 3L)),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L))),
                helper.getLevel());
        if (!(decoded instanceof AEProcessingPattern pattern)) {
            throw new GameTestAssertException("Encoded test pattern did not decode as an AE2 processing pattern");
        }
        KeyCounter[] prototype = { counter(iron, 2L), counter(gold, 3L) };
        KeyCounter expanded = new KeyCounter();
        for (GenericStack stack : PatternProviderBatching.expandPatternInputs(pattern, prototype, 4L)) {
            expanded.add(stack.what(), stack.amount());
        }

        helper.assertValueEqual(expanded.get(iron), 8L,
                "Repeated sparse item inputs must scale with the admitted batch");
        helper.assertValueEqual(expanded.get(gold), 12L,
                "Distinct sparse item inputs must scale with the admitted batch");
        helper.assertValueEqual(prototype[0].get(iron), 2L,
                "Batch expansion must not mutate the CPU input prototype");
        helper.assertValueEqual(prototype[1].get(gold), 3L,
                "Batch expansion must retain every unit input amount");
        helper.succeed();
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }
}
