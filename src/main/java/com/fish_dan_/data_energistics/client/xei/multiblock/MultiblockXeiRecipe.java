package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * One stable controller-level recipe registered by every XEI adapter.
 *
 * <p>
 * The recipe object owns no shared Scene. Each viewer cache load creates a fresh composition and publishes that
 * exact live source for future typed transfer integration.
 * </p>
 */
public final class MultiblockXeiRecipe implements MultiblockRecipeViewSource {

    /**
     * Shared category id used by JEI, EMI, and the future REI adapter.
     */
    public static final ResourceLocation CATEGORY_ID = Data_Energistics.id("multiblock_preview");

    private final ResourceLocation controllerId;
    private final MultiblockXeiUiFactory uiFactory;
    @Nullable
    private MultiblockXeiComposition activeComposition;
    @Nullable
    private PreviewSelection retainedSelection;

    /**
     * Creates the sole production recipe registered for the Trinity Data Core controller.
     */
    public static MultiblockXeiRecipe trinity() {
        return create(ModVerticalMultiBlocks.trinityDataCoreId(), MultiblockXeiUiFactory.createDefault());
    }

    /**
     * Creates an injected recipe for direct adapter and lifecycle tests.
     */
    public static MultiblockXeiRecipe create(ResourceLocation controllerId, MultiblockXeiUiFactory uiFactory) {
        return new MultiblockXeiRecipe(controllerId, uiFactory);
    }

    private MultiblockXeiRecipe(ResourceLocation controllerId, MultiblockXeiUiFactory uiFactory) {
        if (controllerId == null || uiFactory == null) {
            throw new IllegalArgumentException("Multiblock XEI recipe arguments cannot be null");
        }
        this.controllerId = controllerId;
        this.uiFactory = uiFactory;
    }

    /**
     * Creates and publishes a fresh independently owned composition for one viewer cache entry.
     */
    public MultiblockXeiComposition createComposition(String idPrefix) {
        return createComposition(idPrefix, (composition, change) -> {});
    }

    /**
     * Creates and publishes a composition with its live recipe-change listener installed before exposure.
     */
    public MultiblockXeiComposition createComposition(
                                                      String idPrefix,
                                                      BiConsumer<MultiblockXeiComposition, MultiblockXeiComposition.RecipeChange> changeListener) {
        if (changeListener == null) {
            throw new IllegalArgumentException("Multiblock XEI recipe change listener cannot be null");
        }
        MultiblockXeiComposition composition = this.uiFactory.create(
                this.controllerId,
                this.retainedSelection,
                idPrefix);
        composition.setRecipeChangeListener(change -> {
            this.retainedSelection = composition.previewUi().session().selection();
            changeListener.accept(composition, change);
        });
        this.retainedSelection = composition.previewUi().session().selection();
        this.activeComposition = composition;
        return composition;
    }

    /**
     * Creates the ModularUI while retaining its composition as the current typed source.
     */
    public ModularUI createModularUI(String idPrefix) {
        return createComposition(idPrefix).modularUI();
    }

    /**
     * Creates a ModularUI whose composition reports recipe-affecting live-view changes.
     */
    public ModularUI createModularUI(
                                     String idPrefix,
                                     BiConsumer<MultiblockXeiComposition, MultiblockXeiComposition.RecipeChange> changeListener) {
        return createComposition(idPrefix, changeListener).modularUI();
    }

    /**
     * Returns whether a deferred viewer refresh still targets this recipe's active composition.
     */
    public boolean isActiveComposition(MultiblockXeiComposition composition) {
        return composition != null && this.activeComposition == composition && composition.isActive();
    }

    @Override
    public ResourceLocation registeredRecipeId() {
        return MultiblockRecipeView.registeredRecipeIdFor(this.controllerId);
    }

    @Override
    public MultiblockRecipeView currentRecipeView() {
        if (this.activeComposition == null) {
            throw new IllegalStateException("Multiblock XEI recipe has no active UI composition");
        }
        return this.activeComposition.currentRecipeView();
    }
}
