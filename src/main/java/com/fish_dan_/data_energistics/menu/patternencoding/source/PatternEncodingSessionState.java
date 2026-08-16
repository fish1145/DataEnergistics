package com.fish_dan_.data_energistics.menu.patternencoding.source;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PatternEncodingSessionState {

    private static final Map<UUID, ResourceLocation> LAST_ENCODED_PATTERN_SOURCES = new ConcurrentHashMap<>();
    private static final Map<UUID, GenericStack> PENDING_TRANSFER_KEY_INPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, GenericStack> PENDING_TRANSFER_KEY_OUTPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<GenericStack>> PENDING_TRANSFER_FLUID_INPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<GenericStack>> PENDING_TRANSFER_FLUID_OUTPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, DataRipperTransferMetadata> PENDING_DATA_RIPPER_TRANSFERS = new ConcurrentHashMap<>();

    private PatternEncodingSessionState() {}

    @Nullable
    static ResourceLocation getLastEncodedPatternSource(UUID playerId) {
        return LAST_ENCODED_PATTERN_SOURCES.get(playerId);
    }

    static void setLastEncodedPatternSource(UUID playerId, ResourceLocation workstationId) {
        LAST_ENCODED_PATTERN_SOURCES.put(playerId, workstationId);
    }

    static void clearLastEncodedPatternSource(UUID playerId) {
        LAST_ENCODED_PATTERN_SOURCES.remove(playerId);
    }

    @Nullable
    static GenericStack getPendingTransferKeyInput(UUID playerId) {
        return PENDING_TRANSFER_KEY_INPUTS.get(playerId);
    }

    static void setPendingTransferKeyInput(UUID playerId, GenericStack keyInput) {
        PENDING_TRANSFER_KEY_INPUTS.put(playerId, keyInput);
    }

    static void clearPendingTransferKeyInput(UUID playerId) {
        PENDING_TRANSFER_KEY_INPUTS.remove(playerId);
    }

    @Nullable
    static GenericStack getPendingTransferKeyOutput(UUID playerId) {
        return PENDING_TRANSFER_KEY_OUTPUTS.get(playerId);
    }

    static void setPendingTransferKeyOutput(UUID playerId, GenericStack keyOutput) {
        PENDING_TRANSFER_KEY_OUTPUTS.put(playerId, keyOutput);
    }

    static void clearPendingTransferKeyOutput(UUID playerId) {
        PENDING_TRANSFER_KEY_OUTPUTS.remove(playerId);
    }

    @Nullable
    static List<GenericStack> getPendingTransferFluidInputs(UUID playerId) {
        return PENDING_TRANSFER_FLUID_INPUTS.get(playerId);
    }

    static void setPendingTransferFluidInputs(UUID playerId, List<GenericStack> fluidInputs) {
        PENDING_TRANSFER_FLUID_INPUTS.put(playerId, fluidInputs);
    }

    static void clearPendingTransferFluidInputs(UUID playerId) {
        PENDING_TRANSFER_FLUID_INPUTS.remove(playerId);
    }

    @Nullable
    static List<GenericStack> getPendingTransferFluidOutputs(UUID playerId) {
        return PENDING_TRANSFER_FLUID_OUTPUTS.get(playerId);
    }

    static void setPendingTransferFluidOutputs(UUID playerId, List<GenericStack> fluidOutputs) {
        PENDING_TRANSFER_FLUID_OUTPUTS.put(playerId, fluidOutputs);
    }

    static void clearPendingTransferFluidOutputs(UUID playerId) {
        PENDING_TRANSFER_FLUID_OUTPUTS.remove(playerId);
    }

    @Nullable
    static DataRipperTransferMetadata getPendingDataRipperTransfer(UUID playerId, int containerId) {
        DataRipperTransferMetadata metadata = PENDING_DATA_RIPPER_TRANSFERS.get(playerId);
        return metadata != null && metadata.containerId() == containerId ? metadata : null;
    }

    static void setPendingDataRipperTransfer(UUID playerId, DataRipperTransferMetadata metadata) {
        PENDING_DATA_RIPPER_TRANSFERS.put(playerId, metadata);
    }

    static void clearPendingDataRipperTransfer(UUID playerId) {
        PENDING_DATA_RIPPER_TRANSFERS.remove(playerId);
        clearPendingTransferKeyInput(playerId);
        clearPendingTransferKeyOutput(playerId);
        clearPendingTransferFluidInputs(playerId);
        clearPendingTransferFluidOutputs(playerId);
    }

    static void clear(UUID playerId) {
        LAST_ENCODED_PATTERN_SOURCES.remove(playerId);
        PENDING_TRANSFER_KEY_INPUTS.remove(playerId);
        PENDING_TRANSFER_KEY_OUTPUTS.remove(playerId);
        PENDING_TRANSFER_FLUID_INPUTS.remove(playerId);
        PENDING_TRANSFER_FLUID_OUTPUTS.remove(playerId);
        PENDING_DATA_RIPPER_TRANSFERS.remove(playerId);
    }

    record DataRipperTransferMetadata(int containerId,
                                      @Nullable GenericStack keyInput,
                                      @Nullable GenericStack keyOutput,
                                      List<GenericStack> fluidInputs,
                                      List<GenericStack> fluidOutputs) {

        DataRipperTransferMetadata {
            fluidInputs = List.copyOf(fluidInputs);
            fluidOutputs = List.copyOf(fluidOutputs);
        }
    }
}
