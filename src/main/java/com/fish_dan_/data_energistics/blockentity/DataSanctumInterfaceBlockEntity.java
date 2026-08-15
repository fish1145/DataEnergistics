package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumFluidPuller;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.FixedSizeMachineUpgradeInventory;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.common.memorycard.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.mixin.core.accessor.ae2.InterfaceLogicUpgradesAccessor;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumInterfaceBlockEntity extends AENetworkedBlockEntity implements DataSanctumLargeInterfaceHost {

    private static final String RETURN_INVENTORY_TAG = "returnInv";
    private static final String ACTIVE_PULL_SIDES_TAG = "active_pull_sides";
    private static final int ACTIVE_PULL_KEYS_PER_TICK = 32;
    private static final int ACTIVE_PULL_AMOUNT_PER_KEY = 4000;
    private static final int ACTIVE_PULL_FLUID_AMOUNT_PER_TICK = 4000;

    private static final IGridNodeListener<DataSanctumInterfaceBlockEntity> NODE_LISTENER = new BlockEntityNodeListener<>() {

        @Override
        public void onGridChanged(DataSanctumInterfaceBlockEntity nodeOwner, IGridNode node) {
            nodeOwner.interfaceLogic.gridChanged();
        }
    };

    private final InterfaceLogic interfaceLogic = new InterfaceLogic(
            this.getMainNode(),
            this,
            DEBlocks.DATA_SANCTUM_INTERFACE.get().asItem(),
            DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT);
    private final DataSanctumReturnInventory returnInventory = new DataSanctumReturnInventory(
            this::onReturnInventoryChanged,
            this::getInstalledCapacityCardCount);
    private final MachineSource actionSource = new MachineSource(this);
    private final EnumSet<Direction> activePullSides = EnumSet.noneOf(Direction.class);
    private final EnumMap<Direction, Integer> activePullKeyCursors = new EnumMap<>(Direction.class);
    private AdjacentBlockCapabilityCache<MEStorage> adjacentMeStorages;
    private AdjacentBlockCapabilityCache<GenericInternalInventory> adjacentGenericInventories;
    private AdjacentBlockCapabilityCache<IItemHandler> adjacentItemHandlers;
    private AdjacentBlockCapabilityCache<IFluidHandler> adjacentFluidHandlers;

    public DataSanctumInterfaceBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(), blockPos, blockState);
        expandUpgradeSlots();
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_SANCTUM_INTERFACE.get())
                .setIdlePowerUsage(0.0D);
        installInterfaceInventories();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.getMainNode().hasGridBooted()) {
            this.interfaceLogic.notifyNeighbors();
        }
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.interfaceLogic;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return DEBlocks.DATA_SANCTUM_INTERFACE.toStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(DEMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, subMenu.getLocator());
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
        data.putInt(ACTIVE_PULL_SIDES_TAG, encodeSides(this.activePullSides));
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
        decodeSides(data.getInt(ACTIVE_PULL_SIDES_TAG), this.activePullSides);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putInt(ACTIVE_PULL_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.activePullSides));
        builder.set(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
        if (settings != null) {
            applyMemoryCardSettings(settings);
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        this.interfaceLogic.addDrops(drops);
        this.returnInventory.addDrops(drops, level, pos);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.interfaceLogic.clearContent();
        this.returnInventory.clear();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.interfaceLogic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    private void installInterfaceInventories() {
        var config = DataSanctumInterfaceInventory.config(
                this.interfaceLogic::onConfigRowChanged,
                this::getInstalledCapacityCardCount);
        var storage = DataSanctumInterfaceInventory.storage(
                this.interfaceLogic::isAllowedInStorageSlot,
                this.interfaceLogic::onStorageChanged,
                this::getInstalledCapacityCardCount);
        this.interfaceLogic.config = config;
        this.interfaceLogic.storage = storage;
    }

    private void expandUpgradeSlots() {
        InterfaceLogicUpgradesAccessor accessor = (InterfaceLogicUpgradesAccessor) this.interfaceLogic;
        accessor.dataEnergistics$setUpgradesField(new FixedSizeMachineUpgradeInventory(
                DEBlocks.DATA_SANCTUM_INTERFACE.get(),
                DataSanctumInterfaceConstants.UPGRADE_SLOT_COUNT,
                accessor::dataEnergistics$invokeOnUpgradesChanged));
    }

    public DataSanctumReturnInventory getReturnInventory() {
        return this.returnInventory;
    }

    public int getInstalledCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.interfaceLogic.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD)));
    }

    public Set<Direction> getActivePullSides() {
        return this.activePullSides.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(this.activePullSides);
    }

    public void setActivePullSideEnabled(Direction side, boolean enabled) {
        if (side == null) {
            return;
        }

        boolean changed = enabled ? this.activePullSides.add(side) : this.activePullSides.remove(side);
        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        if (!settings.contains(ACTIVE_PULL_SIDES_TAG) || !MemoryCardSettingsHelper.replaceSides(this.activePullSides, settings.getInt(ACTIVE_PULL_SIDES_TAG))) {
            return;
        }

        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    public Level getInterfaceLevel() {
        return this.level;
    }

    @Override
    public BlockPos getInterfaceBlockPos() {
        return this.worldPosition;
    }

    @Override
    public Direction mapRelativeSide(RelativeSide relativeSide) {
        return getOrientation().getSide(relativeSide);
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        tryActivePull();
        injectReturnInventory();
    }

    private void onReturnInventoryChanged() {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.saveChanges();
    }

    private void injectReturnInventory() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || this.returnInventory.isEmpty()) {
            return;
        }

        this.returnInventory.injectIntoNetwork(node.getGrid().getStorageService().getInventory(), this.actionSource);
    }

    private boolean tryActivePull() {
        if (this.activePullSides.isEmpty() || !(this.level instanceof ServerLevel serverLevel) || !this.getMainNode().isActive()) {
            return false;
        }
        initializeAdjacentCapabilityCaches(serverLevel);

        int keysScanned = 0;
        for (Direction side : this.activePullSides) {
            BlockPos targetPos = this.worldPosition.relative(side);
            if (!serverLevel.hasChunkAt(targetPos)) {
                continue;
            }

            MEStorage meStorage = this.adjacentMeStorages.get(side);
            if (meStorage != null) {
                PullResult result = pullFromMeStorage(side, meStorage, keysScanned);
                keysScanned = result.keysScanned();
                if (result.changed()) {
                    return true;
                }
                if (keysScanned >= ACTIVE_PULL_KEYS_PER_TICK) {
                    return false;
                }
            }

            GenericInternalInventory genericInventory = this.adjacentGenericInventories.get(side);
            if (genericInventory != null && pullFromGenericInventory(genericInventory)) {
                return true;
            }

            IItemHandler itemHandler = this.adjacentItemHandlers.get(side);
            if (itemHandler != null && pullFromItemHandler(itemHandler)) {
                return true;
            }

            IFluidHandler fluidHandler = this.adjacentFluidHandlers.get(side);
            if (fluidHandler != null && pullFromFluidHandler(fluidHandler)) {
                return true;
            }
        }

        return false;
    }

    private void initializeAdjacentCapabilityCaches(ServerLevel level) {
        if (this.adjacentMeStorages != null) {
            return;
        }

        AdjacentBlockCapabilityCache<MEStorage> meStorages = new AdjacentBlockCapabilityCache<>(
                AECapabilities.ME_STORAGE,
                level,
                this.worldPosition,
                () -> !this.isRemoved());
        AdjacentBlockCapabilityCache<GenericInternalInventory> genericInventories = new AdjacentBlockCapabilityCache<>(
                AECapabilities.GENERIC_INTERNAL_INV,
                level,
                this.worldPosition,
                () -> !this.isRemoved());
        AdjacentBlockCapabilityCache<IItemHandler> itemHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.ItemHandler.BLOCK,
                level,
                this.worldPosition,
                () -> !this.isRemoved());
        AdjacentBlockCapabilityCache<IFluidHandler> fluidHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.FluidHandler.BLOCK,
                level,
                this.worldPosition,
                () -> !this.isRemoved());
        this.adjacentMeStorages = meStorages;
        this.adjacentGenericInventories = genericInventories;
        this.adjacentItemHandlers = itemHandlers;
        this.adjacentFluidHandlers = fluidHandlers;
    }

    private PullResult pullFromMeStorage(Direction side, MEStorage storage, int keysScanned) {
        var availableStacks = storage.getAvailableStacks();
        int availableKeyCount = availableStacks.size();
        if (availableKeyCount == 0) {
            this.activePullKeyCursors.remove(side);
            return new PullResult(false, keysScanned);
        }

        int remainingBudget = ACTIVE_PULL_KEYS_PER_TICK - keysScanned;
        if (remainingBudget <= 0) {
            return new PullResult(false, keysScanned);
        }

        int startIndex = Math.floorMod(this.activePullKeyCursors.getOrDefault(side, 0), availableKeyCount);
        int keysToInspect = Math.min(remainingBudget, availableKeyCount);
        var iterator = availableStacks.iterator();
        for (int skipped = 0; skipped < startIndex; skipped++) {
            iterator.next();
        }
        for (int inspected = 0; inspected < keysToInspect; inspected++) {
            if (!iterator.hasNext()) {
                iterator = availableStacks.iterator();
            }
            var stack = iterator.next();
            keysScanned++;
            this.activePullKeyCursors.put(side, (startIndex + inspected + 1) % availableKeyCount);

            AEKey key = stack.getKey();
            long available = stack.getLongValue();
            if (available <= 0) {
                continue;
            }

            long request = Math.min(available, ACTIVE_PULL_AMOUNT_PER_KEY);
            long canBuffer = this.returnInventory.insert(key, request, Actionable.SIMULATE, this.actionSource);
            if (canBuffer <= 0) {
                continue;
            }

            long extracted = storage.extract(key, canBuffer, Actionable.MODULATE, this.actionSource);
            if (extracted <= 0) {
                continue;
            }

            long buffered = this.returnInventory.insert(key, extracted, Actionable.MODULATE, this.actionSource);
            long leftover = extracted - buffered;
            if (leftover > 0) {
                storage.insert(key, leftover, Actionable.MODULATE, this.actionSource);
            }
            return new PullResult(true, keysScanned);
        }

        return new PullResult(false, keysScanned);
    }

    private boolean pullFromGenericInventory(GenericInternalInventory inventory) {
        if (!inventory.canExtract()) {
            return false;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            AEKey key = inventory.getKey(slot);
            long available = inventory.getAmount(slot);
            if (key == null || available <= 0) {
                continue;
            }

            long request = Math.min(available, ACTIVE_PULL_AMOUNT_PER_KEY);
            long canBuffer = this.returnInventory.insert(key, request, Actionable.SIMULATE, this.actionSource);
            if (canBuffer <= 0) {
                continue;
            }

            long extracted = inventory.extract(slot, key, canBuffer, Actionable.MODULATE);
            if (extracted <= 0) {
                continue;
            }

            long buffered = this.returnInventory.insert(key, extracted, Actionable.MODULATE, this.actionSource);
            long leftover = extracted - buffered;
            if (leftover > 0) {
                inventory.insert(slot, key, leftover, Actionable.MODULATE);
            }
            return true;
        }

        return false;
    }

    private boolean pullFromItemHandler(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulated = handler.extractItem(slot, Integer.MAX_VALUE, true);
            if (simulated.isEmpty()) {
                continue;
            }

            AEItemKey key = AEItemKey.of(simulated);
            if (key == null) {
                continue;
            }

            long canBuffer = this.returnInventory.insert(key, simulated.getCount(), Actionable.SIMULATE, this.actionSource);
            if (canBuffer <= 0) {
                continue;
            }

            ItemStack extracted = handler.extractItem(slot, (int) Math.min(Integer.MAX_VALUE, canBuffer), false);
            if (extracted.isEmpty()) {
                continue;
            }

            long buffered = this.returnInventory.insert(key, extracted.getCount(), Actionable.MODULATE, this.actionSource);
            if (buffered < extracted.getCount()) {
                ItemStack leftover = extracted.copy();
                leftover.shrink((int) Math.min(buffered, extracted.getCount()));
                ItemHandlerHelper.insertItem(handler, leftover, false);
            }
            return true;
        }

        return false;
    }

    private boolean pullFromFluidHandler(IFluidHandler handler) {
        return DataSanctumFluidPuller.pullFirstAccepted(
                handler,
                this.returnInventory,
                this.actionSource,
                ACTIVE_PULL_FLUID_AMOUNT_PER_TICK);
    }

    private static int encodeSides(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) {
            mask |= 1 << side.ordinal();
        }
        return mask;
    }

    private static void decodeSides(int mask, Set<Direction> target) {
        target.clear();
        for (Direction side : Direction.values()) {
            if ((mask & (1 << side.ordinal())) != 0) {
                target.add(side);
            }
        }
    }

    private record PullResult(boolean changed, int keysScanned) {}
}
