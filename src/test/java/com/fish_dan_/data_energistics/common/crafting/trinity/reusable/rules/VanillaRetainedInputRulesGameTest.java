package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class VanillaRetainedInputRulesGameTest {

    private VanillaRetainedInputRulesGameTest() {}

    @TestHolder("vanilla_reusable_book_rule_is_registered_and_preserves_the_actual_original")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registeredBookRulePreservesActualOriginal(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.WRITTEN_BOOK);
        original.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Original"), "Trinity", 0,
                List.of(Filterable.passThrough(Component.literal("Retained page"))), true));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("retained book"));
        verify(helper, ResourceLocation.withDefaultNamespace("book_cloning"), original, new ItemStack(Items.WRITABLE_BOOK));
        helper.succeed();
    }

    @TestHolder("vanilla_reusable_banner_rule_retains_patterned_input_without_reusing_blank_material")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registeredBannerRuleDoesNotReuseBlankMaterial(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.WHITE_BANNER);
        var pattern = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.CROSS);
        original.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(pattern, DyeColor.RED).build());
        verify(helper, ResourceLocation.withDefaultNamespace("banner_duplicate"), original, new ItemStack(Items.WHITE_BANNER));
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, ResourceLocation recipeId, ItemStack original, ItemStack consumable) {
        var level = helper.getLevel();
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(recipeId).orElseThrow().value();
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = original;
        grid[1] = consumable;
        CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(grid));
        helper.assertTrue(recipe.matches(input, level), "Real vanilla recipe accepts the fixture");
        ItemStack encoded = AEItems.CRAFTING_PATTERN.stack();
        AECraftingPattern.encode(encoded, new RecipeHolder<>(recipeId, recipe), grid, recipe.assemble(input, level.registryAccess()), false, false);
        AECraftingPattern pattern = new AECraftingPattern(AEItemKey.of(encoded), level);
        var rules = DataEnergisticsEntrypointLoader.snapshot().reusableInputs();
        helper.assertTrue(rules.mayMatch(pattern, Optional.of(recipeId)), "Production plugin discovers the native recipe");
        List<GenericStack> exact = new ObjectArrayList<>();
        for (var slot : pattern.getInputs()) {
            GenericStack template = slot.getPossibleInputs()[0];
            exact.add(new GenericStack(template.what(), Math.multiplyExact(template.amount(), slot.getMultiplier())));
        }
        int retained = 0;
        for (int slot = 0; slot < exact.size(); slot++) {
            var context = ReusableInputContext.builder().pattern(pattern).actualInput(exact.get(slot)).exactInputs(exact).inputSlot(slot)
                    .ownership(Ownership.CPU_SUPPLIED).actionSource(IActionSource.empty()).level(level).recipeId(Optional.of(recipeId))
                    .machineMode(Optional.empty()).target(CountedCraftingTarget.route("vanilla-rule-test")).build();
            var rule = rules.resolve(context);
            if (exact.get(slot).what().equals(AEItemKey.of(original))) {
                helper.assertTrue(rule.isPresent(), "Original has a registered unchanged rule");
                helper.assertValueEqual(rule.orElseThrow().advance(AEItemKey.of(original), 1_000).successor(), AEItemKey.of(original),
                        "Frozen rule preserves every original component across repeated uses");
                retained++;
            } else {
                helper.assertTrue(rule.isEmpty(), "Ordinary material cannot become a reusable input");
            }
        }
        helper.assertValueEqual(retained, 1, "Exactly one original slot is retained");
        var remaining = recipe.getRemainingItems(input);
        helper.assertTrue(ItemStack.isSameItemSameComponents(remaining.getFirst(), original) && remaining.getFirst().getCount() == 1,
                "Actual vanilla execution agrees with the declared exact successor");
        helper.assertTrue(remaining.get(1).isEmpty(), "Actual vanilla execution consumes the blank material");
    }
}
