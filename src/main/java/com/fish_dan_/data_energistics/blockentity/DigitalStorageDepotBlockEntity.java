package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DigitalStorageDepotBlockEntity extends AENetworkedBlockEntity implements InternalInventoryHost, IUpgradeableObject {

    public static final int STORAGE_COLUMNS = 7;
    public static final int STORAGE_ROWS = 3;
    public static final int STORAGE_SLOTS = STORAGE_COLUMNS * STORAGE_ROWS;
    public static final int UPGRADE_SLOTS = 4;
    public static final int FLUID_SLOTS = 3;
    public static final int KEY_SLOTS = 3;
    public static final int FLUID_CAPACITY = 64_000;
    public static final long KEY_CAPACITY = 64_000L;

    private static final String STORAGE_TAG = "storage";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String FLUID_TAG_PREFIX = "stored_fluid_";
    private static final String KEY_TAG_PREFIX = "stored_key_";

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DIGITAL_STORAGE_DEPOT.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final InternalInventory externalInventory = new FilteredInternalInventory(this.storage, new SlotAccessFilter(true, true));
    private final FluidTank[] fluidTanks = new FluidTank[] {
            new SyncFluidTank(0, FLUID_CAPACITY),
            new SyncFluidTank(1, FLUID_CAPACITY),
            new SyncFluidTank(2, FLUID_CAPACITY)
    };
    private final GenericStackInv[] fluidMenuInventories = new GenericStackInv[] {
            createFluidMenuInventory(0),
            createFluidMenuInventory(1),
            createFluidMenuInventory(2)
    };
    private final GenericStackInv[] keyMenuInventories = new GenericStackInv[] {
            createKeyMenuInventory(0),
            createKeyMenuInventory(1),
            createKeyMenuInventory(2)
    };
    private final IFluidHandler externalFluidHandler = new DepotFluidHandler();
    private boolean syncingFluidMenu;
    private boolean syncingKeyMenu;
    private final GenericStack[] keyStacks = new GenericStack[KEY_SLOTS];
    private final MEStorage networkStorage = new DepotStorage();
    private final IStorageProvider storageProvider = new DepotStorageProvider();
    private boolean exportingToNetwork;

    public DigitalStorageDepotBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .setVisualRepresentation(ModBlocks.DIGITAL_STORAGE_DEPOT.get())
                .setIdlePowerUsage(0.0D);
        syncMenuFluidsFromTanks();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    public void serverTick() {
        // Reserved for future storage logic.
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
    }

    public InternalInventory getExternalInventory() {
        return this.externalInventory;
    }

    public IFluidHandler getExternalFluidHandler() {
        return this.externalFluidHandler;
    }

    public ConfigMenuInventory getFluidMenuInventory(int slot) {
        return this.fluidMenuInventories[slot].createMenuWrapper();
    }

    public ConfigMenuInventory getKeyMenuInventory(int slot) {
        return this.keyMenuInventories[slot].createMenuWrapper();
    }

    public FluidStack getStoredFluid(int slot) {
        return this.fluidTanks[slot].getFluid();
    }

    public void restoreStoredFluids(FluidStack[] fluids) {
        for (int i = 0; i < FLUID_SLOTS; i++) {
            FluidStack fluid = i < fluids.length ? fluids[i] : FluidStack.EMPTY;
            this.fluidTanks[i].setFluid(fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(Math.min(FLUID_CAPACITY, fluid.getAmount())));
        }
        syncMenuFluidsFromTanks();
        this.saveChanges();
        this.markForClientUpdate();
    }

    public int getFluidCapacity() {
        return FLUID_CAPACITY;
    }

    public @Nullable GenericStack getKeyStack(int slot) {
        return this.keyStacks[slot];
    }

    public boolean exportContentsToNetwork(Player player) {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return false;
        }

        MEStorage inventory = node.getGrid().getStorageService().getInventory();
        if (inventory == null) {
            return false;
        }

        boolean changed = false;
        IActionSource source = IActionSource.ofPlayer(player);
        this.exportingToNetwork = true;
        try {
            changed |= exportItemsToNetwork(inventory, source);
            changed |= exportFluidsToNetwork(inventory, source);
            changed |= exportKeysToNetwork(inventory, source);
        } finally {
            this.exportingToNetwork = false;
        }

        if (changed) {
            syncMenuFluidsFromTanks();
            syncKeyMenusFromStacks();
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }
        return changed;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public InternalInventory getInternalInventory() {
        return this.storage;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.storage;
        }
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
        this.markForClientUpdate();
        this.requestStorageUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChanges();
        this.markForClientUpdate();
        this.requestStorageUpdate();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        for (int i = 0; i < FLUID_SLOTS; i++) {
            this.fluidTanks[i].readFromNBT(registries, data.getCompound(FLUID_TAG_PREFIX + i));
        }
        for (int i = 0; i < KEY_SLOTS; i++) {
            this.keyStacks[i] = data.contains(KEY_TAG_PREFIX + i) ? GenericStack.readTag(registries, data.getCompound(KEY_TAG_PREFIX + i)) : null;
        }
        syncMenuFluidsFromTanks();
        syncKeyMenusFromStacks();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        for (int i = 0; i < FLUID_SLOTS; i++) {
            data.put(FLUID_TAG_PREFIX + i, this.fluidTanks[i].writeToNBT(registries, new CompoundTag()));
        }
        for (int i = 0; i < KEY_SLOTS; i++) {
            GenericStack keyStack = this.keyStacks[i];
            if (keyStack != null && keyStack.what() != null && keyStack.amount() > 0) {
                data.put(KEY_TAG_PREFIX + i, GenericStack.writeTag(registries, keyStack));
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.storage) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.storage.clear();
        this.upgrades.clear();
        for (int i = 0; i < FLUID_SLOTS; i++) {
            this.fluidTanks[i].setFluid(FluidStack.EMPTY);
        }
        for (int i = 0; i < KEY_SLOTS; i++) {
            this.keyStacks[i] = null;
        }
        syncKeyMenusFromStacks();
    }

    private void onUpgradesChanged() {
        this.saveChanges();
        this.markForClientUpdate();
        this.requestStorageUpdate();
    }

    private boolean exportItemsToNetwork(MEStorage inventory, IActionSource source) {
        boolean changed = false;
        for (int slot = 0; slot < this.storage.size(); slot++) {
            ItemStack stack = this.storage.getStackInSlot(slot);
            AEItemKey itemKey = AEItemKey.of(stack);
            if (itemKey == null) {
                continue;
            }

            long inserted = inventory.insert(itemKey, stack.getCount(), Actionable.MODULATE, source);
            if (inserted <= 0) {
                continue;
            }

            ItemStack updated = stack.copy();
            updated.shrink((int) Math.min(inserted, updated.getCount()));
            this.storage.setItemDirect(slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
            changed = true;
        }
        return changed;
    }

    private boolean exportFluidsToNetwork(MEStorage inventory, IActionSource source) {
        boolean changed = false;
        for (FluidTank tank : this.fluidTanks) {
            FluidStack fluid = tank.getFluid();
            AEFluidKey fluidKey = AEFluidKey.of(fluid);
            if (fluidKey == null) {
                continue;
            }

            long inserted = inventory.insert(fluidKey, fluid.getAmount(), Actionable.MODULATE, source);
            if (inserted <= 0) {
                continue;
            }

            FluidStack updated = fluid.copy();
            updated.shrink((int) Math.min(inserted, updated.getAmount()));
            tank.setFluid(updated.isEmpty() ? FluidStack.EMPTY : updated);
            changed = true;
        }
        return changed;
    }

    private boolean exportKeysToNetwork(MEStorage inventory, IActionSource source) {
        boolean changed = false;
        for (int i = 0; i < KEY_SLOTS; i++) {
            GenericStack stack = this.keyStacks[i];
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }

            long inserted = inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
            if (inserted <= 0) {
                continue;
            }

            long remaining = stack.amount() - inserted;
            this.keyStacks[i] = remaining <= 0 ? null : new GenericStack(stack.what(), remaining);
            changed = true;
        }
        return changed;
    }

    private void requestStorageUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    private GenericStackInv createFluidMenuInventory(int slotIndex) {
        var inv = new GenericStackInv(Set.of(AEKeyType.fluids()), () -> syncTankFromMenuFluid(slotIndex), GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> {
                    if (!(what instanceof AEFluidKey fluidKey)) {
                        return true;
                    }
                    return !conflictsWithOtherTanks(slotIndex, fluidKey.toStack(1));
                });
            }
        };
        inv.setCapacity(AEKeyType.fluids(), FLUID_CAPACITY);
        return inv;
    }

    private GenericStackInv createKeyMenuInventory(int slotIndex) {
        var inv = new GenericStackInv(AEKeyTypes.getAll(), () -> syncStackFromKeyMenu(slotIndex), GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> {
                    if (!isAllowedMenuKey(what)) {
                        return false;
                    }
                    var current = this.getStack(slot);
                    return current == null || current.amount() <= 0 || current.what().equals(what);
                });
            }
        };
        applyKeyCapacities(inv);
        return inv;
    }

    private static void applyKeyCapacities(GenericStackInv inv) {
        for (AEKeyType type : AEKeyTypes.getAll()) {
            inv.setCapacity(type, KEY_CAPACITY);
        }
    }

    private static boolean isAllowedMenuKey(@Nullable AEKey what) {
        return what != null && !(what instanceof AEItemKey) && !(what instanceof AEFluidKey);
    }

    private void syncMenuFluidsFromTanks() {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            for (int i = 0; i < FLUID_SLOTS; i++) {
                syncMenuFluidFromTank(i);
            }
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private void syncMenuFluidFromTank(int slotIndex) {
        FluidStack fluid = this.fluidTanks[slotIndex].getFluid();
        if (fluid.isEmpty()) {
            this.fluidMenuInventories[slotIndex].setStack(0, null);
        } else {
            AEFluidKey key = AEFluidKey.of(fluid);
            this.fluidMenuInventories[slotIndex].setStack(0, key == null ? null : new GenericStack(key, fluid.getAmount()));
        }
    }

    private void syncTankFromMenuFluid(int slotIndex) {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            var stack = this.fluidMenuInventories[slotIndex].getStack(0);
            if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey) || stack.amount() <= 0) {
                this.fluidTanks[slotIndex].setFluid(FluidStack.EMPTY);
            } else {
                int amount = (int) Math.min(this.fluidTanks[slotIndex].getCapacity(), stack.amount());
                FluidStack newFluid = fluidKey.toStack(amount);
                if (conflictsWithOtherTanks(slotIndex, newFluid)) {
                    syncMenuFluidFromTank(slotIndex);
                    return;
                }
                this.fluidTanks[slotIndex].setFluid(newFluid);
            }
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private void syncKeyMenusFromStacks() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            for (int i = 0; i < KEY_SLOTS; i++) {
                this.keyMenuInventories[i].setStack(0, this.keyStacks[i]);
            }
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private void syncStackFromKeyMenu(int slotIndex) {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            var previous = this.keyStacks[slotIndex];
            var stack = this.keyMenuInventories[slotIndex].getStack(0);
            if (!isCompatibleKeyReplacement(previous, stack)) {
                this.keyMenuInventories[slotIndex].setStack(0, previous);
                return;
            }
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                this.keyStacks[slotIndex] = null;
            } else {
                this.keyStacks[slotIndex] = clampKeyStack(stack);
            }
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private static boolean isCompatibleKeyReplacement(@Nullable GenericStack current, @Nullable GenericStack incoming) {
        if (current == null || current.what() == null || current.amount() <= 0) {
            return true;
        }
        if (incoming == null || incoming.what() == null || incoming.amount() <= 0) {
            return true;
        }
        return current.what().equals(incoming.what());
    }

    private static @Nullable GenericStack clampKeyStack(GenericStack stack) {
        AEKey what = stack.what();
        return what == null ? null : new GenericStack(what, Math.min(KEY_CAPACITY, stack.amount()));
    }

    public static String getFluidTagKey(int slotIndex) {
        return FLUID_TAG_PREFIX + slotIndex;
    }

    public static String getKeyTagKey(int slotIndex) {
        return KEY_TAG_PREFIX + slotIndex;
    }

    public static FluidStack readFluidFromTag(HolderLookup.Provider registries, CompoundTag tag, int slotIndex) {
        CompoundTag fluidTag = tag.getCompound(getFluidTagKey(slotIndex));
        if (fluidTag.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (fluidTag.contains("Fluid")) {
            FluidTank tank = new FluidTank(FLUID_CAPACITY);
            tank.readFromNBT(registries, fluidTag);
            return tank.getFluid();
        }
        return FluidStack.parseOptional(registries, fluidTag);
    }

    public static void writeFluidToTag(HolderLookup.Provider registries, CompoundTag tag, int slotIndex, FluidStack stack) {
        if (stack.isEmpty()) {
            tag.remove(getFluidTagKey(slotIndex));
        } else {
            FluidTank tank = new FluidTank(FLUID_CAPACITY);
            tank.setFluid(stack.copyWithAmount(Math.min(FLUID_CAPACITY, stack.getAmount())));
            tag.put(getFluidTagKey(slotIndex), tank.writeToNBT(registries, new CompoundTag()));
        }
    }

    public static boolean hasConflictingFluid(FluidStack[] fluids, int slotIndex, FluidStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }

        for (int i = 0; i < fluids.length; i++) {
            if (i == slotIndex) {
                continue;
            }
            FluidStack other = fluids[i];
            if (!other.isEmpty() && FluidStack.isSameFluidSameComponents(other, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithOtherTanks(int slotIndex, FluidStack candidate) {
        return hasConflictingFluid(this.fluidTanksAsStacks(), slotIndex, candidate);
    }

    private FluidStack[] fluidTanksAsStacks() {
        FluidStack[] fluids = new FluidStack[FLUID_SLOTS];
        for (int i = 0; i < FLUID_SLOTS; i++) {
            fluids[i] = this.fluidTanks[i].getFluid();
        }
        return fluids;
    }

    private final class DepotStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            storageMounts.mount(networkStorage);
        }
    }

    private final class DepotStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (amount <= 0 || exportingToNetwork) {
                return 0L;
            }
            if (what instanceof AEItemKey itemKey) {
                return insertItem(itemKey, amount, mode);
            }
            if (what instanceof AEFluidKey fluidKey) {
                return insertFluid(fluidKey, amount, mode);
            }
            if (isAllowedMenuKey(what)) {
                return insertKey(what, amount, mode);
            }
            return 0L;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (amount <= 0) {
                return 0L;
            }
            if (what instanceof AEItemKey itemKey) {
                return extractItem(itemKey, amount, mode);
            }
            if (what instanceof AEFluidKey fluidKey) {
                return extractFluid(fluidKey, amount, mode);
            }
            if (isAllowedMenuKey(what)) {
                return extractKey(what, amount, mode);
            }
            return 0L;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (ItemStack stack : storage) {
                AEItemKey itemKey = AEItemKey.of(stack);
                if (itemKey != null) {
                    out.add(itemKey, stack.getCount());
                }
            }
            for (FluidTank tank : fluidTanks) {
                FluidStack stack = tank.getFluid();
                AEFluidKey fluidKey = AEFluidKey.of(stack);
                if (fluidKey != null) {
                    out.add(fluidKey, stack.getAmount());
                }
            }
            for (GenericStack stack : keyStacks) {
                if (stack != null && stack.what() != null && stack.amount() > 0) {
                    out.add(stack.what(), stack.amount());
                }
            }
        }

        @Override
        public Component getDescription() {
            return ModBlocks.DIGITAL_STORAGE_DEPOT.get().getName();
        }

        private long insertItem(AEItemKey what, long amount, Actionable mode) {
            long remaining = amount;
            while (remaining > 0) {
                int attempt = (int) Math.min(remaining, what.getMaxStackSize());
                ItemStack input = what.toStack(attempt);
                ItemStack overflow = storage.addItems(input, mode == Actionable.SIMULATE);
                int inserted = attempt - overflow.getCount();
                if (inserted <= 0) {
                    break;
                }
                remaining -= inserted;
            }
            return amount - remaining;
        }

        private long extractItem(AEItemKey what, long amount, Actionable mode) {
            long extracted = 0L;
            int request = (int) Math.min(amount, Integer.MAX_VALUE);
            for (int slot = 0; slot < storage.size() && request > 0; slot++) {
                ItemStack stack = storage.getStackInSlot(slot);
                if (!what.matches(stack)) {
                    continue;
                }
                ItemStack result = storage.extractItem(slot, request, mode == Actionable.SIMULATE);
                int count = result.getCount();
                extracted += count;
                request -= count;
            }
            return extracted;
        }

        private long insertFluid(AEFluidKey what, long amount, Actionable mode) {
            int requested = (int) Math.min(amount, Integer.MAX_VALUE);
            if (requested <= 0) {
                return 0L;
            }
            return externalFluidHandler.fill(what.toStack(requested), mode.getFluidAction());
        }

        private long extractFluid(AEFluidKey what, long amount, Actionable mode) {
            int requested = (int) Math.min(amount, Integer.MAX_VALUE);
            if (requested <= 0) {
                return 0L;
            }
            return externalFluidHandler.drain(what.toStack(requested), mode.getFluidAction()).getAmount();
        }

        private long insertKey(AEKey what, long amount, Actionable mode) {
            int matchingSlot = findKeySlot(what);
            if (matchingSlot >= 0) {
                GenericStack current = keyStacks[matchingSlot];
                long currentAmount = current == null ? 0L : current.amount();
                long inserted = Math.min(amount, KEY_CAPACITY - currentAmount);
                if (inserted <= 0) {
                    return 0L;
                }
                if (mode == Actionable.MODULATE) {
                    keyStacks[matchingSlot] = new GenericStack(what, currentAmount + inserted);
                    syncKeyMenusFromStacks();
                    saveChanges();
                    markForClientUpdate();
                    requestStorageUpdate();
                }
                return inserted;
            }

            int emptySlot = findEmptyKeySlot();
            if (emptySlot < 0) {
                return 0L;
            }
            long inserted = Math.min(amount, KEY_CAPACITY);
            if (mode == Actionable.MODULATE) {
                keyStacks[emptySlot] = new GenericStack(what, inserted);
                syncKeyMenusFromStacks();
                saveChanges();
                markForClientUpdate();
                requestStorageUpdate();
            }
            return inserted;
        }

        private long extractKey(AEKey what, long amount, Actionable mode) {
            int slot = findKeySlot(what);
            if (slot < 0) {
                return 0L;
            }
            GenericStack current = keyStacks[slot];
            long extracted = Math.min(amount, current.amount());
            if (extracted <= 0) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                long remaining = current.amount() - extracted;
                keyStacks[slot] = remaining <= 0 ? null : new GenericStack(what, remaining);
                syncKeyMenusFromStacks();
                saveChanges();
                markForClientUpdate();
                requestStorageUpdate();
            }
            return extracted;
        }

        private int findKeySlot(AEKey what) {
            for (int i = 0; i < KEY_SLOTS; i++) {
                GenericStack stack = keyStacks[i];
                if (stack != null && stack.what() != null && stack.amount() > 0 && stack.what().equals(what)) {
                    return i;
                }
            }
            return -1;
        }

        private int findEmptyKeySlot() {
            for (int i = 0; i < KEY_SLOTS; i++) {
                GenericStack stack = keyStacks[i];
                if (stack == null || stack.what() == null || stack.amount() <= 0) {
                    return i;
                }
            }
            return -1;
        }
    }

    private final class DepotFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return FLUID_SLOTS;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return getTank(tank).getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return getTank(tank).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return getTank(tank).isFluidValid(stack) && !conflictsWithOtherTanks(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }

            for (int i = 0; i < FLUID_SLOTS; i++) {
                FluidTank tank = getTank(i);
                if (!tank.getFluid().isEmpty() && FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                    return tank.fill(resource, action);
                }
            }

            for (int i = 0; i < FLUID_SLOTS; i++) {
                FluidTank tank = getTank(i);
                if (tank.getFluid().isEmpty() && !conflictsWithOtherTanks(i, resource)) {
                    return tank.fill(resource, action);
                }
            }

            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            for (int i = 0; i < FLUID_SLOTS; i++) {
                FluidStack drained = getTank(i).drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < FLUID_SLOTS; i++) {
                FluidStack drained = getTank(i).drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidTank getTank(int tank) {
            if (tank < 0 || tank >= FLUID_SLOTS) {
                throw new IndexOutOfBoundsException("Invalid tank index: " + tank);
            }
            return fluidTanks[tank];
        }
    }

    private final class SyncFluidTank extends FluidTank {

        private final int slotIndex;

        private SyncFluidTank(int slotIndex, int capacity) {
            super(capacity);
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return super.isFluidValid(stack) && !conflictsWithOtherTanks(this.slotIndex, stack);
        }

        @Override
        protected void onContentsChanged() {
            syncMenuFluidFromTank(this.slotIndex);
            saveChanges();
            markForClientUpdate();
            requestStorageUpdate();
        }
    }

    private static final class SlotAccessFilter implements IAEItemFilter {

        private final boolean allowInsert;
        private final boolean allowExtract;

        private SlotAccessFilter(boolean allowInsert, boolean allowExtract) {
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return this.allowInsert;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return this.allowExtract;
        }
    }
}
