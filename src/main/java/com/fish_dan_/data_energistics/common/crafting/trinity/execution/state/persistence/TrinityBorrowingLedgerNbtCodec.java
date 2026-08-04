package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strict NBT codec for the ownership-preserving dynamic borrowing ledger.
 */
public final class TrinityBorrowingLedgerNbtCodec {

    private static final String SCHEMA_TAG = "schema_version";
    private static final int SCHEMA = 1;
    private static final String ENTRIES_TAG = "entries";
    private static final String KEY_TAG = "key";
    private static final String RESERVED_TAG = "reserved";
    private static final String COMMITTED_TAG = "committed";
    private static final String RELEASED_TAG = "released";
    private static final Set<String> ROOT_FIELDS = Set.of(SCHEMA_TAG, ENTRIES_TAG);
    private static final Set<String> ENTRY_FIELDS = Set.of(
            KEY_TAG,
            RESERVED_TAG,
            COMMITTED_TAG,
            RELEASED_TAG);

    private TrinityBorrowingLedgerNbtCodec() {}

    /**
     * Encodes the complete ledger history.
     *
     * @param entries    immutable borrowing balances
     * @param registries server registry lookup used by AE key codecs
     * @return strict ledger NBT
     */
    public static CompoundTag encode(Map<AEKey, TrinityBorrowingLedger.Balances> entries,
                                     HolderLookup.Provider registries) {
        if (registries == null) {
            throw new IllegalArgumentException("Trinity borrowing persistence requires registries");
        }
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_TAG, SCHEMA);
        ListTag encodedEntries = new ListTag();
        entries.forEach((key, balances) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(KEY_TAG, key.toTagGeneric(registries));
            entry.putLong(RESERVED_TAG, balances.reserved());
            entry.putLong(COMMITTED_TAG, balances.committed());
            entry.putLong(RELEASED_TAG, balances.released());
            encodedEntries.add(entry);
        });
        root.put(ENTRIES_TAG, encodedEntries);
        return root;
    }

    /**
     * Decodes a ledger while rejecting unknown fields, damaged types and duplicate keys.
     *
     * @param tag        strict ledger NBT
     * @param registries server registry lookup used by AE key codecs
     * @return immutable ordered borrowing balances
     */
    public static Map<AEKey, TrinityBorrowingLedger.Balances> decode(
                                                                     CompoundTag tag,
                                                                     HolderLookup.Provider registries) {
        requireFields(tag, ROOT_FIELDS, "borrowing ledger");
        requireType(tag, SCHEMA_TAG, Tag.TAG_INT, "borrowing ledger schema");
        if (tag.getInt(SCHEMA_TAG) != SCHEMA) {
            throw new IllegalArgumentException("Unsupported Trinity borrowing ledger schema");
        }
        requireType(tag, ENTRIES_TAG, Tag.TAG_LIST, "borrowing ledger entries");
        Tag rawEntries = tag.get(ENTRIES_TAG);
        if (!(rawEntries instanceof ListTag encodedEntries) ||
                (!encodedEntries.isEmpty() && encodedEntries.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("A Trinity borrowing ledger must contain compound entries");
        }

        LinkedHashMap<AEKey, TrinityBorrowingLedger.Balances> restored = new LinkedHashMap<>();
        for (Tag encoded : encodedEntries) {
            CompoundTag entry = (CompoundTag) encoded;
            requireFields(entry, ENTRY_FIELDS, "borrowing ledger entry");
            requireType(entry, KEY_TAG, Tag.TAG_COMPOUND, "borrowing ledger key");
            requireType(entry, RESERVED_TAG, Tag.TAG_LONG, "reserved borrowing amount");
            requireType(entry, COMMITTED_TAG, Tag.TAG_LONG, "committed borrowing amount");
            requireType(entry, RELEASED_TAG, Tag.TAG_LONG, "released borrowing amount");
            AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound(KEY_TAG));
            if (key == null) {
                throw new IllegalArgumentException("A Trinity borrowing ledger contains an unknown AE key");
            }
            TrinityBorrowingLedger.Balances balances = new TrinityBorrowingLedger.Balances(
                    entry.getLong(RESERVED_TAG),
                    entry.getLong(COMMITTED_TAG),
                    entry.getLong(RELEASED_TAG));
            if (balances.total() <= 0L || restored.putIfAbsent(key, balances) != null) {
                throw new IllegalArgumentException("A Trinity borrowing ledger requires unique non-empty entries");
            }
        }
        return Collections.unmodifiableMap(restored);
    }

    private static void requireFields(CompoundTag tag, Set<String> fields, String role) {
        if (tag == null || !tag.getAllKeys().equals(fields)) {
            throw new IllegalArgumentException("Unexpected or missing fields in Trinity " + role);
        }
    }

    private static void requireType(CompoundTag tag, String field, int type, String role) {
        if (!tag.contains(field, type)) {
            throw new IllegalArgumentException("Missing or damaged Trinity " + role);
        }
    }
}
