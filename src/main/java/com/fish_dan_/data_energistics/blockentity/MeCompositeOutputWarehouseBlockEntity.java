package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentOutputStorage;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent state and ME storage exposure for ME output compartments.
 */
public class MeCompositeOutputWarehouseBlockEntity extends AeCompartmentBlockEntity {

    private final CompartmentOutputStorage outputStorage = new CompartmentOutputStorage(
            this,
            mutableStorage(),
            Component.translatable("block.data_energistics.me_composite_output_warehouse"));
    private final IStorageProvider storageProvider = new OutputStorageProvider();

    public MeCompositeOutputWarehouseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(), pos, state);
        this.getMainNode().addService(IStorageProvider.class, this.storageProvider);
    }

    @Nullable
    @Override
    public MEStorage outputStorage() {
        return isCompartmentBound() ? this.outputStorage : null;
    }

    IStorageProvider outputStorageProvider() {
        return this.storageProvider;
    }

    @Override
    protected void onCompartmentBindingChanged() {
        requestStorageUpdate();
    }

    @Override
    protected void onCompartmentStorageChanged() {
        requestStorageUpdate();
    }

    protected void requestStorageUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    private final class OutputStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            MEStorage storage = outputStorage();
            if (storage != null) {
                storageMounts.mount(storage, IStorageMounts.DEFAULT_PRIORITY);
            }
        }
    }
}
