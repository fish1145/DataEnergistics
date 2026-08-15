package com.fish_dan_.data_energistics.world.sanctum;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataSanctumPortalSavedData extends SavedData {

    private static final String DATA_NAME = Data_Energistics.MODID + "_data_sanctum_portals";
    private static final String ENTRIES_TAG = "entries";
    private static final String RETURN_POSITION_TAG = "return_pos";
    private static final String SOURCE_DIMENSION_TAG = "source_dimension";
    private static final String SOURCE_POSITION_TAG = "source_pos";
    private static final Factory<DataSanctumPortalSavedData> FACTORY = new Factory<>(
            DataSanctumPortalSavedData::new,
            DataSanctumPortalSavedData::load);

    private final Map<BlockPos, PortalRecord> portals = new LinkedHashMap<>();

    public static DataSanctumPortalSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static DataSanctumPortalSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DataSanctumPortalSavedData data = new DataSanctumPortalSavedData();
        Tag entriesTag = tag.get(ENTRIES_TAG);
        if (entriesTag instanceof ListTag listTag) {
            for (Tag entryTag : listTag) {
                if (!(entryTag instanceof CompoundTag entry)) {
                    continue;
                }

                BlockPos returnPos = NbtUtils.readBlockPos(entry, RETURN_POSITION_TAG).orElse(null);
                BlockPos sourcePos = NbtUtils.readBlockPos(entry, SOURCE_POSITION_TAG).orElse(null);
                String sourceDimensionId = entry.getString(SOURCE_DIMENSION_TAG);
                if (returnPos == null || sourcePos == null || sourceDimensionId.isBlank()) {
                    continue;
                }

                ResourceLocation sourceDimension = ResourceLocation.parse(sourceDimensionId);
                data.portals.put(returnPos.immutable(),
                        new PortalRecord(returnPos.immutable(), sourceDimension, sourcePos.immutable()));
            }
        }
        return data;
    }

    public void registerPortal(BlockPos returnPos, ResourceLocation sourceDimensionId, BlockPos sourcePos) {
        PortalRecord record = new PortalRecord(returnPos.immutable(), sourceDimensionId, sourcePos.immutable());
        PortalRecord existing = this.portals.get(record.returnPos());
        if (record.equals(existing)) {
            return;
        }

        this.portals.put(record.returnPos(), record);
        this.setDirty();
    }

    public void removePortal(BlockPos returnPos, ResourceLocation sourceDimensionId, BlockPos sourcePos) {
        PortalRecord existing = this.portals.get(returnPos);
        if (existing != null && existing.sourceDimensionId().equals(sourceDimensionId) && existing.sourcePos().equals(sourcePos)) {
            this.portals.remove(returnPos);
            this.setDirty();
        }
    }

    public List<PortalRecord> getPortals() {
        return List.copyOf(this.portals.values());
    }

    public List<PortalRecord> getPortalsForSource(ResourceLocation sourceDimensionId, BlockPos sourcePos) {
        ArrayList<PortalRecord> matches = new ArrayList<>();
        for (PortalRecord portal : this.portals.values()) {
            if (portal.sourceDimensionId().equals(sourceDimensionId) && portal.sourcePos().equals(sourcePos)) {
                matches.add(portal);
            }
        }
        return matches;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();
        for (PortalRecord portal : this.portals.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put(RETURN_POSITION_TAG, NbtUtils.writeBlockPos(portal.returnPos()));
            entry.putString(SOURCE_DIMENSION_TAG, portal.sourceDimensionId().toString());
            entry.put(SOURCE_POSITION_TAG, NbtUtils.writeBlockPos(portal.sourcePos()));
            listTag.add(entry);
        }
        tag.put(ENTRIES_TAG, listTag);
        return tag;
    }

    public record PortalRecord(BlockPos returnPos, ResourceLocation sourceDimensionId, BlockPos sourcePos) {}
}
