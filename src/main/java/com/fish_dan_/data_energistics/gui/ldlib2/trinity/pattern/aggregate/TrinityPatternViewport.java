package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

/** Client scroll intent and bounded page cache; incoming pages never overwrite an established viewport position. */
final class TrinityPatternViewport {

    private static final int MAX_CACHED_PAGES = 16;
    private final Int2ObjectLinkedOpenHashMap<TrinityPatternCatalogView> pages = new Int2ObjectLinkedOpenHashMap<>();
    private TrinityPatternCatalogView value = TrinityPatternCatalogView.EMPTY;
    private boolean initialized;
    private int firstRow;

    TrinityPatternCatalogView value() {
        return this.value;
    }

    /** Accepts a current-generation server snapshot and reports catalog changes that invalidate search results. */
    boolean accept(TrinityPatternCatalogView page) {
        boolean changed = this.value.layoutRevision() != page.layoutRevision() ||
                this.value.catalogRevision() != page.catalogRevision() || this.value.slotCount() != page.slotCount();
        if (changed) this.pages.clear();
        this.value = page;
        if (!this.initialized) {
            this.firstRow = page.firstGlobalSlot() / TrinityPatternCatalogView.COLUMN_COUNT;
            this.initialized = true;
        }
        this.firstRow = Math.min(this.firstRow, maximumRow(page.slotCount()));
        this.pages.putAndMoveToLast(page.firstGlobalSlot(), page);
        if (this.pages.size() > MAX_CACHED_PAGES) this.pages.removeFirst();
        return changed;
    }

    int firstSlot() {
        return this.firstRow * TrinityPatternCatalogView.COLUMN_COUNT;
    }

    /** The protocol still asks for a full bounded page, including when the visible final row is partial. */
    int requestedPage() {
        return TrinityPatternCatalogView.normalizeFirstGlobalSlot(firstSlot(), this.value.slotCount());
    }

    boolean setFirstSlot(int firstSlot) {
        int row = Math.clamp(firstSlot / TrinityPatternCatalogView.COLUMN_COUNT, 0, maximumRow(this.value.slotCount()));
        if (row == this.firstRow) return false;
        this.firstRow = row;
        return true;
    }

    @Nullable
    TrinityPatternCatalogView page(int firstSlot) {
        return this.pages.getAndMoveToLast(firstSlot);
    }

    /** Returns a borrowed cached stack, or null for an unloaded slot. Loaded empty slots remain distinguishable. */
    @Nullable
    ItemStack pattern(int globalSlot) {
        for (TrinityPatternCatalogView page : this.pages.values()) {
            int index = globalSlot - page.firstGlobalSlot();
            if (index >= 0 && index < page.patterns().size()) return page.patterns().get(index);
        }
        return null;
    }

    static int maximumRow(int count) {
        int rows = (int) (((long) count + TrinityPatternCatalogView.COLUMN_COUNT - 1) / TrinityPatternCatalogView.COLUMN_COUNT);
        return Math.max(0, rows - TrinityPatternCatalogView.ROW_COUNT);
    }

    static int firstSlot(float normalized, int count) {
        int row = (int) Math.round((double) Math.clamp(normalized, 0.0F, 1.0F) * maximumRow(count));
        return row * TrinityPatternCatalogView.COLUMN_COUNT;
    }

    static int scrollRows(int firstSlot, int rows, int count) {
        long row = firstSlot / TrinityPatternCatalogView.COLUMN_COUNT + (long) rows;
        return (int) Math.clamp(row, 0, maximumRow(count)) * TrinityPatternCatalogView.COLUMN_COUNT;
    }
}
