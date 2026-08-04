package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;

import java.util.Map;

/**
 * Tracks ownership-preserving dynamic material borrowing independently from execution scheduling.
 */
public interface TrinityBorrowingLedger {

    /**
     * Lifecycle positions through which borrowed material may move exactly once.
     */
    enum State {
        /**
         * Material still owned by the CPU and eligible for commit or release.
         */
        RESERVED,
        /**
         * Material accepted by a provider and no longer refundable as an input.
         */
        COMMITTED,
        /**
         * Material returned while it was still owned by the CPU.
         */
        RELEASED
    }

    /**
     * Immutable aggregate for one key.
     *
     * @param reserved  amount still owned by the CPU
     * @param committed amount transferred to providers
     * @param released  amount returned without being committed
     */
    record Balances(long reserved, long committed, long released) {

        /**
         * Rejects negative state amounts before they can hide an ownership violation.
         */
        public Balances {
            if (reserved < 0L || committed < 0L || released < 0L) {
                throw new IllegalArgumentException("Trinity borrowing balances cannot be negative");
            }
            if (reserved > Long.MAX_VALUE - committed || reserved + committed > Long.MAX_VALUE - released) {
                throw new ArithmeticException("Trinity borrowing balance total exceeds the exact long boundary");
            }
        }

        /**
         * @return exact amount ever reserved for this key
         */
        public long total() {
            return Math.addExact(Math.addExact(this.reserved, this.committed), this.released);
        }
    }

    /**
     * @return an empty ownership ledger
     */
    static TrinityBorrowingLedger create() {
        return new TrinityBorrowingLedgerImpl();
    }

    /**
     * Restores an ownership ledger and rejects malformed or non-conserving data.
     *
     * @param tag        encoded ledger
     * @param registries server registry lookup used by AE key codecs
     * @return restored ledger
     */
    static TrinityBorrowingLedger restore(CompoundTag tag, HolderLookup.Provider registries) {
        return TrinityBorrowingLedgerImpl.restore(tag, registries);
    }

    /**
     * Adds newly extracted material to CPU-owned reservations.
     *
     * @param key    borrowed storage key
     * @param amount positive amount obtained by the CPU
     */
    void reserve(AEKey key, long amount);

    /**
     * Moves CPU-owned material to provider ownership.
     *
     * @param key    borrowed storage key
     * @param amount positive amount accepted by a provider
     */
    void commit(AEKey key, long amount);

    /**
     * Moves CPU-owned material to the released history after a real refund.
     *
     * @param key    borrowed storage key
     * @param amount positive amount returned by the CPU
     */
    void release(AEKey key, long amount);

    /**
     * @param key   borrowed storage key
     * @param state ownership state to inspect
     * @return amount currently aggregated in that state
     */
    long amount(AEKey key, State state);

    /**
     * @return immutable snapshot of all non-empty key balances
     */
    Map<AEKey, Balances> entries();

    /**
     * Encodes the complete three-state ownership history.
     *
     * @param registries server registry lookup used by AE key codecs
     * @return strict ledger NBT
     */
    CompoundTag save(HolderLookup.Provider registries);
}
