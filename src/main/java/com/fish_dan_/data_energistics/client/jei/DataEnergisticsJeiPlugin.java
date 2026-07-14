package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.handlers.ChargerRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@JeiPlugin
public final class DataEnergisticsJeiPlugin implements IModPlugin {

    private static final String AE2_JEI_TRANSFER_PACKAGE = String.join(
            ".",
            "tamaized",
            "ae2jeiintegration",
            "integration",
            "modules",
            "jei",
            "transfer");
    private static final String CRAFTING_HANDLER_CLASS = AE2_JEI_TRANSFER_PACKAGE + ".UseCraftingRecipeTransfer";
    private static final String ENCODING_HANDLER_CLASS = AE2_JEI_TRANSFER_PACKAGE + ".EncodePatternTransferHandler";
    private static final ResourceLocation AE2_CHARGER_RECIPE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final RecipeType<RecipeHolder<ChargerRecipe>> AE2_CHARGER_RECIPE_TYPE = RecipeType.createRecipeHolderType(AE2_CHARGER_RECIPE_ID);
    private static final Class<?>[] CRAFTING_HANDLER_PARAMETERS = {
            Class.class,
            MenuType.class,
            IRecipeTransferHandlerHelper.class };
    private static final Class<?>[] ENCODING_HANDLER_PARAMETERS = {
            MenuType.class,
            Class.class,
            IRecipeTransferHandlerHelper.class,
            IIngredientVisibility.class };
    private IJeiRuntime jeiRuntime;

    @Override
    public ResourceLocation getPluginUid() {
        return Data_Energistics.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new TimeShiftRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataCaptureBallCondenserCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataChargerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new DataRipperReassemblerRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new TrinityMultiblockJeiCategory(registration.getJeiHelpers()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(AEBlocks.CONDENSER, DataCaptureBallCondenserCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.DATA_CAPTURE_BALL.get(), TimeShiftRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModBlocks.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModBlocks.DATA_CHARGER.get(), AE2_CHARGER_RECIPE_TYPE);
        registration.addRecipeCatalyst(ModBlocks.EXTENDED_DATA_CHARGER.get(), AE2_CHARGER_RECIPE_TYPE);
        registration.addRecipeCatalyst(ModBlocks.TRINITY_DATA_CORE.get(), TrinityMultiblockJeiCategory.RECIPE_TYPE);
        registerDataChargerCatalysts(registration, DataChargerRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var craftingHandler = createCraftingHandler(registration.getTransferHelper());
        if (craftingHandler != null) {
            registration.addRecipeTransferHandler(craftingHandler, RecipeTypes.CRAFTING);
        }

        var encodingHandler = createEncodingHandler(
                registration.getTransferHelper(),
                registration.getJeiHelpers().getIngredientVisibility());
        if (encodingHandler != null) {
            registration.addUniversalRecipeTransferHandler(encodingHandler);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(DataCaptureBallCondenserCategory.RECIPE_TYPE, List.of(DataCaptureBallCondenserRecipe.INSTANCE));
        registration.addRecipes(TrinityMultiblockJeiCategory.RECIPE_TYPE, List.of(MultiblockXeiRecipe.trinity()));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<WorldInteractionJeiRecipe> worldInteractionRecipes = new ArrayList<>();
            worldInteractionRecipes.addAll(level.getRecipeManager().getAllRecipesFor(ModRecipes.TIME_SHIFT_TYPE.get()).stream()
                    .map(WorldInteractionJeiRecipe.TimeShiftView::new)
                    .toList());
            worldInteractionRecipes.addAll(level.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE.get()).stream()
                    .map(WorldInteractionJeiRecipe.RightClickView::new)
                    .toList());
            registration.addRecipes(
                    TimeShiftRecipeCategory.RECIPE_TYPE,
                    worldInteractionRecipes);
            registration.addRecipes(
                    DataRipperReassemblerRecipeCategory.RECIPE_TYPE,
                    level.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get()).stream()
                            .map(RecipeHolder::value)
                            .toList());
            registerRecipeType(
                    registration,
                    DataChargerRecipeCategory.RECIPE_TYPE,
                    level.getRecipeManager().getAllRecipesFor(ModRecipes.DATA_CHARGER_TYPE.get()),
                    RecipeHolder::value);
        }
        registration.addIngredientInfo(
                new ItemStack(ModItems.DATA_CAPTURE_BALL.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.data_energistics.data_capture_ball.line1"),
                Component.translatable("jei.data_energistics.data_capture_ball.line2"),
                Component.translatable("jei.data_energistics.data_capture_ball.line3"),
                Component.translatable(
                        "jei.data_energistics.data_reassembler.crafting_requirement",
                        DataCaptureBallCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST));
        registration.addIngredientInfo(
                ModItems.DATA_RIPPER_REASSEMBLER.toStack(),
                VanillaTypes.ITEM_STACK,
                Component.translatable(
                        "jei.data_energistics.data_reassembler.crafting_requirement",
                        DataCaptureBallCraftingRemainderHelper.DATA_REASSEMBLER_DATA_COST));
        registerMatterConvergingCrossbowAnvilRecipes(registration);
    }

    private static <R extends Recipe<?>, T> void registerRecipeType(IRecipeRegistration registration, RecipeType<T> recipeType,
                                                                    List<RecipeHolder<R>> recipes,
                                                                    Function<RecipeHolder<R>, T> mapper) {
        registration.addRecipes(recipeType, recipes.stream().map(mapper).toList());
    }

    private static void registerDataChargerCatalysts(IRecipeCatalystRegistration registration, RecipeType<?> recipeType) {
        registration.addRecipeCatalyst(ModBlocks.DATA_CHARGER.get(), recipeType);
        registration.addRecipeCatalyst(ModBlocks.EXTENDED_DATA_CHARGER.get(), recipeType);
    }

    private static void registerMatterConvergingCrossbowAnvilRecipes(IRecipeRegistration registration) {
        HolderLookup.RegistryLookup<Enchantment> lookup = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var power = lookup.getOrThrow(Enchantments.POWER);

        ItemStack baseCrossbow = ModItems.MATTER_CONVERGING_CROSSBOW.get().getDefaultInstance();
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
        this.jeiRuntime = jeiRuntime;
        hidePoweredRepairRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.jeiRuntime = null;
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

    @SuppressWarnings("unchecked")
    private static IRecipeTransferHandler<UniversalCraftingTermMenu, RecipeHolder<CraftingRecipe>> createCraftingHandler(IRecipeTransferHandlerHelper transferHelper) {
        Object handler = ReflectionAccess.newInstance(
                CRAFTING_HANDLER_CLASS,
                CRAFTING_HANDLER_PARAMETERS,
                UniversalCraftingTermMenu.class,
                ModMenus.UNIVERSAL_CRAFTING_TERM.get(),
                transferHelper);
        if (handler instanceof IRecipeTransferHandler<?, ?> recipeTransferHandler) {
            return (IRecipeTransferHandler<UniversalCraftingTermMenu, RecipeHolder<CraftingRecipe>>) recipeTransferHandler;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static IUniversalRecipeTransferHandler<UniversalPatternEncodingTermMenu> createEncodingHandler(
                                                                                                           IRecipeTransferHandlerHelper transferHelper,
                                                                                                           IIngredientVisibility ingredientVisibility) {
        Object handler = ReflectionAccess.newInstance(
                ENCODING_HANDLER_CLASS,
                ENCODING_HANDLER_PARAMETERS,
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                UniversalPatternEncodingTermMenu.class,
                transferHelper,
                ingredientVisibility);
        if (handler instanceof IUniversalRecipeTransferHandler<?> recipeTransferHandler) {
            return (IUniversalRecipeTransferHandler<UniversalPatternEncodingTermMenu>) recipeTransferHandler;
        }
        return null;
    }
}
