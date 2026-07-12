package com.fish_dan_.data_energistics.integration.oritech;

import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccessImpl;

import net.neoforged.neoforge.energy.IEnergyStorage;

import org.junit.jupiter.api.Test;
import rearth.oritech.api.energy.EnergyApi.EnergyStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OritechEnergyIntegrationTest {

    private final UnlimitedEnergyAccess access = new UnlimitedEnergyAccessImpl();

    @Test
    void bypassesOritechTransferMethodsThroughItsTypedLongApi() {
        TestOritechStorage oritechStorage = new TestOritechStorage(4_000_000_000L, 9_000_000_000L);
        IEnergyStorage storage = OritechEnergyIntegration.wrapEnergyStorage(oritechStorage);

        assertEquals(4_000_000_000L, this.access.stored(storage));
        assertEquals(9_000_000_000L, this.access.capacity(storage));
        assertEquals(3_000_000_000L, this.access.insert(storage, 3_000_000_000L, false));
        assertEquals(2_000_000_000L, this.access.extract(storage, 2_000_000_000L, false));
        this.access.notifyStorageChanged(storage);

        assertEquals(5_000_000_000L, oritechStorage.getAmount());
        assertEquals(0, oritechStorage.rateLimitedCalls());
        assertEquals(1, oritechStorage.updates());
    }

    private static final class TestOritechStorage extends EnergyStorage {

        private long amount;
        private final long capacity;
        private int rateLimitedCalls;
        private int updates;

        private TestOritechStorage(long amount, long capacity) {
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            this.rateLimitedCalls++;
            long inserted = Math.min(1L, Math.min(maxAmount, this.capacity - this.amount));
            if (!simulate) {
                this.amount += inserted;
            }
            return inserted;
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            this.rateLimitedCalls++;
            long extracted = Math.min(1L, Math.min(maxAmount, this.amount));
            if (!simulate) {
                this.amount -= extracted;
            }
            return extracted;
        }

        @Override
        public void setAmount(long amount) {
            this.amount = amount;
        }

        @Override
        public long getAmount() {
            return this.amount;
        }

        @Override
        public long getCapacity() {
            return this.capacity;
        }

        @Override
        public void update() {
            this.updates++;
        }

        private int rateLimitedCalls() {
            return this.rateLimitedCalls;
        }

        private int updates() {
            return this.updates;
        }
    }
}
