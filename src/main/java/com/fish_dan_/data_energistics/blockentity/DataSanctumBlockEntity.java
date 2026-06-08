package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
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
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DataSanctumBlockEntity extends AENetworkedPoweredBlockEntity implements InterfaceLogicHost {

    public static final double BASE_ENERGY_CAPACITY = 500_000.0D;
    public static final int ENERGY_UPGRADE_SLOTS = 3;
    private static final int BLACK_HOLE_MODE = 1;
    private static final int BLACK_HOLE_WORK_INTERVAL_TICKS = 200;
    private static final int BLACK_HOLE_DEV_WORK_INTERVAL_TICKS = 20;
    private static final int BLACK_HOLE_BLOCKS_PER_CYCLE = 20;
    private static final long BLACK_HOLE_DATA_FLOW_PER_CYCLE = 2_000L;
    private static final long BLACK_HOLE_DATA_FLOW_PER_ENTITY = BLACK_HOLE_DATA_FLOW_PER_CYCLE;
    private static final double BLACK_HOLE_AE_COST_PER_BLOCK = 2_500.0D;
    private static final int BLACK_HOLE_CHUNK_RADIUS = 2;
    private static final int BLACK_HOLE_CHUNK_DIAMETER = BLACK_HOLE_CHUNK_RADIUS * 2 + 1;
    private static final int BLACK_HOLE_BLOCK_RADIUS = BLACK_HOLE_CHUNK_DIAMETER * 8;
    private static final double BLACK_HOLE_CENTER_Y_OFFSET = 2.5D;
    private static final double BLACK_HOLE_CENTER_ENTITY_RADIUS = 4.0D;
    private static final int BLACK_HOLE_SURFACE_INNER_MARGIN = 3;
    private static final int BLACK_HOLE_SURFACE_OUTER_MARGIN = 3;
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String ENERGY_UPGRADES_TAG = "energy_upgrades";
    private static final String NETWORK_PORT_NODE_TAG = "network_port_node";
    private static final String RETURN_INVENTORY_TAG = "returnInv";
    private static final String BLACK_HOLE_WORK_TICKS_TAG = "black_hole_work_ticks";
    private static final String BLACK_HOLE_COLUMN_CURSOR_TAG = "black_hole_column_cursor";
    private static final String BLACK_HOLE_EXPANSION_RADIUS_TAG = "black_hole_expansion_radius";
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
            this::onReturnInventoryChanged,
            () -> 0);
    private final MachineSource actionSource = new MachineSource(this);
    private final IUpgradeInventory energyUpgrades = UpgradeInventories.forMachine(
            ModBlocks.DATA_SANCTUM.get(), ENERGY_UPGRADE_SLOTS, this::onEnergyUpgradesChanged);
    private final IInWorldGridNodeHost networkPortHost = new NetworkPortNodeHost(this);
    private final IManagedGridNode networkPortNode = GridHelper.createManagedNode(this, NETWORK_PORT_NODE_LISTENER)
            .setVisualRepresentation(ModBlocks.DATA_SANCTUM.get())
            .setIdlePowerUsage(0.0D)
            .setInWorldNode(true);
    private IGridConnection networkPortConnection;
    private boolean showRange;
    private int blackHoleWorkTicks;
    private int blackHoleBlockCursor;
    private int blackHoleExpansionRadius;
    private int preparedBlackHoleRadius;
    private final List<BlockPos> pendingBlackHoleBlocks = new ArrayList<>();

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
        refillEnergyCache();
        updateVisualState(isOnline(), this.lastMode);
        performModeWork();
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

        updateVisualState(isOnline(), clampedMode);
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
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        CompoundTag networkPortNodeTag = new CompoundTag();
        this.networkPortNode.saveToNBT(networkPortNodeTag);
        data.put(NETWORK_PORT_NODE_TAG, networkPortNodeTag);
        data.putInt(BLACK_HOLE_WORK_TICKS_TAG, this.blackHoleWorkTicks);
        data.putInt(BLACK_HOLE_COLUMN_CURSOR_TAG, this.blackHoleBlockCursor);
        data.putInt(BLACK_HOLE_EXPANSION_RADIUS_TAG, this.blackHoleExpansionRadius);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.interfaceLogic.readFromNBT(data, registries);
        this.returnInventory.readFromChildTag(data, RETURN_INVENTORY_TAG, registries);
        this.energyUpgrades.readFromNBT(data, ENERGY_UPGRADES_TAG, registries);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        if (data.contains(NETWORK_PORT_NODE_TAG)) {
            this.networkPortNode.loadFromNBT(data.getCompound(NETWORK_PORT_NODE_TAG));
        }
        this.setInternalMaxPower(computeMaxPower(this.energyUpgrades));
        clampStoredPowerToCapacity();
        this.blackHoleWorkTicks = Math.max(0, data.getInt(BLACK_HOLE_WORK_TICKS_TAG));
        this.blackHoleBlockCursor = 0;
        this.blackHoleExpansionRadius = Math.max(0, Math.min(BLACK_HOLE_BLOCK_RADIUS, data.getInt(BLACK_HOLE_EXPANSION_RADIUS_TAG)));
        this.preparedBlackHoleRadius = 0;
        this.pendingBlackHoleBlocks.clear();
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.showRange);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean showRange = data.readBoolean();
        if (showRange != this.showRange) {
            this.showRange = showRange;
            changed = true;
        }
        return changed;
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
                this.interfaceLogic::onConfigRowChanged,
                () -> 0);
        var storage = DataSanctumInterfaceInventory.storage(
                DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE,
                this.interfaceLogic::isAllowedInStorageSlot,
                this.interfaceLogic::onStorageChanged,
                () -> 0);
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
        return this.getMainNode().isOnline() && hasExternalNetworkPortConnection();
    }

    public boolean setRangeDisplayEnabled(boolean enabled) {
        if (this.showRange != enabled) {
            this.showRange = enabled;
            this.setChanged();
            this.markForClientUpdate();
        }
        return this.showRange;
    }

    public boolean isRangeDisplayEnabled() {
        return this.showRange;
    }

    public boolean canDisplayBlackHoleRange() {
        BlockState state = getBlockState();
        return this.showRange && state.hasProperty(DataSanctumBlock.MODE) && state.getValue(DataSanctumBlock.MODE) == BLACK_HOLE_MODE;
    }

    public AABB getBlackHoleCoverageAabb() {
        double centerX = getBlackHoleCenterX();
        double centerY = getBlackHoleCenterY();
        double centerZ = getBlackHoleCenterZ();
        double minY = this.level == null ? centerY - BLACK_HOLE_BLOCK_RADIUS : Math.max(this.level.getMinBuildHeight(), centerY - BLACK_HOLE_BLOCK_RADIUS);
        double maxY = this.level == null ? centerY + BLACK_HOLE_BLOCK_RADIUS : Math.min(this.level.getMaxBuildHeight(), centerY + BLACK_HOLE_BLOCK_RADIUS);
        return new AABB(
                centerX - BLACK_HOLE_BLOCK_RADIUS,
                minY,
                centerZ - BLACK_HOLE_BLOCK_RADIUS,
                centerX + BLACK_HOLE_BLOCK_RADIUS,
                maxY,
                centerZ + BLACK_HOLE_BLOCK_RADIUS);
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

    private boolean hasExternalNetworkPortConnection() {
        IGridNode portNode = this.networkPortNode.getNode();
        if (portNode == null) {
            return false;
        }

        for (IGridConnection connection : portNode.getInWorldConnections().values()) {
            if (connection != null && connection != this.networkPortConnection) {
                return true;
            }
        }

        return false;
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

    private void refillEnergyCache() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 0.0001D) {
            return;
        }

        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0D) {
            this.injectExternalPower(appeng.api.config.PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void performModeWork() {
        if (this.lastMode != BLACK_HOLE_MODE) {
            resetBlackHoleWorkState(false);
            return;
        }

        if (this.level == null || this.level.isClientSide() || !isOnline()) {
            resetBlackHoleWorkState(true);
            return;
        }

        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            resetBlackHoleWorkState(true);
            return;
        }

        consumeBlackHoleCenterEntities(this.level);

        this.blackHoleWorkTicks++;
        if (this.blackHoleWorkTicks < getBlackHoleWorkIntervalTicks()) {
            return;
        }

        this.blackHoleWorkTicks = 0;
        advanceBlackHoleExpansionRadius();
        consumeBlackHoleEntities(this.level, this.blackHoleExpansionRadius);

        int pendingBeforeConsume = this.pendingBlackHoleBlocks.size();
        boolean canBufferDataFlow = canBufferBlackHoleDataFlow(BLACK_HOLE_DATA_FLOW_PER_CYCLE);
        int destroyedCount = canBufferDataFlow ? consumeBlackHoleBlocks() : 0;
        int pendingAfterConsume = this.pendingBlackHoleBlocks.size();
        int preparedCount = prepareBlackHoleBlocks(this.level, this.blackHoleExpansionRadius);
        logBlackHoleWorkCycle(pendingBeforeConsume, canBufferDataFlow, destroyedCount, pendingAfterConsume, preparedCount);
        if (destroyedCount <= 0) {
            return;
        }

        bufferBlackHoleDataFlow(BLACK_HOLE_DATA_FLOW_PER_CYCLE);
        saveChanges();
    }

    private void resetBlackHoleWorkState(boolean preserveCursor) {
        this.blackHoleWorkTicks = 0;
        if (!preserveCursor) {
            this.blackHoleBlockCursor = 0;
            this.blackHoleExpansionRadius = 0;
            this.preparedBlackHoleRadius = 0;
            this.pendingBlackHoleBlocks.clear();
        }
    }

    private void advanceBlackHoleExpansionRadius() {
        if (this.blackHoleExpansionRadius < BLACK_HOLE_BLOCK_RADIUS) {
            this.blackHoleExpansionRadius++;
            saveChanges();
        }
    }

    private static int getBlackHoleWorkIntervalTicks() {
        return Data_Energistics.isDev() ? BLACK_HOLE_DEV_WORK_INTERVAL_TICKS : BLACK_HOLE_WORK_INTERVAL_TICKS;
    }

    private int consumeBlackHoleBlocks() {
        if (!(this.level instanceof Level level)) {
            return 0;
        }
        if (this.pendingBlackHoleBlocks.isEmpty()) {
            return 0;
        }
        if (this.blackHoleBlockCursor >= this.pendingBlackHoleBlocks.size()) {
            this.blackHoleBlockCursor = 0;
        }

        int blockLimit = getBlackHoleBlockLimitPerCycle();
        int destroyedCount = 0;
        for (int index = this.blackHoleBlockCursor; index < this.pendingBlackHoleBlocks.size(); index++) {
            this.blackHoleBlockCursor = index;
            BlockPos targetPos = this.pendingBlackHoleBlocks.get(index);
            if (targetPos.getY() < level.getMinBuildHeight() || targetPos.getY() >= level.getMaxBuildHeight()) {
                continue;
            }

            BlockState state = level.getBlockState(targetPos);
            if (getBlackHoleBlockConsumption(state) != BlockConsumption.CONSUME) {
                continue;
            }

            if (extractOperationPower(BLACK_HOLE_AE_COST_PER_BLOCK, Actionable.SIMULATE) + 0.0001D < BLACK_HOLE_AE_COST_PER_BLOCK) {
                return destroyedCount;
            }

            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            extractOperationPower(BLACK_HOLE_AE_COST_PER_BLOCK, Actionable.MODULATE);
            destroyedCount++;
            if (destroyedCount >= blockLimit) {
                int nextIndex = index + 1;
                if (nextIndex >= this.pendingBlackHoleBlocks.size()) {
                    this.blackHoleBlockCursor = 0;
                    this.pendingBlackHoleBlocks.clear();
                } else {
                    this.blackHoleBlockCursor = nextIndex;
                }
                return destroyedCount;
            }
        }

        this.blackHoleBlockCursor = 0;
        this.pendingBlackHoleBlocks.clear();
        return destroyedCount;
    }

    private int prepareBlackHoleBlocks(Level level, int radius) {
        if (radius <= 0 || radius <= this.preparedBlackHoleRadius) {
            return 0;
        }

        List<BlockPos> preparedBlocks = createBlackHoleSurfaceBlocks(level, radius);
        this.pendingBlackHoleBlocks.addAll(preparedBlocks);
        this.preparedBlackHoleRadius = radius;
        return preparedBlocks.size();
    }

    private static int getBlackHoleBlockLimitPerCycle() {
        return Data_Energistics.isDev() ? Integer.MAX_VALUE : BLACK_HOLE_BLOCKS_PER_CYCLE;
    }

    private void logBlackHoleWorkCycle(int pendingBeforeConsume, boolean canBufferDataFlow, int destroyedCount,
                                       int pendingAfterConsume, int preparedCount) {
        if (!Data_Energistics.isDev()) {
            return;
        }

        Data_Energistics.LOGGER.info(
                "Data Sanctum black hole cycle pos={} radius={} preparedRadius={} pendingBefore={} canBuffer={} destroyed={} pendingAfter={} preparedNow={} cursor={}",
                this.worldPosition,
                this.blackHoleExpansionRadius,
                this.preparedBlackHoleRadius,
                pendingBeforeConsume,
                canBufferDataFlow,
                destroyedCount,
                pendingAfterConsume,
                preparedCount,
                this.blackHoleBlockCursor);
    }

    private int consumeBlackHoleCenterEntities(Level level) {
        return consumeBlackHoleEntities(level, BLACK_HOLE_CENTER_ENTITY_RADIUS);
    }

    private int consumeBlackHoleEntities(Level level, double radius) {
        double centerX = getBlackHoleCenterX();
        double centerY = getBlackHoleCenterY();
        double centerZ = getBlackHoleCenterZ();
        AABB bounds = new AABB(
                centerX - radius,
                Math.max(level.getMinBuildHeight(), centerY - radius),
                centerZ - radius,
                centerX + radius,
                Math.min(level.getMaxBuildHeight(), centerY + radius),
                centerZ + radius);
        double radiusSqr = radius * radius;
        List<Entity> entities = level.getEntities((Entity) null, bounds,
                entity -> isConsumableEntityByBlackHole(entity) && distanceToBlackHoleCenterSqr(entity, centerX, centerY, centerZ) <= radiusSqr);
        if (entities.isEmpty()) {
            return 0;
        }

        long canBuffer = this.returnInventory.insert(
                DataFlowKey.of(),
                safeMultiply(BLACK_HOLE_DATA_FLOW_PER_ENTITY, entities.size()),
                Actionable.SIMULATE,
                this.actionSource);
        int consumableCount = (int) Math.min(entities.size(), canBuffer / BLACK_HOLE_DATA_FLOW_PER_ENTITY);
        if (consumableCount <= 0) {
            return 0;
        }

        int consumedCount = 0;
        for (Entity entity : entities) {
            if (consumedCount >= consumableCount) {
                break;
            }

            if (entity instanceof ServerPlayer player) {
                player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            } else {
                entity.discard();
            }
            consumedCount++;
        }

        if (consumedCount > 0) {
            bufferBlackHoleDataFlow(safeMultiply(BLACK_HOLE_DATA_FLOW_PER_ENTITY, consumedCount));
        }
        return consumedCount;
    }

    private boolean canBufferBlackHoleDataFlow(long amount) {
        return amount > 0 && this.returnInventory.insert(DataFlowKey.of(), amount, Actionable.SIMULATE, this.actionSource) >= amount;
    }

    private long bufferBlackHoleDataFlow(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return this.returnInventory.insert(DataFlowKey.of(), amount, Actionable.MODULATE, this.actionSource);
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

    private static double distanceToBlackHoleCenterSqr(Entity entity, double centerX, double centerY, double centerZ) {
        var entityCenter = entity.getBoundingBox().getCenter();
        double x = entityCenter.x - centerX;
        double y = entityCenter.y - centerY;
        double z = entityCenter.z - centerZ;
        return x * x + y * y + z * z;
    }

    private boolean isConsumableEntityByBlackHole(Entity entity) {
        if (entity.isRemoved() || !entity.isAlive()) {
            return false;
        }

        if (entity instanceof ServerPlayer player && isProtectedBlackHolePlayer(player)) {
            return false;
        }

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId != null && isProtectedBlackHoleNamespace(entityId.getNamespace())) {
            return false;
        }

        if (entity instanceof ItemEntity itemEntity) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
            return itemId == null || !isProtectedBlackHoleNamespace(itemId.getNamespace());
        }

        return true;
    }

    private boolean isProtectedBlackHolePlayer(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2) || isPlayerLinkedToBlackHoleGrid(player);
    }

    private boolean isPlayerLinkedToBlackHoleGrid(ServerPlayer player) {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return false;
        }

        IGrid grid = node.getGrid();
        return hasLinkedWirelessTerminal(player.getInventory().items, player, grid) || hasLinkedWirelessTerminal(player.getInventory().armor, player, grid) || hasLinkedWirelessTerminal(player.getInventory().offhand, player, grid);
    }

    private static boolean hasLinkedWirelessTerminal(List<ItemStack> stacks, ServerPlayer player, IGrid grid) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof WirelessTerminalItem wirelessTerminal && wirelessTerminal.getLinkedGrid(stack, player.level(), null) == grid) {
                return true;
            }
        }
        return false;
    }

    private BlockConsumption getBlackHoleBlockConsumption(BlockState state) {
        if (state.isAir()) {
            return BlockConsumption.PASS_THROUGH;
        }
        if (state.is(Blocks.BEDROCK)) {
            return BlockConsumption.BLOCKED;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return BlockConsumption.CONSUME;
        }

        return isProtectedBlackHoleNamespace(blockId.getNamespace()) ? BlockConsumption.BLOCKED : BlockConsumption.CONSUME;
    }

    private static boolean isProtectedBlackHoleNamespace(String namespace) {
        return "ae2".equals(namespace) || Data_Energistics.MODID.equals(namespace);
    }

    private double getBlackHoleCenterX() {
        return this.worldPosition.getX() + 0.5D;
    }

    private double getBlackHoleCenterY() {
        return this.worldPosition.getY() + BLACK_HOLE_CENTER_Y_OFFSET;
    }

    private double getBlackHoleCenterZ() {
        return this.worldPosition.getZ() + 0.5D;
    }

    private List<BlockPos> createBlackHoleSurfaceBlocks(Level level, int radius) {
        ArrayList<BlockOffset> blocks = new ArrayList<>();
        int innerRadius = Math.max(0, radius - BLACK_HOLE_SURFACE_INNER_MARGIN);
        int outerRadius = Math.min(BLACK_HOLE_BLOCK_RADIUS, radius + BLACK_HOLE_SURFACE_OUTER_MARGIN);
        double innerRadiusSqr = innerRadius * innerRadius;
        double outerRadiusSqr = outerRadius * outerRadius;
        int minOffsetY = Math.max(level.getMinBuildHeight() - this.worldPosition.getY(), (int) Math.floor(BLACK_HOLE_CENTER_Y_OFFSET - outerRadius - 0.5D));
        int maxOffsetY = Math.min(level.getMaxBuildHeight() - 1 - this.worldPosition.getY(), (int) Math.ceil(BLACK_HOLE_CENTER_Y_OFFSET + outerRadius - 0.5D));
        for (int offsetX = -outerRadius; offsetX <= outerRadius; offsetX++) {
            for (int offsetY = minOffsetY; offsetY <= maxOffsetY; offsetY++) {
                for (int offsetZ = -outerRadius; offsetZ <= outerRadius; offsetZ++) {
                    double dy = offsetY + 0.5D - BLACK_HOLE_CENTER_Y_OFFSET;
                    double distanceSqr = offsetX * offsetX + dy * dy + offsetZ * offsetZ;
                    if (distanceSqr >= innerRadiusSqr && distanceSqr <= outerRadiusSqr) {
                        blocks.add(new BlockOffset(offsetX, offsetY, offsetZ, distanceSqr));
                    }
                }
            }
        }

        blocks.sort(Comparator
                .comparingDouble(BlockOffset::distanceSqr)
                .thenComparingDouble(block -> Math.abs(block.offsetY() + 0.5D - BLACK_HOLE_CENTER_Y_OFFSET))
                .thenComparingDouble(block -> Math.atan2(block.offsetZ(), block.offsetX()))
                .thenComparingInt(BlockOffset::offsetY));
        ArrayList<BlockPos> positions = new ArrayList<>(blocks.size());
        for (BlockOffset block : blocks) {
            positions.add(this.worldPosition.offset(block.offsetX(), block.offsetY(), block.offsetZ()));
        }
        return positions;
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

    private record BlockOffset(int offsetX, int offsetY, int offsetZ, double distanceSqr) {}

    private enum BlockConsumption {
        CONSUME,
        PASS_THROUGH,
        BLOCKED
    }
}
