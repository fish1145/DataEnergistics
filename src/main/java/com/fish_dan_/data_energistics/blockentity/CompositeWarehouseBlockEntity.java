package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent state for plain input and output compartment warehouses.
 */
public class CompositeWarehouseBlockEntity extends CompartmentBlockEntity implements IUpgradeableObject {

    private static final String SLOT_STORAGE_TAG = "slot_storage";
    private static final String FLUID_CONFIG_TAG = "fluid_config";
    private static final String KEY_CONFIG_TAG = "key_config";
    private static final String UPGRADES_TAG = "upgrades";
    private static final int UPGRADE_SLOT_COUNT = 5;
    public static final int BASE_COMPOSITE_WAREHOUSE_ROWS = 2;
    public static final int COMPOSITE_WAREHOUSE_COLUMNS = 9;
    public static final int COMPOSITE_WAREHOUSE_ITEM_COLUMNS = 7;
    public static final int COMPOSITE_WAREHOUSE_ROWS = 7;
    public static final int COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS = COMPOSITE_WAREHOUSE_COLUMNS *
            COMPOSITE_WAREHOUSE_ROWS;
    public static final int COMPOSITE_WAREHOUSE_ITEM_SLOTS = COMPOSITE_WAREHOUSE_ITEM_COLUMNS *
            COMPOSITE_WAREHOUSE_ROWS;
    public static final int COMPOSITE_WAREHOUSE_FLUID_CONFIG_SLOTS = COMPOSITE_WAREHOUSE_ROWS;
    public static final int COMPOSITE_WAREHOUSE_KEY_CONFIG_SLOTS = COMPOSITE_WAREHOUSE_ROWS;

    private final CompartmentInventory slotStorage = CompartmentInventory.storage(
            COMPOSITE_WAREHOUSE_ITEM_SLOTS,
            this::onContentInventoryChanged,
            this::unlockedMainSlotCount);
    private final CompartmentInventory fluidConfig = CompartmentInventory.fluidConfig(
            this::onContentInventoryChanged,
            COMPOSITE_WAREHOUSE_FLUID_CONFIG_SLOTS,
            this::unlockedFluidSlotCount);
    private final CompartmentInventory keyConfig = CompartmentInventory.keyConfig(
            this::onContentInventoryChanged,
            COMPOSITE_WAREHOUSE_KEY_CONFIG_SLOTS,
            this::unlockedKeySlotCount);
    private final IUpgradeInventory upgrades;

    public CompositeWarehouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPOSITE_WAREHOUSE_BLOCK_ENTITY.get(), pos, state);
        requirePlainWarehouse(state);
        this.upgrades = UpgradeInventories.forMachine(state.getBlock(), UPGRADE_SLOT_COUNT, this::onUpgradesChanged);
    }

    public CompartmentInventory slotStorage() {
        return this.slotStorage;
    }

    public CompartmentInventory fluidConfig() {
        return this.fluidConfig;
    }

    public CompartmentInventory keyConfig() {
        return this.keyConfig;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public boolean supportsUpgrades() {
        return true;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    public int installedCapacityCards() {
        return this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
    }

    public boolean canRemoveCapacityCard(int slot) {
        if (!isCapacityCard(slot)) {
            return true;
        }

        int reducedRows = capacityUnlockedRowCount(Math.max(0, installedCapacityCards() - 1));
        int reducedMainSlots = mainSlotsForRows(reducedRows);
        for (int storageSlot = reducedMainSlots; storageSlot < COMPOSITE_WAREHOUSE_ITEM_SLOTS; storageSlot++) {
            if (this.slotStorage.getKey(storageSlot) != null && this.slotStorage.getAmount(storageSlot) > 0L) {
                return false;
            }
        }
        if (hasConfiguredOverflow(this.fluidConfig, fluidSlotsForRows(reducedRows))) {
            return false;
        }
        if (hasConfiguredOverflow(this.keyConfig, keySlotsForRows(reducedRows))) {
            return false;
        }
        return true;
    }

    @Override
    public int unlockedSlotCount() {
        return unlockedRowCount() * COMPOSITE_WAREHOUSE_COLUMNS;
    }

    public int unlockedRowCount() {
        return capacityUnlockedRowCount(installedCapacityCards());
    }

    public int unlockedMainSlotCount() {
        return mainSlotsForRows(unlockedRowCount());
    }

    public int unlockedFluidSlotCount() {
        return fluidSlotsForRows(unlockedRowCount());
    }

    public int unlockedKeySlotCount() {
        return keySlotsForRows(unlockedRowCount());
    }

    @Override
    public int configurableSlotLimit() {
        return COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.slotStorage.writeToChildTag(tag, SLOT_STORAGE_TAG, registries);
        this.fluidConfig.writeToChildTag(tag, FLUID_CONFIG_TAG, registries);
        this.keyConfig.writeToChildTag(tag, KEY_CONFIG_TAG, registries);
        this.upgrades.writeToNBT(tag, UPGRADES_TAG, registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.slotStorage.readFromChildTag(tag, SLOT_STORAGE_TAG, registries);
        this.fluidConfig.readFromChildTag(tag, FLUID_CONFIG_TAG, registries);
        this.keyConfig.readFromChildTag(tag, KEY_CONFIG_TAG, registries);
        this.upgrades.readFromNBT(tag, UPGRADES_TAG, registries);
    }

    @Override
    protected void rebuildCanonicalStorage() {
        mutableStorage().clear();
        copyInventoryToStorage(this.slotStorage);
        copyInventoryToStorage(this.fluidConfig);
        copyInventoryToStorage(this.keyConfig);
    }

    private static int capacityUnlockedRowCount(int capacityCards) {
        return Math.min(COMPOSITE_WAREHOUSE_ROWS, BASE_COMPOSITE_WAREHOUSE_ROWS + Math.max(0, capacityCards));
    }

    private static int mainSlotsForRows(int rows) {
        return rows * COMPOSITE_WAREHOUSE_ITEM_COLUMNS;
    }

    private static int fluidSlotsForRows(int rows) {
        return rows;
    }

    private static int keySlotsForRows(int rows) {
        return rows;
    }

    private static boolean hasConfiguredOverflow(CompartmentInventory inventory, int unlockedSlots) {
        for (int slot = unlockedSlots; slot < inventory.size(); slot++) {
            if (inventory.getKey(slot) != null && inventory.getAmount(slot) > 0L) {
                return true;
            }
        }
        return false;
    }

    private boolean isCapacityCard(int slot) {
        if (slot < 0 || slot >= this.upgrades.size()) {
            return false;
        }
        return this.upgrades.getStackInSlot(slot).is(AEItems.CAPACITY_CARD.asItem());
    }

    private void onUpgradesChanged() {
        rebuildCanonicalStorage();
        setChanged();
    }

    private static void requirePlainWarehouse(BlockState state) {
        if (!(state.getBlock() instanceof CompartmentBlock compartmentBlock)) {
            throw new IllegalStateException("Composite warehouse block entity requires a compartment block");
        }
        CompartmentType type = compartmentBlock.compartmentType();
        if (type != CompartmentType.INPUT && type != CompartmentType.OUTPUT) {
            throw new IllegalStateException("Composite warehouse block entity cannot be attached to " + type);
        }
    }
}
