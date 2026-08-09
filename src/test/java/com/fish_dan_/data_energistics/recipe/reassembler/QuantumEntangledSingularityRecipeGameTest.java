package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class QuantumEntangledSingularityRecipeGameTest {

    private static final BlockPos SINGLE_REASSEMBLER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos SINGLE_ENERGY_CELL_POS = new BlockPos(2, 1, 1);
    private static final BlockPos BATCH_REASSEMBLER_POS = new BlockPos(1, 1, 3);
    private static final BlockPos BATCH_ENERGY_CELL_POS = new BlockPos(2, 1, 3);
    private static final int COMPARISON_TICKS = 1_024;

    private QuantumEntangledSingularityRecipeGameTest() {}

    @TestHolder("quantum_entangled_singularity_recipe_component_contract")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void quantumEntangledSingularityRecipeComponentContract(GameTestHelper helper) {
        DataRipperReassemblerRecipe unassigned = recipe(helper, "data_energistics/ae2/quantum_entangled_singularity");
        DataRipperReassemblerRecipe assigned = recipe(helper, "data_energistics/ae2/quantum_entangled_singularity_frequency");

        ItemStack unassignedOutput = unassigned.getCraftedItemOutputs().getFirst();
        helper.assertValueEqual(unassignedOutput.getCount(), 4, "The base recipe must output four singularities");
        helper.assertTrue(
                !unassignedOutput.has(AEComponents.ENTANGLED_SINGULARITY_ID),
                "The base recipe output must not have a frequency component");
        helper.assertTrue(!unassigned.assignsQuantumFrequency(), "The base recipe must not assign a frequency");

        ItemStack unassignedInput = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack(2);
        DataRipperReassemblerRecipeInput input = new DataRipperReassemblerRecipeInput(
                List.of(unassignedInput),
                List.of(),
                new GenericStack(DataFlowKey.of(), 100L));
        helper.assertTrue(assigned.matches(input, helper.getLevel()), "The frequency recipe must accept unassigned singularities");
        helper.assertValueEqual(assigned.getKeyInput().amount(), 100L, "The frequency recipe must consume 100 Data Flow");
        helper.assertTrue(assigned.assignsQuantumFrequency(), "The frequency recipe must explicitly assign a frequency");

        ItemStack assignedInput = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack(2);
        QuantumBridgeBlockEntity.assignFrequency(assignedInput);
        helper.assertTrue(
                !assigned.matches(new DataRipperReassemblerRecipeInput(
                        List.of(assignedInput),
                        List.of(),
                        new GenericStack(DataFlowKey.of(), 100L)), helper.getLevel()),
                "The frequency recipe must reject already assigned singularities");

        ItemStack firstOutput = assigned.getCraftedItemOutputs().getFirst();
        Long firstFrequency = firstOutput.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        helper.assertTrue(firstFrequency != null, "The frequency recipe output must have a frequency");
        helper.assertValueEqual(firstOutput.getCount(), 2, "The frequency recipe must output two singularities");

        ItemStack secondOutput = assigned.getCraftedItemOutputs().getFirst();
        Long secondFrequency = secondOutput.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        helper.assertTrue(secondFrequency != null, "A second frequency recipe output must have a frequency");
        helper.assertTrue(!firstFrequency.equals(secondFrequency), "Separate frequency recipe runs must not share a frequency");
        helper.succeed();
    }

    @TestHolder("data_reassembler_batch_matches_1024_quantum_ticks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void dataReassemblerBatchMatches1024QuantumTicks(GameTestHelper helper) {
        DataRipperReassemblerBlockEntity single = placeReassembler(helper, SINGLE_REASSEMBLER_POS);
        DataRipperReassemblerBlockEntity batch = placeReassembler(helper, BATCH_REASSEMBLER_POS);
        helper.setBlock(SINGLE_ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(BATCH_ENERGY_CELL_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        single.isOnline() && batch.isOnline(),
                        "Both Data Reassemblers must join their powered AE networks"))
                .thenExecute(() -> {
                    prepareQuantumWork(helper, single);
                    prepareQuantumWork(helper, batch);
                    helper.assertValueEqual(
                            snapshot(single),
                            snapshot(batch),
                            "The single-tick and batch machines must start from identical processing state");

                    for (int tick = 0; tick < COMPARISON_TICKS; tick++) {
                        single.serverTick();
                    }
                    ReassemblerBatchSnapshot expected = snapshot(single);
                    batch.advanceAdditionalTicks(COMPARISON_TICKS);

                    helper.assertValueEqual(
                            snapshot(batch),
                            expected,
                            "One 1024-tick batch must match 1024 real serverTick calls");
                    helper.assertValueEqual(expected.completedRecipes(), 3,
                            "The full output inventory must stop both paths after three completed recipes");
                    helper.assertValueEqual(expected.validFrequencyCount(), expected.occupiedOutputSlots(),
                            "Every completed quantum output must receive a valid frequency");
                    helper.assertValueEqual(expected.uniqueFrequencyCount(), expected.occupiedOutputSlots(),
                            "Every completed quantum output must receive a unique frequency");
                })
                .thenSucceed();
    }

    private static DataRipperReassemblerRecipe recipe(GameTestHelper helper, String path) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(Data_Energistics.id(path))
                .orElseThrow(() -> new GameTestAssertException("Missing recipe: " + path));
        if (holder.value() instanceof DataRipperReassemblerRecipe recipe) {
            return recipe;
        }
        throw new GameTestAssertException("Recipe is not a data reassembler recipe: " + path);
    }

    private static DataRipperReassemblerBlockEntity placeReassembler(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, DEBlocks.DATA_RIPPER_REASSEMBLER.get());
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
    }

    private static void prepareQuantumWork(GameTestHelper helper, DataRipperReassemblerBlockEntity reassembler) {
        reassembler.getStorageInventory().setItemDirect(
                DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT,
                AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack(12));
        long inserted = reassembler.getExternalKeyInventory().insert(
                DataRipperReassemblerBlockEntity.KEY_INPUT_SLOT,
                DataFlowKey.of(),
                600L,
                Actionable.MODULATE);
        helper.assertValueEqual(inserted, 600L, "The quantum batch test must fully load its Data Flow input");
    }

    private static ReassemblerBatchSnapshot snapshot(DataRipperReassemblerBlockEntity reassembler) {
        List<Integer> outputCounts = new ArrayList<>(DataRipperReassemblerBlockEntity.ITEM_OUTPUT_SLOT_COUNT);
        Set<Long> uniqueFrequencies = new HashSet<>();
        int occupiedOutputSlots = 0;
        int validFrequencyCount = 0;
        for (int slot = DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT; slot < DataRipperReassemblerBlockEntity.ITEM_OUTPUT_START_SLOT +
                DataRipperReassemblerBlockEntity.ITEM_OUTPUT_SLOT_COUNT; slot++) {
            ItemStack output = reassembler.getStorageInventory().getStackInSlot(slot);
            outputCounts.add(output.getCount());
            if (!output.isEmpty()) {
                occupiedOutputSlots++;
            }
            Long frequency = output.get(AEComponents.ENTANGLED_SINGULARITY_ID);
            if (frequency != null) {
                validFrequencyCount++;
                uniqueFrequencies.add(frequency);
            }
        }
        GenericStack keyInput = reassembler.getKeyInputStack();
        int inputCount = reassembler.getStorageInventory()
                .getStackInSlot(DataRipperReassemblerBlockEntity.ITEM_INPUT_START_SLOT)
                .getCount();
        return new ReassemblerBatchSnapshot(
                reassembler.getProgress(),
                reassembler.getMaxProgress(),
                inputCount,
                keyInput == null ? 0L : keyInput.amount(),
                List.copyOf(outputCounts),
                (12 - inputCount) / 2,
                occupiedOutputSlots,
                validFrequencyCount,
                uniqueFrequencies.size());
    }

    private record ReassemblerBatchSnapshot(int progress, int maxProgress, int inputCount, long keyInputAmount,
                                            List<Integer> outputCounts, int completedRecipes, int occupiedOutputSlots,
                                            int validFrequencyCount, int uniqueFrequencyCount) {}
}
