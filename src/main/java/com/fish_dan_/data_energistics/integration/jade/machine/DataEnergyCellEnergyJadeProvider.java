package com.fish_dan_.data_energistics.integration.jade.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import appeng.blockentity.networking.EnergyCellBlockEntity;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;
import java.util.Objects;

/** Provides the AE energy storage view for the data energy cell. */
public final class DataEnergyCellEnergyJadeProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, EnergyView> {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "data_energy_cell_energy");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof EnergyCellBlockEntity energyCell) || energyCell.getBlockState().getBlock() != DEBlocks.DATA_ENERGY_CELL.get()) {
            return List.of();
        }

        long stored = Math.max(0L, Math.round(energyCell.getAECurrentPower()));
        long capacity = Math.max(0L, Math.round(energyCell.getAEMaxPower()));
        capacity = Math.max(capacity, stored);
        if (capacity <= 0L) {
            return List.of();
        }

        return List.of(new ViewGroup<>(List.of(EnergyView.of(stored, capacity))));
    }

    @Override
    public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return groups.stream()
                .map(group -> new ClientViewGroup<>(group.views.stream()
                        .map(tag -> EnergyView.read(tag, "AE"))
                        .filter(Objects::nonNull)
                        .toList()))
                .toList();
    }
}
