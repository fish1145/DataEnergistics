package com.fish_dan_.data_energistics.menu;

import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.OutputSlot;
import appeng.menu.slot.RestrictedInputSlot;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataExtractorDropRoutingMode;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.util.OreDataCarrierData;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class DataExtractorMenu extends UpgradeableMenu<DataExtractorBlockEntity> implements IProgressProvider {
    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";
    private static final String ACTION_SET_RANGE_VISIBLE = "set_range_visible";
    private static final String ACTION_SET_DROP_ROUTING_MODE = "set_drop_routing_mode";
    public static final SlotSemantic SWORD_INPUT = SlotSemantics.register("DATA_EXTRACTOR_SWORD", false);
    public static final SlotSemantic ORE_INPUT = SlotSemantics.register("DATA_EXTRACTOR_ORE", false);

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
    @GuiSync(765)
    public int workIntervalSeconds = DataExtractorBlockEntity.BASE_WORK_INTERVAL_SECONDS;
    @GuiSync(766)
    public int targetCount;
    @GuiSync(767)
    public int targetLimit = DataExtractorBlockEntity.BASE_TARGET_LIMIT;
    @GuiSync(768)
    public boolean redstoneControlled;
    @GuiSync(769)
    public boolean rangeVisible;
    @GuiSync(770)
    public int dropRoutingModeOrdinal;

    public DataExtractorMenu(int id, Inventory playerInventory, DataExtractorBlockEntity host) {
        super(ModMenus.DATA_EXTRACTOR.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
        registerClientAction(ACTION_SET_RANGE_VISIBLE, Boolean.class, this::setRangeVisible);
        registerClientAction(ACTION_SET_DROP_ROUTING_MODE, Integer.class, this::setDropRoutingMode);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        this.addSlot(new DataCarrierInputSlot(storage, 0), SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(storage, 1, null), SlotSemantics.MACHINE_OUTPUT);
        this.addSlot(new SwordInputSlot(storage, 2), SWORD_INPUT);
        this.addSlot(new OreInputSlot(storage, 3), ORE_INPUT);
    }

    @Override
    public void onSlotChange(net.minecraft.world.inventory.Slot slot) {
        super.onSlotChange(slot);
        if (this.isClientSide()) {
            this.workIntervalSeconds = DataExtractorBlockEntity.computeWorkIntervalSeconds(
                    this.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
            this.targetLimit = DataExtractorBlockEntity.computeTargetLimit(this.getUpgrades());
            this.dataFlowPerCycle = DataExtractorBlockEntity.computeDataFlowPerCycle(this.getUpgrades(), this.targetCount);
            this.damagePerCycle = DataExtractorBlockEntity.computeDamagePerCycle(
                    this.getHost().getStorageInventory().getStackInSlot(2),
                    this.getHost().getLevel() != null ? this.getHost().getLevel().registryAccess() : null
            );
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            DataExtractorBlockEntity host = this.getHost();
            this.online = host.isOnline();
            this.targetCount = host.getTargetCount();
            this.targetLimit = host.getTargetLimit();
            this.dataFlowPerCycle = host.getDataFlowPerCycle(this.targetCount);
            this.damagePerCycle = host.getDamagePerCycle();
            this.workIntervalSeconds = host.getWorkIntervalSeconds();
            this.redstoneControlled = host.isRedstoneControlled();
            this.rangeVisible = host.isRangeDisplayEnabled();
            this.dropRoutingModeOrdinal = host.getDropRoutingMode().ordinal();
            ItemStack carrier = host.getStorageInventory().getStackInSlot(0);
            if (BiologyDataCarrierData.hasRecordedEntity(carrier)) {
                this.collectionProgress = Math.round(BiologyDataCarrierData.getCollectedDamage(carrier) * 10.0F);
                this.collectionMaxProgress = Math.round(BiologyDataCarrierData.getRequiredDamage(carrier) * 10.0F);
            } else if (OreDataCarrierData.hasRecordedOre(carrier)) {
                this.collectionProgress = Math.round(OreDataCarrierData.getCollectedAmount(carrier) * 10.0F);
                this.collectionMaxProgress = Math.round(OreDataCarrierData.getRequiredAmount(carrier) * 10.0F);
            } else {
                this.collectionProgress = 0;
                this.collectionMaxProgress = 0;
            }
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

    public void sendSetRedstoneControlled(boolean enabled) {
        sendClientAction(ACTION_SET_REDSTONE_CONTROL, enabled);
    }

    public void sendSetRangeVisible(boolean visible) {
        sendClientAction(ACTION_SET_RANGE_VISIBLE, visible);
    }

    public void sendSetDropRoutingMode(DataExtractorDropRoutingMode mode) {
        sendClientAction(ACTION_SET_DROP_ROUTING_MODE, mode.ordinal());
    }

    public DataExtractorDropRoutingMode getDropRoutingMode() {
        return DataExtractorDropRoutingMode.fromOrdinal(this.dropRoutingModeOrdinal);
    }

    private void setRedstoneControlled(Boolean enabled) {
        if (enabled == null || this.getHost() == null) {
            return;
        }

        this.redstoneControlled = this.getHost().setRedstoneControlled(enabled);
        broadcastChanges();
    }

    private void setRangeVisible(Boolean visible) {
        if (visible == null || this.getHost() == null) {
            return;
        }

        this.rangeVisible = this.getHost().setRangeDisplayEnabled(visible);
        broadcastChanges();
    }

    private void setDropRoutingMode(Integer ordinal) {
        if (ordinal == null || this.getHost() == null) {
            return;
        }

        this.dropRoutingModeOrdinal = this.getHost()
                .setDropRoutingMode(DataExtractorDropRoutingMode.fromOrdinal(ordinal))
                .ordinal();
        broadcastChanges();
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

    private static final class SwordInputSlot extends RestrictedInputSlot {
        private SwordInputSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(1);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.SWORDS) && super.mayPlace(stack);
        }
    }

    private static final class OreInputSlot extends RestrictedInputSlot {
        private OreInputSlot(appeng.api.inventories.InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(64);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DataExtractorBlockEntity.isOreOrRawOre(stack) && super.mayPlace(stack);
        }
    }
}
