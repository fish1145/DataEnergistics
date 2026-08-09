package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointRole;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompensatingTowerEnergyTransactionTest {

    private static final int STABLE_TICKS = 128;

    @Test
    void nearLongMaxNetworkBufferFillsMachineOnceWithoutRepeatedMutation() {
        StatefulEndpoint machine = new StatefulEndpoint(
                id(0),
                0,
                1_024,
                TowerEnergyDirection.BIDIRECTIONAL,
                TowerEnergyEndpointRole.BALANCED);
        StatefulEndpoint networkBuffer = new StatefulEndpoint(
                id(1),
                Long.MAX_VALUE - 1_024,
                Long.MAX_VALUE,
                TowerEnergyDirection.BIDIRECTIONAL,
                TowerEnergyEndpointRole.BUFFER);
        BigInteger initialEnergy = totalEnergy(machine, networkBuffer);
        CompensatingTowerEnergyTransaction transaction = new CompensatingTowerEnergyTransaction();

        TowerEnergyTransactionResult firstTick = transaction.execute(List.of(machine, networkBuffer));

        assertTrue(firstTick.mutated());
        assertEquals(1_024, firstTick.plannedFe());
        assertEquals(1_024, firstTick.insertedFe());
        assertEquals(0, firstTick.quarantinedFe());
        assertEquals(initialEnergy, totalEnergy(machine, networkBuffer));
        assertEquals(1, networkBuffer.mutationCount());
        assertEquals(1, networkBuffer.publicationCount());

        for (int tick = 0; tick < STABLE_TICKS; tick++) {
            TowerEnergyTransactionResult stableTick = transaction.execute(List.of(machine, networkBuffer));
            assertFalse(stableTick.mutated());
            assertEquals(0, stableTick.plannedFe());
            assertEquals(0, stableTick.insertedFe());
        }

        assertEquals(initialEnergy, totalEnergy(machine, networkBuffer));
        assertEquals(1, networkBuffer.mutationCount());
        assertEquals(1, networkBuffer.publicationCount());
    }

    @Test
    void nearLongMaxNetworkBufferAbsorbsSourceOnceWithoutRepeatedMutation() {
        StatefulEndpoint source = new StatefulEndpoint(
                id(2),
                1_024,
                1_024,
                TowerEnergyDirection.SOURCE,
                TowerEnergyEndpointRole.BALANCED);
        StatefulEndpoint networkBuffer = new StatefulEndpoint(
                id(3),
                Long.MAX_VALUE - 2_048,
                Long.MAX_VALUE,
                TowerEnergyDirection.BIDIRECTIONAL,
                TowerEnergyEndpointRole.BUFFER);
        BigInteger initialEnergy = totalEnergy(source, networkBuffer);
        CompensatingTowerEnergyTransaction transaction = new CompensatingTowerEnergyTransaction();

        TowerEnergyTransactionResult firstTick = transaction.execute(List.of(source, networkBuffer));

        assertTrue(firstTick.mutated());
        assertEquals(1_024, firstTick.plannedFe());
        assertEquals(1_024, firstTick.insertedFe());
        assertEquals(0, firstTick.quarantinedFe());
        assertEquals(initialEnergy, totalEnergy(source, networkBuffer));
        assertEquals(1, networkBuffer.mutationCount());
        assertEquals(1, networkBuffer.publicationCount());

        for (int tick = 0; tick < STABLE_TICKS; tick++) {
            TowerEnergyTransactionResult stableTick = transaction.execute(List.of(source, networkBuffer));
            assertFalse(stableTick.mutated());
            assertEquals(0, stableTick.plannedFe());
            assertEquals(0, stableTick.insertedFe());
        }

        assertEquals(initialEnergy, totalEnergy(source, networkBuffer));
        assertEquals(1, networkBuffer.mutationCount());
        assertEquals(1, networkBuffer.publicationCount());
    }

    private static BigInteger totalEnergy(StatefulEndpoint first, StatefulEndpoint second) {
        return BigInteger.valueOf(first.stored()).add(BigInteger.valueOf(second.stored()));
    }

    private static TowerEnergyEndpointId id(int x) {
        return new TowerEnergyEndpointId(new BlockPos(x, 0, 0), Direction.NORTH);
    }

    /**
     * Models a mutable FE endpoint while retaining exact mutation and publication counts across simulated ticks.
     */
    private static final class StatefulEndpoint implements TowerEnergyTransferEndpoint {

        /** Stable planner identity for this test endpoint. */
        private final TowerEnergyEndpointId endpoint;

        /** Fixed non-negative storage capacity. */
        private final long capacity;

        /** Transfer permissions retained for every frozen snapshot. */
        private final TowerEnergyDirection direction;

        /** Planner participation role retained for every frozen snapshot. */
        private final TowerEnergyEndpointRole role;

        /** Current mutable stored FE. */
        private long stored;

        /** Number of real extraction, insertion, or compensation mutations. */
        private int mutationCount;

        /** Number of post-transaction publication callbacks. */
        private int publicationCount;

        private StatefulEndpoint(TowerEnergyEndpointId endpoint,
                                 long stored,
                                 long capacity,
                                 TowerEnergyDirection direction,
                                 TowerEnergyEndpointRole role) {
            this.endpoint = endpoint;
            this.stored = stored;
            this.capacity = capacity;
            this.direction = direction;
            this.role = role;
        }

        @Override
        public TowerEnergyEndpointId endpoint() {
            return this.endpoint;
        }

        @Override
        public TowerEnergyEndpointSnapshot freeze() {
            return new TowerEnergyEndpointSnapshot(
                    this.endpoint,
                    this.stored,
                    this.capacity,
                    this.direction.allowsExtract() ? this.stored : 0,
                    this.direction.allowsReceive() ? this.capacity - this.stored : 0,
                    this.direction,
                    this.role);
        }

        @Override
        public long simulateExtraction(long amount) {
            return this.direction.allowsExtract() ? Math.min(amount, this.stored) : 0;
        }

        @Override
        public long extract(long amount) {
            long extracted = simulateExtraction(amount);
            if (extracted > 0) {
                this.stored -= extracted;
                this.mutationCount++;
            }
            return extracted;
        }

        @Override
        public long compensateExtraction(long amount) {
            return insert(amount);
        }

        @Override
        public long simulateInsertion(long amount) {
            return this.direction.allowsReceive() ? Math.min(amount, this.capacity - this.stored) : 0;
        }

        @Override
        public long insert(long amount) {
            long inserted = simulateInsertion(amount);
            if (inserted > 0) {
                this.stored += inserted;
                this.mutationCount++;
            }
            return inserted;
        }

        @Override
        public void publishMutation() {
            this.publicationCount++;
        }

        @Override
        public String description() {
            return "stateful test endpoint " + this.endpoint;
        }

        private long stored() {
            return this.stored;
        }

        private int mutationCount() {
            return this.mutationCount;
        }

        private int publicationCount() {
            return this.publicationCount;
        }
    }
}
