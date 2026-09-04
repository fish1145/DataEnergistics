package com.fish_dan_.data_energistics.api.registry.machine.upload;

/**
 * Prepares one machine-owned state change for an encoded-pattern upload.
 *
 * <p>
 * The callback runs synchronously on the server thread. It may inspect the complete ephemeral context, but must not
 * retain its mutable values. State mutation belongs in the returned prepared change, not in this callback.
 * </p>
 */
@FunctionalInterface
public interface PatternUploadWorkstationAdapter {

    /**
     * Decides whether and how this exact workstation participates in the upload.
     *
     * @param context exact provider, workstation and pattern facts
     * @return pass, rejection, or a prepared reversible change
     */
    PatternUploadWorkstationPreparation prepare(PatternUploadWorkstationContext context);
}
