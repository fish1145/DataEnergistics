package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.client.recipe.UniversalTerminalCombineRecipeView;
import com.fish_dan_.data_energistics.client.screen.OrderPackageScreen;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;

import appeng.api.integrations.emi.EmiStackConverters;
import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.special.EmiAnvilEnchantRecipe;
import dev.emi.emi.registry.EmiRecipes;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@EmiEntrypoint
public final class DataEnergisticsEmiPlugin implements EmiPlugin {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ResourceLocation AE2_CHARGER_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final ConverterRegistration CONVERTER_REGISTRATION = new ConverterRegistration();

    @Override
    public void initialize(EmiInitRegistry registry) {
        registry.addIngredientSerializer(DataResourceEmiStack.class, DataResourceEmiStackSerializer.INSTANCE);
        try {
            CONVERTER_REGISTRATION.registerOnce(
                    () -> EmiStackConverters.register(DataResourceEmiStackConverter.INSTANCE));
        } catch (IllegalStateException exception) {
            LOGGER.error(exception.getMessage());
            throw exception;
        }
    }

    @Override
    public void register(EmiRegistry registry) {
        try {
            CONVERTER_REGISTRATION.requireRegistered();
        } catch (IllegalStateException exception) {
            LOGGER.error(exception.getMessage());
            throw exception;
        }
        registry.addEmiStack(new DataResourceEmiStack(DataResourceEmiKey.DATA, 1L));
        registry.addEmiStack(new DataResourceEmiStack(DataResourceEmiKey.DATA_FLOW, 1L));
        registry.addGenericStackProvider(new PatternEncodingGenericStackEmiProvider());
        registry.addDragDropHandler(OrderPackageScreen.class, new OrderPackageEmiDragDropHandler());
        registry.addGenericExclusionArea(new UniversalTerminalEmiExclusionArea());
        registry.removeRecipes(PoweredRepairRecipeFilter::shouldHideEmiRepairRecipe);

        registry.addRecipeHandler(
                ModMenus.UNIVERSAL_CRAFTING_TERM.get(),
                new EmiUseCraftingRecipeHandler<>(UniversalCraftingTermMenu.class));
        registry.addRecipeHandler(
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                new EmiEncodePatternHandler<>(UniversalPatternEncodingTermMenu.class));
        registry.addRecipeHandler(
                PatternEncodingTermMenu.TYPE,
                new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class));
        registry.addRecipeHandler(
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                new EmiMultiblockPatternTransferHandler<>(UniversalPatternEncodingTermMenu.class));

        registry.addCategory(TimeShiftEmiRecipe.CATEGORY);
        registry.getRecipeManager().getAllRecipesFor(ModRecipes.TIME_SHIFT_TYPE.get()).stream()
                .map(TimeShiftEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addWorkstation(TimeShiftEmiRecipe.CATEGORY, EmiStack.of(ModItems.DATA_CAPTURE_BALL.get()));
        registry.addCategory(TrinityMultiblockEmiRecipe.CATEGORY);
        registry.addRecipe(new TrinityMultiblockEmiRecipe());
        registry.addWorkstation(
                TrinityMultiblockEmiRecipe.CATEGORY,
                EmiStack.of(ModBlocks.TRINITY_DATA_CORE.get()));
        registry.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE.get()).stream()
                .map(DataCaptureBallRightClickEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addCategory(DataRipperReassemblerEmiRecipe.CATEGORY);
        registry.addWorkstation(DataRipperReassemblerEmiRecipe.CATEGORY, EmiStack.of(ModBlocks.DATA_RIPPER_REASSEMBLER.get()));
        registry.addDeferredRecipes(consumer -> registry.getRecipeManager()
                .getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()).stream()
                .map(DataRipperReassemblerEmiRecipe::new)
                .forEach(consumer));
        registerRecipeCategory(
                registry,
                DataChargerEmiRecipe.CATEGORY,
                EmiStack.of(ModBlocks.DATA_CHARGER.get()),
                EmiStack.of(ModBlocks.EXTENDED_DATA_CHARGER.get()),
                DataChargerEmiRecipe::new,
                registry.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CHARGER_TYPE.get()));

        buildUniversalTerminalRecipes().forEach(registry::addRecipe);
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(ModItems.DATA_CAPTURE_BALL.get()),
                        EmiStack.of(ModBlocks.DATA_RIPPER_REASSEMBLER.get())),
                List.of(
                        Component.translatable("jei.data_energistics.data_capture_ball.line1"),
                        Component.translatable("jei.data_energistics.data_capture_ball.line2"),
                        Component.translatable("jei.data_energistics.data_capture_ball.line3"),
                        Component.translatable(
                                "jei.data_energistics.data_reassembler.crafting_requirement",
                                DataCaptureBallCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST)),
                null));
        registry.addRecipe(new DataCaptureBallEmiCondenserRecipe());
        registry.addRecipe(new EmiAnvilEnchantRecipe(
                ModItems.MATTER_CONVERGING_CROSSBOW.get(),
                EmiPort.getEnchantmentRegistry().get(Enchantments.POWER.location()),
                1,
                Data_Energistics.id("emi/anvil/matter_converging_crossbow_power")));
        registry.addDeferredRecipes(consumer -> registerAe2ChargerWorkstations(registry));
    }

    private static List<EmiCraftingRecipe> buildUniversalTerminalRecipes() {
        return UniversalTerminalCombineRecipeView.fromRegisteredTerminals().stream()
                .map(recipe -> new EmiCraftingRecipe(
                        List.of(EmiStack.of(recipe.firstInput()), EmiStack.of(recipe.secondInput())),
                        EmiStack.of(recipe.output()),
                        recipe.id()))
                .toList();
    }

    private static <R extends Recipe<?>> void registerRecipeCategory(
                                                                     EmiRegistry registry,
                                                                     EmiRecipeCategory category,
                                                                     EmiStack workstation,
                                                                     EmiStack extendedWorkstation,
                                                                     Function<RecipeHolder<R>, ? extends EmiRecipe> mapper,
                                                                     List<RecipeHolder<R>> recipes) {
        registry.addCategory(category);
        registry.addWorkstation(category, workstation);
        registry.addWorkstation(category, extendedWorkstation);
        recipes.stream()
                .map(mapper)
                .forEach(registry::addRecipe);
    }

    private static void registerAe2ChargerWorkstations(EmiRegistry registry) {
        EmiRecipeCategory ae2ChargerCategory = findCategoryById(AE2_CHARGER_CATEGORY_ID);
        if (ae2ChargerCategory == null) {
            LOGGER.warn("AE2 charger EMI category was not registered; skipping Data Energistics charger workstations");
            return;
        }

        registry.addWorkstation(ae2ChargerCategory, EmiStack.of(ModBlocks.DATA_CHARGER.get()));
        registry.addWorkstation(ae2ChargerCategory, EmiStack.of(ModBlocks.EXTENDED_DATA_CHARGER.get()));
    }

    private static EmiRecipeCategory findCategoryById(ResourceLocation categoryId) {
        return EmiRecipes.categories.stream()
                .filter(category -> category.getId().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    static final class ConverterRegistration {

        private boolean registered;

        synchronized void registerOnce(BooleanSupplier registrar) {
            if (registered) {
                return;
            }
            if (!registrar.getAsBoolean()) {
                throw new IllegalStateException(
                        "An AE2 EMI stack converter is already registered for Data Energistics resources");
            }
            registered = true;
        }

        synchronized void requireRegistered() {
            if (!registered) {
                throw new IllegalStateException(
                        "The Data resource EMI stack converter must be initialized before Data Energistics EMI registration");
            }
        }
    }
}
