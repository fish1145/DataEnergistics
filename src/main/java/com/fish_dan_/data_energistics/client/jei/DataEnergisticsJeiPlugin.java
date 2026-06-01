package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.recipe.PoweredRepairRecipeFilter;
import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import appeng.core.definitions.AEBlocks;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class DataEnergisticsJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CRAFTING_HANDLER_CLASS = "tamaized.ae2jeiintegration.integration.modules.jei.transfer.UseCraftingRecipeTransfer";
    private static final String ENCODING_HANDLER_CLASS = "tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler";
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
                new DataRipperReassemblerRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(AEBlocks.CONDENSER, DataCaptureBallCondenserCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModItems.DATA_CAPTURE_BALL.get(), TimeShiftRecipeCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(ModBlocks.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerRecipeCategory.RECIPE_TYPE);
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
        }
        registration.addIngredientInfo(
                new ItemStack(ModItems.DATA_CAPTURE_BALL.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.data_energistics.data_capture_ball.line1"),
                Component.translatable("jei.data_energistics.data_capture_ball.line2"),
                Component.translatable("jei.data_energistics.data_capture_ball.line3"));
        registerMatterConvergingCrossbowAnvilRecipes(registration);
    }

    private static void registerMatterConvergingCrossbowAnvilRecipes(IRecipeRegistration registration) {
        HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> lookup = net.minecraft.client.Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var power = lookup.getOrThrow(Enchantments.POWER);

        ItemStack baseCrossbow = ModItems.MATTER_CONVERGING_CROSSBOW.get().getDefaultInstance();
        ItemStack enchantedCrossbow = baseCrossbow.copy();
        enchantedCrossbow.enchant(power, 1);

        ItemStack powerBook = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        builder.upgrade(power, 1);
        powerBook.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable());

        registration.addRecipes(
                mezz.jei.api.constants.RecipeTypes.ANVIL,
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
    private static IRecipeTransferHandler<UniversalCraftingTermMenu, net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> createCraftingHandler(IRecipeTransferHandlerHelper transferHelper) {
        Object handler = ReflectionAccess.newInstance(
                CRAFTING_HANDLER_CLASS,
                new Class<?>[] {
                        Class.class,
                        net.minecraft.world.inventory.MenuType.class,
                        IRecipeTransferHandlerHelper.class },
                    UniversalCraftingTermMenu.class,
                    ModMenus.UNIVERSAL_CRAFTING_TERM.get(),
                    transferHelper);
        if (handler instanceof IRecipeTransferHandler<?, ?> recipeTransferHandler) {
            return (IRecipeTransferHandler<UniversalCraftingTermMenu, net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>>) recipeTransferHandler;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static IUniversalRecipeTransferHandler<UniversalPatternEncodingTermMenu> createEncodingHandler(
                                                                                                           IRecipeTransferHandlerHelper transferHelper,
                                                                                                           mezz.jei.api.runtime.IIngredientVisibility ingredientVisibility) {
        Object handler = ReflectionAccess.newInstance(
                ENCODING_HANDLER_CLASS,
                new Class<?>[] {
                        net.minecraft.world.inventory.MenuType.class,
                        Class.class,
                        IRecipeTransferHandlerHelper.class,
                        mezz.jei.api.runtime.IIngredientVisibility.class },
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
