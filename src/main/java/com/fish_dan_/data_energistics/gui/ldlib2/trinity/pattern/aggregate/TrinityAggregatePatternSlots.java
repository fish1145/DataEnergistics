package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.client.screen.trinity.TrinityPatternSearchMode;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotAction;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Fixed 9 by 8 client viewport over the server-authoritative aggregate pattern catalog.
 */
final class TrinityAggregatePatternSlots extends BindableUIElement<TrinityPatternCatalogView> {

    private static final int SLOT_SIZE = 18;
    private static final int MAX_CACHED_PAGES = 16;

    private final long generation;
    private final Level level;
    private final IntConsumer pageRequest;
    private final TrinityPatternSlotActionSender slotActionSender;
    private final TrinityAggregatePatternSearchIndex searchIndex;
    private final List<LocalSlot> localSlots = new ArrayList<>(TrinityPatternCatalogView.PAGE_SIZE);
    private final int[] displayedGlobalSlots = new int[TrinityPatternCatalogView.PAGE_SIZE];
    private final Map<Integer, TrinityPatternCatalogView> pageCache = new LinkedHashMap<>(MAX_CACHED_PAGES, 0.75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, TrinityPatternCatalogView> eldest) {
            return size() > MAX_CACHED_PAGES;
        }
    };
    private final List<SearchHit> searchHits = new ArrayList<>();

    private TrinityPatternCatalogView value = TrinityPatternCatalogView.EMPTY;
    private TrinityPatternSearchMode searchMode = TrinityPatternSearchMode.INPUT_OUTPUT;
    private String query = "";
    private Language language = Language.getInstance();
    private @Nullable Scroller.Vertical scrollbar;
    private @Nullable Button searchModeButton;
    private int physicalFirstGlobalSlot;
    private int requestedFirstGlobalSlot = -1;
    private int searchFirstResult;
    private int scanCoveredUntil;
    private boolean searchComplete = true;

    TrinityAggregatePatternSlots(String id,
                                 long generation,
                                 Level level,
                                 IntConsumer pageRequest,
                                 TrinityPatternSlotActionSender slotActionSender) {
        this.generation = generation;
        this.level = level;
        this.pageRequest = pageRequest;
        this.slotActionSender = slotActionSender;
        this.searchIndex = new TrinityAggregatePatternSearchIndex(level);
        Arrays.fill(this.displayedGlobalSlots, -1);
        setId(id);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(2)
                .top(2)
                .width(TrinityPatternCatalogView.COLUMN_COUNT * SLOT_SIZE)
                .height(TrinityPatternCatalogView.ROW_COUNT * SLOT_SIZE));
        for (int index = 0; index < TrinityPatternCatalogView.PAGE_SIZE; index++) {
            LocalSlot localSlot = new LocalSlot();
            ItemSlot itemSlot = new PatternDisplaySlot(localSlot, index);
            itemSlot.setId(id + "_" + index);
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
        internalSetup();
    }

    void bindControls(Scroller.Vertical scrollbar, TextField search, Button searchModeButton) {
        this.scrollbar = scrollbar;
        this.searchModeButton = searchModeButton;
        scrollbar.setOnValueChanged(this::setNormalizedPosition);
        search.setTextResponder(this::setQuery);
        searchModeButton.setOnClick(event -> cycleSearchMode());
        updateSearchModeTooltip();
        updateScrollbar(0, 0);
    }

    @Override
    public TrinityPatternCatalogView getValue() {
        return this.value;
    }

    @Override
    public TrinityAggregatePatternSlots setValue(@Nullable TrinityPatternCatalogView value, boolean notify) {
        TrinityPatternCatalogView next = value == null ? TrinityPatternCatalogView.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }

        boolean catalogChanged = !sameCatalog(this.value, next);
        this.value = next;
        if (catalogChanged) {
            resetCatalog(next);
        } else {
            cachePage(next);
            if (next.firstGlobalSlot() == this.requestedFirstGlobalSlot) {
                this.requestedFirstGlobalSlot = -1;
            }
            if (hasQuery()) {
                continueSearch();
            } else {
                this.physicalFirstGlobalSlot = next.firstGlobalSlot();
                showPhysicalPage(next);
            }
        }
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

    private void resetCatalog(TrinityPatternCatalogView next) {
        this.pageCache.clear();
        this.searchHits.clear();
        this.searchIndex.clear();
        this.requestedFirstGlobalSlot = -1;
        this.physicalFirstGlobalSlot = TrinityPatternCatalogView.normalizeFirstGlobalSlot(
                this.physicalFirstGlobalSlot,
                next.slotCount());
        cachePage(next);
        if (hasQuery()) {
            restartSearch();
        } else {
            this.physicalFirstGlobalSlot = next.firstGlobalSlot();
            showPhysicalPage(next);
        }
    }

    private static boolean sameCatalog(TrinityPatternCatalogView left, TrinityPatternCatalogView right) {
        return left.layoutRevision() == right.layoutRevision() &&
                left.catalogRevision() == right.catalogRevision() &&
                left.slotCount() == right.slotCount();
    }

    private void cachePage(TrinityPatternCatalogView page) {
        this.pageCache.put(page.firstGlobalSlot(), page);
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
        updateSearchModeTooltip();
        if (hasQuery()) {
            restartSearch();
        }
    }

    private void updateSearchModeTooltip() {
        if (this.searchModeButton == null) {
            return;
        }
        Component tooltip = Component.translatable(switch (this.searchMode) {
            case INPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.input";
            case OUTPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.output";
            case INPUT_OUTPUT -> "button.data_energistics.trinity_data_core.pattern.search_mode.input_output";
        });
        this.searchModeButton.text.style(style -> style.tooltips(tooltip));
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
        this.searchComplete = this.value.slotCount() == 0;
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
                    this.value.slotCount());
            TrinityPatternCatalogView page = this.pageCache.get(requested);
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
            this.searchComplete = this.scanCoveredUntil >= this.value.slotCount();
            refreshSearchResults();
        }
        refreshSearchResults();
    }

    private void showOrRequestPhysicalPage() {
        int first = TrinityPatternCatalogView.normalizeFirstGlobalSlot(
                this.physicalFirstGlobalSlot,
                this.value.slotCount());
        this.physicalFirstGlobalSlot = first;
        TrinityPatternCatalogView cached = this.pageCache.get(first);
        if (cached != null) {
            showPhysicalPage(cached);
        } else {
            clearDisplayedSlots();
            updateScrollbar(first, Math.max(0, this.value.slotCount() - TrinityPatternCatalogView.PAGE_SIZE));
        }
        requestPage(first);
    }

    private void showPhysicalPage(TrinityPatternCatalogView page) {
        clearDisplayedSlots();
        for (int index = 0; index < page.patterns().size(); index++) {
            int globalSlot = page.firstGlobalSlot() + index;
            this.displayedGlobalSlots[index] = globalSlot;
            this.localSlots.get(index).set(page.patterns().get(index).copy());
        }
        int maximum = Math.max(0, page.slotCount() - TrinityPatternCatalogView.PAGE_SIZE);
        updateScrollbar(page.firstGlobalSlot(), maximum);
    }

    private void refreshSearchResults() {
        int maximum = Math.max(0, this.searchHits.size() - TrinityPatternCatalogView.PAGE_SIZE);
        this.searchFirstResult = Math.clamp(this.searchFirstResult, 0, maximum);
        clearDisplayedSlots();
        int end = Math.min(
                this.searchHits.size(),
                Math.addExact(this.searchFirstResult, TrinityPatternCatalogView.PAGE_SIZE));
        for (int resultIndex = this.searchFirstResult; resultIndex < end; resultIndex++) {
            int viewIndex = resultIndex - this.searchFirstResult;
            SearchHit hit = this.searchHits.get(resultIndex);
            this.displayedGlobalSlots[viewIndex] = hit.globalSlot();
            this.localSlots.get(viewIndex).set(hit.pattern().copy());
        }
        updateScrollbar(this.searchFirstResult, maximum);
    }

    private void clearDisplayedSlots() {
        Arrays.fill(this.displayedGlobalSlots, -1);
        for (LocalSlot localSlot : this.localSlots) {
            localSlot.set(ItemStack.EMPTY);
        }
    }

    private void setNormalizedPosition(float normalized) {
        if (!this.level.isClientSide()) {
            return;
        }
        if (hasQuery()) {
            int maximum = Math.max(0, this.searchHits.size() - TrinityPatternCatalogView.PAGE_SIZE);
            int requested = Math.round(Math.clamp(normalized, 0.0F, 1.0F) * maximum);
            if (requested != this.searchFirstResult) {
                this.searchFirstResult = requested;
                refreshSearchResults();
            }
            return;
        }

        int maximum = Math.max(0, this.value.slotCount() - TrinityPatternCatalogView.PAGE_SIZE);
        int requested = Math.round(Math.clamp(normalized, 0.0F, 1.0F) * maximum);
        if (requested == this.physicalFirstGlobalSlot) {
            return;
        }
        this.physicalFirstGlobalSlot = requested;
        showOrRequestPhysicalPage();
    }

    private void updateScrollbar(int position, int maximum) {
        if (this.scrollbar == null) {
            return;
        }
        boolean scrollable = maximum > 0;
        this.scrollbar.setActive(scrollable);
        this.scrollbar.setAllowHitTest(scrollable);
        this.scrollbar.setValue(scrollable ? (float) position / maximum : 0.0F, false);
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
        if (!this.level.isClientSide()) {
            return;
        }
        int globalSlot = this.displayedGlobalSlots[viewIndex];
        if (globalSlot < 0) {
            return;
        }

        TrinityPatternSlotAction action;
        if (event.button == 0 && DataEnergisticsClientBridgeAccess.get().isShiftDown()) {
            action = TrinityPatternSlotAction.QUICK_MOVE;
        } else if (event.button == 0) {
            action = TrinityPatternSlotAction.PRIMARY;
        } else if (event.button == 1) {
            action = TrinityPatternSlotAction.SECONDARY;
        } else {
            return;
        }
        this.slotActionSender.send(
                this.generation,
                this.value.layoutRevision(),
                this.value.catalogRevision(),
                globalSlot,
                action);
    }

    private final class PatternDisplaySlot extends ItemSlot {

        private final int viewIndex;

        private PatternDisplaySlot(LocalSlot slot, int viewIndex) {
            super(slot);
            this.viewIndex = viewIndex;
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            sendSlotAction(this.viewIndex, event);
        }
    }

    private record SearchHit(int globalSlot, ItemStack pattern) {}
}
