package com.fish_dan_.data_energistics.client.xei.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One independently owned, viewer-neutral Trinity preview composition.
 *
 * <p>
 * Recipe-affecting controls update the live typed view. Logical-layer controls remain view-only, while a
 * candidate change recreates the shared preview through its factory so the old Scene follows normal LDLib2 removal
 * and resource release.
 * </p>
 */
public final class MultiblockXeiComposition implements MultiblockRecipeViewSource {

    /**
     * Width shared by JEI and EMI adapters.
     */
    public static final int WIDTH = 180;
    /**
     * Height shared by JEI and EMI adapters.
     */
    public static final int HEIGHT = 272;

    private static final int STRUCTURE_SELECTOR_HEIGHT = 18;
    private static final int PREVIEW_HEIGHT = 208;
    private static final int CANDIDATE_TOP = STRUCTURE_SELECTOR_HEIGHT + PREVIEW_HEIGHT;
    private static final int CANDIDATE_HEIGHT = 20;
    private static final int RECIPE_TOP = CANDIDATE_TOP + CANDIDATE_HEIGHT + 2;
    private static final int RECIPE_HEIGHT = 24;

    private final MultiblockPreviewCatalog catalog;
    private final MultiblockPreviewSpec spec;
    private final StructurePreviewUiFactory previewFactory;
    private final boolean logicalClient;
    private final String idPrefix;
    private final List<String> structureKeys;
    private final CompositionRoot root;
    private final UIElement previewHost;
    private final ScrollerView candidateControls;
    private final ScrollerView recipeSlots;
    private final ModularUI modularUI;
    private StructurePreviewUi previewUi;
    private boolean removed;

    MultiblockXeiComposition(MultiblockPreviewCatalog catalog,
                             MultiblockPreviewSpec spec,
                             PreviewSelection initialSelection,
                             StructurePreviewUiFactory previewFactory,
                             boolean logicalClient,
                             String idPrefix) {
        if (catalog == null || spec == null || initialSelection == null || previewFactory == null ||
                idPrefix == null || idPrefix.isBlank()) {
            throw new IllegalArgumentException("Multiblock XEI composition arguments cannot be null or blank");
        }
        initialSelection.validateAgainst(spec);
        this.catalog = catalog;
        this.spec = spec;
        this.previewFactory = previewFactory;
        this.logicalClient = logicalClient;
        this.idPrefix = idPrefix;
        this.structureKeys = spec.substructures().stream().map(SubstructurePreviewSpec::id).toList();

        this.root = new CompositionRoot(this::markRemoved);
        this.root.setId(idPrefix + "_root");
        this.root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        this.root.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));

        this.previewHost = createPreviewHost();
        this.candidateControls = createHorizontalScroller(idPrefix + "_candidates", CANDIDATE_TOP, CANDIDATE_HEIGHT);
        this.recipeSlots = createHorizontalScroller(idPrefix + "_recipe_slots", RECIPE_TOP, RECIPE_HEIGHT);
        this.root.addChildren(createStructureSelector(), this.previewHost, this.candidateControls, this.recipeSlots);

        this.previewUi = createPreview(initialSelection);
        this.previewHost.addChild(this.previewUi.panel());
        refreshCandidateControls();
        refreshRecipeSlots();
        this.modularUI = ModularUI.of(UI.of(this.root));
    }

    /**
     * Returns the complete independently owned ModularUI used by one adapter cache entry.
     */
    public ModularUI modularUI() {
        return this.modularUI;
    }

    /**
     * Returns the exact shared preview/session currently mounted by this composition.
     */
    public StructurePreviewUi previewUi() {
        requireActive();
        return this.previewUi;
    }

    /**
     * Activates a named child structure through the shared panel refresh/listener path.
     */
    public void selectStructure(String structureKey) {
        requireActive();
        this.previewUi.panel().selectStructure(structureKey);
    }

    /**
     * Selects one shape variant while retaining every compatible structure-local choice.
     */
    public void selectVariant(int variantIndex) {
        requireActive();
        PreviewSelection updated = this.previewUi.session().selection().withVariantIndex(variantIndex);
        replacePreview(updated);
    }

    /**
     * Replaces one active predicate candidate while retaining all structure-local variant, tier, and repeat state.
     */
    public void selectCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        requireActive();
        PreviewPredicateSnapshot predicate = candidatePredicates().get(predicateKey);
        if (predicate == null) {
            throw new IllegalArgumentException("Unknown selectable multiblock preview predicate: " + predicateKey);
        }
        if (candidateIndex < 0 || candidateIndex >= predicate.candidates().size()) {
            throw new IllegalArgumentException("Candidate index " + candidateIndex + " is outside 0.." +
                    (predicate.candidates().size() - 1) + " for " + predicateKey);
        }
        PreviewSelection updated = this.previewUi.session().selection().withCandidate(predicateKey, candidateIndex);
        replacePreview(updated);
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

    private UIElement createStructureSelector() {
        UIElement selector = new UIElement();
        selector.setId(this.idPrefix + "_structures");
        selector.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(WIDTH)
                .height(STRUCTURE_SELECTOR_HEIGHT));
        int count = this.spec.substructures().size();
        for (int index = 0; index < count; index++) {
            SubstructurePreviewSpec substructure = this.spec.substructures().get(index);
            int left = index * WIDTH / count;
            int right = (index + 1) * WIDTH / count;
            Button button = new Button();
            button.setId(this.idPrefix + "_structure_" + substructure.id());
            button.setText(substructure.title());
            button.setOnClick(event -> selectStructure(substructure.id()));
            button.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(left)
                    .top(0)
                    .width(right - left)
                    .height(STRUCTURE_SELECTOR_HEIGHT));
            selector.addChild(button);
        }
        return selector;
    }

    private UIElement createPreviewHost() {
        UIElement host = new UIElement();
        host.setId(this.idPrefix + "_preview_host");
        host.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(STRUCTURE_SELECTOR_HEIGHT)
                .width(WIDTH)
                .height(PREVIEW_HEIGHT));
        host.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        return host;
    }

    private ScrollerView createHorizontalScroller(String id, int top, int height) {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(id);
        scroller.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(top)
                .width(WIDTH)
                .height(height));
        scroller.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        scroller.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        scroller.viewContainer(viewContainer -> viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .height(height)));
        return scroller;
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
        refreshCandidateControls();
        refreshRecipeSlots();
    }

    private void replacePreview(PreviewSelection selection) {
        StructurePreviewUi next = createPreview(selection);
        StructurePreviewUi previous = this.previewUi;
        try {
            if (!this.previewHost.removeChild(previous.panel())) {
                throw new IllegalStateException("Multiblock XEI preview panel disappeared before replacement");
            }
        } catch (RuntimeException | Error failure) {
            markRemoved();
            releaseUnattached(next.panel(), failure);
            throw failure;
        }
        try {
            this.previewHost.addChild(next.panel());
            this.previewUi = next;
            refreshCandidateControls();
            refreshRecipeSlots();
        } catch (RuntimeException | Error failure) {
            markRemoved();
            if (next.panel().getParent() == this.previewHost) {
                try {
                    this.previewHost.removeChild(next.panel());
                } catch (RuntimeException | Error removalFailure) {
                    if (failure != removalFailure) {
                        failure.addSuppressed(removalFailure);
                    }
                }
            } else {
                releaseUnattached(next.panel(), failure);
            }
            throw failure;
        }
    }

    private void refreshCandidateControls() {
        this.candidateControls.clearAllScrollViewChildren();
        int ordinal = 0;
        for (PreviewPredicateSnapshot predicate : candidatePredicates().values()) {
            int candidateOrdinal = ordinal++;
            Button button = new Button();
            button.setId(this.idPrefix + "_candidate_" + predicate.key().sourceLayer() + "_" +
                    predicate.key().y() + "_" + predicate.key().x());
            button.setText("C" + candidateOrdinal + ":" + predicate.selectedCandidateIndex());
            button.style(style -> style.tooltips(candidateTooltip(predicate)));
            button.setOnClick(event -> selectCandidate(
                    predicate.key(),
                    (predicate.selectedCandidateIndex() + 1) % predicate.candidates().size()));
            button.layout(layout -> layout.width(58).height(CANDIDATE_HEIGHT));
            this.candidateControls.addScrollViewChild(button);
        }
    }

    private void refreshRecipeSlots() {
        this.recipeSlots.clearAllScrollViewChildren();
        List<MultiblockXeiIngredient> ingredients = MultiblockXeiIngredient.from(this.previewUi.session().recipeView());
        for (int index = 0; index < ingredients.size(); index++) {
            this.recipeSlots.addScrollViewChild(recipeEntry(ingredients.get(index), index));
        }
    }

    private UIElement recipeEntry(MultiblockXeiIngredient ingredient, int index) {
        PreviewMaterial material = ingredient.material();
        int amount = Math.toIntExact(material.amount());
        ItemStack displayStack = material.key().toStack(1);

        UIElement entry = new UIElement();
        entry.setId(this.idPrefix + "_recipe_" + ingredient.io().name().toLowerCase() + "_" + index);
        entry.layout(layout -> layout.width(52).height(20));

        ItemSlot slot = new ItemSlot();
        slot.setItem(displayStack);
        slot.xeiRecipeIngredient(ingredient.io());
        slot.xeiRecipeSlot(ingredient.io(), 1.0f, amount, Stream.of(displayStack.copy()));
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0));

        Label amountLabel = new Label();
        amountLabel.setText(Component.literal("x" + material.amount()));
        amountLabel.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        amountLabel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(19)
                .top(0)
                .width(33)
                .height(18));
        entry.addChildren(slot, amountLabel);
        return entry;
    }

    private Map<PreviewPredicateKey, PreviewPredicateSnapshot> candidatePredicates() {
        Map<PreviewPredicateKey, PreviewPredicateSnapshot> predicates = new LinkedHashMap<>();
        for (PreviewCellSnapshot cell : this.previewUi.session().snapshot().cells()) {
            PreviewPredicateSnapshot predicate = cell.predicate();
            if (predicate.candidates().size() < 2) {
                continue;
            }
            PreviewPredicateSnapshot previous = predicates.putIfAbsent(predicate.key(), predicate);
            if (previous != null && !previous.equals(predicate)) {
                throw new IllegalStateException("Repeated preview predicate resolved inconsistently: " + predicate.key());
            }
        }
        return Collections.unmodifiableMap(predicates);
    }

    private static Component[] candidateTooltip(PreviewPredicateSnapshot predicate) {
        PreviewCandidate selected = predicate.selectedCandidate().orElseThrow();
        Component selectedName = selected.placementKey()
                .<Component>map(key -> key.getDisplayName().copy())
                .orElseGet(() -> Component.translatable("block.minecraft.air"));
        return new Component[] {
                Component.literal(predicate.key().sourceLayer() + "/" + predicate.key().y() + "/" +
                        predicate.key().x()),
                selectedName };
    }

    private void requireActive() {
        if (this.removed) {
            throw new IllegalStateException("Multiblock XEI composition has already been removed");
        }
    }

    private void markRemoved() {
        this.removed = true;
    }

    private static void releaseUnattached(UIElement element, Throwable firstFailure) {
        UIElement disposalRoot = new UIElement();
        try {
            disposalRoot.addChild(element);
            disposalRoot.removeChild(element);
        } catch (RuntimeException | Error releaseFailure) {
            if (firstFailure != releaseFailure) {
                firstFailure.addSuppressed(releaseFailure);
            }
        }
    }

    /**
     * Root callback makes stale transfer sources fail before reading an already released Scene/session tree.
     */
    private static final class CompositionRoot extends UIElement {

        private final Runnable removalCallback;
        private boolean removed;

        private CompositionRoot(Runnable removalCallback) {
            this.removalCallback = removalCallback;
        }

        @Override
        protected void onRemoved() {
            if (this.removed) {
                return;
            }
            this.removed = true;
            this.removalCallback.run();
            super.onRemoved();
        }
    }
}
