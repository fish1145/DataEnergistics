package com.fish_dan_.data_energistics.blockentity;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.parts.CableBusContainer;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.AEItemDefinitionFilter;
import com.fish_dan_.data_energistics.ae2.CustomAdHocChannelHost;
import com.fish_dan_.data_energistics.Config;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.AE2FluxIntegration;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public class DataDistributionTowerBlockEntity extends AENetworkedBlockEntity implements CustomAdHocChannelHost, InternalInventoryHost {
    private static final String TICK_BUDGET_TAG = "transfer_budget_hint";
    private static final String SHOW_RANGE_TAG = "show_range";
    private static final String LINKED_POSITIONS_TAG = "linked_positions";
    private static final int CACHE_TICKS = 20;
    private static final int INITIAL_PENDING_DELAY = 2;
    private static final double BASE_IDLE_POWER_USAGE = 4.0;
    private static final double IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK = 8.0;
    private static final int BOOSTERS_PER_CHUNK_RING = 8;
    private static final int VERTICAL_RANGE_ABOVE = 256;
    private static final int VERTICAL_RANGE_BELOW = 128;
    private static final Map<ChunkKey, Set<BlockPos>> TOWER_CHUNK_POSITIONS = new HashMap<>();
    private static MinecraftServer boundServer;

    private final Map<BlockPos, Integer> pendingLinkPositions = new LinkedHashMap<>();
    private final Set<BlockPos> linkedPositions = new LinkedHashSet<>();
    private final Map<BlockPos, List<IGridConnection>> linkedConnections = new HashMap<>();
    private final AppEngInternalInventory wirelessBoosters = new AppEngInternalInventory(this, 1);
    private long lastEndpointCacheTick = Long.MIN_VALUE;
    private List<BlockPos> cachedEndpoints = List.of();
    private long cachedTransferBudgetHint = 0L;
    private boolean showRange = false;
    private boolean pendingRangeRefresh = false;
    private int indexedChunkRadius = -1;
    private int syncedChunkRadius = 0;

    public DataDistributionTowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.wirelessBoosters.setFilter(new AEItemDefinitionFilter(AEItems.WIRELESS_BOOSTER));
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                .setIdlePowerUsage(BASE_IDLE_POWER_USAGE);
    }

    @Override
    public void onReady() {
        super.onReady();
        updateIdlePowerUsage();
        if (this.level != null && !this.level.isClientSide()) {
            registerInChunkIndex();
            invalidateEndpointCache();
            requeuePersistedLinks();
            scanNearbyConnectableNodes();
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            unregisterFromChunkIndex();
            destroyAllConnections();
        }
        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.cachedTransferBudgetHint = data.getLong(TICK_BUDGET_TAG);
        this.showRange = data.getBoolean(SHOW_RANGE_TAG);
        this.wirelessBoosters.readFromNBT(data, "wireless_boosters", registries);
        this.syncedChunkRadius = computeChunkRadius();
        updateIdlePowerUsage();
        this.pendingLinkPositions.clear();
        this.linkedPositions.clear();
        this.linkedConnections.clear();

        Tag root = data.get(LINKED_POSITIONS_TAG);
        if (root instanceof ListTag list) {
            for (Tag tag : list) {
                if (tag instanceof CompoundTag compound) {
                    NbtUtils.readBlockPos(compound, "pos").ifPresent(pos -> this.linkedPositions.add(pos.immutable()));
                }
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putLong(TICK_BUDGET_TAG, this.cachedTransferBudgetHint);
        data.putBoolean(SHOW_RANGE_TAG, this.showRange);
        this.wirelessBoosters.writeToNBT(data, "wireless_boosters", registries);

        ListTag linked = new ListTag();
        for (BlockPos pos : this.linkedPositions) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            linked.add(entry);
        }
        data.put(LINKED_POSITIONS_TAG, linked);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.showRange);
        data.writeVarInt(computeChunkRadius());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean showRange = data.readBoolean();
        if (showRange != this.showRange) {
            this.showRange = showRange;
            changed = true;
        }
        int syncedChunkRadius = data.readVarInt();
        if (syncedChunkRadius != this.syncedChunkRadius) {
            this.syncedChunkRadius = syncedChunkRadius;
            changed = true;
        }
        return changed;
    }

    public void serverTick() {
        if (this.level == null) {
            return;
        }

        if (this.pendingRangeRefresh) {
            applyPendingRangeRefresh();
        }

        IGridNode selfNode = this.getMainNode().getNode();
        if (selfNode == null || !selfNode.isActive()) {
            return;
        }

        if (selfNode.getUsedChannels() < getMaxLinkChannels()) {
            processPendingLinks(selfNode);
        }

        if (isClusterCoordinator()) {
            performActiveRangeTransfer();
        }
    }

    public IEnergyStorage getEnergyStorageForQuery(BlockPos accessPos, @Nullable net.minecraft.core.Direction side) {
        BlockPos excludedPos = side == null ? null : accessPos.relative(side);
        return new TowerEnergyStorage(excludedPos);
    }

    public boolean toggleRangeDisplay() {
        this.showRange = !this.showRange;
        this.setChanged();
        this.markForClientUpdate();
        return this.showRange;
    }

    public boolean isRangeDisplayEnabled() {
        return this.showRange;
    }

    public int getConfiguredChunkRadius() {
        return getChunkRadius();
    }

    public AABB getCoverageAabb() {
        ChunkPos center = new ChunkPos(this.worldPosition);
        int chunkRadius = getChunkRadius();
        int minX = (center.x - chunkRadius) << 4;
        int minZ = (center.z - chunkRadius) << 4;
        int maxX = (center.x + chunkRadius + 1) << 4;
        int maxZ = (center.z + chunkRadius + 1) << 4;
        int minY = this.worldPosition.getY() - VERTICAL_RANGE_BELOW;
        int maxY = this.worldPosition.getY() + VERTICAL_RANGE_ABOVE + 1;

        if (this.level == null) {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return new AABB(
                minX,
                Math.max(this.level.getMinBuildHeight(), minY),
                minZ,
                maxX,
                Math.min(this.level.getMaxBuildHeight(), maxY),
                maxZ
        );
    }

    public String getChannelDisplayText() {
        IGridNode node = this.getMainNode().getNode();
        int used = node == null ? 0 : node.getUsedChannels();
        return used + "/" + getMaxLinkChannels();
    }

    public String getEnergyDisplayText() {
        return formatFeAmount(getAvailableFeForUi());
    }

    public int getUsedChannelCount() {
        IGridNode node = this.getMainNode().getNode();
        return node == null ? 0 : node.getUsedChannels();
    }

    public int getMaxChannelCount() {
        return getMaxLinkChannels();
    }

    public int getAvailableFeForUi() {
        return clampStoredAmount(getTotalExtractableEnergy(null));
    }

    public boolean isNetworkNodeOnline() {
        return isTowerActive();
    }

    public AppEngInternalInventory getInternalInventory() {
        return this.wirelessBoosters;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        updateIdlePowerUsage();
        invalidateEndpointCache();
        setChanged();
        markForClientUpdate();
        this.pendingRangeRefresh = true;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
    }

    public int getBoundTargetCount() {
        return getBoundTargetSummaries(Integer.MAX_VALUE).size();
    }

    public List<String> getBoundTargetDisplayLines(int maxLines) {
        if (this.level == null || maxLines <= 0) {
            return List.of();
        }

        ArrayList<String> lines = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity) {
                continue;
            }

            BlockState state = this.level.getBlockState(pos);
            Block block = state.getBlock();
            String name = block.getName().getString();
            lines.add(name);
        }

        lines.sort(String::compareToIgnoreCase);
        if (lines.size() > maxLines) {
            return List.copyOf(lines.subList(0, maxLines));
        }
        return List.copyOf(lines);
    }

    public List<BoundTargetSummary> getBoundTargetSummaries(int maxEntries) {
        if (this.level == null || maxEntries <= 0) {
            return List.of();
        }

        cleanupInvalidBoundTargets();

        ArrayList<BoundTargetSummary> results = new ArrayList<>();
        for (DisplayTarget target : collectDisplayTargets()) {
            BlockPos pos = target.pos();
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (appendCableBusSummaries(results, blockEntity, pos, target.kind(), maxEntries)) {
                if (results.size() >= maxEntries) {
                    break;
                }
                continue;
            }

            BlockState state = this.level.getBlockState(pos);
            Block block = state.getBlock();
            Item item = block.asItem();
            if (item == Items.AIR) {
                item = Items.BARRIER;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String displayName = resolveTargetDisplayName(state, blockEntity);
            results.add(new BoundTargetSummary(itemId, displayName, 1, this.level.dimension().location(), pos.immutable(), target.kind()));
            if (results.size() >= maxEntries) {
                break;
            }
        }

        if (results.size() > maxEntries) {
            return List.copyOf(results.subList(0, maxEntries));
        }
        return List.copyOf(results);
    }

    private boolean appendCableBusSummaries(List<BoundTargetSummary> results, @Nullable BlockEntity blockEntity, BlockPos pos,
                                            TargetKind kind, int maxEntries) {
        if (!(blockEntity instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        CableBusContainer cableBus = cableBusBlockEntity.getCableBus();
        boolean addedAny = false;

        addedAny |= appendPartSummary(results, cableBus.getPart(null), pos, kind, maxEntries, null, "", "");
        if (results.size() >= maxEntries) {
            return true;
        }

        ArrayList<CableBusSidePart> sideParts = new ArrayList<>();
        for (var direction : net.minecraft.core.Direction.values()) {
            IPart part = cableBus.getPart(direction);
            if (part != null) {
                sideParts.add(new CableBusSidePart(part, direction));
            }
        }

        for (int i = 0; i < sideParts.size(); i++) {
            CableBusSidePart sidePart = sideParts.get(i);
            String prefix = i == sideParts.size() - 1 ? "└ " : "├ ";
            String suffix = "";
            addedAny |= appendPartSummary(results, sidePart.part(), pos, kind, maxEntries, sidePart.direction(), prefix, suffix);
            if (results.size() >= maxEntries) {
                return true;
            }
        }

        return addedAny;
    }

    private boolean appendPartSummary(List<BoundTargetSummary> results, @Nullable IPart part, BlockPos pos, TargetKind kind,
                                      int maxEntries, @Nullable net.minecraft.core.Direction direction,
                                      String prefix, String suffix) {
        if (part == null || this.level == null || results.size() >= maxEntries) {
            return false;
        }

        Item item = resolvePartItem(part);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String displayName = resolvePartDisplayName(part, item, direction, prefix, suffix);
        results.add(new BoundTargetSummary(itemId, displayName, 1, this.level.dimension().location(), pos.immutable(), kind));
        return true;
    }

    private Item resolvePartItem(IPart part) {
        IPartItem<?> partItem = part.getPartItem();
        if (partItem instanceof Item item) {
            return item;
        }
        if (partItem instanceof ItemLike itemLike) {
            Item item = itemLike.asItem();
            if (item != Items.AIR) {
                return item;
            }
        }
        return Items.BARRIER;
    }

    private String resolvePartDisplayName(IPart part, Item item, @Nullable net.minecraft.core.Direction direction,
                                          String prefix, String groupSuffix) {
        String directionSuffix = direction == null ? "" : " [" + formatDirection(direction) + "]";
        String suffix = directionSuffix + groupSuffix;
        if (part instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            if (displayName != null) {
                String resolved = displayName.getString();
                if (!resolved.isBlank()) {
                    return prefix + resolved + suffix;
                }
            }
        }

        if (item != Items.AIR) {
            String itemName = new ItemStack(item).getHoverName().getString();
            if (!itemName.isBlank()) {
                return prefix + itemName + suffix;
            }
        }

        return prefix + part.getClass().getSimpleName() + suffix;
    }

    private String formatDirection(net.minecraft.core.Direction direction) {
        return switch (direction) {
            case NORTH -> "\u5317";
            case SOUTH -> "\u5357";
            case WEST -> "\u897f";
            case EAST -> "\u4e1c";
            case UP -> "\u4e0a";
            case DOWN -> "\u4e0b";
        };
    }

    private List<DisplayTarget> collectDisplayTargets() {
        if (this.level == null) {
            return List.of();
        }

        cleanupInvalidBoundTargets();

        LinkedHashMap<BlockPos, TargetKind> positions = new LinkedHashMap<>();

        for (BlockPos pos : this.linkedPositions) {
            if (!this.level.getBlockState(pos).isAir()) {
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (!(blockEntity instanceof DataDistributionTowerBlockEntity)) {
                    positions.put(pos.immutable(), TargetKind.AE);
                }
            }
        }

        for (BlockPos pos : getCachedEndpoints()) {
            if (this.level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity) {
                continue;
            }
            IEnergyStorage storage = findAccessibleEnergyStorage(pos, true);
            if (storage != null && storage.canReceive()) {
                positions.putIfAbsent(pos.immutable(), TargetKind.FE);
            }
        }

        ArrayList<DisplayTarget> results = new ArrayList<>(positions.size());
        positions.forEach((pos, kind) -> results.add(new DisplayTarget(pos, kind)));
        return List.copyOf(results);
    }

    private String resolveTargetDisplayName(BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            if (displayName != null) {
                String resolved = displayName.getString();
                if (!resolved.isBlank()) {
                    return resolved;
                }
            }
        }

        Block block = state.getBlock();
        Item item = block.asItem();
        if (item != Items.AIR) {
            String itemName = new ItemStack(item).getHoverName().getString();
            if (!itemName.isBlank()) {
                return itemName;
            }
        }

        return block.getName().getString();
    }

    @Override
    public int getCustomAdHocChannels() {
        ChannelMode mode = AEConfig.instance().getChannelMode();
        if (mode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        return 32 * mode.getCableCapacityFactor();
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevelAccessor serverLevelAccessor) {
            Level level = serverLevelAccessor.getLevel();
            invalidateNearbyCaches(level, event.getPos());
            onPotentialNodeAdded(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevelAccessor serverLevelAccessor) {
            Level level = serverLevelAccessor.getLevel();
            invalidateNearbyCaches(level, event.getPos());
            onPotentialNodeRemoved(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        ensureBound(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        TOWER_CHUNK_POSITIONS.clear();
        boundServer = null;
    }

    public static void onPotentialNodeAdded(@NotNull Level level, @NotNull BlockPos targetPos) {
        IInWorldGridNodeHost targetNodeHost = GridHelper.getNodeHost(level, targetPos);
        if (targetNodeHost == null) {
            return;
        }

        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.isWithinTowerCoverage(targetPos)) {
                    continue;
                }

                if (tower.queueLink(targetPos, INITIAL_PENDING_DELAY)) {
                    tower.setChanged();
                }
            }
        }
    }

    public static void onPotentialNodeRemoved(@NotNull Level level, @NotNull BlockPos targetPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(targetPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.isWithinTowerCoverage(targetPos)) {
                    continue;
                }

                tower.removeTarget(targetPos);
            }
        }
    }

    public int scanNearbyConnectableNodes() {
        if (this.level == null) {
            return 0;
        }

        int added = 0;
        for (BlockEntity blockEntity : getNearbyBlockEntities()) {
            BlockPos pos = blockEntity.getBlockPos().immutable();
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            if (this.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null) == null) {
                continue;
            }
            if (queueLink(pos, 0)) {
                added++;
            }
        }

        if (added > 0) {
            this.setChanged();
        }
        return added;
    }

    private void processPendingLinks(IGridNode selfNode) {
        for (Iterator<Map.Entry<BlockPos, Integer>> iterator = this.pendingLinkPositions.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }

            BlockPos targetPos = entry.getKey();
            if (!this.level.isLoaded(targetPos)) {
                continue;
            }

            reconnectTarget(selfNode, targetPos);
            iterator.remove();
            break;
        }
    }

    private boolean isTowerActive() {
        return this.getMainNode().isActive();
    }

    private int getChunkRadius() {
        if (this.level != null && this.level.isClientSide()) {
            return this.syncedChunkRadius;
        }
        return computeChunkRadius();
    }

    private int computeChunkRadius() {
        ItemStack boosterStack = this.wirelessBoosters.getStackInSlot(0);
        int boosterCount = boosterStack.isEmpty() ? 0 : boosterStack.getCount();
        return Math.max(0, Config.dataDistributionTowerRange - 1 + boosterCount / BOOSTERS_PER_CHUNK_RING);
    }

    private int getCoveredChunkCount() {
        int diameter = computeChunkRadius() * 2 + 1;
        return diameter * diameter;
    }

    private double computeIdlePowerUsage() {
        return BASE_IDLE_POWER_USAGE
                + Math.max(0, getCoveredChunkCount() - 1) * IDLE_POWER_USAGE_PER_ADDITIONAL_CHUNK;
    }

    private void updateIdlePowerUsage() {
        this.getMainNode().setIdlePowerUsage(computeIdlePowerUsage());
    }

    private boolean isWithinTowerCoverage(BlockPos targetPos) {
        return isWithinChunkRange(this.worldPosition, targetPos, getChunkRadius())
                && targetPos.getY() >= this.worldPosition.getY() - VERTICAL_RANGE_BELOW
                && targetPos.getY() <= this.worldPosition.getY() + VERTICAL_RANGE_ABOVE;
    }

    private int getTransferBudgetPerTick() {
        return Config.dataDistributionTowerTransferPerTick;
    }

    private int getMaxLinkChannels() {
        IGridNode node = this.getMainNode().getNode();
        if (node != null) {
            return node.getMaxChannels();
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return getCustomAdHocChannels();
        }
        IPathingService pathingService = grid.getPathingService();
        ChannelMode mode = pathingService.getChannelMode();
        return mode == ChannelMode.INFINITE ? Integer.MAX_VALUE : 32 * mode.getCableCapacityFactor();
    }

    private void registerInChunkIndex() {
        ensureBound(this.level == null ? null : this.level.getServer());
        if (this.level == null) {
            return;
        }

        ChunkPos center = new ChunkPos(this.worldPosition);
        int chunkRadius = getChunkRadius();
        this.indexedChunkRadius = chunkRadius;
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                ChunkKey key = new ChunkKey(this.level, center.x + offsetX, center.z + offsetZ);
                TOWER_CHUNK_POSITIONS.computeIfAbsent(key, ignored -> new HashSet<>()).add(this.worldPosition.immutable());
            }
        }
    }

    private void unregisterFromChunkIndex() {
        if (this.level == null) {
            return;
        }

        ChunkPos center = new ChunkPos(this.worldPosition);
        int chunkRadius = this.indexedChunkRadius >= 0 ? this.indexedChunkRadius : getChunkRadius();
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                ChunkKey key = new ChunkKey(this.level, center.x + offsetX, center.z + offsetZ);
                Set<BlockPos> positions = TOWER_CHUNK_POSITIONS.get(key);
                if (positions != null) {
                    positions.remove(this.worldPosition);
                    if (positions.isEmpty()) {
                        TOWER_CHUNK_POSITIONS.remove(key);
                    }
                }
            }
        }
        this.indexedChunkRadius = -1;
    }

    private void invalidateEndpointCache() {
        this.lastEndpointCacheTick = Long.MIN_VALUE;
        this.cachedEndpoints = List.of();
    }

    private List<BlockPos> getCachedEndpoints() {
        if (this.level == null) {
            return List.of();
        }

        long gameTime = this.level.getGameTime();
        if (this.cachedEndpoints.isEmpty() || gameTime - this.lastEndpointCacheTick >= CACHE_TICKS) {
            refreshEndpointCache();
        }
        return this.cachedEndpoints;
    }

    private void refreshEndpointCache() {
        if (this.level == null) {
            this.cachedEndpoints = List.of();
            return;
        }

        LinkedHashSet<BlockPos> endpoints = new LinkedHashSet<>();
        for (BlockEntity blockEntity : getNearbyBlockEntities()) {
            BlockPos pos = blockEntity.getBlockPos().immutable();
            if (isTowerBlock(pos)) {
                continue;
            }
            if (hasAnyEnergyCapability(pos)) {
                endpoints.add(pos);
            }
        }

        this.cachedEndpoints = List.copyOf(endpoints);
        this.lastEndpointCacheTick = this.level.getGameTime();
    }

    private List<BlockEntity> getNearbyBlockEntities() {
        if (this.level == null) {
            return List.of();
        }

        ArrayList<BlockEntity> results = new ArrayList<>();
        ChunkPos center = new ChunkPos(this.worldPosition);
        int chunkRadius = getChunkRadius();
        int minChunkX = center.x - chunkRadius;
        int maxChunkX = center.x + chunkRadius;
        int minChunkZ = center.z - chunkRadius;
        int maxChunkZ = center.z + chunkRadius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (isWithinTowerCoverage(blockEntity.getBlockPos())) {
                        results.add(blockEntity);
                    }
                }
            }
        }

        return results;
    }

    private boolean hasAnyEnergyCapability(BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) {
            if (getEnergyStorageAt(pos, direction) != null) {
                return true;
            }
        }
        return getEnergyStorageAt(pos, null) != null;
    }

    private boolean isTowerBlock(BlockPos pos) {
        return this.level != null && this.level.getBlockState(pos).is(ModBlocks.DATA_DISTRIBUTION_TOWER.get());
    }

    @Nullable
    private IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable net.minecraft.core.Direction side) {
        if (this.level == null || isTowerBlock(pos)) {
            return null;
        }
        return this.level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
    }

    @Nullable
    private IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive) {
        for (var direction : net.minecraft.core.Direction.values()) {
            IEnergyStorage storage = getEnergyStorageAt(pos, direction);
            if (storage != null && (forReceive ? storage.canReceive() : storage.canExtract())) {
                return storage;
            }
        }

        IEnergyStorage internal = getEnergyStorageAt(pos, null);
        if (internal != null && (forReceive ? internal.canReceive() : internal.canExtract())) {
            return internal;
        }
        return null;
    }

    private List<DataDistributionTowerBlockEntity> collectTowerCluster() {
        if (this.level == null) {
            return List.of(this);
        }

        ArrayList<DataDistributionTowerBlockEntity> towers = new ArrayList<>();
        ArrayDeque<DataDistributionTowerBlockEntity> queue = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        queue.add(this);
        visited.add(this.worldPosition);

        while (!queue.isEmpty()) {
            DataDistributionTowerBlockEntity tower = queue.removeFirst();
            towers.add(tower);

            for (BlockPos linkedPos : tower.linkedPositions) {
                if (!visited.add(linkedPos)) {
                    continue;
                }

                BlockEntity blockEntity = this.level.getBlockEntity(linkedPos);
                if (blockEntity instanceof DataDistributionTowerBlockEntity nearbyTower) {
                    queue.add(nearbyTower);
                }
            }
        }

        return towers;
    }

    private List<EnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
        LinkedHashMap<BlockPos, IEnergyStorage> endpoints = new LinkedHashMap<>();
        for (DataDistributionTowerBlockEntity tower : collectTowerCluster()) {
            for (BlockPos pos : tower.getCachedEndpoints()) {
                if (excludedPos != null && excludedPos.equals(pos)) {
                    continue;
                }
                if (endpoints.containsKey(pos)) {
                    continue;
                }

                IEnergyStorage storage = tower.findAccessibleEnergyStorage(pos, forReceive);
                if (storage != null) {
                    endpoints.put(pos, storage);
                }
            }
        }

        ArrayList<EnergyEndpoint> result = new ArrayList<>(endpoints.size());
        endpoints.forEach((pos, storage) -> result.add(new EnergyEndpoint(pos, storage)));
        return result;
    }

    private boolean isClusterCoordinator() {
        List<DataDistributionTowerBlockEntity> towers = collectTowerCluster();
        DataDistributionTowerBlockEntity coordinator = this;
        for (DataDistributionTowerBlockEntity tower : towers) {
            if (compareBlockPos(tower.worldPosition, coordinator.worldPosition) < 0) {
                coordinator = tower;
            }
        }
        return coordinator == this;
    }

    private void performActiveRangeTransfer() {
        long remainingBudget = getTransferBudgetPerTick();
        this.cachedTransferBudgetHint = remainingBudget;

        if (AE2FluxIntegration.isAvailable() && remainingBudget > 0) {
            long simulatedExtract = Math.min(remainingBudget,
                    AE2FluxIntegration.extractEnergyFromOwnNetwork(this, remainingBudget, true));
            if (simulatedExtract > 0) {
                long simulatedInsert = distributeEnergyInRange(simulatedExtract, true, null);
                if (simulatedInsert > 0) {
                    long transferAmount = Math.min(simulatedExtract, simulatedInsert);
                    long actuallyExtracted = Math.min(transferAmount,
                            AE2FluxIntegration.extractEnergyFromOwnNetwork(this, transferAmount, false));
                    if (actuallyExtracted > 0) {
                        long actuallyInserted = distributeEnergyInRange(actuallyExtracted, false, null);
                        remainingBudget -= Math.min(actuallyExtracted, actuallyInserted);
                    }
                }
            }
        }

        List<EnergyEndpoint> sources = collectEnergyEndpoints(false, null);
        for (EnergyEndpoint source : sources) {
            if (remainingBudget <= 0) {
                break;
            }

            IEnergyStorage sourceStorage = source.storage();
            if (!sourceStorage.canExtract() || sourceStorage.getEnergyStored() <= 0) {
                continue;
            }

            int requested = clampEnergyRequest(remainingBudget);
            int simulatedExtract = sourceStorage.extractEnergy(requested, true);
            if (simulatedExtract <= 0) {
                continue;
            }

            long simulatedInsert = distributeEnergyInRange(simulatedExtract, true, source.pos());
            if (simulatedInsert <= 0) {
                continue;
            }

            int transferAmount = clampEnergyRequest(Math.min(simulatedExtract, simulatedInsert));
            int actuallyExtracted = sourceStorage.extractEnergy(transferAmount, false);
            if (actuallyExtracted <= 0) {
                continue;
            }

            long actuallyInserted = distributeEnergyInRange(actuallyExtracted, false, source.pos());
            remainingBudget -= Math.min(actuallyExtracted, actuallyInserted);
        }
    }

    private long distributeEnergyInRange(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (!isTowerActive() || amount <= 0) {
            return 0;
        }

        List<EnergyEndpoint> endpoints = collectEnergyEndpoints(true, excludedPos);
        if (endpoints.isEmpty()) {
            return 0;
        }

        long totalInserted = 0;
        long remaining = amount;
        ArrayList<EnergyEndpoint> active = new ArrayList<>(endpoints);

        while (remaining > 0 && !active.isEmpty()) {
            long progress = 0;
            long share = Math.max(1L, remaining / active.size());
            ArrayList<EnergyEndpoint> next = new ArrayList<>();

            for (EnergyEndpoint endpoint : active) {
                if (remaining <= 0) {
                    break;
                }

                IEnergyStorage storage = endpoint.storage();
                if (!storage.canReceive()) {
                    continue;
                }

                int requested = clampEnergyRequest(Math.min(remaining, share));
                int inserted = storage.receiveEnergy(requested, simulate);
                if (inserted > 0) {
                    totalInserted += inserted;
                    remaining -= inserted;
                    progress += inserted;
                }

                if (storage.canReceive()) {
                    next.add(endpoint);
                }
            }

            if (progress <= 0) {
                break;
            }
            active = next;
        }

        if (remaining > 0) {
            for (EnergyEndpoint endpoint : endpoints) {
                if (remaining <= 0) {
                    break;
                }

                IEnergyStorage storage = endpoint.storage();
                if (!storage.canReceive()) {
                    continue;
                }

                int inserted = storage.receiveEnergy(clampEnergyRequest(remaining), simulate);
                if (inserted > 0) {
                    totalInserted += inserted;
                    remaining -= inserted;
                }
            }
        }

        return totalInserted;
    }

    private int extractEnergyFromRange(int amount, boolean simulate, @Nullable BlockPos excludedPos) {
        return clampStoredAmount(extractEnergyFromRangeLong(amount, simulate, excludedPos));
    }

    private long extractEnergyFromRangeLong(long amount, boolean simulate, @Nullable BlockPos excludedPos) {
        if (!isTowerActive() || amount <= 0) {
            return 0;
        }

        List<EnergyEndpoint> endpoints = collectEnergyEndpoints(false, excludedPos);
        long totalExtracted = 0;
        long remaining = amount;
        ArrayList<EnergyEndpoint> active = new ArrayList<>(endpoints);

        while (remaining > 0 && !active.isEmpty()) {
            long progress = 0;
            long share = Math.max(1L, remaining / active.size());
            ArrayList<EnergyEndpoint> next = new ArrayList<>();

            for (EnergyEndpoint endpoint : active) {
                if (remaining <= 0) {
                    break;
                }

                IEnergyStorage storage = endpoint.storage();
                if (!storage.canExtract()) {
                    continue;
                }

                int requested = clampEnergyRequest(Math.min(remaining, share));
                int extracted = storage.extractEnergy(requested, simulate);
                if (extracted > 0) {
                    totalExtracted += extracted;
                    remaining -= extracted;
                    progress += extracted;
                }

                if (storage.canExtract() && storage.getEnergyStored() > 0) {
                    next.add(endpoint);
                }
            }

            if (progress <= 0) {
                break;
            }
            active = next;
        }

        if (remaining > 0) {
            for (EnergyEndpoint endpoint : endpoints) {
                if (remaining <= 0) {
                    break;
                }

                IEnergyStorage storage = endpoint.storage();
                if (!storage.canExtract()) {
                    continue;
                }

                int extracted = storage.extractEnergy(clampEnergyRequest(remaining), simulate);
                if (extracted > 0) {
                    totalExtracted += extracted;
                    remaining -= extracted;
                }
            }
        }

        if (remaining > 0 && AE2FluxIntegration.isAvailable()) {
            totalExtracted += AE2FluxIntegration.extractEnergyFromOwnNetwork(this, remaining, simulate);
        }

        return totalExtracted;
    }

    private long getTotalExtractableEnergy(@Nullable BlockPos excludedPos) {
        long total = 0L;
        for (EnergyEndpoint endpoint : collectEnergyEndpoints(false, excludedPos)) {
            total = saturatingAdd(total, endpoint.storage().getEnergyStored());
        }
        if (AE2FluxIntegration.isAvailable()) {
            total = saturatingAdd(total, AE2FluxIntegration.extractEnergyFromOwnNetwork(this, Long.MAX_VALUE, true));
        }
        return total;
    }

    private long getTotalEnergyCapacity(@Nullable BlockPos excludedPos) {
        long total = 0L;
        for (EnergyEndpoint endpoint : collectEnergyEndpoints(false, excludedPos)) {
            total = saturatingAdd(total, endpoint.storage().getMaxEnergyStored());
        }
        return total;
    }

    private boolean hasAnyReceiver(@Nullable BlockPos excludedPos) {
        return isTowerActive() && !collectEnergyEndpoints(true, excludedPos).isEmpty();
    }

    private boolean hasAnySource(@Nullable BlockPos excludedPos) {
        return isTowerActive() && !collectEnergyEndpoints(false, excludedPos).isEmpty();
    }

    private boolean queueLink(BlockPos targetPos, int delay) {
        if (this.worldPosition.equals(targetPos) || !isWithinTowerCoverage(targetPos)) {
            return false;
        }

        Integer existingDelay = this.pendingLinkPositions.get(targetPos);
        if (existingDelay == null || existingDelay > delay) {
            this.pendingLinkPositions.put(targetPos.immutable(), delay);
            return true;
        }
        return false;
    }

    private void cleanupInvalidBoundTargets() {
        if (this.level == null || this.linkedPositions.isEmpty()) {
            return;
        }

        ArrayList<BlockPos> invalidPositions = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
            if (this.level.getBlockState(pos).isAir()) {
                invalidPositions.add(pos);
            }
        }

        for (BlockPos pos : invalidPositions) {
            removeTarget(pos);
        }
    }

    private void removeTarget(BlockPos targetPos) {
        this.pendingLinkPositions.remove(targetPos);
        this.linkedPositions.remove(targetPos);

        List<IGridConnection> existingConnections = this.linkedConnections.remove(targetPos);
        if (existingConnections != null) {
            for (IGridConnection connection : existingConnections) {
                if (connection != null) {
                    connection.destroy();
                }
            }
        }

        this.invalidateEndpointCache();
        this.setChanged();
    }

    private void destroyAllConnections() {
        for (List<IGridConnection> connections : this.linkedConnections.values()) {
            for (IGridConnection connection : connections) {
                if (connection != null) {
                    connection.destroy();
                }
            }
        }
        this.linkedConnections.clear();
    }

    private void reconnectTarget(IGridNode selfNode, BlockPos targetPos) {
        List<IGridConnection> oldConnections = this.linkedConnections.remove(targetPos);
        if (oldConnections != null) {
            for (IGridConnection connection : oldConnections) {
                if (connection != null) {
                    connection.destroy();
                }
            }
        }

        this.linkedPositions.remove(targetPos);

        List<IGridNode> targetNodes = getConnectableNodes(this.level, targetPos);
        if (targetNodes.isEmpty()) {
            this.setChanged();
            return;
        }

        ArrayList<IGridConnection> newConnections = new ArrayList<>();
        for (IGridNode targetNode : targetNodes) {
            if (targetNode == null || targetNode == selfNode) {
                continue;
            }

            IGrid targetGrid = targetNode.getGrid();
            IGrid selfGrid = selfNode.getGrid();
            if (targetGrid != null && selfGrid != null) {
                if (targetGrid.getPathingService().getControllerState() != ControllerState.NO_CONTROLLER) {
                    if (targetGrid != selfGrid) {
                        continue;
                    } else if (targetNode.meetsChannelRequirements()) {
                        continue;
                    }
                }
            }

            try {
                newConnections.add(GridHelper.createConnection(selfNode, targetNode));
            } catch (IllegalStateException ignored) {
            }
        }

        if (newConnections.isEmpty()) {
            this.setChanged();
            return;
        }

        this.linkedConnections.put(targetPos.immutable(), newConnections);
        this.linkedPositions.add(targetPos.immutable());
        this.invalidateEndpointCache();
        this.setChanged();
    }

    private void requeuePersistedLinks() {
        if (this.linkedPositions.isEmpty()) {
            return;
        }

        List<BlockPos> persisted = List.copyOf(this.linkedPositions);
        this.linkedPositions.clear();
        for (BlockPos pos : persisted) {
            queueLink(pos, INITIAL_PENDING_DELAY);
        }
    }

    public static List<IGridNode> getConnectableNodes(Level level, BlockPos pos) {
        ArrayList<IGridNode> nodes = new ArrayList<>();
        IInWorldGridNodeHost nodeHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (nodeHost == null || nodeHost instanceof ControllerBlockEntity) {
            return nodes;
        }

        if (nodeHost instanceof CableBusBlockEntity cableBusBlockEntity) {
            CableBusContainer cableBus = cableBusBlockEntity.getCableBus();
            IPart center = cableBus.getPart(null);
            if (center != null) {
                nodes.add(center.getGridNode());
            } else {
                for (var direction : net.minecraft.core.Direction.values()) {
                    IPart part = cableBus.getPart(direction);
                    if (part != null) {
                        nodes.add(part.getGridNode());
                    }
                }
            }
        } else {
            for (var direction : net.minecraft.core.Direction.values()) {
                IGridNode node = nodeHost.getGridNode(direction);
                if (node != null) {
                    nodes.add(node);
                    break;
                }
            }
        }

        nodes.removeIf(Objects::isNull);
        return nodes;
    }

    private static void invalidateNearbyCaches(Level level, BlockPos changedPos) {
        Set<BlockPos> towerPositions = TOWER_CHUNK_POSITIONS.get(new ChunkKey(level, new ChunkPos(changedPos)));
        if (towerPositions == null || towerPositions.isEmpty()) {
            return;
        }

        for (BlockPos towerPos : new HashSet<>(towerPositions)) {
            BlockEntity blockEntity = level.getBlockEntity(towerPos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity tower) {
                if (!tower.isWithinTowerCoverage(changedPos)) {
                    continue;
                }

                tower.invalidateEndpointCache();
            }
        }
    }

    private static void ensureBound(@Nullable MinecraftServer server) {
        if (server == null) {
            TOWER_CHUNK_POSITIONS.clear();
            boundServer = null;
            return;
        }

        if (boundServer != server) {
            TOWER_CHUNK_POSITIONS.clear();
            boundServer = server;
        }
    }

    private static boolean isWithinChunkRange(BlockPos source, BlockPos target, int chunkRadius) {
        ChunkPos sourceChunk = new ChunkPos(source);
        ChunkPos targetChunk = new ChunkPos(target);
        return Math.abs(sourceChunk.x - targetChunk.x) <= chunkRadius
                && Math.abs(sourceChunk.z - targetChunk.z) <= chunkRadius;
    }

    private static int compareBlockPos(BlockPos a, BlockPos b) {
        int cmp = Integer.compare(a.getX(), b.getX());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.getY(), b.getY());
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static int clampEnergyRequest(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static int clampStoredAmount(long amount) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static long saturatingAdd(long current, long delta) {
        if (delta <= 0) {
            return current;
        }
        if (Long.MAX_VALUE - current < delta) {
            return Long.MAX_VALUE;
        }
        return current + delta;
    }

    private static String formatFeAmount(long amount) {
        if (amount >= 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fG", amount / 1_000_000_000.0);
        }
        if (amount >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", amount / 1_000_000.0);
        }
        if (amount >= 1_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", amount / 1_000.0);
        }
        return Long.toString(amount);
    }

    private void applyPendingRangeRefresh() {
        this.pendingRangeRefresh = false;

        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        int oldChunkRadius = this.indexedChunkRadius >= 0 ? this.indexedChunkRadius : getChunkRadius();
        int newChunkRadius = getChunkRadius();

        if (oldChunkRadius != newChunkRadius) {
            unregisterFromChunkIndex();
            registerInChunkIndex();
        }

        pruneTargetsOutsideRange();
        invalidateEndpointCache();
        scanNearbyConnectableNodes();
    }

    private void pruneTargetsOutsideRange() {
        ArrayList<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : this.linkedPositions) {
            if (!isWithinTowerCoverage(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : this.pendingLinkPositions.keySet()) {
            if (!isWithinTowerCoverage(pos) && !toRemove.contains(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos pos : toRemove) {
            removeTarget(pos);
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack boosters = this.wirelessBoosters.getStackInSlot(0);
        if (!boosters.isEmpty()) {
            drops.add(boosters.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.wirelessBoosters.setItemDirect(0, ItemStack.EMPTY);
        updateIdlePowerUsage();
    }

    private record ChunkKey(Object dimension, int x, int z) {
        private ChunkKey(Level level, int x, int z) {
            this(level.dimension(), x, z);
        }

        private ChunkKey(Level level, ChunkPos pos) {
            this(level, pos.x, pos.z);
        }
    }

    private record EnergyEndpoint(BlockPos pos, IEnergyStorage storage) {
    }

    public record BoundTargetSummary(ResourceLocation itemId, String displayName, int count, ResourceLocation dimensionId, BlockPos pos, TargetKind kind) {
    }

    public enum TargetKind {
        AE,
        FE
    }

    private record DisplayTarget(BlockPos pos, TargetKind kind) {
    }

    private record CableBusSidePart(IPart part, net.minecraft.core.Direction direction) {
    }

    private class TowerEnergyStorage implements IEnergyStorage {
        @Nullable
        private final BlockPos excludedPos;

        private TowerEnergyStorage(@Nullable BlockPos excludedPos) {
            this.excludedPos = excludedPos;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return clampStoredAmount(distributeEnergyInRange(maxReceive, simulate, this.excludedPos));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return extractEnergyFromRange(maxExtract, simulate, this.excludedPos);
        }

        @Override
        public int getEnergyStored() {
            return clampStoredAmount(getTotalExtractableEnergy(this.excludedPos));
        }

        @Override
        public int getMaxEnergyStored() {
            return clampStoredAmount(getTotalEnergyCapacity(this.excludedPos));
        }

        @Override
        public boolean canExtract() {
            return hasAnySource(this.excludedPos);
        }

        @Override
        public boolean canReceive() {
            return hasAnyReceiver(this.excludedPos);
        }
    }

}
