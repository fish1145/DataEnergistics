package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.config.Config;

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

public class DataSanctumInterfaceInventory extends ConfigInventory {

    public DataSanctumInterfaceInventory(Set<AEKeyType> supportedTypes,
                                         @Nullable AEKeySlotFilter slotFilter,
                                         GenericStackInv.Mode mode,
                                         int size,
                                         @Nullable Runnable listener) {
        super(supportedTypes, slotFilter, mode, size, listener, true);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        long capacity = getConfiguredCapacity(key);
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
            long maxAmount = getConfiguredCapacity(stack.what());
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
        Objects.requireNonNull(what, "what");
        if (amount < 0) {
            throw new IllegalArgumentException("amount >= 0");
        }

        if (!canInsert() || !isAllowedIn(slot, what)) {
            return 0;
        }

        long capacity = getConfiguredCapacity(what);
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

    private static long getConfiguredCapacity(AEKey key) {
        if (key.getType() == AEKeyType.items()) {
            return Config.dataSanctumInterfaceItemLimit;
        }
        if (key.getType() == AEKeyType.fluids()) {
            return (long) Config.dataSanctumInterfaceFluidBuckets * AEFluidKey.AMOUNT_BUCKET;
        }
        return Config.dataSanctumInterfaceItemLimit;
    }

    public static DataSanctumInterfaceInventory config(Runnable listener) {
        return config(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, listener);
    }

    public static DataSanctumInterfaceInventory config(int size, Runnable listener) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                null,
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener);
    }

    public static DataSanctumInterfaceInventory storage(AEKeySlotFilter slotFilter, Runnable listener) {
        return storage(DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT, slotFilter, listener);
    }

    public static DataSanctumInterfaceInventory storage(int size, AEKeySlotFilter slotFilter, Runnable listener) {
        return new DataSanctumInterfaceInventory(
                AEKeyTypes.getAll(),
                slotFilter,
                GenericStackInv.Mode.STORAGE,
                size,
                listener);
    }
}
