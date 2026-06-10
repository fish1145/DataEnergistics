package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.DigitalStorageDepotSettings;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotMemoryCardData;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.Setting;
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
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPriorityHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.util.ConfigManager;
import appeng.util.ConfigMenuInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DigitalStorageDepotBlockEntity extends AENetworkedBlockEntity implements InternalInventoryHost, IConfigurableObject, IUpgradeableObject, IPriorityHost {

    public static final int STORAGE_COLUMNS = 7;
    public static final int STORAGE_ROWS = 3;
    public static final int STORAGE_SLOTS = STORAGE_COLUMNS * STORAGE_ROWS;
    public static final int UPGRADE_SLOTS = 4;
    public static final int FLUID_SLOTS = 3;
    public static final int KEY_SLOTS = 3;
    public static final int FLUID_CAPACITY = 64_000;
    public static final long KEY_CAPACITY = 64_000L;
    public static final int CAPACITY_CARD_MULTIPLIER = 4;

    private static final String STORAGE_TAG = "storage";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String FLUID_TAG_PREFIX = "stored_fluid_";
    private static final String KEY_TAG_PREFIX = "stored_key_";
    private static final String PRIORITY_TAG = "priority";
    private static final String AUTO_EXPORT_MODE_TAG = "auto_export_mode";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final String ITEM_OUTPUT_SIDES_TAG = "item_output_sides";
    private static final String FLUID_OUTPUT_SIDES_TAG = "fluid_output_sides";
    private static final String KEY_OUTPUT_SIDES_TAG = "key_output_sides";

    private final AppEngInternalInventory storage = new DepotItemInventory();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DIGITAL_STORAGE_DEPOT.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    @Getter
    private final InternalInventory externalInventory = new FilteredInternalInventory(this.storage, new SlotAccessFilter(true, true));
    @Getter
    private final IItemHandler externalItemHandler = new DepotExternalItemHandler();
    @Getter
    private final GenericInternalInventory externalKeyInventory = new DepotExternalKeyInventory();
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
    @Getter
    private final IFluidHandler externalFluidHandler = new DepotFluidHandler();
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private boolean suppressConfigSync;
    private boolean syncingFluidMenu;
    private boolean syncingKeyMenu;
    private final GenericStack[] keyStacks = new GenericStack[KEY_SLOTS];
    private final MEStorage networkStorage = new DepotStorage();
    private final IStorageProvider storageProvider = new DepotStorageProvider();
    private boolean exportingToNetwork;
    private int priority;
    @Getter
    private DataExtractorAutoExportMode autoExportMode = DataExtractorAutoExportMode.OFF;
    private final Set<Direction> itemOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> fluidOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> keyOutputSides = EnumSet.allOf(Direction.class);

    public DigitalStorageDepotBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockPos, blockState);
        this.configManager.registerSetting(DigitalStorageDepotSettings.AUTO_EXPORT_MODE, DataExtractorAutoExportMode.OFF);
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
        tryAutoExport();
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
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
            this.fluidTanks[i].setFluid(fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(Math.min(getFluidCapacity(), fluid.getAmount())));
        }
        syncMenuFluidsFromTanks();
        this.saveChanges();
        this.markForClientUpdate();
    }

    public int getFluidCapacity() {
        return computeFluidCapacity(getInstalledCapacityCardCount());
    }

    public long getKeyCapacity() {
        return computeKeyCapacity(getInstalledCapacityCardCount());
    }

    public int getItemCapacity(ItemStack stack) {
        return computeItemCapacity(stack.getMaxStackSize(), getInstalledCapacityCardCount());
    }

    public int getInstalledCapacityCardCount() {
        return Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.DIGITAL_STORAGE_DEPOT.get().asItem().getDefaultInstance();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenus.DIGITAL_STORAGE_DEPOT.get(), player, subMenu.getLocator());
    }

    @Override
    public void setPriority(int newValue) {
        if (this.priority == newValue) {
            return;
        }

        this.priority = newValue;
        this.saveChanges();
        this.markForClientUpdate();
        this.requestStorageUpdate();
    }

    public DataExtractorAutoExportMode setAutoExportMode(DataExtractorAutoExportMode mode) {
        DataExtractorAutoExportMode resolvedMode = mode == null ? DataExtractorAutoExportMode.OFF : mode;
        if (this.autoExportMode == resolvedMode) {
            return this.autoExportMode;
        }

        this.configManager.putSetting(DigitalStorageDepotSettings.AUTO_EXPORT_MODE, resolvedMode);
        return this.autoExportMode;
    }

    public Set<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        if (sides.isEmpty()) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.copyOf(sides);
    }

    public void setOutputSideEnabled(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        boolean changed = enabled ? sides.add(side) : sides.remove(side);
        if (!changed) {
            return;
        }

        this.saveChanges();
        this.markForClientUpdate();
    }

    public boolean canRemoveCapacityCard(int slot) {
        ItemStack stack = this.upgrades.getStackInSlot(slot);
        if (stack.isEmpty() || !stack.is(AEItems.CAPACITY_CARD.asItem())) {
            return true;
        }

        int remainingCards = Math.max(0, getInstalledCapacityCardCount() - 1);
        int reducedFluidCapacity = computeFluidCapacity(remainingCards);
        long reducedKeyCapacity = computeKeyCapacity(remainingCards);
        for (ItemStack storedStack : this.storage) {
            if (!storedStack.isEmpty() && storedStack.getCount() > computeItemCapacity(storedStack.getMaxStackSize(), remainingCards)) {
                return false;
            }
        }
        for (FluidTank tank : this.fluidTanks) {
            if (tank.getFluidAmount() > reducedFluidCapacity) {
                return false;
            }
        }
        for (GenericStack keyStack : this.keyStacks) {
            if (keyStack != null && keyStack.amount() > reducedKeyCapacity) {
                return false;
            }
        }
        return true;
    }

    public @Nullable GenericStack getKeyStack(int slot) {
        return this.keyStacks[slot];
    }

    public boolean exportContentsToNetwork(Player player) {
        return exportContentsToNetwork(IActionSource.ofPlayer(player));
    }

    public boolean exportContentsToNetwork(IActionSource source) {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return false;
        }

        MEStorage inventory = node.getGrid().getStorageService().getInventory();
        if (inventory == null) {
            return false;
        }

        boolean changed = false;
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

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
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
        this.suppressConfigSync = true;
        try {
            this.storage.readFromNBT(data, STORAGE_TAG, registries);
            this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
            this.priority = data.getInt(PRIORITY_TAG);
            this.autoExportMode = DataExtractorAutoExportMode.fromOrdinal(data.getInt(AUTO_EXPORT_MODE_TAG));
            syncConfigManagerAutoExportMode();
            boolean hasTypedOutputSides = data.contains(ITEM_OUTPUT_SIDES_TAG) || data.contains(FLUID_OUTPUT_SIDES_TAG) || data.contains(KEY_OUTPUT_SIDES_TAG);
            if (hasTypedOutputSides) {
                readOutputSides(data, ITEM_OUTPUT_SIDES_TAG, this.itemOutputSides);
                readOutputSides(data, FLUID_OUTPUT_SIDES_TAG, this.fluidOutputSides);
                readOutputSides(data, KEY_OUTPUT_SIDES_TAG, this.keyOutputSides);
            } else if (data.contains(OUTPUT_SIDES_TAG)) {
                Set<Direction> legacySides = EnumSet.noneOf(Direction.class);
                readOutputSides(data, OUTPUT_SIDES_TAG, legacySides);
                copyOutputSidesToAllTypes(legacySides.isEmpty() ? EnumSet.allOf(Direction.class) : legacySides);
            } else {
                copyOutputSidesToAllTypes(EnumSet.allOf(Direction.class));
            }
            for (int i = 0; i < FLUID_SLOTS; i++) {
                this.fluidTanks[i].readFromNBT(registries, data.getCompound(FLUID_TAG_PREFIX + i));
            }
            for (int i = 0; i < KEY_SLOTS; i++) {
                this.keyStacks[i] = data.contains(KEY_TAG_PREFIX + i) ? GenericStack.readTag(registries, data.getCompound(KEY_TAG_PREFIX + i)) : null;
            }
            refreshDynamicCapacities();
            syncMenuFluidsFromTanks();
            syncKeyMenusFromStacks();
        } finally {
            this.suppressConfigSync = false;
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.putInt(PRIORITY_TAG, this.priority);
        data.putInt(AUTO_EXPORT_MODE_TAG, this.autoExportMode.ordinal());
        data.put(ITEM_OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
        data.put(FLUID_OUTPUT_SIDES_TAG, createOutputSidesTag(this.fluidOutputSides));
        data.put(KEY_OUTPUT_SIDES_TAG, createOutputSidesTag(this.keyOutputSides));
        data.put(OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
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
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        builder.set(ModDataComponents.DIGITAL_STORAGE_DEPOT_OUTPUT_SETTINGS.get(), new DigitalStorageDepotMemoryCardData(
                this.autoExportMode.ordinal(),
                encodeOutputSides(this.itemOutputSides),
                encodeOutputSides(this.fluidOutputSides),
                encodeOutputSides(this.keyOutputSides)));
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        DigitalStorageDepotMemoryCardData outputSettings = input.get(ModDataComponents.DIGITAL_STORAGE_DEPOT_OUTPUT_SETTINGS.get());
        if (outputSettings != null) {
            applyOutputSettings(outputSettings);
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
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.storage.clear();
        this.upgrades.clear();
        for (int i = 0; i < FLUID_SLOTS; i++) {
            this.fluidTanks[i].setFluid(FluidStack.EMPTY);
        }
        Arrays.fill(this.keyStacks, null);
        syncKeyMenusFromStacks();
    }

    private void onUpgradesChanged() {
        refreshDynamicCapacities();
        this.saveChanges();
        this.markForClientUpdate();
        this.requestStorageUpdate();
    }

    private void refreshDynamicCapacities() {
        for (int i = 0; i < this.storage.size(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            this.storage.setMaxStackSize(i, stack.isEmpty() ? computeItemCapacity(Item.ABSOLUTE_MAX_STACK_SIZE, getInstalledCapacityCardCount()) : getItemCapacity(stack));
        }
        for (FluidTank tank : this.fluidTanks) {
            tank.setCapacity(getFluidCapacity());
        }
        for (GenericStackInv inv : this.fluidMenuInventories) {
            inv.setCapacity(AEKeyType.fluids(), getFluidCapacity());
        }
        for (GenericStackInv inv : this.keyMenuInventories) {
            applyKeyCapacities(inv, getKeyCapacity());
        }
        syncMenuFluidsFromTanks();
        syncKeyMenusFromStacks();
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

    private void tryAutoExport() {
        if (this.autoExportMode == DataExtractorAutoExportMode.OFF) {
            return;
        }

        if (this.autoExportMode == DataExtractorAutoExportMode.AE) {
            exportContentsToNetwork(IActionSource.ofMachine(this));
            return;
        }

        exportItemsToAdjacentHandlers();
        exportFluidsToAdjacentHandlers();
        exportKeysToAdjacentHandlers();
    }

    private void exportItemsToAdjacentHandlers() {
        List<IItemHandler> handlers = getAdjacentItemHandlers(this.itemOutputSides);
        if (handlers.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int slot = 0; slot < this.storage.size(); slot++) {
            ItemStack stack = this.storage.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remaining = stack.copy();
            for (IItemHandler handler : handlers) {
                if (remaining.isEmpty()) {
                    break;
                }
                remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
            }

            if (remaining.getCount() == stack.getCount()) {
                continue;
            }

            this.storage.setItemDirect(slot, remaining);
            changed = true;
        }

        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }
    }

    private void exportFluidsToAdjacentHandlers() {
        List<IFluidHandler> handlers = getAdjacentFluidHandlers(this.fluidOutputSides);
        if (handlers.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (FluidTank tank : this.fluidTanks) {
            FluidStack current = tank.getFluid();
            if (current.isEmpty()) {
                continue;
            }

            FluidStack remaining = current.copy();
            for (IFluidHandler handler : handlers) {
                if (remaining.isEmpty()) {
                    break;
                }

                int filled = handler.fill(remaining, FluidAction.EXECUTE);
                if (filled <= 0) {
                    continue;
                }
                remaining.shrink(filled);
            }

            if (remaining.getAmount() == current.getAmount()) {
                continue;
            }

            tank.setFluid(remaining.isEmpty() ? FluidStack.EMPTY : remaining);
            changed = true;
        }

        if (changed) {
            syncMenuFluidsFromTanks();
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }
    }

    private void exportKeysToAdjacentHandlers() {
        List<IItemHandler> handlers = getAdjacentItemHandlers(this.keyOutputSides);
        if (handlers.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < KEY_SLOTS; i++) {
            GenericStack stack = this.keyStacks[i];
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }

            ItemStack wrapped = GenericStack.wrapInItemStack(stack.what(), stack.amount());
            ItemStack remaining = insertIntoAdjacentHandlers(wrapped, handlers);
            if (ItemStack.matches(wrapped, remaining)) {
                continue;
            }

            GenericStack remainingStack = GenericStack.fromItemStack(remaining);
            this.keyStacks[i] = remainingStack == null || remainingStack.what() == null || remainingStack.amount() <= 0 ? null : clampKeyStack(remainingStack);
            changed = true;
        }

        if (changed) {
            syncKeyMenusFromStacks();
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }
    }

    private ItemStack insertIntoAdjacentHandlers(ItemStack stack, List<IItemHandler> handlers) {
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
        }
        return remaining;
    }

    private List<IItemHandler> getAdjacentItemHandlers(Set<Direction> outputSides) {
        if (this.level == null) {
            return List.of();
        }

        List<IItemHandler> handlers = new java.util.ArrayList<>();
        for (Direction direction : outputSides) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            IItemHandler handler = this.level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers.isEmpty() ? List.of() : List.copyOf(handlers);
    }

    private List<IFluidHandler> getAdjacentFluidHandlers(Set<Direction> outputSides) {
        if (this.level == null) {
            return List.of();
        }

        List<IFluidHandler> handlers = new java.util.ArrayList<>();
        for (Direction direction : outputSides) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            IFluidHandler handler = this.level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers.isEmpty() ? List.of() : List.copyOf(handlers);
    }

    private void requestStorageUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    private void syncConfigManagerAutoExportMode() {
        if (this.configManager.getSetting(DigitalStorageDepotSettings.AUTO_EXPORT_MODE) != this.autoExportMode) {
            this.configManager.putSetting(DigitalStorageDepotSettings.AUTO_EXPORT_MODE, this.autoExportMode);
        }
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        if (this.suppressConfigSync || setting != DigitalStorageDepotSettings.AUTO_EXPORT_MODE) {
            return;
        }

        DataExtractorAutoExportMode resolvedMode = manager.getSetting(DigitalStorageDepotSettings.AUTO_EXPORT_MODE);
        if (this.autoExportMode == resolvedMode) {
            return;
        }

        this.autoExportMode = resolvedMode;
        this.saveChanges();
        this.markForClientUpdate();
    }

    private Set<Direction> getOutputSidesInternal(DigitalStorageDepotOutputType outputType) {
        return switch (outputType) {
            case ITEMS -> this.itemOutputSides;
            case FLUIDS -> this.fluidOutputSides;
            case KEYS -> this.keyOutputSides;
        };
    }

    private void applyOutputSettings(DigitalStorageDepotMemoryCardData outputSettings) {
        boolean changed = false;
        DataExtractorAutoExportMode autoExportMode = DataExtractorAutoExportMode.fromOrdinal(outputSettings.autoExportModeOrdinal());
        if (this.autoExportMode != autoExportMode) {
            this.autoExportMode = autoExportMode;
            syncConfigManagerAutoExportMode();
            changed = true;
        }
        changed |= replaceOutputSides(DigitalStorageDepotOutputType.ITEMS, decodeOutputSides(outputSettings.itemOutputSidesMask()));
        changed |= replaceOutputSides(DigitalStorageDepotOutputType.FLUIDS, decodeOutputSides(outputSettings.fluidOutputSidesMask()));
        changed |= replaceOutputSides(DigitalStorageDepotOutputType.KEYS, decodeOutputSides(outputSettings.keyOutputSidesMask()));
        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }
    }

    private boolean replaceOutputSides(DigitalStorageDepotOutputType outputType, Set<Direction> updatedSides) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        if (sides.equals(updatedSides)) {
            return false;
        }

        sides.clear();
        sides.addAll(updatedSides);
        return true;
    }

    private void copyOutputSidesToAllTypes(Set<Direction> sides) {
        this.itemOutputSides.clear();
        this.fluidOutputSides.clear();
        this.keyOutputSides.clear();
        this.itemOutputSides.addAll(sides);
        this.fluidOutputSides.addAll(sides);
        this.keyOutputSides.addAll(sides);
    }

    private static void readOutputSides(CompoundTag data, String tagName, Set<Direction> target) {
        target.clear();
        if (!data.contains(tagName)) {
            return;
        }

        for (Tag name : data.getList(tagName, Tag.TAG_STRING)) {
            Direction side = Direction.byName(name.getAsString());
            if (side != null) {
                target.add(side);
            }
        }
    }

    private static ListTag createOutputSidesTag(Set<Direction> sides) {
        ListTag tag = new ListTag();
        for (Direction side : sides) {
            tag.add(StringTag.valueOf(side.getName()));
        }
        return tag;
    }

    private static int encodeOutputSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static Set<Direction> decodeOutputSides(int mask) {
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                sides.add(side);
            }
        }
        return sides;
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
        inv.setCapacity(AEKeyType.fluids(), getFluidCapacity());
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
                    return (current == null || current.amount() <= 0 || current.what().equals(what)) && !conflictsWithOtherKeys(slotIndex, what);
                });
            }
        };
        applyKeyCapacities(inv, getKeyCapacity());
        return inv;
    }

    private static void applyKeyCapacities(GenericStackInv inv, long capacity) {
        for (AEKeyType type : AEKeyTypes.getAll()) {
            inv.setCapacity(type, capacity);
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
            if (stack != null && stack.what() != null && stack.amount() > 0 && conflictsWithOtherKeys(slotIndex, stack.what())) {
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

    private @Nullable GenericStack clampKeyStack(GenericStack stack) {
        AEKey what = stack.what();
        return what == null ? null : new GenericStack(what, Math.min(getKeyCapacity(), stack.amount()));
    }

    private long insertExternalKey(GenericStack stack, boolean simulate) {
        AEKey what = stack.what();
        if (!isAllowedMenuKey(what) || stack.amount() <= 0) {
            return 0L;
        }

        int matchingSlot = -1;
        int emptySlot = -1;
        for (int i = 0; i < KEY_SLOTS; i++) {
            GenericStack current = this.keyStacks[i];
            if (current != null && current.what() != null && current.amount() > 0 && current.what().equals(what)) {
                matchingSlot = i;
                break;
            }
            if (emptySlot < 0 && (current == null || current.what() == null || current.amount() <= 0)) {
                emptySlot = i;
            }
        }

        int slot = matchingSlot >= 0 ? matchingSlot : emptySlot;
        if (slot < 0 || (matchingSlot < 0 && conflictsWithOtherKeys(slot, what))) {
            return 0L;
        }

        GenericStack current = this.keyStacks[slot];
        long currentAmount = current == null ? 0L : current.amount();
        long inserted = Math.min(stack.amount(), Math.max(0L, getKeyCapacity() - currentAmount));
        if (inserted <= 0L) {
            return 0L;
        }

        if (!simulate) {
            this.keyStacks[slot] = new GenericStack(what, currentAmount + inserted);
            syncKeyMenusFromStacks();
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }

        return inserted;
    }

    private ItemStack extractExternalKey(int keySlot, int amount, boolean simulate) {
        if (keySlot < 0 || keySlot >= KEY_SLOTS || amount <= 0) {
            return ItemStack.EMPTY;
        }

        GenericStack current = this.keyStacks[keySlot];
        if (current == null || current.what() == null || current.amount() <= 0) {
            return ItemStack.EMPTY;
        }

        long extracted = Math.min(current.amount(), amount);
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }

        if (!simulate) {
            long remaining = current.amount() - extracted;
            this.keyStacks[keySlot] = remaining <= 0 ? null : new GenericStack(current.what(), remaining);
            syncKeyMenusFromStacks();
            this.saveChanges();
            this.markForClientUpdate();
            this.requestStorageUpdate();
        }

        return GenericStack.wrapInItemStack(current.what(), extracted);
    }

    public static String getFluidTagKey(int slotIndex) {
        return FLUID_TAG_PREFIX + slotIndex;
    }

    public static String getStorageTagKey() {
        return STORAGE_TAG;
    }

    public static String getUpgradesTagKey() {
        return UPGRADES_TAG;
    }

    public static String getKeyTagKey(int slotIndex) {
        return KEY_TAG_PREFIX + slotIndex;
    }

    public static int computeFluidCapacity(int capacityCardCount) {
        return (int) Math.min(Integer.MAX_VALUE, computeStorageCapacity(FLUID_CAPACITY, capacityCardCount));
    }

    public static long computeKeyCapacity(int capacityCardCount) {
        return computeStorageCapacity(KEY_CAPACITY, capacityCardCount);
    }

    public static int computeItemCapacity(int baseCapacity, int capacityCardCount) {
        return (int) Math.min(Integer.MAX_VALUE, computeStorageCapacity(baseCapacity, capacityCardCount));
    }

    private static long computeStorageCapacity(long baseCapacity, int capacityCardCount) {
        long multiplier = 1L + (long) Math.max(0, capacityCardCount) * CAPACITY_CARD_MULTIPLIER;
        if (baseCapacity > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return baseCapacity * multiplier;
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
        writeFluidToTag(registries, tag, slotIndex, stack, FLUID_CAPACITY);
    }

    public static void writeFluidToTag(HolderLookup.Provider registries, CompoundTag tag, int slotIndex, FluidStack stack, int capacity) {
        if (stack.isEmpty()) {
            tag.remove(getFluidTagKey(slotIndex));
        } else {
            FluidTank tank = new FluidTank(capacity);
            tank.setFluid(stack.copyWithAmount(Math.min(capacity, stack.getAmount())));
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

    public static boolean hasConflictingKey(GenericStack[] keys, int slotIndex, AEKey candidate) {
        if (candidate == null) {
            return false;
        }

        for (int i = 0; i < keys.length; i++) {
            if (i == slotIndex) {
                continue;
            }
            GenericStack other = keys[i];
            if (other != null && other.what() != null && other.amount() > 0 && other.what().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithOtherTanks(int slotIndex, FluidStack candidate) {
        return hasConflictingFluid(this.fluidTanksAsStacks(), slotIndex, candidate);
    }

    private boolean conflictsWithOtherKeys(int slotIndex, AEKey candidate) {
        return hasConflictingKey(this.keyStacks, slotIndex, candidate);
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
            storageMounts.mount(networkStorage, priority);
        }
    }

    private final class DepotExternalItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return storage.size() + KEY_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0) {
                return ItemStack.EMPTY;
            }
            if (slot < storage.size()) {
                return storage.getStackInSlot(slot);
            }

            int keySlot = slot - storage.size();
            if (keySlot < 0 || keySlot >= KEY_SLOTS) {
                return ItemStack.EMPTY;
            }

            GenericStack current = keyStacks[keySlot];
            return current == null || current.what() == null || current.amount() <= 0 ? ItemStack.EMPTY : GenericStack.wrapInItemStack(current.what(), current.amount());
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return stack;
            }

            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack != null && genericStack.what() != null && isAllowedMenuKey(genericStack.what())) {
                long inserted = insertExternalKey(genericStack, simulate);
                if (inserted <= 0) {
                    return stack;
                }

                long remaining = genericStack.amount() - inserted;
                return remaining <= 0 ? ItemStack.EMPTY : GenericStack.wrapInItemStack(genericStack.what(), remaining);
            }

            if (slot < 0 || slot >= storage.size()) {
                return stack;
            }

            return storage.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            if (slot < storage.size()) {
                return storage.extractItem(slot, amount, simulate);
            }

            return extractExternalKey(slot - storage.size(), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot >= 0 && slot < storage.size()) {
                return storage.getSlotLimit(slot);
            }
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && !ItemStack.matches(stack, insertItem(slot, stack.copy(), true));
        }
    }

    private final class DepotExternalKeyInventory implements GenericInternalInventory {

        private int batchDepth;
        private boolean batchDirty;

        @Override
        public int size() {
            return KEY_SLOTS;
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            return isValidKeySlot(slot) ? keyStacks[slot] : null;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? null : stack.what();
        }

        @Override
        public long getAmount(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? 0L : stack.amount();
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return isAllowedMenuKey(key) ? getKeyCapacity() : 0L;
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            return isSupportedType(keyType) ? getKeyCapacity() : 0L;
        }

        @Override
        public boolean canInsert() {
            return true;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack newStack) {
            if (!isValidKeySlot(slot)) {
                return;
            }

            GenericStack clamped = newStack;
            if (clamped != null) {
                if (!isAllowedMenuKey(clamped.what()) || conflictsWithOtherKeys(slot, clamped.what())) {
                    return;
                }
                clamped = clampKeyStack(clamped);
            }

            GenericStack current = keyStacks[slot];
            boolean changed = current == null ? clamped != null : !current.equals(clamped);
            if (!changed) {
                return;
            }

            keyStacks[slot] = clamped;
            syncKeyMenusFromStacks();
            onChange();
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type != null && type != AEKeyType.items() && type != AEKeyType.fluids();
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            if (!isValidKeySlot(slot) || !isAllowedMenuKey(what)) {
                return false;
            }

            GenericStack current = keyStacks[slot];
            return (current == null || current.what() == null || current.what().equals(what)) && !conflictsWithOtherKeys(slot, what);
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what) || amount <= 0) {
                return 0L;
            }

            GenericStack current = keyStacks[slot];
            long currentAmount = current == null ? 0L : current.amount();
            long inserted = Math.min(amount, Math.max(0L, getKeyCapacity() - currentAmount));
            if (inserted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                keyStacks[slot] = new GenericStack(what, currentAmount + inserted);
                syncKeyMenusFromStacks();
                onChange();
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            if (!isValidKeySlot(slot) || what == null || amount <= 0) {
                return 0L;
            }

            GenericStack current = keyStacks[slot];
            if (current == null || current.what() == null || !current.what().equals(what)) {
                return 0L;
            }

            long extracted = Math.min(current.amount(), amount);
            if (extracted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                long remaining = current.amount() - extracted;
                keyStacks[slot] = remaining <= 0 ? null : new GenericStack(what, remaining);
                syncKeyMenusFromStacks();
                onChange();
            }
            return extracted;
        }

        @Override
        public void beginBatch() {
            this.batchDepth++;
        }

        @Override
        public void endBatch() {
            if (this.batchDepth > 0) {
                this.batchDepth--;
            }
            if (this.batchDepth == 0 && this.batchDirty) {
                this.batchDirty = false;
                notifyExternalKeyInventoryChanged();
            }
        }

        @Override
        public void endBatchSuppressed() {
            if (this.batchDepth > 0) {
                this.batchDepth--;
            }
            if (this.batchDepth == 0) {
                this.batchDirty = false;
            }
        }

        @Override
        public void onChange() {
            if (this.batchDepth > 0) {
                this.batchDirty = true;
                return;
            }
            notifyExternalKeyInventoryChanged();
        }

        private boolean isValidKeySlot(int slot) {
            return slot >= 0 && slot < KEY_SLOTS;
        }

        private void notifyExternalKeyInventoryChanged() {
            saveChanges();
            markForClientUpdate();
            requestStorageUpdate();
        }
    }

    private final class DepotItemInventory extends AppEngInternalInventory {

        private DepotItemInventory() {
            super(DigitalStorageDepotBlockEntity.this, STORAGE_SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }

            ItemStack inSlot = getStackInSlot(slot);
            if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, stack)) {
                return stack;
            }

            int limit = computeItemCapacity(stack.getMaxStackSize(), getInstalledCapacityCardCount());
            setMaxStackSize(slot, limit);
            int currentAmount = inSlot.isEmpty() ? 0 : inSlot.getCount();
            int inserted = Math.min(stack.getCount(), Math.max(0, limit - currentAmount));
            if (inserted <= 0) {
                return stack;
            }

            if (!simulate) {
                ItemStack updated = inSlot.isEmpty() ? stack.copy() : inSlot.copy();
                updated.setCount(currentAmount + inserted);
                setItemDirect(slot, updated);
            }

            if (inserted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = getStackInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extracted = Math.min(stack.getCount(), amount);
            if (extracted <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack result = stack.copy();
            result.setCount(extracted);
            if (!simulate) {
                ItemStack remaining = stack.copy();
                remaining.shrink(extracted);
                setItemDirect(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
            }
            return result;
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
                long inserted = Math.min(amount, getKeyCapacity() - currentAmount);
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
            long inserted = Math.min(amount, getKeyCapacity());
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
        public int getCapacity() {
            return getFluidCapacity();
        }

        @Override
        protected void onContentsChanged() {
            syncMenuFluidFromTank(this.slotIndex);
            saveChanges();
            markForClientUpdate();
            requestStorageUpdate();
        }
    }

    private record SlotAccessFilter(boolean allowInsert, boolean allowExtract) implements IAEItemFilter {

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
