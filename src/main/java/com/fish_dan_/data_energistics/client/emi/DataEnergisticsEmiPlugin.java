package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.special.EmiAnvilEnchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@EmiEntrypoint
public final class DataEnergisticsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea(new UniversalTerminalEmiExclusionArea());
        registry.removeRecipes(PoweredRepairRecipeFilter::shouldHideEmiRepairRecipe);

        registry.addRecipeHandler(
                ModMenus.UNIVERSAL_CRAFTING_TERM.get(),
                new EmiUseCraftingRecipeHandler<>(UniversalCraftingTermMenu.class));
        registry.addRecipeHandler(
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                new EmiEncodePatternHandler<>(UniversalPatternEncodingTermMenu.class));

        registry.addCategory(TimeShiftEmiRecipe.CATEGORY);
        registry.getRecipeManager().getAllRecipesFor(ModRecipes.TIME_SHIFT_TYPE.get()).stream()
                .map(TimeShiftEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addWorkstation(TimeShiftEmiRecipe.CATEGORY, EmiStack.of(ModItems.DATA_CAPTURE_BALL.get()));
        registry.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE.get()).stream()
                .map(DataCaptureBallRightClickEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addCategory(DataRipperReassemblerEmiRecipe.CATEGORY);
        registry.addWorkstation(DataRipperReassemblerEmiRecipe.CATEGORY, EmiStack.of(ModBlocks.DATA_RIPPER_REASSEMBLER.get()));
        registry.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()).stream()
                .map(DataRipperReassemblerEmiRecipe::new)
                .forEach(registry::addRecipe);

        buildUniversalTerminalRecipes().forEach(registry::addRecipe);
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(ModItems.DATA_CAPTURE_BALL.get())),
                List.of(
                        Component.translatable("jei.data_energistics.data_capture_ball.line1"),
                        Component.translatable("jei.data_energistics.data_capture_ball.line2"),
                        Component.translatable("jei.data_energistics.data_capture_ball.line3")),
                null));
        registry.addRecipe(new DataCaptureBallEmiCondenserRecipe());
        registry.addRecipe(new EmiAnvilEnchantRecipe(
                ModItems.MATTER_CONVERGING_CROSSBOW.get(),
                EmiPort.getEnchantmentRegistry().get(Enchantments.POWER.location()),
                1,
                Data_Energistics.id("emi/anvil/matter_converging_crossbow_power")));
    }

    private static List<EmiCraftingRecipe> buildUniversalTerminalRecipes() {
        List<EmiCraftingRecipe> recipes = new ArrayList<>();
        List<UniversalTerminalData.TerminalEntry> terminals = UniversalTerminalData.getDefinitions().stream()
                .filter(definition -> !new ItemStack(ModItems.UNIVERSAL_TERMINAL.get()).is(definition.createIcon().getItem()))
                .map(definition -> {
                    ItemStack stack = definition.createIcon();
                    return stack.isEmpty() ? null : new UniversalTerminalData.TerminalEntry(definition.name(), stack);
                })
                .filter(Objects::nonNull)
                .toList();

        for (int i = 0; i < terminals.size(); i++) {
            for (int j = i + 1; j < terminals.size(); j++) {
                UniversalTerminalData.TerminalEntry first = terminals.get(i);
                UniversalTerminalData.TerminalEntry second = terminals.get(j);
                recipes.add(new EmiCraftingRecipe(
                        List.of(EmiStack.of(first.stack().copy()), EmiStack.of(second.stack().copy())),
                        EmiStack.of(ModItems.UNIVERSAL_TERMINAL.get()),
                        Data_Energistics.id("universal_terminal_combine/" + sanitize(first.name()) + "_" + sanitize(second.name()))));
            }
        }

        return recipes;
    }

    private static String sanitize(String terminalName) {
        return terminalName.replace(':', '_').replace('/', '_');
    }
}
