package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.block.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.world.TeleportAnchorSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.InternalInventory;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataTeleportAnchorBlockEntity extends AENetworkedPoweredBlockEntity {

    public static final double ENERGY_CAPACITY = 40_000.0D;
    public static final double TELEPORT_ENERGY_COST = 10_000.0D;
    private static final long ANCHOR_PRUNE_INTERVAL_TICKS = 100L;
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";
    private static final String HAS_TARGET_TAG = "has_target";
    private static final String TARGET_DIMENSION_TAG = "target_dimension";
    private static final String TARGET_X_TAG = "target_x";
    private static final String TARGET_Y_TAG = "target_y";
    private static final String TARGET_Z_TAG = "target_z";
    private static final int TELEPORT_RADIUS = 1;
    private static final int TELEPORT_HEIGHT = 3;
    private static final Map<ResourceLocation, Map<BlockPos, DataTeleportAnchorBlockEntity>> LOADED_ANCHORS = new HashMap<>();

    private boolean redstoneControlled;
    private boolean hasTarget;
    private ResourceLocation targetDimension = Level.OVERWORLD.location();
    private BlockPos targetPos = BlockPos.ZERO;
    private long lastAnchorPruneGameTime = Long.MIN_VALUE;

    public DataTeleportAnchorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_TELEPORT_ANCHOR.get())
                .setExposedOnSides(getCableExposedSides(blockState))
                .setIdlePowerUsage(0.0D);
        this.setInternalMaxPower(ENERGY_CAPACITY);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        if (!isCableSideExposed(dir)) {
            return AECableType.NONE;
        }

        return AECableType.COVERED;
    }

    private boolean isCableSideExposed(Direction dir) {
        return dir != Direction.UP;
    }

    private static Set<Direction> getCableExposedSides(BlockState blockState) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        return sides;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
        if (this.level instanceof ServerLevel serverLevel) {
            registerLoadedAnchor(serverLevel);
            setOwnChunkForced(serverLevel, true);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            unregisterRuntimeAnchor(serverLevel);
        }
        super.setRemoved();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (this.level instanceof ServerLevel serverLevel) {
            registerLoadedAnchor(serverLevel);
        }
        refillEnergyBuffer();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline() && (!this.redstoneControlled || isReceivingRedstonePower());
    }

    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    public boolean setRedstoneControlled(boolean enabled) {
        if (this.redstoneControlled == enabled) {
            return this.redstoneControlled;
        }

        this.redstoneControlled = enabled;
        this.saveChanges();
        updateOnlineState();
        this.markForClientUpdate();
        return this.redstoneControlled;
    }

    public boolean hasTarget() {
        return this.hasTarget;
    }

    public ResourceLocation getTargetDimension() {
        return this.targetDimension;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public String getAnchorDimensionId() {
        return this.level == null ? Level.OVERWORLD.location().toString() : this.level.dimension().location().toString();
    }

    public String getAnchorDisplayName() {
        String resolvedName = this.getDisplayName().getString();
        return resolvedName.isBlank() ? "Data Teleport Anchor" : resolvedName;
    }

    public String getChannelId() {
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof DataTeleportAnchorBlock && state.hasProperty(DataTeleportAnchorBlock.COLOR)) {
            return state.getValue(DataTeleportAnchorBlock.COLOR).getSerializedName();
        }
        return "default";
    }

    public List<AnchorSummary> getAvailableAnchors() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        pruneInvalidAnchorsIfNeeded(serverLevel);
        String ownChannel = getChannelId();
        ArrayList<AnchorSummary> anchors = new ArrayList<>();
        for (var record : TeleportAnchorSavedData.get(serverLevel.getServer()).getAnchors()) {
            if (isSelfAnchor(record.dimensionId(), record.pos())) {
                continue;
            }
            if (!channelsCanConnect(ownChannel, record.channel())) {
                continue;
            }
            anchors.add(new AnchorSummary(record.name(), record.dimensionId().toString(), record.pos().immutable(), record.channel()));
        }

        anchors.sort(Comparator
                .comparing(AnchorSummary::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AnchorSummary::dimensionId)
                .thenComparing(summary -> summary.pos().getX())
                .thenComparing(summary -> summary.pos().getY())
                .thenComparing(summary -> summary.pos().getZ()));
        return List.copyOf(anchors);
    }

    public TeleportResult teleportEntitiesToRecordedTarget() {
        if (!this.hasTarget) {
            return new TeleportResult(TeleportStatus.NO_RECORDED_TARGET, 0);
        }
        return this.teleportEntitiesTo(this.targetDimension, this.targetPos, false);
    }

    public TeleportResult teleportEntitiesTo(ResourceLocation targetDimensionId, BlockPos targetAnchorPos) {
        return teleportEntitiesTo(targetDimensionId, targetAnchorPos, true);
    }

    private TeleportResult teleportEntitiesTo(ResourceLocation targetDimensionId, BlockPos targetAnchorPos,
                                              boolean requireSourcePower) {
        if (!(this.level instanceof ServerLevel sourceLevel)) {
            return new TeleportResult(TeleportStatus.TARGET_NOT_FOUND, 0);
        }
        if (!isOnline()) {
            return new TeleportResult(TeleportStatus.SOURCE_OFFLINE, 0);
        }
        if (isSelfAnchor(targetDimensionId, targetAnchorPos)) {
            return new TeleportResult(TeleportStatus.SELF_TARGET, 0);
        }

        var targetLevelKey = ResourceKey.create(Registries.DIMENSION, targetDimensionId);
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(targetLevelKey);
        if (targetLevel == null) {
            TeleportAnchorSavedData.get(sourceLevel.getServer()).removeAnchor(targetDimensionId, targetAnchorPos);
            return new TeleportResult(TeleportStatus.TARGET_NOT_FOUND, 0);
        }

        DataTeleportAnchorBlockEntity targetAnchor = getLoadedAnchor(targetLevel, targetAnchorPos);
        if (targetAnchor == null) {
            TeleportAnchorSavedData.get(sourceLevel.getServer()).removeAnchor(targetDimensionId, targetAnchorPos);
            return new TeleportResult(TeleportStatus.TARGET_NOT_FOUND, 0);
        }
        if (!channelsCanConnect(getChannelId(), targetAnchor.getChannelId())) {
            return new TeleportResult(TeleportStatus.CHANNEL_MISMATCH, 0);
        }
        if (!targetAnchor.isOnline()) {
            return new TeleportResult(TeleportStatus.TARGET_OFFLINE, 0);
        }
        if (requireSourcePower && !hasRequiredTeleportEnergy()) {
            return new TeleportResult(TeleportStatus.INSUFFICIENT_POWER, 0);
        }

        AABB sourceArea = getTeleportArea(this.worldPosition);
        AABB targetArea = getTeleportArea(targetAnchor.getBlockPos());
        List<Entity> entities = sourceLevel.getEntities((Entity) null, sourceArea,
                entity -> entity.isAlive() && entity.getVehicle() == null);
        if (entities.isEmpty()) {
            return new TeleportResult(TeleportStatus.NO_ENTITIES, 0);
        }

        int teleportedCount = 0;
        for (Entity entity : entities) {
            Vec3 targetPos = remapEntityPosition(entity.position(), sourceArea, targetArea);
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(targetLevel, targetPos.x, targetPos.y, targetPos.z, Set.of(),
                        player.getYRot(), player.getXRot());
                player.fallDistance = 0.0F;
                teleportedCount++;
                continue;
            }

            if (entity.level() == targetLevel) {
                entity.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                entity.fallDistance = 0.0F;
                teleportedCount++;
                continue;
            }

            Entity movedEntity = entity.changeDimension(new DimensionTransition(
                    targetLevel,
                    targetPos,
                    entity.getDeltaMovement(),
                    entity.getYRot(),
                    entity.getXRot(),
                    DimensionTransition.DO_NOTHING));
            if (movedEntity != null) {
                movedEntity.fallDistance = 0.0F;
                teleportedCount++;
            }
        }

        if (teleportedCount > 0) {
            if (requireSourcePower) {
                consumeAllStoredEnergy();
            }
            return new TeleportResult(TeleportStatus.SUCCESS, teleportedCount);
        }
        return new TeleportResult(TeleportStatus.NO_ENTITIES, 0);
    }

    public void recordTarget(ServerPlayer player) {
        this.hasTarget = true;
        this.targetDimension = player.level().dimension().location();
        this.targetPos = player.blockPosition();
        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.hasTarget = data.getBoolean(HAS_TARGET_TAG);
        if (data.contains(TARGET_DIMENSION_TAG)) {
            this.targetDimension = ResourceLocation.parse(data.getString(TARGET_DIMENSION_TAG));
        } else {
            this.targetDimension = Level.OVERWORLD.location();
        }
        this.targetPos = new BlockPos(
                data.getInt(TARGET_X_TAG),
                data.getInt(TARGET_Y_TAG),
                data.getInt(TARGET_Z_TAG));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        data.putBoolean(HAS_TARGET_TAG, this.hasTarget);
        data.putString(TARGET_DIMENSION_TAG, this.targetDimension.toString());
        data.putInt(TARGET_X_TAG, this.targetPos.getX());
        data.putInt(TARGET_Y_TAG, this.targetPos.getY());
        data.putInt(TARGET_Z_TAG, this.targetPos.getZ());
    }

    private boolean isReceivingRedstonePower() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    private boolean isSelfAnchor(ResourceLocation dimensionId, BlockPos pos) {
        return this.level != null && this.level.dimension().location().equals(dimensionId) && this.worldPosition.equals(pos);
    }

    private static boolean channelsCanConnect(String sourceChannel, String targetChannel) {
        if ("default".equals(sourceChannel) || "default".equals(targetChannel)) {
            return true;
        }
        return sourceChannel.equals(targetChannel);
    }

    private boolean hasRequiredTeleportEnergy() {
        return this.getAECurrentPower() + 0.0001D >= TELEPORT_ENERGY_COST;
    }

    private void consumeAllStoredEnergy() {
        double extracted = Math.min(this.getAECurrentPower(), TELEPORT_ENERGY_COST);
        if (extracted > 0.0001D) {
            this.extractAEPower(extracted, Actionable.MODULATE, PowerMultiplier.ONE);
        }
        this.saveChanges();
        this.markForClientUpdate();
    }

    private void refillEnergyBuffer() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        var node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 0.0001D) {
            return;
        }

        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0001D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void pruneInvalidAnchorsIfNeeded(ServerLevel serverLevel) {
        long gameTime = serverLevel.getGameTime();
        if (this.lastAnchorPruneGameTime != Long.MIN_VALUE && gameTime - this.lastAnchorPruneGameTime < ANCHOR_PRUNE_INTERVAL_TICKS) {
            return;
        }

        TeleportAnchorSavedData.get(serverLevel.getServer()).pruneMissingAnchors(serverLevel.getServer());
        this.lastAnchorPruneGameTime = gameTime;
    }

    private void registerLoadedAnchor(ServerLevel serverLevel) {
        TeleportAnchorSavedData.get(serverLevel.getServer())
                .registerAnchor(serverLevel.dimension().location(), this.worldPosition, getAnchorDisplayName(), getChannelId());
        LOADED_ANCHORS
                .computeIfAbsent(serverLevel.dimension().location(), ignored -> new HashMap<>())
                .put(this.worldPosition.immutable(), this);
    }

    public void removePersistedAnchor() {
        if (this.level instanceof ServerLevel serverLevel) {
            TeleportAnchorSavedData.get(serverLevel.getServer())
                    .removeAnchor(serverLevel.dimension().location(), this.worldPosition);
            setOwnChunkForced(serverLevel, false);
            unregisterRuntimeAnchor(serverLevel);
        }
    }

    private void unregisterRuntimeAnchor(ServerLevel serverLevel) {
        Map<BlockPos, DataTeleportAnchorBlockEntity> anchorsByPos = LOADED_ANCHORS.get(serverLevel.dimension().location());
        if (anchorsByPos == null) {
            return;
        }
        anchorsByPos.remove(this.worldPosition);
        if (anchorsByPos.isEmpty()) {
            LOADED_ANCHORS.remove(serverLevel.dimension().location());
        }
    }

    public static void clearRuntimeAnchorCache() {
        LOADED_ANCHORS.clear();
    }

    private void setOwnChunkForced(ServerLevel serverLevel, boolean forced) {
        ChunkPos chunkPos = new ChunkPos(this.worldPosition);
        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, forced);
    }

    private static boolean isAnchorValid(ServerLevel level, BlockPos pos, DataTeleportAnchorBlockEntity anchor) {
        if (anchor == null || anchor.isRemoved() || anchor.level != level) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof DataTeleportAnchorBlockEntity;
    }

    private static DataTeleportAnchorBlockEntity getLoadedAnchor(ServerLevel level, BlockPos pos) {
        Map<BlockPos, DataTeleportAnchorBlockEntity> anchorsByPos = LOADED_ANCHORS.get(level.dimension().location());
        if (anchorsByPos != null) {
            DataTeleportAnchorBlockEntity anchor = anchorsByPos.get(pos);
            if (isAnchorValid(level, pos, anchor)) {
                return anchor;
            }
            if (anchor != null) {
                anchorsByPos.remove(pos);
                if (anchorsByPos.isEmpty()) {
                    LOADED_ANCHORS.remove(level.dimension().location());
                }
            }
        }

        // Force-load the target chunk on demand so cross-dimension anchors remain usable after rejoin.
        level.getChunkAt(pos);

        if (level.getBlockEntity(pos) instanceof DataTeleportAnchorBlockEntity anchor) {
            LOADED_ANCHORS
                    .computeIfAbsent(level.dimension().location(), ignored -> new HashMap<>())
                    .put(pos.immutable(), anchor);
            return anchor;
        }
        return null;
    }

    private static AABB getTeleportArea(BlockPos anchorPos) {
        return new AABB(
                anchorPos.getX() - TELEPORT_RADIUS,
                anchorPos.getY() + 1,
                anchorPos.getZ() - TELEPORT_RADIUS,
                anchorPos.getX() + TELEPORT_RADIUS + 1,
                anchorPos.getY() + 1 + TELEPORT_HEIGHT,
                anchorPos.getZ() + TELEPORT_RADIUS + 1);
    }

    private static Vec3 remapEntityPosition(Vec3 sourcePos, AABB sourceArea, AABB targetArea) {
        double relativeX = clamp(sourcePos.x - sourceArea.minX, 0.0D, sourceArea.getXsize() - 0.001D);
        double relativeY = clamp(sourcePos.y - sourceArea.minY, 0.0D, sourceArea.getYsize() - 0.001D);
        double relativeZ = clamp(sourcePos.z - sourceArea.minZ, 0.0D, sourceArea.getZsize() - 0.001D);
        return new Vec3(
                targetArea.minX + relativeX,
                targetArea.minY + relativeY,
                targetArea.minZ + relativeZ);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateOnlineState() {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataTeleportAnchorBlock)) {
            return;
        }

        boolean online = isOnline();
        if (state.hasProperty(DataTeleportAnchorBlock.LIT) && state.getValue(DataTeleportAnchorBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataTeleportAnchorBlock.LIT, online), 3);
        }
    }

    public record AnchorSummary(String name, String dimensionId, BlockPos pos, String channelId) {}

    public record TeleportResult(TeleportStatus status, int entityCount) {}

    public enum TeleportStatus {
        SUCCESS,
        SOURCE_OFFLINE,
        TARGET_OFFLINE,
        CHANNEL_MISMATCH,
        SELF_TARGET,
        TARGET_NOT_FOUND,
        INSUFFICIENT_POWER,
        NO_RECORDED_TARGET,
        NO_ENTITIES
    }
}
