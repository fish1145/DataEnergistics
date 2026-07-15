package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinatorHolder;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUi;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.network.HostUiRequestPayload;
import com.fish_dan_.data_energistics.network.TrinityHostedAutoBuildPayload;
import com.fish_dan_.data_energistics.network.TrinityOpenCpuStatusPayload;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.AEBaseMenu;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class TrinityDataCoreMenu extends AEBaseMenu implements HostUiCoordinatorHolder {

    @Nullable
    private final TrinityDataCoreMenuHost host;
    private final Consumer<CustomPacketPayload> hostedActionSink;
    private final TrinityHostedActionExecutor hostedActionExecutor;
    private final Map<HostUiKey, ClientHostedActionState> clientHostedActions = new HashMap<>();
    private final Map<HostUiKey, ServerHostedActionState> serverHostedActions = new HashMap<>();
    /** Double-sided child-window endpoint owned by this menu's mounted LDLib2 root. */
    @Getter
    private final HostUiCoordinator hostUiCoordinator;

    public TrinityDataCoreMenu(int id, Inventory playerInventory, @Nullable TrinityDataCoreMenuHost host) {
        this(
                id,
                playerInventory,
                host,
                PacketDistributor::sendToServer,
                new TrinityHostedActionExecutorImpl(host));
    }

    /**
     * Creates a menu with injectable hosted business and transport boundaries for direct protocol tests.
     */
    TrinityDataCoreMenu(int id,
                        Inventory playerInventory,
                        @Nullable TrinityDataCoreMenuHost host,
                        Consumer<CustomPacketPayload> hostedActionSink,
                        TrinityHostedActionExecutor hostedActionExecutor) {
        this(id, playerInventory, host, hostedActionSink, hostedActionExecutor, null);
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
        super(ModMenus.TRINITY_DATA_CORE.get(), id, playerInventory, host);
        if (hostedActionSink == null || hostedActionExecutor == null) {
            throw new IllegalArgumentException("Trinity hosted action collaborators cannot be null");
        }
        this.host = host;
        this.hostedActionSink = hostedActionSink;
        this.hostedActionExecutor = hostedActionExecutor;
        createPlayerInventorySlots(playerInventory);
        this.hostUiCoordinator = TrinityDataCoreHostUi.mount(this, hostUi -> playerInventory.player.level().isClientSide ?
                createClientCoordinator(hostUi, playerInventory, additionalProviderRegistrar) :
                createServerCoordinator(hostUi, playerInventory, additionalProviderRegistrar));
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
        return this.host != null && player == getPlayer() && player.containerMenu == this && isValidMenu() &&
                stillValid(player) && getLocator() != null &&
                getLocator().locate(player, TrinityDataCoreMenuHost.class) == this.host;
    }

    public @Nullable TrinityDataCoreMenuHost getHost() {
        return this.host;
    }

    /** Sends one stable CPU selection with the host identity received through the LDLib2 status channel. */
    public boolean sendOpenCpuStatus(UUID syncedHostId, int cpuNumber) {
        if (syncedHostId == null) {
            throw new IllegalArgumentException("Synchronized Trinity host ID is required");
        }
        if (this.host == null || !getPlayer().level().isClientSide() || getPlayer().containerMenu != this ||
                !isValidMenu()) {
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
                        ticket.generation(),
                        ticket.sequence(),
                        submission));
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

    /**
     * Claims a server action sequence after routing, host availability, and open-generation checks succeed.
     *
     * @param ticket exact hosted action ticket
     * @return whether the sequence is newer than every accepted request in this key and generation
     */
    public boolean claimHostedActionSequence(TrinityHostedActionTicket ticket) {
        requireHostedActionKey(ticket.key());
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

    private boolean sendHostedAction(HostUiKey key,
                                     long generation,
                                     Function<TrinityHostedActionTicket, CustomPacketPayload> payloadFactory) {
        requireHostedActionKey(key);
        if (this.hostUiCoordinator.isTerminal() || this.hostUiCoordinator.pendingRequest() != null ||
                !this.hostUiCoordinator.hostUi().isOpen(key, generation)) {
            return false;
        }
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
        } catch (RuntimeException | Error failure) {
            state.pending = null;
            state.result = new TrinityHostedActionResult(
                    key,
                    generation,
                    ticket.sequence(),
                    TrinityHostedActionStatus.REJECTED);
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

    private static void requireHostedActionKey(HostUiKey key) {
        if (!TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key)) {
            throw new IllegalArgumentException("Unsupported Trinity hosted action key: " + key);
        }
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
    }

    /** Production business adapter for the exact menu host captured during construction. */
    private static final class TrinityHostedActionExecutorImpl implements TrinityHostedActionExecutor {

        @Nullable
        private final TrinityDataCoreMenuHost host;

        private TrinityHostedActionExecutorImpl(@Nullable TrinityDataCoreMenuHost host) {
            this.host = host;
        }

        @Override
        public void autoBuild(Player player, TrinityAutoBuildRequest request) {
            if (!(this.host instanceof TrinityDataCoreBlockEntity dataCore)) {
                throw new IllegalStateException("Trinity hosted auto-build requires a data core block entity host");
            }
            dataCore.autoBuildTrinityStructure(player, request);
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
