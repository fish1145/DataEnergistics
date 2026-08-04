package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Owns one dynamic network-borrow transaction until provider ownership is known.
 */
public interface TrinityBorrowingTransaction {

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
    static TrinityBorrowingTransaction create(MEStorage network,
                                              TrinityBorrowingLedger ledger,
                                              ListCraftingInventory inventory,
                                              IActionSource source,
                                              int cpuNumber,
                                              Consumer<AEKey> changeListener) {
        return new TrinityBorrowingTransactionImpl(
                network,
                ledger,
                inventory,
                source,
                cpuNumber,
                changeListener);
    }

    /**
     * Verifies that a prospective physical borrow can still be represented by every possible ledger transition.
     * Callers must invoke this before mutating network storage.
     *
     * @param key    key about to be borrowed
     * @param amount maximum amount that may leave network storage
     */
    void validateRecord(AEKey key, long amount);

    /**
     * Records material already transferred from the network into the CPU inventory.
     *
     * @param key    borrowed key
     * @param amount transferred amount
     */
    void record(AEKey key, long amount);

    /**
     * Moves the borrowed portion consumed by an accepted provider dispatch from reserved to committed.
     *
     * @param inputsPerCraft exact bound inputs for one logical firing
     * @param count          accepted firing count
     */
    void commitConsumed(List<GenericStack> inputsPerCraft, long count);

    /**
     * Retains all current reservations for a replacement plan rather than rolling them back.
     */
    void retain();

    /**
     * Returns every still-owned reservation that network storage can accept.
     *
     * <p>
     * Material rejected by storage remains in the CPU inventory and remains {@code RESERVED}; it is never
     * synthesized or discarded from ledger state.
     */
    void releaseUncommitted();
}
