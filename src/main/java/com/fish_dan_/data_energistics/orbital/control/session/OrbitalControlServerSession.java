package com.fish_dan_.data_energistics.orbital.control.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlFeedback;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlIntent;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlMenuSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlDraft;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Menu-lifetime authority for one server player's orbital control surface.
 *
 * <p>
 * Every RPC is accepted only while this exact {@link ModularUI} remains the player's current menu and the source
 * gate still validates the held terminal, Curios terminal or bound console. Expected rejections become typed feedback;
 * unexpected failures are logged once at this top-level boundary.
 * </p>
 */
public final class OrbitalControlServerSession {

    private final ServerPlayer player;
    private final Supplier<OrbitalControlTerminalSnapshot> terminalSnapshot;
    private final BooleanSupplier sourceValid;
    private OrbitalControlFeedback feedback = OrbitalControlFeedback.NONE;
    private @Nullable ModularUI modularUI;

    public OrbitalControlServerSession(
                                       ServerPlayer player,
                                       Supplier<OrbitalControlTerminalSnapshot> terminalSnapshot,
                                       BooleanSupplier sourceValid) {
        this.player = player;
        this.terminalSnapshot = terminalSnapshot;
        this.sourceValid = sourceValid;
        OrbitalControlActionDispatcher.discardPreview(player);
    }

    /** Attaches the completed UI exactly once so later RPCs cannot be replayed through another menu. */
    public void attach(ModularUI modularUI) {
        if (this.modularUI != null) {
            throw new IllegalStateException("An orbital control server session is already attached to a menu");
        }
        this.modularUI = modularUI;
    }

    /** Captures one atomic terminal, fire-control and feedback view for LDLib2 synchronization. */
    public OrbitalControlMenuSnapshot snapshot() {
        MinecraftServer server = this.player.getServer();
        if (server == null || !server.isSameThread() || !this.sourceValid.getAsBoolean()) {
            invalidateSource();
            return new OrbitalControlMenuSnapshot(
                    OrbitalControlTerminalSnapshot.EMPTY,
                    OrbitalFireControlSessionSnapshot.REJECTED,
                    this.feedback);
        }
        OrbitalFireControlSessionSnapshot fireControl = OrbitalControlActionDispatcher.currentFireControlSnapshot(
                this.player);
        if (fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.REJECTED &&
                (this.feedback == OrbitalControlFeedback.PREVIEW_REQUESTED ||
                        this.feedback == OrbitalControlFeedback.HOLD_STARTED)) {
            this.feedback = OrbitalControlFeedback.PREVIEW_STALE;
        }
        if (fireControl.phase() == OrbitalFireControlSessionSnapshot.Phase.IDLE && isRejected(this.feedback)) {
            fireControl = OrbitalFireControlSessionSnapshot.REJECTED;
        }
        return new OrbitalControlMenuSnapshot(this.terminalSnapshot.get(), fireControl, this.feedback);
    }

    /** Executes one decoded client intent at the sole trusted menu boundary. */
    public void handle(OrbitalControlIntent intent) {
        if (!canHandle()) {
            invalidateSource();
            return;
        }
        try {
            this.feedback = switch (intent) {
                case OrbitalControlIntent.CycleWeapon cycle -> cycleWeapon(cycle.forward());
                case OrbitalControlIntent.CancelOrAbortMode cancel -> OrbitalControlActionDispatcher.cancelOrAbortSelectedMode(
                        this.player,
                        cancel.mode()) ? OrbitalControlFeedback.TASK_STOPPED : OrbitalControlFeedback.ACTION_REJECTED;
                case OrbitalControlIntent.RequestPreview preview -> requestPreview(preview.draft());
                case OrbitalControlIntent.StartHold start -> startHold(start);
                case OrbitalControlIntent.ReleaseHold release -> releaseHold(release);
                case OrbitalControlIntent.CancelHold ignored -> {
                    OrbitalControlActionDispatcher.cancelFireHold(this.player);
                    yield OrbitalControlFeedback.HOLD_CANCELLED;
                }
                case OrbitalControlIntent.DiscardPreview ignored -> {
                    OrbitalControlActionDispatcher.discardPreview(this.player);
                    yield OrbitalControlFeedback.NONE;
                }
            };
        } catch (IllegalArgumentException ignored) {
            this.feedback = OrbitalControlFeedback.ACTION_REJECTED;
        } catch (RuntimeException exception) {
            this.feedback = OrbitalControlFeedback.INTERNAL_FAILURE;
            Data_Energistics.LOGGER.error(
                    "Failed to handle orbital control intent {} for player {}",
                    intent.getClass().getSimpleName(),
                    this.player.getUUID(),
                    exception);
        }
    }

    private OrbitalControlFeedback cycleWeapon(boolean forward) {
        return OrbitalControlActionDispatcher.cycleWeapon(this.player, forward).isPresent() ?
                OrbitalControlFeedback.WEAPON_SELECTED : OrbitalControlFeedback.ACTION_REJECTED;
    }

    private OrbitalControlFeedback requestPreview(OrbitalFireControlDraft draft) {
        boolean accepted = OrbitalControlActionDispatcher.previewFireAtTarget(
                this.player,
                draft.mode(),
                draft.dimensionId(),
                draft.targetX(),
                draft.targetZ(),
                draft.targetYMode(),
                draft.targetYValue(),
                draft.directedRadius(),
                draft.directedDepth(),
                this::canHandle);
        return accepted ? OrbitalControlFeedback.PREVIEW_REQUESTED : OrbitalControlFeedback.ACTION_REJECTED;
    }

    private OrbitalControlFeedback startHold(OrbitalControlIntent.StartHold start) {
        OrbitalFireControlSessionSnapshot current = OrbitalControlActionDispatcher.currentFireControlSnapshot(this.player);
        if (current.preview() == null || !Objects.equals(current.preview().nonce(), start.nonce())) {
            return OrbitalControlFeedback.PREVIEW_STALE;
        }
        return OrbitalControlActionDispatcher.startFireHold(
                this.player,
                current.preview().mode(),
                start.nonce(),
                this::canHandle) ? OrbitalControlFeedback.HOLD_STARTED : OrbitalControlFeedback.PREVIEW_STALE;
    }

    private OrbitalControlFeedback releaseHold(OrbitalControlIntent.ReleaseHold release) {
        OrbitalFireControlSessionSnapshot current = OrbitalControlActionDispatcher.currentFireControlSnapshot(this.player);
        if (current.preview() == null || !Objects.equals(current.preview().nonce(), release.nonce())) {
            return OrbitalControlFeedback.PREVIEW_STALE;
        }
        return OrbitalControlActionDispatcher.releaseFireAtTarget(
                this.player,
                current.preview().mode(),
                release.nonce(),
                this::canHandle) ? OrbitalControlFeedback.ATTACK_CONFIRMED : OrbitalControlFeedback.ACTION_REJECTED;
    }

    private boolean canHandle() {
        MinecraftServer server = this.player.getServer();
        if (server == null || !server.isSameThread() || !this.sourceValid.getAsBoolean() || this.modularUI == null) {
            return false;
        }
        return this.player.containerMenu instanceof IModularUIHolder holder && holder.getModularUI() == this.modularUI;
    }

    private void invalidateSource() {
        OrbitalControlActionDispatcher.cancelFireHold(this.player);
        OrbitalControlActionDispatcher.discardPreview(this.player);
        this.feedback = OrbitalControlFeedback.SOURCE_INVALID;
    }

    private static boolean isRejected(OrbitalControlFeedback feedback) {
        return switch (feedback) {
            case SOURCE_INVALID, PREVIEW_STALE, ACTION_REJECTED, INTERNAL_FAILURE -> true;
            default -> false;
        };
    }
}
