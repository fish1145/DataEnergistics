package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.AEKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Atomic client-facing snapshot of one Trinity Data Core's capacity and exact stored keys.
 */
public record TrinityDataCoreStorageView(TrinityDataCoreStorageStatus status,
                                         int firstEntry,
                                         List<Entry> entries) {

    public static final int VISIBLE_ROW_COUNT = 8;
    public static final int PAGE_SIZE = VISIBLE_ROW_COUNT;

    public static final TrinityDataCoreStorageView EMPTY = new TrinityDataCoreStorageView(
            TrinityDataCoreStorageStatus.EMPTY,
            0,
            List.of());
    public static final Codec<TrinityDataCoreStorageView> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    TrinityDataCoreStorageStatus.CODEC
                            .fieldOf("status")
                            .forGetter(TrinityDataCoreStorageView::status),
                    Codec.INT
                            .fieldOf("first_entry")
                            .forGetter(TrinityDataCoreStorageView::firstEntry),
                    Entry.CODEC.listOf()
                            .fieldOf("entries")
                            .forGetter(TrinityDataCoreStorageView::entries))
            .apply(instance, TrinityDataCoreStorageView::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreStorageView> STREAM_CODEC = StreamCodec.of(
            TrinityDataCoreStorageView::encode,
            TrinityDataCoreStorageView::decode);

    public TrinityDataCoreStorageView {
        if (firstEntry != normalizeFirstEntry(firstEntry, status.typeCount())) {
            throw new IllegalArgumentException("Trinity storage page does not start at a valid visible row");
        }
        if (entries.size() > PAGE_SIZE) {
            throw new IllegalArgumentException("Trinity storage view exceeds the synchronized entry limit");
        }
        List<Entry> sorted = new ArrayList<>(entries);
        if (sorted.contains(null)) {
            throw new IllegalArgumentException("Trinity storage view must not contain null entries");
        }
        for (int index = 0; index < sorted.size(); index++) {
            Entry entry = sorted.get(index);
            for (int previous = 0; previous < index; previous++) {
                if (!sorted.get(previous).key().equals(entry.key())) {
                    continue;
                }
                throw new IllegalArgumentException("Trinity storage view contains a duplicate AE key");
            }
        }
        if ((long) firstEntry + sorted.size() > status.typeCount()) {
            throw new IllegalArgumentException("Trinity storage page extends beyond its exact type range");
        }
        entries = List.copyOf(sorted);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TrinityDataCoreStorageView value) {
        TrinityDataCoreStorageStatus.STREAM_CODEC.encode(buffer, value.status);
        buffer.writeVarInt(value.firstEntry);
        buffer.writeVarInt(value.entries.size());
        for (Entry entry : value.entries) {
            Entry.STREAM_CODEC.encode(buffer, entry);
        }
    }

    private static TrinityDataCoreStorageView decode(RegistryFriendlyByteBuf buffer) {
        TrinityDataCoreStorageStatus status = TrinityDataCoreStorageStatus.STREAM_CODEC.decode(buffer);
        int firstEntry = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid synchronized Trinity storage entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(Entry.STREAM_CODEC.decode(buffer));
        }
        return new TrinityDataCoreStorageView(status, firstEntry, entries);
    }

    /**
     * Clamps an arbitrary client request to the first entry of the visible list.
     */
    public static int normalizeFirstEntry(int requestedFirstEntry, int entryCount) {
        if (entryCount < 0) {
            throw new IllegalArgumentException("Trinity storage entry count must not be negative");
        }
        int maximumFirstEntry = Math.max(0, entryCount - PAGE_SIZE);
        return Math.min(Math.max(0, requestedFirstEntry), maximumFirstEntry);
    }

    /**
     * One exact key amount retained as arbitrary precision through storage persistence and UI synchronization.
     */
    public record Entry(AEKey key, BigInteger amount) {

        private static final Codec<BigInteger> BIG_INTEGER_CODEC = Codec.STRING.xmap(
                BigInteger::new,
                BigInteger::toString);

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        AEKey.CODEC.fieldOf("key").forGetter(Entry::key),
                        BIG_INTEGER_CODEC.fieldOf("amount").forGetter(Entry::amount))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                Entry::encode,
                Entry::decode);

        public Entry {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity storage entry amount must be positive");
            }
        }

        private static void encode(RegistryFriendlyByteBuf buffer, Entry value) {
            AEKey.STREAM_CODEC.encode(buffer, value.key);
            buffer.writeUtf(value.amount.toString());
        }

        private static Entry decode(RegistryFriendlyByteBuf buffer) {
            @Nullable
            AEKey key = AEKey.readKey(buffer);
            if (key == null) {
                throw new IllegalArgumentException("Synchronized Trinity storage entry contains an unknown AE key");
            }
            return new Entry(key, new BigInteger(buffer.readUtf()));
        }
    }
}
