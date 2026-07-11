package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Context;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Failure;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.FailureType;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.parts.PartPlacement;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Default atomic builder for MDLib-backed structures.
 *
 * <p>
 * The implementation expands the requested repetition, preflights every target and material, then commits detached
 * placement stacks against captured inventory and world snapshots. A failed placement restores both snapshots before
 * returning.
 * </p>
 */
public final class MultiBlockAutoBuildImpl implements MultiBlockAutoBuild {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final int WORLD_UPDATE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;

    /**
     * Executes one complete atomic build request.
     */
    @Override
    public Result execute(Context context) {
        PlanOutcome planOutcome = createPlan(context);
        if (planOutcome.failure() != null) {
            return Result.failure(planOutcome.reused(), planOutcome.failure());
        }

        AllocationOutcome allocation = allocateMaterials(context, planOutcome.positions());
        if (allocation.failure() != null) {
            return Result.failure(planOutcome.reused(), allocation.failure());
        }

        InventoryTransaction inventory = new InventoryTransaction(context.player().getInventory(),
                allocation.placements(), context.player().isCreative());
        List<WorldSnapshot> worldSnapshots;
        try {
            worldSnapshots = captureWorld(context, allocation.placements());
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to capture auto-build world snapshots for {}", context.structureName(), exception);
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.PLACE_FAILED,
                    null,
                    "Unable to capture the structure world state before placement"));
        }

        if (!inventory.commit()) {
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.MISSING_MATERIAL,
                    null,
                    "Player inventory changed before material reservation committed"));
        }

        PlacementBatchOutcome placementOutcome = placeAll(context, allocation.placements());
        if (placementOutcome.failure() == null) {
            inventory.complete();
            releaseReplacementDrops(context, placementOutcome.replacementDrops());
            return Result.success(allocation.placements().size(), planOutcome.reused());
        }

        boolean worldRestored = restoreWorld(context, worldSnapshots);
        inventory.rollback();
        if (!worldRestored) {
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.ROLLBACK_FAILED,
                    placementOutcome.failure().position(),
                    "A placement failed and at least one captured world position could not be restored"));
        }
        return Result.failure(planOutcome.reused(), placementOutcome.failure());
    }

    private static PlanOutcome createPlan(Context context) {
        RepetitionOutcome repetitionOutcome = resolveRepetitions(context.pattern(), context.repeatCount());
        if (repetitionOutcome.failure() != null) {
            return new PlanOutcome(List.of(), 0, repetitionOutcome.failure());
        }

        BlockPattern pattern = context.pattern();
        PatternCoordinates coordinates = new PatternCoordinates(pattern);
        MultiblockState state = new MultiblockState(context.world(), context.origin(), context.structureName());
        state.clean();
        ArrayList<PositionPlan> positions = new ArrayList<>();
        int reused = 0;
        int expandedZ = coordinates.minZ();

        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            for (int repeat = 0; repeat < repetitionOutcome.repetitions()[unit]; repeat++) {
                for (int inner = 0; inner < pattern.unitDepths[unit]; inner++) {
                    int patternZ = pattern.unitStarts[unit] + inner;
                    state.getLayerCount().clear();
                    state.getStructureLayerCount().clear();
                    LayerOutcome layer = planLayer(context, coordinates, state, patternZ, expandedZ, positions);
                    reused += layer.reused();
                    if (layer.failure() != null) {
                        return new PlanOutcome(List.of(), reused, layer.failure());
                    }
                    expandedZ++;
                }
            }
        }
        return new PlanOutcome(List.copyOf(positions), reused, null);
    }

    private static LayerOutcome planLayer(Context context,
                                          PatternCoordinates coordinates,
                                          MultiblockState state,
                                          int patternZ,
                                          int expandedZ,
                                          List<PositionPlan> positions) {
        int reused = 0;
        for (int yOffset = coordinates.minY(); yOffset < coordinates.minY() + coordinates.thumbLength(); yOffset++) {
            for (int xOffset = coordinates.minX(); xOffset < coordinates.minX() + coordinates.palmLength(); xOffset++) {
                TraceabilityPredicate predicate = coordinates.predicate(patternZ, yOffset, xOffset);
                if (predicate.isAny() || predicate.isAir()) {
                    continue;
                }
                BlockPos target = context.origin().offset(coordinates.actualRelativeOffset(
                        xOffset,
                        yOffset,
                        expandedZ,
                        context.front(),
                        Direction.NORTH,
                        context.flipped()));
                if (target.equals(context.origin())) {
                    continue;
                }

                PositionOutcome positionOutcome = preflightPosition(context, state, predicate, target, positions);
                if (positionOutcome.failure() != null) {
                    return new LayerOutcome(reused, positionOutcome.failure());
                }
                if (positionOutcome.reused()) {
                    reused++;
                }
            }
        }
        return new LayerOutcome(reused, null);
    }

    private static PositionOutcome preflightPosition(Context context,
                                                     MultiblockState state,
                                                     TraceabilityPredicate predicate,
                                                     BlockPos target,
                                                     List<PositionPlan> positions) {
        if (!context.level().isLoaded(target) || !state.update(target, predicate)) {
            return PositionOutcome.failure(failure(
                    FailureType.UNLOADED,
                    target,
                    "Required structure position is not loaded"));
        }

        TierSelectionOutcome tierSelectionOutcome = resolveTierSelection(
                predicate,
                context.selectedTierBlocks(),
                target);
        if (tierSelectionOutcome.failure() != null) {
            return PositionOutcome.failure(tierSelectionOutcome.failure());
        }

        TierSelection tierSelection = tierSelectionOutcome.selection();
        BlockState currentState = context.level().getBlockState(target);
        boolean predicateMatched = predicate.test(state);
        boolean requiresPart = requiresPartCandidate(predicate);
        boolean missingRequiredPart = requiresPart && !hasMatchingRequiredPart(context, predicate, target);
        boolean replacesExistingTier = tierSelection.replacesExisting(
                currentState.getBlock(),
                context.tierRanks());
        if (tierSelection.rejectsExisting(currentState.getBlock()) && !replacesExistingTier) {
            return PositionOutcome.failure(failure(
                    FailureType.BLOCKED,
                    target,
                    "Required position contains a different selected predicate tier"));
        }
        if (predicateMatched && !replacesExistingTier && !missingRequiredPart) {
            return PositionOutcome.reusedPosition();
        }
        if (!replacesExistingTier && !predicateMatched && !currentState.isAir() && !currentState.canBeReplaced()) {
            return PositionOutcome.failure(failure(
                    FailureType.BLOCKED,
                    target,
                    "Required position is occupied by a non-replaceable block"));
        }

        List<Candidate> candidates = supportedCandidates(predicate, tierSelection, missingRequiredPart);
        if (candidates.isEmpty()) {
            return PositionOutcome.failure(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    target,
                    "Structure predicate has no supported block or AE2 part candidate"));
        }
        positions.add(new PositionPlan(
                target.immutable(),
                predicate,
                candidates,
                requiresPart,
                replacesExistingTier));
        return PositionOutcome.plannedPosition();
    }

    private static RepetitionOutcome resolveRepetitions(BlockPattern pattern, int requestedRepeat) {
        int[] repetitions = new int[pattern.aisleRepetitions.length];
        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            int minimum = pattern.aisleRepetitions[unit][0];
            int maximum = pattern.aisleRepetitions[unit][1];
            if (minimum == maximum) {
                repetitions[unit] = minimum;
                continue;
            }
            if (requestedRepeat < minimum || requestedRepeat > maximum) {
                return new RepetitionOutcome(new int[0], new Failure(
                        FailureType.INVALID_REPETITION,
                        null,
                        "Requested repetition " + requestedRepeat + " is outside [" + minimum + ", " + maximum +
                                "] for unit " + unit));
            }
            repetitions[unit] = requestedRepeat;
        }
        return new RepetitionOutcome(repetitions, null);
    }

    private static TierSelectionOutcome resolveTierSelection(TraceabilityPredicate predicate,
                                                             Map<Block, Block> selectedTierBlocks,
                                                             BlockPos target) {
        List<Block> candidates = tierCandidateBlocks(predicate);
        Block selectedBlock = null;
        for (Block candidate : candidates) {
            Block mappedBlock = selectedTierBlocks.get(candidate);
            if (mappedBlock != null) {
                selectedBlock = mappedBlock;
                break;
            }
        }
        if (selectedBlock == null) {
            return TierSelectionOutcome.success(new TierSelection(candidates, null));
        }

        for (Block candidate : candidates) {
            Block mappedBlock = selectedTierBlocks.get(candidate);
            if (mappedBlock == null) {
                return TierSelectionOutcome.failure(failure(
                        FailureType.INVALID_TIER_SELECTION,
                        target,
                        "Selected predicate tier does not map every block candidate"));
            }
            if (mappedBlock != selectedBlock) {
                return TierSelectionOutcome.failure(failure(
                        FailureType.INVALID_TIER_SELECTION,
                        target,
                        "Selected predicate tier maps candidates to different target blocks"));
            }
        }
        if (!candidates.contains(selectedBlock)) {
            return TierSelectionOutcome.failure(failure(
                    FailureType.INVALID_TIER_SELECTION,
                    target,
                    "Selected predicate tier target is not a block candidate"));
        }
        return TierSelectionOutcome.success(new TierSelection(candidates, selectedBlock));
    }

    private static List<Block> tierCandidateBlocks(TraceabilityPredicate predicate) {
        LinkedHashSet<Block> candidates = new LinkedHashSet<>();
        for (ItemStack candidate : predicate.placementCandidates()) {
            if (!candidate.isEmpty() && candidate.getItem() instanceof BlockItem blockItem) {
                candidates.add(blockItem.getBlock());
            }
        }
        for (BlockState candidate : predicate.blockStateCandidates()) {
            candidates.add(candidate.getBlock());
        }
        return List.copyOf(candidates);
    }

    private static List<Candidate> supportedCandidates(TraceabilityPredicate predicate,
                                                       TierSelection tierSelection,
                                                       boolean partsOnly) {
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (ItemStack candidateStack : predicate.placementCandidates()) {
            if (partsOnly && !(candidateStack.getItem() instanceof IPartItem<?>)) {
                continue;
            }
            Candidate candidate = placementCandidate(candidateStack, predicate, tierSelection);
            if (candidate != null) {
                addCandidate(candidates, candidate);
            }
        }
        if (partsOnly) {
            return List.copyOf(candidates);
        }
        for (BlockState state : predicate.blockStateCandidates()) {
            if (!tierSelection.allows(state.getBlock())) {
                continue;
            }
            Item item = state.getBlock().asItem();
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty()) {
                addCandidate(candidates, new Candidate(stack.copyWithCount(1), state));
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean requiresPartCandidate(TraceabilityPredicate predicate) {
        for (ItemStack candidate : predicate.placementCandidates()) {
            if (candidate.getItem() instanceof IPartItem<?>) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingRequiredPart(Context context,
                                                   TraceabilityPredicate predicate,
                                                   BlockPos target) {
        for (ItemStack candidate : predicate.placementCandidates()) {
            if (!(candidate.getItem() instanceof IPartItem<?> partItem)) {
                continue;
            }
            Direction placementSide = context.partSideResolver().resolve(target, candidate.copyWithCount(1));
            if (placementSide == null) {
                continue;
            }
            if (isPartItem(PartHelper.getPart(context.level(), target, null), partItem) ||
                    isPartItem(PartHelper.getPart(context.level(), target, placementSide), partItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPartItem(@Nullable IPart part, IPartItem<?> item) {
        return part != null && part.getPartItem() == item;
    }

    @Nullable
    private static Candidate placementCandidate(ItemStack source,
                                                TraceabilityPredicate predicate,
                                                TierSelection tierSelection) {
        if (source.isEmpty()) {
            return null;
        }
        if (source.getItem() instanceof IPartItem<?>) {
            if (tierSelection.isSelected()) {
                return null;
            }
            return new Candidate(source.copyWithCount(1), null);
        }
        if (!(source.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        Block selectedBlock = tierSelection.selectedBlock() == null ? blockItem.getBlock() :
                tierSelection.selectedBlock();
        ItemStack selectedStack = selectedBlock == blockItem.getBlock() ? source.copyWithCount(1) :
                selectedBlock.asItem().getDefaultInstance();
        if (selectedStack.isEmpty()) {
            return null;
        }
        return new Candidate(selectedStack.copyWithCount(1), desiredStateFor(selectedBlock, predicate));
    }

    private static BlockState desiredStateFor(Block selectedBlock, TraceabilityPredicate predicate) {
        for (BlockState candidateState : predicate.blockStateCandidates()) {
            if (candidateState.getBlock() == selectedBlock) {
                return candidateState;
            }
        }
        return selectedBlock.defaultBlockState();
    }

    private static void addCandidate(List<Candidate> candidates, Candidate candidate) {
        for (Candidate existing : candidates) {
            boolean sameState = existing.desiredState() == null ? candidate.desiredState() == null :
                    existing.desiredState().equals(candidate.desiredState());
            if (sameState && ItemStack.isSameItemSameComponents(existing.stack(), candidate.stack())) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private static AllocationOutcome allocateMaterials(Context context, List<PositionPlan> positions) {
        Inventory inventory = context.player().getInventory();
        int[] available = new int[inventory.getContainerSize()];
        for (int slot = 0; slot < available.length; slot++) {
            available[slot] = inventory.getItem(slot).getCount();
        }

        ArrayList<Placement> placements = new ArrayList<>(positions.size());
        for (PositionPlan position : positions) {
            Candidate candidate = null;
            int inventorySlot = -1;
            if (context.player().isCreative()) {
                candidate = position.candidates().getFirst();
            } else {
                CandidateSlot match = findMaterial(inventory, available, position.candidates());
                if (match != null) {
                    candidate = match.candidate();
                    inventorySlot = match.slot();
                    available[inventorySlot]--;
                }
            }
            if (candidate == null) {
                return new AllocationOutcome(List.of(), failure(
                        FailureType.MISSING_MATERIAL,
                        position.position(),
                        "Player inventory cannot supply any accepted placement candidate"));
            }

            PlacementValidation validation = validatePlacement(context, position.position(), candidate);
            if (validation.failure() != null) {
                return new AllocationOutcome(List.of(), validation.failure());
            }
            placements.add(new Placement(
                    position.position(),
                    position.predicate(),
                    candidate.stack().copyWithCount(1),
                    candidate.desiredState(),
                    validation.partSide(),
                    position.requiresPart(),
                    position.replacesExistingTier(),
                    inventorySlot));
        }
        return new AllocationOutcome(List.copyOf(placements), null);
    }

    @Nullable
    private static CandidateSlot findMaterial(Inventory inventory, int[] available, List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            for (int slot = 0; slot < available.length; slot++) {
                ItemStack inventoryStack = inventory.getItem(slot);
                if (available[slot] > 0 &&
                        ItemStack.isSameItemSameComponents(inventoryStack, candidate.stack())) {
                    return new CandidateSlot(candidate, slot);
                }
            }
        }
        return null;
    }

    private static PlacementValidation validatePlacement(Context context, BlockPos position, Candidate candidate) {
        ItemStack stack = candidate.stack();
        if (!context.player().mayUseItemAt(position.relative(Direction.UP), Direction.UP, stack)) {
            return PlacementValidation.failed(failure(
                    FailureType.PERMISSION_DENIED,
                    position,
                    "Player may not place at the required position"));
        }
        if (stack.getItem() instanceof IPartItem<?>) {
            Direction side = context.partSideResolver().resolve(position, stack.copyWithCount(1));
            if (side == null) {
                return PlacementValidation.failed(failure(
                        FailureType.UNSUPPORTED_CANDIDATE,
                        position,
                        "AE2 part placement requires an explicit host side"));
            }
            return PlacementValidation.success(side);
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return PlacementValidation.failed(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    position,
                    "Candidate item cannot place a block or AE2 part"));
        }
        if (!blockItem.getBlock().isEnabled(context.level().enabledFeatures())) {
            return PlacementValidation.failed(failure(
                    FailureType.PLACE_FAILED,
                    position,
                    "Candidate block is disabled by active feature flags"));
        }

        BlockState desiredState = candidate.desiredState();
        if (desiredState == null || desiredState.getBlock() != blockItem.getBlock()) {
            return PlacementValidation.failed(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    position,
                    "Block candidate does not declare a matching target state"));
        }
        return PlacementValidation.success(null);
    }

    private static List<WorldSnapshot> captureWorld(Context context, List<Placement> placements) {
        LinkedHashMap<BlockPos, WorldSnapshot> snapshots = new LinkedHashMap<>();
        for (Placement placement : placements) {
            snapshots.putIfAbsent(placement.position(), WorldSnapshot.capture(context.level(), placement.position()));
        }
        return List.copyOf(snapshots.values());
    }

    private static PlacementBatchOutcome placeAll(Context context, List<Placement> placements) {
        List<Placement> pending = placements;
        ArrayList<ReplacementDrop> replacementDrops = new ArrayList<>();
        while (!pending.isEmpty()) {
            ArrayList<Placement> deferred = new ArrayList<>();
            boolean madeProgress = false;
            for (Placement placement : pending) {
                try {
                    PlacementReadiness readiness = placementReadiness(context, placement);
                    if (!readiness.ready()) {
                        deferred.add(placement);
                        continue;
                    }
                    PlacementCommit placementCommit = place(context, placement);
                    if (!placementCommit.success()) {
                        return PlacementBatchOutcome.failure(failure(FailureType.PLACE_FAILED, placement.position(),
                                "Placement did not satisfy its original structure predicate"));
                    }
                    replacementDrops.addAll(placementCommit.replacementDrops());
                    madeProgress = true;
                } catch (RuntimeException exception) {
                    LOGGER.error("Atomic auto-build placement failed for {} at {}", context.structureName(),
                            placement.position(), exception);
                    return PlacementBatchOutcome.failure(failure(FailureType.PLACE_FAILED, placement.position(),
                            "Placement raised an exception; captured state will be restored"));
                }
            }

            if (deferred.isEmpty()) {
                return PlacementBatchOutcome.success(replacementDrops);
            }
            if (!madeProgress) {
                Placement firstDeferred = deferred.getFirst();
                PlacementReadiness readiness = placementReadiness(context, firstDeferred);
                return PlacementBatchOutcome.failure(failure(
                        FailureType.PLACE_FAILED,
                        firstDeferred.position(),
                        readiness.detail() + "; no deferred placement dependency made progress"));
            }
            pending = deferred;
        }
        return PlacementBatchOutcome.success(replacementDrops);
    }

    private static PlacementReadiness placementReadiness(Context context, Placement placement) {
        ItemStack stack = placement.stack();
        if (stack.getItem() instanceof IPartItem<?>) {
            Direction partSide = placement.partSide();
            if (partSide != null && PartPlacement.canPlacePartOnBlock(
                    context.player(), context.level(), stack, placement.position(), partSide)) {
                return PlacementReadiness.readyPlacement();
            }
            return PlacementReadiness.deferred("AE2 part cannot yet be placed on the selected host side");
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            throw new IllegalStateException("Validated placement candidate is not a block or AE2 part");
        }

        BlockState desiredState = placement.desiredState();
        if (desiredState == null) {
            throw new IllegalStateException("Validated block placement has no target state");
        }
        if (placement.replacesExistingTier()) {
            if (!desiredState.canSurvive(context.level(), placement.position())) {
                return PlacementReadiness.deferred(
                        "Replacement tier block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                                " cannot survive until its support is placed");
            }
            return PlacementReadiness.readyPlacement();
        }

        ItemStack detached = stack.copyWithCount(1);
        PlaceContext placeContext = new PlaceContext(
                context.level(), context.player(), detached, placement.position());
        BlockPlaceContext updatedContext = blockItem.updatePlacementContext(placeContext);
        if (!placeContext.canPlace() || updatedContext == null ||
                blockItem.getBlock().getStateForPlacement(updatedContext) == null) {
            return PlacementReadiness.deferred(
                    "Candidate block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                            " has no valid placement state yet");
        }

        if (!desiredState.canSurvive(context.level(), placement.position())) {
            return PlacementReadiness.deferred(
                    "Candidate block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                            " cannot survive until its support is placed");
        }
        if (!context.level().isUnobstructed(
                desiredState, placement.position(), CollisionContext.of(context.player()))) {
            return PlacementReadiness.deferred(
                    "Candidate block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                            " is obstructed");
        }
        return PlacementReadiness.readyPlacement();
    }

    private static PlacementCommit place(Context context, Placement placement) {
        List<ReplacementDrop> replacementDrops = List.of();
        if (placement.replacesExistingTier()) {
            ReplacementOutcome replacement = replaceExistingTier(context, placement);
            if (!replacement.success()) {
                return PlacementCommit.failed();
            }
            replacementDrops = replacement.drops();
        } else {
            ItemStack detached = placement.stack().copyWithCount(1);
            if (detached.getItem() instanceof IPartItem<?> partItem) {
                Direction partSide = placement.partSide();
                if (partSide == null || PartPlacement.placePart(
                        context.player(),
                        context.level(),
                        partItem,
                        detached.getComponents(),
                        placement.position(),
                        partSide) == null) {
                    return PlacementCommit.failed();
                }
            } else if (detached.getItem() instanceof BlockItem blockItem) {
                InteractionResult result = blockItem.place(new PlaceContext(
                        context.level(), context.player(), detached, placement.position()));
                if (result == InteractionResult.FAIL) {
                    return PlacementCommit.failed();
                }
            } else {
                return PlacementCommit.failed();
            }
        }

        if (placement.desiredState() != null && !applyDesiredState(
                context.level(), placement.position(), placement.desiredState())) {
            return PlacementCommit.failed();
        }
        MultiblockState verification = new MultiblockState(context.world(), context.origin(), context.structureName());
        if (!verification.update(placement.position(), placement.predicate())) {
            return PlacementCommit.failed();
        }
        if (!placement.predicate().test(verification) ||
                placement.requiresPart() && !hasMatchingRequiredPart(context, placement.predicate(), placement.position())) {
            return PlacementCommit.failed();
        }
        return PlacementCommit.success(replacementDrops);
    }

    private static ReplacementOutcome replaceExistingTier(Context context, Placement placement) {
        BlockState desiredState = placement.desiredState();
        if (desiredState == null) {
            return ReplacementOutcome.failed();
        }
        BlockState replacedState = context.level().getBlockState(placement.position());
        BlockEntity replacedBlockEntity = context.level().getBlockEntity(placement.position());
        List<ItemStack> drops = Block.getDrops(
                replacedState,
                context.level(),
                placement.position(),
                replacedBlockEntity).stream()
                .filter(drop -> !drop.isEmpty())
                .map(ItemStack::copy)
                .toList();
        if (!context.level().setBlock(placement.position(), desiredState, WORLD_UPDATE_FLAGS)) {
            return ReplacementOutcome.failed();
        }
        if (drops.isEmpty()) {
            return ReplacementOutcome.success(List.of());
        }
        return ReplacementOutcome.success(List.of(new ReplacementDrop(placement.position(), drops)));
    }

    private static void releaseReplacementDrops(Context context, List<ReplacementDrop> replacementDrops) {
        ArrayList<ReplacementOverflow> overflows = new ArrayList<>();
        for (ReplacementDrop replacementDrop : replacementDrops) {
            for (ItemStack stack : replacementDrop.stacks()) {
                ItemStack remaining = stack.copy();
                try {
                    context.player().addItem(remaining);
                } catch (RuntimeException exception) {
                    LOGGER.error("Unable to return auto-build replacement loot {} to player {}; dropping its remainder",
                            remaining, context.player().getGameProfile().getName(), exception);
                }
                if (remaining.isEmpty()) {
                    continue;
                }
                appendReplacementOverflow(overflows, replacementDrop.position(), remaining);
            }
        }
        for (ReplacementOverflow overflow : overflows) {
            try {
                Block.popResource(context.level(), overflow.position(), overflow.stack().copy());
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to drop auto-build replacement loot {} at {} after player inventory overflow",
                        overflow.stack(), overflow.position(), exception);
            }
        }
    }

    private static void appendReplacementOverflow(List<ReplacementOverflow> overflows,
                                                  BlockPos position,
                                                  ItemStack remaining) {
        ItemStack unmerged = remaining.copy();
        for (ReplacementOverflow overflow : overflows) {
            ItemStack existing = overflow.stack();
            if (!ItemStack.isSameItemSameComponents(existing, unmerged)) {
                continue;
            }
            int freeSpace = existing.getMaxStackSize() - existing.getCount();
            if (freeSpace <= 0) {
                continue;
            }
            int merged = Math.min(freeSpace, unmerged.getCount());
            existing.grow(merged);
            unmerged.shrink(merged);
            if (unmerged.isEmpty()) {
                return;
            }
        }
        while (!unmerged.isEmpty()) {
            int count = Math.min(unmerged.getCount(), unmerged.getMaxStackSize());
            overflows.add(new ReplacementOverflow(position.immutable(), unmerged.copyWithCount(count)));
            unmerged.shrink(count);
        }
    }

    private static boolean applyDesiredState(Level level, BlockPos position, BlockState desiredState) {
        BlockState currentState = level.getBlockState(position);
        if (currentState.getBlock() != desiredState.getBlock()) {
            return false;
        }
        if (currentState.equals(desiredState)) {
            return true;
        }
        return level.setBlock(position, desiredState, Block.UPDATE_ALL);
    }

    private static boolean restoreWorld(Context context, List<WorldSnapshot> snapshots) {
        boolean restored = true;
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            WorldSnapshot snapshot = snapshots.get(index);
            try {
                if (!snapshot.restore(context.level())) {
                    restored = false;
                    LOGGER.error("Unable to restore atomic auto-build world snapshot at {}", snapshot.position());
                }
            } catch (RuntimeException exception) {
                restored = false;
                LOGGER.error("Exception while restoring atomic auto-build world snapshot at {}", snapshot.position(),
                        exception);
            }
        }
        return restored;
    }

    private static Failure failure(FailureType type, BlockPos position, String detail) {
        return new Failure(type, position.immutable(), detail);
    }

    private record PlanOutcome(List<PositionPlan> positions, int reused, @Nullable Failure failure) {}

    private record LayerOutcome(int reused, @Nullable Failure failure) {}

    private record PositionOutcome(boolean reused, @Nullable Failure failure) {

        private static PositionOutcome reusedPosition() {
            return new PositionOutcome(true, null);
        }

        private static PositionOutcome plannedPosition() {
            return new PositionOutcome(false, null);
        }

        private static PositionOutcome failure(Failure failure) {
            return new PositionOutcome(false, failure);
        }
    }

    private record RepetitionOutcome(int[] repetitions, @Nullable Failure failure) {}

    private record TierSelection(List<Block> candidateBlocks, @Nullable Block selectedBlock) {

        private boolean isSelected() {
            return this.selectedBlock != null;
        }

        private boolean allows(Block block) {
            return this.selectedBlock == null || this.selectedBlock == block;
        }

        private boolean rejectsExisting(Block block) {
            return this.selectedBlock != null && this.candidateBlocks.contains(block) && this.selectedBlock != block;
        }

        private boolean replacesExisting(Block block, Map<Block, Integer> tierRanks) {
            if (!rejectsExisting(block)) {
                return false;
            }
            Integer existingRank = tierRanks.get(block);
            Integer selectedRank = tierRanks.get(this.selectedBlock);
            return existingRank != null && selectedRank != null && selectedRank > existingRank;
        }
    }

    private record TierSelectionOutcome(@Nullable TierSelection selection, @Nullable Failure failure) {

        private static TierSelectionOutcome success(TierSelection selection) {
            return new TierSelectionOutcome(selection, null);
        }

        private static TierSelectionOutcome failure(Failure failure) {
            return new TierSelectionOutcome(null, failure);
        }
    }

    private record PositionPlan(BlockPos position,
                                TraceabilityPredicate predicate,
                                List<Candidate> candidates,
                                boolean requiresPart,
                                boolean replacesExistingTier) {}

    private record Candidate(ItemStack stack, @Nullable BlockState desiredState) {}

    private record CandidateSlot(Candidate candidate, int slot) {}

    private record AllocationOutcome(List<Placement> placements, @Nullable Failure failure) {}

    private record PlacementBatchOutcome(@Nullable Failure failure, List<ReplacementDrop> replacementDrops) {

        private static PlacementBatchOutcome success(List<ReplacementDrop> replacementDrops) {
            return new PlacementBatchOutcome(null, List.copyOf(replacementDrops));
        }

        private static PlacementBatchOutcome failure(Failure failure) {
            return new PlacementBatchOutcome(failure, List.of());
        }
    }

    private record PlacementValidation(@Nullable Direction partSide, @Nullable Failure failure) {

        private static PlacementValidation success(@Nullable Direction partSide) {
            return new PlacementValidation(partSide, null);
        }

        private static PlacementValidation failed(Failure failure) {
            return new PlacementValidation(null, failure);
        }
    }

    private record PlacementReadiness(boolean ready, String detail) {

        private static PlacementReadiness readyPlacement() {
            return new PlacementReadiness(true, "");
        }

        private static PlacementReadiness deferred(String detail) {
            return new PlacementReadiness(false, detail);
        }
    }

    private record PlacementCommit(boolean success, List<ReplacementDrop> replacementDrops) {

        private static PlacementCommit success(List<ReplacementDrop> replacementDrops) {
            return new PlacementCommit(true, List.copyOf(replacementDrops));
        }

        private static PlacementCommit failed() {
            return new PlacementCommit(false, List.of());
        }
    }

    private record ReplacementOutcome(boolean success, List<ReplacementDrop> drops) {

        private static ReplacementOutcome success(List<ReplacementDrop> drops) {
            return new ReplacementOutcome(true, List.copyOf(drops));
        }

        private static ReplacementOutcome failed() {
            return new ReplacementOutcome(false, List.of());
        }
    }

    private record ReplacementDrop(BlockPos position, List<ItemStack> stacks) {}

    private record ReplacementOverflow(BlockPos position, ItemStack stack) {}

    private record Placement(BlockPos position,
                             TraceabilityPredicate predicate,
                             ItemStack stack,
                             @Nullable BlockState desiredState,
                             @Nullable Direction partSide,
                             boolean requiresPart,
                             boolean replacesExistingTier,
                             int inventorySlot) {}

    private record WorldSnapshot(BlockPos position, BlockState state, @Nullable CompoundTag blockEntityData) {

        private static WorldSnapshot capture(Level level, BlockPos position) {
            BlockState state = level.getBlockState(position);
            BlockEntity blockEntity = level.getBlockEntity(position);
            CompoundTag blockEntityData = blockEntity == null ? null :
                    blockEntity.saveWithFullMetadata(level.registryAccess());
            return new WorldSnapshot(position.immutable(), state, blockEntityData);
        }

        private boolean restore(Level level) {
            boolean stateRestored = level.setBlock(this.position, this.state, WORLD_UPDATE_FLAGS) ||
                    level.getBlockState(this.position).equals(this.state);
            if (!stateRestored || this.blockEntityData == null) {
                return stateRestored;
            }
            BlockEntity blockEntity = level.getBlockEntity(this.position);
            if (blockEntity == null) {
                return false;
            }
            blockEntity.loadWithComponents(this.blockEntityData, level.registryAccess());
            blockEntity.setChanged();
            return true;
        }
    }

    private static final class InventoryTransaction {

        private final Inventory inventory;
        private final List<Placement> placements;
        private final boolean creative;
        private final List<ItemStack> snapshot;
        private boolean committed;

        private InventoryTransaction(Inventory inventory, List<Placement> placements, boolean creative) {
            this.inventory = inventory;
            this.placements = placements;
            this.creative = creative;
            this.snapshot = captureInventory(inventory);
        }

        private boolean commit() {
            if (this.creative) {
                this.committed = true;
                return true;
            }
            Map<Integer, Integer> deductions = new LinkedHashMap<>();
            Map<Integer, ItemStack> expectedStacks = new LinkedHashMap<>();
            for (Placement placement : this.placements) {
                deductions.merge(placement.inventorySlot(), 1, Integer::sum);
                expectedStacks.putIfAbsent(placement.inventorySlot(), placement.stack());
            }
            for (Map.Entry<Integer, Integer> entry : deductions.entrySet()) {
                int slot = entry.getKey();
                ItemStack current = this.inventory.getItem(slot);
                if (!ItemStack.isSameItemSameComponents(current, expectedStacks.get(slot)) ||
                        current.getCount() < entry.getValue()) {
                    return false;
                }
            }
            for (Map.Entry<Integer, Integer> entry : deductions.entrySet()) {
                ItemStack remaining = this.inventory.getItem(entry.getKey()).copy();
                remaining.shrink(entry.getValue());
                this.inventory.setItem(entry.getKey(), remaining);
            }
            this.inventory.setChanged();
            this.committed = true;
            return true;
        }

        private void rollback() {
            if (!this.committed || this.creative) {
                return;
            }
            for (int slot = 0; slot < this.snapshot.size(); slot++) {
                this.inventory.setItem(slot, this.snapshot.get(slot).copy());
            }
            this.inventory.setChanged();
            this.committed = false;
        }

        private void complete() {
            if (!this.creative) {
                this.inventory.setChanged();
            }
            this.committed = false;
        }

        private static List<ItemStack> captureInventory(Inventory inventory) {
            ArrayList<ItemStack> stacks = new ArrayList<>(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                stacks.add(inventory.getItem(slot).copy());
            }
            return List.copyOf(stacks);
        }
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
