package com.fish_dan_.data_energistics.integration.viewer.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.screen.machine.DataRipperReassemblerScreen;
import com.fish_dan_.data_energistics.client.screen.machine.OrderPackageScreen;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.viewer.jei.entrypoint.DataEnergisticsJeiEntrypointLoader;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.Ae2JeiIngredientRegistration;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredient;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredientHelper;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.DataResourceJeiIngredientRenderer;
import com.fish_dan_.data_energistics.integration.viewer.jei.ingredient.PatternEncodingGenericStackJeiHandler;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.DataChargePressRecipeCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.DataChargerRecipeCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.DataRipperReassemblerRecipeCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.TimeShiftRecipeCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.TrinityMultiblockJeiCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.WorldInteractionJeiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.jei.recipe.condenser.CondenserOutputRecipeCategory;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.Ae2JeiTransferRegistration;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.JeiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.integration.viewer.jei.transfer.MultiblockPatternJeiTransferHandler;
import com.fish_dan_.data_energistics.integration.viewer.jei.ui.OrderPackageJeiGhostIngredientHandler;
import com.fish_dan_.data_energistics.integration.viewer.xei.XeiLayoutRefreshQueue;
import com.fish_dan_.data_energistics.integration.viewer.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.integration.viewer.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataChargePressRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataRipperReassemblerRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.UniversalTerminalCombineRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser.CondenserOutputRecipeView;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.recipe.reassembler.RadixContainmentSphereCraftingRemainderHelper;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import appeng.core.definitions.AEBlocks;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.recipes.handlers.ChargerRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@JeiPlugin
public final class DataEnergisticsJeiPlugin implements IModPlugin {

    private static final String AE2_JEI_INTEGRATION_MOD_ID = "ae2jeiintegration";
    private static final ResourceLocation AE2_CHARGER_RECIPE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final RecipeType<RecipeHolder<ChargerRecipe>> AE2_CHARGER_RECIPE_TYPE = RecipeType.createRecipeHolderType(AE2_CHARGER_RECIPE_ID);
    private static final ResourceLocation RECIPE_TYPE_NAME_SOURCE_ID = Data_Energistics.id("jei_recipe_type_names");
    private static final Object MULTIBLOCK_REFRESH_KEY = new Object();
    @Nullable
    private IJeiRuntime jeiRuntime;
    @Nullable
    private TrinityMultiblockJeiCategory trinityMultiblockCategory;
    private boolean multiblockRefreshInProgress;

    @Override
    public ResourceLocation getPluginUid() {
        return Data_Energistics.id("jei_plugin");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        registration.register(
                DataResourceJeiIngredient.TYPE,
                DataResourceJeiIngredient.ALL_INGREDIENTS,
                DataResourceJeiIngredientHelper.INSTANCE,
                DataResourceJeiIngredientRenderer.INSTANCE,
                DataResourceJeiIngredient.CODEC);
        if (Data_Energistics.isModLoaded(AE2_JEI_INTEGRATION_MOD_ID)) {
            Ae2JeiIngredientRegistration.registerOnce();
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        TrinityMultiblockJeiCategory multiblockCategory = installTrinityMultiblockCategory(
                new TrinityMultiblockJeiCategory(
                        registration.getJeiHelpers(),
                        this::requestMultiblockRefresh));
        registration.addRecipeCategories(
                new TimeShiftRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new CondenserOutputRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataChargerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataChargePressRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataRipperReassemblerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                multiblockCategory);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        registration.addRecipeCatalyst(AEBlocks.CONDENSER, CondenserOutputRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(DEItems.RADIX_CONTAINMENT_SPHERE.get(), TimeShiftRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.DATA_ASYNCHRONOUS_PROCESSING_FACTORY.get(),
                DataRipperReassemblerRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.DATA_INTEGRATED_CHARGER.get(), DataChargePressRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.DATA_CHARGER.get(), AE2_CHARGER_RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.EXTENDED_DATA_CHARGER.get(), AE2_CHARGER_RECIPE_TYPE);
        registration.addRecipeCatalyst(DEBlocks.TRINITY_DATA_CORE.get(), TrinityMultiblockJeiCategory.RECIPE_TYPE);
        registerDataChargerCatalysts(registration, DataChargerRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        registration.addGuiContainerHandler(
                DataRipperReassemblerScreen.class,
                new PatternEncodingGenericStackJeiHandler<>());
        registration.addGuiContainerHandler(
                OrderPackageScreen.class,
                new PatternEncodingGenericStackJeiHandler<>());
        registration.addGhostIngredientHandler(
                OrderPackageScreen.class,
                new OrderPackageJeiGhostIngredientHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        var transferHelper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(
                new MultiblockPatternJeiTransferHandler<>(
                        PatternEncodingTermMenu.class,
                        PatternEncodingTermMenu.TYPE,
                        TrinityMultiblockJeiCategory.RECIPE_TYPE,
                        transferHelper),
                TrinityMultiblockJeiCategory.RECIPE_TYPE);
        registration.addRecipeTransferHandler(
                new MultiblockPatternJeiTransferHandler<>(
                        UniversalPatternEncodingTermMenu.class,
                        DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                        TrinityMultiblockJeiCategory.RECIPE_TYPE,
                        transferHelper),
                TrinityMultiblockJeiCategory.RECIPE_TYPE);

        DataEnergisticsJeiEntrypointLoader.initialize(registration);

        if (Data_Energistics.isModLoaded(AE2_JEI_INTEGRATION_MOD_ID)) {
            Ae2JeiTransferRegistration.register(registration);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!shouldRegisterJei()) {
            return;
        }
        registration.addRecipes(TrinityMultiblockJeiCategory.RECIPE_TYPE, List.of(MultiblockXeiRecipe.trinity()));
        registration.addRecipes(RecipeTypes.CRAFTING, buildUniversalTerminalRecipes());
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<WorldInteractionJeiRecipe> worldInteractionRecipes = new ArrayList<>();
            worldInteractionRecipes.addAll(level.getRecipeManager().getAllRecipesFor(DERecipes.TIME_SHIFT_TYPE.get()).stream()
                    .map(WorldInteractionJeiRecipe.TimeShiftView::new)
                    .toList());
            worldInteractionRecipes.addAll(level.getRecipeManager().getAllRecipesFor(DERecipes.RADIX_CONTAINMENT_SPHERE_RIGHT_CLICK_TYPE.get()).stream()
                    .map(WorldInteractionJeiRecipe.RightClickView::new)
                    .toList());
            registration.addRecipes(
                    CondenserOutputRecipeCategory.RECIPE_TYPE,
                    level.getRecipeManager().getAllRecipesFor(DERecipes.CONDENSER_OUTPUT_TYPE.get()).stream()
                            .map(CondenserOutputRecipeView::from)
                            .toList());
            registration.addRecipes(
                    TimeShiftRecipeCategory.RECIPE_TYPE,
                    worldInteractionRecipes);
            registration.addRecipes(
                    DataRipperReassemblerRecipeCategory.RECIPE_TYPE,
                    level.getRecipeManager().getAllRecipesFor(DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()).stream()
                            .map(DataRipperReassemblerRecipeView::from)
                            .toList());
            registerRecipeType(
                    registration,
                    DataChargerRecipeCategory.RECIPE_TYPE,
                    level.getRecipeManager().getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get()),
                    RecipeHolder::value);
            registration.addRecipes(
                    DataChargePressRecipeCategory.RECIPE_TYPE,
                    DataChargePressRecipeView.fromRecipeManager(level.getRecipeManager()));
        }
        registration.addIngredientInfo(
                new ItemStack(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.data_energistics.radix_containment_sphere.line1"),
                Component.translatable("jei.data_energistics.radix_containment_sphere.line2"),
                Component.translatable("jei.data_energistics.radix_containment_sphere.line3"),
                Component.translatable(
                        "jei.data_energistics.data_reassembler.crafting_requirement",
                        RadixContainmentSphereCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST));
        registration.addIngredientInfo(
                DEItems.DATA_RIPPER_REASSEMBLER.toStack(),
                VanillaTypes.ITEM_STACK,
                Component.translatable(
                        "jei.data_energistics.data_reassembler.crafting_requirement",
                        RadixContainmentSphereCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST));
        registerMatterConvergingCrossbowAnvilRecipes(registration);
    }

    private static List<RecipeHolder<CraftingRecipe>> buildUniversalTerminalRecipes() {
        return UniversalTerminalCombineRecipeView.fromRegisteredTerminals().stream()
                .map(recipe -> new RecipeHolder<CraftingRecipe>(
                        recipe.id(),
                        new ShapelessRecipe(
                                "",
                                CraftingBookCategory.MISC,
                                recipe.output(),
                                NonNullList.of(
                                        Ingredient.EMPTY,
                                        Ingredient.of(recipe.firstInput()),
                                        Ingredient.of(recipe.secondInput())))))
                .toList();
    }

    private static <R extends Recipe<?>, T> void registerRecipeType(IRecipeRegistration registration, RecipeType<T> recipeType,
                                                                    List<RecipeHolder<R>> recipes,
                                                                    Function<RecipeHolder<R>, T> mapper) {
        registration.addRecipes(recipeType, recipes.stream().map(mapper).toList());
    }

    private static void registerDataChargerCatalysts(IRecipeCatalystRegistration registration, RecipeType<?> recipeType) {
        registration.addRecipeCatalyst(DEBlocks.DATA_CHARGER.get(), recipeType);
        registration.addRecipeCatalyst(DEBlocks.EXTENDED_DATA_CHARGER.get(), recipeType);
    }

    private static void registerMatterConvergingCrossbowAnvilRecipes(IRecipeRegistration registration) {
        HolderLookup.RegistryLookup<Enchantment> lookup = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var power = lookup.getOrThrow(Enchantments.POWER);

        ItemStack baseCrossbow = DEItems.MATTER_CONVERGING_CROSSBOW.get().getDefaultInstance();
        ItemStack enchantedCrossbow = baseCrossbow.copy();
        enchantedCrossbow.enchant(power, 1);

        ItemStack powerBook = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        builder.upgrade(power, 1);
        powerBook.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable());

        registration.addRecipes(
                RecipeTypes.ANVIL,
                List.of(registration.getVanillaRecipeFactory().createAnvilRecipe(
                        baseCrossbow,
                        List.of(powerBook),
                        List.of(enchantedCrossbow),
                        Data_Energistics.id("anvil/matter_converging_crossbow_power"))));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (!shouldRegisterJei()) {
            return;
        }
        this.jeiRuntime = jeiRuntime;
        PatternProviderRecipeTypeNames.register(
                RECIPE_TYPE_NAME_SOURCE_ID,
                recipeTypeId -> resolveRecipeTypeName(jeiRuntime.getRecipeManager(), recipeTypeId));
        JeiPatternTransferContextBridge.registerWorkstationSource(
                recipeTypeId -> resolveRecipeTypeWorkstations(jeiRuntime.getRecipeManager(), recipeTypeId));
        hidePoweredRepairRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        if (!shouldRegisterJei()) {
            return;
        }
        XeiLayoutRefreshQueue.cancel(MULTIBLOCK_REFRESH_KEY);
        PatternProviderRecipeTypeNames.unregister(RECIPE_TYPE_NAME_SOURCE_ID);
        JeiPatternTransferContextBridge.unregisterWorkstationSource();
        this.jeiRuntime = null;
        this.multiblockRefreshInProgress = false;
        releaseTrinityMultiblockCategory();
    }

    /**
     * JEI is the fallback integration; EMI owns the viewer registrations when both mods are installed.
     */
    private static boolean shouldRegisterJei() {
        return !ModFlags.isEmiLoaded();
    }

    private static List<Component> resolveRecipeTypeName(IRecipeManager recipeManager,
                                                         ResourceLocation recipeTypeId) {
        return recipeManager.getRecipeType(recipeTypeId)
                .map(recipeType -> List.of(resolveRecipeTypeName(recipeManager, recipeType)))
                .orElseGet(List::of);
    }

    private static <T> Component resolveRecipeTypeName(IRecipeManager recipeManager,
                                                       RecipeType<T> recipeType) {
        return recipeManager.getRecipeCategory(recipeType).getTitle();
    }

    private static List<ResourceLocation> resolveRecipeTypeWorkstations(
                                                                        IRecipeManager recipeManager,
                                                                        ResourceLocation recipeTypeId) {
        return recipeManager.getRecipeType(recipeTypeId)
                .map(recipeType -> recipeManager.createRecipeCatalystLookup(recipeType)
                        .getItemStack()
                        .filter(stack -> !stack.isEmpty())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Releases and detaches the category owned by the completed JEI runtime cycle.
     */
    void releaseTrinityMultiblockCategory() {
        TrinityMultiblockJeiCategory multiblockCategory = this.trinityMultiblockCategory;
        this.trinityMultiblockCategory = null;
        if (multiblockCategory != null) {
            multiblockCategory.releaseCachedUis();
        }
    }

    /**
     * Installs the sole category owned by the current JEI runtime registration cycle.
     */
    TrinityMultiblockJeiCategory installTrinityMultiblockCategory(
                                                                  TrinityMultiblockJeiCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("Trinity multiblock JEI category cannot be null");
        }
        if (this.trinityMultiblockCategory != null) {
            throw new IllegalStateException("Trinity multiblock JEI category was already registered");
        }
        this.trinityMultiblockCategory = category;
        return category;
    }

    /**
     * Returns the category retained by the current JEI runtime registration cycle.
     */
    @Nullable
    TrinityMultiblockJeiCategory currentTrinityMultiblockCategory() {
        return this.trinityMultiblockCategory;
    }

    private void requestMultiblockRefresh(
                                          MultiblockXeiRecipe recipe,
                                          MultiblockXeiComposition composition,
                                          MultiblockXeiComposition.RecipeChange change) {
        IJeiRuntime runtime = this.jeiRuntime;
        TrinityMultiblockJeiCategory category = currentTrinityMultiblockCategory();
        if (runtime == null || category == null || this.multiblockRefreshInProgress) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || screen != runtime.getRecipesGui()) {
            return;
        }
        XeiLayoutRefreshQueue.enqueue(
                MULTIBLOCK_REFRESH_KEY,
                screen,
                () -> this.jeiRuntime == runtime &&
                        this.trinityMultiblockCategory == category &&
                        !this.multiblockRefreshInProgress &&
                        recipe.isActiveComposition(composition) &&
                        composition.currentRecipeView().projectionFingerprint().equals(change.projectionFingerprint()),
                () -> refreshMultiblockRecipe(runtime, category, recipe));
    }

    private void refreshMultiblockRecipe(IJeiRuntime runtime,
                                         TrinityMultiblockJeiCategory category,
                                         MultiblockXeiRecipe recipe) {
        this.multiblockRefreshInProgress = true;
        try {
            // JEI exposes navigation, but no in-place formal-slot invalidation API.
            runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
        } finally {
            this.multiblockRefreshInProgress = false;
        }
    }

    private void hidePoweredRepairRecipes() {
        if (this.jeiRuntime == null) {
            return;
        }

        var recipeManager = this.jeiRuntime.getRecipeManager();
        var anvilRecipes = recipeManager.createRecipeLookup(RecipeTypes.ANVIL)
                .includeHidden()
                .get()
                .filter(PoweredRepairRecipeFilter::shouldHideJeiRepairRecipe)
                .toList();
        if (!anvilRecipes.isEmpty()) {
            recipeManager.hideRecipes(RecipeTypes.ANVIL, anvilRecipes);
        }

        var craftingRepairRecipes = recipeManager.createRecipeLookup(RecipeTypes.CRAFTING)
                .includeHidden()
                .get()
                .filter(PoweredRepairRecipeFilter::shouldHideJeiCraftingRepairRecipe)
                .toList();
        if (!craftingRepairRecipes.isEmpty()) {
            recipeManager.hideRecipes(RecipeTypes.CRAFTING, craftingRepairRecipes);
        }
    }
}
