package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.key.EchoKeyType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persists the shared byte budget of a Data Flow cell while accepting its native Data Flow and Echo key types.
 */
public final class DataFlowCellInventory implements StorageCell {

    public static final Set<AEKeyType> SUPPORTED_KEY_TYPES = Set.of(DataFlowKeyType.TYPE, EchoKeyType.TYPE);

    private static final int MAX_STORED_TYPES = 63;
    private static final int AMOUNT_PER_BYTE = DataFlowKeyType.TYPE.getAmountPerByte();

    private final ItemStack stack;
    private final IBasicCellItem cellItem;
    private final @Nullable ISaveProvider container;
    private final Map<AEKey, Long> storedAmounts;
    private final ConfigInventory configInventory;
    private final IUpgradeInventory upgrades;
    private final IPartitionList partitionList;
    private final IncludeExclude partitionListMode;
    private final int totalTypes;
    private final boolean hasVoidUpgrade;
    private boolean persisted = true;

    /**
     * Creates the inventory view backed by a single regular or portable Data Flow cell stack.
     */
    public DataFlowCellInventory(ItemStack stack, @Nullable ISaveProvider container) {
        this.stack = Objects.requireNonNull(stack, "Cannot create a Data Flow cell inventory for a null stack");
        if (!(stack.getItem() instanceof IBasicCellItem basicCellItem)) {
            throw new IllegalArgumentException("Data Flow cell stack must implement IBasicCellItem");
        }

        this.cellItem = basicCellItem;
        this.container = container;
        this.storedAmounts = loadStoredAmounts(stack);
        this.configInventory = cellItem.getConfigInventory(stack);
        this.upgrades = cellItem.getUpgrades(stack);
        this.totalTypes = Math.min(
                SUPPORTED_KEY_TYPES.size(),
                Math.min(MAX_STORED_TYPES, Math.max(1, cellItem.getTotalTypes(stack))));
        this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);

        var partitionBuilder = IPartitionList.builder();
        boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
        boolean fuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        if (fuzzy) {
            partitionBuilder.fuzzyMode(cellItem.getFuzzyMode(stack));
        }
        partitionBuilder.addAll(configInventory.keySet());
        this.partitionList = partitionBuilder.build();
        this.partitionListMode = hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0L || !supports(what) || !partitionList.matchesFilter(what, partitionListMode) || cellItem.isBlackListed(stack, what)) {
            return 0L;
        }

        long inserted = Math.min(amount, getRemainingAmount(what));
        if (mode == Actionable.MODULATE && inserted > 0L) {
            storedAmounts.merge(what, inserted, Long::sum);
            saveChanges();
        }

        if (!isPreformatted() && hasVoidUpgrade && !canHoldNewItem()) {
            return storedAmounts.containsKey(what) ? amount : inserted;
        }
        return hasVoidUpgrade ? amount : inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        long stored = storedAmounts.getOrDefault(what, 0L);
        long extracted = Math.min(amount, stored);
        if (mode == Actionable.MODULATE && extracted > 0L) {
            if (extracted == stored) {
                storedAmounts.remove(what);
            } else {
                storedAmounts.put(what, stored - extracted);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Map.Entry<AEKey, Long> entry : storedAmounts.entrySet()) {
            out.add(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return storedAmounts.containsKey(input);
    }

    @Override
    public CellState getStatus() {
        if (storedAmounts.isEmpty()) {
            return CellState.EMPTY;
        }
        if (canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (canGrowStoredKey()) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return cellItem.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return cellItem.storableInStorageCell() || storedAmounts.isEmpty();
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }

        List<GenericStack> persistedStacks = new ArrayList<>(storedAmounts.size());
        for (Map.Entry<AEKey, Long> entry : storedAmounts.entrySet()) {
            if (entry.getValue() > 0L) {
                persistedStacks.add(new GenericStack(entry.getKey(), entry.getValue()));
            }
        }
        if (persistedStacks.isEmpty()) {
            stack.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            stack.set(AEComponents.STORAGE_CELL_INV, persistedStacks);
        }
        persisted = true;
    }

    /**
     * Returns the cell byte capacity shared by Data Flow and Echo.
     */
    public long getTotalBytes() {
        return cellItem.getBytes(stack);
    }

    /**
     * Returns all bytes consumed by key headers and per-key resource quantities.
     */
    public long getUsedBytes() {
        return (long) getStoredItemTypes() * getBytesPerType() + bytesForAmount(getStoredAmount(), AMOUNT_PER_BYTE);
    }

    /**
     * Returns the count of resource keys currently stored in this cell.
     */
    public int getStoredItemTypes() {
        return storedAmounts.size();
    }

    /**
     * Returns the maximum number of resource keys that this cell accepts simultaneously.
     */
    public int getTotalItemTypes() {
        return totalTypes;
    }

    public boolean isPreformatted() {
        return !partitionList.isEmpty();
    }

    public boolean isFuzzy() {
        return partitionList instanceof FuzzyPriorityList;
    }

    public IncludeExclude getPartitionListMode() {
        return partitionListMode;
    }

    public ConfigInventory getConfigInventory() {
        return configInventory;
    }

    public IUpgradeInventory getUpgradesInventory() {
        return upgrades;
    }

    private static Map<AEKey, Long> loadStoredAmounts(ItemStack stack) {
        Map<AEKey, Long> storedAmounts = new HashMap<>();
        List<GenericStack> storedStacks = stack.get(AEComponents.STORAGE_CELL_INV);
        if (storedStacks == null) {
            return storedAmounts;
        }

        for (GenericStack stored : storedStacks) {
            if (stored.amount() > 0L) {
                storedAmounts.merge(stored.what(), stored.amount(), Long::sum);
            }
        }
        return storedAmounts;
    }

    private void saveChanges() {
        persisted = false;
        if (container == null) {
            persist();
        } else {
            container.saveChanges();
        }
    }

    private boolean canHoldNewItem() {
        long freeBytes = getFreeBytes();
        return (freeBytes > getBytesPerType() || freeBytes == getBytesPerType() && getUnusedAmount() > 0L) && getStoredItemTypes() < totalTypes;
    }

    private boolean canGrowStoredKey() {
        for (AEKey key : storedAmounts.keySet()) {
            if (supports(key) && getRemainingAmount(key) > 0L) {
                return true;
            }
        }
        return false;
    }

    private long getRemainingAmount(AEKey key) {
        if (!supports(key)) {
            return 0L;
        }

        long remainingAmount = getFreeBytes() * AMOUNT_PER_BYTE + getUnusedAmount();
        long stored = storedAmounts.getOrDefault(key, 0L);
        if (stored <= 0L) {
            if (!canHoldNewItem()) {
                return 0L;
            }
            remainingAmount -= (long) getBytesPerType() * AMOUNT_PER_BYTE;
        }
        return Math.max(0L, remainingAmount);
    }

    private int getBytesPerType() {
        return cellItem.getBytesPerType(stack);
    }

    private static boolean supports(AEKey key) {
        return SUPPORTED_KEY_TYPES.contains(key.getType());
    }

    private long getFreeBytes() {
        return Math.max(0L, getTotalBytes() - getUsedBytes());
    }

    private long getStoredAmount() {
        long storedAmount = 0L;
        for (long amount : storedAmounts.values()) {
            storedAmount += amount;
        }
        return storedAmount;
    }

    private long getUnusedAmount() {
        return unusedAmountInLastByte(getStoredAmount(), AMOUNT_PER_BYTE);
    }

    private static long bytesForAmount(long amount, int amountPerByte) {
        return (amount + amountPerByte - 1L) / amountPerByte;
    }

    private static long unusedAmountInLastByte(long amount, int amountPerByte) {
        long remainder = amount % amountPerByte;
        return remainder == 0L ? 0L : amountPerByte - remainder;
    }
}
