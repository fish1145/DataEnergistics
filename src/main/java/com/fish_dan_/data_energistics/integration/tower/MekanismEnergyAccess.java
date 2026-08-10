package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import mekanism.common.integration.energy.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.config.slot.ISlotInfo;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * Static optional access to Mekanism's public long-width energy containers.
 *
 * <p>
 * Mekanism's sided FE capability clamps reported state to {@code int}. These methods keep optional symbols behind one
 * guarded class-loading boundary, retain the complete FE-equivalent state, verify the queried side's automation
 * permissions through simulation, and use verified container state writes to preserve the tower's rate-limit-free
 * transfer contract.
 * </p>
 */
public final class MekanismEnergyAccess {

    private MekanismEnergyAccess() {}

    /**
     * Checks whether a resolved FE route has a matching public Mekanism long-width handler.
     */
    public static boolean supports(Level level, BlockPos pos, @Nullable Direction side, IEnergyStorage storage) {
        return ModFlags.isMekanismLoaded() && LoadedAccess.supports(level, pos, side, storage);
    }

    /**
     * Returns the physical container identity shared by equivalent sided FE routes.
     */
    @Nullable
    public static Object findBackingIdentity(
                                             Level level,
                                             BlockPos pos,
                                             @Nullable Direction side,
                                             IEnergyStorage storage) {
        if (!ModFlags.isMekanismLoaded()) {
            return null;
        }
        return LoadedAccess.findBackingIdentity(level, pos, side, storage);
    }

    /**
     * Captures the complete state and exact sided transfer budgets in the tower's existing snapshot model.
     */
    public static TowerEnergyEndpointSnapshot freeze(
                                                     Level level,
                                                     BlockPos pos,
                                                     @Nullable Direction side,
                                                     IEnergyStorage storage,
                                                     TowerEnergyEndpointId endpoint) {
        requireLoaded();
        return LoadedAccess.freeze(level, pos, side, storage, endpoint);
    }

    /**
     * Captures the full stored amount and capacity through one sided Mekanism state read.
     */
    public static EnergySnapshot snapshot(
                                          Level level,
                                          BlockPos pos,
                                          @Nullable Direction side,
                                          IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.snapshot(level, pos, side, storage);
    }

    /**
     * Resolves the transfer directions that can currently move energy through the queried external side.
     *
     * <p>
     * Mekanism's FE wrapper reports both directions unconditionally, so direction selection must use the public
     * long-width handler's sided simulations instead.
     * </p>
     */
    @Nullable
    public static TowerEnergyDirection resolveTransferDirection(
                                                                Level level,
                                                                BlockPos pos,
                                                                @Nullable Direction side,
                                                                IEnergyStorage storage) {
        requireLoaded();
        return LoadedAccess.resolveTransferDirection(level, pos, side, storage);
    }

    /**
     * Transfers FE into the sided Mekanism containers without applying their numeric transfer rate.
     */
    public static long insert(
                              Level level,
                              BlockPos pos,
                              @Nullable Direction side,
                              IEnergyStorage storage,
                              long amount,
                              boolean simulate) {
        requireLoaded();
        return LoadedAccess.transfer(level, pos, side, storage, amount, simulate, true);
    }

    /**
     * Transfers FE out of the sided Mekanism containers without applying their numeric transfer rate.
     */
    public static long extract(
                               Level level,
                               BlockPos pos,
                               @Nullable Direction side,
                               IEnergyStorage storage,
                               long amount,
                               boolean simulate) {
        requireLoaded();
        return LoadedAccess.transfer(level, pos, side, storage, amount, simulate, false);
    }

    /**
     * Restores extraction through the internal route irrespective of the external side mode.
     */
    public static long compensateExtraction(Level level, BlockPos pos, IEnergyStorage storage, long amount) {
        requireLoaded();
        return LoadedAccess.compensateExtraction(level, pos, storage, amount);
    }

    /**
     * Returns the smallest complete FE amount that survives the configured FE-to-Joule conversion.
     */
    public static long transferQuantum() {
        requireLoaded();
        return LoadedAccess.transferQuantum();
    }

    private static void requireLoaded() {
        if (!ModFlags.isMekanismLoaded()) {
            throw new IllegalStateException("Mekanism energy access is unavailable");
        }
    }

    /**
     * References optional Mekanism symbols only after the outer load guard succeeds.
     */
    private static final class LoadedAccess {

        private static final IEnergyConversion CONVERTER = EnergyUnit.FORGE_ENERGY;

        private LoadedAccess() {}

        private static boolean supports(
                                        Level level,
                                        BlockPos pos,
                                        @Nullable Direction side,
                                        IEnergyStorage storage) {
            IMekanismStrictEnergyHandler handler = findHandler(level, pos, storage);
            return handler != null && !handler.getEnergyContainers(side).isEmpty();
        }

        @Nullable
        private static Object findBackingIdentity(
                                                  Level level,
                                                  BlockPos pos,
                                                  @Nullable Direction side,
                                                  IEnergyStorage storage) {
            IMekanismStrictEnergyHandler handler = findHandler(level, pos, storage);
            if (handler == null) {
                return null;
            }
            List<IEnergyContainer> containers = handler.getEnergyContainers(side);
            return containers.isEmpty() ? null : containers.getFirst();
        }

        private static TowerEnergyEndpointSnapshot freeze(
                                                          Level level,
                                                          BlockPos pos,
                                                          @Nullable Direction side,
                                                          IEnergyStorage storage,
                                                          TowerEnergyEndpointId endpoint) {
            IMekanismStrictEnergyHandler handler = requireHandler(level, pos, storage);
            List<IEnergyContainer> containers = handler.getEnergyContainers(side);
            if (containers.isEmpty()) {
                throw new IllegalStateException("Mekanism energy route exposes no containers");
            }

            EnergySnapshot snapshot = readSnapshot(containers);
            long stored = snapshot.stored();
            long capacity = snapshot.capacity();
            TowerEnergyDirection direction = resolveRouteDirection(handler, side);
            if (direction == null) {
                throw new IllegalStateException("Mekanism FE route no longer permits transfer");
            }
            long extractable = direction.allowsExtract() ? transfer(containers, side, storage, stored, true, false, true) : 0;
            long receivable = direction.allowsReceive() ? transfer(containers, side, storage, capacity - stored, true, true, true) : 0;
            return new TowerEnergyEndpointSnapshot(
                    endpoint,
                    stored,
                    capacity,
                    Math.min(extractable, stored),
                    Math.min(receivable, capacity - stored),
                    direction);
        }

        private static EnergySnapshot snapshot(
                                               Level level,
                                               BlockPos pos,
                                               @Nullable Direction side,
                                               IEnergyStorage storage) {
            List<IEnergyContainer> containers = requireHandler(level, pos, storage).getEnergyContainers(side);
            if (containers.isEmpty()) {
                throw new IllegalStateException("Mekanism energy route exposes no containers");
            }
            return readSnapshot(containers);
        }

        @Nullable
        private static TowerEnergyDirection resolveTransferDirection(
                                                                     Level level,
                                                                     BlockPos pos,
                                                                     @Nullable Direction side,
                                                                     IEnergyStorage storage) {
            IMekanismStrictEnergyHandler handler = requireHandler(level, pos, storage);
            List<IEnergyContainer> containers = handler.getEnergyContainers(side);
            if (containers.isEmpty()) {
                return null;
            }
            return resolveRouteDirection(handler, side);
        }

        private static EnergySnapshot readSnapshot(List<IEnergyContainer> containers) {
            long storedJoules = 0;
            long capacityJoules = 0;
            for (IEnergyContainer container : containers) {
                long stored = container.getEnergy();
                long capacity = container.getMaxEnergy();
                if (stored < 0 || capacity < stored) {
                    throw new IllegalStateException(
                            "Mekanism energy container returned invalid state " + stored + "/" + capacity);
                }
                storedJoules = saturatingAdd(storedJoules, stored);
                capacityJoules = saturatingAdd(capacityJoules, capacity);
            }

            long stored = CONVERTER.convertTo(storedJoules);
            long capacity = CONVERTER.convertTo(capacityJoules);
            if (capacity < stored) {
                throw new IllegalStateException(
                        "Mekanism FE conversion returned invalid state " + stored + "/" + capacity);
            }
            return new EnergySnapshot(stored, capacity);
        }

        private static long transfer(
                                     Level level,
                                     BlockPos pos,
                                     @Nullable Direction side,
                                     IEnergyStorage storage,
                                     long amount,
                                     boolean simulate,
                                     boolean inserting) {
            List<IEnergyContainer> containers = requireHandler(level, pos, storage).getEnergyContainers(side);
            return transfer(containers, side, storage, amount, simulate, inserting, true);
        }

        private static long compensateExtraction(
                                                 Level level,
                                                 BlockPos pos,
                                                 IEnergyStorage storage,
                                                 long amount) {
            List<IEnergyContainer> containers = requireHandler(level, pos, storage).getEnergyContainers(null);
            return transfer(containers, null, storage, amount, false, true, false);
        }

        private static long transferQuantum() {
            double conversion = CONVERTER.getConversion();
            if (!Double.isFinite(conversion) || conversion <= 0) {
                throw new IllegalStateException("Mekanism FE conversion must be finite and positive");
            }
            BigDecimal decimal = BigDecimal.valueOf(conversion).stripTrailingZeros();
            if (decimal.scale() <= 0) {
                return 1;
            }
            BigInteger numerator = decimal.unscaledValue().abs();
            BigInteger denominator = BigInteger.TEN.pow(decimal.scale());
            long quantum = denominator.divide(denominator.gcd(numerator)).longValueExact();
            if (CONVERTER.convertTo(CONVERTER.convertFrom(quantum)) != quantum) {
                throw new IllegalStateException(
                        "Mekanism FE conversion has no exact long-width transfer quantum: " + conversion);
            }
            return quantum;
        }

        private static long transfer(
                                     List<IEnergyContainer> containers,
                                     @Nullable Direction side,
                                     IEnergyStorage storage,
                                     long amount,
                                     boolean simulate,
                                     boolean inserting,
                                     boolean enforceSidedPermission) {
            if (amount < 0) {
                throw new IllegalArgumentException("Mekanism energy transfer amount must not be negative: " + amount);
            }
            if (amount == 0) {
                return 0;
            }
            if (containers.isEmpty()) {
                return 0;
            }

            long requestedJoules = CONVERTER.convertFrom(amount);
            if (requestedJoules == 0) {
                return 0;
            }
            if (enforceSidedPermission && !allowsTransfer(storage, amount, inserting)) {
                return 0;
            }

            int containerCount = containers.size();
            long[] stored = new long[containerCount];
            long[] capacities = new long[containerCount];
            boolean[] permitted = new boolean[containerCount];
            long availableJoules = 0;
            AutomationType automationType = AutomationType.handler(side);
            for (int index = 0; index < containerCount; index++) {
                IEnergyContainer container = containers.get(index);
                long containerStored = container.getEnergy();
                long capacity = container.getMaxEnergy();
                if (containerStored < 0 || capacity < containerStored) {
                    throw new IllegalStateException(
                            "Mekanism energy container returned invalid state " + containerStored + "/" + capacity);
                }
                stored[index] = containerStored;
                capacities[index] = capacity;

                long available = inserting ? capacity - containerStored : containerStored;
                if (available == 0) {
                    continue;
                }
                long probe = Math.min(requestedJoules, available);
                long simulated;
                if (inserting) {
                    long remainder = container.insert(probe, Action.SIMULATE, automationType);
                    if (remainder > probe) {
                        throw new IllegalStateException(
                                "Mekanism energy container returned invalid insertion remainder " + remainder + " for " + probe);
                    }
                    simulated = probe - remainder;
                } else {
                    simulated = container.extract(probe, Action.SIMULATE, automationType);
                    if (simulated > probe) {
                        throw new IllegalStateException(
                                "Mekanism energy container returned invalid extraction result " + simulated + " for " + probe);
                    }
                }
                if (simulated > 0) {
                    permitted[index] = true;
                    availableJoules = saturatingAdd(availableJoules, available);
                }
            }
            if (availableJoules == 0) {
                return 0;
            }

            long transferredJoules = Math.min(requestedJoules, availableJoules);
            if (!CONVERTER.isOneToOne()) {
                transferredJoules = convertToAndBack(transferredJoules);
            }
            if (transferredJoules == 0) {
                return 0;
            }
            long transferred = CONVERTER.convertTo(transferredJoules);
            if (transferred <= 0 || transferred > amount) {
                throw new IllegalStateException(
                        "Mekanism FE conversion returned invalid transfer " + transferred + " for " + amount);
            }
            if (!simulate) {
                writeTransfer(containers, stored, capacities, permitted, transferredJoules, inserting);
            }
            return transferred;
        }

        private static boolean allowsTransfer(IEnergyStorage storage, long amount, boolean inserting) {
            int request = (int) Math.min(amount, Integer.MAX_VALUE);
            int simulated = inserting ? storage.receiveEnergy(request, true) : storage.extractEnergy(request, true);
            if (simulated < 0 || simulated > request) {
                throw new IllegalStateException(
                        "Mekanism FE route returned invalid simulation " + simulated + " for " + request);
            }
            return simulated > 0;
        }

        /**
         * Resolves stable side permissions instead of inferring them from a full or empty container.
         *
         * <p>
         * Configurable Mekanism machines expose their exact energy input/output mode here. Other sided handlers remain
         * bidirectional at planning time; their simulated transfer budgets still prevent unsupported mutations.
         * </p>
         */
        @Nullable
        private static TowerEnergyDirection resolveRouteDirection(
                                                                  IMekanismStrictEnergyHandler handler,
                                                                  @Nullable Direction side) {
            if (side == null) {
                return null;
            }
            if (handler instanceof ISideConfiguration sideConfiguration) {
                TileComponentConfig config = sideConfiguration.getConfig();
                if (config.supports(TransmissionType.ENERGY)) {
                    ISlotInfo slot = config.getSlotInfo(TransmissionType.ENERGY, side);
                    return slot == null ? null : TowerEnergyDirection.fromPermissions(slot.canOutput(), slot.canInput());
                }
            }
            return TowerEnergyDirection.BIDIRECTIONAL;
        }

        private static void writeTransfer(
                                          List<IEnergyContainer> containers,
                                          long[] stored,
                                          long[] capacities,
                                          boolean[] permitted,
                                          long amount,
                                          boolean inserting) {
            long[] expected = stored.clone();
            try {
                long remaining;
                if (inserting) {
                    remaining = insertIntoContainers(
                            containers, expected, capacities, permitted, amount, false);
                    remaining = insertIntoContainers(
                            containers, expected, capacities, permitted, remaining, true);
                } else {
                    remaining = extractFromContainers(containers, expected, permitted, amount);
                }
                if (remaining != 0) {
                    throw new IllegalStateException(
                            "Mekanism direct energy write left " + remaining + " joules untransferred");
                }
                verifyContainerState(containers, expected, capacities);
            } catch (RuntimeException exception) {
                RuntimeException rollbackFailure = rollbackContainers(containers, stored, capacities);
                IllegalStateException failure = new IllegalStateException(
                        rollbackFailure == null ? "Mekanism direct energy write failed and state was restored" : "Mekanism direct energy write failed and rollback was incomplete",
                        exception);
                if (rollbackFailure != null) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }

        private static long insertIntoContainers(
                                                 List<IEnergyContainer> containers,
                                                 long[] expected,
                                                 long[] capacities,
                                                 boolean[] permitted,
                                                 long amount,
                                                 boolean emptyContainers) {
            long remaining = amount;
            for (int index = 0; index < containers.size() && remaining > 0; index++) {
                if (!permitted[index] || (expected[index] == 0) != emptyContainers) {
                    continue;
                }
                long inserted = Math.min(remaining, capacities[index] - expected[index]);
                if (inserted > 0) {
                    expected[index] = Math.addExact(expected[index], inserted);
                    containers.get(index).setEnergy(expected[index]);
                    remaining -= inserted;
                }
            }
            return remaining;
        }

        private static long extractFromContainers(
                                                  List<IEnergyContainer> containers,
                                                  long[] expected,
                                                  boolean[] permitted,
                                                  long amount) {
            long remaining = amount;
            for (int index = 0; index < containers.size() && remaining > 0; index++) {
                if (!permitted[index]) {
                    continue;
                }
                long extracted = Math.min(remaining, expected[index]);
                if (extracted > 0) {
                    expected[index] -= extracted;
                    containers.get(index).setEnergy(expected[index]);
                    remaining -= extracted;
                }
            }
            return remaining;
        }

        private static void verifyContainerState(
                                                 List<IEnergyContainer> containers,
                                                 long[] expected,
                                                 long[] capacities) {
            for (int index = 0; index < containers.size(); index++) {
                IEnergyContainer container = containers.get(index);
                long actual = container.getEnergy();
                long capacity = container.getMaxEnergy();
                if (actual != expected[index] || capacity != capacities[index]) {
                    throw new IllegalStateException(
                            "Mekanism direct energy write produced unexpected state " + actual + "/" + capacity + " instead of " + expected[index] + "/" + capacities[index]);
                }
            }
        }

        @Nullable
        private static RuntimeException rollbackContainers(
                                                           List<IEnergyContainer> containers,
                                                           long[] stored,
                                                           long[] capacities) {
            RuntimeException failure = null;
            for (int index = containers.size() - 1; index >= 0; index--) {
                IEnergyContainer container = containers.get(index);
                try {
                    container.setEnergy(stored[index]);
                    long actual = container.getEnergy();
                    long capacity = container.getMaxEnergy();
                    if (actual != stored[index] || capacity != capacities[index]) {
                        throw new IllegalStateException(
                                "Mekanism energy rollback produced unexpected state " + actual + "/" + capacity + " instead of " + stored[index] + "/" + capacities[index]);
                    }
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            return failure;
        }

        private static long convertToAndBack(long joules) {
            long fe = CONVERTER.convertTo(joules);
            if (fe <= 0) {
                return 0;
            }
            long converted = CONVERTER.convertFrom(fe);
            double conversion = CONVERTER.getConversion();
            if (conversion >= 1 && converted % conversion > 0) {
                return CONVERTER.convertFrom(fe - 1);
            }
            return converted;
        }

        @Nullable
        private static IMekanismStrictEnergyHandler findHandler(
                                                                Level level,
                                                                BlockPos pos,
                                                                IEnergyStorage storage) {
            if (!(storage instanceof ForgeEnergyIntegration) || !(level.getBlockEntity(pos) instanceof IMekanismStrictEnergyHandler handler) || !handler.canHandleEnergy()) {
                return null;
            }
            return handler;
        }

        private static IMekanismStrictEnergyHandler requireHandler(
                                                                   Level level,
                                                                   BlockPos pos,
                                                                   IEnergyStorage storage) {
            IMekanismStrictEnergyHandler handler = findHandler(level, pos, storage);
            if (handler == null) {
                throw new IllegalStateException("Mekanism energy handler is no longer available");
            }
            return handler;
        }

        private static long saturatingAdd(long left, long right) {
            return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
        }
    }
}
