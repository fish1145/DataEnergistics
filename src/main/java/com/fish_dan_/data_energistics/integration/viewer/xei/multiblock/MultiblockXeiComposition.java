package com.fish_dan_.data_energistics.integration.viewer.xei.multiblock;

import com.fish_dan_.data_energistics.client.util.TrinityAmountFormatter;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild.AutoBuildComposition;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.StructurePreviewUiFactory;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** One independently owned, NBT-authored multiblock composition shared by JEI and EMI. */
public final class MultiblockXeiComposition implements MultiblockRecipeViewSource {

    /** Width authored by {@code xei_auto_build.ui.nbt}. */
    public static final int WIDTH = 256;
    /** Height authored by {@code xei_auto_build.ui.nbt}. */
    public static final int HEIGHT = 186;

    private static final String PREVIEW_TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final String ADJUSTMENT_TRANSLATION_PREFIX = "screen.data_energistics.trinity_data_core.auto_build.adjustment.";
    private static final String MATERIAL_GRID_SUFFIX = "_material_grid";

    private final MultiblockPreviewCatalog catalog;
    private final MultiblockPreviewSpec spec;
    private final StructurePreviewUiFactory previewFactory;
    private final boolean logicalClient;
    private final String idPrefix;
    private final ObjectList<String> structureKeys;
    private final StructurePreviewUi previewUi;
    private final AutoBuildComposition composition;
    private final PreviewMaterial ownerOutputMaterial;
    private final ModularUI modularUI;
    private Consumer<RecipeChange> recipeChangeListener = change -> {};
    private boolean recipeChangeListenerRegistered;
    private boolean removed;

    MultiblockXeiComposition(MultiblockPreviewCatalog catalog,
                             MultiblockPreviewSpec spec,
                             PreviewSelection initialSelection,
                             StructurePreviewUiFactory previewFactory,
                             boolean logicalClient,
                             String idPrefix) {
        if (idPrefix.isBlank()) {
            throw new IllegalArgumentException("Multiblock XEI composition id prefix cannot be blank");
        }
        initialSelection.validateAgainst(spec);
        this.catalog = catalog;
        this.spec = spec;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
        this.idPrefix = idPrefix;
        ObjectList<String> structureKeys = new ObjectArrayList<>(spec.substructures().size());
        spec.substructures().forEach(substructure -> structureKeys.add(substructure.id()));
        this.structureKeys = ObjectLists.unmodifiable(structureKeys);

        UI ui = TrinityUiNbtLayouts.load("xei_auto_build");
        MultiblockXeiLayout.Layout layout = MultiblockXeiLayout.require(ui.rootElement, idPrefix);
        layout.title().setText(spec.title());
        layout.root().addEventListener(UIEvents.REMOVED, ignored -> this.removed = true);

        this.previewUi = createPreview(initialSelection);
        this.ownerOutputMaterial = this.previewUi.session().recipeView().output();
        this.composition = AutoBuildComposition.builder(this.previewUi, layout.elements())
                .geometry(layout.geometry())
                .recipeInputs(idPrefix + MATERIAL_GRID_SUFFIX, TrinityAmountFormatter::format)
                .build();
        this.composition.bindStructureActions(new AutoBuildComposition.StructureActions(
                () -> selectRelativeStructure(-1),
                () -> selectRelativeStructure(1)));
        this.composition.setAdjustmentHeadings(
                Component.translatable(ADJUSTMENT_TRANSLATION_PREFIX + "context"),
                Component.translatable(ADJUSTMENT_TRANSLATION_PREFIX + "value"));
        this.composition.setAdjustmentTooltips(
                Component.translatable(ADJUSTMENT_TRANSLATION_PREFIX + "context"),
                label -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "previous", label),
                label -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "next", label));
        refreshBoundState(null);
        this.modularUI = ModularUI.of(ui);
    }

    /** Returns the complete independently owned ModularUI used by one viewer cache entry. */
    public ModularUI modularUI() {
        return this.modularUI;
    }

    /** Returns the exact shared preview/session currently mounted by this composition. */
    public StructurePreviewUi previewUi() {
        requireActive();
        return this.previewUi;
    }

    /** Installs the sole listener notified after a recipe-affecting selection refresh completes. */
    public void setRecipeChangeListener(Consumer<RecipeChange> recipeChangeListener) {
        requireActive();
        if (this.recipeChangeListenerRegistered) {
            throw new IllegalStateException("Multiblock XEI recipe change listener can only be registered once");
        }
        this.recipeChangeListener = recipeChangeListener;
        this.recipeChangeListenerRegistered = true;
    }

    /** Returns whether this composition still owns a live UI/session tree. */
    public boolean isActive() {
        return !this.removed;
    }

    @Override
    public ResourceLocation registeredRecipeId() {
        return MultiblockRecipeView.registeredRecipeIdFor(this.spec.controllerId());
    }

    @Override
    public MultiblockRecipeView currentRecipeView() {
        requireActive();
        MultiblockPreviewCatalogSnapshot currentCatalog = this.catalog.snapshot();
        MultiblockPreviewSpec currentSpec = currentCatalog.require(this.spec.controllerId());
        if (currentCatalog.definitionRevision() != this.spec.definitionRevision() ||
                currentSpec.definitionRevision() != this.spec.definitionRevision()) {
            throw new IllegalStateException("Multiblock XEI composition uses stale definition revision " +
                    this.spec.definitionRevision() + ", current revision is " + currentCatalog.definitionRevision());
        }
        MultiblockRecipeView view = this.previewUi.session().recipeView();
        if (!view.registeredRecipeId().equals(registeredRecipeId())) {
            throw new IllegalStateException("Multiblock XEI composition returned a recipe for another controller");
        }
        return view;
    }

    private StructurePreviewUi createPreview(PreviewSelection selection) {
        StructurePreviewUi created = this.previewFactory.create(
                this.spec,
                selection,
                this.structureKeys,
                this.idPrefix + "_preview",
                this.logicalClient);
        created.panel().setSelectionChangeListener(this::onSelectionChanged);
        return created;
    }

    private void onSelectionChanged(PreviewSelection selection) {
        if (!selection.equals(this.previewUi.session().selection())) {
            throw new IllegalStateException("Multiblock XEI panel published a selection other than its session state");
        }
        String retainedAdjustment = this.composition.activeAdjustmentKey();
        refreshBoundState(retainedAdjustment);
        MultiblockRecipeView view = currentRecipeView();
        this.recipeChangeListener.accept(new RecipeChange(view.projectionFingerprint(), false));
    }

    private void refreshBoundState(@Nullable String retainedAdjustment) {
        MultiblockRecipeView view = this.previewUi.session().recipeView();
        if (!this.ownerOutputMaterial.equals(view.output())) {
            throw new IllegalStateException("Multiblock XEI encoded output changed within one controller recipe");
        }
        this.composition.setStructureTitle(activeSubstructure().title());
        this.composition.setMaterials(view.inputs());
        this.composition.refreshLayers();
        this.composition.setAdjustments(createAdjustments(), retainedAdjustment);
        refreshStructureTooltips();
    }

    private ObjectList<AutoBuildComposition.Adjustment> createAdjustments() {
        SubstructurePreviewSpec substructure = activeSubstructure();
        ObjectList<AutoBuildComposition.Adjustment> adjustments = new ObjectArrayList<>();

        if (substructure.variantCount() > 1) {
            adjustments.add(new AutoBuildComposition.Adjustment(
                    "variant",
                    () -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "variant"),
                    () -> Component.translatable(
                            PREVIEW_TRANSLATION_PREFIX + "variant.value",
                            this.previewUi.session().selection().activeSelection().variantIndex() + 1,
                            activeSubstructure().variantCount()),
                    () -> selectRelativeVariant(-1),
                    () -> selectRelativeVariant(1),
                    () -> true));
        }

        PreviewTierDomain tierDomain = substructure.tierDomains().getFirst();
        adjustments.add(new AutoBuildComposition.Adjustment(
                "tier:" + tierDomain.id(),
                tierDomain::label,
                () -> tierDomain.option(
                        this.previewUi.session().selection().activeSelection().tierSelections().get(tierDomain.id()))
                        .label(),
                this.previewUi.panel()::previousTier,
                this.previewUi.panel()::nextTier,
                () -> tierDomain.options().size() > 1));

        for (int unitIndex : this.previewUi.session().variableRepeatUnits()) {
            adjustments.add(new AutoBuildComposition.Adjustment(
                    "repeat:" + unitIndex,
                    () -> Component.translatable(PREVIEW_TRANSLATION_PREFIX + "repeat", unitIndex + 1),
                    () -> Component.literal(Integer.toString(
                            this.previewUi.session().selection().activeSelection().repeatCounts().get(unitIndex))),
                    () -> this.previewUi.panel().previousRepeat(unitIndex),
                    () -> this.previewUi.panel().nextRepeat(unitIndex),
                    () -> true));
        }
        return ObjectLists.unmodifiable(adjustments);
    }

    private void selectRelativeStructure(int direction) {
        this.previewUi.panel().selectStructure(relativeStructureKey(direction));
    }

    private void selectRelativeVariant(int direction) {
        SubstructurePreviewSpec substructure = activeSubstructure();
        int current = this.previewUi.session().selection().activeSelection().variantIndex();
        this.previewUi.panel().selectVariant(Math.floorMod(current + direction, substructure.variantCount()));
    }

    private void refreshStructureTooltips() {
        this.composition.setStructureTooltips(
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "previous",
                        this.spec.substructure(relativeStructureKey(-1)).title()),
                Component.translatable(
                        PREVIEW_TRANSLATION_PREFIX + "next",
                        this.spec.substructure(relativeStructureKey(1)).title()));
    }

    private String relativeStructureKey(int direction) {
        int current = this.structureKeys.indexOf(this.previewUi.session().structureKey());
        if (current < 0) {
            throw new IllegalStateException("Active XEI multiblock structure is absent from its catalog");
        }
        return this.structureKeys.get(Math.floorMod(current + direction, this.structureKeys.size()));
    }

    private SubstructurePreviewSpec activeSubstructure() {
        return this.spec.substructure(this.previewUi.session().structureKey());
    }

    private void requireActive() {
        if (this.removed) {
            throw new IllegalStateException("Multiblock XEI composition has already been removed");
        }
    }

    /** Immutable viewer-refresh event produced after one valid recipe-affecting selection. */
    public record RecipeChange(ProjectionFingerprint projectionFingerprint, boolean widgetPoolGrew) {}
}
