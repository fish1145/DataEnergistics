package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.client.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

/**
 * EMI adapter for the shared live Trinity multiblock composition.
 */
public final class TrinityMultiblockEmiRecipe extends ModularUIEMIRecipe implements MultiblockRecipeViewSource {

    /**
     * Shared category identity and controller icon used by the sole Trinity wrapper recipe.
     */
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            MultiblockXeiRecipe.CATEGORY_ID,
            EmiStack.of(ModBlocks.TRINITY_DATA_CORE.get()));

    private final MultiblockXeiRecipe recipe;

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
        return this.recipe.createComposition(idPrefix);
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return CATEGORY;
    }

    @Override
    public ResourceLocation getId() {
        return registeredRecipeId();
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

    private static ModularUI createModularUI(ModularUIEMIRecipe recipe) {
        if (!(recipe instanceof TrinityMultiblockEmiRecipe trinityRecipe)) {
            throw new IllegalArgumentException("Trinity EMI UI provider received another recipe type");
        }
        return trinityRecipe.recipe.createModularUI("trinity_multiblock_emi");
    }
}
