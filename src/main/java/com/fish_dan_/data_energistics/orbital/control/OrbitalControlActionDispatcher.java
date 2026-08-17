package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side entry point for the small set of actions exposed by the LDLib2 orbital control surface.
 *
 * <p>
 * The client supplies no weapon identity, cost, dimension, or target coordinates. The target is sampled from the
 * server player's current view and every mutation is delegated to the existing SavedData transaction, which performs
 * the final access, endpoint, reserve, and lifecycle checks.
 * </p>
 */
public final class OrbitalControlActionDispatcher {

    private static final double TARGET_DISTANCE = 256.0D;
    private static final float TARGET_TICK_DELTA = 1.0F;
    private static final int DIRECTED_RADIUS = OrbitalDirectedEnergyStrike.MIN_RADIUS;

    private OrbitalControlActionDispatcher() {}

    /**
     * Confirms one warning against the first fire-capable weapon visible to the player and the block under the
     * server-side crosshair.
     *
     * @return the newly reserved attack, or an empty result when the target or authoritative checks reject the action
     */
    public static Optional<OrbitalAttackRecord> fireAtLookTarget(ServerPlayer player, OrbitalAttackMode mode) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        BlockPos target = blockTarget(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.data_energistics.orbital_control_terminal.no_block_target"),
                    true);
            return Optional.empty();
        }

        Optional<OrbitalWeaponRecord> weapon = OrbitalWeaponSavedData.get(server)
                .accessibleTo(player.getUUID())
                .stream()
                .filter(candidate -> candidate.canPerform(player.getUUID(), OrbitalWeaponAction.FIRE))
                .findFirst();
        if (weapon.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation dimensionId = player.level().dimension().location();
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        Optional<OrbitalAttackRecord> result;
        try {
            UUID weaponId = weapon.orElseThrow().weaponId();
            result = switch (mode) {
                case KINETIC -> attacks.tryConfirmKinetic(
                        server,
                        player.getUUID(),
                        weaponId,
                        dimensionId,
                        target);
                case DIRECTED_ENERGY -> attacks.tryConfirmDirectedEnergy(
                        server,
                        player.getUUID(),
                        weaponId,
                        dimensionId,
                        target,
                        DIRECTED_RADIUS,
                        OrbitalDirectedEnergyDepth.DEPTH_32);
                case DIGITAL_ANNIHILATION -> attacks.tryConfirmDigitalAnnihilation(
                        server,
                        player.getUUID(),
                        weaponId,
                        dimensionId,
                        target);
            };
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital control action {} failed for player {} at {}",
                    mode,
                    player.getUUID(),
                    target,
                    exception);
            return Optional.empty();
        }
        result.ifPresent(attack -> player.displayClientMessage(
                Component.translatable(
                        "message.data_energistics.orbital_control_terminal.warning_reserved",
                        mode.name()),
                true));
        return result;
    }

    /**
     * Cancels the first warning attack the player is currently authorized to cancel.
     *
     * @return true only after the warning escrow was refunded by the authoritative attack store
     */
    public static boolean cancelFirstWarning(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        try {
            return OrbitalWeaponSavedData.get(server)
                    .accessibleTo(player.getUUID())
                    .stream()
                    .flatMap(weapon -> attacks.forWeapon(weapon.weaponId()).stream())
                    .filter(attack -> attack.phase() == OrbitalAttackPhase.RESERVED_WARNING)
                    .sorted(Comparator.comparing(OrbitalAttackRecord::attackId))
                    .map(attack -> attacks.cancelWarning(server, player.getUUID(), attack.attackId()))
                    .filter(Boolean::booleanValue)
                    .findFirst()
                    .orElse(false);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital warning cancellation failed for player {}",
                    player.getUUID(),
                    exception);
            return false;
        }
    }

    @Nullable
    private static BlockPos blockTarget(ServerPlayer player) {
        HitResult hit = player.pick(TARGET_DISTANCE, TARGET_TICK_DELTA, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        return blockHit.getBlockPos().immutable();
    }
}
