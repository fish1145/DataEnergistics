package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataSanctumFluidPuller;
import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.ae2.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.ae2.FixedSizeMachineUpgradeInventory;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.mixin.core.InterfaceLogicTickAccessor;
import com.fish_dan_.data_energistics.mixin.core.InterfaceLogicUpgradesAccessor;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
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
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import appeng.util.SettingsFrom;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumInterfacePart extends AEBasePart implements DataSanctumLargeInterfaceHost, IGridTickable {

    private static final String RETURN_INVENTORY_TAG = "returnInv";
    private static final String ACTIVE_PULL_ENABLED_TAG = "active_pull_enabled";
    private static final String ACTIVE_PULL_SIDES_TAG = "active_pull_sides";
    private static final int ACTIVE_PULL_KEYS_PER_TICK = 32;
    private static final int ACTIVE_PULL_AMOUNT_PER_KEY = 4000;
    private static final int ACTIVE_PULL_FLUID_AMOUNT_PER_TICK = 4000;
    private static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "part/data_sanctum_interface_base");

    private static final IGridNodeListener<DataSanctumInterfacePart> NODE_LISTENER = new NodeListener<>() {

        @Override
        public void onGridChanged(DataSanctumInterfacePart nodeOwner, IGridNode node) {
            super.onGridChanged(nodeOwner, node);
            nodeOwner.interfaceLogic.gridChanged();
        }
    };

    @PartModels
    private static final PartModel MODELS_OFF;
    @PartModels
    private static final PartModel MODELS_ON;
    @PartModels
    private static final PartModel MODELS_HAS_CHANNEL;

    static {
        MODELS_OFF = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_off"));
        MODELS_ON = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_on"));
        MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                "part/data_sanctum_interface_has_channel"));
    }

    private final InterfaceLogic interfaceLogic = createLogic();
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
    private boolean activePullEnabled;

    public DataSanctumInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        expandUpgradeSlots();
        installInterfaceInventories();
        getMainNode().addService(IGridTickable.class, this);
    }

    protected InterfaceLogic createLogic() {
        return new InterfaceLogic(
                getMainNode(),
                this,
                getPartItem().asItem(),
                DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER)
                .setIdlePowerUsage(0.0D)
                .addService(IGridTickable.class, this);
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.interfaceLogic.notifyNeighbors();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
        decodeSides(data.getInt(ACTIVE_PULL_SIDES_TAG), this.activePullSides);
        this.activePullEnabled = data.contains(ACTIVE_PULL_ENABLED_TAG) ? data.getBoolean(ACTIVE_PULL_ENABLED_TAG) : !this.activePullSides.isEmpty();
        normalizeActivePullState();
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
        data.putBoolean(ACTIVE_PULL_ENABLED_TAG, this.activePullEnabled);
        data.putInt(ACTIVE_PULL_SIDES_TAG, encodeSides(getActivePullSides()));
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putInt(ACTIVE_PULL_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(getActivePullSides()));
        builder.set(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
        if (settings != null) {
            applyMemoryCardSettings(settings);
        }
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        this.interfaceLogic.addDrops(drops);
        Level level = getInterfaceLevel();
        if (level != null) {
            this.returnInventory.addDrops(drops, level, getInterfaceBlockPos());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.interfaceLogic.clearContent();
        this.returnInventory.clear();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.interfaceLogic.getUpgrades();
        }
        return super.getSubInventory(id);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!player.getCommandSenderWorld().isClientSide()) {
            openMenu(player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenus.DATA_SANCTUM_LARGE_INTERFACE.get(), player, subMenu.getLocator());
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.interfaceLogic;
    }

    @Override
    public DataSanctumReturnInventory getReturnInventory() {
        return this.returnInventory;
    }

    @Override
    public int getInstalledCapacityCardCount() {
        return Math.max(0, Math.min(
                DataSanctumInterfaceConstants.MAX_CAPACITY_CARDS,
                this.interfaceLogic.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD)));
    }

    @Override
    public Set<Direction> getActivePullSides() {
        Direction fixedSide = getSingleActivePullSide();
        if (!this.activePullEnabled || fixedSide == null) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.of(fixedSide);
    }

    @Override
    public void setActivePullSideEnabled(Direction side, boolean enabled) {
        Direction fixedSide = getSingleActivePullSide();
        if (fixedSide == null) {
            return;
        }

        boolean changed = this.activePullEnabled != enabled;
        this.activePullEnabled = enabled;
        normalizeActivePullState();
        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        if (!settings.contains(ACTIVE_PULL_SIDES_TAG)) {
            return;
        }

        Direction fixedSide = getSingleActivePullSide();
        boolean enabled = fixedSide != null && MemoryCardSettingsHelper.decodeSides(settings.getInt(ACTIVE_PULL_SIDES_TAG)).contains(fixedSide);
        if (this.activePullEnabled == enabled) {
            return;
        }

        this.activePullEnabled = enabled;
        normalizeActivePullState();
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public boolean hasActivePullSideSelection() {
        return false;
    }

    @Override
    public @Nullable Direction getSingleActivePullSide() {
        return getSide();
    }

    @Override
    public @Nullable Level getInterfaceLevel() {
        BlockEntity blockEntity = getBlockEntity();
        return blockEntity != null ? blockEntity.getLevel() : null;
    }

    @Override
    public BlockPos getInterfaceBlockPos() {
        BlockEntity blockEntity = getBlockEntity();
        return blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;
    }

    @Override
    public Direction mapRelativeSide(RelativeSide relativeSide) {
        Direction front = getSide();
        return BlockOrientation.get(front != null ? front : Direction.NORTH).getSide(relativeSide);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getPartItem().asItem());
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!isActive()) {
            return TickRateModulation.SLEEP;
        }

        boolean pulled = tryActivePull();
        injectReturnInventory();
        InterfaceLogicTickAccessor logicTickAccessor = (InterfaceLogicTickAccessor) this.interfaceLogic;
        boolean stocked = logicTickAccessor.dataEnergistics$invokeUpdateStorage();
        boolean hasStockWork = logicTickAccessor.dataEnergistics$invokeHasWorkToDo();
        if (hasStockWork) {
            return stocked || pulled ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }
        return pulled ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    @Override
    public void saveChanges() {
        if (getHost() != null) {
            getHost().markForSave();
        }
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
                getPartItem().asItem(),
                DataSanctumInterfaceConstants.UPGRADE_SLOT_COUNT,
                accessor::dataEnergistics$invokeOnUpgradesChanged));
    }

    private void markForClientUpdate() {
        if (getHost() != null) {
            getHost().markForUpdate();
        }
    }

    private void onReturnInventoryChanged() {
        IGridNode node = this.getMainNode().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().getTickManager().alertDevice(node);
        }
        saveChanges();
    }

    private void injectReturnInventory() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || this.returnInventory.isEmpty()) {
            return;
        }

        this.returnInventory.injectIntoNetwork(node.getGrid().getStorageService().getInventory(), this.actionSource);
    }

    private boolean tryActivePull() {
        normalizeActivePullState();
        Level level = getInterfaceLevel();
        Set<Direction> activePullSides = getActivePullSides();
        if (activePullSides.isEmpty() || !(level instanceof ServerLevel serverLevel) || !this.getMainNode().isActive()) {
            return false;
        }
        initializeAdjacentCapabilityCaches(serverLevel);

        int keysScanned = 0;
        for (Direction side : activePullSides) {
            BlockPos targetPos = getInterfaceBlockPos().relative(side);
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

        BlockPos origin = getInterfaceBlockPos();
        AdjacentBlockCapabilityCache<MEStorage> meStorages = new AdjacentBlockCapabilityCache<>(
                AECapabilities.ME_STORAGE,
                level,
                origin,
                this::isCapabilityCacheValid);
        AdjacentBlockCapabilityCache<GenericInternalInventory> genericInventories =
                new AdjacentBlockCapabilityCache<>(
                        AECapabilities.GENERIC_INTERNAL_INV,
                        level,
                        origin,
                        this::isCapabilityCacheValid);
        AdjacentBlockCapabilityCache<IItemHandler> itemHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.ItemHandler.BLOCK,
                level,
                origin,
                this::isCapabilityCacheValid);
        AdjacentBlockCapabilityCache<IFluidHandler> fluidHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.FluidHandler.BLOCK,
                level,
                origin,
                this::isCapabilityCacheValid);
        this.adjacentMeStorages = meStorages;
        this.adjacentGenericInventories = genericInventories;
        this.adjacentItemHandlers = itemHandlers;
        this.adjacentFluidHandlers = fluidHandlers;
    }

    private boolean isCapabilityCacheValid() {
        BlockEntity blockEntity = getBlockEntity();
        Direction side = getSide();
        return blockEntity != null && !blockEntity.isRemoved() && side != null && getHost().getPart(side) == this;
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

    private void normalizeActivePullState() {
        Direction fixedSide = getSingleActivePullSide();
        this.activePullSides.clear();
        if (this.activePullEnabled && fixedSide != null) {
            this.activePullSides.add(fixedSide);
        }
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
