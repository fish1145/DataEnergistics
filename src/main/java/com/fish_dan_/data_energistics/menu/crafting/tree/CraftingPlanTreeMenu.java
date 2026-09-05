package com.fish_dan_.data_energistics.menu.crafting.tree;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityCraftingRequestContext;
import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalHostAccessor;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.menu.crafting.tree.selection.CraftingPlanCpuSelection;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeRequest;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeResult;
import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanTreeSession;
import com.fish_dan_.data_energistics.network.crafting.tree.action.CraftingPlanTreeActionPayload;
import com.fish_dan_.data_energistics.network.crafting.tree.action.CraftingPlanTreeActionPayload.Action;
import com.fish_dan_.data_energistics.network.crafting.tree.assembly.CraftingPlanGraphAssembler;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphPayload;
import com.fish_dan_.data_energistics.network.crafting.tree.protocol.CraftingPlanGraphReceiver;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ISubMenuHost;
import appeng.me.helpers.PlayerSource;
import appeng.menu.AEBaseMenu;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftConfirmMenu.SyncableSubmitResult;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** Independent server menu for the graph confirmation page; no AE2 screen or AE2CT state is retained. */
public final class CraftingPlanTreeMenu extends AEBaseMenu implements ISubMenu, CraftingPlanGraphReceiver {

    private final UUID sessionId;
    private final ISubMenuHost host;
    private final @Nullable CraftingPlanTreeSession session;
    private final @Nullable IGrid boundGrid;
    private final CraftingPlanCpuSelection cpuSelection;
    private final CraftingPlanGraphAssembler assembler = new CraftingPlanGraphAssembler();
    private @Nullable CraftingPlanGraph graph;
    private long graphRevision = -1;
    private long sentRevision = -1;

    @GuiSync(0)
    public long planRevision;
    @GuiSync(1)
    public boolean planning;
    @GuiSync(2)
    public boolean startable;
    @GuiSync(3)
    public Component cpuName = Component.empty();
    @GuiSync(4)
    public Component status = Component.empty();
    @GuiSync(5)
    public Component graphError = Component.empty();
    @GuiSync(6)
    public long cpuBytes;
    @GuiSync(7)
    public int cpuCoProcessors;
    @GuiSync(8)
    public long planningNanos;
    @GuiSync(9)
    public SyncableSubmitResult submitError = new SyncableSubmitResult((ICraftingSubmitResult) null);
    @GuiSync(10)
    public boolean resultReady;

    private CraftingPlanTreeMenu(int id, Inventory inventory, ISubMenuHost host, MenuHostLocator locator,
                                 UUID sessionId, long revision, @Nullable CraftingPlanTreeSession session,
                                 @Nullable Object handoffOwner) {
        super(DEMenus.CRAFTING_PLAN_TREE.get(), id, inventory, host);
        this.host = host;
        this.sessionId = sessionId;
        this.planRevision = revision;
        this.session = session;
        setLocator(locator);
        this.boundGrid = grid();
        this.cpuSelection = new CraftingPlanCpuSelection(session == null ? null : session.selectedCpu());
        if (session != null) {
            if (handoffOwner == null || !session.request().playerId().equals(inventory.player.getUUID())) {
                throw new IllegalArgumentException("Invalid plan-tree session owner");
            }
            session.transfer(handoffOwner, this);
        }
    }

    /** Network creation sends only identity and host location; graph records arrive in bounded payloads. */
    public static CraftingPlanTreeMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        MenuHostLocator locator = MenuLocators.readFromPacket(buffer);
        ISubMenuHost host = locator.locate(inventory.player, ISubMenuHost.class);
        if (host == null) throw new IllegalArgumentException("Plan-tree host is unavailable");
        return new CraftingPlanTreeMenu(id, inventory, host, locator, buffer.readUUID(), buffer.readVarLong(), null, null);
    }

    /** Transfers before openMenu removes the originating menu. Failed opens never leave an unowned session. */
    public static void open(ServerPlayer player, CraftingPlanTreeSession session, Object previousOwner) {
        CraftingPlanTreeRequest request = session.request();
        Object handoff = new Object();
        session.transfer(previousOwner, handoff);
        @Nullable
        CraftingPlanTreeMenu[] created = new CraftingPlanTreeMenu[1];
        try {
            boolean opened = player.openMenu(new MenuProvider() {

                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.data_energistics.plan_tree.title");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player owner) {
                    CraftingPlanTreeMenu menu = new CraftingPlanTreeMenu(id, inventory, request.host(), request.locator(), session.id(),
                            session.revision(), session, handoff);
                    created[0] = menu;
                    return menu;
                }

                @Override
                public boolean shouldTriggerClientSideContainerClosingOnOpen() {
                    return false;
                }
            }, buffer -> {
                MenuLocators.writeToPacket(buffer, request.locator());
                buffer.writeUUID(session.id());
                buffer.writeVarLong(session.revision());
            }).isPresent();
            if (!opened) {
                if (player.containerMenu == previousOwner) session.transfer(handoff, previousOwner);
                else session.closeIfOwnedBy(handoff);
                player.sendSystemMessage(Component.translatable("gui.data_energistics.plan_tree.open_failed"));
            }
        } catch (RuntimeException failure) {
            if (created[0] != null) {
                session.closeIfOwnedBy(created[0]);
                created[0].setValidMenu(false);
            }
            session.closeIfOwnedBy(handoff);
            Data_Energistics.LOGGER.error("Failed to open plan-tree session {} for {}", session.id(), player.getUUID(), failure);
            player.sendSystemMessage(Component.translatable("gui.data_energistics.plan_tree.open_failed"));
        }
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public @Nullable CraftingPlanGraph graph() {
        return this.graphRevision == this.planRevision ? this.graph : null;
    }

    public void request(Action action) {
        if (!isClientSide()) throw new IllegalStateException("Plan-tree request called on server");
        PacketDistributor.sendToServer(new CraftingPlanTreeActionPayload(this.containerId, this.sessionId, this.planRevision, action));
    }

    public void handleAction(CraftingPlanTreeActionPayload payload) {
        CraftingPlanTreeSession session = this.session;
        if (session == null || payload.containerId() != this.containerId || !payload.sessionId().equals(this.sessionId) || payload.revision() != session.revision() || getPlayer().containerMenu != this || !session.isOwnedBy(this) || !session.request().playerId().equals(getPlayer().getUUID())) return;
        IGrid grid = accessibleGrid();
        if (grid == null) return;
        try {
            switch (payload.action()) {
                case CANCEL -> cancel(session);
                case RETURN_LIST -> {
                    if (!session.isPlanning() && session.result() != null) returnToList(session);
                }
                case REPLAN -> {
                    if (!session.isPlanning()) replan(session, grid);
                }
                case START -> start(session, grid);
                case NEXT_CPU, PREVIOUS_CPU -> {
                    CraftingPlanTreeResult current = session.result();
                    if (session.isPlanning() || current == null || current.plan().simulation()) return;
                    refreshCpuSelection(session, grid);
                    this.cpuSelection.cycle(payload.action() == Action.NEXT_CPU);
                    session.selectCpu(this, this.cpuSelection.selected());
                }
            }
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Plan-tree action {} failed session={} revision={}", payload.action(), this.sessionId, this.planRevision, failure);
            this.status = Component.translatable("gui.data_energistics.plan_tree.action_failed");
        }
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) return;
        IGrid grid = accessibleGrid();
        if (grid == null || this.session == null) {
            setValidMenu(false);
            return;
        }
        try {
            ICraftingPlan completed = this.session.takeCompletedPlan(this);
            if (completed != null) this.session.publish(this,
                    CraftingPlanTreeResult.create(completed, this.session.request(), grid, actionSource()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failCalculation(interrupted);
        } catch (ExecutionException | RuntimeException failure) {
            failCalculation(failure);
        }
        this.planRevision = this.session.revision();
        this.planning = this.session.isPlanning();
        CraftingPlanTreeResult result = this.session.result();
        this.resultReady = result != null;
        refreshCpuSelection(this.session, grid);
        if (result != null) {
            this.planningNanos = result.planningNanos();
            if (this.sentRevision != this.planRevision) {
                this.graphError = result.graphError();
                if (result.graph() != null) {
                    try {
                        for (var payload : CraftingPlanGraphPayload.batches(this.containerId, this.sessionId,
                                this.planRevision, result.graph(), getPlayer().registryAccess())) {
                            PacketDistributor.sendToPlayer((ServerPlayer) getPlayer(), payload);
                        }
                    } catch (RuntimeException failure) {
                        Data_Energistics.LOGGER.error("Plan-tree graph transfer failed session={} revision={}", this.sessionId, this.planRevision, failure);
                        this.graphError = Component.translatable("gui.data_energistics.plan_tree.too_large");
                    }
                }
                this.sentRevision = this.planRevision;
            }
        }
        super.broadcastChanges();
    }

    private @Nullable CraftingPlanTreeResult refreshCpuSelection(CraftingPlanTreeSession session, IGrid grid) {
        CraftingPlanTreeResult result = session.result();
        this.cpuSelection.refresh(grid.getCraftingService().getCpus(), result == null ? null : result.plan());
        var cpu = this.cpuSelection.selected();
        session.selectCpu(this, cpu);
        this.cpuName = !this.cpuSelection.available() ? Component.translatable("gui.data_energistics.plan_tree.no_cpu") : cpu == null ? Component.translatable("gui.data_energistics.plan_tree.automatic") : cpu.getName() == null ? Component.translatable("gui.data_energistics.plan_tree.unnamed_cpu") : cpu.getName();
        this.cpuBytes = cpu == null ? 0 : cpu.getAvailableStorage();
        this.cpuCoProcessors = cpu == null ? 0 : cpu.getCoProcessors();
        this.startable = result != null && !result.plan().simulation() && this.cpuSelection.available() && !session.isPlanning();
        return this.startable ? result : null;
    }

    private void start(CraftingPlanTreeSession session, IGrid grid) {
        CraftingPlanTreeResult result = refreshCpuSelection(session, grid);
        if (result == null) return;
        var submitted = grid.getCraftingService().submitJob(result.plan(), null, this.cpuSelection.selected(), true, actionSource());
        this.submitError = new SyncableSubmitResult(submitted);
        if (submitted.successful()) finishOrQueue(session);
    }

    private void replan(CraftingPlanTreeSession session, IGrid grid) {
        var request = session.request();
        var future = grid.getCraftingService().beginCraftingCalculation(getPlayer().level(), this::actionSource,
                request.target(), request.amount(), CalculationStrategy.CRAFT_LESS);
        session.beginPlanning(this, future);
        this.planning = true;
        this.startable = false;
        this.resultReady = false;
        this.planRevision = session.revision();
        this.graphError = Component.empty();
        this.status = Component.empty();
        this.submitError = new SyncableSubmitResult((ICraftingSubmitResult) null);
    }

    private void returnToList(CraftingPlanTreeSession session) {
        Object handoff = new Object();
        session.transfer(this, handoff);
        try {
            boolean opened = MenuOpener.returnTo(CraftConfirmMenu.TYPE, getPlayer(), session.request().locator());
            if (!opened || !(getPlayer().containerMenu instanceof CraftingPlanSessionTransfer target)) {
                throw new IllegalStateException("AE2 confirmation menu did not open for plan-tree return");
            }
            target.data_energistics$adoptPlanTreeSession(session, handoff);
        } catch (RuntimeException failure) {
            if (getPlayer().containerMenu == this && session.isOwnedBy(handoff)) session.transfer(handoff, this);
            else {
                session.closeIfOwnedBy(handoff);
                session.closeIfOwnedBy(getPlayer().containerMenu);
                if (getPlayer().containerMenu instanceof AEBaseMenu opened) opened.setValidMenu(false);
            }
            throw failure;
        }
    }

    private void cancel(CraftingPlanTreeSession session) {
        var request = session.request();
        if (request.queue() != null && !request.queue().isEmpty()) {
            finishOrQueue(session);
            return;
        }
        CraftAmountMenu.open((ServerPlayer) getPlayer(), request.locator(), request.target(),
                (int) Math.min(Integer.MAX_VALUE, request.amount()));
        if (getPlayer().containerMenu instanceof TrinityCraftAmountMenuState amountMenu) {
            amountMenu.data_energistics$setQuantityMode(request.quantityMode());
            amountMenu.data_energistics$setInitialAmount(request.amount());
        }
    }

    private void finishOrQueue(CraftingPlanTreeSession session) {
        var request = session.request();
        if (request.queue() != null && !request.queue().isEmpty()) {
            CraftConfirmMenu.openWithCraftingList(getActionHost(), (ServerPlayer) getPlayer(), request.locator(), request.queue());
        } else if (getTarget() instanceof UniversalTerminalPart part) {
            part.returnToMainMenu(getPlayer(), this);
        } else if (getTarget() instanceof UniversalTerminalHostAccessor accessor) {
            accessor.getUniversalTerminalPart().returnToMainMenu(getPlayer(), this);
        } else {
            this.host.returnToMainMenu(getPlayer(), this);
        }
    }

    private void failCalculation(Exception failure) {
        Data_Energistics.LOGGER.error("Plan-tree calculation failed session={} revision={}", this.sessionId, this.planRevision, failure);
        this.status = Component.translatable("gui.data_energistics.plan_tree.calculation_failed");
        this.startable = false;
    }

    private IActionSource actionSource() {
        if (this.session == null) throw new IllegalStateException("Client cannot obtain plan-tree action source");
        return TrinityCraftingRequestContext.attach(new PlayerSource(getPlayer(), getActionHost()), this.session.request().quantityMode());
    }

    private @Nullable IGrid grid() {
        IActionHost host = getActionHost();
        var node = host == null ? null : host.getActionableNode();
        return node == null ? null : node.getGrid();
    }

    private @Nullable IGrid accessibleGrid() {
        IGrid current = grid();
        if (current == null || current != this.boundGrid || !isValidMenu()) return null;
        var located = getLocator().locate(getPlayer(), IActionHost.class);
        return located != null && located.getActionableNode() != null && located.getActionableNode().getGrid() == current ? current : null;
    }

    @Override
    public ISubMenuHost getHost() {
        return this.host;
    }

    @Override
    public void receiveCraftingPlanGraph(CraftingPlanGraphPayload payload) {
        if (!isClientSide() || payload.containerId() != this.containerId || !payload.sessionId().equals(this.sessionId) || payload.revision() < this.planRevision) return;
        try {
            this.assembler.accept(payload).ifPresent(snapshot -> {
                this.graph = snapshot;
                this.graphRevision = payload.revision();
            });
        } catch (IllegalArgumentException failure) {
            Data_Energistics.LOGGER.error("Rejected plan-tree snapshot session={} revision={}", this.sessionId, payload.revision(), failure);
            this.graphError = Component.translatable("gui.data_energistics.plan_tree.graph_failed");
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.assembler.clear();
        this.graph = null;
        if (this.session != null) this.session.closeIfOwnedBy(this);
    }
}
