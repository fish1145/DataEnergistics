package com.fish_dan_.data_energistics.api.registry.machine.capacity;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable common-setup declaration for one block-entity machine capacity adapter.
 *
 * @param registrationId    globally unique plugin-owned registration ID
 * @param blockEntityTypeId exact registered block-entity type handled by the adapter
 * @param scope             identity of the shared physical capacity pool
 * @param adapter           read-only capacity callback
 */
public record CraftingMachineCapacityRegistration(ResourceLocation registrationId,
                                                  ResourceLocation blockEntityTypeId,
                                                  CraftingMachineCapacityScope scope,
                                                  CraftingMachineCapacityAdapter adapter) {

    public CraftingMachineCapacityRegistration {
        Objects.requireNonNull(registrationId, "Crafting machine capacity registration ID");
        Objects.requireNonNull(blockEntityTypeId, "Crafting machine block-entity type ID");
        Objects.requireNonNull(scope, "Crafting machine capacity scope");
        Objects.requireNonNull(adapter, "Crafting machine capacity adapter");
    }

    /** Creates a registration whose capacity is shared by every face of one block entity. */
    public static CraftingMachineCapacityRegistration blockEntity(
                                                                  ResourceLocation registrationId,
                                                                  ResourceLocation blockEntityTypeId,
                                                                  CraftingMachineCapacityAdapter adapter) {
        return new CraftingMachineCapacityRegistration(
                registrationId,
                blockEntityTypeId,
                CraftingMachineCapacityScope.BLOCK_ENTITY,
                adapter);
    }

    /** Creates a registration whose capacity is isolated for each input face. */
    public static CraftingMachineCapacityRegistration inputSide(
                                                                ResourceLocation registrationId,
                                                                ResourceLocation blockEntityTypeId,
                                                                CraftingMachineCapacityAdapter adapter) {
        return new CraftingMachineCapacityRegistration(
                registrationId,
                blockEntityTypeId,
                CraftingMachineCapacityScope.INPUT_SIDE,
                adapter);
    }
}
