package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityInformationExchangeDepotStatus;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.exchange.TrinityInformationExchangeDepotUi;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;

/** Dedicated mode menu for one physical Trinity information exchange depot. */
public final class TrinityInformationExchangeDepotMenu extends AEBaseMenu {

    private static final String ACTION_SET_MODE = "set_information_exchange_mode";
    private static final TrinityPatternMaintenanceSnapshot.Operation[] MAINTENANCE_OPERATIONS = TrinityPatternMaintenanceSnapshot.Operation.values();
    private static final TrinityPatternMaintenanceSnapshot.Stage[] MAINTENANCE_STAGES = TrinityPatternMaintenanceSnapshot.Stage.values();

    private final TrinityInformationExchangeDepotMenuHost host;

    @GuiSync(801)
    public int modeId;
    @GuiSync(802)
    public int maintenanceOperationId;
    @GuiSync(803)
    public int maintenanceStageId;
    @GuiSync(804)
    public long maintenanceCompletedUnits;
    @GuiSync(805)
    public long maintenanceTotalUnits;
    @GuiSync(806)
    public long maintenanceTickNanos;
    @GuiSync(807)
    public long maintenanceTickWorkUnits;
    @GuiSync(808)
    public long coreTickNanos;
    @GuiSync(809)
    public long exchangeDepotTickNanos;

    public TrinityInformationExchangeDepotMenu(
                                               int id,
                                               Inventory playerInventory,
                                               TrinityInformationExchangeDepotMenuHost host) {
        super(DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(), id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeFromClient);
        refreshState();
        TrinityInformationExchangeDepotUi.mount(
                this,
                Component.translatable("gui.data_energistics.trinity_information_exchange_depot.title"));
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            refreshState();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.host.isInformationExchangeDepotMenuValid(player);
    }

    public StorageMode mode() {
        return StorageMode.fromNetworkId(this.modeId);
    }

    /** Returns the synchronized maintenance operation without conflating an installed-pattern refund with migration. */
    public TrinityPatternMaintenanceSnapshot.Operation maintenanceOperation() {
        return enumValue(
                MAINTENANCE_OPERATIONS,
                this.maintenanceOperationId,
                "maintenance operation");
    }

    /** Returns the exact bounded migration/refund phase reported by the Data Core. */
    public TrinityPatternMaintenanceSnapshot.Stage maintenanceStage() {
        return enumValue(
                MAINTENANCE_STAGES,
                this.maintenanceStageId,
                "maintenance stage");
    }

    public void sendSetMode(StorageMode mode) {
        if (!isClientSide() || getPlayer().containerMenu != this) {
            return;
        }
        sendClientAction(ACTION_SET_MODE, mode.networkId());
    }

    private void setModeFromClient(int requestedModeId) {
        Player player = getPlayer();
        try {
            StorageMode requestedMode = StorageMode.fromNetworkId(requestedModeId);
            if (player.containerMenu != this || !stillValid(player) ||
                    !this.host.setInformationExchangeMode(player, requestedMode)) {
                Data_Energistics.LOGGER.warn(
                        "Rejected Trinity information exchange mode change: player={}, menu={}, mode={}",
                        player.getName().getString(),
                        this.containerId,
                        requestedMode);
            }
        } catch (IllegalArgumentException exception) {
            Data_Energistics.LOGGER.warn(
                    "Rejected malformed Trinity information exchange mode: player={}, menu={}, mode={}",
                    player.getName().getString(),
                    this.containerId,
                    requestedModeId,
                    exception);
        }
        refreshState();
        broadcastChanges();
    }

    private void refreshState() {
        this.modeId = this.host.informationExchangeMode().networkId();
        TrinityInformationExchangeDepotStatus status = this.host.informationExchangeStatus();
        TrinityPatternMaintenanceSnapshot maintenance = status.patternMaintenance();
        this.maintenanceOperationId = maintenance.operation().ordinal();
        this.maintenanceStageId = maintenance.stage().ordinal();
        this.maintenanceCompletedUnits = maintenance.completedUnits();
        this.maintenanceTotalUnits = maintenance.totalUnits();
        this.maintenanceTickNanos = maintenance.lastTickNanos();
        this.maintenanceTickWorkUnits = maintenance.lastTickWorkUnits();
        this.coreTickNanos = status.coreTickNanos();
        this.exchangeDepotTickNanos = status.exchangeDepotTickNanos();
    }

    private static <T> T enumValue(T[] values, int networkId, String role) {
        if (networkId < 0 || networkId >= values.length) {
            throw new IllegalStateException("Unknown Trinity information exchange " + role + " " + networkId);
        }
        return values[networkId];
    }
}
