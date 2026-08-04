package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Physically conservative implementation of a Trinity dynamic borrowing transaction.
 */
final class TrinityBorrowingTransactionImpl implements TrinityBorrowingTransaction {

    private final MEStorage network;
    private final TrinityBorrowingLedger ledger;
    private final ListCraftingInventory inventory;
    private final IActionSource source;
    private final int cpuNumber;
    private final Consumer<AEKey> changeListener;
    private final Map<AEKey, Long> reservedBefore = new LinkedHashMap<>();
    private final Map<AEKey, Long> ownedReservations = new LinkedHashMap<>();

    TrinityBorrowingTransactionImpl(MEStorage network,
                                    TrinityBorrowingLedger ledger,
                                    ListCraftingInventory inventory,
                                    IActionSource source,
                                    int cpuNumber,
                                    Consumer<AEKey> changeListener) {
        this.network = network;
        this.ledger = ledger;
        this.inventory = inventory;
        this.source = source;
        this.cpuNumber = cpuNumber;
        this.changeListener = changeListener;
    }

    @Override
    public void validateRecord(AEKey key, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A dynamic borrowing transfer must be positive");
        }
        ensureRepresentableTotal(this.ownedReservations.getOrDefault(key, 0L), amount);
        long reserved = Math.addExact(
                this.ledger.amount(key, TrinityBorrowingLedger.State.RESERVED),
                amount);
        ensureRepresentableTotal(this.ledger.amount(key, TrinityBorrowingLedger.State.COMMITTED), reserved);
        ensureRepresentableTotal(this.ledger.amount(key, TrinityBorrowingLedger.State.RELEASED), reserved);
    }

    @Override
    public void record(AEKey key, long amount) {
        validateRecord(key, amount);
        long owned = Math.addExact(this.ownedReservations.getOrDefault(key, 0L), amount);
        this.reservedBefore.putIfAbsent(key, this.ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        this.ledger.reserve(key, amount);
        this.ownedReservations.put(key, owned);
    }

    @Override
    public void commitConsumed(List<GenericStack> inputsPerCraft, long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("A committed dynamic borrowing dispatch must be positive");
        }
        LinkedHashMap<AEKey, Long> consumed = new LinkedHashMap<>();
        for (GenericStack input : inputsPerCraft) {
            long amount = Math.multiplyExact(input.amount(), count);
            consumed.merge(input.what(), amount, Math::addExact);
        }
        consumed.forEach(this::commitBorrowedPortion);
    }

    private void commitBorrowedPortion(AEKey key, long consumed) {
        long reserved = this.ledger.amount(key, TrinityBorrowingLedger.State.RESERVED);
        long committed = Math.min(reserved, consumed);
        if (committed == 0L) {
            return;
        }
        this.ledger.commit(key, committed);
        long earlierReservation = this.reservedBefore.getOrDefault(key, reserved);
        long ownedCommitted = Math.max(0L, committed - Math.min(committed, earlierReservation));
        if (ownedCommitted == 0L) {
            return;
        }
        long owned = this.ownedReservations.getOrDefault(key, 0L);
        long remaining = Math.subtractExact(owned, ownedCommitted);
        if (remaining == 0L) {
            this.ownedReservations.remove(key);
        } else {
            this.ownedReservations.put(key, remaining);
        }
    }

    @Override
    public void retain() {
        this.ownedReservations.clear();
        this.reservedBefore.clear();
    }

    @Override
    public void releaseUncommitted() {
        for (Map.Entry<AEKey, Long> entry : List.copyOf(this.ownedReservations.entrySet())) {
            release(entry.getKey(), entry.getValue());
        }
    }

    private void release(AEKey key, long owned) {
        long available = this.inventory.extract(key, owned, Actionable.SIMULATE);
        long returning = Math.min(owned, available);
        if (returning == 0L) {
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} lost ownership of {} reserved dynamic units of {} before release",
                    this.cpuNumber,
                    owned,
                    key);
            return;
        }

        long extracted = this.inventory.extract(key, returning, Actionable.MODULATE);
        long inserted;
        try {
            inserted = this.network.insert(key, extracted, Actionable.MODULATE, this.source);
        } catch (RuntimeException exception) {
            this.inventory.insert(key, extracted, Actionable.MODULATE);
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} could not return reserved dynamic material {} to network storage",
                    this.cpuNumber,
                    key,
                    exception);
            return;
        }
        if (inserted < 0L || inserted > extracted) {
            this.inventory.insert(key, extracted, Actionable.MODULATE);
            Data_Energistics.LOGGER.error(
                    "Trinity CPU {} retained reserved dynamic material {} after storage violated its insertion contract: offered {}, accepted {}",
                    this.cpuNumber,
                    key,
                    extracted,
                    inserted);
            return;
        }
        if (inserted < extracted) {
            this.inventory.insert(key, extracted - inserted, Actionable.MODULATE);
        }
        if (inserted > 0L) {
            this.ledger.release(key, inserted);
            this.changeListener.accept(key);
        }
        long retained = owned - inserted;
        if (retained == 0L) {
            this.ownedReservations.remove(key);
        } else {
            this.ownedReservations.put(key, retained);
        }
    }

    private static void ensureRepresentableTotal(long settled, long outstanding) {
        if (settled > Long.MAX_VALUE - outstanding) {
            throw new ArithmeticException("Trinity borrowing balance exceeds the AE2 long amount boundary");
        }
    }
}
