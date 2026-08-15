package com.fish_dan_.data_energistics.blockentity.storage;

import com.fish_dan_.data_energistics.common.compartment.AvailabilityCheckedCompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageGroup;
import com.fish_dan_.data_energistics.common.compartment.MapBackedCompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.PatternBufferCompartmentPart;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent state for pattern buffer compartments.
 */
public class MePatternBufferBlockEntity extends CompartmentBlockEntity implements PatternBufferCompartmentPart {

    private static final String FLUID_CONFIG_TAG = "fluid_config";
    private static final String KEY_CONFIG_TAG = "key_config";
    private static final String PATTERN_STORAGE_TAG = "pattern_storage";
    private static final String PATTERN_BUFFER_STORAGES_TAG = "pattern_buffer_storages";
    private static final String PATTERN_BUFFER_SLOT_TAG = "slot";
    private static final String PATTERN_BUFFER_STORAGE_TAG = "storage";
    private static final String SHARED_CATALYST_STORAGE_TAG = "shared_catalyst_storage";
    public static final int PATTERN_SLOT_COUNT = 54;
    private static final int SHARED_CATALYST_SLOTS = 9;

    private final CompartmentInventory patternStorage = CompartmentInventory.patternStorage(
            PATTERN_SLOT_COUNT,
            this::onContentInventoryChanged,
            this::unlockedSlotCount);
    private final CompartmentInventory sharedCatalystStorage = CompartmentInventory.storage(
            SHARED_CATALYST_SLOTS,
            this::onContentInventoryChanged,
            () -> SHARED_CATALYST_SLOTS);
    private final CompartmentInventory fluidConfig = CompartmentInventory.fluidConfig(this::onContentInventoryChanged, 2);
    private final CompartmentInventory keyConfig = CompartmentInventory.keyConfig(this::onContentInventoryChanged);
    private final ArrayList<CompartmentStorage> patternBufferStorages = new ArrayList<>(PATTERN_SLOT_COUNT);
    private final ArrayList<CompartmentStorage> patternBufferStorageViews = new ArrayList<>(PATTERN_SLOT_COUNT);
    private final CompartmentStorage patternAggregateStorageView = new AvailabilityCheckedCompartmentStorage(
            this::isCompartmentBound,
            () -> new CompartmentStorageGroup(this::unlockedPatternBufferStorageViews));

    public MePatternBufferBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.ME_PATTERN_BUFFER_BLOCK_ENTITY.get(), pos, state);
        for (int slot = 0; slot < PATTERN_SLOT_COUNT; slot++) {
            CompartmentStorage patternBuffer = new MapBackedCompartmentStorage(this::onContentInventoryChanged);
            this.patternBufferStorages.add(patternBuffer);
            this.patternBufferStorageViews.add(new AvailabilityCheckedCompartmentStorage(
                    this::isCompartmentBound,
                    () -> patternBuffer));
        }
    }

    @Override
    public CompartmentInventory patternStorage() {
        return this.patternStorage;
    }

    @Override
    public int configurableSlotLimit() {
        return PATTERN_SLOT_COUNT;
    }

    public CompartmentInventory sharedCatalystStorage() {
        return this.sharedCatalystStorage;
    }

    public CompartmentInventory fluidConfig() {
        return this.fluidConfig;
    }

    public CompartmentInventory keyConfig() {
        return this.keyConfig;
    }

    @Override
    public CompartmentStorage patternBufferStorage(int slot) {
        if (slot < 0 || slot >= this.patternBufferStorages.size()) {
            throw new IllegalArgumentException("Pattern buffer slot out of range: " + slot);
        }
        return this.patternBufferStorageViews.get(slot);
    }

    @Override
    public int patternBufferSlotCount() {
        return this.patternBufferStorages.size();
    }

    @Override
    public CompartmentStorage patternAggregateStorage() {
        return this.patternAggregateStorageView;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.patternStorage.writeToChildTag(tag, PATTERN_STORAGE_TAG, registries);
        this.sharedCatalystStorage.writeToChildTag(tag, SHARED_CATALYST_STORAGE_TAG, registries);
        this.fluidConfig.writeToChildTag(tag, FLUID_CONFIG_TAG, registries);
        this.keyConfig.writeToChildTag(tag, KEY_CONFIG_TAG, registries);
        tag.put(PATTERN_BUFFER_STORAGES_TAG, writePatternBufferStorages(registries));
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.patternStorage.readFromChildTag(tag, PATTERN_STORAGE_TAG, registries);
        this.sharedCatalystStorage.readFromChildTag(tag, SHARED_CATALYST_STORAGE_TAG, registries);
        this.fluidConfig.readFromChildTag(tag, FLUID_CONFIG_TAG, registries);
        this.keyConfig.readFromChildTag(tag, KEY_CONFIG_TAG, registries);
        readPatternBufferStorages(tag.getList(PATTERN_BUFFER_STORAGES_TAG, Tag.TAG_COMPOUND), registries);
    }

    @Override
    protected void rebuildCanonicalStorage() {
        mutableStorage().clear();
        copyInventoryToStorage(this.patternStorage);
        copyInventoryToStorage(this.sharedCatalystStorage);
        copyInventoryToStorage(this.fluidConfig);
        copyInventoryToStorage(this.keyConfig);
        copyPatternBuffersToStorage();
    }

    private void copyPatternBuffersToStorage() {
        for (int slot = 0; slot < Math.min(this.patternBufferStorages.size(), unlockedSlotCount()); slot++) {
            for (Object2LongMap.Entry<AEKey> entry : this.patternBufferStorages.get(slot).entries().object2LongEntrySet()) {
                if (entry.getKey() != null && entry.getLongValue() > 0L) {
                    mutableStorage().insert(entry.getKey(), entry.getLongValue(), false);
                }
            }
        }
    }

    private List<CompartmentStorage> unlockedPatternBufferStorageViews() {
        int slotCount = Math.min(this.patternBufferStorageViews.size(), unlockedSlotCount());
        return List.copyOf(this.patternBufferStorageViews.subList(0, slotCount));
    }

    private ListTag writePatternBufferStorages(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < this.patternBufferStorages.size(); slot++) {
            CompartmentStorage patternBuffer = this.patternBufferStorages.get(slot);
            if (patternBuffer.isEmpty()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt(PATTERN_BUFFER_SLOT_TAG, slot);
            entryTag.put(PATTERN_BUFFER_STORAGE_TAG, patternBuffer.serializeNBT(registries));
            list.add(entryTag);
        }
        return list;
    }

    private void readPatternBufferStorages(ListTag list, HolderLookup.Provider registries) {
        for (CompartmentStorage patternBuffer : this.patternBufferStorages) {
            patternBuffer.clear();
        }
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            int slot = entryTag.getInt(PATTERN_BUFFER_SLOT_TAG);
            if (slot < 0 || slot >= this.patternBufferStorages.size()) {
                throw new IllegalArgumentException("Pattern buffer storage slot out of range: " + slot);
            }
            this.patternBufferStorages.get(slot).deserializeNBT(
                    registries,
                    entryTag.getList(PATTERN_BUFFER_STORAGE_TAG, Tag.TAG_COMPOUND));
        }
    }
}
