package com.fish_dan_.data_energistics.ae2.sanctum;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.DataSanctumInterfaceSchema;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

public class DataSanctumReturnInventory extends ConfigInventory {

    private boolean injectingIntoNetwork;
    private final IntSupplier capacityCardCountSupplier;

    public DataSanctumReturnInventory(@Nullable Runnable listener, IntSupplier capacityCardCountSupplier) {
        this(DataSanctumInterfaceConstants.RETURN_SLOT_COUNT, listener, capacityCardCountSupplier);
    }

    public DataSanctumReturnInventory(int size, @Nullable Runnable listener, IntSupplier capacityCardCountSupplier) {
        super(AEKeyTypes.getAll(),
                null,
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                true);
        this.capacityCardCountSupplier = capacityCardCountSupplier;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert() {
        return !this.injectingIntoNetwork;
    }

    @Override
    public long getMaxAmount(AEKey key) {
        long capacity = getConfiguredCapacity(key, getCapacityCardCount());
        return capacity <= 0 ? 0 : capacity;
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack != null) {
            if (!isSupportedType(stack.what()) || stack.amount() <= 0) {
                stack = null;
            } else {
                long maxAmount = getConfiguredCapacity(stack.what(), getCapacityCardCount());
                if (stack.amount() > maxAmount) {
                    stack = new GenericStack(stack.what(), maxAmount);
                }
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

        AEKey currentWhat = getKey(slot);
        long currentAmount = getAmount(slot);
        if (currentWhat != null && !currentWhat.equals(what)) {
            return 0;
        }

        long capacity = getConfiguredCapacity(what, getCapacityCardCount());
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

    public boolean injectIntoNetwork(MEStorage storage, IActionSource source) {
        boolean didSomething = false;
        this.injectingIntoNetwork = true;

        try {
            for (int slot = 0; slot < this.stacks.length; slot++) {
                GenericStack stack = this.stacks[slot];
                if (stack == null) {
                    continue;
                }

                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
                if (inserted <= 0) {
                    continue;
                }

                if (inserted >= stack.amount()) {
                    this.stacks[slot] = null;
                } else {
                    this.stacks[slot] = new GenericStack(stack.what(), stack.amount() - inserted);
                }
                didSomething = true;
            }
        } finally {
            this.injectingIntoNetwork = false;
        }

        if (didSomething) {
            onChange();
        }
        return didSomething;
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (GenericStack stack : this.stacks) {
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
    }

    private int getCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.capacityCardCountSupplier.getAsInt()));
    }

    private static long getConfiguredCapacity(AEKey key, int capacityCardCount) {
        DataSanctumInterfaceSchema settings = DataEnergisticsConfiguration.INSTANCE.machines.dataSanctumInterface;
        long baseCapacity;
        if (key.getType() == AEKeyType.fluids()) {
            baseCapacity = safeMultiply(settings.returnFluidBuckets, AEFluidKey.AMOUNT_BUCKET);
        } else {
            baseCapacity = settings.returnItemLimit;
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
}
