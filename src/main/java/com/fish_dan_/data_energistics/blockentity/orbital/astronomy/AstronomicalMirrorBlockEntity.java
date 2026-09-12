package com.fish_dan_.data_energistics.blockentity.orbital.astronomy;

import com.fish_dan_.data_energistics.orbital.astronomy.InterferenceArrayPattern;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Persists the exclusive high-tier array core claim for one 3x3 astronomical mirror unit.
 */
public final class AstronomicalMirrorBlockEntity extends BlockEntity {

    private static final String CLAIMED_CORE_TAG = "claimedCore";

    private @Nullable BlockPos claimedCore;

    public AstronomicalMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.ASTRONOMICAL_MIRROR_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Claims this mirror for one core, rejecting a second structurally valid core until the first releases it.
     */
    public boolean tryClaim(ServerLevel level, BlockPos corePos) {
        BlockPos immutableCorePos = corePos.immutable();
        if (immutableCorePos.equals(this.claimedCore)) {
            return true;
        }
        if (this.claimedCore != null && InterferenceArrayPattern.hasValidCoreBase(level, this.claimedCore)) {
            return false;
        }
        this.claimedCore = immutableCorePos;
        setChanged();
        return true;
    }

    /**
     * Releases this mirror only when the caller owns its current claim.
     */
    public void release(BlockPos corePos) {
        if (corePos.equals(this.claimedCore)) {
            this.claimedCore = null;
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.loadAdditional(data, registries);
        this.claimedCore = data.contains(CLAIMED_CORE_TAG) ? BlockPos.of(data.getLong(CLAIMED_CORE_TAG)) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (this.claimedCore != null) {
            data.putLong(CLAIMED_CORE_TAG, this.claimedCore.asLong());
        }
    }
}
