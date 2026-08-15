package com.fish_dan_.data_energistics.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores generated mimetic items until an external destination has actually accepted them.
 *
 * <p>
 * The ordered map-backed ledger is authoritative because container capacity can be smaller than one mimetic work
 * cycle.
 * </p>
 */
public final class MimeticPendingOutputLedger {

    /**
     * Accepts one legal item stack and reports the number of items the destination consumed.
     */
    @FunctionalInterface
    public interface ItemSink {

        /**
         * Attempts to consume the offered stack without retaining or mutating the ledger's state object.
         *
         * @param stack component-preserving stack whose count does not exceed its maximum stack size
         * @return consumed count between zero and the offered count, inclusive
         */
        int accept(ItemStack stack);
    }

    /** Largest practical number of elements that an {@link ArrayList} can address. */
    private static final long MAX_MATERIALIZED_STACKS = Integer.MAX_VALUE - 8L;

    /** Component-sensitive authoritative balances in first-seen order. */
    private final LinkedHashMap<AEItemKey, Long> contents = new LinkedHashMap<>();

    /** Transient round-robin order, advanced without scanning every pending key. */
    private final Deque<AEItemKey> offerQueue = new ArrayDeque<>();

    /** Persists each runtime mutation, especially every successful external consumption. */
    private final Runnable changeListener;

    /**
     * Creates an empty ledger whose runtime mutations notify its owning block entity.
     *
     * @param changeListener callback used to mark owning persistence dirty
     */
    public MimeticPendingOutputLedger(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /**
     * Reports whether no generated items remain pending.
     *
     * @return {@code true} when the ledger has no positive balances
     */
    public boolean isEmpty() {
        return this.contents.isEmpty();
    }

    /**
     * Queries one component-sensitive item balance for routing and diagnostics.
     *
     * @param key item identity including data components
     * @return pending item count, or zero when absent
     */
    public long amount(AEItemKey key) {
        return this.contents.getOrDefault(key, 0L);
    }

    /**
     * Aggregates one generated batch without imposing inventory slot limits.
     *
     * @param stacks generated stacks; empty stacks are ignored
     */
    public void append(List<ItemStack> stacks) {
        LinkedHashMap<AEItemKey, Long> generated = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            @Nullable
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                throw new IllegalArgumentException("Non-empty generated stack has no AE item key");
            }
            generated.merge(key, (long) stack.getCount(), MimeticPendingOutputLedger::addExact);
        }
        if (generated.isEmpty()) {
            return;
        }

        for (Map.Entry<AEItemKey, Long> entry : generated.entrySet()) {
            long current = this.contents.getOrDefault(entry.getKey(), 0L);
            entry.setValue(addExact(current, entry.getValue()));
        }
        for (Map.Entry<AEItemKey, Long> entry : generated.entrySet()) {
            if (!this.contents.containsKey(entry.getKey())) {
                this.offerQueue.addLast(entry.getKey());
            }
            this.contents.put(entry.getKey(), entry.getValue());
        }
        this.changeListener.run();
    }

    /**
     * Fairly offers pending keys within a strict work budget and removes only counts accepted by the sink.
     *
     * <p>
     * Successive calls resume at the next key. A call may stop before exhausting its budget after every remaining key
     * rejects one offer.
     * </p>
     *
     * @param sink        destination adapter
     * @param offerBudget positive maximum number of sink invocations
     * @return exact accepted item count
     */
    public long flush(ItemSink sink, int offerBudget) {
        if (offerBudget <= 0) {
            throw new IllegalArgumentException("offerBudget must be positive");
        }

        long totalAccepted = 0L;
        int consecutiveRejectedOffers = 0;
        for (int offers = 0; offers < offerBudget && !this.offerQueue.isEmpty(); offers++) {
            AEItemKey key = this.offerQueue.getFirst();
            long current = this.contents.get(key);
            ItemStack offeredStack = createLegalStack(key, current);
            int offered = offeredStack.getCount();
            int accepted = sink.accept(offeredStack);
            if (accepted < 0 || accepted > offered) {
                throw new IllegalStateException(
                        "Mimetic output sink accepted " + accepted + " items from an offer of " + offered);
            }

            this.offerQueue.removeFirst();
            long remaining = current - accepted;
            if (remaining > 0L) {
                this.contents.put(key, remaining);
                this.offerQueue.addLast(key);
            } else {
                this.contents.remove(key);
            }
            if (accepted == 0) {
                consecutiveRejectedOffers++;
                if (consecutiveRejectedOffers >= this.offerQueue.size()) {
                    break;
                }
                continue;
            }

            consecutiveRejectedOffers = 0;
            totalAccepted = Math.addExact(totalAccepted, accepted);
            this.changeListener.run();
        }
        return totalAccepted;
    }

    /**
     * Serializes all balances as ordered {@code GenericStack} NBT entries.
     *
     * @param registries registry access required by item component codecs
     * @return ordered serialized balances
     */
    public ListTag writeToNbt(HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<AEItemKey, Long> entry : this.contents.entrySet()) {
            entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
        }
        return entries;
    }

    /**
     * Replaces all balances from ordered {@code GenericStack} NBT entries without reporting a runtime mutation.
     *
     * @param registries registry access required by item component codecs
     * @param entries    ordered serialized balances
     */
    public void readFromNbt(HolderLookup.Provider registries, ListTag entries) {
        LinkedHashMap<AEItemKey, Long> restored = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entryTag = entries.getCompound(index);
            @Nullable
            GenericStack stack = GenericStack.readTag(registries, entryTag);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey) || stack.amount() <= 0L) {
                throw new IllegalArgumentException("Invalid mimetic pending-output entry at index " + index);
            }
            restored.merge(itemKey, stack.amount(), MimeticPendingOutputLedger::addExact);
        }

        Deque<AEItemKey> restoredOfferQueue = new ArrayDeque<>(restored.keySet());
        this.contents.clear();
        this.contents.putAll(restored);
        this.offerQueue.clear();
        this.offerQueue.addAll(restoredOfferQueue);
    }

    /**
     * Materializes every practical balance as component-preserving legal stacks for block destruction drops.
     *
     * @return independent stacks without consuming the ledger
     */
    public List<ItemStack> toItemStacks() {
        long stackCount = 0L;
        for (Map.Entry<AEItemKey, Long> entry : this.contents.entrySet()) {
            int maximumStackSize = maximumStackSize(entry.getKey());
            long required = 1L + (entry.getValue() - 1L) / maximumStackSize;
            if (required > MAX_MATERIALIZED_STACKS - stackCount) {
                throw new IllegalStateException("Mimetic pending output is too large to materialize as a Java list");
            }
            stackCount += required;
        }

        List<ItemStack> stacks = new ArrayList<>((int) stackCount);
        for (Map.Entry<AEItemKey, Long> entry : this.contents.entrySet()) {
            long remaining = entry.getValue();
            while (remaining > 0L) {
                ItemStack stack = createLegalStack(entry.getKey(), remaining);
                stacks.add(stack);
                remaining -= stack.getCount();
            }
        }
        return stacks;
    }

    /**
     * Removes all balances after they have been transferred or spawned.
     */
    public void clear() {
        if (this.contents.isEmpty()) {
            return;
        }
        this.contents.clear();
        this.offerQueue.clear();
        this.changeListener.run();
    }

    /**
     * Creates one component-preserving offer capped to its item-defined maximum stack size.
     *
     * @param key       item identity including components
     * @param available positive pending amount
     * @return legal independent offer stack
     */
    private static ItemStack createLegalStack(AEItemKey key, long available) {
        if (available <= 0L) {
            throw new IllegalArgumentException("available must be positive");
        }
        int count = (int) Math.min(available, maximumStackSize(key));
        ItemStack stack = key.toStack(count);
        if (stack.isEmpty() || stack.getCount() != count || stack.getCount() > stack.getMaxStackSize()) {
            throw new IllegalStateException("AE item key produced an invalid mimetic output stack");
        }
        return stack;
    }

    /**
     * Resolves the data-component-aware stack limit for one key.
     *
     * @param key item identity including components
     * @return positive maximum stack size
     */
    private static int maximumStackSize(AEItemKey key) {
        ItemStack prototype = key.toStack(1);
        int maximumStackSize = prototype.getMaxStackSize();
        if (prototype.isEmpty() || maximumStackSize <= 0) {
            throw new IllegalStateException("AE item key has no legal item-stack representation");
        }
        return maximumStackSize;
    }

    /**
     * Adds positive balances without allowing signed overflow.
     *
     * @param left  existing non-negative amount
     * @param right appended non-negative amount
     * @return exact non-negative sum
     * @throws ArithmeticException when the sum exceeds the long range
     */
    private static long addExact(long left, long right) {
        if (left < 0L || right < 0L) {
            throw new IllegalArgumentException("Mimetic pending-output amounts cannot be negative");
        }
        return Math.addExact(left, right);
    }
}
