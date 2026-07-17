package com.fish_dan_.data_energistics.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;

import java.util.List;

/**
 * Stores generated mimetic items until an external destination has actually accepted them.
 *
 * <p>
 * The ledger is authoritative because container capacity can be smaller than one mimetic work cycle.
 */
public interface MimeticPendingOutput {

    /**
     * Accepts one legal item stack and reports the number of items the destination consumed.
     */
    @FunctionalInterface
    interface ItemSink {

        /**
         * Attempts to consume the offered stack without retaining or mutating the ledger's state object.
         *
         * @param stack component-preserving stack whose count does not exceed its maximum stack size
         * @return consumed count between zero and the offered count, inclusive
         */
        int accept(ItemStack stack);
    }

    /**
     * Reports whether no generated items remain pending.
     *
     * @return {@code true} when the ledger has no positive balances
     */
    boolean isEmpty();

    /**
     * Queries one component-sensitive item balance for routing and diagnostics.
     *
     * @param key item identity including data components
     * @return pending item count, or zero when absent
     */
    long amount(AEItemKey key);

    /**
     * Aggregates one generated batch without imposing inventory slot limits.
     *
     * @param stacks generated stacks; empty stacks are ignored
     */
    void append(List<ItemStack> stacks);

    /**
     * Fairly offers pending keys within a strict work budget and removes only counts accepted by the sink.
     *
     * <p>
     * Successive calls resume at the next key. A call may stop before exhausting its budget after every remaining key
     * rejects one offer.
     *
     * @param sink        destination adapter
     * @param offerBudget positive maximum number of sink invocations
     * @return exact accepted item count
     */
    long flush(ItemSink sink, int offerBudget);

    /**
     * Serializes all balances as ordered {@code GenericStack} NBT entries.
     *
     * @param registries registry access required by item component codecs
     * @return ordered serialized balances
     */
    ListTag writeToNbt(HolderLookup.Provider registries);

    /**
     * Replaces all balances from ordered {@code GenericStack} NBT entries without reporting a runtime mutation.
     *
     * @param registries registry access required by item component codecs
     * @param entries    ordered serialized balances
     */
    void readFromNbt(HolderLookup.Provider registries, ListTag entries);

    /**
     * Materializes every practical balance as component-preserving legal stacks for block destruction drops.
     *
     * @return independent stacks without consuming the ledger
     */
    List<ItemStack> toItemStacks();

    /**
     * Removes all balances after they have been transferred or spawned.
     */
    void clear();
}
