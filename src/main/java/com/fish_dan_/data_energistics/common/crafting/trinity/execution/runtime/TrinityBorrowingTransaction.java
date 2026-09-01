package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns one dynamic network-borrow transaction until provider ownership is known.
 * <p>
 * Physically conservative implementation of a Trinity dynamic borrowing transaction.
 */
public final class TrinityBorrowingTransaction {

    /**
     * Creates an empty transaction bound to the CPU working inventory and network storage.
     *
     * @param network        network storage receiving rollback material
     * @param ledger         durable ownership ledger
     * @param inventory      CPU working inventory
     * @param source         action source used for network insertion
     * @param cpuNumber      CPU number used in structured diagnostics
     * @param changeListener listener notified after working inventory changes
     * @return new transaction
     */
    public static TrinityBorrowingTransaction create(MEStorage network,
                                                     TrinityBorrowingLedger ledger,
                                                     ListCraftingInventory inventory,
                                                     IActionSource source,
                                                     int cpuNumber,
                                                     Consumer<AEKey> changeListener) {
        return new TrinityBorrowingTransaction(
                network,
                ledger,
                inventory,
                source,
                cpuNumber,
                changeListener);
    }

    private final MEStorage network;
    private final TrinityBorrowingLedger ledger;
    private final ListCraftingInventory inventory;
    private final IActionSource source;
    private final int cpuNumber;
    private final Consumer<AEKey> changeListener;
    private final Map<AEKey, BigInteger> reservedBefore = new LinkedHashMap<>();
    private final Map<AEKey, Long> ownedReservations = new LinkedHashMap<>();

    TrinityBorrowingTransaction(MEStorage network,
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

    /**
     * Verifies that a prospective physical borrow can still be represented by every possible ledger transition.
     * Callers must invoke this before mutating network storage.
     *
     * @param key    key about to be borrowed
     * @param amount maximum amount that may leave network storage
     */
    public void validateRecord(AEKey key, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A dynamic borrowing transfer must be positive");
        }
        Math.addExact(this.ownedReservations.getOrDefault(key, 0L), amount);
    }

    /**
     * Records material already transferred from the network into the CPU inventory.
     *
     * @param key    borrowed key
     * @param amount transferred amount
     */
    public void record(AEKey key, long amount) {
        validateRecord(key, amount);
        long owned = Math.addExact(this.ownedReservations.getOrDefault(key, 0L), amount);
        this.reservedBefore.putIfAbsent(key, this.ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        this.ledger.reserve(key, amount);
        this.ownedReservations.put(key, owned);
    }

    /**
     * Moves the borrowed portion consumed by an accepted provider dispatch from reserved to committed.
     *
     * @param inputsPerCraft exact bound inputs for one logical firing
     * @param count          accepted firing count
     */
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
        BigInteger reserved = this.ledger.amount(key, TrinityBorrowingLedger.State.RESERVED);
        long committed = reserved.min(BigInteger.valueOf(consumed)).longValueExact();
        if (committed == 0L) {
            return;
        }
        this.ledger.commit(key, committed);
        BigInteger earlierReservation = this.reservedBefore.getOrDefault(key, reserved);
        long ownedCommitted = BigInteger.valueOf(committed)
                .subtract(earlierReservation.min(BigInteger.valueOf(committed)))
                .longValueExact();
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

    /**
     * Retains all current reservations for a replacement plan rather than rolling them back.
     */
    public void retain() {
        this.ownedReservations.clear();
        this.reservedBefore.clear();
    }

    /**
     * Returns every still-owned reservation that network storage can accept.
     *
     * <p>
     * Material rejected by storage remains in the CPU inventory and remains {@code RESERVED}; it is never
     * synthesized or discarded from ledger state.
     */
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
}
