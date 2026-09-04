package com.fish_dan_.data_energistics.api.registry.machine.upload;

import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineScope;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable common-setup declaration for one block-entity workstation upload adapter.
 *
 * @param registrationId    globally unique plugin-owned registration ID
 * @param blockEntityTypeId exact registered block-entity type handled by the adapter
 * @param scope             identity of the machine state changed by one prepared transaction
 * @param adapter           server-thread upload preflight callback
 */
public record PatternUploadWorkstationRegistration(ResourceLocation registrationId,
                                                   ResourceLocation blockEntityTypeId,
                                                   CraftingMachineScope scope,
                                                   PatternUploadWorkstationAdapter adapter) {

    public PatternUploadWorkstationRegistration {
        Objects.requireNonNull(registrationId, "Pattern upload workstation registration ID");
        Objects.requireNonNull(blockEntityTypeId, "Pattern upload workstation block-entity type ID");
        Objects.requireNonNull(scope, "Pattern upload workstation scope");
        Objects.requireNonNull(adapter, "Pattern upload workstation adapter");
    }

    /** Creates a registration whose upload state is shared by every face of one block entity. */
    public static PatternUploadWorkstationRegistration blockEntity(
                                                                   ResourceLocation registrationId,
                                                                   ResourceLocation blockEntityTypeId,
                                                                   PatternUploadWorkstationAdapter adapter) {
        return new PatternUploadWorkstationRegistration(
                registrationId,
                blockEntityTypeId,
                CraftingMachineScope.BLOCK_ENTITY,
                adapter);
    }

    /** Creates a registration whose upload state is isolated for each input face. */
    public static PatternUploadWorkstationRegistration inputSide(
                                                                 ResourceLocation registrationId,
                                                                 ResourceLocation blockEntityTypeId,
                                                                 PatternUploadWorkstationAdapter adapter) {
        return new PatternUploadWorkstationRegistration(
                registrationId,
                blockEntityTypeId,
                CraftingMachineScope.INPUT_SIDE,
                adapter);
    }
}
