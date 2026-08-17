package com.fish_dan_.data_energistics.menu.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorAutoExportMode;
import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.item.carrier.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.item.carrier.CropDataCarrierData;
import com.fish_dan_.data_energistics.item.carrier.OreDataCarrierData;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.util.IConfigManager;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.RestrictedInputSlot;

import java.util.ArrayList;
import java.util.List;

public class DataExtractorMenu extends UpgradeableMenu<DataExtractorBlockEntity> implements IProgressProvider {

    private static final String ACTION_SET_REDSTONE_CONTROL = "set_redstone_control";
    private static final String ACTION_SET_RANGE_VISIBLE = "set_range_visible";
    private static final String ACTION_SET_AUTO_EXPORT = "set_auto_export";
    private static final String ACTION_SET_OUTPUT_SIDE = "set_output_side";
    public static final SlotSemantic SWORD_INPUT = SlotSemantics.register("DATA_EXTRACTOR_SWORD", false);
    public static final SlotSemantic ORE_INPUT = SlotSemantics.register("DATA_EXTRACTOR_ORE", false);
    public static final SlotSemantic CROP_INPUT = SlotSemantics.register("DATA_EXTRACTOR_CROP", false);
    public static final SlotSemantic DISPLAY_COMPONENT_INPUT = SlotSemantics.register("DATA_EXTRACTOR_DISPLAY_COMPONENT", false);

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
    public int workIntervalSeconds = DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.workIntervalSeconds;
    @GuiSync(766)
    public int targetCount;
    @GuiSync(767)
    public int targetLimit = DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.baseTargetLimit;
    @GuiSync(768)
    public boolean redstoneControlled;
    @GuiSync(769)
    public boolean rangeVisible;
    @GuiSync(770)
    public int autoExportModeOrdinal;
    @GuiSync(771)
    public int outputSidesMask = 63;
    @GuiSync(772)
    public boolean carrierInteractionAllowed = true;

    public DataExtractorMenu(int id, Inventory playerInventory, DataExtractorBlockEntity host) {
        super(DEMenus.DATA_EXTRACTOR.get(), id, playerInventory, host);
        registerClientAction(ACTION_SET_REDSTONE_CONTROL, Boolean.class, this::setRedstoneControlled);
        registerClientAction(ACTION_SET_RANGE_VISIBLE, Boolean.class, this::setRangeVisible);
        registerClientAction(ACTION_SET_AUTO_EXPORT, Integer.class, this::setAutoExportMode);
        registerClientAction(ACTION_SET_OUTPUT_SIDE, String.class, this::setOutputSide);
    }

    @Override
    protected void setupInventorySlots() {
        var storage = this.getHost().getStorageInventory();
        this.addSlot(new DataCarrierInputSlot(storage, 0), SlotSemantics.MACHINE_INPUT);
        this.addSlot(new SwordInputSlot(storage, 1), SWORD_INPUT);
        this.addSlot(new OreInputSlot(storage, 2), ORE_INPUT);
        this.addSlot(new CropInputSlot(storage, 3), CROP_INPUT);
        this.addSlot(new DisplayComponentInputSlot(storage, 4), DISPLAY_COMPONENT_INPUT);
    }

    @Override
    public void onSlotChange(Slot slot) {
        super.onSlotChange(slot);
        if (this.isClientSide()) {
            this.workIntervalSeconds = DataExtractorBlockEntity.computeWorkIntervalSeconds(
                    this.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
            this.targetLimit = DataExtractorBlockEntity.computeTargetLimit(this.getUpgrades());
            this.damagePerCycle = DataExtractorBlockEntity.computeDamagePerCycle(
                    this.getHost().getStorageInventory().getStackInSlot(1),
                    this.getHost().getLevel() != null ? this.getHost().getLevel().registryAccess() : null);
            this.dataFlowPerCycle = DataExtractorBlockEntity.computeDataFlowPerCycle(
                    this.getUpgrades(),
                    this.damagePerCycle,
                    this.targetCount);
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
            this.autoExportModeOrdinal = host.getAutoExportMode().ordinal();
            this.outputSidesMask = encodeOutputSides(host.getOutputSides());
            this.carrierInteractionAllowed = host.isCarrierInteractionAllowed();
            ItemStack carrier = host.getStorageInventory().getStackInSlot(0);
            if (BiologyDataCarrierData.hasRecordedEntity(carrier)) {
                this.collectionProgress = Math.round(BiologyDataCarrierData.getCollectedDamage(carrier) * 10.0F);
                this.collectionMaxProgress = Math.round(BiologyDataCarrierData.getRequiredDamage(carrier) * 10.0F);
            } else if (OreDataCarrierData.hasRecordedOre(carrier)) {
                this.collectionProgress = Math.round(OreDataCarrierData.getCollectedAmount(carrier) * 10.0F);
                this.collectionMaxProgress = Math.round(OreDataCarrierData.getRequiredAmount(carrier) * 10.0F);
            } else if (CropDataCarrierData.hasRecordedCrop(carrier)) {
                this.collectionProgress = Math.round(CropDataCarrierData.getCollectedAmount(carrier) * 10.0F);
                this.collectionMaxProgress = Math.round(CropDataCarrierData.getRequiredAmount(carrier) * 10.0F);
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

    public void sendSetAutoExportMode(DataExtractorAutoExportMode mode) {
        sendClientAction(ACTION_SET_AUTO_EXPORT, mode.ordinal());
    }

    public DataExtractorAutoExportMode getAutoExportMode() {
        return DataExtractorAutoExportMode.fromOrdinal(this.autoExportModeOrdinal);
    }

    public List<Direction> getOutputSides() {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if ((this.outputSidesMask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    public void sendSetOutputSide(Direction side, boolean enabled) {
        sendClientAction(ACTION_SET_OUTPUT_SIDE, side.getName() + ":" + enabled);
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

    private void setAutoExportMode(Integer ordinal) {
        if (ordinal == null || this.getHost() == null) {
            return;
        }

        this.autoExportModeOrdinal = this.getHost()
                .setAutoExportMode(DataExtractorAutoExportMode.fromOrdinal(ordinal))
                .ordinal();
        broadcastChanges();
    }

    private void setOutputSide(String payload) {
        if (payload == null || this.getHost() == null) {
            return;
        }

        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return;
        }

        Direction side = Direction.byName(payload.substring(0, separator));
        if (side == null) {
            return;
        }

        boolean enabled = Boolean.parseBoolean(payload.substring(separator + 1));
        this.getHost().setOutputSideEnabled(side, enabled);
        this.outputSidesMask = encodeOutputSides(this.getHost().getOutputSides());
        broadcastChanges();
    }

    private int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private final class DataCarrierInputSlot extends RestrictedInputSlot {

        private DataCarrierInputSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(1);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isCarrierInteractionAllowed() && stack.is(DEItems.DATA_CARRIER.get()) && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isCarrierInteractionAllowed() && super.mayPickup(player);
        }

        private boolean isCarrierInteractionAllowed() {
            return DataExtractorMenu.this.isClientSide() ? DataExtractorMenu.this.carrierInteractionAllowed : DataExtractorMenu.this.getHost().isCarrierInteractionAllowed();
        }
    }

    private static final class SwordInputSlot extends RestrictedInputSlot {

        private SwordInputSlot(InternalInventory inv, int invSlot) {
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

        private OreInputSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(64);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DataExtractorBlockEntity.isOreOrRawOre(stack) && super.mayPlace(stack);
        }
    }

    private static final class CropInputSlot extends RestrictedInputSlot {

        private CropInputSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(64);
            this.setIcon(null);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DataExtractorBlockEntity.isSupportedCrop(stack) && super.mayPlace(stack);
        }
    }

    private static final class DisplayComponentInputSlot extends RestrictedInputSlot {

        private DisplayComponentInputSlot(InternalInventory inv, int invSlot) {
            super(PlacableItemType.INSCRIBER_INPUT, inv, invSlot);
            this.setStackLimit(1);
            this.setIcon(Icon.BACKGROUND_VIEW_CELL);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DataExtractorBlockEntity.isDisplayComponentItem(stack) && super.mayPlace(stack);
        }
    }
}
