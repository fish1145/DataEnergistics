package com.fish_dan_.data_energistics.menu;

import appeng.api.util.IConfigManager;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;

public class DataExtractorMenu extends UpgradeableMenu<DataExtractorBlockEntity> {
    @GuiSync(760)
    public boolean online;
    @GuiSync(761)
    public int speedCardCount;
    @GuiSync(762)
    public int capacityCardCount;
    @GuiSync(763)
    public int dataFlowPerTick;
    @GuiSync(764)
    public int damagePerTick;

    public DataExtractorMenu(int id, Inventory playerInventory, DataExtractorBlockEntity host) {
        super(ModMenus.DATA_EXTRACTOR.get(), id, playerInventory, host);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            DataExtractorBlockEntity host = this.getHost();
            this.online = host.isOnline();
            this.speedCardCount = host.getSpeedCardCount();
            this.capacityCardCount = host.getCapacityCardCount();
            this.dataFlowPerTick = DataExtractorBlockEntity.DATA_FLOW_PER_TICK;
            this.damagePerTick = DataExtractorBlockEntity.DAMAGE_PER_TICK;
        }

        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes upgrade slots.
    }
}
