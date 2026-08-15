package com.fish_dan_.data_energistics.integration.modernindustrialization;

import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.api.energy.MIEnergyStorage;
import aztech.modern_industrialization.config.MIServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * Type-safe external-energy access to Modern Industrialization's sided EU storage.
 */
public final class ModernIndustrializationEnergyIntegration {

    private ModernIndustrializationEnergyIntegration() {}

    /**
     * Resolves one MI storage and wraps it with the currently configured external-energy-per-EU conversion.
     *
     * <p>
     * The ratio is read for every state or transfer operation so a server config reload cannot leave a stale cached
     * conversion. Transfer requests that do not contain a complete EU are rounded down without mutating the storage.
     * </p>
     *
     * @param level level containing the target
     * @param pos   target block position
     * @param side  queried side, or null for unsided access
     * @return long-width MI energy storage, or null when no sided MI storage is exposed
     */
    @Nullable
    public static ModernIndustrializationEnergyStorage findEnergyStorage(
                                                                         Level level,
                                                                         BlockPos pos,
                                                                         @Nullable Direction side) {
        MIEnergyStorage miStorage = level.getCapability(EnergyApi.SIDED, pos, side);
        if (miStorage == null) {
            return null;
        }
        return new ModernIndustrializationEnergyStorageImpl(miStorage);
    }

    /**
     * Implements the project-facing contract without leaking optional MI symbols into shared tower code.
     */
    private static final class ModernIndustrializationEnergyStorageImpl
                                                                        implements ModernIndustrializationEnergyStorage {

        private final MIEnergyStorage miStorage;

        private ModernIndustrializationEnergyStorageImpl(MIEnergyStorage miStorage) {
            this.miStorage = miStorage;
        }

        @Override
        public Object backingIdentity() {
            return this.miStorage;
        }

        @Override
        public EnergySnapshot snapshot() {
            long storedEu = this.miStorage.getAmount();
            long capacityEu = this.miStorage.getCapacity();
            if (storedEu < 0 || capacityEu < storedEu) {
                throw new IllegalStateException(
                        "Modern Industrialization energy storage returned invalid EU state " + storedEu + "/" + capacityEu);
            }
            int currentRatio = currentRatio();
            return new EnergySnapshot(
                    saturatingMultiply(storedEu, currentRatio),
                    saturatingMultiply(capacityEu, currentRatio));
        }

        @Override
        public long transferQuantum() {
            return currentRatio();
        }

        @Override
        public long insert(long amount, boolean simulate) {
            validateAmount(amount);
            if (amount == 0 || !canReceive()) {
                return 0;
            }
            return transfer(amount, simulate, true);
        }

        @Override
        public long extract(long amount, boolean simulate) {
            validateAmount(amount);
            if (amount == 0 || !canExtract()) {
                return 0;
            }
            return transfer(amount, simulate, false);
        }

        @Override
        public long compensateExtraction(long amount) {
            return insert(amount, false);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return (int) insert(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return (int) extract(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return clampToInt(snapshot().stored());
        }

        @Override
        public int getMaxEnergyStored() {
            return clampToInt(snapshot().capacity());
        }

        @Override
        public boolean canExtract() {
            return this.miStorage.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.miStorage.canReceive();
        }

        private long transfer(long amount, boolean simulate, boolean inserting) {
            int currentRatio = currentRatio();
            long requestedEu = amount / currentRatio;
            if (requestedEu == 0) {
                return 0;
            }
            long transferredEu = inserting ? this.miStorage.receive(requestedEu, simulate) : this.miStorage.extract(requestedEu, simulate);
            validateEuTransferResult(inserting ? "insertion" : "extraction", requestedEu, transferredEu);
            return Math.multiplyExact(transferredEu, currentRatio);
        }

        private static void validateAmount(long amount) {
            if (amount < 0) {
                throw new IllegalArgumentException(
                        "Modern Industrialization energy amount must not be negative: " + amount);
            }
        }

        private int currentRatio() {
            int currentRatio = MIServerConfig.INSTANCE.forgeEnergyPerEu.getAsInt();
            if (currentRatio <= 0) {
                throw new IllegalStateException(
                        "Modern Industrialization external-energy-per-EU ratio must be positive: " + currentRatio);
            }
            return currentRatio;
        }

        private static void validateEuTransferResult(String operation, long requested, long actual) {
            if (actual < 0 || actual > requested) {
                throw new IllegalStateException(
                        "Modern Industrialization energy storage returned invalid EU " + operation + " result " + actual + " for " + requested);
            }
        }

        private static int clampToInt(long amount) {
            return (int) Math.min(amount, Integer.MAX_VALUE);
        }

        private static long saturatingMultiply(long amount, int ratio) {
            return amount > Long.MAX_VALUE / ratio ? Long.MAX_VALUE : amount * ratio;
        }
    }
}
