package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TeleportAnchorSavedData extends SavedData {

    private static final String DATA_NAME = Data_Energistics.MODID + "_teleport_anchors";
    private static final String ENTRIES_TAG = "entries";
    private static final String DIMENSION_TAG = "dimension";
    private static final String POSITION_TAG = "pos";
    private static final String NAME_TAG = "name";
    private static final String CHANNEL_TAG = "channel";
    private static final Factory<TeleportAnchorSavedData> FACTORY = new Factory<>(
            TeleportAnchorSavedData::new,
            TeleportAnchorSavedData::load);

    private final Map<AnchorKey, AnchorRecord> anchors = new LinkedHashMap<>();

    public static TeleportAnchorSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static TeleportAnchorSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TeleportAnchorSavedData data = new TeleportAnchorSavedData();
        Tag entriesTag = tag.get(ENTRIES_TAG);
        if (entriesTag instanceof ListTag listTag) {
            for (Tag entryTag : listTag) {
                if (!(entryTag instanceof CompoundTag entry)) {
                    continue;
                }

                String dimensionId = entry.getString(DIMENSION_TAG);
                String name = entry.getString(NAME_TAG);
                String channel = entry.contains(CHANNEL_TAG) ? entry.getString(CHANNEL_TAG) : "default";
                BlockPos pos = NbtUtils.readBlockPos(entry, POSITION_TAG).orElse(null);
                if (dimensionId.isBlank() || name.isBlank() || channel.isBlank() || pos == null) {
                    continue;
                }

                ResourceLocation dimension = ResourceLocation.parse(dimensionId);
                data.anchors.put(new AnchorKey(dimension, pos.immutable()),
                        new AnchorRecord(dimension, pos.immutable(), name, channel));
            }
        }
        return data;
    }

    public void registerAnchor(ResourceLocation dimensionId, BlockPos pos, String name, String channel) {
        AnchorKey key = new AnchorKey(dimensionId, pos.immutable());
        AnchorRecord existing = this.anchors.get(key);
        if (existing != null && existing.name().equals(name) && existing.channel().equals(channel)) {
            return;
        }

        this.anchors.put(key, new AnchorRecord(dimensionId, pos.immutable(), name, channel));
        this.setDirty();
    }

    public void removeAnchor(ResourceLocation dimensionId, BlockPos pos) {
        AnchorKey key = new AnchorKey(dimensionId, pos.immutable());
        if (this.anchors.remove(key) != null) {
            this.setDirty();
        }
    }

    public List<AnchorRecord> getAnchors() {
        return List.copyOf(this.anchors.values());
    }

    public Collection<AnchorRecord> getAnchors(ResourceLocation dimensionId) {
        ArrayList<AnchorRecord> matches = new ArrayList<>();
        for (AnchorRecord anchor : this.anchors.values()) {
            if (anchor.dimensionId().equals(dimensionId)) {
                matches.add(anchor);
            }
        }
        return matches;
    }

    public int pruneMissingAnchors(MinecraftServer server) {
        ArrayList<AnchorKey> staleKeys = new ArrayList<>();
        for (var entry : this.anchors.entrySet()) {
            if (!isAnchorStillPresent(server, entry.getValue())) {
                staleKeys.add(entry.getKey());
            }
        }

        if (staleKeys.isEmpty()) {
            return 0;
        }

        for (AnchorKey staleKey : staleKeys) {
            this.anchors.remove(staleKey);
        }
        this.setDirty();
        return staleKeys.size();
    }

    private boolean isAnchorStillPresent(MinecraftServer server, AnchorRecord record) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimensionId()));
        if (level == null) {
            return false;
        }

        level.getChunkAt(record.pos());
        return level.getBlockEntity(record.pos()) instanceof DataTeleportAnchorBlockEntity;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();
        for (AnchorRecord anchor : this.anchors.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(DIMENSION_TAG, anchor.dimensionId().toString());
            entry.put(POSITION_TAG, NbtUtils.writeBlockPos(anchor.pos()));
            entry.putString(NAME_TAG, anchor.name());
            entry.putString(CHANNEL_TAG, anchor.channel());
            listTag.add(entry);
        }
        tag.put(ENTRIES_TAG, listTag);
        return tag;
    }

    private record AnchorKey(ResourceLocation dimensionId, BlockPos pos) {}

    public record AnchorRecord(ResourceLocation dimensionId, BlockPos pos, String name, String channel) {}
}
