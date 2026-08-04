package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityBorrowingLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrinityBorrowingTransactionTest {

    @BeforeAll
    static void initialize() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void commitsOnlyConsumedMaterialAndReleasesTheOwnedRemainder() {
        AEKey key = AEFluidKey.of(Fluids.WATER);
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ListCraftingInventory inventory = inventoryWith(key, 6L);
        RecordingStorage network = new RecordingStorage();
        List<AEKey> changes = new ArrayList<>();
        TrinityBorrowingTransaction transaction = transaction(network, ledger, inventory, changes);

        transaction.record(key, 6L);
        transaction.commitConsumed(List.of(new GenericStack(key, 2L)), 2L);
        transaction.releaseUncommitted();

        assertEquals(0L, ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(4L, ledger.amount(key, TrinityBorrowingLedger.State.COMMITTED));
        assertEquals(2L, ledger.amount(key, TrinityBorrowingLedger.State.RELEASED));
        assertEquals(4L, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(2L, network.inserted);
        assertEquals(List.of(key), changes);
    }

    @Test
    void retainsStorageRejectedMaterialAsCpuOwnedReservation() {
        AEKey key = AEFluidKey.of(Fluids.WATER);
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ListCraftingInventory inventory = inventoryWith(key, 3L);
        RecordingStorage network = new RecordingStorage();
        network.acceptanceLimit = 1L;
        TrinityBorrowingTransaction transaction = transaction(network, ledger, inventory, new ArrayList<>());

        transaction.record(key, 3L);
        transaction.releaseUncommitted();

        assertEquals(2L, ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(1L, ledger.amount(key, TrinityBorrowingLedger.State.RELEASED));
        assertEquals(2L, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void storageContractFailureRestoresInventoryWithoutForgingLedgerRelease() {
        AEKey key = AEFluidKey.of(Fluids.WATER);
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ListCraftingInventory inventory = inventoryWith(key, 3L);
        RecordingStorage network = new RecordingStorage();
        network.invalidAcceptance = true;
        TrinityBorrowingTransaction transaction = transaction(network, ledger, inventory, new ArrayList<>());

        transaction.record(key, 3L);

        assertDoesNotThrow(transaction::releaseUncommitted);
        assertEquals(3L, ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(0L, ledger.amount(key, TrinityBorrowingLedger.State.RELEASED));
        assertEquals(3L, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    @Test
    void retainedReplacementReservationIsNotRolledBack() {
        AEKey key = AEFluidKey.of(Fluids.WATER);
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ListCraftingInventory inventory = inventoryWith(key, 2L);
        RecordingStorage network = new RecordingStorage();
        TrinityBorrowingTransaction transaction = transaction(network, ledger, inventory, new ArrayList<>());

        transaction.record(key, 2L);
        transaction.retain();
        transaction.releaseUncommitted();

        assertEquals(2L, ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(2L, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(0L, network.inserted);
    }

    @Test
    void rejectsUnrepresentableBorrowBeforePhysicalStorageMutation() {
        AEKey key = AEFluidKey.of(Fluids.WATER);
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ledger.reserve(key, Long.MAX_VALUE);
        ledger.commit(key, Long.MAX_VALUE);
        RecordingStorage network = new RecordingStorage();
        ListCraftingInventory inventory = inventoryWith(key, 1L);
        TrinityBorrowingTransaction transaction = transaction(network, ledger, inventory, new ArrayList<>());

        assertThrows(ArithmeticException.class, () -> transaction.validateRecord(key, 1L));

        assertEquals(0L, ledger.amount(key, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(Long.MAX_VALUE, ledger.amount(key, TrinityBorrowingLedger.State.COMMITTED));
        assertEquals(1L, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(0L, network.inserted);
    }

    private static TrinityBorrowingTransaction transaction(RecordingStorage network,
                                                           TrinityBorrowingLedger ledger,
                                                           ListCraftingInventory inventory,
                                                           List<AEKey> changes) {
        return TrinityBorrowingTransaction.create(
                network,
                ledger,
                inventory,
                IActionSource.empty(),
                7,
                changes::add);
    }

    private static ListCraftingInventory inventoryWith(AEKey key, long amount) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        inventory.insert(key, amount, Actionable.MODULATE);
        return inventory;
    }

    private static final class RecordingStorage implements MEStorage {

        private long acceptanceLimit = Long.MAX_VALUE;
        private long inserted;
        private boolean invalidAcceptance;

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long accepted = this.invalidAcceptance ? Math.addExact(amount, 1L) : Math.min(amount, this.acceptanceLimit);
            if (mode == Actionable.MODULATE && !this.invalidAcceptance) {
                this.inserted = Math.addExact(this.inserted, accepted);
            }
            return accepted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("recording");
        }
    }
}
