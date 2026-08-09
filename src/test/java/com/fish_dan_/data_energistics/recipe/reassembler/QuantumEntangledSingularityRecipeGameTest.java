package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEItems;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class QuantumEntangledSingularityRecipeGameTest {

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

    private static DataRipperReassemblerRecipe recipe(GameTestHelper helper, String path) {
        var holder = helper.getLevel().getRecipeManager()
                .byKey(Data_Energistics.id(path))
                .orElseThrow(() -> new GameTestAssertException("Missing recipe: " + path));
        if (holder.value() instanceof DataRipperReassemblerRecipe recipe) {
            return recipe;
        }
        throw new GameTestAssertException("Recipe is not a data reassembler recipe: " + path);
    }
}
