package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.client.xei.XeiLayoutRefreshQueue;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeIngredientHandler;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * EMI adapter for the shared live Trinity multiblock composition.
 */
public final class TrinityMultiblockEmiRecipe extends ModularUIEMIRecipe implements MultiblockRecipeViewSource {

    /**
     * Shared category identity and controller icon used by the sole Trinity wrapper recipe.
     */
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            MultiblockXeiRecipe.CATEGORY_ID,
            EmiStack.of(DEBlocks.TRINITY_DATA_CORE.get()));

    private final MultiblockXeiRecipe recipe;
    private final Object widgetRefreshKey = new Object();
    @Nullable
    private MultiblockXeiComposition activeComposition;
    private boolean widgetRefreshInProgress;

    /**
     * Creates the production Trinity recipe registered by the EMI plugin.
     */
    public TrinityMultiblockEmiRecipe() {
        this(MultiblockXeiRecipe.trinity());
    }

    /**
     * Creates an injected EMI adapter for direct parity and lifecycle tests.
     */
    public TrinityMultiblockEmiRecipe(MultiblockXeiRecipe recipe) {
        super(TrinityMultiblockEmiRecipe::createModularUI);
        if (recipe == null) {
            throw new IllegalArgumentException("Trinity multiblock EMI recipe source cannot be null");
        }
        this.recipe = recipe;
    }

    /**
     * Creates and publishes a fresh composition through the same neutral source used by JEI.
     */
    public MultiblockXeiComposition createComposition(String idPrefix) {
        MultiblockXeiComposition composition = this.recipe.createComposition(
                idPrefix,
                this::requestWidgetRefresh);
        bindRecipeIngredients(composition);
        this.activeComposition = composition;
        return composition;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return EmiMultiblockRecipeId.synthetic(registeredRecipeId());
    }

    @Override
    public int getDisplayWidth() {
        return MultiblockXeiComposition.WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return MultiblockXeiComposition.HEIGHT;
    }

    @Override
    public ResourceLocation registeredRecipeId() {
        return this.recipe.registeredRecipeId();
    }

    @Override
    public MultiblockRecipeView currentRecipeView() {
        return this.recipe.currentRecipeView();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.copyOf(collectRecipeIngredients().inputs);
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return List.copyOf(collectRecipeIngredients().catalysts);
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.copyOf(collectRecipeIngredients().outputs);
    }

    private EMIRecipeIngredientHandler collectRecipeIngredients() {
        EMIRecipeIngredientHandler ingredients = new EMIRecipeIngredientHandler();
        MultiblockXeiComposition composition = this.activeComposition;
        if (composition == null) {
            getModularUI();
            composition = this.activeComposition;
        }
        if (composition == null) {
            throw new IllegalStateException("EMI multiblock recipe did not create an active composition");
        }
        UIEvent event = UIEvent.create(EMIUIEvents.RECIPE_INGREDIENT);
        event.target = composition.modularUI().ui.rootElement;
        event.customData = ingredients;
        UIEventDispatcher.dispatchAllChildren(event);
        return ingredients;
    }

    private static void bindRecipeIngredients(MultiblockXeiComposition composition) {
        composition.modularUI().ui.rootElement.addEventListener(
                EMIUIEvents.RECIPE_INGREDIENT,
                event -> publishRecipeIngredients(event, composition));
    }

    private static void publishRecipeIngredients(UIEvent event, MultiblockXeiComposition composition) {
        if (!(event.customData instanceof EMIRecipeIngredientHandler ingredients)) {
            return;
        }
        MultiblockRecipeView view = composition.currentRecipeView();
        List<EmiIngredient> inputs = view.inputs().stream()
                .map(TrinityMultiblockEmiRecipe::emiStack)
                .map(stack -> (EmiIngredient) stack)
                .toList();
        ingredients.addInput(inputs);
        ingredients.addOutput(List.of(emiStack(view.output())));
    }

    private static EmiStack emiStack(PreviewMaterial material) {
        return EmiStack.of(material.key().toStack(1), material.amount());
    }

    private void requestWidgetRefresh(MultiblockXeiComposition composition,
                                      MultiblockXeiComposition.RecipeChange change) {
        if (!change.widgetPoolGrew() || this.widgetRefreshInProgress) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        XeiLayoutRefreshQueue.enqueue(
                this.widgetRefreshKey,
                screen,
                () -> !this.widgetRefreshInProgress &&
                        this.recipe.isActiveComposition(composition),
                this::refreshWidgets);
    }

    private void refreshWidgets() {
        this.widgetRefreshInProgress = true;
        try {
            EmiApi.focusRecipe(this);
        } finally {
            this.widgetRefreshInProgress = false;
        }
    }

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (!(recipe instanceof TrinityMultiblockEmiRecipe trinityRecipe)) {
            throw new IllegalArgumentException("Trinity EMI UI provider received another recipe type");
        }
        return trinityRecipe.createComposition("trinity_multiblock_emi").modularUI();
    }
}
