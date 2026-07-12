package com.fish_dan_.data_energistics.integration.oritech;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyStorage;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;
import rearth.oritech.api.energy.EnergyApi;
import rearth.oritech.api.energy.EnergyApi.BlockProvider;
import rearth.oritech.api.energy.EnergyApi.EnergyStorage;

/**
 * Type-safe Oritech energy bridge used when no NeoForge energy capability is exposed.
 */
public final class OritechEnergyIntegration {

    private OritechEnergyIntegration() {}

    /**
     * Resolves Oritech's public long-width storage API for one block side.
     *
     * @param level level containing the target
     * @param pos   target position
     * @param side  queried side, or null for internal access
     * @return NeoForge view backed by Oritech's public storage, or null
     */
    @Nullable
    public static IEnergyStorage findEnergyStorage(Level level, BlockPos pos, @Nullable Direction side) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        EnergyStorage storage = EnergyApi.BLOCK.find(level, pos, state, blockEntity, side);
        if (storage == null && blockEntity instanceof BlockProvider provider) {
            storage = provider.getEnergyStorage(side);
        }
        return storage == null ? null : wrapEnergyStorage(storage);
    }

    /**
     * Wraps Oritech's public long-width API for the tower's standard and unlimited energy paths.
     *
     * @param storage Oritech storage returned by its public API
     * @return NeoForge storage with a type-safe unlimited mutation path
     */
    static IEnergyStorage wrapEnergyStorage(EnergyStorage storage) {
        return new OritechEnergyStorageImpl(storage);
    }

    /**
     * Preserves Oritech's long-width amount behind the standard capability boundary.
     */
    private static final class OritechEnergyStorageImpl implements UnlimitedEnergyStorage {

        private final EnergyStorage storage;

        private OritechEnergyStorageImpl(EnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || !canReceive()) {
                return 0;
            }
            long inserted = mutateLong(() -> this.storage.insert(maxReceive, simulate), "insert energy");
            if (!simulate && inserted > 0) {
                onUnlimitedEnergyChanged();
            }
            return clampToInt(inserted);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || !canExtract()) {
                return 0;
            }
            long extracted = mutateLong(() -> this.storage.extract(maxExtract, simulate), "extract energy");
            if (!simulate && extracted > 0) {
                onUnlimitedEnergyChanged();
            }
            return clampToInt(extracted);
        }

        @Override
        public int getEnergyStored() {
            return clampToInt(readStandardLong(this.storage::getAmount, "read stored energy"));
        }

        @Override
        public int getMaxEnergyStored() {
            return clampToInt(readStandardLong(this.storage::getCapacity, "read energy capacity"));
        }

        @Override
        public boolean canExtract() {
            return readPermission(this.storage::supportsExtraction, "read extraction permission");
        }

        @Override
        public boolean canReceive() {
            return readPermission(this.storage::supportsInsertion, "read insertion permission");
        }

        @Override
        public long getStoredEnergyLong() {
            return readExactLong(this.storage::getAmount, "read exact stored energy");
        }

        @Override
        public long getEnergyCapacityLong() {
            return readExactLong(this.storage::getCapacity, "read exact energy capacity");
        }

        @Override
        public void setStoredEnergyLong(long amount) {
            mutate(() -> this.storage.setAmount(amount), "set stored energy");
        }

        @Override
        public void onUnlimitedEnergyChanged() {
            mutate(this.storage::update, "publish energy change");
        }

        private static long readStandardLong(LongOperation operation, String description) {
            try {
                return operation.execute();
            } catch (Throwable throwable) {
                ThrowableIsolation.rethrowIfFatal(throwable);
                Data_Energistics.LOGGER.error("Failed to {} through Oritech energy API", description, throwable);
                return 0L;
            }
        }

        private static boolean readPermission(BooleanOperation operation, String description) {
            try {
                return operation.execute();
            } catch (Throwable throwable) {
                ThrowableIsolation.rethrowIfFatal(throwable);
                Data_Energistics.LOGGER.error("Failed to {} through Oritech energy API", description, throwable);
                return false;
            }
        }

        private static long readExactLong(LongOperation operation, String description) {
            try {
                return operation.execute();
            } catch (Throwable throwable) {
                throw operationFailure(description, throwable);
            }
        }

        private static long mutateLong(LongOperation operation, String description) {
            try {
                return operation.execute();
            } catch (Throwable throwable) {
                throw operationFailure(description, throwable);
            }
        }

        private static void mutate(VoidOperation operation, String description) {
            try {
                operation.execute();
            } catch (Throwable throwable) {
                throw operationFailure(description, throwable);
            }
        }

        private static IllegalStateException operationFailure(String description, Throwable throwable) {
            ThrowableIsolation.rethrowIfFatal(throwable);
            Data_Energistics.LOGGER.error("Failed to {} through Oritech energy API", description, throwable);
            return new IllegalStateException("Failed to " + description + " through Oritech energy API", throwable);
        }

        private static int clampToInt(long amount) {
            if (amount <= 0) {
                return 0;
            }
            return (int) Math.min(amount, Integer.MAX_VALUE);
        }

        /** Supplies one exact or standard-width Oritech energy value. */
        @FunctionalInterface
        private interface LongOperation {

            /**
             * Reads or mutates one long-width energy value.
             *
             * @return operation result
             */
            long execute();
        }

        /** Supplies one Oritech storage permission. */
        @FunctionalInterface
        private interface BooleanOperation {

            /**
             * Reads the third-party permission.
             *
             * @return permission state
             */
            boolean execute();
        }

        /** Executes one Oritech mutation that has no return value. */
        @FunctionalInterface
        private interface VoidOperation {

            /** Executes the third-party mutation. */
            void execute();
        }
    }
}
