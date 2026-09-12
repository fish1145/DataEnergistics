package com.fish_dan_.data_energistics.orbital.control.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifies the validated control source to reopen after a client leaves LDLib2 for a fullscreen tactical map.
 *
 * <p>
 * Instances are created with the menu and remain process-local; they are not an authority token. A returned source is
 * sent back as untrusted input and the server rechecks terminal presence or the console dimension, position, distance
 * and access rights before opening anything. The value is immutable and may be retained only for the lifetime of one
 * client map-selection session.
 * </p>
 */
public sealed interface OrbitalControlUiSource {

    Terminal TERMINAL = new Terminal();

    /** Selects the source-agnostic player menu backed by a handheld or Curios terminal. */
    record Terminal() implements OrbitalControlUiSource {}

    /** Selects one concrete control-console location; the server must validate it again on return. */
    record Console(ResourceLocation dimensionId, BlockPos blockPos) implements OrbitalControlUiSource {

        public Console {
            blockPos = blockPos.immutable();
        }
    }
}
