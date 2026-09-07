package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus.Entry;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Server-thread derived owner index. Only real accepted-history/acknowledgement changes invalidate snapshots. */
public final class ReusableCustodyIndex {

    private final UUID loadedEpoch = UUID.randomUUID();
    private final ReusableCraftingCustodyCensus empty = new ReusableCraftingCustodyCensus(loadedEpoch, 0, true, List.of());
    private final Object2ObjectOpenHashMap<String, Owner> owners = new Object2ObjectOpenHashMap<>();

    public void record(Entry entry) {
        Owner owner = owners.computeIfAbsent(entry.cpuOwner(), ignored -> new Owner());
        Entry previous = owner.entries.put(entry.sessionId(), entry);
        if (!entry.equals(previous)) {
            owner.revision = Math.incrementExact(owner.revision);
            owner.snapshot = null;
        }
    }

    public ReusableCraftingCustodyCensus census(String cpuOwner) {
        Owner owner = owners.get(cpuOwner);
        if (owner == null) return empty;
        if (owner.snapshot == null) {
            owner.snapshot = new ReusableCraftingCustodyCensus(loadedEpoch, owner.revision, true, List.copyOf(owner.entries.values()));
        }
        return owner.snapshot;
    }

    private static final class Owner {

        private final Object2ObjectLinkedOpenHashMap<UUID, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
        private long revision;
        private @Nullable ReusableCraftingCustodyCensus snapshot;
    }
}
