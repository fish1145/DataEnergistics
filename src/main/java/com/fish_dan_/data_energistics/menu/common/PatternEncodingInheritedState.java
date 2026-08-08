package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the state woven into AE2's base pattern menu to subclasses that maintain a specialized synchronized view.
 *
 * <p>
 * The interface replaces reflective access to mixin-owned fields. It is internal to the menu implementation and
 * deliberately exposes only the state that must remain coherent between the base menu and a subclass override.
 * </p>
 */
@ApiStatus.Internal
public interface PatternEncodingInheritedState {

    /** Returns the provider rows synchronized by the base menu. */
    @NotNull
    PatternEncodingPreviewMenu.SyncedPatternProviderList dataEnergistics$getInheritedSyncedPatternProviders();

    /** Returns the pending workstation held by the base menu. */
    @Nullable
    ResourceLocation dataEnergistics$getInheritedPendingPatternSource();

    /** Updates the pending workstation used by base-menu actions. */
    void dataEnergistics$setInheritedPendingPatternSource(@Nullable ResourceLocation workstationId);

    /** Returns the last confirmed workstation held by the base menu. */
    @Nullable
    ResourceLocation dataEnergistics$getInheritedLastEncodedPatternSource();

    /** Updates the last confirmed workstation used by base-menu actions. */
    void dataEnergistics$setInheritedLastEncodedPatternSource(@Nullable ResourceLocation workstationId);

    /** Returns whether the base menu may write pattern-source information. */
    boolean dataEnergistics$isInheritedPatternSourceEnabled();

    /** Updates whether the base menu may write pattern-source information. */
    void dataEnergistics$setInheritedPatternSourceEnabled(boolean enabled);

    /** Returns whether the base menu may upload encoded patterns. */
    boolean dataEnergistics$isInheritedUploadEnabled();

    /** Updates whether the base menu may upload encoded patterns. */
    void dataEnergistics$setInheritedUploadEnabled(boolean enabled);
}
