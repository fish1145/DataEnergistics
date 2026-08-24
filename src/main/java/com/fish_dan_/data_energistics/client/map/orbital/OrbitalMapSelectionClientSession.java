package com.fish_dan_.data_energistics.client.map.orbital;

import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlConsoleOpenPayload;
import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlOpenPayload;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalAccess;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlDraft;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiSource;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiSource.Console;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlUiSource.Terminal;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Owns one expiring external-map selection and at most one draft waiting for the reopened LDLib2 control menu. */
public final class OrbitalMapSelectionClientSession {

    private static final long TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2L);

    private static @Nullable ActiveSelection active;
    private static @Nullable PendingSelection pending;

    private OrbitalMapSelectionClientSession() {}

    /** Replaces any older selection with a fresh provider-bound token and immutable fire-control draft. */
    public static UUID begin(
                             ResourceLocation providerId,
                             @Nullable UUID weaponId,
                             OrbitalFireControlDraft draft,
                             OrbitalControlUiSource source) {
        long now = Util.getNanos();
        UUID sessionToken = UUID.randomUUID();
        active = new ActiveSelection(sessionToken, providerId, weaponId, draft, source, now);
        pending = null;
        return sessionToken;
    }

    /** Returns whether the named provider currently owns a non-expired left-click selection. */
    public static boolean isAwaiting(ResourceLocation providerId, UUID sessionToken) {
        expire();
        return active != null && active.providerId().equals(providerId) && active.token().equals(sessionToken);
    }

    /** Completes only the active session owned by this provider; ordinary map clicks remain untouched otherwise. */
    public static boolean completeSelection(
                                            ResourceLocation providerId,
                                            UUID sessionToken,
                                            ResourceLocation dimensionId,
                                            int targetX,
                                            int targetZ) {
        expire();
        ActiveSelection selection = active;
        if (selection == null ||
                !selection.providerId().equals(providerId) ||
                !selection.token().equals(sessionToken)) {
            return false;
        }
        finish(selection, selection.draft().withMapTarget(dimensionId, targetX, targetZ));
        return true;
    }

    /**
     * Creates the same preview flow from a map context menu, preserving an active draft or using a kinetic default.
     */
    public static boolean openPreview(
                                      ResourceLocation providerId,
                                      ResourceLocation dimensionId,
                                      int targetX,
                                      int targetZ) {
        expire();
        ActiveSelection selection = active;
        if (selection != null) {
            if (!selection.providerId().equals(providerId)) {
                return false;
            }
            finish(selection, selection.draft().withMapTarget(dimensionId, targetX, targetZ));
            return true;
        }
        if (canOpenDirectPreview()) {
            long now = Util.getNanos();
            PendingSelection direct = new PendingSelection(
                    null,
                    OrbitalFireControlDraft.directKineticTarget(dimensionId, targetX, targetZ),
                    OrbitalControlUiSource.TERMINAL,
                    now);
            pending = direct;
            requestControlMenu(direct.source());
            return true;
        }
        return false;
    }

    /** Returns whether a third-party map should expose its direct preview action for the current client player. */
    public static boolean canOpenDirectPreview() {
        var player = Minecraft.getInstance().player;
        return player != null && OrbitalControlTerminalAccess.hasTerminal(player);
    }

    /** Consumes the one pending draft only when it still targets the selected or an unspecified weapon. */
    public static @Nullable OrbitalFireControlDraft takePending(@Nullable UUID selectedWeaponId) {
        expire();
        PendingSelection selection = pending;
        if (selection == null || selectedWeaponId == null) {
            return null;
        }
        pending = null;
        return selection.weaponId() == null || Objects.equals(selection.weaponId(), selectedWeaponId) ?
                selection.draft() : null;
    }

    /** Returns whether a validated menu return is waiting, allowing LDLib2 to reopen directly on fire control. */
    public static boolean hasPending() {
        expire();
        return pending != null;
    }

    /** Expires old state on the client tick even when no map event is firing. */
    public static void tick() {
        expire();
    }

    /** Cancels a selection or unconsumed return draft when the player explicitly abandons map selection. */
    public static void cancel() {
        active = null;
        pending = null;
    }

    /** Clears all process-local state when the client disconnects. */
    public static void clear() {
        cancel();
    }

    private static void finish(ActiveSelection selection, OrbitalFireControlDraft draft) {
        active = null;
        PendingSelection completed = new PendingSelection(
                selection.weaponId(),
                draft,
                selection.source(),
                Util.getNanos());
        pending = completed;
        requestControlMenu(completed.source());
    }

    private static void requestControlMenu(OrbitalControlUiSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            clear();
            return;
        }
        minecraft.setScreen(null);
        switch (source) {
            case Terminal() -> PacketDistributor.sendToServer(OrbitalControlOpenPayload.INSTANCE);
            case Console(ResourceLocation dimensionId, var blockPos) -> PacketDistributor.sendToServer(
                    new OrbitalControlConsoleOpenPayload(dimensionId, blockPos));
        }
    }

    private static void expire() {
        long now = Util.getNanos();
        if (active != null && now - active.createdAtNanos() >= TIMEOUT_NANOS) {
            active = null;
        }
        if (pending != null && now - pending.createdAtNanos() >= TIMEOUT_NANOS) {
            pending = null;
        }
    }

    private record ActiveSelection(
                                   UUID token,
                                   ResourceLocation providerId,
                                   @Nullable UUID weaponId,
                                   OrbitalFireControlDraft draft,
                                   OrbitalControlUiSource source,
                                   long createdAtNanos) {}

    private record PendingSelection(
                                    @Nullable UUID weaponId,
                                    OrbitalFireControlDraft draft,
                                    OrbitalControlUiSource source,
                                    long createdAtNanos) {}
}
