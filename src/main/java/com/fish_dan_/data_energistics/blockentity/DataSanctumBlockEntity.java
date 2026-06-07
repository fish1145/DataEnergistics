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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final long BLACK_HOLE_DATA_FLOW_PER_CYCLE = 2_000L;
    private static final long BLACK_HOLE_DATA_FLOW_PER_ENTITY = BLACK_HOLE_DATA_FLOW_PER_CYCLE;
    private static final double BLACK_HOLE_AE_COST_PER_BLOCK = 2_500.0D;
    private static final int BLACK_HOLE_CHUNK_RADIUS = 2;
    private static final int BLACK_HOLE_CHUNK_DIAMETER = BLACK_HOLE_CHUNK_RADIUS * 2 + 1;
    private static final int BLACK_HOLE_BLOCK_RADIUS = BLACK_HOLE_CHUNK_DIAMETER * 8;
    private static final double BLACK_HOLE_CENTER_Y_OFFSET = 2.5D;
    private static final double BLACK_HOLE_CENTER_ENTITY_RADIUS = 4.0D;
    private static final ColumnOffset[] BLACK_HOLE_COLUMN_ORDER = createBlackHoleColumnOrder();
    private static final int BLACK_HOLE_TOTAL_COLUMNS = BLACK_HOLE_COLUMN_ORDER.length;
    private static final int BLACK_HOLE_UNINITIALIZED_Y = Integer.MIN_VALUE;
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
    private static final IGridNodeListener<DataSanctumBlockEntity> NETWORK_PORT_NODE_LISTENER = new BlockEntityNodeListener<>() {
    };

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
    private int blackHoleColumnCursor;
    private int blackHoleExpansionRadius;
    private int[] blackHoleTopY;
    private int[] blackHoleCachedMaxY;

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
        data.putInt(BLACK_HOLE_COLUMN_CURSOR_TAG, this.blackHoleColumnCursor);
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
        this.blackHoleColumnCursor = Math.max(0, data.getInt(BLACK_HOLE_COLUMN_CURSOR_TAG)) % BLACK_HOLE_TOTAL_COLUMNS;
        this.blackHoleExpansionRadius = Math.max(0, Math.min(BLACK_HOLE_BLOCK_RADIUS, data.getInt(BLACK_HOLE_EXPANSION_RADIUS_TAG)));
        this.blackHoleTopY = null;
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
        double minY = this.level == null
                ? centerY - BLACK_HOLE_BLOCK_RADIUS
                : Math.max(this.level.getMinBuildHeight(), centerY - BLACK_HOLE_BLOCK_RADIUS);
        double maxY = this.level == null
                ? centerY + BLACK_HOLE_BLOCK_RADIUS
                : Math.min(this.level.getMaxBuildHeight(), centerY + BLACK_HOLE_BLOCK_RADIUS);
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

        if (!canBufferBlackHoleDataFlow(BLACK_HOLE_DATA_FLOW_PER_CYCLE)) {
            return;
        }

        int destroyedCount = consumeBlackHoleBlocks(this.blackHoleExpansionRadius);
        if (destroyedCount <= 0) {
            this.blackHoleTopY = null;
            return;
        }

        bufferBlackHoleDataFlow(BLACK_HOLE_DATA_FLOW_PER_CYCLE);
        saveChanges();
    }

    private void resetBlackHoleWorkState(boolean preserveCursor) {
        this.blackHoleWorkTicks = 0;
        if (!preserveCursor) {
            this.blackHoleColumnCursor = 0;
            this.blackHoleExpansionRadius = 0;
            this.blackHoleTopY = null;
            this.blackHoleCachedMaxY = null;
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

    private int consumeBlackHoleBlocks(int radius) {
        if (!(this.level instanceof Level level)) {
            return 0;
        }

        int columnLimit = getBlackHoleColumnLimit(radius);
        if (columnLimit <= 0) {
            return 0;
        }
        if (this.blackHoleColumnCursor >= columnLimit) {
            this.blackHoleColumnCursor = 0;
        }

        ensureBlackHoleTopYCache();
        if (this.blackHoleTopY == null) {
            return 0;
        }

        int destroyedCount = 0;
        int attempts = 0;
        while (attempts < columnLimit) {
            int columnIndex = this.blackHoleColumnCursor;
            this.blackHoleColumnCursor = (this.blackHoleColumnCursor + 1) % columnLimit;
            attempts++;

            while (true) {
                BlockPos targetPos = findNextConsumableBlock(level, columnIndex, radius);
                if (targetPos == null) {
                    break;
                }

                if (extractOperationPower(BLACK_HOLE_AE_COST_PER_BLOCK, Actionable.SIMULATE) + 0.0001D < BLACK_HOLE_AE_COST_PER_BLOCK) {
                    return destroyedCount;
                }

                level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                extractOperationPower(BLACK_HOLE_AE_COST_PER_BLOCK, Actionable.MODULATE);
                destroyedCount++;
            }
        }
        return destroyedCount;
    }

    private static int getBlackHoleColumnLimit(int radius) {
        int radiusSqr = Math.max(0, radius) * Math.max(0, radius);
        int limit = 0;
        while (limit < BLACK_HOLE_TOTAL_COLUMNS && BLACK_HOLE_COLUMN_ORDER[limit].distanceSqr() <= radiusSqr) {
            limit++;
        }
        return limit;
    }

    private void ensureBlackHoleTopYCache() {
        if (this.blackHoleTopY == null || this.blackHoleTopY.length != BLACK_HOLE_TOTAL_COLUMNS
                || this.blackHoleCachedMaxY == null || this.blackHoleCachedMaxY.length != BLACK_HOLE_TOTAL_COLUMNS) {
            this.blackHoleTopY = new int[BLACK_HOLE_TOTAL_COLUMNS];
            this.blackHoleCachedMaxY = new int[BLACK_HOLE_TOTAL_COLUMNS];
            java.util.Arrays.fill(this.blackHoleTopY, BLACK_HOLE_UNINITIALIZED_Y);
            java.util.Arrays.fill(this.blackHoleCachedMaxY, BLACK_HOLE_UNINITIALIZED_Y);
        }
    }

    private @Nullable BlockPos findNextConsumableBlock(Level level, int columnIndex, int radius) {
        ColumnPos columnPos = getBlackHoleColumnPos(columnIndex);
        VerticalRange verticalRange = getBlackHoleColumnVerticalRange(level, columnPos, radius);
        if (verticalRange == null) {
            this.blackHoleTopY[columnIndex] = BLACK_HOLE_UNINITIALIZED_Y;
            this.blackHoleCachedMaxY[columnIndex] = BLACK_HOLE_UNINITIALIZED_Y;
            return null;
        }

        LevelChunk chunk = level.getChunkSource().getChunk(columnPos.chunkX(), columnPos.chunkZ(), false);
        if (chunk == null) {
            this.blackHoleTopY[columnIndex] = BLACK_HOLE_UNINITIALIZED_Y;
            this.blackHoleCachedMaxY[columnIndex] = BLACK_HOLE_UNINITIALIZED_Y;
            return null;
        }

        int minY = verticalRange.minY();
        int maxY = verticalRange.maxY();
        int currentY = this.blackHoleTopY[columnIndex];
        if (currentY == BLACK_HOLE_UNINITIALIZED_Y || currentY > maxY || this.blackHoleCachedMaxY[columnIndex] != maxY) {
            currentY = Math.min(maxY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, columnPos.localX(), columnPos.localZ()) - 1);
            this.blackHoleCachedMaxY[columnIndex] = maxY;
        }
        if (currentY < minY) {
            this.blackHoleTopY[columnIndex] = minY - 1;
            return null;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(columnPos.worldX(), currentY, columnPos.worldZ());
        while (currentY >= minY) {
            cursor.setY(currentY);
            BlockState state = chunk.getBlockState(cursor);
            BlockConsumption consumption = getBlackHoleBlockConsumption(state);
            if (consumption == BlockConsumption.CONSUME) {
                this.blackHoleTopY[columnIndex] = currentY - 1;
                return cursor.immutable();
            }
            if (consumption == BlockConsumption.BLOCKED) {
                this.blackHoleTopY[columnIndex] = currentY;
                return null;
            }
            currentY--;
        }

        this.blackHoleTopY[columnIndex] = minY - 1;
        return null;
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
                entity -> isConsumableEntityByBlackHole(entity)
                        && distanceToBlackHoleCenterSqr(entity, centerX, centerY, centerZ) <= radiusSqr);
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
        return amount > 0
                && this.returnInventory.insert(DataFlowKey.of(), amount, Actionable.SIMULATE, this.actionSource) >= amount;
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
        return hasLinkedWirelessTerminal(player.getInventory().items, player, grid)
                || hasLinkedWirelessTerminal(player.getInventory().armor, player, grid)
                || hasLinkedWirelessTerminal(player.getInventory().offhand, player, grid);
    }

    private static boolean hasLinkedWirelessTerminal(List<ItemStack> stacks, ServerPlayer player, IGrid grid) {
        for (ItemStack stack : stacks) {
            if (stack.getItem() instanceof WirelessTerminalItem wirelessTerminal
                    && wirelessTerminal.getLinkedGrid(stack, player.level(), null) == grid) {
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

    private ColumnPos getBlackHoleColumnPos(int columnIndex) {
        ColumnOffset offset = BLACK_HOLE_COLUMN_ORDER[columnIndex];
        int worldX = this.worldPosition.getX() + offset.offsetX();
        int worldZ = this.worldPosition.getZ() + offset.offsetZ();
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        return new ColumnPos(
                chunkX,
                chunkZ,
                worldX & 15,
                worldZ & 15,
                worldX,
                worldZ);
    }

    private @Nullable VerticalRange getBlackHoleColumnVerticalRange(Level level, ColumnPos columnPos, int radius) {
        double dx = columnPos.worldX() + 0.5D - getBlackHoleCenterX();
        double dz = columnPos.worldZ() + 0.5D - getBlackHoleCenterZ();
        double radiusSqr = Math.max(0, radius) * Math.max(0, radius);
        double horizontalDistanceSqr = dx * dx + dz * dz;
        if (horizontalDistanceSqr > radiusSqr) {
            return null;
        }

        double verticalRadius = Math.sqrt(radiusSqr - horizontalDistanceSqr);
        double centerY = getBlackHoleCenterY();
        int minY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(centerY - verticalRadius - 0.5D));
        int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(centerY + verticalRadius - 0.5D));
        if (maxY < minY) {
            return null;
        }
        return new VerticalRange(minY, maxY);
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

    private static ColumnOffset[] createBlackHoleColumnOrder() {
        ArrayList<ColumnOffset> columns = new ArrayList<>((BLACK_HOLE_BLOCK_RADIUS * 2 + 1) * (BLACK_HOLE_BLOCK_RADIUS * 2 + 1));
        int radiusSqr = BLACK_HOLE_BLOCK_RADIUS * BLACK_HOLE_BLOCK_RADIUS;
        for (int offsetX = -BLACK_HOLE_BLOCK_RADIUS; offsetX < BLACK_HOLE_BLOCK_RADIUS; offsetX++) {
            for (int offsetZ = -BLACK_HOLE_BLOCK_RADIUS; offsetZ < BLACK_HOLE_BLOCK_RADIUS; offsetZ++) {
                int distanceSqr = offsetX * offsetX + offsetZ * offsetZ;
                if (distanceSqr <= radiusSqr) {
                    columns.add(new ColumnOffset(offsetX, offsetZ, distanceSqr));
                }
            }
        }

        columns.sort(Comparator
                .comparingInt(ColumnOffset::distanceSqr)
                .thenComparingDouble(column -> Math.atan2(column.offsetZ(), column.offsetX())));
        return columns.toArray(ColumnOffset[]::new);
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

    private record ColumnPos(int chunkX, int chunkZ, int localX, int localZ, int worldX, int worldZ) {
    }

    private record ColumnOffset(int offsetX, int offsetZ, int distanceSqr) {
    }

    private record VerticalRange(int minY, int maxY) {
    }

    private enum BlockConsumption {
        CONSUME,
        PASS_THROUGH,
        BLOCKED
    }
}
