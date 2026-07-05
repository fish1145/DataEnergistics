package com.fish_dan_.data_energistics.common.compartment;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Config-backed storage used by compartment menus and host-facing logic.
 *
 * <p>
 * The inventory keeps a fixed backing size so NBT and menu slots are stable, while capacity
 * cards decide which main compartment slots are writable at runtime.
 */
public class CompartmentInventory extends ConfigInventory {

    private final IntSupplier unlockedSlotCountSupplier;
    private final Predicate<AEKey> keyFilter;
    private final boolean wrappedItemOrFluidOnly;

    public CompartmentInventory(Set<AEKeyType> supportedTypes,
                                GenericStackInv.Mode mode,
                                int size,
                                @Nullable Runnable listener,
                                IntSupplier unlockedSlotCountSupplier) {
        this(supportedTypes, mode, size, listener, unlockedSlotCountSupplier, key -> true);
    }

    public CompartmentInventory(Set<AEKeyType> supportedTypes,
                                GenericStackInv.Mode mode,
                                int size,
                                @Nullable Runnable listener,
                                IntSupplier unlockedSlotCountSupplier,
                                Predicate<AEKey> keyFilter) {
        this(supportedTypes, mode, size, listener, unlockedSlotCountSupplier, keyFilter, false);
    }

    public CompartmentInventory(Set<AEKeyType> supportedTypes,
                                GenericStackInv.Mode mode,
                                int size,
                                @Nullable Runnable listener,
                                IntSupplier unlockedSlotCountSupplier,
                                Predicate<AEKey> keyFilter,
                                boolean wrappedItemOrFluidOnly) {
        super(supportedTypes, null, mode, size, listener, true);
        this.unlockedSlotCountSupplier = Objects.requireNonNull(
                unlockedSlotCountSupplier,
                "unlockedSlotCountSupplier");
        this.keyFilter = Objects.requireNonNull(keyFilter, "keyFilter");
        this.wrappedItemOrFluidOnly = wrappedItemOrFluidOnly;
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey what) {
        AEKey normalized = CompartmentKeyNormalizer.normalize(what);
        return normalized != null &&
                isSlotUnlocked(slot) &&
                isOriginalKeyAllowed(what, normalized) &&
                this.keyFilter.test(normalized) &&
                super.isAllowedIn(slot, normalized);
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        GenericStack normalized = CompartmentKeyNormalizer.normalize(stack);
        if (normalized != null && !isSlotUnlocked(slot)) {
            return;
        }
        if (normalized != null && !isOriginalStackAllowed(slot, stack, normalized)) {
            return;
        }
        super.setStack(slot, normalized);
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (!isSlotUnlocked(slot)) {
            return 0L;
        }
        AEKey normalized = CompartmentKeyNormalizer.normalize(what);
        if (normalized == null || !isOriginalKeyAllowed(what, normalized)) {
            return 0L;
        }
        return super.insert(slot, normalized, amount, mode);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        AEKey normalized = CompartmentKeyNormalizer.normalize(key);
        return normalized != null && this.keyFilter.test(normalized) ? Long.MAX_VALUE : 0L;
    }

    public boolean isSlotUnlocked(int slot) {
        return slot >= 0 && slot < Math.min(size(), this.unlockedSlotCountSupplier.getAsInt());
    }

    private boolean isOriginalStackAllowed(int slot, @Nullable GenericStack original, GenericStack normalized) {
        return normalized.what() != null &&
                (original == null || isOriginalKeyAllowed(original.what(), normalized.what())) &&
                this.keyFilter.test(normalized.what()) &&
                super.isAllowedIn(slot, normalized.what());
    }

    private boolean isOriginalKeyAllowed(AEKey original, AEKey normalized) {
        if (!this.wrappedItemOrFluidOnly) {
            return true;
        }
        if (original instanceof AEItemKey itemKey) {
            GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
            return wrapped != null && wrapped.what() != null;
        }
        return !(normalized instanceof AEItemKey) && !(normalized instanceof AEFluidKey);
    }

    public static CompartmentInventory storage(int size, Runnable listener, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                AEKeyTypes.getAll(),
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                unlockedSlotCountSupplier);
    }

    public static CompartmentInventory itemStorage(int size, Runnable listener, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                Set.of(AEKeyType.items()),
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                unlockedSlotCountSupplier);
    }

    public static CompartmentInventory patternStorage(int size, Runnable listener, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                Set.of(AEKeyType.items()),
                GenericStackInv.Mode.STORAGE,
                size,
                listener,
                unlockedSlotCountSupplier,
                key -> key instanceof AEItemKey itemKey && PatternDetailsHelper.isEncodedPattern(itemKey.toStack()));
    }

    public static CompartmentInventory config(int size, Runnable listener, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                AEKeyTypes.getAll(),
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener,
                unlockedSlotCountSupplier);
    }

    public static CompartmentInventory fluidConfig(Runnable listener) {
        return fluidConfig(listener, 1);
    }

    public static CompartmentInventory fluidConfig(Runnable listener, int size) {
        return fluidConfig(listener, size, () -> size);
    }

    public static CompartmentInventory fluidConfig(Runnable listener, int size, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                Set.of(AEKeyType.fluids()),
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener,
                unlockedSlotCountSupplier);
    }

    public static CompartmentInventory keyConfig(Runnable listener) {
        return keyConfig(listener, 1, () -> 1);
    }

    public static CompartmentInventory keyConfig(Runnable listener, int size, IntSupplier unlockedSlotCountSupplier) {
        return new CompartmentInventory(
                AEKeyTypes.getAll(),
                GenericStackInv.Mode.CONFIG_STACKS,
                size,
                listener,
                unlockedSlotCountSupplier,
                key -> key != null,
                true);
    }
}
