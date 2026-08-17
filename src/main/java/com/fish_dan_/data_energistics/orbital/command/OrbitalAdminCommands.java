package com.fish_dan_.data_energistics.orbital.command;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/** Server-only recovery commands for operators; players never receive the administrator refund capability. */
public final class OrbitalAdminCommands {

    public OrbitalAdminCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("data_energistics")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("orbital")
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> inspectAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("retry")
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> retryAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("abort")
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> abortAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("refund")
                                        .then(Commands.argument("attack", UuidArgument.uuid())
                                                .executes(context -> refundAttack(
                                                        context.getSource(),
                                                        UuidArgument.getUuid(context, "attack")))))
                                .then(Commands.literal("repair-owner-index")
                                        .executes(context -> repairOwnerIndex(context.getSource())))
                                .then(Commands.literal("reconcile-endpoints")
                                        .executes(context -> reconcileEndpoints(context.getSource())))));
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
