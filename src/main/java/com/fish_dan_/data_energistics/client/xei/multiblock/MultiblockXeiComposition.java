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
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.common.multiblock.preview.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.PreviewMaterialStrip;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewPresentation;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUi;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewUiFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One independently owned, viewer-neutral Trinity preview composition.
 *
 * <p>
 * Recipe-affecting controls update the retained session and Scene. Logical-layer controls remain view-only and do
 * not publish recipe changes.
 * </p>
 */
public final class MultiblockXeiComposition implements MultiblockRecipeViewSource {

    /**
     * Width shared by JEI and EMI adapters.
     */
    public static final int WIDTH = StructurePreviewPresentation.WIDTH;
    /**
     * Height shared by JEI and EMI adapters.
     */
    public static final int HEIGHT = 232;
    /** Suffix used by the horizontal candidate rail. */
    public static final String CANDIDATES_SUFFIX = "_candidates";
    /** Prefix used by the canonical recipe-input material strip. */
    public static final String RECIPE_INPUTS_SUFFIX = "_recipe_inputs";
    /** Stable suffix used by the sole encoded output slot. */
    public static final String OWNER_OUTPUT_SUFFIX = "_owner_output";
    /** Minimum clickable width retained before named structures overflow into horizontal scrolling. */
    static final int STRUCTURE_BUTTON_MIN_WIDTH = 64;

    private static final String TRANSLATION_PREFIX = "screen.data_energistics.multiblock_preview.";
    private static final int STRUCTURE_SELECTOR_HEIGHT = 20;
    private static final int STRUCTURE_SELECTOR_CONTENT_HEIGHT = 15;
    private static final int PREVIEW_TOP = STRUCTURE_SELECTOR_HEIGHT + 2;
    private static final int PREVIEW_HEIGHT = 158;
    private static final int CANDIDATE_TOP = PREVIEW_TOP + PREVIEW_HEIGHT + 2;
    private static final int CANDIDATE_HEIGHT = 24;
    private static final int CANDIDATE_CONTENT_HEIGHT = 19;
    private static final int RECIPE_TOP = CANDIDATE_TOP + CANDIDATE_HEIGHT + 2;
    private static final int RECIPE_HEIGHT = HEIGHT - RECIPE_TOP;
    private static final int RECIPE_INPUT_WIDTH = 158;
    private static final int RECIPE_ARROW_LEFT = 161;
    private static final int OWNER_OUTPUT_LEFT = WIDTH - 18;
    private static final int ACTIVE_TEXT_COLOR = 0xFF111820;
    private static final int INACTIVE_TEXT_COLOR = 0xFFE5EDF4;

    private final MultiblockPreviewCatalog catalog;
    private final MultiblockPreviewSpec spec;
    private final StructurePreviewUiFactory previewFactory;
    private final boolean logicalClient;
    private final String idPrefix;
    private final List<String> structureKeys;
    private final Map<String, Button> structureButtons = new LinkedHashMap<>();
    private final CompositionRoot root;
    private final UIElement previewHost;
    private final ScrollerView candidateControls;
    private final PreviewMaterialStrip recipeInputs;
    private final PreviewMaterial ownerOutputMaterial;
    private final ItemSlot ownerOutput;
    private final ModularUI modularUI;
    private StructurePreviewUi previewUi;
    private Consumer<RecipeChange> recipeChangeListener = change -> {};
    private boolean recipeChangeListenerRegistered;
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
        this.candidateControls = createHorizontalScroller(
                idPrefix + CANDIDATES_SUFFIX,
                CANDIDATE_TOP,
                CANDIDATE_HEIGHT,
                CANDIDATE_CONTENT_HEIGHT);
        this.previewUi = createPreview(initialSelection);
        this.recipeInputs = createRecipeInputs();
        this.ownerOutputMaterial = this.previewUi.session().recipeView().output();
        this.ownerOutput = createOwnerOutput();
        this.root.addChildren(
                createStructureSelector(),
                this.previewHost,
                this.candidateControls,
                createRecipeStrip());
        this.previewHost.addChild(this.previewUi.panel());
        refreshStructureSelector();
        refreshCandidateControls();
        refreshRecipeInputs();
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
     * Installs the sole listener notified after a recipe-affecting selection refresh completes.
     */
    public void setRecipeChangeListener(Consumer<RecipeChange> recipeChangeListener) {
        requireActive();
        if (recipeChangeListener == null) {
            throw new IllegalArgumentException("Multiblock XEI recipe change listener cannot be null");
        }
        if (this.recipeChangeListenerRegistered) {
            throw new IllegalStateException("Multiblock XEI recipe change listener can only be registered once");
        }
        this.recipeChangeListener = recipeChangeListener;
        this.recipeChangeListenerRegistered = true;
    }

    /**
     * Returns whether this composition still owns a live UI/session tree.
     */
    public boolean isActive() {
        return !this.removed;
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
        this.previewUi.panel().selectVariant(variantIndex);
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
        this.previewUi.panel().selectCandidate(predicateKey, candidateIndex);
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

    private ScrollerView createStructureSelector() {
        ScrollerView selector = createHorizontalScroller(
                this.idPrefix + "_structures",
                0,
                STRUCTURE_SELECTOR_HEIGHT,
                STRUCTURE_SELECTOR_CONTENT_HEIGHT);
        int count = this.spec.substructures().size();
        boolean fillViewport = count <= WIDTH / STRUCTURE_BUTTON_MIN_WIDTH;
        for (int index = 0; index < count; index++) {
            SubstructurePreviewSpec substructure = this.spec.substructures().get(index);
            int buttonWidth = fillViewport ?
                    (index + 1) * WIDTH / count - index * WIDTH / count :
                    STRUCTURE_BUTTON_MIN_WIDTH;
            Button button = new Button();
            button.setId(this.idPrefix + "_structure_" + substructure.id());
            button.setText(substructure.title());
            button.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .fontSize(8.0f)
                    .textWrap(TextWrap.HOVER_ROLL)
                    .textAlignHorizontal(Horizontal.CENTER)
                    .textAlignVertical(Vertical.CENTER)
                    .textShadow(false));
            button.text.setOverflowVisible(false);
            button.text.getLayout().flex(1);
            button.setOverflowVisible(false);
            button.setOnClick(event -> selectStructure(substructure.id()));
            button.layout(layout -> layout.width(buttonWidth).height(STRUCTURE_SELECTOR_CONTENT_HEIGHT));
            if (this.structureButtons.putIfAbsent(substructure.id(), button) != null) {
                throw new IllegalStateException("Repeated multiblock structure selector id " + substructure.id());
            }
            selector.addScrollViewChild(button);
        }
        return selector;
    }

    private UIElement createPreviewHost() {
        UIElement host = new UIElement();
        host.setId(this.idPrefix + "_preview_host");
        host.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(PREVIEW_TOP)
                .width(WIDTH)
                .height(PREVIEW_HEIGHT));
        host.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        return host;
    }

    private ScrollerView createHorizontalScroller(String id, int top, int height, int contentHeight) {
        ScrollerView scroller = new ScrollerView();
        scroller.setId(id);
        scroller.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(top)
                .width(WIDTH)
                .height(height));
        scroller.getStyle().backgroundTexture(IGuiTexture.EMPTY);
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.HORIZONTAL)
                .horizontalScrollDisplay(ScrollDisplay.AUTO)
                .verticalScrollDisplay(ScrollDisplay.NEVER)
                .scrollerViewStyle(0));
        scroller.viewPort(viewPort -> {
            viewPort.layout(layout -> layout.paddingAll(0));
            viewPort.getStyle().backgroundTexture(Sprites.BORDER);
        });
        scroller.viewContainer(viewContainer -> viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .height(contentHeight)));
        return scroller;
    }

    private PreviewMaterialStrip createRecipeInputs() {
        PreviewMaterialStrip inputs = new PreviewMaterialStrip(
                this.idPrefix + RECIPE_INPUTS_SUFFIX,
                IngredientIO.INPUT);
        inputs.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(RECIPE_INPUT_WIDTH)
                .height(RECIPE_HEIGHT));
        return inputs;
    }

    private ItemSlot createOwnerOutput() {
        int amount = xeiAmount(this.ownerOutputMaterial);
        ItemStack displayStack = this.ownerOutputMaterial.key().toStack(amount);
        ItemSlot slot = new ItemSlot();
        slot.setId(this.idPrefix + OWNER_OUTPUT_SUFFIX);
        slot.setItem(displayStack);
        slot.xeiRecipeSlot(IngredientIO.OUTPUT, 1.0f);
        slot.style(style -> style.tooltips(
                Component.translatable(TRANSLATION_PREFIX + "owner_output"),
                Component.translatable(TRANSLATION_PREFIX + "owner_output.hint")));
        slot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(OWNER_OUTPUT_LEFT)
                .top(2)
                .width(18)
                .height(18));
        return slot;
    }

    private UIElement createRecipeStrip() {
        UIElement strip = new UIElement();
        strip.setId(this.idPrefix + "_recipe_strip");
        strip.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(RECIPE_TOP)
                .width(WIDTH)
                .height(RECIPE_HEIGHT));
        strip.getStyle().backgroundTexture(Sprites.BORDER);

        UIElement arrow = new UIElement();
        arrow.setId(this.idPrefix + "_recipe_arrow");
        arrow.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(RECIPE_ARROW_LEFT)
                .top(5)
                .width(12)
                .height(12));
        arrow.style(style -> style.backgroundTexture(Icons.RIGHT_ARROW_NO_BAR));
        strip.addChildren(this.recipeInputs, arrow, this.ownerOutput);
        return strip;
    }

    private StructurePreviewUi createPreview(PreviewSelection selection) {
        StructurePreviewUi created = this.previewFactory.create(
                this.spec,
                selection,
                this.structureKeys,
                this.idPrefix + "_preview",
                this.logicalClient,
                StructurePreviewPresentation.XEI);
        created.panel().setSelectionChangeListener(this::onSelectionChanged);
        return created;
    }

    private void onSelectionChanged(PreviewSelection selection) {
        if (!selection.equals(this.previewUi.session().selection())) {
            throw new IllegalStateException("Multiblock XEI panel published a selection other than its session state");
        }
        refreshStructureSelector();
        refreshCandidateControls();
        boolean widgetPoolGrew = refreshRecipeInputs();
        MultiblockRecipeView view = currentRecipeView();
        this.recipeChangeListener.accept(new RecipeChange(view.projectionFingerprint(), widgetPoolGrew));
    }

    private void refreshStructureSelector() {
        String activeStructure = this.previewUi.session().structureKey();
        for (Map.Entry<String, Button> entry : this.structureButtons.entrySet()) {
            boolean selected = entry.getKey().equals(activeStructure);
            Button button = entry.getValue();
            button.buttonStyle(style -> {
                if (selected) {
                    style.baseTexture(Sprites.RECT_RD_LIGHT)
                            .hoverTexture(Sprites.RECT_RD_LIGHT)
                            .pressedTexture(Sprites.RECT_RD);
                } else {
                    style.baseTexture(Sprites.RECT_RD_DARK)
                            .hoverTexture(Sprites.RECT_RD)
                            .pressedTexture(Sprites.RECT_RD_DARK);
                }
            });
            button.textStyle(style -> style.textColor(selected ? ACTIVE_TEXT_COLOR : INACTIVE_TEXT_COLOR));
        }
    }

    private void refreshCandidateControls() {
        this.candidateControls.clearAllScrollViewChildren();
        Map<PreviewPredicateKey, PreviewPredicateSnapshot> predicates = candidatePredicates();
        if (predicates.isEmpty()) {
            this.candidateControls.addScrollViewChild(emptyCandidateLabel());
            return;
        }
        for (PreviewPredicateSnapshot predicate : predicates.values()) {
            PreviewCandidate selected = predicate.selectedCandidate().orElseThrow();
            ItemStack displayStack = selected.placementKey()
                    .map(key -> key.toStack(1))
                    .orElse(ItemStack.EMPTY);
            Button button = new Button();
            button.setId(this.idPrefix + "_candidate_" + predicate.key().sourceLayer() + "_" +
                    predicate.key().y() + "_" + predicate.key().x());
            button.setText(Component.translatable(
                    TRANSLATION_PREFIX + "candidate.value",
                    predicate.selectedCandidateIndex() + 1,
                    predicate.candidates().size()));
            button.addPreIcon(new ItemStackTexture(displayStack));
            button.textStyle(style -> style
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .fontSize(7.5f)
                    .textAlignHorizontal(Horizontal.CENTER)
                    .textAlignVertical(Vertical.CENTER)
                    .textShadow(false));
            button.text.setOverflowVisible(false);
            button.text.getLayout().flex(1);
            button.style(style -> style.tooltips(candidateTooltip(predicate)));
            button.setOnClick(event -> selectCandidate(
                    predicate.key(),
                    (predicate.selectedCandidateIndex() + 1) % predicate.candidates().size()));
            button.layout(layout -> layout.width(54).height(CANDIDATE_CONTENT_HEIGHT));
            this.candidateControls.addScrollViewChild(button);
        }
    }

    private Label emptyCandidateLabel() {
        Label label = new Label();
        label.setText(Component.translatable(TRANSLATION_PREFIX + "candidate.none"));
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(7.5f)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER)
                .textShadow(false));
        label.layout(layout -> layout.width(WIDTH).height(CANDIDATE_CONTENT_HEIGHT));
        return label;
    }

    private boolean refreshRecipeInputs() {
        MultiblockRecipeView view = this.previewUi.session().recipeView();
        if (!this.ownerOutputMaterial.equals(view.output())) {
            throw new IllegalStateException("Multiblock XEI encoded output changed within one controller recipe");
        }
        return this.recipeInputs.setRecipeInputs(view.inputs());
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
                Component.translatable(TRANSLATION_PREFIX + "candidate", selectedName),
                Component.translatable(
                        TRANSLATION_PREFIX + "candidate.position",
                        predicate.key().sourceLayer(),
                        predicate.key().y(),
                        predicate.key().x()) };
    }

    private static int xeiAmount(PreviewMaterial material) {
        if (material.amount() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "XEI material amount exceeds the supported int range: " + material.amount());
        }
        return (int) material.amount();
    }

    private void requireActive() {
        if (this.removed) {
            throw new IllegalStateException("Multiblock XEI composition has already been removed");
        }
    }

    private void markRemoved() {
        this.removed = true;
    }

    /**
     * Immutable viewer-refresh event produced after one valid recipe-affecting selection.
     *
     * @param projectionFingerprint identity of the newly active recipe projection
     * @param widgetPoolGrew        whether the stable XEI input slot pool gained entries
     */
    public record RecipeChange(ProjectionFingerprint projectionFingerprint, boolean widgetPoolGrew) {

        public RecipeChange {
            if (projectionFingerprint == null) {
                throw new IllegalArgumentException("Multiblock XEI recipe change fingerprint cannot be null");
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
