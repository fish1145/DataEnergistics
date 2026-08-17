package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalOwnershipTransfer;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-thread entry points for ownership transfer and controlled retirement.
 *
 * <p>The dispatcher owns the short-lived retirement confirmation capability. A client never chooses the acting
 * player, bypasses the one-shot transfer offer, or calls the destructive retirement mutation without first receiving
 * a server-generated confirmation token.</p>
 */
public final class OrbitalOwnershipActionDispatcher {

    private static final long RETIRE_CONFIRMATION_TICKS = 20L * 60L;
    private static final Map<UUID, RetirementConfirmation> RETIREMENT_CONFIRMATIONS = new HashMap<>();
    private static @Nullable MinecraftServer trackedServer;

    private OrbitalOwnershipActionDispatcher() {}

    /** Creates a sixty-second, one-shot transfer offer for an online recipient. */
    public static Optional<OrbitalOwnershipTransfer> requestTransfer(
                                                                     ServerPlayer actor,
                                                                     UUID weaponId,
                                                                     UUID recipientId) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        trackServer(server);
        return OrbitalWeaponSavedData.get(server).requestOwnershipTransfer(
                server,
                actor.getUUID(),
                weaponId,
                recipientId);
    }

    /** Accepts and consumes the recipient-bound transfer capability. */
    public static boolean acceptTransfer(ServerPlayer recipient, UUID transferId) {
        MinecraftServer server = recipient.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        trackServer(server);
        return OrbitalWeaponSavedData.get(server).acceptOwnershipTransfer(
                server,
                recipient.getUUID(),
                transferId);
    }

    /**
     * Creates a server-owned retirement confirmation token after checking the current owner and weapon state.
     * Reissuing a token invalidates earlier tokens for the same player and weapon.
     */
    public static Optional<UUID> beginRetirement(ServerPlayer actor, UUID weaponId) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        trackServer(server);
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> weapon = data.find(weaponId);
        if (weapon.isEmpty()
                || !weapon.orElseThrow().canPerform(actor.getUUID(), OrbitalWeaponAction.RETIRE)) {
            return Optional.empty();
        }
        long now = server.overworld().getGameTime();
        RETIREMENT_CONFIRMATIONS.values().removeIf(confirmation ->
                confirmation.playerId().equals(actor.getUUID())
                        && confirmation.weaponId().equals(weaponId));
        UUID token = UUID.randomUUID();
        RETIREMENT_CONFIRMATIONS.put(
                token,
                new RetirementConfirmation(
                        actor.getUUID(),
                        weaponId,
                        now >= Long.MAX_VALUE - RETIRE_CONFIRMATION_TICKS
                                ? Long.MAX_VALUE
                                : now + RETIRE_CONFIRMATION_TICKS));
        return Optional.of(token);
    }

    /** Consumes a retirement token and performs the final authoritative state check and removal. */
    public static boolean confirmRetirement(
                                             ServerPlayer actor,
                                             UUID weaponId,
                                             UUID token) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        trackServer(server);
        RetirementConfirmation confirmation = RETIREMENT_CONFIRMATIONS.remove(token);
        if (confirmation == null
                || !confirmation.playerId().equals(actor.getUUID())
                || !confirmation.weaponId().equals(weaponId)
                || confirmation.expired(server.overworld().getGameTime())) {
            return false;
        }
        return OrbitalWeaponSavedData.get(server).retire(server, actor.getUUID(), weaponId);
    }

    /** Removes expired confirmations and isolates capabilities when a new server instance is observed. */
    public static void expire(MinecraftServer server) {
        if (!server.isSameThread()) {
            return;
        }
        trackServer(server);
        long now = server.overworld().getGameTime();
        RETIREMENT_CONFIRMATIONS.values().removeIf(confirmation -> confirmation.expired(now));
    }

    /** Clears transient retirement capabilities when a server shuts down. */
    public static void clear(MinecraftServer server) {
        if (trackedServer == server) {
            RETIREMENT_CONFIRMATIONS.clear();
            trackedServer = null;
        }
    }

    private static void trackServer(MinecraftServer server) {
        if (trackedServer == server) {
            return;
        }
        trackedServer = server;
        RETIREMENT_CONFIRMATIONS.clear();
    }

    private record RetirementConfirmation(UUID playerId, UUID weaponId, long expiresAt) {

        private boolean expired(long gameTime) {
            return gameTime >= this.expiresAt;
        }
    }
}
