package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
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

    private final TrinityInformationExchangeDepotMenuHost host;

    @GuiSync(801)
    public int modeId;

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
    }
}
