package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable projection of the dynamic universal-terminal combinations shared by recipe viewers.
 */
public record UniversalTerminalCombineRecipeView(
                                                 ResourceLocation id,
                                                 ItemStack firstInput,
                                                 ItemStack secondInput,
                                                 ItemStack output) {

    public UniversalTerminalCombineRecipeView {
        firstInput = firstInput.copyWithCount(1);
        secondInput = secondInput.copyWithCount(1);
        output = output.copyWithCount(1);
    }

    @Override
    public ItemStack firstInput() {
        return this.firstInput.copy();
    }

    @Override
    public ItemStack secondInput() {
        return this.secondInput.copy();
    }

    @Override
    public ItemStack output() {
        return this.output.copy();
    }

    /**
     * Enumerates every pair of currently registered non-universal terminal icons.
     */
    public static List<UniversalTerminalCombineRecipeView> fromRegisteredTerminals() {
        List<UniversalTerminalData.TerminalEntry> terminals = UniversalTerminalData.getDefinitions().stream()
                .map(definition -> new UniversalTerminalData.TerminalEntry(definition.name(), definition.createIcon()))
                .filter(entry -> !entry.stack().isEmpty())
                .filter(entry -> !entry.stack().is(DEItems.UNIVERSAL_TERMINAL.get()))
                .toList();
        return build(terminals, new ItemStack(DEItems.UNIVERSAL_TERMINAL.get()));
    }

    static List<UniversalTerminalCombineRecipeView> build(
                                                          List<UniversalTerminalData.TerminalEntry> terminals,
                                                          ItemStack output) {
        List<UniversalTerminalCombineRecipeView> recipes = new ArrayList<>();
        Set<ResourceLocation> recipeIds = new HashSet<>();
        for (int i = 0; i < terminals.size(); i++) {
            for (int j = i + 1; j < terminals.size(); j++) {
                UniversalTerminalData.TerminalEntry first = terminals.get(i);
                UniversalTerminalData.TerminalEntry second = terminals.get(j);
                ResourceLocation id = recipeId(first.name(), second.name());
                if (!recipeIds.add(id)) {
                    String message = "Conflicting universal terminal recipe id for " + first.name() + " and " + second.name() + ": " + id;
                    Data_Energistics.LOGGER.error(message);
                    throw new IllegalStateException(message);
                }
                recipes.add(new UniversalTerminalCombineRecipeView(id, first.stack(), second.stack(), output));
            }
        }
        return List.copyOf(recipes);
    }

    private static ResourceLocation recipeId(String firstTerminalName, String secondTerminalName) {
        return Data_Energistics.id(
                "universal_terminal_combine/" + sanitize(firstTerminalName) + "_" + sanitize(secondTerminalName));
    }

    private static String sanitize(String terminalName) {
        return terminalName.replace(':', '_').replace('/', '_');
    }
}
