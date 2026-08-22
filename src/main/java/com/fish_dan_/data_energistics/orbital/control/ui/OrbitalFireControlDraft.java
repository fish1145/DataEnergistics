package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.OrbitalTargetYMode;

import net.minecraft.resources.ResourceLocation;

/** Immutable client draft carried across a fullscreen tactical-map selection. */
public record OrbitalFireControlDraft(
                                      OrbitalAttackMode mode,
                                      String dimension,
                                      int targetX,
                                      int targetZ,
                                      OrbitalTargetYMode targetYMode,
                                      int targetYValue,
                                      int directedRadius,
                                      int depthCode) {

    public static final int NO_DIRECTED_DEPTH = -1;

    /** Replaces only the map-owned fields while preserving the operator's mode, Y and directed-energy choices. */
    public OrbitalFireControlDraft withMapTarget(ResourceLocation dimensionId, int targetX, int targetZ) {
        return new OrbitalFireControlDraft(
                this.mode,
                dimensionId.toString(),
                targetX,
                targetZ,
                this.targetYMode,
                this.targetYValue,
                this.directedRadius,
                this.depthCode);
    }

    /** Creates the conservative draft used by a third-party map's direct context-menu entry. */
    public static OrbitalFireControlDraft directKineticTarget(ResourceLocation dimensionId, int targetX, int targetZ) {
        return new OrbitalFireControlDraft(
                OrbitalAttackMode.KINETIC,
                dimensionId.toString(),
                targetX,
                targetZ,
                OrbitalTargetYMode.SURFACE_OFFSET,
                0,
                0,
                NO_DIRECTED_DEPTH);
    }
}
