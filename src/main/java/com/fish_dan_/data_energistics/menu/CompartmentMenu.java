package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageDisplayInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.client.Point;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.menu.slot.IOptionalSlot;
import appeng.menu.slot.IOptionalSlotHost;
import appeng.menu.slot.OptionalFakeSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.RestrictedInputSlot.PlacableItemType;
import appeng.util.ConfigMenuInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base menu for compartment parts.
 */
public class CompartmentMenu extends AEBaseMenu implements IOptionalSlotHost {

    public static final int COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT = CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ITEM_COLUMNS;
    public static final int COMPOSITE_WAREHOUSE_ROW_COUNT = CompositeWarehouseBlockEntity.COMPOSITE_WAREHOUSE_ROWS;
    public static final int COMPOSITE_WAREHOUSE_SLOT_COUNT = COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT *
            COMPOSITE_WAREHOUSE_ROW_COUNT;
    public static final int ME_COMPOSITE_INPUT_GROUP_COUNT = 27;
    public static final int ME_COMPOSITE_OUTPUT_DISPLAY_SLOT_COUNT = 36;
    public static final int PATTERN_BUFFER_DISPLAY_SLOT_COUNT = 24;
    public static final int SHARED_CATALYST_SLOT_COUNT = 9;
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_1 = SlotSemantics.register("COMPARTMENT_STORAGE_ROW_1", false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_2 = SlotSemantics.register("COMPARTMENT_STORAGE_ROW_2", false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_3 = SlotSemantics.register(
            "COMPARTMENT_STORAGE_ROW_3",
            false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_4 = SlotSemantics.register(
            "COMPARTMENT_STORAGE_ROW_4",
            false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_5 = SlotSemantics.register(
            "COMPARTMENT_STORAGE_ROW_5",
            false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_6 = SlotSemantics.register(
            "COMPARTMENT_STORAGE_ROW_6",
            false);
    public static final SlotSemantic COMPARTMENT_STORAGE_ROW_7 = SlotSemantics.register(
            "COMPARTMENT_STORAGE_ROW_7",
            false);
    public static final SlotSemantic COMPARTMENT_CONFIG = SlotSemantics.register("COMPARTMENT_CONFIG", false);
    public static final SlotSemantic COMPARTMENT_CONFIG_ROW_1 = SlotSemantics.register(
            "COMPARTMENT_CONFIG_ROW_1",
            false);
    public static final SlotSemantic COMPARTMENT_CONFIG_ROW_2 = SlotSemantics.register(
            "COMPARTMENT_CONFIG_ROW_2",
            false);
    public static final SlotSemantic COMPARTMENT_CONFIG_ROW_3 = SlotSemantics.register(
            "COMPARTMENT_CONFIG_ROW_3",
            false);
    public static final SlotSemantic COMPARTMENT_BUFFER = SlotSemantics.register("COMPARTMENT_BUFFER", false);
    public static final SlotSemantic COMPARTMENT_BUFFER_ROW_1 = SlotSemantics.register(
            "COMPARTMENT_BUFFER_ROW_1",
            false);
    public static final SlotSemantic COMPARTMENT_BUFFER_ROW_2 = SlotSemantics.register(
            "COMPARTMENT_BUFFER_ROW_2",
            false);
    public static final SlotSemantic COMPARTMENT_BUFFER_ROW_3 = SlotSemantics.register(
            "COMPARTMENT_BUFFER_ROW_3",
            false);
    public static final SlotSemantic COMPARTMENT_FLUID = SlotSemantics.register("COMPARTMENT_FLUID", false);
    public static final SlotSemantic COMPARTMENT_EXTRA_FLUID = SlotSemantics.register(
            "COMPARTMENT_EXTRA_FLUID",
            false);
    public static final SlotSemantic COMPARTMENT_FLUID_ROW_2 = SlotSemantics.register(
            "COMPARTMENT_FLUID_ROW_2",
            false);
    public static final SlotSemantic COMPARTMENT_FLUID_ROW_3 = SlotSemantics.register(
            "COMPARTMENT_FLUID_ROW_3",
            false);
    public static final SlotSemantic COMPARTMENT_FLUID_ROW_4 = SlotSemantics.register(
            "COMPARTMENT_FLUID_ROW_4",
            false);
    public static final SlotSemantic COMPARTMENT_KEY = SlotSemantics.register("COMPARTMENT_KEY", false);
    public static final SlotSemantic COMPARTMENT_KEY_ROW_2 = SlotSemantics.register(
            "COMPARTMENT_KEY_ROW_2",
            false);
    public static final SlotSemantic COMPARTMENT_KEY_ROW_3 = SlotSemantics.register(
            "COMPARTMENT_KEY_ROW_3",
            false);
    public static final SlotSemantic COMPARTMENT_PATTERN = SlotSemantics.register("COMPARTMENT_PATTERN", false);
    public static final SlotSemantic COMPARTMENT_PATTERN_BUFFER = SlotSemantics.register(
            "COMPARTMENT_PATTERN_BUFFER",
            false);
    public static final SlotSemantic COMPARTMENT_CATALYST = SlotSemantics.register("COMPARTMENT_CATALYST", false);

    @GuiSync(900)
    public int unlockedSlotCount;
    @GuiSync(901)
    public int unlockedRowCount;

    private final CompartmentBlockEntity host;
    private final List<CapacityGatedSlot> capacityGatedSlots = new ArrayList<>();

    public CompartmentMenu(MenuType<?> menuType, int id, Inventory playerInventory, CompartmentBlockEntity host) {
        super(menuType, id, playerInventory, host);
        this.host = host;
        if (host != null) {
            if (host instanceof CompositeWarehouseBlockEntity compositeWarehouse) {
                setupCompositeWarehouseUpgrades(compositeWarehouse);
                this.unlockedRowCount = compositeWarehouse.unlockedRowCount();
            }
            setupCompartmentSlots(host);
            this.unlockedSlotCount = host.unlockedSlotCount();
            updateCapacityGatedSlots();
        }
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && getCompartmentHost() != null) {
            CompartmentBlockEntity host = getCompartmentHost();
            this.unlockedSlotCount = host.unlockedSlotCount();
            if (host instanceof CompositeWarehouseBlockEntity compositeWarehouse) {
                this.unlockedRowCount = compositeWarehouse.unlockedRowCount();
            }
            updateCapacityGatedSlots();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        return this.unlockedRowCount > CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS + idx;
    }

    public CompartmentType getCompartmentType() {
        return this.host != null ? this.host.compartmentType() : CompartmentType.INPUT;
    }

    public CompartmentBlockEntity getHost() {
        return this.host;
    }

    public boolean supportsUpgrades() {
        return this.host instanceof CompositeWarehouseBlockEntity;
    }

    private CompartmentBlockEntity getCompartmentHost() {
        return this.host;
    }

    private void setupCompartmentSlots(CompartmentBlockEntity host) {
        switch (host.compartmentType()) {
            case INPUT, OUTPUT -> {
                if (host instanceof CompositeWarehouseBlockEntity compositeWarehouse) {
                    setupCompositeWarehouseSlots(compositeWarehouse);
                } else {
                    throw unexpectedHost(host, "CompositeWarehouseBlockEntity");
                }
            }
            case ME_INPUT -> {
                if (host instanceof MeCompositeInputWarehouseBlockEntity meInput) {
                    setupMeInputSlots(meInput);
                } else {
                    throw unexpectedHost(host, "MeCompositeInputWarehouseBlockEntity");
                }
            }
            case ME_OUTPUT -> {
                if (host instanceof MeCompositeOutputWarehouseBlockEntity meOutput) {
                    setupMeOutputSlots(meOutput);
                } else {
                    throw unexpectedHost(host, "MeCompositeOutputWarehouseBlockEntity");
                }
            }
            case PATTERN_BUFFER -> {
                if (host instanceof MePatternBufferBlockEntity patternBuffer) {
                    setupPatternBufferSlots(patternBuffer);
                } else {
                    throw unexpectedHost(host, "MePatternBufferBlockEntity");
                }
            }
        }
    }

    private void setupCompositeWarehouseSlots(CompositeWarehouseBlockEntity host) {
        ConfigMenuInventory storage = host.slotStorage().createMenuWrapper();
        for (int row = 0; row < COMPOSITE_WAREHOUSE_ROW_COUNT; row++) {
            int optionalGroup = row - CompositeWarehouseBlockEntity.BASE_COMPOSITE_WAREHOUSE_ROWS;
            for (int column = 0; column < COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT; column++) {
                int slot = row * COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT + column;
                AppEngSlot storageSlot = optionalGroup < 0 ? new AppEngSlot(storage, slot) : new OptionalCompartmentSlot(storage, slot, optionalGroup);
                addSlot(storageSlot, storageRowSemantic(row));
            }
        }
        addCompositeWarehouseFluidAndKeySlots(host);
    }

    private void addCompositeWarehouseFluidAndKeySlots(CompositeWarehouseBlockEntity host) {
        ConfigMenuInventory fluids = host.fluidConfig().createMenuWrapper();
        ConfigMenuInventory keys = host.keyConfig().createMenuWrapper();
        addSlot(new FakeSlot(fluids, 0), COMPARTMENT_FLUID);
        addSlot(new FakeSlot(keys, 0), COMPARTMENT_KEY);
        addSlot(
                new TexturedOptionalFakeSlot(fluids, this, 1, 0, 0),
                COMPARTMENT_FLUID_ROW_2);
        addSlot(
                new TexturedOptionalFakeSlot(keys, this, 1, 1, 1),
                COMPARTMENT_KEY_ROW_2);
        addSlot(
                new TexturedOptionalFakeSlot(fluids, this, 2, 2, 0),
                COMPARTMENT_FLUID_ROW_3);
        addSlot(
                new TexturedOptionalFakeSlot(keys, this, 2, 3, 1),
                COMPARTMENT_KEY_ROW_3);
        addSlot(
                new TexturedOptionalFakeSlot(fluids, this, 3, 4, 0),
                COMPARTMENT_FLUID_ROW_4);
    }

    private void setupCompositeWarehouseUpgrades(CompositeWarehouseBlockEntity host) {
        IUpgradeInventory upgrades = host.getUpgrades();
        InternalInventory protectedUpgrades = new CapacityProtectedUpgradeInventory(upgrades, host);
        for (int slot = 0; slot < upgrades.size(); slot++) {
            addSlot(new CapacityProtectedUpgradeSlot(protectedUpgrades, slot, host), SlotSemantics.UPGRADE);
        }
    }

    private void setupMeInputSlots(MeCompositeInputWarehouseBlockEntity host) {
        ConfigMenuInventory marker = host.markerInventory().createMenuWrapper();
        ConfigMenuInventory buffer = host.meInputBuffer().createMenuWrapper();
        for (int slot = 0; slot < ME_COMPOSITE_INPUT_GROUP_COUNT; slot++) {
            int row = slot / 9;
            addCapacityGatedSlot(new FakeSlot(marker, slot), configRowSemantic(row), slot);
            AppEngSlot bufferSlot = new AppEngSlot(buffer, slot);
            bufferSlot.setNotDraggable();
            addCapacityGatedSlot(bufferSlot, bufferRowSemantic(row), slot);
        }
    }

    private void setupMeOutputSlots(MeCompositeOutputWarehouseBlockEntity host) {
        CompartmentStorageDisplayInventory display = new CompartmentStorageDisplayInventory(
                host::storage,
                ME_COMPOSITE_OUTPUT_DISPLAY_SLOT_COUNT);
        for (int slot = 0; slot < ME_COMPOSITE_OUTPUT_DISPLAY_SLOT_COUNT; slot++) {
            AppEngSlot displaySlot = new AppEngSlot(display, slot);
            displaySlot.setNotDraggable();
            addSlot(displaySlot, COMPARTMENT_BUFFER);
        }
    }

    private void setupPatternBufferSlots(MePatternBufferBlockEntity host) {
        ConfigMenuInventory patterns = host.patternStorage().createMenuWrapper();
        for (int slot = 0; slot < host.patternStorage().size(); slot++) {
            addCapacityGatedSlot(new AppEngSlot(patterns, slot), COMPARTMENT_PATTERN, slot);
        }
        CompartmentStorageDisplayInventory patternBufferDisplay = new CompartmentStorageDisplayInventory(
                host::patternAggregateStorage,
                PATTERN_BUFFER_DISPLAY_SLOT_COUNT);
        for (int slot = 0; slot < PATTERN_BUFFER_DISPLAY_SLOT_COUNT; slot++) {
            AppEngSlot displaySlot = new AppEngSlot(patternBufferDisplay, slot);
            displaySlot.setNotDraggable();
            addSlot(displaySlot, COMPARTMENT_PATTERN_BUFFER);
        }
        ConfigMenuInventory catalysts = host.sharedCatalystStorage().createMenuWrapper();
        for (int slot = 0; slot < SHARED_CATALYST_SLOT_COUNT; slot++) {
            addSlot(new AppEngSlot(catalysts, slot), COMPARTMENT_CATALYST);
        }
        ConfigMenuInventory fluids = host.fluidConfig().createMenuWrapper();
        addSlot(new FakeSlot(fluids, 0), COMPARTMENT_FLUID);
        addSlot(new FakeSlot(host.keyConfig().createMenuWrapper(), 0), COMPARTMENT_KEY);
        addSlot(new FakeSlot(fluids, 1), COMPARTMENT_EXTRA_FLUID);
    }

    private void addCapacityGatedSlot(AppEngSlot slot, SlotSemantic semantic) {
        addCapacityGatedSlot(slot, semantic, this.capacityGatedSlots.size());
    }

    private void addCapacityGatedSlot(AppEngSlot slot, SlotSemantic semantic, int backingSlot) {
        this.capacityGatedSlots.add(new CapacityGatedSlot(slot, backingSlot));
        addSlot(slot, semantic);
    }

    private void updateCapacityGatedSlots() {
        for (CapacityGatedSlot gatedSlot : this.capacityGatedSlots) {
            boolean unlocked = gatedSlot.backingSlot() < this.unlockedSlotCount;
            gatedSlot.slot().setActive(unlocked);
            gatedSlot.slot().setSlotEnabled(unlocked);
        }
    }

    private static SlotSemantic storageRowSemantic(int row) {
        return switch (row) {
            case 0 -> COMPARTMENT_STORAGE_ROW_1;
            case 1 -> COMPARTMENT_STORAGE_ROW_2;
            case 2 -> COMPARTMENT_STORAGE_ROW_3;
            case 3 -> COMPARTMENT_STORAGE_ROW_4;
            case 4 -> COMPARTMENT_STORAGE_ROW_5;
            case 5 -> COMPARTMENT_STORAGE_ROW_6;
            case 6 -> COMPARTMENT_STORAGE_ROW_7;
            default -> throw new IllegalArgumentException("Composite warehouse storage row out of range: " + row);
        };
    }

    private static SlotSemantic configRowSemantic(int row) {
        return switch (row) {
            case 0 -> COMPARTMENT_CONFIG_ROW_1;
            case 1 -> COMPARTMENT_CONFIG_ROW_2;
            case 2 -> COMPARTMENT_CONFIG_ROW_3;
            default -> throw new IllegalArgumentException("ME input config row out of range: " + row);
        };
    }

    private static SlotSemantic bufferRowSemantic(int row) {
        return switch (row) {
            case 0 -> COMPARTMENT_BUFFER_ROW_1;
            case 1 -> COMPARTMENT_BUFFER_ROW_2;
            case 2 -> COMPARTMENT_BUFFER_ROW_3;
            default -> throw new IllegalArgumentException("ME input buffer row out of range: " + row);
        };
    }

    private static IllegalStateException unexpectedHost(CompartmentBlockEntity host, String expectedHost) {
        return new IllegalStateException("Compartment menu expected " + expectedHost +
                " for " + host.compartmentType());
    }

    private record CapacityGatedSlot(AppEngSlot slot, int backingSlot) {}

    private static final class TexturedOptionalFakeSlot extends OptionalFakeSlot implements CompartmentSlotLabel {

        private final int textureRow;

        private TexturedOptionalFakeSlot(InternalInventory inv,
                                         IOptionalSlotHost host,
                                         int invSlot,
                                         int group,
                                         int textureRow) {
            super(inv, host, invSlot, group);
            this.textureRow = textureRow;
        }

        @Override
        public int slotTextureRow() {
            return this.textureRow;
        }
    }

    private final class OptionalCompartmentSlot extends AppEngSlot implements IOptionalSlot {

        private final int group;

        private OptionalCompartmentSlot(InternalInventory inv, int invSlot, int group) {
            super(inv, invSlot);
            this.group = group;
        }

        @Override
        public boolean isSlotEnabled() {
            return CompartmentMenu.this.isSlotEnabled(this.group);
        }

        @Override
        public boolean isRenderDisabled() {
            return true;
        }

        @Override
        public Point getBackgroundPos() {
            return new Point(this.x - 1, this.y - 1);
        }
    }

    private static final class CapacityProtectedUpgradeSlot extends RestrictedInputSlot {

        private final int invSlot;
        private final CompositeWarehouseBlockEntity host;

        private CapacityProtectedUpgradeSlot(InternalInventory inv, int invSlot, CompositeWarehouseBlockEntity host) {
            super(PlacableItemType.UPGRADES, inv, invSlot);
            this.invSlot = invSlot;
            this.host = host;
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.host.canRemoveCapacityCard(this.invSlot) && super.mayPickup(player);
        }
    }

    private static final class CapacityProtectedUpgradeInventory implements InternalInventory {

        private final IUpgradeInventory delegate;
        private final CompositeWarehouseBlockEntity host;

        private CapacityProtectedUpgradeInventory(IUpgradeInventory delegate, CompositeWarehouseBlockEntity host) {
            this.delegate = delegate;
            this.host = host;
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return this.delegate.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            this.delegate.setItemDirect(slotIndex, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return this.delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!this.host.canRemoveCapacityCard(slot)) {
                return ItemStack.EMPTY;
            }
            return this.delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public void sendChangeNotification(int slot) {
            this.delegate.sendChangeNotification(slot);
        }
    }
}
