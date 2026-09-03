package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerDeviceKey;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the binding schema used by release 3.1.3 and the current release.
 */
public final class VersionedTowerBindingCodec {

    /** Current persistent binding schema. */
    public static final int CURRENT_VERSION = 2;

    /** Version tag identifying the supported binding representation. */
    public static final String VERSION_TAG = "tower_bindings_version";

    /** Current binding-list tag. */
    public static final String BINDINGS_TAG = "tower_bindings";

    /**
     * Reads the complete supported binding representation.
     *
     * @param root tower block-entity tag
     * @return immutable bindings ordered by FIFO sequence
     */
    public List<TowerBinding> read(CompoundTag root) {
        int version = root.getInt(VERSION_TAG);
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported tower binding version: " + version);
        }
        return readVersioned(root);
    }

    /**
     * Writes the complete current binding representation.
     *
     * @param root     tower block-entity tag
     * @param bindings bindings to persist
     */
    public void write(CompoundTag root, List<TowerBinding> bindings) {
        ObjectArrayList<TowerBinding> orderedBindings = new ObjectArrayList<>(bindings);
        orderedBindings.sort(Comparator.comparingLong(TowerBinding::fifoSequence));

        ListTag bindingTags = new ListTag();
        for (TowerBinding binding : orderedBindings) {
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putString("dimension", binding.dimensionId().toString());
            bindingTag.put("anchor", NbtUtils.writeBlockPos(binding.anchor()));
            bindingTag.putString("kind", binding.kind().name());
            bindingTag.putString("source", binding.source().name());
            bindingTag.putLong("fifo", binding.fifoSequence());
            bindingTag.putBoolean("enabled", binding.enabled());

            ObjectArrayList<TowerDeviceKey> orderedDeviceKeys = new ObjectArrayList<>(binding.disabledDeviceKeys());
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
    }

    private static List<TowerBinding> readVersioned(CompoundTag root) {
        if (!root.contains(BINDINGS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Versioned tower data is missing its binding list");
        }
        ListTag bindingTags = root.getList(BINDINGS_TAG, Tag.TAG_COMPOUND);
        ObjectArrayList<TowerBinding> bindings = new ObjectArrayList<>(bindingTags.size());
        LongSet fifoSequences = new LongOpenHashSet();
        for (Tag rawBindingTag : bindingTags) {
            CompoundTag bindingTag = (CompoundTag) rawBindingTag;
            ResourceLocation dimensionId = parseId(bindingTag.getString("dimension"), "binding dimension");
            BlockPos anchor = NbtUtils.readBlockPos(bindingTag, "anchor")
                    .orElseThrow(() -> new IllegalArgumentException("Tower binding is missing its anchor"));
            TowerBindingKind kind = readBindingKind(bindingTag);
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
                    dimensionId, anchor, kind, source, fifoSequence, enabled, disabledDeviceKeys));
        }
        bindings.sort(Comparator.comparingLong(TowerBinding::fifoSequence));
        return List.copyOf(bindings);
    }

    private static TowerBindingKind readBindingKind(CompoundTag bindingTag) {
        try {
            return TowerBindingKind.valueOf(bindingTag.getString("kind"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Tower binding has an invalid kind", exception);
        }
    }

    private static Set<TowerDeviceKey> readDeviceKeys(CompoundTag bindingTag) {
        if (!bindingTag.contains("disabled_devices")) {
            return Set.of();
        }
        if (!bindingTag.contains("disabled_devices", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Tower binding disabled-device data is not a list");
        }
        ObjectOpenHashSet<TowerDeviceKey> result = new ObjectOpenHashSet<>();
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

    private static ResourceLocation parseId(String serializedId, String fieldName) {
        ResourceLocation result = ResourceLocation.tryParse(serializedId);
        if (result == null) {
            throw new IllegalArgumentException("Tower binding has an invalid " + fieldName + ": " + serializedId);
        }
        return result;
    }
}
