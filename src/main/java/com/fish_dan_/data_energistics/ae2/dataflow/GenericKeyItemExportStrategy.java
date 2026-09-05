package com.fish_dan_.data_energistics.ae2.dataflow;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DEAE2Keys;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.CowMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class GenericKeyItemExportStrategy implements StackExportStrategy {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final AEKeyType keyType;
    private final BlockCapabilityCache<GenericInternalInventory, Direction> genericInventoryCache;
    private final BlockCapabilityCache<IItemHandler, Direction> cache;

    public GenericKeyItemExportStrategy(AEKeyType keyType, ServerLevel level, BlockPos fromPos, Direction fromSide) {
        this.keyType = keyType;
        this.genericInventoryCache = BlockCapabilityCache.create(AECapabilities.GENERIC_INTERNAL_INV, level, fromPos, fromSide);
        this.cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, fromPos, fromSide);
    }

    public static void registerMissingStrategies() {
        for (AEKeyType type : DEAE2Keys.types()) {
            if (type == AEKeyType.items() || type == AEKeyType.fluids()) {
                continue;
            }
            registerIfMissing(type);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerIfMissing(AEKeyType type) {
        CowMap<AEKeyType, StackExportStrategy.Factory> strategies = StackWorldBehaviors.exportStrategies;
        synchronized (strategies) {
            if (strategies.map.containsKey(type)) {
                return;
            }

            Map<AEKeyType, StackExportStrategy.Factory> updated = new IdentityHashMap<>(strategies.map);
            updated.put(type, (level, fromPos, fromSide) -> new GenericKeyItemExportStrategy(type, level, fromPos, fromSide));
            strategies.map = Collections.unmodifiableMap(updated);
        }
    }

    @Override
    public long transfer(StackTransferContext context, AEKey what, long maxAmount) {
        AEKey normalized = normalizeKey(what);
        if (normalized == null || maxAmount <= 0) {
            return 0L;
        }

        long inserted = insertIntoTarget(normalized, maxAmount, Actionable.SIMULATE);
        if (inserted <= 0L) {
            return 0L;
        }

        var inv = context.getInternalStorage();
        long extracted = StorageHelper.poweredExtraction(
                context.getEnergySource(),
                inv.getInventory(),
                normalized,
                maxAmount,
                context.getActionSource(),
                Actionable.SIMULATE);

        inserted = insertIntoTarget(normalized, extracted, Actionable.SIMULATE);
        if (inserted <= 0) {
            return 0L;
        }

        extracted = StorageHelper.poweredExtraction(
                context.getEnergySource(),
                inv.getInventory(),
                normalized,
                inserted,
                context.getActionSource(),
                Actionable.MODULATE);

        inserted = insertIntoTarget(normalized, extracted, Actionable.MODULATE);
        if (inserted < extracted) {
            long leftover = extracted - inserted;
            leftover -= inv.getInventory().insert(normalized, leftover, Actionable.MODULATE, context.getActionSource());
            if (leftover > 0) {
                LOGGER.error("Generic key export: adjacent block unexpectedly refused insert, voided {}x{}", leftover, normalized);
            }
        }

        return inserted;
    }

    @Override
    public long push(AEKey what, long maxAmount, Actionable mode) {
        AEKey normalized = normalizeKey(what);
        if (normalized == null || maxAmount <= 0) {
            return 0L;
        }

        return insertIntoTarget(normalized, maxAmount, mode);
    }

    private @Nullable AEKey normalizeKey(AEKey what) {
        if (what.getType() == this.keyType) {
            return what;
        }
        if (!(what instanceof AEItemKey itemKey)) {
            return null;
        }

        GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
        if (wrapped == null || wrapped.what().getType() != this.keyType) {
            return null;
        }
        return wrapped.what();
    }

    private long insertIntoTarget(AEKey what, long amount, Actionable mode) {
        GenericInternalInventory genericInventory = this.genericInventoryCache.getCapability();
        if (genericInventory != null && genericInventory.isSupportedType(what)) {
            return insert(genericInventory, what, amount, mode);
        }

        IItemHandler handler = this.cache.getCapability();
        return handler == null ? 0L : insert(handler, what, amount, mode);
    }

    private static long insert(GenericInternalInventory inventory, AEKey what, long amount, Actionable mode) {
        if (amount <= 0L) {
            return 0L;
        }

        long remaining = amount;
        inventory.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size() && remaining > 0L; slot++) {
                if (!inventory.isAllowedIn(slot, what)) {
                    continue;
                }

                long inserted = inventory.insert(slot, what, remaining, mode);
                if (inserted > 0L) {
                    remaining -= inserted;
                }
            }
        } finally {
            if (mode == Actionable.MODULATE) {
                inventory.endBatch();
            } else {
                inventory.endBatchSuppressed();
            }
        }
        return amount - remaining;
    }

    private static long insert(IItemHandler handler, AEKey what, long amount, Actionable mode) {
        if (amount <= 0) {
            return 0L;
        }

        ItemStack wrapped = GenericStack.wrapInItemStack(what, amount);
        if (wrapped.isEmpty()) {
            return 0L;
        }

        ItemStack remainder = ItemHandlerHelper.insertItem(handler, wrapped, mode.isSimulate());
        if (remainder.isEmpty()) {
            return amount;
        }

        GenericStack remaining = GenericStack.fromItemStack(remainder);
        if (remaining != null && remaining.what().equals(what)) {
            return Math.max(0L, amount - remaining.amount());
        }

        return ItemStack.matches(wrapped, remainder) ? 0L : amount;
    }
}
