package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus.Entry;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.List;
import java.util.UUID;

/** Combines visible executor summaries without rescanning historic entries on unchanged server ticks. */
public final class ReusableCustodyAggregation {

    private final UUID loadedEpoch = UUID.randomUUID();
    private final ReusableCraftingCustodyCensus empty = new ReusableCraftingCustodyCensus(loadedEpoch, 0, true, List.of());
    private final ReusableCraftingCustodyCensus unavailable = new ReusableCraftingCustodyCensus(loadedEpoch, 0, false, List.of());
    private final Object2ObjectOpenHashMap<String, Cached> owners = new Object2ObjectOpenHashMap<>();

    public ReusableCraftingCustodyCensus census(String cpuOwner, boolean complete, List<ReusableCraftingCustodyCensus> sources) {
        boolean covered = complete;
        boolean noEntries = true;
        for (ReusableCraftingCustodyCensus source : sources) {
            covered &= source.complete();
            noEntries &= source.sessions().isEmpty();
        }
        Cached previous = owners.get(cpuOwner);
        if (previous == null && noEntries) return covered ? empty : unavailable;
        if (previous != null && previous.snapshot.complete() == covered && sameSources(previous.sources, sources)) {
            return previous.snapshot;
        }
        Object2ObjectLinkedOpenHashMap<UUID, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
        for (ReusableCraftingCustodyCensus source : sources) {
            for (Entry entry : source.sessions()) {
                if (!cpuOwner.equals(entry.cpuOwner())) {
                    throw new IllegalArgumentException("Custody source returned another CPU owner");
                }
                Entry duplicate = entries.putIfAbsent(entry.sessionId(), entry);
                if (duplicate != null && !duplicate.equals(entry)) {
                    throw new IllegalStateException("Visible reusable custody sources disagree about one session");
                }
            }
        }
        long revision = previous == null ? 0 : Math.incrementExact(previous.snapshot.revision());
        ReusableCraftingCustodyCensus snapshot = new ReusableCraftingCustodyCensus(loadedEpoch, revision, covered, List.copyOf(entries.values()));
        owners.put(cpuOwner, new Cached(List.copyOf(sources), snapshot));
        return snapshot;
    }

    private static boolean sameSources(List<ReusableCraftingCustodyCensus> previous, List<ReusableCraftingCustodyCensus> current) {
        if (previous.size() != current.size()) return false;
        for (int index = 0; index < previous.size(); index++) {
            if (previous.get(index) != current.get(index)) return false;
        }
        return true;
    }

    private record Cached(List<ReusableCraftingCustodyCensus> sources, ReusableCraftingCustodyCensus snapshot) {}
}
