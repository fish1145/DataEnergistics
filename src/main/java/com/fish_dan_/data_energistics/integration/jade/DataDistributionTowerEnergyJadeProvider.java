package com.fish_dan_.data_energistics.integration.jade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;
import java.util.Objects;

public class DataDistributionTowerEnergyJadeProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, EnergyView> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_distribution_tower_energy");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof DataDistributionTowerBlockEntity tower)) {
            return List.of();
        }

        long stored = tower.getAvailableFeForUi();
        long capacity = Math.max(tower.getEnergyCapacityForUi(), stored);
        if (capacity <= 0) {
            return List.of();
        }

        return List.of(new ViewGroup<>(List.of(EnergyView.of(stored, capacity))));
    }

    @Override
    public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return groups.stream()
                .map(group -> new ClientViewGroup<>(group.views.stream()
                        .map(tag -> EnergyView.read(tag, "FE"))
                        .filter(Objects::nonNull)
                        .toList()))
                .toList();
    }
}
