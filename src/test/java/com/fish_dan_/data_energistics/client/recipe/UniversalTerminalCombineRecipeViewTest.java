package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UniversalTerminalCombineRecipeViewTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void enumeratesEveryStableTerminalPairWithTheExpectedInputsAndOutput() {
        List<UniversalTerminalData.TerminalEntry> terminals = List.of(
                new UniversalTerminalData.TerminalEntry("terminal", new ItemStack(Items.CHEST)),
                new UniversalTerminalData.TerminalEntry("crafting", new ItemStack(Items.CRAFTING_TABLE)),
                new UniversalTerminalData.TerminalEntry("pattern_access", new ItemStack(Items.ENDER_CHEST)),
                new UniversalTerminalData.TerminalEntry("addon:requester_terminal", new ItemStack(Items.COMPARATOR)));
        ItemStack output = new ItemStack(Items.COMPASS);

        List<UniversalTerminalCombineRecipeView> recipes = UniversalTerminalCombineRecipeView.build(terminals, output);
        List<UniversalTerminalCombineRecipeView> rebuilt = UniversalTerminalCombineRecipeView.build(terminals, output);

        assertEquals(terminals.size() * (terminals.size() - 1) / 2, recipes.size());
        assertEquals(recipes.stream().map(UniversalTerminalCombineRecipeView::id).toList(),
                rebuilt.stream().map(UniversalTerminalCombineRecipeView::id).toList());
        assertEquals(recipes.size(), new HashSet<>(recipes.stream().map(UniversalTerminalCombineRecipeView::id).toList()).size());
        assertEquals(Data_Energistics.id("universal_terminal_combine/terminal_crafting"), recipes.getFirst().id());
        assertEquals(Data_Energistics.id("universal_terminal_combine/pattern_access_addon_requester_terminal"),
                recipes.getLast().id());

        int recipeIndex = 0;
        for (int firstIndex = 0; firstIndex < terminals.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < terminals.size(); secondIndex++) {
                UniversalTerminalCombineRecipeView recipe = recipes.get(recipeIndex++);
                assertEquals(terminals.get(firstIndex).stack().getItem(), recipe.firstInput().getItem());
                assertEquals(terminals.get(secondIndex).stack().getItem(), recipe.secondInput().getItem());
                assertEquals(output.getItem(), recipe.output().getItem());
                assertEquals(1, recipe.firstInput().getCount());
                assertEquals(1, recipe.secondInput().getCount());
                assertEquals(1, recipe.output().getCount());
            }
        }
    }
}
