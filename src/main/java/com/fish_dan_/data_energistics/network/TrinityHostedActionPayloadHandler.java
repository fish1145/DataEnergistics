package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmissionResolver;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmissionResolverImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Main-thread router for the generation-aware Trinity auto-build action. */
public final class TrinityHostedActionPayloadHandler {

    private static final TrinityAutoBuildSubmissionResolver AUTO_BUILD_RESOLVER = new TrinityAutoBuildSubmissionResolverImpl();

    private TrinityHostedActionPayloadHandler() {}

    /** Validates, claims, reconstructs, and invokes the existing atomic auto-build entry once. */
    static void handleAutoBuild(TrinityHostedAutoBuildPayload payload, Player player) {
        handleAutoBuild(payload, player, responseSink(player));
    }

    /** Test seam retaining current-catalog reconstruction and the complete production routing order. */
    public static void handleAutoBuild(TrinityHostedAutoBuildPayload payload,
                                       Player player,
                                       Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(payload.containerId(), payload.ticket(), player, responseSink);
        if (routed == null) {
            return;
        }
        TrinityAutoBuildRequest request;
        try {
            MultiblockPreviewSpec spec = ModVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                    .require(ModVerticalMultiBlocks.trinityDataCoreId());
            request = AUTO_BUILD_RESOLVER.resolve(spec, payload.submission());
        } catch (RuntimeException | Error failure) {
            logFailure("auto-build submission was rejected", routed.player(), routed.menu(), payload.ticket(), failure);
            respond(
                    routed.player(),
                    payload.containerId(),
                    payload.ticket(),
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return;
        }
        try {
            routed.menu().executeHostedAutoBuild(routed.player(), request);
            respond(
                    routed.player(),
                    payload.containerId(),
                    payload.ticket(),
                    TrinityHostedActionStatus.COMPLETED,
                    responseSink);
        } catch (RuntimeException | Error failure) {
            logFailure("auto-build business entry failed", routed.player(), routed.menu(), payload.ticket(), failure);
            respond(
                    routed.player(),
                    payload.containerId(),
                    payload.ticket(),
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
        }
    }

    @Nullable
    private static RoutedAction route(int containerId,
                                      TrinityHostedActionTicket ticket,
                                      Player player,
                                      Consumer<TrinityHostedActionResponsePayload> responseSink) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Trinity hosted action: reason={}, player={}, menu={}, host={}, key={}, generation={}, sequence={}",
                    "not a server player",
                    player == null ? "<null>" : player.getName().getString(),
                    player == null ? "<none>" : player.containerMenu,
                    "<none>",
                    ticket.key().id(),
                    ticket.generation(),
                    ticket.sequence());
            return null;
        }

        AbstractContainerMenu currentMenu = serverPlayer.containerMenu;
        if (currentMenu.containerId != containerId || !(currentMenu instanceof TrinityDataCoreMenu menu)) {
            logRejected("wrong container or menu", serverPlayer, currentMenu, null, ticket);
            respond(
                    serverPlayer,
                    containerId,
                    ticket,
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return null;
        }

        boolean hostAvailable;
        try {
            hostAvailable = menu.isHostUiAvailable(serverPlayer);
        } catch (RuntimeException | Error failure) {
            logFailure("host availability check failed", serverPlayer, menu, ticket, failure);
            hostAvailable = false;
        }
        if (!hostAvailable) {
            logRejected("host is unavailable or replaced", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    ticket,
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return null;
        }
        boolean generationOpen;
        try {
            generationOpen = menu.getHostUiExtension().isOpen(ticket.key(), ticket.generation());
        } catch (RuntimeException | Error failure) {
            logFailure("hosted generation check failed", serverPlayer, menu, ticket, failure);
            generationOpen = false;
        }
        if (!generationOpen) {
            logRejected("hosted window generation is not open", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    ticket,
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return null;
        }
        boolean claimed;
        try {
            claimed = menu.claimHostedActionSequence(ticket);
        } catch (RuntimeException | Error failure) {
            logFailure("action sequence claim failed", serverPlayer, menu, ticket, failure);
            claimed = false;
        }
        if (!claimed) {
            logRejected("duplicate or out-of-order action sequence", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    ticket,
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return null;
        }
        return new RoutedAction(serverPlayer, menu);
    }

    private static Consumer<TrinityHostedActionResponsePayload> responseSink(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return response -> PacketDistributor.sendToPlayer(serverPlayer, response);
        }
        return response -> {};
    }

    private static void respond(ServerPlayer player,
                                int containerId,
                                TrinityHostedActionTicket ticket,
                                TrinityHostedActionStatus status,
                                Consumer<TrinityHostedActionResponsePayload> responseSink) {
        TrinityHostedActionResult result = new TrinityHostedActionResult(
                ticket.key(),
                ticket.generation(),
                ticket.sequence(),
                status);
        try {
            responseSink.accept(new TrinityHostedActionResponsePayload(containerId, result));
        } catch (RuntimeException | Error failure) {
            logFailure("response transport failed", player, player.containerMenu, ticket, failure);
            if (player.containerMenu.containerId == containerId) {
                player.closeContainer();
            }
        }
    }

    private static void logRejected(String reason,
                                    ServerPlayer player,
                                    Object menu,
                                    @Nullable Object host,
                                    TrinityHostedActionTicket ticket) {
        Data_Energistics.LOGGER.warn(
                "Rejected Trinity hosted action: reason={}, player={}, menu={}, host={}, key={}, generation={}, sequence={}",
                reason,
                player.getGameProfile().getName(),
                menu,
                host,
                ticket.key().id(),
                ticket.generation(),
                ticket.sequence());
    }

    private static void logFailure(String reason,
                                   ServerPlayer player,
                                   Object menu,
                                   TrinityHostedActionTicket ticket,
                                   Throwable failure) {
        Object host = menu instanceof TrinityDataCoreMenu trinityMenu ? trinityMenu.getHost() : null;
        Data_Energistics.LOGGER.error(
                "Rejected Trinity hosted action: reason={}, player={}, menu={}, host={}, key={}, generation={}, sequence={}",
                reason,
                player.getGameProfile().getName(),
                menu,
                host,
                ticket.key().id(),
                ticket.generation(),
                ticket.sequence(),
                failure);
    }

    private record RoutedAction(ServerPlayer player, TrinityDataCoreMenu menu) {}
}
