package com.fish_dan_.data_energistics.common.entrypoint.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacity;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityContext;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityScope;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Installed runtime index and isolated invocation boundary for machine-capacity plugin callbacks. */
public final class CraftingMachineCapacityAdapters {

    private static Object2ObjectMap<ResourceLocation, CraftingMachineCapacityRegistration> registrations = Object2ObjectMaps.emptyMap();
    private static boolean installed;

    private CraftingMachineCapacityAdapters() {}

    /** Installs the immutable common-setup registration snapshot exactly once. */
    public static synchronized void install(List<CraftingMachineCapacityRegistration> declarations) {
        if (installed) {
            throw new IllegalStateException("Crafting machine capacity adapters are already installed");
        }
        Object2ObjectMap<ResourceLocation, CraftingMachineCapacityRegistration> indexed = new Object2ObjectLinkedOpenHashMap<>(declarations.size());
        for (CraftingMachineCapacityRegistration declaration : declarations) {
            if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(declaration.blockEntityTypeId())) {
                throw new IllegalStateException(
                        "Unknown crafting machine capacity block-entity type: " + declaration.blockEntityTypeId());
            }
            CraftingMachineCapacityRegistration existing = indexed.putIfAbsent(
                    declaration.blockEntityTypeId(), declaration);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate frozen crafting machine capacity type: " + declaration.blockEntityTypeId());
            }
        }
        registrations = Object2ObjectMaps.unmodifiable(indexed);
        installed = true;
    }

    /**
     * Captures an applicable machine capacity, returning {@code null} for unregistered or non-applicable machines.
     * Callback failures are isolated as authoritative zero capacity so native insertion simulation cannot bypass the
     * registered safety boundary.
     */
    @Nullable
    public static Observation capture(Level level,
                                      BlockPos machinePosition,
                                      Direction inputSide,
                                      IPatternDetails patternDetails,
                                      KeyCounter[] prototype,
                                      long requestedCrafts) {
        BlockEntity machine = level.getBlockEntity(machinePosition);
        if (machine == null) {
            return null;
        }
        ResourceLocation blockEntityTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType());
        if (!registrations.containsKey(blockEntityTypeId)) {
            return null;
        }
        CraftingMachineCapacityRegistration registration = registrations.get(blockEntityTypeId);
        try {
            Optional<CraftingMachineCapacity> resolved = Objects.requireNonNull(
                    registration.adapter().capture(new CraftingMachineCapacityContext(
                            level,
                            machinePosition,
                            inputSide,
                            machine,
                            patternDetails,
                            prototype,
                            requestedCrafts)),
                    "Crafting machine capacity adapter returned null");
            if (resolved.isEmpty()) {
                return null;
            }
            long remaining = resolved.get().remainingLogicalCrafts();
            return new Observation(registration.scope(), Math.min(remaining, requestedCrafts));
        } catch (RuntimeException | LinkageError exception) {
            Data_Energistics.LOGGER.error(
                    "Crafting machine capacity adapter {} failed for type {} at {} {} side {} and pattern {}; treating the target as full",
                    registration.registrationId(),
                    blockEntityTypeId,
                    level.dimension().location(),
                    machinePosition,
                    inputSide,
                    patternDetails.getDefinition(),
                    exception);
            return new Observation(registration.scope(), 0L);
        }
    }

    /** Applicable registered capacity and the identity scope used for shared-machine accounting. */
    public record Observation(CraftingMachineCapacityScope scope, long remainingLogicalCrafts) {}
}
