package com.fish_dan_.data_energistics.api.registry.machine.upload;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Typed result of one workstation upload preflight.
 *
 * <p>
 * {@link Pass} means the registered machine type deliberately does not handle this pattern. {@link Rejected} means
 * the machine recognizes the upload but cannot accept it safely. {@link Prepared} supplies the reversible state
 * transition that the runtime coordinates with the provider inventory commit.
 * </p>
 */
public sealed interface PatternUploadWorkstationPreparation
                                                            permits PatternUploadWorkstationPreparation.Pass,
                                                            PatternUploadWorkstationPreparation.Prepared,
                                                            PatternUploadWorkstationPreparation.Rejected {

    /** Returns the shared result used when the adapter does not apply to this pattern. */
    static PatternUploadWorkstationPreparation pass() {
        return Pass.INSTANCE;
    }

    /** Creates a result containing one prepared reversible machine change. */
    static PatternUploadWorkstationPreparation prepared(PreparedPatternUploadChange change) {
        return new Prepared(change);
    }

    /** Creates an authoritative rejection with a user-visible reason. */
    static PatternUploadWorkstationPreparation rejected(Component message) {
        return new Rejected(message);
    }

    /** The registered machine type does not handle this exact pattern. */
    enum Pass implements PatternUploadWorkstationPreparation {
        INSTANCE
    }

    /** A machine-owned state change ready to be coordinated with provider inventory mutation. */
    record Prepared(PreparedPatternUploadChange change) implements PatternUploadWorkstationPreparation {

        public Prepared {
            Objects.requireNonNull(change, "Prepared pattern upload change");
        }
    }

    /** An authoritative machine rejection that prevents this provider leaf from receiving the pattern. */
    record Rejected(Component message) implements PatternUploadWorkstationPreparation {

        public Rejected {
            message = Objects.requireNonNull(message, "Pattern upload rejection message").copy();
        }

        /** Returns a copy so a retained plugin result cannot mutate the runtime-owned rejection reason. */
        @Override
        public Component message() {
            return this.message.copy();
        }
    }
}
