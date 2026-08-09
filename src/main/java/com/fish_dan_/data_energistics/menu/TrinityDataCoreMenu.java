package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinatorHolder;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUi;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.network.HostUiRequestPayload;
import com.fish_dan_.data_energistics.network.TrinityHostedAutoBuildPayload;
import com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusPayload;
import com.fish_dan_.data_energistics.network.TrinityRefundPatternsPayload;
import com.fish_dan_.data_energistics.network.TrinityRefundRetainedItemsPayload;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class TrinityDataCoreMenu extends AbstractContainerMenu implements HostUiCoordinatorHolder {

    private static final long STATIC_ACTION_GENERATION = 1L;

    private final Inventory playerInventory;
    @Nullable
    private final TrinityDataCoreMenuHost host;
    private final UUID hostId;
    private final UUID menuSessionId;
    private final Consumer<CustomPacketPayload> hostedActionSink;
    private final TrinityHostedActionExecutor hostedActionExecutor;
    private final Map<HostUiKey, ClientHostedActionState> clientHostedActions = new HashMap<>();
    private final Map<HostUiKey, ServerHostedActionState> serverHostedActions = new HashMap<>();
    /** Double-sided child-window endpoint owned by this menu's mounted LDLib2 root. */
    @Getter
    private final HostUiCoordinator hostUiCoordinator;

    /** Opens the native Trinity menu for one exact live controller and transmits only its block position. */
    public static boolean open(ServerPlayer player, TrinityDataCoreBlockEntity host) {
        if (player == null || host == null) {
            Data_Energistics.LOGGER.error("Cannot open the Trinity Data Core menu without a player and host");
            throw new IllegalArgumentException("Trinity Data Core menu player and host cannot be null");
        }
        if (!isLiveHost(player, host)) {
            Data_Energistics.LOGGER.warn(
                    "Cannot open a stale or out-of-range Trinity Data Core menu: player={}, host={}, position={}",
                    player.getGameProfile().getName(),
                    host.getHostId(),
                    host.getBlockPos());
            return false;
        }
        try {
            UUID hostId = host.getHostId();
            UUID menuSessionId = UUID.randomUUID();
            boolean opened = player.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, menuPlayer) -> new TrinityDataCoreMenu(
                                    containerId,
                                    inventory,
                                    host,
                                    hostId,
                                    menuSessionId,
                                    PacketDistributor::sendToServer,
                                    new TrinityHostedActionExecutorImpl(host),
                                    null),
                            host.getBlockState().getBlock().getName()),
                    buffer -> {
                        buffer.writeBlockPos(host.getBlockPos());
                        buffer.writeUUID(hostId);
                        buffer.writeUUID(menuSessionId);
                    }).isPresent();
            if (!opened) {
                Data_Energistics.LOGGER.error(
                        "Failed to open the Trinity Data Core menu: player={}, host={}, position={}",
                        player.getGameProfile().getName(),
                        host.getHostId(),
                        host.getBlockPos());
            }
            return opened;
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to create the Trinity Data Core menu: player={}, host={}, position={}",
                    player.getGameProfile().getName(),
                    host.getHostId(),
                    host.getBlockPos(),
                    failure);
            return false;
        }
    }

    public TrinityDataCoreMenu(int id, Inventory playerInventory, @Nullable TrinityDataCoreMenuHost host) {
        this(
                id,
                playerInventory,
                host,
                PacketDistributor::sendToServer,
                new TrinityHostedActionExecutorImpl(host));
    }

    /** Creates the client menu from the exact host and session identities written by the server opening path. */
    public TrinityDataCoreMenu(int id,
                               Inventory playerInventory,
                               @Nullable TrinityDataCoreMenuHost host,
                               UUID hostId,
                               UUID menuSessionId) {
        this(
                id,
                playerInventory,
                host,
                hostId,
                menuSessionId,
                PacketDistributor::sendToServer,
                new TrinityHostedActionExecutorImpl(host),
                null);
    }

    /**
     * Creates a menu with injectable hosted business and transport boundaries for direct protocol tests.
     */
    TrinityDataCoreMenu(int id,
                        Inventory playerInventory,
                        @Nullable TrinityDataCoreMenuHost host,
                        Consumer<CustomPacketPayload> hostedActionSink,
                        TrinityHostedActionExecutor hostedActionExecutor) {
        this(
                id,
                playerInventory,
                host,
                host == null ? UUID.randomUUID() : host.getHostId(),
                UUID.randomUUID(),
                hostedActionSink,
                hostedActionExecutor,
                null);
    }

    /**
     * Creates a direct-test menu that can register real hosted providers before the coordinator seals their order.
     */
    TrinityDataCoreMenu(int id,
                        Inventory playerInventory,
                        @Nullable TrinityDataCoreMenuHost host,
                        Consumer<CustomPacketPayload> hostedActionSink,
                        TrinityHostedActionExecutor hostedActionExecutor,
                        @Nullable Consumer<HostUiExtension> additionalProviderRegistrar) {
        this(
                id,
                playerInventory,
                host,
                host == null ? UUID.randomUUID() : host.getHostId(),
                UUID.randomUUID(),
                hostedActionSink,
                hostedActionExecutor,
                additionalProviderRegistrar);
    }

    /**
     * Creates one menu bound to the exact server-issued host and opening session identities.
     */
    TrinityDataCoreMenu(int id,
                        Inventory playerInventory,
                        @Nullable TrinityDataCoreMenuHost host,
                        UUID hostId,
                        UUID menuSessionId,
                        Consumer<CustomPacketPayload> hostedActionSink,
                        TrinityHostedActionExecutor hostedActionExecutor,
                        @Nullable Consumer<HostUiExtension> additionalProviderRegistrar) {
        super(DEMenus.TRINITY_DATA_CORE.get(), id);
        if (playerInventory == null || hostId == null || menuSessionId == null || hostedActionSink == null ||
                hostedActionExecutor == null) {
            throw new IllegalArgumentException("Trinity menu identities and hosted action collaborators cannot be null");
        }
        this.playerInventory = playerInventory;
        this.host = host;
        this.hostId = hostId;
        this.menuSessionId = menuSessionId;
        this.hostedActionSink = hostedActionSink;
        this.hostedActionExecutor = hostedActionExecutor;
        this.hostUiCoordinator = TrinityDataCoreHostUi.mount(this, hostUi -> playerInventory.player.level().isClientSide ?
                createClientCoordinator(hostUi, playerInventory, additionalProviderRegistrar) :
                createServerCoordinator(hostUi, playerInventory, additionalProviderRegistrar));
    }

    /** Returns the player whose inventory owns this menu on both logical sides. */
    public Player getPlayer() {
        return this.playerInventory.player;
    }

    /** A status-only menu has no machine slots, so shift-click cannot move an item to another side. */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Keeps the menu bound to the same nearby live controller instance used during construction. */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return player == getPlayer() && this.host instanceof TrinityDataCoreBlockEntity dataCore &&
                isLiveHost(player, dataCore);
    }

    /** Releases the server-side LDLib2 tree when vanilla removes this native menu. */
    @Override
    public void removed(@NotNull Player player) {
        Throwable failure = null;
        try {
            super.removed(player);
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to remove the vanilla Trinity Data Core menu", exception);
            failure = exception;
        }
        try {
            removeModularUi();
        } catch (RuntimeException | Error exception) {
            Data_Energistics.LOGGER.error("Failed to remove the Trinity Data Core LDLib2 UI", exception);
            if (failure == null) {
                failure = exception;
            } else if (failure != exception) {
                failure.addSuppressed(exception);
            }
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /**
     * Returns the sealed draggable host API retained for Screen input and registered-window lifecycle requests.
     *
     * @return extension owned by this menu's coordinator
     */
    public HostUiExtension getHostUiExtension() {
        return this.hostUiCoordinator.hostUi();
    }

    /**
     * Revalidates the exact server player, current menu, holder, and business host before changing membership.
     *
     * @param player player who sent the lifecycle request
     * @return whether the current server menu may mutate its LDLib2 tree
     */
    @Override
    public boolean isHostUiAvailable(Player player) {
        return this.host != null && player == getPlayer() && player.containerMenu == this && stillValid(player);
    }

    public @Nullable TrinityDataCoreMenuHost getHost() {
        return this.host;
    }

    /** Returns the persistent controller identity sent when this exact menu was opened. */
    public UUID getHostId() {
        return this.hostId;
    }

    /** Returns the unique non-persistent identity for this one open menu lifecycle. */
    public UUID getMenuSessionId() {
        return this.menuSessionId;
    }

    /** Matches an ACK envelope only when it belongs to this host and this open-menu lifecycle. */
    public boolean matchesHostedActionEnvelope(UUID hostId, UUID menuSessionId) {
        return this.hostId.equals(hostId) && this.menuSessionId.equals(menuSessionId);
    }

    /** Sends one stable CPU selection with the host identity received through the LDLib2 status channel. */
    public boolean sendOpenCpuStatus(UUID syncedHostId, int cpuNumber) {
        if (syncedHostId == null) {
            throw new IllegalArgumentException("Synchronized Trinity host ID is required");
        }
        if (this.host == null || !getPlayer().level().isClientSide() || getPlayer().containerMenu != this ||
                !stillValid(getPlayer())) {
            return false;
        }
        try {
            this.hostedActionSink.accept(new TrinityOpenCpuStatusPayload(
                    this.containerId,
                    syncedHostId,
                    cpuNumber));
            return true;
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to request Trinity CPU status: player={}, container={}, host={}, cpu={}",
                    getPlayer().getName().getString(),
                    this.containerId,
                    syncedHostId,
                    cpuNumber,
                    failure);
            return false;
        }
    }

    /**
     * Sends one complete revision-bound automatic-build submission from its exact still-open window.
     *
     * @param generation accepted OPEN sequence owned by the calling provider
     * @param submission complete recipe-affecting selection without view state
     * @return whether a new action ticket was emitted
     */
    public boolean sendHostedAutoBuild(long generation, TrinityAutoBuildSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Trinity hosted auto-build submission cannot be null");
        }
        return sendHostedAction(
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                generation,
                ticket -> new TrinityHostedAutoBuildPayload(
                        this.containerId,
                        this.hostId,
                        this.menuSessionId,
                        ticket.generation(),
                        ticket.sequence(),
                        submission));
    }

    /** Sends the static action that returns only installed patterns from the active catalog. */
    public boolean sendRefundPatterns() {
        return sendStaticAction(
                TrinityDataCoreHostUiKeys.REFUND_PATTERNS,
                ticket -> new TrinityRefundPatternsPayload(
                        this.containerId,
                        this.hostId,
                        this.menuSessionId,
                        ticket.sequence()));
    }

    /** Sends the static action that returns queued inputs and pending outputs without clearing patterns. */
    public boolean sendRefundRetainedItems() {
        return sendStaticAction(
                TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS,
                ticket -> new TrinityRefundRetainedItemsPayload(
                        this.containerId,
                        this.hostId,
                        this.menuSessionId,
                        ticket.sequence()));
    }

    /**
     * Reports whether one provider generation is waiting for its exact server ACK.
     *
     * @param key        automatic-build hosted window
     * @param generation window generation owned by the provider
     * @return whether its confirm action must remain disabled
     */
    public boolean isHostedActionPending(HostUiKey key, long generation) {
        ClientHostedActionState state = this.clientHostedActions.get(key);
        return state != null && state.generation == generation && state.pending != null;
    }

    /**
     * Consumes the terminal result retained for one exact provider generation.
     *
     * @param key        automatic-build hosted window
     * @param generation window generation owned by the provider
     * @return completed or rejected result, or null when no result is ready
     */
    @Nullable
    public TrinityHostedActionResult consumeHostedActionResult(HostUiKey key, long generation) {
        ClientHostedActionState state = this.clientHostedActions.get(key);
        if (state == null || state.generation != generation) {
            return null;
        }
        TrinityHostedActionResult result = state.result;
        state.result = null;
        return result;
    }

    /** Accepts an ACK only when it exactly matches the currently pending client ticket. */
    public boolean handleHostedActionResponse(TrinityHostedActionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Trinity hosted action response cannot be null");
        }
        ClientHostedActionState state = this.clientHostedActions.get(result.key());
        if (state == null || state.pending == null || !state.pending.equals(result.ticket())) {
            return false;
        }
        state.pending = null;
        state.result = result;
        return true;
    }

    /** Rejects an ACK from any replaced host or prior menu opening before touching pending action state. */
    public boolean handleHostedActionResponse(UUID hostId,
                                              UUID menuSessionId,
                                              TrinityHostedActionResult result) {
        if (hostId == null || menuSessionId == null) {
            throw new IllegalArgumentException("Trinity hosted action response identities cannot be null");
        }
        return matchesHostedActionEnvelope(hostId, menuSessionId) && handleHostedActionResponse(result);
    }

    /**
     * Claims a server action sequence after routing, host availability, and open-generation checks succeed.
     *
     * @param ticket exact hosted action ticket
     * @return whether the sequence is newer than every accepted request in this key and generation
     */
    public boolean claimHostedActionSequence(TrinityHostedActionTicket ticket) {
        requireActionKey(ticket.key());
        ServerHostedActionState state = this.serverHostedActions.computeIfAbsent(
                ticket.key(),
                ignored -> new ServerHostedActionState());
        if (state.generation != ticket.generation()) {
            state.generation = ticket.generation();
            state.lastAcceptedSequence = 0L;
        }
        if (ticket.sequence() <= state.lastAcceptedSequence) {
            return false;
        }
        state.lastAcceptedSequence = ticket.sequence();
        return true;
    }

    /** Runs the existing atomic builder entry exactly once after submission reconstruction and ticket claim. */
    public void executeHostedAutoBuild(Player player, TrinityAutoBuildRequest request) {
        this.hostedActionExecutor.autoBuild(player, request);
    }

    /** Executes the server-authoritative installed-pattern refund after the request ticket was claimed. */
    public TrinityHostedActionStatus executeRefundPatterns(Player player) {
        return this.hostedActionExecutor.refundPatterns(player);
    }

    /** Executes the server-authoritative queued-input and pending-output refund after ticket claim. */
    public TrinityHostedActionStatus executeRefundRetainedItems(Player player) {
        return this.hostedActionExecutor.refundRetainedItems(player);
    }

    private boolean sendHostedAction(HostUiKey key,
                                     long generation,
                                     Function<TrinityHostedActionTicket, CustomPacketPayload> payloadFactory) {
        requireHostedWindowActionKey(key);
        if (this.hostUiCoordinator.isTerminal() || this.hostUiCoordinator.pendingRequest() != null ||
                !this.hostUiCoordinator.hostUi().isOpen(key, generation)) {
            return false;
        }
        return sendAction(key, generation, payloadFactory);
    }

    /** Sends an always-mounted menu action without pretending it belongs to a hosted child window. */
    private boolean sendStaticAction(HostUiKey key,
                                     Function<TrinityHostedActionTicket, CustomPacketPayload> payloadFactory) {
        requireStaticActionKey(key);
        Player player = getPlayer();
        if (this.host == null || player.containerMenu != this || !stillValid(player)) {
            return false;
        }
        return sendAction(key, STATIC_ACTION_GENERATION, payloadFactory);
    }

    /** Allocates one isolated action ticket and clears it only when its exact response arrives. */
    private boolean sendAction(HostUiKey key,
                               long generation,
                               Function<TrinityHostedActionTicket, CustomPacketPayload> payloadFactory) {
        requireActionKey(key);
        ClientHostedActionState state = this.clientHostedActions.computeIfAbsent(
                key,
                ignored -> new ClientHostedActionState());
        if (state.generation != generation) {
            state.reset(generation);
        }
        if (state.pending != null || state.nextSequence > TrinityHostedActionTicket.MAX_SEQUENCE) {
            return false;
        }
        TrinityHostedActionTicket ticket = new TrinityHostedActionTicket(key, generation, state.nextSequence++);
        state.pending = ticket;
        state.result = null;
        try {
            this.hostedActionSink.accept(payloadFactory.apply(ticket));
            return true;
        } catch (RuntimeException failure) {
            state.pending = null;
            state.result = new TrinityHostedActionResult(
                    key,
                    generation,
                    ticket.sequence(),
                    TrinityHostedActionStatus.INTERNAL_ERROR);
            Data_Energistics.LOGGER.error(
                    "Failed to send Trinity hosted action for player {}, menu {}, host {}, key {}, generation {}, sequence {}",
                    getPlayer().getName().getString(),
                    this.containerId,
                    this.host,
                    key.id(),
                    generation,
                    ticket.sequence(),
                    failure);
            return false;
        }
    }

    private static void requireActionKey(HostUiKey key) {
        if (!TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key) &&
                !TrinityDataCoreHostUiKeys.REFUND_PATTERNS.equals(key) &&
                !TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS.equals(key)) {
            throw new IllegalArgumentException("Unsupported Trinity hosted action key: " + key);
        }
    }

    private static void requireHostedWindowActionKey(HostUiKey key) {
        requireActionKey(key);
        if (!TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key)) {
            throw new IllegalArgumentException("Trinity action does not own a hosted child window: " + key);
        }
    }

    private static void requireStaticActionKey(HostUiKey key) {
        requireActionKey(key);
        if (!TrinityDataCoreHostUiKeys.REFUND_PATTERNS.equals(key) &&
                !TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS.equals(key)) {
            throw new IllegalArgumentException("Trinity action is not a static menu action: " + key);
        }
    }

    private static boolean isLiveHost(Player player, TrinityDataCoreBlockEntity host) {
        var level = host.getLevel();
        var position = host.getBlockPos();
        return level != null && player.level() == level && level.getBlockEntity(position) == host &&
                level.getBlockState(position).is(DEBlocks.TRINITY_DATA_CORE.get()) &&
                player.distanceToSqr(
                        position.getX() + 0.5D,
                        position.getY() + 0.5D,
                        position.getZ() + 0.5D) <= 64.0D;
    }

    private void removeModularUi() {
        if (!(this instanceof IModularUIHolderMenu holder)) {
            throw new IllegalStateException("Trinity Data Core menu does not implement the LDLib2 menu holder");
        }
        var modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new IllegalStateException("Trinity Data Core menu has no mounted LDLib2 UI to remove");
        }
        modularUI.onRemoved();
    }

    private HostUiCoordinator createClientCoordinator(HostUiExtension hostUi,
                                                      Inventory playerInventory,
                                                      @Nullable Consumer<HostUiExtension> additionalProviderRegistrar) {
        registerAdditionalProviders(hostUi, additionalProviderRegistrar);
        return HostUiCoordinator.createClient(
                hostUi,
                request -> PacketDistributor.sendToServer(new HostUiRequestPayload(this.containerId, request)),
                playerInventory.player::closeContainer);
    }

    private static HostUiCoordinator createServerCoordinator(
                                                             HostUiExtension hostUi,
                                                             Inventory playerInventory,
                                                             @Nullable Consumer<HostUiExtension> additionalProviderRegistrar) {
        registerAdditionalProviders(hostUi, additionalProviderRegistrar);
        return HostUiCoordinator.createServer(hostUi, playerInventory.player::closeContainer);
    }

    private static void registerAdditionalProviders(
                                                    HostUiExtension hostUi,
                                                    @Nullable Consumer<HostUiExtension> additionalProviderRegistrar) {
        if (additionalProviderRegistrar != null) {
            additionalProviderRegistrar.accept(hostUi);
        }
    }

    /** Narrow business boundary kept independent from payload routing and replay protection. */
    interface TrinityHostedActionExecutor {

        /** Invokes one existing atomic automatic-build attempt. */
        void autoBuild(Player player, TrinityAutoBuildRequest request);

        /** Invokes one complete installed-pattern refund attempt. */
        TrinityHostedActionStatus refundPatterns(Player player);

        /** Invokes one complete queued-input and pending-output refund attempt. */
        TrinityHostedActionStatus refundRetainedItems(Player player);
    }

    /** Production business adapter for the exact menu host captured during construction. */
    private record TrinityHostedActionExecutorImpl(@Nullable TrinityDataCoreMenuHost host)
            implements TrinityHostedActionExecutor {

        @Override
        public void autoBuild(Player player, TrinityAutoBuildRequest request) {
            if (!(this.host instanceof TrinityDataCoreBlockEntity dataCore)) {
                throw new IllegalStateException("Trinity hosted auto-build requires a data core block entity host");
            }
            dataCore.autoBuildTrinityStructure(player, request);
        }

        @Override
        public TrinityHostedActionStatus refundPatterns(Player player) {
            if (this.host == null) {
                throw new IllegalStateException("Trinity installed-pattern refund requires a data core host");
            }
            return switch (this.host.tryRefundPatterns(player)) {
                case COMPLETED -> TrinityHostedActionStatus.COMPLETED;
                case NO_PATTERNS -> TrinityHostedActionStatus.NO_OP;
                case BLOCKED_BY_WORK, STALE -> TrinityHostedActionStatus.STALE_STATE;
                case DELIVERY_REJECTED, DELIVERY_FAILED -> TrinityHostedActionStatus.DELIVERY_FAILED;
                case INTERNAL_ERROR -> TrinityHostedActionStatus.INTERNAL_ERROR;
            };
        }

        @Override
        public TrinityHostedActionStatus refundRetainedItems(Player player) {
            if (this.host == null) {
                throw new IllegalStateException("Trinity retained-item refund requires a data core host");
            }
            if (!this.host.hasRefundablePatternState()) {
                return this.host.isCraftingStructureFormed() ?
                        TrinityHostedActionStatus.NO_OP : TrinityHostedActionStatus.STALE_STATE;
            }
            return this.host.tryRefundAll(player) ?
                    TrinityHostedActionStatus.COMPLETED : TrinityHostedActionStatus.DELIVERY_FAILED;
        }
    }

    /** Mutable client protocol state scoped to one hosted action key. */
    private static final class ClientHostedActionState {

        private long generation;
        private long nextSequence = 1L;
        @Nullable
        private TrinityHostedActionTicket pending;
        @Nullable
        private TrinityHostedActionResult result;

        private void reset(long generation) {
            this.generation = generation;
            this.nextSequence = 1L;
            this.pending = null;
            this.result = null;
        }
    }

    /** Mutable replay guard scoped to one hosted action key on the server menu. */
    private static final class ServerHostedActionState {

        private long generation;
        private long lastAcceptedSequence;
    }
}
