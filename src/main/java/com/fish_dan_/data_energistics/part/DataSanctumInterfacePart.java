package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.ae2.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
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
import appeng.api.stacks.AEFluidKey;
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
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumInterfacePart extends AEBasePart implements DataSanctumLargeInterfaceHost, IGridTickable {

    private static final String RETURN_INVENTORY_TAG = "returnInv";
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

    public DataSanctumInterfacePart(IPartItem<?> partItem) {
        super(partItem);
        installInterfaceInventories();
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
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
        data.putInt(ACTIVE_PULL_SIDES_TAG, encodeSides(this.activePullSides));
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
        return this.activePullSides.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(this.activePullSides);
    }

    @Override
    public void setActivePullSideEnabled(Direction side, boolean enabled) {
        if (side == null) {
            return;
        }

        boolean changed = enabled ? this.activePullSides.add(side) : this.activePullSides.remove(side);
        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
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
            return TickRateModulation.IDLE;
        }

        tryActivePull();
        injectReturnInventory();
        return TickRateModulation.IDLE;
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
        Level level = getInterfaceLevel();
        if (this.activePullSides.isEmpty() || !(level instanceof ServerLevel serverLevel) || !this.getMainNode().isActive()) {
            return false;
        }

        int keysScanned = 0;
        for (Direction side : getActivePullSides()) {
            BlockPos targetPos = getInterfaceBlockPos().relative(side);
            if (!serverLevel.hasChunkAt(targetPos)) {
                continue;
            }

            BlockState targetState = serverLevel.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            BlockEntity targetBlockEntity = serverLevel.getBlockEntity(targetPos);
            Direction targetFace = side.getOpposite();
            MEStorage meStorage = serverLevel.getCapability(
                    AECapabilities.ME_STORAGE,
                    targetPos,
                    targetState,
                    targetBlockEntity,
                    targetFace);
            if (meStorage != null) {
                PullResult result = pullFromMeStorage(meStorage, keysScanned);
                keysScanned = result.keysScanned();
                if (result.changed()) {
                    return true;
                }
                if (keysScanned >= ACTIVE_PULL_KEYS_PER_TICK) {
                    return false;
                }
            }

            GenericInternalInventory genericInventory = serverLevel.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    targetPos,
                    targetState,
                    targetBlockEntity,
                    targetFace);
            if (genericInventory != null && pullFromGenericInventory(genericInventory)) {
                return true;
            }

            IItemHandler itemHandler = serverLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    targetState,
                    targetBlockEntity,
                    targetFace);
            if (itemHandler != null && pullFromItemHandler(itemHandler)) {
                return true;
            }

            IFluidHandler fluidHandler = serverLevel.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    targetPos,
                    targetState,
                    targetBlockEntity,
                    targetFace);
            if (fluidHandler != null && pullFromFluidHandler(fluidHandler)) {
                return true;
            }
        }

        return false;
    }

    private PullResult pullFromMeStorage(MEStorage storage, int keysScanned) {
        for (var stack : storage.getAvailableStacks()) {
            if (keysScanned++ >= ACTIVE_PULL_KEYS_PER_TICK) {
                return new PullResult(false, keysScanned);
            }

            AEKey key = stack.getKey();
            long available = stack.getLongValue();
            if (key == null || available <= 0) {
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
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack simulated = handler.drain(ACTIVE_PULL_FLUID_AMOUNT_PER_TICK, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                continue;
            }

            AEFluidKey key = AEFluidKey.of(simulated);
            if (key == null) {
                continue;
            }

            long canBuffer = this.returnInventory.insert(key, simulated.getAmount(), Actionable.SIMULATE, this.actionSource);
            if (canBuffer <= 0) {
                continue;
            }

            FluidStack request = simulated.copy();
            request.setAmount((int) Math.min(Integer.MAX_VALUE, canBuffer));
            FluidStack extracted = handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
            if (extracted.isEmpty()) {
                continue;
            }

            long buffered = this.returnInventory.insert(key, extracted.getAmount(), Actionable.MODULATE, this.actionSource);
            if (buffered < extracted.getAmount()) {
                FluidStack leftover = extracted.copy();
                leftover.setAmount(extracted.getAmount() - (int) Math.min(buffered, extracted.getAmount()));
                handler.fill(leftover, IFluidHandler.FluidAction.EXECUTE);
            }
            return true;
        }

        return false;
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
