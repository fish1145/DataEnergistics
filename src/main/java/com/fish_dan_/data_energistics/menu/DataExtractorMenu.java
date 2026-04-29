package com.fish_dan_.data_energistics.menu;

import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class DataExtractorMenu extends UpgradeableMenu<DataExtractorBlockEntity> implements IProgressProvider {
    @GuiSync(760)
    public boolean online;
    @GuiSync(761)
    public int dataFlowPerCycle;
    @GuiSync(762)
    public int damagePerCycle;
    @GuiSync(763)
    public int collectionProgress;
    @GuiSync(764)
    public int collectionMaxProgress;

    public DataExtractorMenu(int id, Inventory playerInventory, DataExtractorBlockEntity host) {
        super(ModMenus.DATA_EXTRACTOR.get(), id, playerInventory, host);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        this.addSlot(new DataCarrierInputSlot(storage, 0), SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(storage, 1, null), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            DataExtractorBlockEntity host = this.getHost();
            this.online = host.isOnline();
            this.dataFlowPerCycle = DataExtractorBlockEntity.DATA_FLOW_PER_CYCLE;
            this.damagePerCycle = DataExtractorBlockEntity.DAMAGE_PER_CYCLE;
            ItemStack carrier = host.getStorageInventory().getStackInSlot(0);
            this.collectionProgress = Math.round(BiologyDataCarrierData.getCollectedDamage(carrier) * 10.0F);
            this.collectionMaxProgress = Math.round(BiologyDataCarrierData.getRequiredDamage(carrier) * 10.0F);
        }

        super.broadcastChanges();
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        // This menu only exposes upgrade slots.
    }

    @Override
    public int getCurrentProgress() {
        return this.collectionProgress;
    }

    @Override
    public int getMaxProgress() {
        return this.collectionMaxProgress;
    }

    private static final class DataCarrierInputSlot extends RestrictedInputSlot {
        private DataCarrierInputSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(1);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.DATA_CARRIER.get()) && super.mayPlace(stack);
        }
    }
}
