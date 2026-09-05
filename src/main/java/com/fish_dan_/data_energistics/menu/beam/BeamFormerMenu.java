package com.fish_dan_.data_energistics.menu.beam;

import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.api.util.IConfigManager;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;

import net.minecraft.world.entity.player.Inventory;

/** The same AE2 upgrade slots and authoritative status view for block and part endpoints. */
public final class BeamFormerMenu extends UpgradeableMenu<BeamEndpoint> {

    @GuiSync(790)
    public boolean online;
    @GuiSync(791)
    public int range;
    @GuiSync(792)
    public int cards;
    @GuiSync(793)
    public int power;
    @GuiSync(794)
    public int connections;
    @GuiSync(795)
    public int bindings;
    @GuiSync(796)
    public boolean omni;
    @GuiSync(797)
    public boolean hidden;
    @GuiSync(798)
    public boolean faulted;

    public BeamFormerMenu(int id, Inventory inventory, BeamEndpoint host) {
        super(DEMenus.BEAM_FORMER.get(), id, inventory, host);
    }

    @Override
    protected void setupInventorySlots() {
        // Only AE2's inherited upgrade inventory is exposed; beam devices have no material inventory.
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager manager) {
        // Visibility is an endpoint interaction, not an AE2 bus setting.
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            var state = getHost().beamState();
            this.online = getHost().beamNode().isOnline();
            this.range = state.range();
            this.cards = state.cards();
            this.power = state.power();
            this.connections = state.connectionCount();
            this.bindings = state.bindingCount();
            this.omni = state.kind() == BeamDeviceKind.OMNI;
            this.hidden = state.hidden();
            this.faulted = state.faulted();
        }
        super.broadcastChanges();
    }
}
