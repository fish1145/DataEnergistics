package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentOutputStorage;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent state and ME storage exposure for ME output compartments.
 */
public class MeCompositeOutputWarehouseBlockEntity extends CompartmentBlockEntity {

    private final CompartmentOutputStorage outputStorage = new CompartmentOutputStorage(
            this,
            mutableStorage(),
            Component.translatable("block.data_energistics.me_composite_output_warehouse"));

    public MeCompositeOutputWarehouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    @Override
    public MEStorage outputStorage() {
        return isCompartmentBound() ? this.outputStorage : null;
    }
}
