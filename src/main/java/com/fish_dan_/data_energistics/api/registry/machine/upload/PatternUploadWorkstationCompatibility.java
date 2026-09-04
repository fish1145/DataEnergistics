package com.fish_dan_.data_energistics.api.registry.machine.upload;

/** Read-only compatibility of one live workstation variant with the currently encoded pattern. */
public enum PatternUploadWorkstationCompatibility {
    /** No encoded pattern or insufficient information is available for a safe decision. */
    UNKNOWN,
    /** The live workstation variant can execute the server-validated encoded pattern. */
    COMPATIBLE,
    /** The machine recognizes the pattern, but its live variant cannot execute it. */
    INCOMPATIBLE
}
