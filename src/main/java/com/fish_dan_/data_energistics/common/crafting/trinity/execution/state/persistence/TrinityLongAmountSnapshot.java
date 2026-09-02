package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence;

import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap.BasicEntry;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps.UnmodifiableMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * Publishes primitive long-amount snapshots without exposing writable fastutil map entries.
 *
 * <p>
 * The source is captured on its owning thread. Published snapshots retain encounter order and may be shared
 * for reading once the source ownership contract has been satisfied.
 * </p>
 */
public final class TrinityLongAmountSnapshot {

    private TrinityLongAmountSnapshot() {}

    /**
     * Copies a caller-owned amount map once; subsequent source mutations cannot affect the returned snapshot.
     *
     * @param source non-null amount map captured on its owning thread
     * @return primitive read-only snapshot with immutable entries
     */
    public static <K> Object2LongMap<K> copyOf(Object2LongMap<K> source) {
        Object2LongMap<K> copied = new Object2LongLinkedOpenHashMap<>(source);
        copied.defaultReturnValue(source.defaultReturnValue());
        return owned(copied);
    }

    /**
     * Transfers an exclusively owned local map into a read-only view without copying its contents.
     *
     * @param amounts non-null map whose caller relinquishes all mutable aliases, including entries and iterators;
     *                it must never be mutated after this call
     * @return primitive read-only snapshot preserving the source's encounter order and missing-key semantics
     */
    public static <K> Object2LongMap<K> owned(Object2LongMap<K> amounts) {
        return new ReadOnlyAmounts<>(amounts);
    }

    private static final class ReadOnlyAmounts<K> extends UnmodifiableMap<K> {

        private final ObjectSet<Entry<K>> snapshotEntries;

        private ReadOnlyAmounts(Object2LongMap<K> amounts) {
            super(amounts);
            this.snapshotEntries = ObjectSets.unmodifiable(new AbstractObjectSet<>() {

                @Override
                public ObjectIterator<Entry<K>> iterator() {
                    ObjectIterator<Entry<K>> source = amounts.object2LongEntrySet().iterator();
                    return new ObjectIterator<>() {

                        @Override
                        public boolean hasNext() {
                            return source.hasNext();
                        }

                        @Override
                        public Entry<K> next() {
                            Entry<K> entry = source.next();
                            return new BasicEntry<>(entry.getKey(), entry.getLongValue());
                        }
                    };
                }

                @Override
                public int size() {
                    return amounts.size();
                }
            });
        }

        @Override
        public ObjectSet<Entry<K>> object2LongEntrySet() {
            return this.snapshotEntries;
        }
    }
}
