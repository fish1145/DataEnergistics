package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus.Entry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.List;
import java.util.UUID;

/** Asset-free CLOSED evidence that survives physical-core route replacement. No timeout discards this history. */
public final class ReusableCustodyArchive {

    private final Object2ObjectLinkedOpenHashMap<UUID, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
    private final ReusableCustodyIndex index = new ReusableCustodyIndex();

    public void retain(List<Entry> acknowledged) {
        for (Entry entry : acknowledged) {
            if (!entry.settlementAcknowledged()) {
                throw new IllegalArgumentException("A custody archive cannot replace live session assets");
            }
            Entry previous = entries.putIfAbsent(entry.sessionId(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalStateException("Closed reusable custody changed after archival");
            }
            if (previous == null) index.record(entry);
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public ReusableCraftingCustodyCensus census(String cpuOwner) {
        return index.census(cpuOwner);
    }

    public ListTag writeToTag() {
        ListTag result = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("session", entry.sessionId());
            tag.putUUID("job", entry.jobId());
            tag.putString("owner", entry.cpuOwner());
            tag.putString("target", entry.targetIdentity());
            tag.putLong("accepted", entry.accepted());
            result.add(tag);
        }
        return result;
    }

    public static ReusableCustodyArchive readFromTag(ListTag encoded, String targetPrefix) {
        ReusableCustodyArchive result = new ReusableCustodyArchive();
        for (Tag raw : encoded) {
            if (!(raw instanceof CompoundTag tag) || !tag.hasUUID("session") || !tag.hasUUID("job") ||
                    !tag.contains("owner", Tag.TAG_STRING) || !tag.contains("target", Tag.TAG_STRING) || !tag.contains("accepted", Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Malformed closed reusable custody archive");
            }
            Entry entry = new Entry(tag.getUUID("session"), tag.getUUID("job"), tag.getString("owner"),
                    tag.getString("target"), tag.getLong("accepted"), true);
            if (!entry.targetIdentity().startsWith(targetPrefix) || result.entries.putIfAbsent(entry.sessionId(), entry) != null) {
                throw new IllegalArgumentException("Closed reusable custody archive has a foreign target or duplicate session");
            }
            result.index.record(entry);
        }
        return result;
    }
}
