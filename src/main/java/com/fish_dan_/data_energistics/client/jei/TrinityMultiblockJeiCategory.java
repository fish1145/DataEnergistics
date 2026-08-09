package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiIngredient;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeIngredientHandler;
import lombok.Getter;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JEI adapter for the shared live Trinity multiblock composition.
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class TrinityMultiblockJeiCategory extends ModularUIRecipeCategory<MultiblockXeiRecipe> {

    /**
     * Sole controller-level JEI recipe type for every Trinity substructure and selection.
     */
    public static final RecipeType<MultiblockXeiRecipe> RECIPE_TYPE = new RecipeType<>(
            MultiblockXeiRecipe.CATEGORY_ID,
            MultiblockXeiRecipe.class);

    @Getter
    private final IDrawable icon;

    /**
     * Creates the category with LDLib2's ModularUI recipe provider and the Trinity controller icon.
     */
    public TrinityMultiblockJeiCategory(IJeiHelpers helpers, RecipeRefresh recipeRefresh) {
        this(createIcon(helpers), recipeRefresh);
    }

    TrinityMultiblockJeiCategory(IDrawable icon, RecipeRefresh recipeRefresh) {
        super(recipe -> createModularUI(recipe, recipeRefresh));
        this.icon = icon;
    }

    private static IDrawable createIcon(IJeiHelpers helpers) {
        return helpers.getGuiHelper().createDrawableItemLike(ModBlocks.TRINITY_DATA_CORE.get());
    }

    /**
     * Releases every LDLib2 composition retained by the category before a JEI runtime restart.
     */
    void releaseCachedUis() {
        this.uiCache.invalidateAll();
        this.uiCache.cleanUp();
    }

    private static ModularUI createModularUI(MultiblockXeiRecipe recipe, RecipeRefresh recipeRefresh) {
        MultiblockXeiComposition composition = recipe.createComposition(
                "trinity_multiblock_jei",
                (activeComposition, change) -> recipeRefresh.request(recipe, activeComposition, change));
        bindRecipeIngredients(composition);
        return composition.modularUI();
    }

    /**
     * Binds the one root-level live ingredient publisher used by JEI layout construction.
     */
    public static void bindRecipeIngredients(MultiblockXeiComposition composition) {
        composition.modularUI().ui.rootElement.addEventListener(
                JEIUIEvents.RECIPE_INGREDIENT,
                event -> publishRecipeIngredients(event, composition));
    }

    private static void publishRecipeIngredients(UIEvent event, MultiblockXeiComposition composition) {
        if (!(event.customData instanceof JEIRecipeIngredientHandler ingredients)) {
            return;
        }
        MultiblockRecipeView view = composition.currentRecipeView();
        for (PreviewMaterial material : view.inputs()) {
            ingredients.add(
                    RecipeIngredientRole.INPUT,
                    typedIngredient(IngredientIO.INPUT, material));
        }
        ingredients.add(
                RecipeIngredientRole.OUTPUT,
                typedIngredient(IngredientIO.OUTPUT, view.output()));
    }

    private static ITypedIngredient<ItemStack> typedIngredient(IngredientIO role, PreviewMaterial material) {
        ItemStack stack = new MultiblockXeiIngredient(role, material).toItemStack();
        return LDLibJEIPlugin.createTypedIngredient(VanillaTypes.ITEM_STACK, stack)
                .orElseThrow(() -> new IllegalStateException("JEI rejected a live multiblock item ingredient"));
    }

    @Override
    public RecipeType<MultiblockXeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return ModBlocks.TRINITY_DATA_CORE.get().getName();
    }

    @Override
    public int getWidth() {
        return MultiblockXeiComposition.WIDTH;
    }

    @Override
    public int getHeight() {
        return MultiblockXeiComposition.HEIGHT;
    }

    @FunctionalInterface
    public interface RecipeRefresh {

        /**
         * Requests a deferred layout refresh for one exact active composition.
         */
        void request(MultiblockXeiRecipe recipe,
                     MultiblockXeiComposition composition,
                     MultiblockXeiComposition.RecipeChange change);
    }
}
