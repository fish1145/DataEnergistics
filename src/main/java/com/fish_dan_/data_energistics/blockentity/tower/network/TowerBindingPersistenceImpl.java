package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NBT implementation for version-one tower bindings and ordered legacy migration.
 */
public final class TowerBindingPersistenceImpl implements TowerBindingPersistence {

    /** Current persistent binding schema. */
    public static final int CURRENT_VERSION = 1;

    /** Version tag used to distinguish current data from legacy linked positions. */
    public static final String VERSION_TAG = "tower_bindings_version";

    /** Current binding-list tag. */
    public static final String BINDINGS_TAG = "tower_bindings";

    /** Legacy linked-position tag retained solely for migration. */
    public static final String LEGACY_LINKED_POSITIONS_TAG = "linked_positions";

    @Override
    public List<TowerBinding> read(CompoundTag root,
                                   @Nullable ResourceLocation towerDimensionId,
                                   Map<BlockPos, Boolean> legacyDisabledStates) {
        if (root.contains(VERSION_TAG, Tag.TAG_INT)) {
            int version = root.getInt(VERSION_TAG);
            if (version != CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported tower binding version: " + version);
            }
            return readCurrent(root);
        }
        if (towerDimensionId == null) {
            throw new IllegalStateException("Legacy tower bindings require the tower dimension");
        }
        return migrateLegacy(root, towerDimensionId, legacyDisabledStates);
    }

    @Override
    public void write(CompoundTag root, List<TowerBinding> bindings) {
        ArrayList<TowerBinding> orderedBindings = new ArrayList<>(bindings);
        orderedBindings.sort(Comparator.comparingLong(TowerBinding::fifoSequence));

        ListTag bindingTags = new ListTag();
        for (TowerBinding binding : orderedBindings) {
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putString("dimension", binding.dimensionId().toString());
            bindingTag.put("anchor", NbtUtils.writeBlockPos(binding.anchor()));
            bindingTag.putString("source", binding.source().name());
            bindingTag.putLong("fifo", binding.fifoSequence());
            bindingTag.putBoolean("enabled", binding.enabled());

            ArrayList<TowerDeviceKey> orderedDeviceKeys = new ArrayList<>(binding.disabledDeviceKeys());
            orderedDeviceKeys.sort(Comparator.naturalOrder());
            ListTag disabledTags = new ListTag();
            for (TowerDeviceKey deviceKey : orderedDeviceKeys) {
                CompoundTag deviceTag = new CompoundTag();
                deviceTag.putString("dimension", deviceKey.dimensionId().toString());
                if (deviceKey.position() != null) {
                    deviceTag.put("position", NbtUtils.writeBlockPos(deviceKey.position()));
                }
                deviceTag.putInt("side", deviceKey.side());
                deviceTag.putString("type", deviceKey.nodeType());
                deviceTag.putInt("occurrence", deviceKey.occurrence());
                disabledTags.add(deviceTag);
            }
            bindingTag.put("disabled_devices", disabledTags);
            bindingTags.add(bindingTag);
        }
        root.putInt(VERSION_TAG, CURRENT_VERSION);
        root.put(BINDINGS_TAG, bindingTags);
        root.remove(LEGACY_LINKED_POSITIONS_TAG);
    }

    private static List<TowerBinding> readCurrent(CompoundTag root) {
        if (!root.contains(BINDINGS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Versioned tower data is missing its binding list");
        }
        ListTag bindingTags = root.getList(BINDINGS_TAG, Tag.TAG_COMPOUND);
        ArrayList<TowerBinding> bindings = new ArrayList<>(bindingTags.size());
        Set<Long> fifoSequences = new HashSet<>();
        for (Tag rawBindingTag : bindingTags) {
            CompoundTag bindingTag = (CompoundTag) rawBindingTag;
            ResourceLocation dimensionId = parseId(bindingTag.getString("dimension"), "binding dimension");
            BlockPos anchor = NbtUtils.readBlockPos(bindingTag, "anchor")
                    .orElseThrow(() -> new IllegalArgumentException("Tower binding is missing its anchor"));
            TowerBindingSource source;
            try {
                source = TowerBindingSource.valueOf(bindingTag.getString("source"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Tower binding has an invalid source", exception);
            }
            long fifoSequence = bindingTag.getLong("fifo");
            if (!fifoSequences.add(fifoSequence)) {
                throw new IllegalArgumentException("Tower bindings contain duplicate FIFO sequence " + fifoSequence);
            }
            boolean enabled = !bindingTag.contains("enabled") || bindingTag.getBoolean("enabled");
            Set<TowerDeviceKey> disabledDeviceKeys = readDeviceKeys(bindingTag);
            bindings.add(new TowerBinding(
                    dimensionId, anchor, source, fifoSequence, enabled, disabledDeviceKeys));
        }
        bindings.sort(Comparator.comparingLong(TowerBinding::fifoSequence));
        return List.copyOf(bindings);
    }

    private static Set<TowerDeviceKey> readDeviceKeys(CompoundTag bindingTag) {
        if (!bindingTag.contains("disabled_devices")) {
            return Set.of();
        }
        if (!bindingTag.contains("disabled_devices", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Tower binding disabled-device data is not a list");
        }
        HashSet<TowerDeviceKey> result = new HashSet<>();
        for (Tag rawDeviceTag : bindingTag.getList("disabled_devices", Tag.TAG_COMPOUND)) {
            CompoundTag deviceTag = (CompoundTag) rawDeviceTag;
            ResourceLocation dimensionId = parseId(deviceTag.getString("dimension"), "device dimension");
            BlockPos position = NbtUtils.readBlockPos(deviceTag, "position").orElse(null);
            TowerDeviceKey deviceKey = new TowerDeviceKey(
                    dimensionId,
                    position,
                    deviceTag.getInt("side"),
                    deviceTag.getString("type"),
                    deviceTag.getInt("occurrence"));
            if (!result.add(deviceKey)) {
                throw new IllegalArgumentException("Tower binding contains a duplicate disabled-device key");
            }
        }
        return Set.copyOf(result);
    }

    private static List<TowerBinding> migrateLegacy(CompoundTag root,
                                                    ResourceLocation towerDimensionId,
                                                    Map<BlockPos, Boolean> legacyDisabledStates) {
        if (!root.contains(LEGACY_LINKED_POSITIONS_TAG)) {
            return List.of();
        }
        if (!root.contains(LEGACY_LINKED_POSITIONS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Legacy linked positions are not a list");
        }
        ListTag positions = root.getList(LEGACY_LINKED_POSITIONS_TAG, Tag.TAG_COMPOUND);
        ArrayList<TowerBinding> bindings = new ArrayList<>(positions.size());
        HashSet<BlockPos> seen = new HashSet<>();
        for (Tag rawPositionTag : positions) {
            CompoundTag positionTag = (CompoundTag) rawPositionTag;
            BlockPos position = NbtUtils.readBlockPos(positionTag, "pos")
                    .orElseThrow(() -> new IllegalArgumentException("Legacy tower binding is missing its position"));
            if (!seen.add(position)) {
                continue;
            }
            boolean enabled = !legacyDisabledStates.getOrDefault(position, false);
            bindings.add(new TowerBinding(
                    towerDimensionId,
                    position,
                    TowerBindingSource.MANUAL,
                    bindings.size(),
                    enabled,
                    Set.of()));
        }
        return List.copyOf(bindings);
    }

    private static ResourceLocation parseId(String serializedId, String fieldName) {
        ResourceLocation result = ResourceLocation.tryParse(serializedId);
        if (result == null) {
            throw new IllegalArgumentException("Tower binding has an invalid " + fieldName + ": " + serializedId);
        }
        return result;
    }
}
