package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.util.ConfigInventory;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerAe2CraftingGameTest {

    private DataRipperReassemblerAe2CraftingGameTest() {}

    @TestHolder("data_reassembler_processing_pattern_normalizes_only_custom_wrappers")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternNormalizesOnlyCustomWrappers(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(9);
        ConfigInventory outputs = configInventory(4);
        AEItemKey wrappedDataFlow = wrappedKey(DataFlowKey.of(), 1_200L);
        AEItemKey wrappedData = wrappedKey(DataKey.of(), 6L);
        GenericStack dataDust = new GenericStack(itemKey(ModItems.DATA_DUST.toStack()), 32L);
        GenericStack ordinaryItem = new GenericStack(itemKey(new ItemStack(Items.IRON_INGOT)), 16L);
        GenericStack water = new GenericStack(AEFluidKey.of(Fluids.WATER), 100L);
        GenericStack nonTargetWrapper = new GenericStack(wrappedKey(AEFluidKey.of(Fluids.LAVA), 125L), 2L);
        GenericStack directData = new GenericStack(DataKey.of(), 7L);
        GenericStack realisticInnerAmount = new GenericStack(wrappedKey(DataFlowKey.of(), 2_400L), 1L);
        GenericStack realisticOuterAmount = new GenericStack(wrappedKey(DataKey.of(), 1L), 30L);
        GenericStack dataCrystalOutput = new GenericStack(itemKey(ModItems.DATA_CRYSTAL.toStack()), 96L);
        GenericStack directDataFlow = new GenericStack(DataFlowKey.of(), 9L);
        GenericStack lava = new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L);

        inputs.setStack(0, new GenericStack(wrappedDataFlow, 2L));
        inputs.setStack(1, dataDust);
        inputs.setStack(2, ordinaryItem);
        inputs.setStack(3, water);
        inputs.setStack(4, nonTargetWrapper);
        inputs.setStack(5, directData);
        inputs.setStack(6, realisticInnerAmount);
        inputs.setStack(7, realisticOuterAmount);
        outputs.setStack(0, dataCrystalOutput);
        outputs.setStack(1, new GenericStack(wrappedData, 5L));
        outputs.setStack(2, directDataFlow);
        outputs.setStack(3, lava);

        ItemStack encodedPattern = PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs);
        AEProcessingPattern decodedPattern = requireProcessingPattern(encodedPattern, helper.getLevel());
        List<GenericStack> sparseInputs = decodedPattern.getSparseInputs();
        List<GenericStack> sparseOutputs = decodedPattern.getSparseOutputs();

        helper.assertValueEqual(sparseInputs.size(), 9,
                "Encoding must preserve the complete sparse input layout and ordering");
        helper.assertValueEqual(sparseOutputs.size(), 4,
                "Encoding must preserve the complete sparse output layout and ordering");
        assertSparseStack(helper, sparseInputs, 0, DataFlowKey.of(), 2_400L,
                "Wrapped DataFlow input must normalize and multiply its amounts");
        assertSparseStack(helper, sparseInputs, 1, dataDust.what(), dataDust.amount(),
                "Ordinary item input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 2, ordinaryItem.what(), ordinaryItem.amount(),
                "Ordinary non-output item input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 3, water.what(), water.amount(),
                "Direct fluid input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 4, nonTargetWrapper.what(), nonTargetWrapper.amount(),
                "A wrapped non-target key must remain an item key");
        assertSparseStack(helper, sparseInputs, 5, directData.what(), directData.amount(),
                "Direct Data input must remain unchanged");
        assertSparseStack(helper, sparseInputs, 6, DataFlowKey.of(), 2_400L,
                "A realistic outer-one wrapped DataFlow input must retain its inner amount");
        assertSparseStack(helper, sparseInputs, 7, DataKey.of(), 30L,
                "A realistic inner-one wrapped Data input must retain its outer amount");
        helper.assertTrue(sparseInputs.get(8) == null, "Sparse trailing input holes must remain in order");

        assertSparseStack(helper, sparseOutputs, 0, dataCrystalOutput.what(), dataCrystalOutput.amount(),
                "The requested Data Crystal output must remain unchanged");
        assertSparseStack(helper, sparseOutputs, 1, DataKey.of(), 30L,
                "Wrapped Data output must normalize and multiply its amounts");
        assertSparseStack(helper, sparseOutputs, 2, directDataFlow.what(), directDataFlow.amount(),
                "Direct DataFlow output must remain unchanged");
        assertSparseStack(helper, sparseOutputs, 3, lava.what(), lava.amount(),
                "Direct fluid output must remain unchanged");

        assertSparseStack(helper, inputs.toList(), 0, wrappedDataFlow, 2L,
                "Encoding must not mutate the source input inventory");
        assertSparseStack(helper, inputs.toList(), 6, realisticInnerAmount.what(), realisticInnerAmount.amount(),
                "Encoding must preserve the realistic outer-one source wrapper");
        assertSparseStack(helper, inputs.toList(), 7, realisticOuterAmount.what(), realisticOuterAmount.amount(),
                "Encoding must preserve the realistic inner-one source wrapper");
        assertSparseStack(helper, outputs.toList(), 1, wrappedData, 5L,
                "Encoding must not mutate the source output inventory");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_rejects_invalid_wrapped_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternRejectsInvalidWrappedAmounts(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(1);
        GenericStack validInput = new GenericStack(itemKey(ModItems.DATA_DUST.toStack()), 1L);
        GenericStack validOutput = new GenericStack(itemKey(ModItems.DATA_CRYSTAL.toStack()), 1L);
        outputs.setStack(0, validOutput);

        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), Long.MAX_VALUE), 2L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "Input amount overflow must reject the entire pattern");

        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), 0L), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive wrapped input amount must reject the entire pattern");

        setRawStack(inputs, new GenericStack(wrappedKey(DataFlowKey.of(), 1L), -1L), helper.getLevel());
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive outer input amount must reject the entire pattern");

        setRawStack(inputs, new GenericStack(wrappedKey(DataFlowKey.of(), 1L), 0L), helper.getLevel());
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A zero outer input amount must reject the entire pattern");

        inputs.setStack(0, validInput);
        outputs.setStack(0, new GenericStack(wrappedKey(DataKey.of(), Long.MAX_VALUE), 2L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "Output amount overflow must reject the entire pattern");

        outputs.setStack(0, new GenericStack(wrappedKey(DataKey.of(), -1L), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A non-positive wrapped output amount must reject the entire pattern");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_accepts_exact_long_boundary")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternAcceptsExactLongBoundary(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(2);
        inputs.setStack(0, new GenericStack(wrappedKey(DataFlowKey.of(), Long.MAX_VALUE), 1L));
        outputs.setStack(0, new GenericStack(itemKey(ModItems.DATA_CRYSTAL.toStack()), 1L));
        outputs.setStack(1, new GenericStack(wrappedKey(DataKey.of(), 1L), Long.MAX_VALUE));

        ItemStack encodedPattern = PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs);
        AEProcessingPattern decodedPattern = requireProcessingPattern(encodedPattern, helper.getLevel());
        helper.assertValueEqual(decodedPattern.getSparseInputs().size(), 1,
                "The exact long input boundary must preserve its sparse layout");
        helper.assertValueEqual(decodedPattern.getSparseOutputs().size(), 2,
                "The exact long output boundary must preserve its sparse layout");
        assertSparseStack(helper, decodedPattern.getSparseInputs(), 0, DataFlowKey.of(), Long.MAX_VALUE,
                "Long.MAX_VALUE times one must not be treated as input overflow");
        assertSparseStack(helper, decodedPattern.getSparseOutputs(), 1, DataKey.of(), Long.MAX_VALUE,
                "One times Long.MAX_VALUE must not be treated as output overflow");
        helper.succeed();
    }

    @TestHolder("data_reassembler_processing_pattern_preserves_empty_inventory_semantics")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void processingPatternPreservesEmptyInventorySemantics(GameTestHelper helper) {
        ConfigInventory inputs = configInventory(1);
        ConfigInventory outputs = configInventory(2);
        outputs.setStack(0, new GenericStack(itemKey(ModItems.DATA_CRYSTAL.toStack()), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "An all-empty input inventory must not produce a processing pattern");

        inputs.setStack(0, new GenericStack(itemKey(ModItems.DATA_DUST.toStack()), 1L));
        outputs.clear();
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "An all-empty output inventory must not produce a processing pattern");

        outputs.setStack(1, new GenericStack(itemKey(ModItems.DATA_CRYSTAL.toStack()), 1L));
        helper.assertTrue(PatternEncodingSourceHelper.encodeProcessingPattern(inputs, outputs) == null,
                "A missing primary output must return null even when a later output slot is populated");
        helper.succeed();
    }

    private static ConfigInventory configInventory(int size) {
        return ConfigInventory.configStacks(size).allowOverstacking(true).build();
    }

    private static AEItemKey wrappedKey(AEKey key, long innerAmount) {
        return itemKey(GenericStack.wrapInItemStack(key, innerAmount));
    }

    private static AEItemKey itemKey(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalArgumentException("Item stack has no AE item key: " + stack);
        }
        return key;
    }

    private static AEProcessingPattern requireProcessingPattern(ItemStack encodedPattern, ServerLevel level) {
        if (encodedPattern == null || encodedPattern.isEmpty()) {
            throw new GameTestAssertException("Processing pattern encoding returned no pattern");
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(encodedPattern, level);
        if (details instanceof AEProcessingPattern processingPattern) {
            return processingPattern;
        }
        throw new GameTestAssertException("Encoded stack did not decode as an AE2 processing pattern: " + details);
    }

    private static void assertSparseStack(
                                          GameTestHelper helper,
                                          List<GenericStack> stacks,
                                          int slot,
                                          AEKey expectedKey,
                                          long expectedAmount,
                                          String message) {
        GenericStack stack = stacks.get(slot);
        helper.assertTrue(stack != null, message + ": slot was empty");
        helper.assertValueEqual(stack.what(), expectedKey, message + ": key");
        helper.assertValueEqual(stack.amount(), expectedAmount, message + ": amount");
    }

    private static void setRawStack(ConfigInventory inventory, GenericStack stack, ServerLevel level) {
        ListTag encoded = new ListTag();
        encoded.add(GenericStack.writeTag(level.registryAccess(), stack));
        inventory.readFromTag(encoded, level.registryAccess());
    }
}
