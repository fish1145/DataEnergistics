package com.fish_dan_.data_energistics.world.farmland;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class PersistentFarmlandSavedData extends SavedData {

    private static final String DATA_NAME = Data_Energistics.MODID + "_persistent_farmland";
    private static final String POSITIONS_TAG = "Positions";
    private final Set<Long> farmlandPositions = new HashSet<>();

    public static final Factory<PersistentFarmlandSavedData> FACTORY = new Factory<>(
            PersistentFarmlandSavedData::new,
            PersistentFarmlandSavedData::load);

    public static PersistentFarmlandSavedData get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME + "_" + level.dimension().location());
    }

    public static PersistentFarmlandSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PersistentFarmlandSavedData data = new PersistentFarmlandSavedData();
        ListTag positions = tag.getList(POSITIONS_TAG, Tag.TAG_LONG);
        for (Tag entry : positions) {
            data.farmlandPositions.add(((LongTag) entry).getAsLong());
        }
        return data;
    }

    public void add(BlockPos pos) {
        if (this.farmlandPositions.add(pos.asLong())) {
            this.setDirty();
        }
    }

    public boolean contains(BlockPos pos) {
        return this.farmlandPositions.contains(pos.asLong());
    }

    public void remove(BlockPos pos) {
        if (this.farmlandPositions.remove(pos.asLong())) {
            this.setDirty();
        }
    }

    public Set<Long> getPositions() {
        return Set.copyOf(this.farmlandPositions);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag positions = new ListTag();
        for (long pos : this.farmlandPositions) {
            positions.add(LongTag.valueOf(pos));
        }
        tag.put(POSITIONS_TAG, positions);
        return tag;
    }
}
