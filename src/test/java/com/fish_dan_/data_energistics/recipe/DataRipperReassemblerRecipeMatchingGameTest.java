package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerRecipeMatchingGameTest {

    private DataRipperReassemblerRecipeMatchingGameTest() {}

    @TestHolder("data_ripper_reassembler_ingredient_matching")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ingredientMatchingPrioritizesExactItems(GameTestHelper helper) {
        DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipe(
                List.of(
                        new DataRipperReassemblerIngredient(Ingredient.of(ItemTags.PLANKS), 1),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.OAK_PLANKS), 1)),
                List.of(), List.of(), List.of(), 1, null, null);
        ItemStack oakPlanks = new ItemStack(Items.OAK_PLANKS);
        ItemStack birchPlanks = new ItemStack(Items.BIRCH_PLANKS);
        DataRipperReassemblerRecipeInput input = new DataRipperReassemblerRecipeInput(
                List.of(oakPlanks, birchPlanks), List.of(), null);

        helper.assertTrue(
                recipe.matches(input, helper.getLevel()),
                "A Tag Ingredient must leave the exact item available for a later exact Ingredient");
        helper.assertValueEqual(oakPlanks.getCount(), 1, "Recipe matching must not consume the original exact input");
        helper.assertValueEqual(birchPlanks.getCount(), 1, "Recipe matching must not consume the original Tag input");
        helper.succeed();
    }
}
