package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataReassemblerCraftingRecipeGameTest {

    private static final double RADIX_CONTAINMENT_SPHERE_ENERGY = 1_000.0D;

    private DataReassemblerCraftingRecipeGameTest() {}

    @TestHolder("radix_containment_sphere_remainder_follows_data_reassembler_recipe")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void remainderFollowsDataReassemblerRecipe(GameTestHelper helper) {
        CraftingInput inputWithExtraData = dataReassemblerInput(17L, false);
        RecipeHolder<CraftingRecipe> recipeWithExtraData = requireDataReassemblerRecipe(helper, inputWithExtraData);
        assertReturnedBall(helper, recipeWithExtraData.value(), inputWithExtraData, 9L);
        assertAe2PatternRemainder(helper, recipeWithExtraData, inputWithExtraData, 9L);

        CraftingInput inputWithExactData = dataReassemblerInput(8L, false);
        assertReturnedBall(helper, requireDataReassemblerRecipe(helper, inputWithExactData).value(), inputWithExactData, 0L);

        CraftingInput mirroredInput = dataReassemblerInput(16L, true);
        assertReturnedBall(helper, requireDataReassemblerRecipe(helper, mirroredInput).value(), mirroredInput, 8L);

        assertInsufficientDataConsumesBall(helper);
        assertDifferentRecipeDoesNotReturnBall(helper);
        helper.succeed();
    }

    private static RecipeHolder<CraftingRecipe> requireDataReassemblerRecipe(GameTestHelper helper, CraftingInput input) {
        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new GameTestAssertException("Data Reassembler crafting input did not match a recipe"));
        helper.assertTrue(
                recipe.value().getResultItem(helper.getLevel().registryAccess()).is(DEItems.DATA_RIPPER_REASSEMBLER.get()),
                "Crafting input must match the current Data Reassembler recipe");
        helper.assertTrue(
                recipe.value() instanceof DataReassemblerCraftingRecipe,
                "The Data Reassembler must use its data-aware crafting recipe");
        return recipe;
    }

    private static void assertReturnedBall(
                                           GameTestHelper helper,
                                           CraftingRecipe recipe,
                                           CraftingInput input,
                                           long expectedData) {
        int containmentSphereSlot = findRadixContainmentSphereSlot(input);
        long originalData = RadixContainmentSphereItem.getStoredDataAmount(input.getItem(containmentSphereSlot));
        NonNullList<ItemStack> remainders = recipe.getRemainingItems(input);

        ItemStack returned = remainders.get(containmentSphereSlot);
        helper.assertTrue(returned.is(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                "Crafting a Data Reassembler must return the Radix Containment Sphere");
        helper.assertValueEqual(
                RadixContainmentSphereItem.getStoredDataAmount(returned),
                expectedData,
                "The returned Radix Containment Sphere must consume exactly eight data");
        helper.assertValueEqual(
                RadixContainmentSphereItem.getStoredDataAmount(input.getItem(containmentSphereSlot)),
                originalData,
                "Applying the crafting remainder must not mutate the crafting input");
    }

    private static void assertAe2PatternRemainder(
                                                  GameTestHelper helper,
                                                  RecipeHolder<CraftingRecipe> recipe,
                                                  CraftingInput input,
                                                  long expectedData) {
        ItemStack[] ingredients = new ItemStack[input.size()];
        for (int slot = 0; slot < input.size(); slot++) {
            ingredients[slot] = input.getItem(slot).copy();
        }
        ItemStack encodedPattern = PatternDetailsHelper.encodeCraftingPattern(
                recipe,
                ingredients,
                recipe.value().assemble(input, helper.getLevel().registryAccess()),
                true,
                false);
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(encodedPattern, helper.getLevel());
        if (pattern == null) {
            throw new GameTestAssertException("AE2 did not decode the Data Reassembler crafting pattern");
        }
        if (!(pattern instanceof IMolecularAssemblerSupportedPattern assemblerPattern)) {
            throw new GameTestAssertException("AE2 did not expose the pattern to Molecular Assemblers");
        }

        int containmentSphereSlot = findRadixContainmentSphereSlot(input);
        ItemStack assemblerRemainder = assemblerPattern.getRemainingItems(input).get(containmentSphereSlot);
        helper.assertTrue(
                assemblerRemainder.is(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                "Molecular Assembler execution must return the Radix Containment Sphere");
        helper.assertValueEqual(
                RadixContainmentSphereItem.getStoredDataAmount(assemblerRemainder),
                expectedData,
                "Molecular Assembler execution must consume exactly eight data");

        AEItemKey containmentSphereKey = AEItemKey.of(input.getItem(containmentSphereSlot));
        IPatternDetails.IInput containmentSphereInput = Arrays.stream(pattern.getInputs())
                .filter(patternInput -> Arrays.stream(patternInput.getPossibleInputs())
                        .anyMatch(possibleInput -> possibleInput.what().equals(containmentSphereKey)))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(
                        "AE2 pattern did not retain the configured Radix Containment Sphere input"));
        AEKey remainingKey = containmentSphereInput.getRemainingKey(containmentSphereKey);
        if (!(remainingKey instanceof AEItemKey remainingItem)) {
            throw new GameTestAssertException("AE2 pattern did not expose the Radix Containment Sphere remainder");
        }

        helper.assertValueEqual(
                RadixContainmentSphereItem.getStoredDataAmount(remainingItem.toStack()),
                expectedData,
                "AE2 autocrafting must see the Radix Containment Sphere with exactly eight data consumed");
        helper.assertTrue(
                containmentSphereInput.getRemainingKey(
                        AEItemKey.of(RadixContainmentSphereItem.createConfiguredStack(RADIX_CONTAINMENT_SPHERE_ENERGY, 7L))) == null,
                "AE2 autocrafting must not return an insufficient Radix Containment Sphere as a valid remainder");
    }

    private static void assertInsufficientDataConsumesBall(GameTestHelper helper) {
        for (long dataAmount : new long[] { 0L, 7L }) {
            CraftingInput input = dataReassemblerInput(dataAmount, false);
            CraftingRecipe recipe = requireDataReassemblerRecipe(helper, input).value();
            int containmentSphereSlot = findRadixContainmentSphereSlot(input);

            helper.assertTrue(
                    recipe.getRemainingItems(input).get(containmentSphereSlot).isEmpty(),
                    "A Radix Containment Sphere with fewer than eight data must be consumed as a normal ingredient");
            helper.assertValueEqual(
                    RadixContainmentSphereItem.getStoredDataAmount(input.getItem(containmentSphereSlot)),
                    dataAmount,
                    "Consuming an insufficient Radix Containment Sphere must not mutate the crafting input");
        }
    }

    private static void assertDifferentRecipeDoesNotReturnBall(GameTestHelper helper) {
        CraftingInput validInput = dataReassemblerInput(16L, false);
        List<ItemStack> stacks = new ArrayList<>(validInput.size());
        for (int slot = 0; slot < validInput.size(); slot++) {
            stacks.add(validInput.getItem(slot).copy());
        }
        stacks.set(8, new ItemStack(Items.DIRT));
        CraftingInput invalidInput = CraftingInput.of(3, 3, stacks);

        helper.assertTrue(
                helper.getLevel().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, invalidInput, helper.getLevel())
                        .isEmpty(),
                "A different crafting layout must not use the Data Reassembler remainder");
        helper.assertValueEqual(
                RadixContainmentSphereItem.getStoredDataAmount(invalidInput.getItem(findRadixContainmentSphereSlot(invalidInput))),
                16L,
                "A different crafting layout must not consume stored data");
    }

    private static CraftingInput dataReassemblerInput(long dataAmount, boolean mirrored) {
        ItemStack containmentSphere = RadixContainmentSphereItem.createConfiguredStack(RADIX_CONTAINMENT_SPHERE_ENERGY, dataAmount);
        if (mirrored) {
            return CraftingInput.of(3, 3, List.of(
                    containmentSphere,
                    DEItems.DATA_FRAMEWORK.toStack(),
                    DEItems.DATA_PROCESSOR.toStack(),
                    AEBlocks.QUARTZ_GLASS.stack(),
                    AEParts.TERMINAL.stack(),
                    AEBlocks.ENERGY_CELL.stack(),
                    AEItems.ANNIHILATION_CORE.stack(),
                    DEItems.DIGITAL_STORAGE_DEPOT.toStack(),
                    AEItems.FORMATION_CORE.stack()));
        }
        return CraftingInput.of(3, 3, List.of(
                DEItems.DATA_PROCESSOR.toStack(),
                DEItems.DATA_FRAMEWORK.toStack(),
                containmentSphere,
                AEBlocks.ENERGY_CELL.stack(),
                AEParts.TERMINAL.stack(),
                AEBlocks.QUARTZ_GLASS.stack(),
                AEItems.FORMATION_CORE.stack(),
                DEItems.DIGITAL_STORAGE_DEPOT.toStack(),
                AEItems.ANNIHILATION_CORE.stack()));
    }

    private static int findRadixContainmentSphereSlot(CraftingInput input) {
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(DEItems.RADIX_CONTAINMENT_SPHERE.get())) {
                return slot;
            }
        }
        throw new GameTestAssertException("Crafting input does not contain a Radix Containment Sphere");
    }
}
