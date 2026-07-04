package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.blockentity.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageDisplayInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigMenuInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base menu for compartment parts.
 */
public class CompartmentMenu extends AEBaseMenu {

    public static final int COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT = 8;
    public static final int COMPOSITE_WAREHOUSE_ROW_COUNT = 5;
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
    public static final SlotSemantic COMPARTMENT_KEY = SlotSemantics.register("COMPARTMENT_KEY", false);
    public static final SlotSemantic COMPARTMENT_PATTERN = SlotSemantics.register("COMPARTMENT_PATTERN", false);
    public static final SlotSemantic COMPARTMENT_PATTERN_BUFFER = SlotSemantics.register(
            "COMPARTMENT_PATTERN_BUFFER",
            false);
    public static final SlotSemantic COMPARTMENT_CATALYST = SlotSemantics.register("COMPARTMENT_CATALYST", false);

    @GuiSync(900)
    public int unlockedSlotCount;

    private final CompartmentBlockEntity host;
    private final List<CapacityGatedSlot> capacityGatedSlots = new ArrayList<>();

    public CompartmentMenu(MenuType<?> menuType, int id, Inventory playerInventory, CompartmentBlockEntity host) {
        super(menuType, id, playerInventory, host);
        this.host = host;
        if (host != null) {
            if (host instanceof CompositeWarehouseBlockEntity compositeWarehouse) {
                setupUpgrades(compositeWarehouse.getUpgrades());
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
            updateCapacityGatedSlots();
        }
        super.broadcastChanges();
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
            case INPUT, OUTPUT -> setupCompositeWarehouseSlots(requireHost(host, CompositeWarehouseBlockEntity.class));
            case ME_INPUT -> setupMeInputSlots(requireHost(host, MeCompositeInputWarehouseBlockEntity.class));
            case ME_OUTPUT -> setupMeOutputSlots(requireHost(host, MeCompositeOutputWarehouseBlockEntity.class));
            case PATTERN_BUFFER -> setupPatternBufferSlots(requireHost(host, MePatternBufferBlockEntity.class));
        }
    }

    private void setupCompositeWarehouseSlots(CompositeWarehouseBlockEntity host) {
        ConfigMenuInventory storage = host.slotStorage().createMenuWrapper();
        for (int slot = 0; slot < COMPOSITE_WAREHOUSE_SLOT_COUNT; slot++) {
            int row = slot / COMPOSITE_WAREHOUSE_ROW_SLOT_COUNT;
            addCapacityGatedSlot(new AppEngSlot(storage, slot), storageRowSemantic(row), slot);
        }
        addSlot(new FakeSlot(host.fluidConfig().createMenuWrapper(), 0), COMPARTMENT_FLUID);
        addSlot(new FakeSlot(host.keyConfig().createMenuWrapper(), 0), COMPARTMENT_KEY);
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

    private static <T extends CompartmentBlockEntity> T requireHost(CompartmentBlockEntity host, Class<T> type) {
        if (!type.isInstance(host)) {
            throw new IllegalStateException("Compartment menu expected " + type.getSimpleName() +
                    " for " + host.compartmentType() + " but got " + host.getClass().getSimpleName());
        }
        return type.cast(host);
    }

    private record CapacityGatedSlot(AppEngSlot slot, int backingSlot) {}
}
