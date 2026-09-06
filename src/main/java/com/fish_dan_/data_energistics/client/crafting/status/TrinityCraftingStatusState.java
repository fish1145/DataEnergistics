package com.fish_dan_.data_energistics.client.crafting.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;
import com.fish_dan_.data_energistics.network.trinity.crafting.protocol.TrinityCraftingStatusPayload;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Assembles an ordered menu update and publishes all rows atomically, retaining their exact quantities across deltas.
 */
public final class TrinityCraftingStatusState {

    private Long2ObjectLinkedOpenHashMap<TrinityCraftingStatusEntry> entries = new Long2ObjectLinkedOpenHashMap<>();
    private final List<TrinityCraftingStatusEntry> pendingEntries = new ObjectArrayList<>();
    private @Nullable TrinityCraftingStatusPayload pending;
    private long sequence = -1;
    private int nextBatch;
    private boolean initialized;
    private boolean publishing;

    /** Drops exact state when a native status packet switches the screen away from the Trinity data stream. */
    public void onNativeUpdate() {
        if (!this.publishing) {
            this.entries.clear();
            this.pendingEntries.clear();
            this.pending = null;
            this.initialized = false;
        }
    }

    /**
     * Applies matching batches on the client thread. Old/duplicate updates are discarded; malformed deltas throw before
     * changing the displayed snapshot. The callback receives a full AE2 view so AE2's merge does not erase exact
     * entries.
     */
    public void receive(TrinityCraftingStatusPayload payload, Consumer<CraftingStatus> publish) {
        if (payload.sequence() < this.sequence) {
            return;
        }
        if (payload.sequence() > this.sequence) {
            this.sequence = payload.sequence();
            this.pending = null;
            this.pendingEntries.clear();
            if (payload.batchIndex() != 0 || !payload.header().full() && !this.initialized) {
                return;
            }
            this.pending = payload;
            this.nextBatch = 0;
        }
        TrinityCraftingStatusPayload first = this.pending;
        if (first == null || payload.batchIndex() < this.nextBatch) {
            return;
        }
        if (payload.batchIndex() != this.nextBatch || payload.totalEntries() != first.totalEntries() ||
                !payload.header().equals(first.header())) {
            throw new IllegalArgumentException("Inconsistent Trinity CPU status batch sequence");
        }
        this.pendingEntries.addAll(payload.entries());
        this.nextBatch++;
        if (this.pendingEntries.size() != first.totalEntries()) {
            return;
        }

        Long2ObjectLinkedOpenHashMap<TrinityCraftingStatusEntry> updated = first.header().full() ?
                new Long2ObjectLinkedOpenHashMap<>() : new Long2ObjectLinkedOpenHashMap<>(this.entries);
        LongOpenHashSet serials = new LongOpenHashSet();
        for (TrinityCraftingStatusEntry entry : this.pendingEntries) {
            long serial = entry.getSerial();
            if (!serials.add(serial)) {
                throw new IllegalArgumentException("Duplicate Trinity CPU status serial in one update");
            }
            if (entry.isDeleted()) {
                updated.remove(serial);
                continue;
            }
            AEKey key = entry.getWhat();
            if (key == null) {
                if (!updated.containsKey(serial)) {
                    throw new IllegalArgumentException("Trinity CPU status delta refers to an unknown serial");
                }
                key = updated.get(serial).getWhat();
            } else if (updated.containsKey(serial) && !key.equals(updated.get(serial).getWhat())) {
                throw new IllegalArgumentException("Trinity CPU status serial changed its material key");
            }
            updated.put(serial, new TrinityCraftingStatusEntry(serial, key, entry.stored(), entry.active(), entry.pending()));
        }
        List<CraftingStatusEntry> rows = new ObjectArrayList<>(updated.values());
        var header = first.header();
        this.publishing = true;
        try {
            publish.accept(new CraftingStatus(true, header.elapsedTime(), header.remainingWork(), header.startWork(), rows, header.suspended()));
        } finally {
            this.publishing = false;
        }
        this.entries = updated;
        this.initialized = true;
        this.pending = null;
        this.pendingEntries.clear();
    }
}
