package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityBorrowingLedgerNbtCodec;

import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks ownership-preserving dynamic material borrowing independently from execution scheduling.
 * <p>
 * Map-backed borrowing ledger whose only legal transitions originate in {@link State#RESERVED}.
 */
public final class TrinityBorrowingLedger {

    /**
     * Lifecycle positions through which borrowed material may move exactly once.
     */
    public enum State {
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
    public record Balances(BigInteger reserved, BigInteger committed, BigInteger released) {

        /**
         * Rejects negative state amounts before they can hide an ownership violation.
         */
        public Balances {
            if (reserved.signum() < 0 || committed.signum() < 0 || released.signum() < 0) {
                throw new IllegalArgumentException("Trinity borrowing balances cannot be negative");
            }
        }

        /**
         * @return exact amount ever reserved for this key
         */
        public BigInteger total() {
            return this.reserved.add(this.committed).add(this.released);
        }
    }

    /**
     * @return an empty ownership ledger
     */
    public static TrinityBorrowingLedger create() {
        return new TrinityBorrowingLedger();
    }

    private final LinkedHashMap<AEKey, MutableBalances> entries = new LinkedHashMap<>();

    /**
     * Creates an empty ledger. Prefer {@link TrinityBorrowingLedger#create()} at integration boundaries.
     */
    public TrinityBorrowingLedger() {}

    TrinityBorrowingLedger(Map<AEKey, Balances> entries) {
        entries.forEach((key, balances) -> this.entries.put(key, new MutableBalances(balances)));
    }

    /**
     * Restores an ownership ledger and rejects malformed or non-conserving data.
     *
     * @param tag        encoded ledger
     * @param registries server registry lookup used by AE key codecs
     * @return restored ledger
     */
    public static TrinityBorrowingLedger restore(CompoundTag tag, HolderLookup.Provider registries) {
        return new TrinityBorrowingLedger(TrinityBorrowingLedgerNbtCodec.decode(tag, registries));
    }

    /**
     * Adds newly extracted material to CPU-owned reservations.
     *
     * @param key    borrowed storage key
     * @param amount positive amount obtained by the CPU
     */
    public void reserve(AEKey key, long amount) {
        requireTransfer(key, amount);
        MutableBalances balances = this.entries.computeIfAbsent(key, ignored -> new MutableBalances());
        balances.reserved = balances.reserved.add(BigInteger.valueOf(amount));
    }

    /**
     * Moves CPU-owned material to provider ownership.
     *
     * @param key    borrowed storage key
     * @param amount positive amount accepted by a provider
     */
    public void commit(AEKey key, long amount) {
        MutableBalances balances = requireReserved(key, amount);
        BigInteger transferred = BigInteger.valueOf(amount);
        balances.reserved = balances.reserved.subtract(transferred);
        balances.committed = balances.committed.add(transferred);
    }

    /**
     * Moves CPU-owned material to the released history after a real refund.
     *
     * @param key    borrowed storage key
     * @param amount positive amount returned by the CPU
     */
    public void release(AEKey key, long amount) {
        MutableBalances balances = requireReserved(key, amount);
        BigInteger transferred = BigInteger.valueOf(amount);
        balances.reserved = balances.reserved.subtract(transferred);
        balances.released = balances.released.add(transferred);
    }

    /**
     * @param key   borrowed storage key
     * @param state ownership state to inspect
     * @return amount currently aggregated in that state
     */
    public BigInteger amount(AEKey key, State state) {
        MutableBalances balances = this.entries.get(key);
        if (balances == null) {
            return BigInteger.ZERO;
        }
        return switch (state) {
            case RESERVED -> balances.reserved;
            case COMMITTED -> balances.committed;
            case RELEASED -> balances.released;
        };
    }

    /**
     * @return immutable snapshot of all non-empty key balances
     */
    public Map<AEKey, Balances> entries() {
        LinkedHashMap<AEKey, Balances> snapshot = new LinkedHashMap<>();
        this.entries.forEach((key, balances) -> snapshot.put(key, balances.snapshot()));
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Encodes the complete three-state ownership history.
     *
     * @param registries server registry lookup used by AE key codecs
     * @return strict ledger NBT
     */
    public CompoundTag save(HolderLookup.Provider registries) {
        return TrinityBorrowingLedgerNbtCodec.encode(entries(), registries);
    }

    private static void requireTransfer(AEKey key, long amount) {
        if (key == null || amount <= 0L) {
            throw new IllegalArgumentException("A Trinity borrowing transition requires a key and positive amount");
        }
    }

    private MutableBalances requireReserved(AEKey key, long amount) {
        requireTransfer(key, amount);
        MutableBalances balances = this.entries.get(key);
        if (balances == null || balances.reserved.compareTo(BigInteger.valueOf(amount)) < 0) {
            throw new IllegalStateException("A Trinity borrowing transition cannot exceed CPU-owned reservations");
        }
        return balances;
    }

    private static final class MutableBalances {

        private BigInteger reserved = BigInteger.ZERO;
        private BigInteger committed = BigInteger.ZERO;
        private BigInteger released = BigInteger.ZERO;

        private MutableBalances() {}

        private MutableBalances(Balances balances) {
            this.reserved = balances.reserved();
            this.committed = balances.committed();
            this.released = balances.released();
        }

        private Balances snapshot() {
            return new Balances(this.reserved, this.committed, this.released);
        }
    }
}
