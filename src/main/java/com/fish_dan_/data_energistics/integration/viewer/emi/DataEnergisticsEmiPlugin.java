package com.fish_dan_.data_energistics.integration.viewer.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.crafting.tree.viewer.CraftingPlanIngredientViewers;
import com.fish_dan_.data_energistics.client.screen.crafting.CraftingPlanTreeScreen;
import com.fish_dan_.data_energistics.client.screen.machine.OrderPackageScreen;
import com.fish_dan_.data_energistics.integration.viewer.emi.entrypoint.DataEnergisticsEmiEntrypointLoader;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.CraftingPlanEmiIngredientViewer;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.DataResourceEmiStack;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.DataResourceEmiStackConverter;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.DataResourceEmiStackSerializer;
import com.fish_dan_.data_energistics.integration.viewer.emi.ingredient.PatternEncodingGenericStackEmiProvider;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataChargePressEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataChargerEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.DataRipperReassemblerEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.RadixContainmentSphereRightClickEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.TimeShiftEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.TrinityMultiblockEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.recipe.condenser.CondenserOutputEmiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.EmiMultiblockPatternTransferHandler;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.EmiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.integration.viewer.emi.ui.OrderPackageEmiDragDropHandler;
import com.fish_dan_.data_energistics.integration.viewer.emi.ui.UniversalTerminalEmiExclusionArea;
import com.fish_dan_.data_energistics.integration.viewer.xei.ingredient.DataResourceKey;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.UniversalTerminalCombineRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.recipe.reassembler.RadixContainmentSphereCraftingRemainderHelper;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.registries.BuiltInRegistries;
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
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.recipe.special.EmiAnvilEnchantRecipe;
import dev.emi.emi.registry.EmiRecipes;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@EmiEntrypoint
public final class DataEnergisticsEmiPlugin implements EmiPlugin {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final ResourceLocation AE2_CHARGER_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final ResourceLocation AE2_CONDENSER_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath("ae2", "condenser");
    private static final ResourceLocation EAE_CRYSTAL_ASSEMBLER_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath(
            "extendedae", "assembler");
    private static final ResourceLocation AAE_REACTION_CHAMBER_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath(
            "advanced_ae", "reaction");
    private static final ResourceLocation RECIPE_TYPE_NAME_SOURCE_ID = Data_Energistics.id("emi_recipe_type_names");
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
        PatternProviderRecipeTypeNames.register(RECIPE_TYPE_NAME_SOURCE_ID,
                DataEnergisticsEmiPlugin::resolveRecipeTypeName);
        EmiPatternTransferContextBridge.registerWorkstationSource(
                DataEnergisticsEmiPlugin::resolveRecipeTypeWorkstations);
        registry.addEmiStack(new DataResourceEmiStack(DataResourceKey.DATA, 1L));
        registry.addEmiStack(new DataResourceEmiStack(DataResourceKey.DATA_FLOW, 1L));
        registry.addEmiStack(new DataResourceEmiStack(DataResourceKey.ECHO, 1L));
        registry.addGenericStackProvider(new PatternEncodingGenericStackEmiProvider());
        CraftingPlanIngredientViewers.register("emi", new CraftingPlanEmiIngredientViewer());
        registry.addScreenBoundsProvider(CraftingPlanTreeScreen.class, screen -> {
            var panel = screen.panelBounds();
            return new Bounds(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
        });
        registry.addDragDropHandler(OrderPackageScreen.class, new OrderPackageEmiDragDropHandler());
        registry.addGenericExclusionArea(new UniversalTerminalEmiExclusionArea());
        registry.removeRecipes(PoweredRepairRecipeFilter::shouldHideEmiRepairRecipe);

        registry.addRecipeHandler(
                DEMenus.UNIVERSAL_CRAFTING_TERM.get(),
                new EmiUseCraftingRecipeHandler<>(UniversalCraftingTermMenu.class));
        DataEnergisticsEmiEntrypointLoader.initialize(registry);
        registry.addRecipeHandler(
                DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                new EmiEncodePatternHandler<>(UniversalPatternEncodingTermMenu.class));
        registry.addRecipeHandler(
                PatternEncodingTermMenu.TYPE,
                new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class));
        registry.addRecipeHandler(
                DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                new EmiMultiblockPatternTransferHandler<>(UniversalPatternEncodingTermMenu.class));

        registry.addCategory(TimeShiftEmiRecipe.CATEGORY);
        registry.getRecipeManager().getAllRecipesFor(DERecipes.TIME_SHIFT_TYPE.get()).stream()
                .map(TimeShiftEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addWorkstation(TimeShiftEmiRecipe.CATEGORY, EmiStack.of(DEItems.RADIX_CONTAINMENT_SPHERE.get()));
        registry.addCategory(TrinityMultiblockEmiRecipe.CATEGORY);
        registry.addRecipe(new TrinityMultiblockEmiRecipe());
        registry.getRecipeManager().getAllRecipesFor(DERecipes.RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_TYPE.get()).stream()
                .map(RadixContainmentSphereRightClickEmiRecipe::new)
                .forEach(registry::addRecipe);
        registry.addDeferredRecipes(consumer -> {
            EmiRecipeCategory category = findCategoryById(AE2_CONDENSER_CATEGORY_ID);
            if (category == null) {
                LOGGER.warn("AE2 condenser EMI category was not registered; skipping Data Energistics condenser recipes");
                return;
            }
            registry.getRecipeManager().getAllRecipesFor(DERecipes.CONDENSER_OUTPUT_TYPE.get()).stream()
                    .map(holder -> new CondenserOutputEmiRecipe(category, holder))
                    .forEach(consumer);
        });
        registry.addCategory(DataRipperReassemblerEmiRecipe.CATEGORY);
        registry.addWorkstation(DataRipperReassemblerEmiRecipe.CATEGORY, EmiStack.of(DEBlocks.DATA_RIPPER_REASSEMBLER.get()));
        registry.addWorkstation(DataRipperReassemblerEmiRecipe.CATEGORY,
                EmiStack.of(DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get()));
        registry.addDeferredRecipes(consumer -> registry.getRecipeManager()
                .getAllRecipesFor(DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()).stream()
                .map(DataRipperReassemblerEmiRecipe::new)
                .forEach(consumer));
        registry.addDeferredRecipes(consumer -> registerExternalFactoryWorkstations(registry));
        registerRecipeCategory(
                registry,
                DataChargerEmiRecipe.CATEGORY,
                EmiStack.of(DEBlocks.DATA_CHARGER.get()),
                EmiStack.of(DEBlocks.EXTENDED_DATA_CHARGER.get()),
                DataChargerEmiRecipe::new,
                registry.getRecipeManager().getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get()));
        registry.addCategory(DataChargePressEmiRecipe.CATEGORY);
        registry.addWorkstation(
                DataChargePressEmiRecipe.CATEGORY,
                EmiStack.of(DEBlocks.DATA_INTEGRATED_CHARGER.get()));
        DataChargePressRecipeView.fromRecipeManager(registry.getRecipeManager()).stream()
                .map(DataChargePressEmiRecipe::new)
                .forEach(registry::addRecipe);

        buildUniversalTerminalRecipes().forEach(registry::addRecipe);
        registry.addRecipe(new EmiInfoRecipe(
                List.of(
                        EmiStack.of(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                        EmiStack.of(DEBlocks.DATA_RIPPER_REASSEMBLER.get())),
                List.of(
                        Component.translatable("jei.data_energistics.radix_containment_sphere.line1"),
                        Component.translatable("jei.data_energistics.radix_containment_sphere.line2"),
                        Component.translatable("jei.data_energistics.radix_containment_sphere.line3"),
                        Component.translatable(
                                "jei.data_energistics.data_reassembler.crafting_requirement",
                                RadixContainmentSphereCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST)),
                null));
        registry.addRecipe(new EmiAnvilEnchantRecipe(
                DEItems.MATTER_CONVERGING_CROSSBOW.get(),
                EmiPort.getEnchantmentRegistry().get(Enchantments.POWER.location()),
                1,
                syntheticEmiRecipeId(Data_Energistics.id("emi/anvil/matter_converging_crossbow_power"))));
        registry.addDeferredRecipes(consumer -> registerAe2ChargerWorkstations(registry));
    }

    private static List<Component> resolveRecipeTypeName(ResourceLocation recipeTypeId) {
        return EmiApi.getRecipeManager().getCategories().stream()
                .filter(category -> category.getId().equals(recipeTypeId))
                .map(category -> List.of(category.getName()))
                .findFirst()
                .orElseGet(List::of);
    }

    private static List<ResourceLocation> resolveRecipeTypeWorkstations(ResourceLocation recipeTypeId) {
        var recipeManager = EmiApi.getRecipeManager();
        return recipeManager.getCategories().stream()
                .filter(category -> category.getId().equals(recipeTypeId))
                .flatMap(category -> recipeManager.getWorkstations(category).stream())
                .flatMap(workstation -> workstation.getEmiStacks().stream())
                .map(EmiStack::getItemStack)
                .filter(stack -> !stack.isEmpty())
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .toList();
    }

    private static List<EmiCraftingRecipe> buildUniversalTerminalRecipes() {
        return UniversalTerminalCombineRecipeView.fromRegisteredTerminals().stream()
                .map(recipe -> new EmiCraftingRecipe(
                        List.of(EmiStack.of(recipe.firstInput()), EmiStack.of(recipe.secondInput())),
                        EmiStack.of(recipe.output()),
                        syntheticEmiRecipeId(recipe.id())))
                .toList();
    }

    private static ResourceLocation syntheticEmiRecipeId(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "/" + id.getPath());
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

        registry.addWorkstation(ae2ChargerCategory, EmiStack.of(DEBlocks.DATA_CHARGER.get()));
        registry.addWorkstation(ae2ChargerCategory, EmiStack.of(DEBlocks.EXTENDED_DATA_CHARGER.get()));
    }

    private static void registerExternalFactoryWorkstations(EmiRegistry registry) {
        registerExternalFactoryWorkstation(registry, EAE_CRYSTAL_ASSEMBLER_CATEGORY_ID);
        registerExternalFactoryWorkstation(registry, AAE_REACTION_CHAMBER_CATEGORY_ID);
    }

    private static void registerExternalFactoryWorkstation(EmiRegistry registry, ResourceLocation categoryId) {
        EmiRecipeCategory category = findCategoryById(categoryId);
        if (category != null) {
            registry.addWorkstation(category, EmiStack.of(DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get()));
        }
    }

    @Nullable
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
