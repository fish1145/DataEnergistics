package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.DataSanctumReturnInventory;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumBlockEntity extends AENetworkedPoweredBlockEntity implements InterfaceLogicHost {

    public static final double BASE_ENERGY_CAPACITY = 500_000.0D;
    public static final int ENERGY_UPGRADE_SLOTS = 3;
    private static final String ENERGY_UPGRADES_TAG = "energy_upgrades";
    private static final String NETWORK_PORT_NODE_TAG = "network_port_node";
    private static final String RETURN_INVENTORY_TAG = "returnInv";
    private static final IGridNodeListener<DataSanctumBlockEntity> MAIN_NODE_LISTENER = new BlockEntityNodeListener<>() {

        @Override
        public void onGridChanged(DataSanctumBlockEntity nodeOwner, IGridNode node) {
            if (nodeOwner.getMainNode().getGrid() != null) {
                nodeOwner.interfaceLogic.gridChanged();
            }
        }
    };
    private static final IGridNodeListener<DataSanctumBlockEntity> NETWORK_PORT_NODE_LISTENER = new BlockEntityNodeListener<>() {};

    private boolean lastLinked;
    private int lastMode;
    private final InterfaceLogic interfaceLogic = new InterfaceLogic(
            this.getMainNode(),
            this,
            ModBlocks.DATA_SANCTUM.get().asItem(),
            DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE);
    private final DataSanctumReturnInventory returnInventory = new DataSanctumReturnInventory(
            DataSanctumInterfaceConstants.RETURN_SLOTS_PER_PAGE,
            this::onReturnInventoryChanged);
    private final MachineSource actionSource = new MachineSource(this);
    private final IUpgradeInventory energyUpgrades = UpgradeInventories.forMachine(
            ModBlocks.DATA_SANCTUM.get(), ENERGY_UPGRADE_SLOTS, this::onEnergyUpgradesChanged);
    private final IInWorldGridNodeHost networkPortHost = new NetworkPortNodeHost(this);
    private final IManagedGridNode networkPortNode = GridHelper.createManagedNode(this, NETWORK_PORT_NODE_LISTENER)
            .setVisualRepresentation(ModBlocks.DATA_SANCTUM.get())
            .setIdlePowerUsage(0.0D)
            .setInWorldNode(true);
    private IGridConnection networkPortConnection;

    public DataSanctumBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), blockPos, blockState);
        this.lastLinked = blockState.hasProperty(DataSanctumBlock.ACTIVE) && blockState.getValue(DataSanctumBlock.ACTIVE);
        this.lastMode = blockState.hasProperty(DataSanctumBlock.MODE) ? blockState.getValue(DataSanctumBlock.MODE) : 0;
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_SANCTUM.get())
                .setIdlePowerUsage(0.0D);
        this.setInternalMaxPower(computeMaxPower(this.energyUpgrades));
        this.setInternalPublicPowerStorage(true);
        this.setInternalPowerFlow(AccessRestriction.READ_WRITE);
        installInterfaceInventories();
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, MAIN_NODE_LISTENER);
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        createNetworkPortNode();
        ensureNetworkPortConnection();
        updateVisualState(this.getMainNode().isOnline(), this.lastMode);
        injectReturnInventory();
    }

    @Override
    public void onReady() {
        super.onReady();
        createNetworkPortNode();
        ensureNetworkPortConnection();
    }

    public void setMode(int mode) {
        int clampedMode = Math.max(0, Math.min(2, mode));
        if (this.level == null || this.level.isClientSide()) {
            this.lastMode = clampedMode;
            return;
        }

        updateVisualState(this.getMainNode().isOnline(), clampedMode);
    }

    public int getMode() {
        return this.lastMode;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (this.getMainNode().hasGridBooted()) {
            this.interfaceLogic.notifyNeighbors();
            ensureNetworkPortConnection();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.interfaceLogic.writeToNBT(data, registries);
        this.returnInventory.writeToChildTag(data, RETURN_INVENTORY_TAG, registries);
        this.energyUpgrades.writeToNBT(data, ENERGY_UPGRADES_TAG, registries);
        CompoundTag networkPortNodeTag = new CompoundTag();
        this.networkPortNode.saveToNBT(networkPortNodeTag);
        data.put(NETWORK_PORT_NODE_TAG, networkPortNodeTag);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
        this.energyUpgrades.readFromNBT(data, ENERGY_UPGRADES_TAG, registries);
        if (data.contains(NETWORK_PORT_NODE_TAG)) {
            this.networkPortNode.loadFromNBT(data.getCompound(NETWORK_PORT_NODE_TAG));
        }
        this.setInternalMaxPower(computeMaxPower(this.energyUpgrades));
        clampStoredPowerToCapacity();
    }

    @Override
    public void setRemoved() {
        destroyNetworkPortConnection();
        this.networkPortNode.destroy();
        super.setRemoved();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.interfaceLogic;
    }

    @Override
    public int getPriority() {
        return this.interfaceLogic.getPriority();
    }

    @Override
    public void setPriority(int newValue) {
        this.interfaceLogic.setPriority(newValue);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.energyUpgrades;
    }

    public IUpgradeInventory getEnergyUpgrades() {
        return this.energyUpgrades;
    }

    public IInWorldGridNodeHost createNetworkPortHost() {
        return this.networkPortHost;
    }

    public DataSanctumReturnInventory getReturnInventory() {
        return this.returnInventory;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.DATA_SANCTUM.toStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(ModMenus.DATA_SANCTUM_INTERFACE.get(), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(ModMenus.DATA_SANCTUM_INTERFACE.get(), player, subMenu.getLocator());
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        this.interfaceLogic.addDrops(drops);
        this.returnInventory.addDrops(drops, level, pos);
        for (ItemStack stack : this.energyUpgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.interfaceLogic.clearContent();
        this.returnInventory.clear();
        this.energyUpgrades.clear();
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.energyUpgrades;
        }
        return super.getSubInventory(id);
    }

    private void installInterfaceInventories() {
        var config = DataSanctumInterfaceInventory.config(
                DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE,
                this.interfaceLogic::onConfigRowChanged);
        var storage = DataSanctumInterfaceInventory.storage(
                DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE,
                this.interfaceLogic::isAllowedInStorageSlot,
                this.interfaceLogic::onStorageChanged);
        this.interfaceLogic.config = config;
        this.interfaceLogic.storage = storage;
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

    public double extractOperationPower(double amount, Actionable mode) {
        if (amount <= 0.0D) {
            return 0.0D;
        }

        double extracted = 0.0D;
        IGridNode node = this.getMainNode().getNode();
        if (node != null && node.getGrid() != null) {
            extracted = node.getGrid().getEnergyService().extractAEPower(amount, mode, PowerMultiplier.ONE);
        }

        double missing = amount - extracted;
        if (missing > 0.0001D) {
            extracted += this.extractAEPower(missing, mode, PowerMultiplier.ONE);
        }
        return extracted;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public int getEnergyCardCount() {
        return getEnergyCardCount(this.energyUpgrades);
    }

    public static double computeMaxPower(IUpgradeInventory upgrades) {
        int energyCards = Math.max(0, Math.min(ENERGY_UPGRADE_SLOTS, getEnergyCardCount(upgrades)));
        return BASE_ENERGY_CAPACITY * (1 << energyCards);
    }

    public static int getEnergyCardCount(IUpgradeInventory upgrades) {
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.ENERGY_CARD));
    }

    private void updateVisualState(boolean linked, int mode) {
        if (this.level == null || (this.lastLinked == linked && this.lastMode == mode)) {
            return;
        }

        BlockState mainState = this.level.getBlockState(this.worldPosition);
        if (!mainState.is(ModBlocks.DATA_SANCTUM.get()) || !isMainPart(mainState)) {
            this.lastLinked = linked;
            this.lastMode = mode;
            return;
        }

        Direction facing = mainState.getValue(DataSanctumBlock.FACING);
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                for (int offsetY = 0; offsetY <= 3; offsetY++) {
                    BlockPos partPos = getPartPos(this.worldPosition, facing, offsetX, offsetZ, offsetY);
                    BlockState state = this.level.getBlockState(partPos);
                    if (state.is(ModBlocks.DATA_SANCTUM.get())) {
                        this.level.setBlock(partPos, state
                                .setValue(DataSanctumBlock.ACTIVE, linked)
                                .setValue(DataSanctumBlock.MODE, mode), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        this.lastLinked = linked;
        this.lastMode = mode;
    }

    private void onEnergyUpgradesChanged() {
        this.setInternalMaxPower(computeMaxPower(this.energyUpgrades));
        clampStoredPowerToCapacity();
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void createNetworkPortNode() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.networkPortNode.isReady()) {
            return;
        }

        BlockPos portPos = getNetworkPortPos();
        if (portPos == null) {
            return;
        }
        this.networkPortNode.create(this.level, portPos);
    }

    private void ensureNetworkPortConnection() {
        if (this.networkPortConnection != null || !this.getMainNode().isReady() || !this.networkPortNode.isReady()) {
            return;
        }

        IGridNode mainNode = this.getMainNode().getNode();
        IGridNode portNode = this.networkPortNode.getNode();
        if (mainNode == null || portNode == null) {
            return;
        }

        try {
            this.networkPortConnection = GridHelper.createConnection(mainNode, portNode);
        } catch (IllegalStateException ignored) {
            this.networkPortConnection = null;
        }
    }

    private void destroyNetworkPortConnection() {
        if (this.networkPortConnection == null) {
            return;
        }

        this.networkPortConnection.destroy();
        this.networkPortConnection = null;
    }

    private @Nullable BlockPos getNetworkPortPos() {
        if (this.level == null) {
            return null;
        }
        BlockState mainState = this.level.getBlockState(this.worldPosition);
        if (!mainState.is(ModBlocks.DATA_SANCTUM.get()) || !isMainPart(mainState)) {
            return null;
        }

        Direction facing = mainState.getValue(DataSanctumBlock.FACING);
        BlockPos portPos = getPartPos(this.worldPosition, facing, 0, 2, 0);
        BlockState portState = this.level.getBlockState(portPos);
        if (!portState.is(ModBlocks.DATA_SANCTUM.get()) || !isNetworkPortPart(portState) || !getMainPos(portPos, portState).equals(this.worldPosition)) {
            return null;
        }
        return portPos;
    }

    private void clampStoredPowerToCapacity() {
        double currentPower = this.getInternalCurrentPower();
        double maxPower = this.getInternalMaxPower();
        if (currentPower > maxPower) {
            this.extractAEPower(currentPower - maxPower, Actionable.MODULATE, PowerMultiplier.ONE);
        }
    }

    private record NetworkPortNodeHost(DataSanctumBlockEntity host) implements IInWorldGridNodeHost {

        @Override
        public IGridNode getGridNode(Direction dir) {
            return this.host.networkPortNode.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction dir) {
            return AECableType.COVERED;
        }
    }

    public static boolean isMainPart(BlockState state) {
        return decodeOffsetX(state) == 0 && decodeOffsetZ(state) == 0 && decodeOffsetY(state) == 0;
    }

    public static boolean isNetworkPortPart(BlockState state) {
        return decodeOffsetX(state) == 0 && decodeOffsetZ(state) == 2 && decodeOffsetY(state) == 0;
    }

    public static boolean isScreenPart(BlockState state) {
        return Math.abs(decodeOffsetX(state)) <= 1 && decodeOffsetZ(state) == -2 && decodeOffsetY(state) == 1;
    }

    public static BlockPos getMainPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DataSanctumBlock.FACING);
        int offsetX = decodeOffsetX(state);
        int offsetZ = decodeOffsetZ(state);
        int offsetY = decodeOffsetY(state);
        return getPartPos(pos, facing, -offsetX, -offsetZ).below(offsetY);
    }

    public static Iterable<BlockPos> iterFootprint(BlockPos mainPos, Direction facing) {
        List<BlockPos> positions = new ArrayList<>(25);
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                positions.add(getPartPos(mainPos, facing, offsetX, offsetZ));
            }
        }
        return positions;
    }

    public static BlockPos getPartPos(BlockPos mainPos, Direction facing, int offsetX, int offsetZ) {
        Direction depth = offsetZ < 0 ? facing : facing.getOpposite();
        Direction sideways = offsetX < 0 ? facing.getCounterClockWise() : facing.getClockWise();
        BlockPos result = offsetZ == 0 ? mainPos : mainPos.relative(depth, Math.abs(offsetZ));
        if (offsetX != 0) {
            result = result.relative(sideways, Math.abs(offsetX));
        }
        return result;
    }

    public static BlockPos getPartPos(BlockPos mainPos, Direction facing, int offsetX, int offsetZ, int offsetY) {
        return getPartPos(mainPos, facing, offsetX, offsetZ).above(offsetY);
    }

    public static int encodeOffsetX(int offsetX) {
        return offsetX + 2;
    }

    public static int encodeOffsetZ(int offsetZ) {
        return offsetZ + 2;
    }

    public static int decodeOffsetX(BlockState state) {
        return state.getValue(DataSanctumBlock.OFFSET_X) - 2;
    }

    public static int decodeOffsetZ(BlockState state) {
        return state.getValue(DataSanctumBlock.OFFSET_Z) - 2;
    }

    public static int decodeOffsetY(BlockState state) {
        return state.getValue(DataSanctumBlock.OFFSET_Y);
    }
}
