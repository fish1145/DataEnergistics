package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmissionResolver;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Main-thread router for the generation-aware Trinity auto-build action.
 */
public final class TrinityHostedActionPayloadHandler {

    private static final TrinityAutoBuildSubmissionResolver AUTO_BUILD_RESOLVER = new TrinityAutoBuildSubmissionResolver();

    private TrinityHostedActionPayloadHandler() {}

    /**
     * Validates, claims, reconstructs, and invokes the existing atomic auto-build entry once.
     */
    static void handleAutoBuild(TrinityHostedAutoBuildPayload payload, Player player) {
        handleAutoBuild(payload, player, responseSink(player));
    }

    /**
     * Test seam retaining current-catalog reconstruction and the complete production routing order.
     */
    public static void handleAutoBuild(TrinityHostedAutoBuildPayload payload,
                                       Player player,
                                       Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                true);
        if (routed == null) {
            return;
        }
        TrinityAutoBuildRequest request;
        try {
            MultiblockPreviewSpec spec = DEVerticalMultiBlocks.MULTIBLOCK_PREVIEWS.snapshot()
                    .require(DEVerticalMultiBlocks.trinityDataCoreId());
            if (payload.submission().projectionFingerprint().definitionRevision() != spec.definitionRevision()) {
                Data_Energistics.LOGGER.warn(
                        "Rejected Trinity auto-build after its menu definition changed: player={}, host={}, " +
                                "submittedRevision={}, currentRevision={}, generation={}, sequence={}",
                        routed.player().getGameProfile().getName(),
                        routed.menu().getHost(),
                        payload.submission().projectionFingerprint().definitionRevision(),
                        spec.definitionRevision(),
                        payload.ticket().generation(),
                        payload.ticket().sequence());
                respond(
                        routed.player(),
                        payload.containerId(),
                        payload.hostId(),
                        payload.menuSessionId(),
                        payload.ticket(),
                        TrinityHostedActionStatus.STALE_STATE,
                        responseSink);
                return;
            }
            request = AUTO_BUILD_RESOLVER.resolve(spec, payload.submission());
        } catch (RuntimeException failure) {
            logFailure("auto-build submission was rejected", routed.player(), routed.menu(), payload.ticket(), failure);
            respond(
                    routed.player(),
                    payload.containerId(),
                    payload.hostId(),
                    payload.menuSessionId(),
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
                    payload.hostId(),
                    payload.menuSessionId(),
                    payload.ticket(),
                    TrinityHostedActionStatus.COMPLETED,
                    responseSink);
        } catch (RuntimeException failure) {
            logFailure("auto-build business entry failed", routed.player(), routed.menu(), payload.ticket(), failure);
            respond(
                    routed.player(),
                    payload.containerId(),
                    payload.hostId(),
                    payload.menuSessionId(),
                    payload.ticket(),
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
        }
    }

    /** Routes a constrained priority operation through the shared generation and replay guards. */
    static void handlePriority(TrinityHostedPriorityPayload payload, Player player) {
        handlePriority(payload, player, responseSink(player));
    }

    /** Test seam retaining the complete production routing and terminal response path. */
    public static void handlePriority(TrinityHostedPriorityPayload payload,
                                      Player player,
                                      Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                true);
        if (routed == null) {
            return;
        }
        TrinityHostedActionStatus status;
        try {
            status = routed.menu().executeHostedPriority(payload.key(), payload.operation());
        } catch (IllegalArgumentException failure) {
            logFailure("priority operation was rejected", routed.player(), routed.menu(), payload.ticket(), failure);
            status = TrinityHostedActionStatus.REJECTED;
        } catch (RuntimeException failure) {
            logFailure("priority business entry failed", routed.player(), routed.menu(), payload.ticket(), failure);
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
        }
        respond(
                routed.player(),
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                status,
                responseSink);
    }

    /** Routes one revision-bound aggregate pattern click through the shared generation and replay guards. */
    static void handlePatternSlot(TrinityHostedPatternSlotPayload payload, Player player) {
        handlePatternSlot(payload, player, responseSink(player));
    }

    /** Test seam retaining the complete hosted routing and terminal response path. */
    public static void handlePatternSlot(TrinityHostedPatternSlotPayload payload,
                                         Player player,
                                         Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                true);
        if (routed == null) {
            return;
        }
        TrinityHostedActionStatus status;
        try {
            status = routed.menu().executeHostedPatternSlot(
                    routed.player(),
                    payload.layoutRevision(),
                    payload.catalogRevision(),
                    payload.globalSlot(),
                    payload.action());
        } catch (IllegalArgumentException failure) {
            logFailure("aggregate pattern slot action was rejected", routed.player(), routed.menu(), payload.ticket(), failure);
            status = TrinityHostedActionStatus.REJECTED;
        } catch (RuntimeException failure) {
            logFailure("aggregate pattern slot business entry failed", routed.player(), routed.menu(), payload.ticket(), failure);
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
        }
        respond(
                routed.player(),
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                status,
                responseSink);
    }

    /** Routes one ordered aggregate quick-move batch through the shared generation and replay guards. */
    static void handlePatternQuickMove(TrinityHostedPatternQuickMovePayload payload, Player player) {
        handlePatternQuickMove(payload, player, responseSink(player));
    }

    /** Test seam retaining the complete hosted routing and terminal response path. */
    public static void handlePatternQuickMove(TrinityHostedPatternQuickMovePayload payload,
                                              Player player,
                                              Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                true);
        if (routed == null) {
            return;
        }
        TrinityHostedActionStatus status;
        try {
            status = routed.menu().executeHostedPatternQuickMove(
                    routed.player(),
                    payload.layoutRevision(),
                    payload.globalSlots());
        } catch (IllegalArgumentException failure) {
            logFailure("aggregate pattern quick-move batch was rejected",
                    routed.player(),
                    routed.menu(),
                    payload.ticket(),
                    failure);
            status = TrinityHostedActionStatus.REJECTED;
        } catch (RuntimeException failure) {
            logFailure("aggregate pattern quick-move business entry failed",
                    routed.player(),
                    routed.menu(),
                    payload.ticket(),
                    failure);
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
        }
        respond(
                routed.player(),
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                status,
                responseSink);
    }

    /** Routes one best-effort pattern migration through the aggregate window generation and replay guards. */
    static void handlePatternMigration(TrinityHostedPatternMigrationPayload payload, Player player) {
        handlePatternMigration(payload, player, responseSink(player));
    }

    /** Test seam retaining the complete hosted routing and terminal response path. */
    public static void handlePatternMigration(TrinityHostedPatternMigrationPayload payload,
                                              Player player,
                                              Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                true);
        if (routed == null) {
            return;
        }
        TrinityHostedActionStatus status;
        try {
            status = routed.menu().executeHostedPatternMigration(routed.player());
        } catch (RuntimeException failure) {
            logFailure("aggregate pattern migration failed", routed.player(), routed.menu(), payload.ticket(), failure);
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
        }
        respond(
                routed.player(),
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                status,
                responseSink);
    }

    /**
     * Routes the installed-pattern action without requiring a hosted child window.
     */
    static void handleRefundPatterns(TrinityRefundPatternsPayload payload, Player player) {
        handleRefundPatterns(payload, player, responseSink(player));
    }

    /**
     * Test seam for the complete installed-pattern routing and result path.
     */
    public static void handleRefundPatterns(TrinityRefundPatternsPayload payload,
                                            Player player,
                                            Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                false);
        if (routed == null) {
            return;
        }
        executeStaticAction(
                "installed-pattern refund failed",
                routed,
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                responseSink,
                () -> routed.menu().executeRefundPatterns(routed.player()));
    }

    /**
     * Routes the retained-work action without requiring a hosted child window.
     */
    static void handleRefundRetainedItems(TrinityRefundRetainedItemsPayload payload, Player player) {
        handleRefundRetainedItems(payload, player, responseSink(player));
    }

    /**
     * Test seam for the complete queued-input and pending-output routing and result path.
     */
    public static void handleRefundRetainedItems(TrinityRefundRetainedItemsPayload payload,
                                                 Player player,
                                                 Consumer<TrinityHostedActionResponsePayload> responseSink) {
        RoutedAction routed = route(
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                player,
                responseSink,
                false);
        if (routed == null) {
            return;
        }
        executeStaticAction(
                "retained-item refund failed",
                routed,
                payload.containerId(),
                payload.hostId(),
                payload.menuSessionId(),
                payload.ticket(),
                responseSink,
                () -> routed.menu().executeRefundRetainedItems(routed.player()));
    }

    /**
     * Sends a terminal business status while translating unexpected execution errors into a distinct status.
     */
    private static void executeStaticAction(String failureReason,
                                            RoutedAction routed,
                                            int containerId,
                                            UUID hostId,
                                            UUID menuSessionId,
                                            TrinityHostedActionTicket ticket,
                                            Consumer<TrinityHostedActionResponsePayload> responseSink,
                                            Supplier<TrinityHostedActionStatus> action) {
        TrinityHostedActionStatus status;
        try {
            status = action.get();
        } catch (RuntimeException failure) {
            logFailure(failureReason, routed.player(), routed.menu(), ticket, failure);
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
        }
        respond(
                routed.player(),
                containerId,
                hostId,
                menuSessionId,
                ticket,
                status,
                responseSink);
    }

    @Nullable
    private static RoutedAction route(int containerId,
                                      UUID hostId,
                                      UUID menuSessionId,
                                      TrinityHostedActionTicket ticket,
                                      Player player,
                                      Consumer<TrinityHostedActionResponsePayload> responseSink,
                                      boolean requiresHostedWindow) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.warn(
                    "Rejected Trinity hosted action: reason={}, player={}, menu={}, host={}, session={}, key={}, generation={}, sequence={}",
                    "not a server player",
                    player == null ? "<null>" : player.getName().getString(),
                    player == null ? "<none>" : player.containerMenu,
                    hostId,
                    menuSessionId,
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
                    hostId,
                    menuSessionId,
                    ticket,
                    TrinityHostedActionStatus.STALE_STATE,
                    responseSink);
            return null;
        }

        if (!menu.matchesHostedActionEnvelope(hostId, menuSessionId)) {
            logRejected("host identity or menu session is stale", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    hostId,
                    menuSessionId,
                    ticket,
                    TrinityHostedActionStatus.STALE_STATE,
                    responseSink);
            return null;
        }

        boolean hostAvailable;
        try {
            hostAvailable = menu.isHostUiAvailable(serverPlayer);
        } catch (RuntimeException failure) {
            logFailure("host availability check failed", serverPlayer, menu, ticket, failure);
            hostAvailable = false;
        }
        if (!hostAvailable) {
            logRejected("host is unavailable or replaced", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    hostId,
                    menuSessionId,
                    ticket,
                    TrinityHostedActionStatus.STALE_STATE,
                    responseSink);
            return null;
        }
        if (!requiresHostedWindow) {
            return claimRoutedAction(serverPlayer, menu, containerId, hostId, menuSessionId, ticket, responseSink);
        }
        boolean generationOpen;
        try {
            generationOpen = menu.getHostUiExtension().isOpen(ticket.key(), ticket.generation());
        } catch (RuntimeException failure) {
            logFailure("hosted generation check failed", serverPlayer, menu, ticket, failure);
            generationOpen = false;
        }
        if (!generationOpen) {
            logRejected("hosted window generation is not open", serverPlayer, menu, menu.getHost(), ticket);
            respond(
                    serverPlayer,
                    containerId,
                    hostId,
                    menuSessionId,
                    ticket,
                    TrinityHostedActionStatus.STALE_STATE,
                    responseSink);
            return null;
        }
        return claimRoutedAction(serverPlayer, menu, containerId, hostId, menuSessionId, ticket, responseSink);
    }

    @Nullable
    private static RoutedAction claimRoutedAction(ServerPlayer player,
                                                  TrinityDataCoreMenu menu,
                                                  int containerId,
                                                  UUID hostId,
                                                  UUID menuSessionId,
                                                  TrinityHostedActionTicket ticket,
                                                  Consumer<TrinityHostedActionResponsePayload> responseSink) {
        boolean claimed;
        try {
            claimed = menu.claimHostedActionSequence(ticket);
        } catch (RuntimeException failure) {
            logFailure("action sequence claim failed", player, menu, ticket, failure);
            claimed = false;
        }
        if (!claimed) {
            logRejected("duplicate or out-of-order action sequence", player, menu, menu.getHost(), ticket);
            respond(
                    player,
                    containerId,
                    hostId,
                    menuSessionId,
                    ticket,
                    TrinityHostedActionStatus.REJECTED,
                    responseSink);
            return null;
        }
        return new RoutedAction(player, menu);
    }

    private static Consumer<TrinityHostedActionResponsePayload> responseSink(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return response -> PacketDistributor.sendToPlayer(serverPlayer, response);
        }
        return response -> {};
    }

    private static void respond(ServerPlayer player,
                                int containerId,
                                UUID hostId,
                                UUID menuSessionId,
                                TrinityHostedActionTicket ticket,
                                TrinityHostedActionStatus status,
                                Consumer<TrinityHostedActionResponsePayload> responseSink) {
        TrinityHostedActionResult result = new TrinityHostedActionResult(
                ticket.key(),
                ticket.generation(),
                ticket.sequence(),
                status);
        try {
            responseSink.accept(new TrinityHostedActionResponsePayload(containerId, hostId, menuSessionId, result));
        } catch (RuntimeException failure) {
            logFailure("response transport failed", player, player.containerMenu, ticket, failure);
            if (player.containerMenu instanceof TrinityDataCoreMenu menu && menu.containerId == containerId &&
                    menu.matchesHostedActionEnvelope(hostId, menuSessionId)) {
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
