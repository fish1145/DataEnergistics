package com.fish_dan_.data_energistics.integration.brandonscore;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BrandonsCoreEnergyIntegrationTest {

    @Test
    void preservesLongAmountsBeyondTheStandardEnergyWidth() {
        TestOpStorage opStorage = new TestOpStorage(4_000_000_000L, 9_000_000_000L, true, true);
        IEnergyStorage storage = opStorage;

        assertTrue(BrandonsCoreEnergyIntegration.supports(storage));
        assertEquals(4_000_000_000L, BrandonsCoreEnergyIntegration.stored(storage));
        assertEquals(9_000_000_000L, BrandonsCoreEnergyIntegration.capacity(storage));

        assertEquals(3_000_000_000L, BrandonsCoreEnergyIntegration.insert(storage, 3_000_000_000L, false));
        assertEquals(7_000_000_000L, BrandonsCoreEnergyIntegration.stored(storage));
        assertEquals(2_500_000_000L, BrandonsCoreEnergyIntegration.extract(storage, 2_500_000_000L, false));
        assertEquals(4_500_000_000L, BrandonsCoreEnergyIntegration.stored(storage));
    }

    @Test
    void honorsInputAndOutputPermissionsBeforeTransfer() {
        TestOpStorage input = new TestOpStorage(10L, 100L, true, false);
        assertTrue(BrandonsCoreEnergyIntegration.canReceive(input));
        assertFalse(BrandonsCoreEnergyIntegration.canExtract(input));
        assertEquals(30L, BrandonsCoreEnergyIntegration.insert(input, 30L, false));
        assertEquals(0L, BrandonsCoreEnergyIntegration.extract(input, 30L, false));
        assertEquals(1, input.receiveCalls());
        assertEquals(0, input.extractCalls());

        TestOpStorage output = new TestOpStorage(80L, 100L, false, true);
        assertFalse(BrandonsCoreEnergyIntegration.canReceive(output));
        assertTrue(BrandonsCoreEnergyIntegration.canExtract(output));
        assertEquals(0L, BrandonsCoreEnergyIntegration.insert(output, 30L, false));
        assertEquals(30L, BrandonsCoreEnergyIntegration.extract(output, 30L, false));
        assertEquals(0, output.receiveCalls());
        assertEquals(1, output.extractCalls());
    }

    @Test
    void rejectsStorageWithoutTheOpCapabilityType() {
        IEnergyStorage standardStorage = new EnergyStorage(100);

        assertFalse(BrandonsCoreEnergyIntegration.supports(standardStorage));
        assertThrows(IllegalArgumentException.class, () -> BrandonsCoreEnergyIntegration.stored(standardStorage));
    }

    private static final class TestOpStorage implements IOPStorage {

        private long stored;
        private final long capacity;
        private final boolean canReceive;
        private final boolean canExtract;
        private int receiveCalls;
        private int extractCalls;

        private TestOpStorage(long stored, long capacity, boolean canReceive, boolean canExtract) {
            this.stored = stored;
            this.capacity = capacity;
            this.canReceive = canReceive;
            this.canExtract = canExtract;
        }

        @Override
        public long receiveOP(long maxReceive, boolean simulate) {
            this.receiveCalls++;
            long accepted = Math.min(maxReceive, this.capacity - this.stored);
            if (!simulate) {
                this.stored += accepted;
            }
            return accepted;
        }

        @Override
        public long extractOP(long maxExtract, boolean simulate) {
            this.extractCalls++;
            long extracted = Math.min(maxExtract, this.stored);
            if (!simulate) {
                this.stored -= extracted;
            }
            return extracted;
        }

        @Override
        public long getOPStored() {
            return this.stored;
        }

        @Override
        public long getMaxOPStored() {
            return this.capacity;
        }

        @Override
        public boolean canExtract() {
            return this.canExtract;
        }

        @Override
        public boolean canReceive() {
            return this.canReceive;
        }

        @Override
        public long modifyEnergyStored(long amount) {
            throw new AssertionError("The integration must not bypass the public transfer methods");
        }

        private int receiveCalls() {
            return this.receiveCalls;
        }

        private int extractCalls() {
            return this.extractCalls;
        }
    }
}
