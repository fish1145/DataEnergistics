package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.TuningForkBlock;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the primitive damage value of a placed tuning fork.
 */
public class TuningForkBlockEntity extends BlockEntity {

    private static final String DAMAGE_TAG = "damage";

    private int damage;

    public TuningForkBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.TUNING_FORK_BLOCK_ENTITY.get(), pos, state);
    }

    public int getDamage() {
        return this.damage;
    }

    public void setDamage(int damage) {
        int clampedDamage = Math.max(0, Math.min(variant().durability(), damage));
        if (clampedDamage != this.damage) {
            this.damage = clampedDamage;
            setChanged();
        }
    }

    /**
     * Applies one completed wave hit, including durability, success roll, partial network insertion and final breakage.
     */
    public boolean processWave(RandomSource random, boolean wardenWave) {
        TuningForkVariant variant = variant();
        this.damage = Math.min(variant.durability(), this.damage + 1);
        setChanged();

        boolean inserted = false;
        if (this.level != null &&
                this.level.getBlockEntity(this.worldPosition.below()) instanceof TuningForkBaseBlockEntity base &&
                (wardenWave ||
                        base.canProduceEcho() && random.nextFloat() < TuningForkVariant.ECHO_SUCCESS_CHANCE)) {
            int echoAmount = wardenWave ? variant.wardenEchoYield() : variant.ordinaryEchoYield();
            inserted = base.tryInsertEcho(variant, echoAmount);
        }

        if (this.damage >= variant.durability() && this.level != null && !this.level.isClientSide()) {
            this.level.destroyBlock(this.worldPosition, false);
        }
        return inserted;
    }

    public ItemStack createDrop() {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        stack.setDamageValue(this.damage);
        return stack;
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.damage = Math.max(0, Math.min(variant().durability(), tag.getInt(DAMAGE_TAG)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(DAMAGE_TAG, this.damage);
    }

    private TuningForkVariant variant() {
        if (getBlockState().getBlock() instanceof TuningForkBlock tuningFork) {
            return tuningFork.getVariant();
        }
        throw new IllegalStateException("Tuning fork block entity has incompatible state: " + getBlockState());
    }
}
