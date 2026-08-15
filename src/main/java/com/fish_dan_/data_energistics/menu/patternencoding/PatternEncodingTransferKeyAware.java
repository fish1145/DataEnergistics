package com.fish_dan_.data_energistics.menu.patternencoding;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

/**
 * Bridges client recipe-viewer metadata to the server-owned pattern encoding menu state.
 *
 * <p>
 * The methods are called only while a live menu is open. Implementations must serialize client-originated values
 * before they cross the client-action boundary and must not treat them as trusted server state.
 * </p>
 */
public interface PatternEncodingTransferKeyAware {

    /**
     * Sends one complete Data Ripper transfer snapshot, or an empty payload to clear the current snapshot.
     */
    void dataEnergistics$sendDataRipperTransferMetadataAction(@Nullable String serializedMetadata);

    void dataEnergistics$sendTransferKeyInputAction(@Nullable String serializedKeyInput);

    void dataEnergistics$sendTransferKeyOutputAction(@Nullable String serializedKeyOutput);

    void dataEnergistics$sendTransferFluidInputsAction(@Nullable String serializedFluidInputs);

    void dataEnergistics$sendTransferFluidOutputsAction(@Nullable String serializedFluidOutputs);

    @Nullable
    GenericStack dataEnergistics$getDisplayedTransferKeyInput();

    @Nullable
    GenericStack dataEnergistics$getDisplayedTransferKeyOutput();

    void dataEnergistics$setDisplayedTransferKeyInputSerialized(@Nullable String serializedKeyInput);

    void dataEnergistics$setDisplayedTransferKeyOutputSerialized(@Nullable String serializedKeyOutput);

    @Nullable
    String dataEnergistics$getDisplayedTransferKeyInputSerialized();

    @Nullable
    String dataEnergistics$getDisplayedTransferKeyOutputSerialized();
}
