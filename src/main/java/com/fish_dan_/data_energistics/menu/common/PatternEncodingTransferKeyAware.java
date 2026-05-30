package com.fish_dan_.data_energistics.menu.common;

import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

public interface PatternEncodingTransferKeyAware {

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
