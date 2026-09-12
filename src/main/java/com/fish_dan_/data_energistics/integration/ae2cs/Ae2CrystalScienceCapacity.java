package com.fish_dan_.data_energistics.integration.ae2cs;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacity;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityContext;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.integration.ae2cs.capacity.AecsInputCapacity;

import appeng.api.inventories.InternalInventory;

import net.minecraft.resources.ResourceLocation;

import io.github.lounode.ae2cs.common.block.entity.AENetworkedComponentBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CircuitEtcherBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalAggregatorBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalGrowthChamberBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalPulverizerBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.EntropyVariationReactionChamberBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.QuartzGrindstoneBlockEntity;
import io.github.lounode.ae2cs.common.machine.component.SideConfigComponent;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/** Publishes optional AECS machine capacities independently of Data Energistics machine registrations. */
@DataEnergisticsEntrypoint(requiredMods = "ae2cs")
@NullMarked
public final class Ae2CrystalScienceCapacity implements DataEnergisticsPlugin {

    @Override
    public void register(DataEnergisticsRegistry registry) {
        for (String id : List.of("circuit_etcher", "crystal_aggregator", "crystal_pulverizer",
                "quartz_grindstone", "crystal_growth_chamber", "entropy_variation_reaction_chamber")) {
            registry.craftingMachines().registerCapacity(CraftingMachineCapacityRegistration.blockEntity(
                    Data_Energistics.id("ae2cs_" + id + "_capacity"),
                    ResourceLocation.fromNamespaceAndPath("ae2cs", id),
                    Ae2CrystalScienceCapacity::capture));
        }
    }

    private static Optional<CraftingMachineCapacity> capture(CraftingMachineCapacityContext context) {
        var machine = (AENetworkedComponentBlockEntity) context.machine();
        var sides = machine.getMachineComponents().getService(SideConfigComponent.class);
        if (!sides.get(context.inputSide()).allowInsert()) {
            return Optional.of(new CraftingMachineCapacity(0L));
        }
        long capacity;
        if (machine instanceof EntropyVariationReactionChamberBlockEntity chamber) {
            capacity = AecsInputCapacity.capture(chamber.getInputInv(), context.prototype(), context.requestedCrafts());
        } else {
            InternalInventory inventory = switch (machine) {
                case CircuitEtcherBlockEntity etcher -> etcher.getInputInv();
                case CrystalAggregatorBlockEntity aggregator -> aggregator.getInputInv();
                case CrystalPulverizerBlockEntity pulverizer -> pulverizer.getInputInv();
                // The separate work slot is not additional queue space: it may contain the active recipe.
                case QuartzGrindstoneBlockEntity grindstone -> grindstone.getInputInv();
                case CrystalGrowthChamberBlockEntity growth -> growth.getInternalInventory();
                default -> throw new IllegalArgumentException("Unsupported AECS capacity machine: " + machine.getType());
            };
            capacity = AecsInputCapacity.capture(inventory, context.prototype(), context.requestedCrafts());
        }
        return Optional.of(new CraftingMachineCapacity(capacity));
    }
}
