package com.fish_dan_.data_energistics.api.registry.machine.upload;

import java.util.Objects;

/** Typed result of one read-only live workstation inspection. */
public sealed interface PatternUploadWorkstationInspection
                                                           permits PatternUploadWorkstationInspection.Pass, PatternUploadWorkstationInspection.Variant {

    /** Returns the shared result for a machine registration that does not expose a panel variant. */
    static PatternUploadWorkstationInspection pass() {
        return Pass.INSTANCE;
    }

    /** Creates a classified live variant. */
    static PatternUploadWorkstationInspection variant(PatternUploadWorkstationVariant variant,
                                                      PatternUploadWorkstationCompatibility compatibility) {
        return new Variant(variant, compatibility);
    }

    /** This registration contributes no workstation variant to provider-panel grouping. */
    enum Pass implements PatternUploadWorkstationInspection {
        INSTANCE
    }

    /** One live variant and its compatibility with the current encoded pattern. */
    record Variant(PatternUploadWorkstationVariant variant,
                   PatternUploadWorkstationCompatibility compatibility)
            implements PatternUploadWorkstationInspection {

        public Variant {
            Objects.requireNonNull(variant, "Pattern upload workstation variant");
            Objects.requireNonNull(compatibility, "Pattern upload workstation compatibility");
        }
    }
}
