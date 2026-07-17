package com.fish_dan_.data_energistics.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered map-backed implementation of the mimetic pending-output ledger.
 */
public final class MimeticPendingOutputImpl implements MimeticPendingOutput {

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
    public MimeticPendingOutputImpl(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return this.contents.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public long amount(AEItemKey key) {
        return this.contents.getOrDefault(Objects.requireNonNull(key, "key"), 0L);
    }

    /** {@inheritDoc} */
    @Override
    public void append(List<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        LinkedHashMap<AEItemKey, Long> generated = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            Objects.requireNonNull(stack, "generated stack");
            if (stack.isEmpty()) {
                continue;
            }

            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                throw new IllegalArgumentException("Non-empty generated stack has no AE item key");
            }
            generated.merge(key, (long) stack.getCount(), MimeticPendingOutputImpl::addExact);
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

    /** {@inheritDoc} */
    @Override
    public long flush(ItemSink sink, int offerBudget) {
        Objects.requireNonNull(sink, "sink");
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

    /** {@inheritDoc} */
    @Override
    public ListTag writeToNbt(HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        ListTag entries = new ListTag();
        for (Map.Entry<AEItemKey, Long> entry : this.contents.entrySet()) {
            entries.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
        }
        return entries;
    }

    /** {@inheritDoc} */
    @Override
    public void readFromNbt(HolderLookup.Provider registries, ListTag entries) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(entries, "entries");
        LinkedHashMap<AEItemKey, Long> restored = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entryTag = entries.getCompound(index);
            GenericStack stack = GenericStack.readTag(registries, entryTag);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey) || stack.amount() <= 0L) {
                throw new IllegalArgumentException("Invalid mimetic pending-output entry at index " + index);
            }
            restored.merge(itemKey, stack.amount(), MimeticPendingOutputImpl::addExact);
        }

        Deque<AEItemKey> restoredOfferQueue = new ArrayDeque<>(restored.keySet());
        this.contents.clear();
        this.contents.putAll(restored);
        this.offerQueue.clear();
        this.offerQueue.addAll(restoredOfferQueue);
    }

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @Override
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
        ItemStack prototype = Objects.requireNonNull(key, "key").toStack(1);
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
