package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.connector.EnergyTransferDirection;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Connector editing and rendering view backed by one distribution tower's synchronized bindings. */
record TowerConnectorEndpoint(DataDistributionTowerBlockEntity tower, RemoteLinkConnectorData selection)
        implements ConnectorEndpoint {

    @Override
    public ObjectList<ConnectorLink> bindingsFast() {
        ObjectArrayList<ConnectorLink> links = new ObjectArrayList<>();
        for (TowerBinding binding : this.tower.towerBindings()) {
            BlockPos relative = binding.anchor().subtract(this.tower.getBlockPos());
            links.add(new ConnectorLink(binding.anchor(), facingTower(relative),
                    binding.energyDirection() == EnergyTransferDirection.INPUT ? ConnectorMode.INPUT : ConnectorMode.PULL));
        }
        return links;
    }

    @Override
    public ConnectorMode mode() {
        return ConnectorMode.INPUT;
    }

    @Override
    public void setMode(ConnectorMode mode) {}

    @Override
    public int slotCount() {
        return 0;
    }

    @Override
    public boolean toggle(BlockPos position, Direction side, int slot) {
        if (this.tower.removeTargetFromConnector(position)) {
            return false;
        }
        this.tower.bindTargetFromConnector(position, this.selection.energyDirection());
        return true;
    }

    private static Direction facingTower(BlockPos relativeTarget) {
        int x = Math.abs(relativeTarget.getX());
        int y = Math.abs(relativeTarget.getY());
        int z = Math.abs(relativeTarget.getZ());
        if (x >= y && x >= z) return relativeTarget.getX() >= 0 ? Direction.WEST : Direction.EAST;
        if (y >= z) return relativeTarget.getY() >= 0 ? Direction.DOWN : Direction.UP;
        return relativeTarget.getZ() >= 0 ? Direction.NORTH : Direction.SOUTH;
    }
}
