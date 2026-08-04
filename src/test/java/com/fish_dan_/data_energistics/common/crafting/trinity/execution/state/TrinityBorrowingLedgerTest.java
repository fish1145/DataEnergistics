package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import net.minecraft.core.RegistryAccess;

import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityBorrowingLedgerTest {

    @BeforeAll
    static void initializeRegistries() {
        TrinityExecutionStateTestSupport.initialize();
    }

    @Test
    void movesAmountsOnlyFromReservedWhilePreservingTotalOwnership() {
        AEKey iron = TrinityExecutionStateTestSupport.echo();
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();

        ledger.reserve(iron, 10L);
        ledger.commit(iron, 4L);
        ledger.release(iron, 3L);

        assertEquals(3L, ledger.amount(iron, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(4L, ledger.amount(iron, TrinityBorrowingLedger.State.COMMITTED));
        assertEquals(3L, ledger.amount(iron, TrinityBorrowingLedger.State.RELEASED));
        assertEquals(10L, ledger.entries().get(iron).total());
        assertThrows(IllegalStateException.class, () -> ledger.commit(iron, 4L));
        assertThrows(IllegalStateException.class, () -> ledger.release(iron, 4L));
    }

    @Test
    void rejectsOverflowAndRoundTripsAllThreeStates() {
        AEKey iron = TrinityExecutionStateTestSupport.echo();
        TrinityBorrowingLedger ledger = TrinityBorrowingLedger.create();
        ledger.reserve(iron, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> ledger.reserve(iron, 1L));
        ledger.commit(iron, 5L);
        ledger.release(iron, 7L);

        TrinityBorrowingLedger restored = TrinityBorrowingLedger.restore(
                ledger.save(RegistryAccess.EMPTY),
                RegistryAccess.EMPTY);

        assertEquals(ledger.entries(), restored.entries());
        assertThrows(UnsupportedOperationException.class,
                () -> restored.entries().put(iron, new TrinityBorrowingLedger.Balances(1L, 0L, 0L)));
    }

    @Test
    void rejectsNewReservationsThatWouldOverflowSettledHistory() {
        AEKey iron = TrinityExecutionStateTestSupport.echo();
        TrinityBorrowingLedger committed = TrinityBorrowingLedger.create();
        committed.reserve(iron, Long.MAX_VALUE);
        committed.commit(iron, Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> committed.reserve(iron, 1L));
        assertEquals(0L, committed.amount(iron, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(Long.MAX_VALUE, committed.amount(iron, TrinityBorrowingLedger.State.COMMITTED));

        TrinityBorrowingLedger released = TrinityBorrowingLedger.create();
        released.reserve(iron, Long.MAX_VALUE);
        released.release(iron, Long.MAX_VALUE);

        assertThrows(ArithmeticException.class, () -> released.reserve(iron, 1L));
        assertEquals(0L, released.amount(iron, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(Long.MAX_VALUE, released.amount(iron, TrinityBorrowingLedger.State.RELEASED));
    }
}
