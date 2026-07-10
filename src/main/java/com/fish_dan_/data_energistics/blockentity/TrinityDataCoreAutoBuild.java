package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

final class TrinityDataCoreAutoBuild {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private TrinityDataCoreAutoBuild() {}

    static Stats buildPattern(ServerLevel serverLevel,
                              Player player,
                              StructureWorldView world,
                              BlockPattern pattern,
                              BlockPos origin,
                              String structureName,
                              Direction front,
                              boolean flipped) {
        Stats stats = new Stats();
        buildPattern(serverLevel, player, world, pattern, origin, structureName, front, flipped, stats);
        return stats;
    }

    static void buildPattern(ServerLevel serverLevel,
                             Player player,
                             StructureWorldView world,
                             BlockPattern pattern,
                             BlockPos origin,
                             String structureName,
                             Direction front,
                             boolean flipped,
                             Stats stats) {
        PatternCoordinates coordinates = new PatternCoordinates(pattern);
        MultiblockState state = new MultiblockState(world, origin, structureName);
        state.clean();
        int expandedZ = coordinates.minZ();
        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            int maximumRepetitions = pattern.aisleRepetitions[unit][1];
            for (int repeat = 0; repeat < maximumRepetitions; repeat++) {
                for (int inner = 0; inner < pattern.unitDepths[unit]; inner++) {
                    int z = pattern.unitStarts[unit] + inner;
                    state.getLayerCount().clear();
                    state.getStructureLayerCount().clear();
                    buildLayer(serverLevel, player, coordinates, state, origin, z, expandedZ, front, flipped, stats);
                    expandedZ++;
                }
            }
        }
    }

    private static void buildLayer(ServerLevel serverLevel,
                                   Player player,
                                   PatternCoordinates coordinates,
                                   MultiblockState state,
                                   BlockPos origin,
                                   int patternZ,
                                   int expandedZ,
                                   Direction front,
                                   boolean flipped,
                                   Stats stats) {
        for (int yOffset = coordinates.minY(); yOffset < coordinates.minY() + coordinates.thumbLength(); yOffset++) {
            for (int xOffset = coordinates.minX(); xOffset < coordinates.minX() + coordinates.palmLength(); xOffset++) {
                TraceabilityPredicate predicate = coordinates.predicate(patternZ, yOffset, xOffset);
                if (shouldSkipPredicate(predicate)) {
                    continue;
                }
                BlockPos target = origin.offset(coordinates.actualRelativeOffset(
                        xOffset,
                        yOffset,
                        expandedZ,
                        front,
                        Direction.NORTH,
                        flipped));
                if (target.equals(origin)) {
                    continue;
                }
                buildPosition(serverLevel, player, state, predicate, target, stats);
            }
        }
    }

    private static boolean shouldSkipPredicate(@Nullable TraceabilityPredicate predicate) {
        return predicate == null || predicate.isAny() || predicate.isAir();
    }

    static void buildPosition(ServerLevel serverLevel,
                              Player player,
                              MultiblockState state,
                              TraceabilityPredicate predicate,
                              BlockPos target,
                              Stats stats) {
        if (!serverLevel.isLoaded(target)) {
            stats.recordUnloaded();
            return;
        }
        if (!state.update(target, predicate)) {
            stats.recordUnloaded();
            return;
        }
        if (predicate.test(state)) {
            return;
        }
        BlockState currentState = serverLevel.getBlockState(target);
        if (!currentState.isAir() && !currentState.canBeReplaced()) {
            stats.recordBlocked();
            return;
        }
        Candidate candidate = findCandidate(player, predicate);
        if (candidate == null) {
            stats.recordMissing();
            return;
        }
        if (!placeCandidate(serverLevel, player, candidate, target)) {
            stats.recordPlaceFailed();
            return;
        }
        if (!state.update(target, predicate) || !predicate.test(state)) {
            if (candidate.desiredState() != null && applyDesiredState(serverLevel, target, candidate.desiredState()) &&
                    state.update(target, predicate) && predicate.test(state)) {
                stats.recordPlaced();
                player.getInventory().setChanged();
                return;
            }
            stats.recordPlaceFailed();
            player.getInventory().setChanged();
            return;
        }
        stats.recordPlaced();
        player.getInventory().setChanged();
    }

    @Nullable
    static Candidate findCandidate(Player player, TraceabilityPredicate predicate) {
        for (ItemStack candidateStack : predicate.placementCandidates()) {
            if (candidateStack.isEmpty()) {
                continue;
            }
            ItemStack stack = findStack(player, candidateStack);
            if (stack != null) {
                return new Candidate(stack, desiredStateFor(candidateStack, predicate));
            }
        }
        for (BlockState state : predicate.blockStateCandidates()) {
            if (state.isAir()) {
                continue;
            }
            Item item = state.getBlock().asItem();
            ItemStack candidateStack = item.getDefaultInstance();
            if (candidateStack.isEmpty()) {
                continue;
            }
            ItemStack stack = findStack(player, candidateStack);
            if (stack != null) {
                return new Candidate(stack, state);
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack findStack(Player player, ItemStack candidate) {
        if (player.isCreative()) {
            return candidate.copyWithCount(1);
        }
        return findInventoryStack(player.getInventory(), candidate);
    }

    @Nullable
    private static ItemStack findInventoryStack(Inventory inventory, ItemStack candidate) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, candidate)) {
                return stack;
            }
        }
        return null;
    }

    @Nullable
    private static BlockState desiredStateFor(ItemStack stack, TraceabilityPredicate predicate) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        for (BlockState state : predicate.blockStateCandidates()) {
            if (state.getBlock() == block) {
                return state;
            }
        }
        return block.defaultBlockState();
    }

    private static boolean placeCandidate(ServerLevel serverLevel,
                                          Player player,
                                          Candidate candidate,
                                          BlockPos target) {
        ItemStack stack = candidate.stack();
        if (stack.getItem() instanceof IPartItem<?> partItem) {
            return placePartCandidate(serverLevel, player, stack, partItem, target);
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            PlaceContext context = new PlaceContext(serverLevel, player, stack, target);
            InteractionResult result = blockItem.place(context);
            return result != InteractionResult.FAIL;
        }
        LOGGER.warn("Unsupported auto-build placement candidate {} at {}", stack, target);
        return false;
    }

    private static boolean placePartCandidate(ServerLevel serverLevel,
                                              Player player,
                                              ItemStack stack,
                                              IPartItem<?> partItem,
                                              BlockPos target) {
        if (PartHelper.setPart(serverLevel, target, null, player, partItem) == null) {
            return false;
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    private static boolean applyDesiredState(ServerLevel serverLevel, BlockPos target, BlockState desiredState) {
        BlockState currentState = serverLevel.getBlockState(target);
        if (currentState.getBlock() != desiredState.getBlock()) {
            return false;
        }
        if (currentState.equals(desiredState)) {
            return true;
        }
        return serverLevel.setBlock(target, desiredState, Block.UPDATE_ALL);
    }

    private static final class PatternCoordinates {

        private final BlockPattern pattern;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int fingerLength;
        private final int thumbLength;
        private final int palmLength;

        private PatternCoordinates(BlockPattern pattern) {
            this.pattern = pattern;
            this.minX = pattern.getMinX();
            this.minY = pattern.getMinY();
            this.minZ = pattern.getMinZ();
            this.fingerLength = pattern.getFingerLength();
            this.thumbLength = pattern.getThumbLength();
            this.palmLength = pattern.getPalmLength();
        }

        private int minX() {
            return this.minX;
        }

        private int minY() {
            return this.minY;
        }

        private int minZ() {
            return this.minZ;
        }

        private int thumbLength() {
            return this.thumbLength;
        }

        private int palmLength() {
            return this.palmLength;
        }

        @Nullable
        private TraceabilityPredicate predicate(int z, int yOffset, int xOffset) {
            if (z < 0 || z >= this.fingerLength) {
                throw new IllegalArgumentException("Pattern z offset is outside the pattern: " + z);
            }
            int y = yOffset - this.minY;
            int x = xOffset - this.minX;
            if (y < 0 || y >= this.thumbLength || x < 0 || x >= this.palmLength) {
                throw new IllegalArgumentException("Pattern x/y offset is outside the pattern: " + xOffset + ", " +
                        yOffset);
            }
            return this.pattern.getPredicate(z, y, x);
        }

        private BlockPos actualRelativeOffset(int xOffset,
                                              int yOffset,
                                              int expandedZ,
                                              Direction front,
                                              Direction upwards,
                                              boolean flipped) {
            return this.pattern.getActualRelativeOffset(xOffset, yOffset, expandedZ, front, upwards, flipped);
        }
    }

    static final class Stats {

        private int placed;
        private int missing;
        private int blocked;
        private int unloaded;
        private int placeFailed;

        int placed() {
            return this.placed;
        }

        int missing() {
            return this.missing;
        }

        int blocked() {
            return this.blocked;
        }

        int unloaded() {
            return this.unloaded;
        }

        int placeFailed() {
            return this.placeFailed;
        }

        private void recordPlaced() {
            this.placed++;
        }

        private void recordMissing() {
            this.missing++;
        }

        private void recordBlocked() {
            this.blocked++;
        }

        private void recordUnloaded() {
            this.unloaded++;
        }

        private void recordPlaceFailed() {
            this.placeFailed++;
        }
    }

    record Candidate(ItemStack stack, @Nullable BlockState desiredState) {}

    private static final class PlaceContext extends BlockPlaceContext {

        private PlaceContext(Level level, Player player, ItemStack stack, BlockPos target) {
            super(
                    level,
                    player,
                    InteractionHand.MAIN_HAND,
                    stack,
                    new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));
            this.replaceClicked = true;
        }
    }
}
