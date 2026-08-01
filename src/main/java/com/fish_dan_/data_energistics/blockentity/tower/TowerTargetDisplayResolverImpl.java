package com.fish_dan_.data_energistics.blockentity.tower;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.BoundTargetSummary;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity.TargetKind;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.tower.AeCraftingDisplayBridge;
import com.fish_dan_.data_energistics.integration.tower.NeoEcoAeTowerBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.AECapabilities;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.parts.CableBusContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default target display resolver for Data Distribution Towers.
 */
public final class TowerTargetDisplayResolverImpl implements TowerTargetDisplayResolver {

    private final TowerTargetDisplayResolverContext context;
    private final NeoEcoAeTowerBridge neoEcoAeBridge;
    private final AeCraftingDisplayBridge aeCraftingDisplayBridge;

    /**
     * Creates a resolver for one tower.
     *
     * @param context                 tower target state used by the display resolver
     * @param neoEcoAeBridge          optional NeoECOAE display grouping bridge
     * @param aeCraftingDisplayBridge optional AE crafting display bridge
     */
    public TowerTargetDisplayResolverImpl(TowerTargetDisplayResolverContext context,
                                          NeoEcoAeTowerBridge neoEcoAeBridge,
                                          AeCraftingDisplayBridge aeCraftingDisplayBridge) {
        this.context = context;
        this.neoEcoAeBridge = neoEcoAeBridge;
        this.aeCraftingDisplayBridge = aeCraftingDisplayBridge;
    }

    @Override
    public int boundTargetCount() {
        return boundTargetSummaries(Integer.MAX_VALUE).size();
    }

    @Override
    public List<BoundTargetSummary> boundTargetSummaries(int maxEntries) {
        Level level = this.context.level();
        if (level == null || maxEntries <= 0) {
            return List.of();
        }

        this.context.cleanupInvalidDisplayTargets();

        ArrayList<BoundTargetSummary> results = new ArrayList<>();
        for (DisplayTarget target : collectDisplayTargets()) {
            BlockPos pos = target.pos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (shouldHideFromBoundTargetDisplay(blockEntity)) {
                continue;
            }
            if (appendCableBusSummaries(results, level, blockEntity, pos, target.kind(), maxEntries)) {
                if (results.size() >= maxEntries) {
                    break;
                }
                continue;
            }

            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            Item item = block.asItem();
            if (item == Items.AIR) {
                item = Items.BARRIER;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String displayName = resolveTargetDisplayName(state, blockEntity);
            results.add(new BoundTargetSummary(itemId, displayName, 1, level.dimension().location(), pos.immutable(),
                    target.kind(), this.context.targetTransferMode(pos), this.context.targetTransferInfo(pos)));
            if (results.size() >= maxEntries) {
                break;
            }
        }

        if (results.size() > maxEntries) {
            return List.copyOf(results.subList(0, maxEntries));
        }
        return List.copyOf(results);
    }

    @Override
    public boolean hasDisplayableAeTarget(BlockPos pos, @Nullable BlockEntity blockEntity) {
        if (this.context.level() == null) {
            return false;
        }

        if (shouldHideFromBoundTargetDisplay(blockEntity)) {
            return false;
        }

        if (blockEntity instanceof CableBusBlockEntity cableBusBlockEntity) {
            return hasAnyCableBusPart(cableBusBlockEntity.getCableBus());
        }

        if (this.context.hasExposedAeNode(pos)) {
            return true;
        }

        if (this.aeCraftingDisplayBridge.isDisplayComponent(blockEntity)) {
            return true;
        }

        return this.aeCraftingDisplayBridge.isClusterBridge(blockEntity);
    }

    @Override
    public boolean shouldHideFromBoundTargetDisplay(@Nullable BlockEntity blockEntity) {
        if (ModFlags.isNeoEcoAeTowerSupportLoaded() && this.neoEcoAeBridge.isSubsystemComponent(blockEntity)) {
            return !this.neoEcoAeBridge.isPreferredSubsystemHost(blockEntity);
        }
        return isAeCraftingNoiseTarget(blockEntity);
    }

    private boolean appendCableBusSummaries(List<BoundTargetSummary> results, Level level,
                                            @Nullable BlockEntity blockEntity, BlockPos pos,
                                            TargetKind kind, int maxEntries) {
        if (!(blockEntity instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        CableBusContainer cableBus = cableBusBlockEntity.getCableBus();
        boolean appended = false;
        IPart centerPart = cableBus.getPart(null);
        if (centerPart != null) {
            appended = appendPartSummary(results, level, centerPart, pos, kind, maxEntries, null, "");
            if (results.size() >= maxEntries) {
                return appended;
            }
        }

        ArrayList<CableBusDisplayPart> sideParts = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            IPart part = cableBus.getPart(direction);
            if (part != null) {
                sideParts.add(new CableBusDisplayPart(part, direction));
            }
        }

        for (int i = 0; i < sideParts.size() && results.size() < maxEntries; i++) {
            CableBusDisplayPart sidePart = sideParts.get(i);
            String prefix = centerPart != null ? (i == sideParts.size() - 1 ? "└" : "├") : "";
            if (appendPartSummary(
                    results, level, sidePart.part(), pos, kind, maxEntries, sidePart.direction(), prefix)) {
                appended = true;
            }
        }

        return appended;
    }

    private boolean appendPartSummary(List<BoundTargetSummary> results, Level level, IPart part, BlockPos pos, TargetKind kind,
                                      int maxEntries, @Nullable Direction direction, String prefix) {
        if (results.size() >= maxEntries) {
            return false;
        }

        Item item = resolvePartItem(part);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String displayName = resolvePartDisplayName(part, item, direction, prefix);
        results.add(new BoundTargetSummary(itemId, displayName, 1, level.dimension().location(), pos.immutable(), kind,
                this.context.targetTransferMode(pos), this.context.targetTransferInfo(pos)));
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

    private String resolvePartDisplayName(IPart part, Item item, @Nullable Direction direction, String prefix) {
        String directionSuffix = direction == null ? "" : " [" + direction.getName() + "]";
        if (part instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            String resolved = displayName.getString();
            if (!resolved.isBlank()) {
                return prefix + resolved + directionSuffix;
            }
        }

        if (item != Items.AIR) {
            String itemName = new ItemStack(item).getHoverName().getString();
            if (!itemName.isBlank()) {
                return prefix + itemName + directionSuffix;
            }
        }

        return prefix + Component.translatable("screen.data_energistics.data_distribution_tower.unknown_device").getString() + directionSuffix;
    }

    private List<DisplayTarget> collectDisplayTargets() {
        Level level = this.context.level();
        if (level == null) {
            return List.of();
        }

        this.context.cleanupInvalidDisplayTargets();

        LinkedHashMap<BlockPos, TargetKind> positions = new LinkedHashMap<>();

        for (BlockPos pos : this.context.trackedPositions()) {
            if (!level.getBlockState(pos).isAir()) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof DataDistributionTowerBlockEntity)) {
                    TargetKind kind = this.context.preferredDisplayKind(pos);
                    if (kind != null) {
                        positions.put(pos.immutable(), kind);
                    }
                }
            }
        }

        for (BlockPos pos : this.context.cachedAeDisplayTargets()) {
            if (this.context.allowsAeDisplayTargets() && !level.getBlockState(pos).isAir()) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof DataDistributionTowerBlockEntity)) {
                    positions.putIfAbsent(pos.immutable(), TargetKind.AE);
                }
            }
        }

        for (BlockPos pos : this.context.cachedEndpointPositions()) {
            if (level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity) {
                continue;
            }
            if (this.context.allowsAeDisplayTargets() && this.context.hasExposedAeNode(pos)) {
                positions.putIfAbsent(pos.immutable(), TargetKind.AE);
                continue;
            }
            if (!this.context.targetAllowsFe(pos)) {
                continue;
            }
            if (this.context.hasReceiveEnergyTarget(pos)) {
                positions.putIfAbsent(pos.immutable(), TargetKind.FE);
            }
        }

        for (BlockPos pos : this.context.configuredTargetPositions()) {
            if (!this.context.isWithinTowerCoverage(pos) || level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DataDistributionTowerBlockEntity || shouldHideFromBoundTargetDisplay(blockEntity)) {
                continue;
            }
            TargetKind kind = this.context.preferredDisplayKind(pos);
            if (kind != null) {
                positions.putIfAbsent(pos.immutable(), kind);
            }
        }

        collapseAeCraftingDisplayTargets(positions);

        ArrayList<DisplayTarget> results = new ArrayList<>(positions.size());
        positions.forEach((pos, kind) -> results.add(new DisplayTarget(pos, kind)));
        results.sort((left, right) -> compareBlockPos(left.pos(), right.pos()));
        return List.copyOf(results);
    }

    private void collapseAeCraftingDisplayTargets(LinkedHashMap<BlockPos, TargetKind> positions) {
        Level level = this.context.level();
        if (level == null || positions.isEmpty()) {
            return;
        }

        ArrayList<BlockPos> craftingPositions = new ArrayList<>();
        HashMap<BlockPos, BlockPos> clusterRepresentatives = new HashMap<>();
        for (Map.Entry<BlockPos, TargetKind> entry : positions.entrySet()) {
            if (entry.getValue() != TargetKind.AE) {
                continue;
            }

            BlockPos pos = entry.getKey();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!isAeCraftingClusterComponent(blockEntity)) {
                continue;
            }

            BlockPos representativePos = findAeCraftingClusterRepresentative(blockEntity);
            if (representativePos == null) {
                continue;
            }

            craftingPositions.add(pos);
            clusterRepresentatives.put(pos, representativePos);
        }

        if (craftingPositions.size() <= 1) {
            return;
        }

        for (BlockPos pos : craftingPositions) {
            BlockPos representativePos = clusterRepresentatives.get(pos);
            if (representativePos != null && !pos.equals(representativePos)) {
                positions.remove(pos);
            }
        }
    }

    private int compareAeCraftingDisplayTargets(BlockPos leftPos, BlockPos rightPos) {
        Level level = this.context.level();
        if (level == null) {
            return compareBlockPos(leftPos, rightPos);
        }

        int leftPriority = this.aeCraftingDisplayBridge.displayPriority(level.getBlockEntity(leftPos));
        int rightPriority = this.aeCraftingDisplayBridge.displayPriority(level.getBlockEntity(rightPos));
        if (leftPriority != rightPriority) {
            return Integer.compare(rightPriority, leftPriority);
        }

        return compareBlockPos(leftPos, rightPos);
    }

    private boolean isAeCraftingClusterComponent(@Nullable BlockEntity blockEntity) {
        return this.aeCraftingDisplayBridge.isDisplayComponent(blockEntity) && this.aeCraftingDisplayBridge.displayPriority(blockEntity) != 1;
    }

    private String resolveTargetDisplayName(BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof Nameable nameable) {
            Component displayName = nameable.getDisplayName();
            String resolved = displayName.getString();
            if (!resolved.isBlank() && !isFallbackAirName(resolved)) {
                return resolved;
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

    private boolean isFallbackAirName(String displayName) {
        return displayName.equals(Items.AIR.getDescription().getString()) || displayName.equals(Blocks.AIR.getName().getString());
    }

    @Nullable
    private BlockPos findAeCraftingClusterRepresentative(@Nullable BlockEntity blockEntity) {
        Level level = this.context.level();
        if (blockEntity == null || !isAeCraftingClusterComponent(blockEntity) || level == null) {
            return null;
        }

        BlockPos startPos = blockEntity.getBlockPos();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);
        BlockPos representative = startPos;

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.removeFirst();
            if (compareAeCraftingDisplayTargets(currentPos, representative) < 0) {
                representative = currentPos;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = currentPos.relative(direction);
                if (!visited.add(neighborPos)) {
                    continue;
                }

                BlockEntity neighbor = level.getBlockEntity(neighborPos);
                if (!isAeCraftingClusterComponent(neighbor) && !this.aeCraftingDisplayBridge.isClusterBridge(neighbor)) {
                    continue;
                }

                queue.addLast(neighborPos);
            }
        }

        return representative;
    }

    private boolean isAeCraftingNoiseTarget(@Nullable BlockEntity blockEntity) {
        Level level = this.context.level();
        if (blockEntity == null || level == null) {
            return false;
        }
        if (this.aeCraftingDisplayBridge.isDisplayComponent(blockEntity) || this.aeCraftingDisplayBridge.isClusterBridge(blockEntity)) {
            return false;
        }
        if (blockEntity instanceof CableBusBlockEntity) {
            return false;
        } else if (level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, blockEntity.getBlockPos(), null) == null) {
            return false;
        }

        BlockPos pos = blockEntity.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (this.aeCraftingDisplayBridge.isClusterNode(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyCableBusPart(CableBusContainer cableBus) {
        if (cableBus.getPart(null) != null) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (cableBus.getPart(direction) != null) {
                return true;
            }
        }

        return false;
    }

    static int compareBlockPos(BlockPos a, BlockPos b) {
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

    private record CableBusDisplayPart(IPart part, Direction direction) {}

    private record DisplayTarget(BlockPos pos, TargetKind kind) {}
}
