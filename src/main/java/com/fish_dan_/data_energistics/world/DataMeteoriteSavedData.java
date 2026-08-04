package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

public class DataMeteoriteSavedData extends SavedData {

    private static final String DATA_NAME = Data_Energistics.MODID + "_meteorites";
    private static final String POSITIONS_TAG = "positions";
    private final Set<Long> meteoritePositions = new HashSet<>();

    public static final Factory<DataMeteoriteSavedData> FACTORY = new Factory<>(
            DataMeteoriteSavedData::new,
            DataMeteoriteSavedData::load);

    public static DataMeteoriteSavedData get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ResourceLocation dimension = level.dimension().location();
        return server.overworld().getDataStorage().computeIfAbsent(
                FACTORY,
                DATA_NAME + "_" + dimension.getNamespace() + "_" + dimension.getPath().replace('/', '_'));
    }

    private static DataMeteoriteSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        DataMeteoriteSavedData data = new DataMeteoriteSavedData();
        ListTag positions = tag.getList(POSITIONS_TAG, Tag.TAG_LONG);
        for (Tag entry : positions) {
            data.meteoritePositions.add(((LongTag) entry).getAsLong());
        }
        return data;
    }

    public void add(BlockPos pos) {
        if (this.meteoritePositions.add(pos.immutable().asLong())) {
            this.setDirty();
        }
    }

    public boolean remove(BlockPos pos) {
        if (this.meteoritePositions.remove(pos.asLong())) {
            this.setDirty();
            return true;
        }
        return false;
    }

    @Nullable
    public BlockPos findClosest(ChunkPos originChunkPos) {
        return this.findClosest(originChunkPos, ignored -> true);
    }

    @Nullable
    public BlockPos findClosest(ChunkPos originChunkPos, Predicate<BlockPos> isValid) {
        if (this.meteoritePositions.isEmpty()) {
            return null;
        }

        BlockPos origin = originChunkPos.getMiddleBlockPosition(0);
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        boolean removedInvalidPosition = false;
        Iterator<Long> positions = this.meteoritePositions.iterator();
        while (positions.hasNext()) {
            long packedPos = positions.next();
            BlockPos pos = BlockPos.of(packedPos);
            if (!isValid.test(pos)) {
                positions.remove();
                removedInvalidPosition = true;
                continue;
            }

            double distance = origin.distSqr(pos.atY(0));
            if (distance < closestDistance) {
                closest = pos;
                closestDistance = distance;
            }
        }

        if (removedInvalidPosition) {
            this.setDirty();
        }
        return closest;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag positions = new ListTag();
        for (long pos : this.meteoritePositions) {
            positions.add(LongTag.valueOf(pos));
        }
        tag.put(POSITIONS_TAG, positions);
        return tag;
    }
}
