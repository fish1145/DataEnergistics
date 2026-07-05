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
    private static final int UPGRADE_SLOT_COUNT = 4;
    private static final int BASE_COMPOSITE_WAREHOUSE_SLOTS = 16;
    private static final int COMPOSITE_WAREHOUSE_SLOTS_PER_CAPACITY_CARD = 8;
    private static final int COMPOSITE_WAREHOUSE_CONFIGURABLE_SLOTS = 40;

    private final CompartmentInventory slotStorage = CompartmentInventory.storage(
            MAX_COMPARTMENT_SLOTS,
            this::onContentInventoryChanged,
            this::unlockedSlotCount);
    private final CompartmentInventory fluidConfig = CompartmentInventory.fluidConfig(this::onContentInventoryChanged, 2);
    private final CompartmentInventory keyConfig = CompartmentInventory.keyConfig(this::onContentInventoryChanged);
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

    @Override
    public int unlockedSlotCount() {
        return Math.min(configurableSlotLimit(), capacityUnlockedSlotCount());
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
        copyInventoryToStorage(this.fluidConfig, 1);
        copyInventoryToStorage(this.keyConfig);
    }

    private int capacityUnlockedSlotCount() {
        return BASE_COMPOSITE_WAREHOUSE_SLOTS +
                installedCapacityCards() * COMPOSITE_WAREHOUSE_SLOTS_PER_CAPACITY_CARD;
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
