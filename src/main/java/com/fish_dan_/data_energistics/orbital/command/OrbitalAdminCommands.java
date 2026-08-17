package com.fish_dan_.data_energistics.orbital.command;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.control.OrbitalOwnershipActionDispatcher;
import com.fish_dan_.data_energistics.orbital.control.OrbitalWeaponAdministrationDispatcher;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalOwnershipTransfer;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Server-side orbital commands: player lifecycle intents plus operator-only attack recovery. */
public final class OrbitalAdminCommands {

    public OrbitalAdminCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("data_energistics")
                        .then(Commands.literal("orbital")
                                .then(Commands.literal("transfer")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("recipient", UuidArgument.uuid())
                                                        .executes(context -> requestTransfer(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "weapon"),
                                                                UuidArgument.getUuid(context, "recipient"))))))
                                .then(Commands.literal("accept-transfer")
                                        .then(Commands.argument("transfer", UuidArgument.uuid())
                                                .executes(context -> acceptTransfer(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "transfer")))))
                                .then(Commands.literal("retire")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .executes(context -> beginRetirement(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "weapon")))))
                                .then(Commands.literal("confirm-retire")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("confirmation", UuidArgument.uuid())
                                                        .executes(context -> confirmRetirement(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "weapon"),
                                                                UuidArgument.getUuid(context, "confirmation"))))))
                                .then(Commands.literal("endpoint-priority")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("dimension", StringArgumentType.word())
                                                        .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                                .then(Commands.argument("y", IntegerArgumentType.integer(-2_000_000, 2_000_000))
                                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                                                .then(Commands.argument("priority", IntegerArgumentType.integer(0, 31))
                                                                                        .executes(context -> setEndpointPriority(
                                                                                                context.getSource(),
                                                                                                UuidArgument.getUuid(context, "weapon"),
                                                                                                StringArgumentType.getString(context, "dimension"),
                                                                                                IntegerArgumentType.getInteger(context, "x"),
                                                                                                IntegerArgumentType.getInteger(context, "y"),
                                                                                                IntegerArgumentType.getInteger(context, "z"),
                                                                                                IntegerArgumentType.getInteger(context, "priority"))))))))))
                                .then(Commands.literal("primary-anchor")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("dimension", StringArgumentType.word())
                                                        .then(Commands.argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                                .then(Commands.argument("y", IntegerArgumentType.integer(-2_000_000, 2_000_000))
                                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
                                                                                .executes(context -> selectPrimaryAnchor(
                                                                                        context.getSource(),
                                                                                        UuidArgument.getUuid(context, "weapon"),
                                                                                        StringArgumentType.getString(context, "dimension"),
                                                                                        IntegerArgumentType.getInteger(context, "x"),
                                                                                        IntegerArgumentType.getInteger(context, "y"),
                                                                                        IntegerArgumentType.getInteger(context, "z")))))))))
                                .then(Commands.literal("authorize")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("player", UuidArgument.uuid())
                                                        .then(Commands.argument("role", StringArgumentType.word())
                                                                .executes(context -> authorize(
                                                                        context.getSource(),
                                                                        UuidArgument.getUuid(context, "weapon"),
                                                                        UuidArgument.getUuid(context, "player"),
                                                                        StringArgumentType.getString(context, "role")))))))
                                .then(Commands.literal("revoke")
                                        .then(Commands.argument("weapon", UuidArgument.uuid())
                                                .then(Commands.argument("player", UuidArgument.uuid())
                                                        .executes(context -> revoke(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "weapon"),
                                                                UuidArgument.getUuid(context, "player"))))))
                                .then(Commands.literal("inspect")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> inspectAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("retry")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> retryAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("abort")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> abortAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("refund")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> refundAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("repair-owner-index")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> repairOwnerIndex(context.getSource())))
                                .then(Commands.literal("reconcile-endpoints")
                                        .requires(source -> source.hasPermission(2))
                                        .executes(context -> reconcileEndpoints(context.getSource())))));
    }

    private static int setEndpointPriority(
                                           CommandSourceStack source,
                                           UUID weaponId,
                                           String dimensionId,
                                           int x,
                                           int y,
                                           int z,
                                           int priority) {
        ServerPlayer owner = playerSource(source);
        if (owner == null) {
            return 0;
        }
        try {
            OrbitalEndpointLocation location = endpointLocation(dimensionId, x, y, z);
            if (!OrbitalWeaponAdministrationDispatcher.setEndpointPriority(owner, weaponId, location, priority)) {
                source.sendFailure(Component.translatable(
                        "commands.data_energistics.orbital.endpoint_priority_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.endpoint_priority_updated",
                            location.dimensionId(),
                            location.pos().toShortString(),
                            priority),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital endpoint priority command failed for weapon {}",
                    weaponId,
                    exception);
            source.sendFailure(Component.translatable(
                    "commands.data_energistics.orbital.endpoint_priority_rejected"));
            return 0;
        }
    }

    private static int selectPrimaryAnchor(
                                           CommandSourceStack source,
                                           UUID weaponId,
                                           String dimensionId,
                                           int x,
                                           int y,
                                           int z) {
        ServerPlayer owner = playerSource(source);
        if (owner == null) {
            return 0;
        }
        try {
            OrbitalEndpointLocation location = endpointLocation(dimensionId, x, y, z);
            if (!OrbitalWeaponAdministrationDispatcher.selectPrimaryAnchor(owner, weaponId, location)) {
                source.sendFailure(Component.translatable(
                        "commands.data_energistics.orbital.primary_anchor_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.primary_anchor_updated",
                            location.dimensionId(),
                            location.pos().toShortString()),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital primary-anchor command failed for weapon {}",
                    weaponId,
                    exception);
            source.sendFailure(Component.translatable(
                    "commands.data_energistics.orbital.primary_anchor_rejected"));
            return 0;
        }
    }

    private static int authorize(
                                 CommandSourceStack source,
                                 UUID weaponId,
                                 UUID playerId,
                                 String roleName) {
        ServerPlayer owner = playerSource(source);
        if (owner == null) {
            return 0;
        }
        try {
            OrbitalAccessRole role = switch (roleName.toLowerCase(Locale.ROOT)) {
                case "operator" -> OrbitalAccessRole.OPERATOR;
                case "observer" -> OrbitalAccessRole.OBSERVER;
                default -> throw new IllegalArgumentException("Unknown orbital role: " + roleName);
            };
            if (!OrbitalWeaponAdministrationDispatcher.authorize(owner, weaponId, playerId, role)) {
                source.sendFailure(Component.translatable(
                        "commands.data_energistics.orbital.authorization_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.authorization_updated",
                            playerId,
                            role.name()),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital authorization command failed for weapon {} and player {}",
                    weaponId,
                    playerId,
                    exception);
            source.sendFailure(Component.translatable(
                    "commands.data_energistics.orbital.authorization_rejected"));
            return 0;
        }
    }

    private static int revoke(CommandSourceStack source, UUID weaponId, UUID playerId) {
        ServerPlayer owner = playerSource(source);
        if (owner == null) {
            return 0;
        }
        try {
            if (!OrbitalWeaponAdministrationDispatcher.revoke(owner, weaponId, playerId)) {
                source.sendFailure(Component.translatable(
                        "commands.data_energistics.orbital.authorization_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.authorization_revoked",
                            playerId),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital authorization-revoke command failed for weapon {} and player {}",
                    weaponId,
                    playerId,
                    exception);
            source.sendFailure(Component.translatable(
                    "commands.data_energistics.orbital.authorization_rejected"));
            return 0;
        }
    }

    private static OrbitalEndpointLocation endpointLocation(
                                                            String dimensionId,
                                                            int x,
                                                            int y,
                                                            int z) {
        return new OrbitalEndpointLocation(ResourceLocation.parse(dimensionId), new BlockPos(x, y, z));
    }

    private static @Nullable ServerPlayer playerSource(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.translatable("commands.data_energistics.orbital.player_required"));
        return null;
    }

    private static int requestTransfer(CommandSourceStack source, UUID weaponId, UUID recipientId) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.player_required"));
            return 0;
        }
        try {
            Optional<OrbitalOwnershipTransfer> offer = OrbitalOwnershipActionDispatcher.requestTransfer(
                    owner,
                    weaponId,
                    recipientId);
            if (offer.isEmpty()) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.transfer_rejected"));
                return 0;
            }
            OrbitalOwnershipTransfer transfer = offer.orElseThrow();
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.transfer_created",
                            transfer.transferId(),
                            transfer.recipientId()),
                    false);
            MinecraftServer server = owner.getServer();
            ServerPlayer recipient = server == null ? null : server.getPlayerList().getPlayer(transfer.recipientId());
            if (recipient != null) {
                recipient.displayClientMessage(
                        Component.translatable(
                                "commands.data_energistics.orbital.transfer_received",
                                transfer.transferId(),
                                transfer.currentOwnerId()),
                        false);
            }
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital ownership transfer request failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.transfer_rejected"));
            return 0;
        }
    }

    private static int acceptTransfer(CommandSourceStack source, UUID transferId) {
        if (!(source.getEntity() instanceof ServerPlayer recipient)) {
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.player_required"));
            return 0;
        }
        try {
            if (!OrbitalOwnershipActionDispatcher.acceptTransfer(recipient, transferId)) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.transfer_accept_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.transfer_accepted", transferId),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital ownership transfer acceptance failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.transfer_accept_rejected"));
            return 0;
        }
    }

    private static int beginRetirement(CommandSourceStack source, UUID weaponId) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.player_required"));
            return 0;
        }
        try {
            Optional<UUID> token = OrbitalOwnershipActionDispatcher.beginRetirement(owner, weaponId);
            if (token.isEmpty()) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.retirement_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.data_energistics.orbital.retirement_confirmation_required",
                            weaponId,
                            token.orElseThrow()),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital retirement confirmation request failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.retirement_rejected"));
            return 0;
        }
    }

    private static int confirmRetirement(CommandSourceStack source, UUID weaponId, UUID token) {
        if (!(source.getEntity() instanceof ServerPlayer owner)) {
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.player_required"));
            return 0;
        }
        try {
            if (!OrbitalOwnershipActionDispatcher.confirmRetirement(owner, weaponId, token)) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.retirement_rejected"));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.retirement_completed", weaponId),
                    false);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital retirement confirmation failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.retirement_rejected"));
            return 0;
        }
    }

    private static int inspectAttack(CommandSourceStack source, UUID attackId) {
        OrbitalAttackRecord attack = OrbitalAttackSavedData.get(source.getServer()).find(attackId).orElse(null);
        if (attack == null) {
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.attack_missing", attackId));
            return 0;
        }
        Component message = Component.translatable(
                "commands.data_energistics.orbital.attack_inspect",
                attack.attackId(),
                attack.weaponId(),
                attack.mode().name(),
                attack.phase().name(),
                attack.dimensionId(),
                attack.target().getX(),
                attack.target().getY(),
                attack.target().getZ(),
                attack.workCursor(),
                attack.workState().name(),
                attack.celestialEscrow(),
                attack.aeEscrow(),
                attack.faultReason() == null ? "-" : attack.faultReason());
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int retryAttack(CommandSourceStack source, UUID attackId) {
        MinecraftServer server = source.getServer();
        try {
            if (!OrbitalAttackSavedData.get(server).retryFaulted(server, attackId)) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.retry_rejected", attackId));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.retry_success", attackId),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            return reportFailure(source, "retry", attackId, exception);
        }
    }

    private static int abortAttack(CommandSourceStack source, UUID attackId) {
        MinecraftServer server = source.getServer();
        try {
            if (!OrbitalAttackSavedData.get(server).adminAbort(server, attackId)) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.abort_rejected", attackId));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.abort_success", attackId),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            return reportFailure(source, "abort", attackId, exception);
        }
    }

    private static int refundAttack(CommandSourceStack source, UUID attackId) {
        MinecraftServer server = source.getServer();
        try {
            if (!OrbitalAttackSavedData.get(server).refundFaulted(server, attackId)) {
                source.sendFailure(Component.translatable("commands.data_energistics.orbital.refund_rejected", attackId));
                return 0;
            }
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.refund_success", attackId),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            return reportFailure(source, "refund", attackId, exception);
        }
    }

    private static int repairOwnerIndex(CommandSourceStack source) {
        try {
            int removed = OrbitalWeaponSavedData.get(source.getServer()).repairIndexes(source.getServer());
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.repair_success", removed),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital owner-index repair command failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.command_failed"));
            return 0;
        }
    }

    private static int reconcileEndpoints(CommandSourceStack source) {
        try {
            OrbitalEndpointChunkTickets.reconcilePersistedBindings(source.getServer());
            source.sendSuccess(
                    () -> Component.translatable("commands.data_energistics.orbital.reconcile_success"),
                    true);
            return 1;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Orbital endpoint reconciliation command failed", exception);
            source.sendFailure(Component.translatable("commands.data_energistics.orbital.command_failed"));
            return 0;
        }
    }

    private static int reportFailure(
                                     CommandSourceStack source,
                                     String action,
                                     UUID attackId,
                                     RuntimeException exception) {
        Data_Energistics.LOGGER.error("Orbital admin {} failed for attack {}", action, attackId, exception);
        source.sendFailure(Component.translatable("commands.data_energistics.orbital.command_failed"));
        return 0;
    }
}
