package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityPatternSearchMode;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotAction;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.crafting.pattern.EncodedPatternItem;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Fixed 9 by 8 client viewport over the server-authoritative aggregate pattern catalog.
 */
final class TrinityAggregatePatternSlots extends BindableUIElement<TrinityPatternCatalogView> {

    private static final int SLOT_SIZE = 18;
    private static final float SEARCH_FONT_SIZE = 8F;
    private static final IGuiTexture PATTERN_ROW_BACKGROUND = SpriteTexture.of("data_energistics:textures/guis/model/model.png");
    private static final IGuiTexture OCCUPIED_PATTERN_SLOT_OVERLAY = SpriteTexture.of(
            "data_energistics:textures/guis/inventory_slot.png");
    private static final IGuiTexture SEARCH_INPUT_ICON = SpriteTexture.of("data_energistics:textures/guis/model/input.png");
    private static final IGuiTexture SEARCH_OUTPUT_ICON = SpriteTexture.of("data_energistics:textures/guis/model/output.png");
    private static final IGuiTexture SEARCH_INPUT_OUTPUT_ICON = SpriteTexture.of("data_energistics:textures/guis/model/input_and_output.png");
    private static final Component SEARCH_PLACEHOLDER = Component.translatable(
            "screen.data_energistics.trinity_data_core.pattern.search_hint")
            .withStyle(style -> style.withColor(ColorPattern.WHITE.color));

    private final long generation;
    private final Level level;
    private final IntConsumer pageRequest;
    private final TrinityPatternSlotActionSender slotActionSender;
    private final TrinityPatternQuickMoveSender quickMoveSender;
    private final TrinityAggregatePatternSearchIndex searchIndex;
    private final List<LocalSlot> localSlots = new ObjectArrayList<>(TrinityPatternCatalogView.PAGE_SIZE);
    private final int[] displayedGlobalSlots = new int[TrinityPatternCatalogView.PAGE_SIZE];
    private final List<SearchHit> searchHits = new ObjectArrayList<>();
    private final IntLinkedOpenHashSet quickMoveSweepSlots = new IntLinkedOpenHashSet();

    private final TrinityPatternViewport viewport = new TrinityPatternViewport();
    private TrinityPatternSearchMode searchMode = TrinityPatternSearchMode.INPUT_OUTPUT;
    private String query = "";
    private Language language = Language.getInstance();
    private Scroller.@Nullable Vertical scrollbar;
    private @Nullable Button searchModeButton;
    private int requestedFirstGlobalSlot = -1;
    private int searchFirstResult;
    private int scanCoveredUntil;
    private boolean searchComplete = true;
    private boolean maintenanceActive;
    private boolean quickMoveSweepActive;
    private long quickMoveSweepLayoutRevision;

    TrinityAggregatePatternSlots(String id,
                                 long generation,
                                 Level level,
                                 IntConsumer pageRequest,
                                 TrinityPatternSlotActionSender slotActionSender,
                                 TrinityPatternQuickMoveSender quickMoveSender) {
        this.generation = generation;
        this.level = level;
        this.pageRequest = pageRequest;
        this.slotActionSender = slotActionSender;
        this.quickMoveSender = quickMoveSender;
        this.searchIndex = new TrinityAggregatePatternSearchIndex(level);
        Arrays.fill(this.displayedGlobalSlots, -1);
        setId(id);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(4)
                .top(6)
                .width(TrinityPatternCatalogView.COLUMN_COUNT * SLOT_SIZE)
                .height(TrinityPatternCatalogView.ROW_COUNT * SLOT_SIZE));
        for (int row = 0; row < TrinityPatternCatalogView.ROW_COUNT; row++) {
            UIElement background = new UIElement();
            background.setId(id + "_row_" + row);
            background.setAllowHitTest(false);
            background.style(style -> style.backgroundTexture(PATTERN_ROW_BACKGROUND));
            int top = row * SLOT_SIZE;
            background.layout(rowLayout -> rowLayout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(0)
                    .top(top)
                    .width(TrinityPatternCatalogView.COLUMN_COUNT * SLOT_SIZE)
                    .height(SLOT_SIZE));
            addChild(background);
        }
        for (int index = 0; index < TrinityPatternCatalogView.PAGE_SIZE; index++) {
            LocalSlot localSlot = new LocalSlot();
            ItemSlot itemSlot = new PatternDisplaySlot(localSlot, index);
            itemSlot.setId(id + "_" + index);
            itemSlot.getStyle().backgroundTexture(IGuiTexture.EMPTY);
            itemSlot.slotStyle(style -> style
                    .slotOverlay(IGuiTexture.dynamic(() -> localSlot.getItem().isEmpty() ?
                            IGuiTexture.EMPTY : OCCUPIED_PATTERN_SLOT_OVERLAY))
                    .showSlotOverlayOnlyEmpty(false));
            int column = index % TrinityPatternCatalogView.COLUMN_COUNT;
            int row = index / TrinityPatternCatalogView.COLUMN_COUNT;
            itemSlot.layout(slotLayout -> slotLayout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(column * SLOT_SIZE)
                    .top(row * SLOT_SIZE)
                    .width(SLOT_SIZE)
                    .height(SLOT_SIZE));
            this.localSlots.add(localSlot);
            addChild(itemSlot);
        }
        addEventListener(UIEvents.TICK, event -> refreshLanguage());
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        addEventListener(UIEvents.DRAG_END, this::finishQuickMoveSweep);
        internalSetup();
    }

    void bindControls(Scroller.Vertical scrollbar, TextField search, Button searchModeButton) {
        this.scrollbar = scrollbar;
        this.searchModeButton = searchModeButton;
        scrollbar.setRange(0.0F, 1.0F);
        scrollbar.setOnValueChanged(this::setNormalizedPosition);
        scrollbar.addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel, true);
        scrollbar.headButton.setOnClick(event -> scrollRows(-1));
        scrollbar.tailButton.setOnClick(event -> scrollRows(1));
        scrollbar.scrollBar.addEventListener(UIEvents.DRAG_END,
                event -> updateScrollbar(firstDisplayedSlot(), displayedEntryCount()));
        search.textFieldStyle(style -> style.fontSize(SEARCH_FONT_SIZE));
        search.setTextResponder(this::setQuery);
        updateSearchPlaceholder(search, false);
        search.addEventListener(UIEvents.FOCUS, event -> updateSearchPlaceholder(search, true));
        search.addEventListener(UIEvents.BLUR, event -> updateSearchPlaceholder(search, false));
        search.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                search.setText("");
                event.stopPropagation();
            }
        });
        searchModeButton.setOnClick(event -> cycleSearchMode());
        updateSearchModePresentation();
        updateScrollbar(firstDisplayedSlot(), displayedEntryCount());
    }

    private static void updateSearchPlaceholder(TextField search, boolean focused) {
        search.textFieldStyle(style -> style.placeholder(focused ? Component.empty() : SEARCH_PLACEHOLDER));
        if (search.getRawText().isEmpty()) {
            search.setText("", false);
        }
    }

    void setMaintenanceActive(boolean maintenanceActive) {
        this.maintenanceActive = maintenanceActive;
        if (maintenanceActive) {
            clearQuickMoveSweep();
        }
    }

    @Override
    public TrinityPatternCatalogView getValue() {
        return this.viewport.value();
    }

    @Override
    public TrinityAggregatePatternSlots setValue(@Nullable TrinityPatternCatalogView value, boolean notify) {
        TrinityPatternCatalogView next = value == null ? TrinityPatternCatalogView.EMPTY : value;
        if (this.viewport.value().equals(next)) {
            return this;
        }

        if (this.quickMoveSweepActive && this.quickMoveSweepLayoutRevision != next.layoutRevision()) {
            clearQuickMoveSweep();
        }
        boolean catalogChanged = this.viewport.accept(next);
        if (catalogChanged) resetCatalog();
        else if (hasQuery()) continueSearch();
        else showPhysicalWindow();
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onRemoved() {
        for (var dataSource : List.copyOf(getBoundDataSources())) {
            unbindDataSource(dataSource);
        }
        super.onRemoved();
    }

    private void resetCatalog() {
        this.searchHits.clear();
        this.searchIndex.clear();
        this.requestedFirstGlobalSlot = -1;
        if (hasQuery()) restartSearch();
        else showOrRequestPhysicalPage();
    }

    private void setQuery(String query) {
        if (!this.level.isClientSide()) {
            return;
        }
        if (this.query.equals(query)) {
            return;
        }
        boolean previouslySearching = hasQuery();
        this.query = query;
        if (hasQuery()) {
            restartSearch();
        } else if (previouslySearching) {
            this.searchHits.clear();
            this.searchComplete = true;
            showOrRequestPhysicalPage();
        }
    }

    private boolean hasQuery() {
        return !this.query.isBlank();
    }

    private void cycleSearchMode() {
        if (!this.level.isClientSide()) {
            return;
        }
        this.searchMode = this.searchMode.next();
        updateSearchModePresentation();
        if (hasQuery()) {
            restartSearch();
        }
    }

    private void updateSearchModePresentation() {
        if (this.searchModeButton == null) {
            return;
        }
        Component tooltip = Component.translatable(switch (this.searchMode) {
            case INPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.input";
            case OUTPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.output";
            case INPUT_OUTPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.input_output";
        });
        IGuiTexture icon = switch (this.searchMode) {
            case INPUT -> SEARCH_INPUT_ICON;
            case OUTPUT -> SEARCH_OUTPUT_ICON;
            case INPUT_OUTPUT -> SEARCH_INPUT_OUTPUT_ICON;
        };
        this.searchModeButton.text.style(style -> style.backgroundTexture(icon).tooltips(tooltip));
        this.searchModeButton.style(style -> style.tooltips(tooltip));
    }

    private void refreshLanguage() {
        if (!this.level.isClientSide()) {
            return;
        }
        Language current = Language.getInstance();
        if (current == this.language) {
            return;
        }
        this.language = current;
        this.searchIndex.clear();
        if (hasQuery()) {
            restartSearch();
        }
    }

    private void restartSearch() {
        this.searchHits.clear();
        this.searchFirstResult = 0;
        this.scanCoveredUntil = 0;
        this.searchComplete = this.viewport.value().slotCount() == 0;
        this.requestedFirstGlobalSlot = -1;
        clearDisplayedSlots();
        refreshSearchResults();
        if (!this.searchComplete) {
            continueSearch();
        }
    }

    private void continueSearch() {
        while (!this.searchComplete) {
            int requested = TrinityPatternCatalogView.normalizeFirstGlobalSlot(
                    this.scanCoveredUntil,
                    this.viewport.value().slotCount());
            TrinityPatternCatalogView page = this.viewport.page(requested);
            if (page == null) {
                requestPage(requested);
                return;
            }

            int pageEnd = Math.min(
                    page.slotCount(),
                    Math.addExact(page.firstGlobalSlot(), page.patterns().size()));
            int firstUnseen = Math.max(this.scanCoveredUntil, page.firstGlobalSlot());
            if (pageEnd <= firstUnseen) {
                this.searchComplete = true;
                break;
            }
            for (int globalSlot = firstUnseen; globalSlot < pageEnd; globalSlot++) {
                ItemStack pattern = page.patterns().get(globalSlot - page.firstGlobalSlot());
                if (!pattern.isEmpty() && this.searchIndex.matches(pattern, this.query, this.searchMode)) {
                    this.searchHits.add(new SearchHit(globalSlot, pattern.copy()));
                }
            }
            this.scanCoveredUntil = pageEnd;
            this.searchComplete = this.scanCoveredUntil >= this.viewport.value().slotCount();
            refreshSearchResults();
        }
        refreshSearchResults();
    }

    private void showOrRequestPhysicalPage() {
        showPhysicalWindow();
        requestPage(this.viewport.requestedPage());
    }

    private void showPhysicalWindow() {
        int first = this.viewport.firstSlot();
        int count = Math.min(TrinityPatternCatalogView.PAGE_SIZE, this.viewport.value().slotCount() - first);
        for (int index = 0; index < TrinityPatternCatalogView.PAGE_SIZE; index++) {
            ItemStack pattern = index < count ? this.viewport.pattern(first + index) : null;
            setDisplayedSlot(index, pattern == null ? -1 : first + index, pattern == null ? ItemStack.EMPTY : pattern);
        }
        updateScrollbar(first, this.viewport.value().slotCount());
    }

    private void refreshSearchResults() {
        int maximum = TrinityPatternViewport.maximumRow(this.searchHits.size()) * TrinityPatternCatalogView.COLUMN_COUNT;
        this.searchFirstResult = Math.min(this.searchFirstResult, maximum);
        int count = Math.min(TrinityPatternCatalogView.PAGE_SIZE, this.searchHits.size() - this.searchFirstResult);
        for (int index = 0; index < TrinityPatternCatalogView.PAGE_SIZE; index++) {
            if (index < count) {
                SearchHit hit = this.searchHits.get(this.searchFirstResult + index);
                setDisplayedSlot(index, hit.globalSlot(), hit.pattern());
            } else {
                setDisplayedSlot(index, -1, ItemStack.EMPTY);
            }
        }
        updateScrollbar(this.searchFirstResult, this.searchHits.size());
    }

    private void setDisplayedSlot(int index, int globalSlot, ItemStack pattern) {
        this.displayedGlobalSlots[index] = globalSlot;
        LocalSlot slot = this.localSlots.get(index);
        if (!ItemStack.matches(slot.getItem(), pattern)) slot.set(pattern.copy());
    }

    private void clearDisplayedSlots() {
        for (int index = 0; index < TrinityPatternCatalogView.PAGE_SIZE; index++) setDisplayedSlot(index, -1, ItemStack.EMPTY);
    }

    private int displayedEntryCount() {
        return hasQuery() ? this.searchHits.size() : this.viewport.value().slotCount();
    }

    private int firstDisplayedSlot() {
        return hasQuery() ? this.searchFirstResult : this.viewport.firstSlot();
    }

    private void onMouseWheel(UIEvent event) {
        if (event.deltaY == 0) return;
        scrollRows(event.deltaY > 0 ? -1 : 1);
        event.stopPropagation();
    }

    private void scrollRows(int rows) {
        setFirstSlot(TrinityPatternViewport.scrollRows(firstDisplayedSlot(), rows, displayedEntryCount()));
    }

    private void setNormalizedPosition(float normalized) {
        setFirstSlot(TrinityPatternViewport.firstSlot(normalized, displayedEntryCount()));
    }

    private void setFirstSlot(int requested) {
        if (!this.level.isClientSide()) return;
        if (hasQuery()) {
            if (requested != this.searchFirstResult) {
                this.searchFirstResult = requested;
                refreshSearchResults();
            }
        } else if (this.viewport.setFirstSlot(requested)) {
            showOrRequestPhysicalPage();
        }
    }

    private void updateScrollbar(int position, int count) {
        if (this.scrollbar == null) return;
        int maximumRow = TrinityPatternViewport.maximumRow(count);
        boolean scrollable = maximumRow > 0;
        this.scrollbar.setActive(scrollable);
        this.scrollbar.selfAndAllChildren().forEach(element -> element.setAllowHitTest(scrollable));
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(scrollable ? 1.0F / maximumRow : 1.0F));
        // The thumb follows the pointer while dragging; quantized rows and delayed page replies do not pull it back.
        if (!this.scrollbar.isDragging()) {
            int totalRows = maximumRow + TrinityPatternCatalogView.ROW_COUNT;
            this.scrollbar.setScrollBarSize(Math.max(8.0F, 100.0F * TrinityPatternCatalogView.ROW_COUNT / totalRows));
            this.scrollbar.setNormalizedValue(scrollable ?
                    (float) (position / TrinityPatternCatalogView.COLUMN_COUNT) / maximumRow : 0.0F, false);
        }
    }

    private void requestPage(int firstGlobalSlot) {
        if (!this.level.isClientSide()) {
            return;
        }
        if (firstGlobalSlot == this.requestedFirstGlobalSlot) {
            return;
        }
        this.requestedFirstGlobalSlot = firstGlobalSlot;
        this.pageRequest.accept(firstGlobalSlot);
    }

    private void sendSlotAction(int viewIndex, UIEvent event) {
        event.stopPropagation();
        event.hasHandler = false;
        if (!this.level.isClientSide() || this.maintenanceActive) {
            return;
        }
        int globalSlot = this.displayedGlobalSlots[viewIndex];
        if (globalSlot < 0) {
            return;
        }

        TrinityPatternSlotAction action;
        if (event.button == 0) {
            action = TrinityPatternSlotAction.PRIMARY;
        } else if (event.button == 1) {
            action = TrinityPatternSlotAction.SECONDARY;
        } else {
            return;
        }
        this.slotActionSender.send(
                this.generation,
                this.viewport.value().layoutRevision(),
                this.viewport.value().catalogRevision(),
                globalSlot,
                action);
    }

    private void beginQuickMoveSweep(int viewIndex, UIEvent event) {
        event.stopPropagation();
        event.hasHandler = false;
        if (!this.level.isClientSide() || this.maintenanceActive || event.button != 0 ||
                !DataEnergisticsClientBridgeAccess.get().isShiftDown()) {
            return;
        }
        clearQuickMoveSweep();
        this.quickMoveSweepLayoutRevision = this.viewport.value().layoutRevision();
        this.quickMoveSweepActive = addQuickMoveSweepSlot(viewIndex);
        if (this.quickMoveSweepActive) {
            startDrag(null, null);
        }
    }

    private void enterQuickMoveSweep(int viewIndex, UIEvent event) {
        if (!this.quickMoveSweepActive || event.dragHandler == null || event.dragHandler.dragSource != this ||
                this.maintenanceActive || !DataEnergisticsClientBridgeAccess.get().isShiftDown()) {
            return;
        }
        addQuickMoveSweepSlot(viewIndex);
    }

    private boolean addQuickMoveSweepSlot(int viewIndex) {
        int globalSlot = this.displayedGlobalSlots[viewIndex];
        if (globalSlot < 0 || this.localSlots.get(viewIndex).getItem().isEmpty()) {
            return false;
        }
        return this.quickMoveSweepSlots.add(globalSlot);
    }

    private void finishQuickMoveSweep(UIEvent event) {
        if (!this.quickMoveSweepActive || event.dragHandler == null || event.dragHandler.dragSource != this) {
            return;
        }
        List<Integer> selectedSlots = List.copyOf(this.quickMoveSweepSlots);
        long layoutRevision = this.quickMoveSweepLayoutRevision;
        clearQuickMoveSweep();
        if (!this.maintenanceActive && !selectedSlots.isEmpty()) {
            this.quickMoveSender.send(this.generation, layoutRevision, selectedSlots);
        }
    }

    private void clearQuickMoveSweep() {
        this.quickMoveSweepActive = false;
        this.quickMoveSweepLayoutRevision = 0L;
        this.quickMoveSweepSlots.clear();
    }

    private final class PatternDisplaySlot extends ItemSlot {

        private final int viewIndex;

        private PatternDisplaySlot(LocalSlot slot, int viewIndex) {
            super(slot);
            this.viewIndex = viewIndex;
            addEventListener(UIEvents.DRAG_ENTER, event -> enterQuickMoveSweep(this.viewIndex, event));
        }

        @Override
        public ItemStack getValue() {
            ItemStack pattern = super.getValue();
            if (!TrinityAggregatePatternSlots.this.level.isClientSide() ||
                    DataEnergisticsClientBridgeAccess.get().isShiftDown()) {
                return pattern;
            }
            if (pattern.getItem() instanceof EncodedPatternItem encodedPattern) {
                ItemStack output = encodedPattern.getOutput(pattern);
                if (!output.isEmpty()) {
                    return output;
                }
            }
            return pattern;
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            if (event.button == 0 && DataEnergisticsClientBridgeAccess.get().isShiftDown()) {
                beginQuickMoveSweep(this.viewIndex, event);
            } else {
                sendSlotAction(this.viewIndex, event);
            }
        }
    }

    private record SearchHit(int globalSlot, ItemStack pattern) {}
}
