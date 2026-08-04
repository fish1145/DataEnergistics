package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityBorrowingLedgerNbtCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map-backed borrowing ledger whose only legal transitions originate in {@link State#RESERVED}.
 */
public final class TrinityBorrowingLedgerImpl implements TrinityBorrowingLedger {

    private final LinkedHashMap<AEKey, MutableBalances> entries = new LinkedHashMap<>();

    /**
     * Creates an empty ledger. Prefer {@link TrinityBorrowingLedger#create()} at integration boundaries.
     */
    public TrinityBorrowingLedgerImpl() {}

    TrinityBorrowingLedgerImpl(Map<AEKey, Balances> entries) {
        entries.forEach((key, balances) -> this.entries.put(key, new MutableBalances(balances)));
    }

    static TrinityBorrowingLedgerImpl restore(CompoundTag tag, HolderLookup.Provider registries) {
        return new TrinityBorrowingLedgerImpl(TrinityBorrowingLedgerNbtCodec.decode(tag, registries));
    }

    @Override
    public void reserve(AEKey key, long amount) {
        requireTransfer(key, amount);
        MutableBalances balances = this.entries.computeIfAbsent(key, ignored -> new MutableBalances());
        long reserved = Math.addExact(balances.reserved, amount);
        ensureRepresentableTotal(balances.committed, reserved);
        ensureRepresentableTotal(balances.released, reserved);
        balances.reserved = reserved;
    }

    @Override
    public void commit(AEKey key, long amount) {
        MutableBalances balances = requireReserved(key, amount);
        long committed = Math.addExact(balances.committed, amount);
        balances.reserved -= amount;
        balances.committed = committed;
    }

    @Override
    public void release(AEKey key, long amount) {
        MutableBalances balances = requireReserved(key, amount);
        long released = Math.addExact(balances.released, amount);
        balances.reserved -= amount;
        balances.released = released;
    }

    @Override
    public long amount(AEKey key, State state) {
        if (key == null || state == null) {
            throw new IllegalArgumentException("A Trinity borrowing query requires key and state");
        }
        MutableBalances balances = this.entries.get(key);
        if (balances == null) {
            return 0L;
        }
        return switch (state) {
            case RESERVED -> balances.reserved;
            case COMMITTED -> balances.committed;
            case RELEASED -> balances.released;
        };
    }

    @Override
    public Map<AEKey, Balances> entries() {
        LinkedHashMap<AEKey, Balances> snapshot = new LinkedHashMap<>();
        this.entries.forEach((key, balances) -> snapshot.put(key, balances.snapshot()));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
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
        if (balances == null || balances.reserved < amount) {
            throw new IllegalStateException("A Trinity borrowing transition cannot exceed CPU-owned reservations");
        }
        return balances;
    }

    private static void ensureRepresentableTotal(long settled, long outstanding) {
        if (settled > Long.MAX_VALUE - outstanding) {
            throw new ArithmeticException("Trinity borrowing balance exceeds the AE2 long amount boundary");
        }
    }

    private static final class MutableBalances {

        private long reserved;
        private long committed;
        private long released;

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
