package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.DataSanctumInterface;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeySlotFilter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

public class DataSanctumInterfaceInventory extends ConfigInventory {

    private final IntSupplier capacityCardCountSupplier;

    public DataSanctumInterfaceInventory(Set<AEKeyType> supportedTypes,
                                         @Nullable AEKeySlotFilter slotFilter,
                                         GenericStackInv.Mode mode,
                                         int size,
                                         @Nullable Runnable listener,
                                         IntSupplier capacityCardCountSupplier) {
        super(supportedTypes, slotFilter, mode, size, listener, true);
        this.capacityCardCountSupplier = capacityCardCountSupplier;
    }

    @Override
    public long getMaxAmount(AEKey key) {
        long capacity = getConfiguredCapacity(key, getCapacityCardCount());
        return capacity <= 0 ? 0 : capacity;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack != null) {
            if (!isSupportedType(stack.what())) {
                return;
            }
            boolean typesOnly = getMode() == Mode.CONFIG_TYPES;
            if (typesOnly && stack.amount() != 0) {
                stack = new GenericStack(stack.what(), 0);
            } else if (!typesOnly && stack.amount() <= 0) {
                if (getMode() == Mode.CONFIG_STACKS && getStack(slot) == null) {
                    stack = new GenericStack(stack.what(), 1);
                } else {
                    stack = null;
                }
            }
        }

        if (stack != null) {
            long maxAmount = getConfiguredCapacity(stack.what(), getCapacityCardCount());
            if (stack.amount() > maxAmount) {
                stack = new GenericStack(stack.what(), maxAmount);
            }
        }
        if (!Objects.equals(this.stacks[slot], stack)) {
            this.stacks[slot] = stack;
            onChange();
        }
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount >= 0");
        }

        if (!canInsert() || !isAllowedIn(slot, what)) {
            return 0;
        }

        long capacity = getConfiguredCapacity(what, getCapacityCardCount());
        AEKey currentWhat = getKey(slot);
        long currentAmount = getAmount(slot);
        if (currentWhat != null && !currentWhat.equals(what)) {
            return 0;
        }

        long insertable = Math.min(amount, Math.max(0, capacity - currentAmount));
        if (insertable <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            setStack(slot, new GenericStack(what, currentAmount + insertable));
            return Math.max(0, getAmount(slot) - currentAmount);
        }
        return insertable;
    }

    private int getCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.capacityCardCountSupplier.getAsInt()));
    }

    private static long getConfiguredCapacity(AEKey key, int capacityCardCount) {
        DataSanctumInterface settings = DataEnergisticsConfiguration.INSTANCE.dataSanctumInterface();
        long baseCapacity;
        if (key.getType() == AEKeyType.items()) {
            baseCapacity = settings.itemLimit();
        } else if (key.getType() == AEKeyType.fluids()) {
            baseCapacity = safeMultiply(settings.fluidBuckets(), AEFluidKey.AMOUNT_BUCKET);
        } else {
            baseCapacity = settings.itemLimit();
        }
        return applyCapacityCards(baseCapacity, capacityCardCount);
    }

    private static long applyCapacityCards(long baseCapacity, int capacityCardCount) {
        return safeMultiply(baseCapacity, 1L << capacityCardCount);
    }

    private static long safeMultiply(long value, long multiplier) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    public static DataSanctumInterfaceInventory config(Runnable listener, IntSupplier capacityCardCountSupplier) {
        return config(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, listener, capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory config(int size, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                null,
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener,
                capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory storage(AEKeySlotFilter slotFilter, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return storage(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, slotFilter, listener, capacityCardCountSupplier);
    }

    public static DataSanctumInterfaceInventory storage(int size, AEKeySlotFilter slotFilter, Runnable listener, IntSupplier capacityCardCountSupplier) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                slotFilter,
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                capacityCardCountSupplier);
    }
}
