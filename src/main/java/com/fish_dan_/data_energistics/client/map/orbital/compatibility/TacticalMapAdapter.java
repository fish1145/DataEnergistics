package com.fish_dan_.data_energistics.client.map.orbital.compatibility;

import com.fish_dan_.data_energistics.client.map.orbital.OrbitalMapSelectionClientSession;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-thread boundary shared by the embedded tactical map and optional fullscreen map mods.
 *
 * <p>
 * Adapters only present a coordinate-selection surface. They never grant weapon access, create a server preview or
 * confirm an attack. A fullscreen adapter reports the selected dimension/X/Z through
 * {@code OrbitalMapSelectionClientSession}; the existing server preview path then validates every untrusted value.
 * Registration and invocation occur on the Minecraft client thread. Adapter instances live for the client process and
 * must not retain a world, player or screen beyond a single {@link #startSelection(Minecraft, UUID)} call.
 * </p>
 */
public interface TacticalMapAdapter {

    /** Returns the stable provider identifier used to bind one-shot selection sessions to their originating map. */
    ResourceLocation id();

    /** Returns the localized provider label shown in the LDLib2 fire-control selector. */
    Component displayName();

    /**
     * Presents this provider after the caller has installed a one-shot selection session.
     *
     * @param minecraft    active client; its player and current screen are available when this method is called
     * @param sessionToken one-shot token that must be supplied when a later map click completes this selection
     * @return whether selection remains embedded, is awaiting an external map, or failed to start
     * @throws RuntimeException when a provider API rejects the launch; the registry catches, logs and disables it
     */
    SelectionStart startSelection(Minecraft minecraft, UUID sessionToken);

    /** Returns whether this provider currently owns the one-shot selection session. */
    default boolean isAwaitingSelection(UUID sessionToken) {
        return OrbitalMapSelectionClientSession.isAwaiting(id(), sessionToken);
    }

    /**
     * Completes this provider's active left-click selection and requests the validated control source to reopen.
     *
     * @return {@code true} only when this provider owned a live selection session
     */
    default boolean completeSelection(
                                      UUID sessionToken,
                                      ResourceLocation dimensionId,
                                      int targetX,
                                      int targetZ) {
        return OrbitalMapSelectionClientSession.completeSelection(
                id(),
                sessionToken,
                dimensionId,
                targetX,
                targetZ);
    }

    /**
     * Opens a preview from this provider's context menu, preserving an active draft or using a conservative default.
     *
     * @return {@code true} when a return menu was requested; {@code false} when another provider owns the session or
     *         no terminal source is available for a direct preview
     */
    default boolean openPreview(ResourceLocation dimensionId, int targetX, int targetZ) {
        return OrbitalMapSelectionClientSession.openPreview(id(), dimensionId, targetX, targetZ);
    }

    enum SelectionStart {
        EMBEDDED,
        EXTERNAL_WAITING,
        FAILED
    }
}
