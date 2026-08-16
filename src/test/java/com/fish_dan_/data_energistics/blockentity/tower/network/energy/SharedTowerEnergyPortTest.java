package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointRole;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SharedTowerEnergyPortTest {

    @Test
    void sharesEnergyAcrossDimensionsWhileExcludingOnlyTheAccessingNeighbor() {
        ResourceLocation localDimension = ResourceLocation.fromNamespaceAndPath("test", "local");
        ResourceLocation remoteDimension = ResourceLocation.fromNamespaceAndPath("test", "remote");
        BlockPos sharedPosition = new BlockPos(1, 2, 3);
        StatefulEndpoint local = new StatefulEndpoint(localDimension, sharedPosition, 40, 100);
        StatefulEndpoint remote = new StatefulEndpoint(remoteDimension, sharedPosition, 60, 100);
        SharedTowerEnergyPort port = new SharedTowerEnergyPort(() -> 1);
        port.replaceEndpoints(List.of(local, remote));

        TowerEnergyAccessSnapshot remoteOnly = port.snapshot(localDimension, sharedPosition);

        assertEquals(new TowerEnergyAccessSnapshot(60, 100, 40, true, true), remoteOnly);
        assertEquals(20, port.extract(20, false, localDimension, sharedPosition));
        assertEquals(30, port.insert(30, false, localDimension, sharedPosition));
        assertEquals(40, local.stored());
        assertEquals(70, remote.stored());
        assertEquals(0, local.publications());
        assertEquals(2, remote.publications());
        assertEquals(
                new TowerEnergyAccessSnapshot(110, 200, 90, true, true),
                port.snapshot(localDimension, null));
    }

    private static final class StatefulEndpoint implements TowerEnergyTransferEndpoint {

        private final TowerEnergyEndpointId endpoint;
        private final long capacity;
        private long stored;
        private int publications;

        private StatefulEndpoint(ResourceLocation dimensionId, BlockPos position, long stored, long capacity) {
            this.endpoint = new TowerEnergyEndpointId(dimensionId, position, null, 0);
            this.stored = stored;
            this.capacity = capacity;
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
                    this.stored,
                    this.capacity - this.stored,
                    TowerEnergyDirection.BIDIRECTIONAL,
                    TowerEnergyEndpointRole.BALANCED);
        }

        @Override
        public long simulateExtraction(long amount) {
            return Math.min(amount, this.stored);
        }

        @Override
        public long extract(long amount) {
            long extracted = simulateExtraction(amount);
            this.stored -= extracted;
            return extracted;
        }

        @Override
        public long compensateExtraction(long amount) {
            return insert(amount);
        }

        @Override
        public long simulateInsertion(long amount) {
            return Math.min(amount, this.capacity - this.stored);
        }

        @Override
        public long insert(long amount) {
            long inserted = simulateInsertion(amount);
            this.stored += inserted;
            return inserted;
        }

        @Override
        public void publishMutation() {
            this.publications++;
        }

        @Override
        public String description() {
            return "shared tower port test endpoint " + this.endpoint;
        }

        private long stored() {
            return this.stored;
        }

        private int publications() {
            return this.publications;
        }
    }
}
