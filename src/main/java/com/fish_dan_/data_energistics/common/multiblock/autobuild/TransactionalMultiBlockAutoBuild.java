package com.fish_dan_.data_energistics.common.multiblock.autobuild;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewPredicateKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.CollisionContext;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.parts.PartPlacement;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-phase builder for MDLib-backed structures. Pre-publication failures restore staged state and supplies;
 * publication failures preserve observable world state.
 */
public final class TransactionalMultiBlockAutoBuild implements MultiBlockAutoBuild {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final int QUIET_UPDATE_FLAGS = Block.UPDATE_NONE | Block.UPDATE_KNOWN_SHAPE |
            Block.UPDATE_SUPPRESS_DROPS;
    private static final int PUBLISH_UPDATE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
    private static final int MAX_UPDATE_DEPTH = 512;

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

        InventoryTransaction inventory = new InventoryTransaction(
                context.structureName(),
                context.player().getInventory(),
                materialReservations(allocation.placements()),
                context.player().isCreative());
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

        SnapshotCaptureSession snapshotCapture;
        try {
            snapshotCapture = SnapshotCaptureSession.begin(context.level());
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to begin auto-build block snapshot capture for {}", context.structureName(), exception);
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.PLACE_FAILED,
                    null,
                    "Unable to begin a block snapshot transaction before material reservation"));
        }
        boolean materialsReserved;
        try {
            materialsReserved = inventory.commit();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to reserve auto-build materials for {}", context.structureName(), exception);
            snapshotCapture.close();
            RefundOutcome refundOutcome = inventory.rollback(context.player());
            if (!refundOutcome.completed()) {
                return Result.failure(planOutcome.reused(), new Failure(
                        FailureType.ROLLBACK_FAILED,
                        null,
                        "Material reservation failed and " + refundOutcome.detail()));
            }
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.PLACE_FAILED,
                    null,
                    "Unable to reserve auto-build materials"));
        }
        if (!materialsReserved) {
            snapshotCapture.close();
            return Result.failure(planOutcome.reused(), new Failure(
                    FailureType.MISSING_MATERIAL,
                    null,
                    "Player inventory changed before material reservation committed"));
        }

        StageOutcome stageOutcome;
        StagingProgress stagingProgress = new StagingProgress();
        boolean worldRestored = true;
        try {
            stageOutcome = stageAll(context, allocation.placements(), worldSnapshots, stagingProgress);
            if (stageOutcome.failure() != null) {
                worldRestored = restoreWorld(context, stagingProgress.physicalSnapshots());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Auto-build staging failed for {}", context.structureName(), exception);
            stageOutcome = StageOutcome.failure(failure(
                    FailureType.PLACE_FAILED,
                    context.origin(),
                    "Staging raised an exception; physically staged state will be restored"));
            worldRestored = restoreWorld(context, stagingProgress.physicalSnapshots());
        } finally {
            snapshotCapture.close();
        }

        if (stageOutcome.failure() != null) {
            RefundOutcome refundOutcome = inventory.rollback(context.player());
            if (!worldRestored || !refundOutcome.completed()) {
                return Result.failure(planOutcome.reused(), new Failure(
                        FailureType.ROLLBACK_FAILED,
                        stageOutcome.failure().position(),
                        rollbackFailureDetail(worldRestored, refundOutcome)));
            }
            return Result.failure(planOutcome.reused(), stageOutcome.failure());
        }

        PublicationOutcome publicationOutcome = publishAll(context, allocation.placements(), stageOutcome);
        if (publicationOutcome.failure() != null) {
            RefundOutcome refundOutcome = inventory.settlePublicationFailure(
                    context.player(), publicationOutcome.consumedPlacements());
            releaseReplacementDrops(context, publicationOutcome.releasedReplacementDrops());
            if (!refundOutcome.completed()) {
                Failure publicationFailure = publicationOutcome.failure();
                return Result.publishFailure(publicationOutcome.placed(), planOutcome.reused(), new Failure(
                        publicationFailure.type(),
                        publicationFailure.position(),
                        publicationFailure.detail() + "; " + refundOutcome.detail()));
            }
            return Result.publishFailure(publicationOutcome.placed(), planOutcome.reused(), publicationOutcome.failure());
        }

        inventory.complete();
        releaseReplacementDrops(context, publicationOutcome.releasedReplacementDrops());
        return Result.success(allocation.placements().size(), planOutcome.reused());
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
        Set<PreviewPredicateKey> appliedCandidateSelections = new LinkedHashSet<>();
        int reused = 0;
        int expandedZ = coordinates.minZ();

        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            for (int repeat = 0; repeat < repetitionOutcome.repetitions()[unit]; repeat++) {
                for (int inner = 0; inner < pattern.unitDepths[unit]; inner++) {
                    int patternZ = pattern.unitStarts[unit] + inner;
                    state.getLayerCount().clear();
                    state.getStructureLayerCount().clear();
                    LayerOutcome layer = planLayer(
                            context,
                            coordinates,
                            state,
                            patternZ,
                            expandedZ,
                            positions,
                            appliedCandidateSelections);
                    reused += layer.reused();
                    if (layer.failure() != null) {
                        return new PlanOutcome(List.of(), reused, layer.failure());
                    }
                    expandedZ++;
                }
            }
        }
        if (!appliedCandidateSelections.containsAll(context.candidateSelections().keySet())) {
            Set<PreviewPredicateKey> unknown = new LinkedHashSet<>(context.candidateSelections().keySet());
            unknown.removeAll(appliedCandidateSelections);
            return new PlanOutcome(List.of(), reused, new Failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    null,
                    "Candidate selections do not address buildable source predicates: " + unknown));
        }
        return new PlanOutcome(List.copyOf(positions), reused, null);
    }

    private static LayerOutcome planLayer(Context context,
                                          PatternCoordinates coordinates,
                                          MultiblockState state,
                                          int patternZ,
                                          int expandedZ,
                                          List<PositionPlan> positions,
                                          Set<PreviewPredicateKey> appliedCandidateSelections) {
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

                PreviewPredicateKey predicateKey = coordinates.predicateKey(patternZ, yOffset, xOffset);
                int candidateIndex = context.candidateSelections().getOrDefault(predicateKey, -1);
                if (candidateIndex >= 0) {
                    appliedCandidateSelections.add(predicateKey);
                }

                PositionOutcome positionOutcome = preflightPosition(
                        context,
                        state,
                        predicate,
                        target,
                        candidateIndex,
                        positions);
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
                                                     int candidateIndex,
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
        Candidate explicitCandidate = null;
        if (candidateIndex >= 0) {
            ExplicitCandidateOutcome explicitOutcome = resolveExplicitCandidate(
                    predicate,
                    tierSelection,
                    candidateIndex,
                    target);
            if (explicitOutcome.failure() != null) {
                return PositionOutcome.failure(explicitOutcome.failure());
            }
            if (explicitOutcome.empty()) {
                return PositionOutcome.plannedPosition();
            }
            explicitCandidate = explicitOutcome.candidate();
        }

        BlockState currentState = context.level().getBlockState(target);
        boolean requiresPart = explicitCandidate == null ?
                requiresPartCandidate(predicate) :
                explicitCandidate.stack().getItem() instanceof IPartItem<?>;
        boolean missingRequiredPart = requiresPart && (explicitCandidate == null ?
                !hasMatchingRequiredPart(context, predicate, target) :
                !hasMatchingRequiredPart(context, explicitCandidate, target));
        boolean predicateMatched = explicitCandidate == null ?
                predicate.test(state) :
                matchesCandidate(context, explicitCandidate, currentState, target);
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

        List<Candidate> candidates = explicitCandidate == null ?
                supportedCandidates(predicate, tierSelection, missingRequiredPart) :
                List.of(explicitCandidate);
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

    private static ExplicitCandidateOutcome resolveExplicitCandidate(TraceabilityPredicate predicate,
                                                                     TierSelection tierSelection,
                                                                     int candidateIndex,
                                                                     BlockPos target) {
        boolean allowsEmpty = predicate.hasAir() || predicate.blockStateCandidates().stream().anyMatch(BlockState::isAir);
        ArrayList<Candidate> candidates = new ArrayList<>();
        List<PatternCandidate> patternCandidates;
        try {
            patternCandidates = predicate.patternCandidates();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ExplicitCandidateOutcome.failure(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    target,
                    "Structure predicate candidate pairing failed: " + exception.getMessage()));
        }
        for (PatternCandidate patternCandidate : patternCandidates) {
            BlockState previewState = patternCandidate.previewState();
            if (previewState.isAir()) {
                return ExplicitCandidateOutcome.failure(failure(
                        FailureType.UNSUPPORTED_CANDIDATE,
                        target,
                        "Air candidates cannot expose placement items"));
            }
            if (tierSelection.isSelected() && previewState.getBlock() != tierSelection.selectedBlock()) {
                continue;
            }
            ItemStack placementStack = patternCandidate.placementStack().copyWithCount(1);
            boolean part = placementStack.getItem() instanceof IPartItem<?>;
            if (!part && !(placementStack.getItem() instanceof BlockItem)) {
                return ExplicitCandidateOutcome.failure(failure(
                        FailureType.UNSUPPORTED_CANDIDATE,
                        target,
                        "Selected preview candidate is neither a block nor an AE2 part"));
            }
            addCandidate(candidates, new Candidate(placementStack, part ? null : previewState));
        }

        if (allowsEmpty && candidateIndex == 0) {
            return ExplicitCandidateOutcome.emptySelection();
        }

        int concreteIndex = candidateIndex - (allowsEmpty ? 1 : 0);
        if (concreteIndex < 0 || concreteIndex >= candidates.size()) {
            return ExplicitCandidateOutcome.failure(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    target,
                    "Selected candidate index " + candidateIndex + " is outside the resolved predicate candidates"));
        }
        return ExplicitCandidateOutcome.selected(candidates.get(concreteIndex));
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

    private static boolean hasMatchingRequiredPart(Context context, Candidate candidate, BlockPos target) {
        if (!(candidate.stack().getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }
        Direction placementSide = context.partSideResolver().resolve(target, candidate.stack().copyWithCount(1));
        return placementSide != null &&
                (isPartItem(PartHelper.getPart(context.level(), target, null), partItem) ||
                        isPartItem(PartHelper.getPart(context.level(), target, placementSide), partItem));
    }

    private static boolean matchesCandidate(Context context,
                                            Candidate candidate,
                                            BlockState currentState,
                                            BlockPos target) {
        if (candidate.stack().getItem() instanceof IPartItem<?>) {
            return hasMatchingRequiredPart(context, candidate, target);
        }
        return candidate.desiredState() != null && candidate.desiredState().equals(currentState);
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
            CandidateSelection selection = selectCandidate(context, inventory, available, position);
            if (selection.failure() != null) {
                return new AllocationOutcome(List.of(), selection.failure());
            }
            Candidate candidate = selection.candidate();
            PlacementValidation validation = selection.validation();
            int inventorySlot = selection.inventorySlot();
            if (inventorySlot >= 0) {
                available[inventorySlot]--;
            }
            placements.add(new Placement(
                    position.position(),
                    position.predicate(),
                    candidate.stack().copyWithCount(1),
                    candidate.desiredState(),
                    validation.stagingState(),
                    validation.partSide(),
                    position.requiresPart(),
                    position.replacesExistingTier(),
                    inventorySlot));
        }
        return new AllocationOutcome(List.copyOf(placements), null);
    }

    private static List<MaterialReservation> materialReservations(List<Placement> placements) {
        ArrayList<MaterialReservation> reservations = new ArrayList<>(placements.size());
        for (Placement placement : placements) {
            reservations.add(new MaterialReservation(
                    placement.position(),
                    placement.inventorySlot(),
                    placement.stack()));
        }
        return List.copyOf(reservations);
    }

    private static CandidateSelection selectCandidate(Context context,
                                                      Inventory inventory,
                                                      int[] available,
                                                      PositionPlan position) {
        Failure validationFailure = null;
        for (Candidate candidate : position.candidates()) {
            int inventorySlot = context.player().isCreative() ? -1 : findMaterialSlot(inventory, available, candidate);
            if (!context.player().isCreative() && inventorySlot < 0) {
                continue;
            }
            PlacementValidation validation = validatePlacement(context, position.position(), candidate);
            if (validation.failure() != null) {
                if (validationFailure == null) {
                    validationFailure = validation.failure();
                }
                continue;
            }
            return CandidateSelection.success(candidate, inventorySlot, validation);
        }
        if (validationFailure != null) {
            return CandidateSelection.failure(validationFailure);
        }
        return CandidateSelection.failure(failure(
                FailureType.MISSING_MATERIAL,
                position.position(),
                "Player inventory cannot supply any approved placement candidate"));
    }

    private static int findMaterialSlot(Inventory inventory, int[] available, Candidate candidate) {
        for (int slot = 0; slot < available.length; slot++) {
            ItemStack inventoryStack = inventory.getItem(slot);
            if (available[slot] > 0 && ItemStack.isSameItemSameComponents(inventoryStack, candidate.stack())) {
                return slot;
            }
        }
        return -1;
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
            BlockState hostState = context.stagingPolicy().partHostState(position, stack.copyWithCount(1), side);
            if (hostState == null) {
                return PlacementValidation.failed(failure(
                        FailureType.UNSUPPORTED_STAGING,
                        position,
                        "AE2 part has no host-approved deferred staging path"));
            }
            if (!PartPlacement.canPlacePartOnBlock(context.player(), context.level(), stack, position, side)) {
                return PlacementValidation.failed(failure(
                        FailureType.PLACE_FAILED,
                        position,
                        "AE2 part cannot be preflighted on its resolved host side"));
            }
            return PlacementValidation.success(side, hostState);
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
        if (!stack.isComponentsPatchEmpty()) {
            return PlacementValidation.failed(failure(
                    FailureType.UNSUPPORTED_STAGING,
                    position,
                    "Block candidate carries components that direct state publication cannot preserve"));
        }

        BlockState desiredState = candidate.desiredState();
        if (desiredState == null || desiredState.getBlock() != blockItem.getBlock()) {
            return PlacementValidation.failed(failure(
                    FailureType.UNSUPPORTED_CANDIDATE,
                    position,
                    "Block candidate does not declare a matching target state"));
        }
        if (!context.stagingPolicy().canStageBlock(position, stack.copyWithCount(1), desiredState)) {
            return PlacementValidation.failed(failure(
                    FailureType.UNSUPPORTED_STAGING,
                    position,
                    "Block candidate has no host-approved silent staging path"));
        }
        return PlacementValidation.success(null, desiredState);
    }

    private static List<WorldSnapshot> captureWorld(Context context, List<Placement> placements) {
        LinkedHashMap<BlockPos, WorldSnapshot> snapshots = new LinkedHashMap<>();
        for (Placement placement : placements) {
            snapshots.putIfAbsent(placement.position(), WorldSnapshot.capture(context.level(), placement.position()));
        }
        return List.copyOf(snapshots.values());
    }

    private static StageOutcome stageAll(Context context,
                                         List<Placement> placements,
                                         List<WorldSnapshot> snapshots,
                                         StagingProgress stagingProgress) {
        LinkedHashMap<BlockPos, WorldSnapshot> snapshotsByPosition = new LinkedHashMap<>();
        for (WorldSnapshot snapshot : snapshots) {
            snapshotsByPosition.put(snapshot.position(), snapshot);
        }
        List<Placement> pending = placements;
        ArrayList<ReplacementDrop> replacementDrops = new ArrayList<>();
        ArrayList<StagedBlock> stagedBlocks = new ArrayList<>();
        ArrayList<DeferredPartPlacement> deferredParts = new ArrayList<>();
        LinkedHashMap<BlockPos, BlockState> stagedStates = new LinkedHashMap<>();
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
                    StagingCommit stagingCommit = stagePlacement(
                            context, placement, snapshotsByPosition, stagedStates, stagingProgress);
                    if (!stagingCommit.success()) {
                        return StageOutcome.failure(failure(FailureType.PLACE_FAILED, placement.position(),
                                "Staging did not satisfy its original structure predicate"));
                    }
                    replacementDrops.addAll(stagingCommit.replacementDrops());
                    if (stagingCommit.stagedBlock() != null) {
                        stagedBlocks.add(stagingCommit.stagedBlock());
                    }
                    if (placement.stack().getItem() instanceof IPartItem<?>) {
                        deferredParts.add(new DeferredPartPlacement(placement));
                    }
                    madeProgress = true;
                } catch (RuntimeException exception) {
                    LOGGER.error("Auto-build staging failed for {} at {}", context.structureName(),
                            placement.position(), exception);
                    return StageOutcome.failure(failure(FailureType.PLACE_FAILED, placement.position(),
                            "Staging raised an exception; captured state will be restored"));
                }
            }

            if (deferred.isEmpty()) {
                Failure verificationFailure = verifyStagedPlacements(context, placements, stagedStates);
                if (verificationFailure != null) {
                    return StageOutcome.failure(verificationFailure);
                }
                return StageOutcome.success(stagedBlocks, deferredParts, replacementDrops);
            }
            if (!madeProgress) {
                Placement firstDeferred = deferred.getFirst();
                PlacementReadiness readiness = placementReadiness(context, firstDeferred);
                return StageOutcome.failure(failure(
                        FailureType.PLACE_FAILED,
                        firstDeferred.position(),
                        readiness.detail() + "; no deferred placement dependency made progress"));
            }
            pending = deferred;
        }
        Failure verificationFailure = verifyStagedPlacements(context, placements, stagedStates);
        if (verificationFailure != null) {
            return StageOutcome.failure(verificationFailure);
        }
        return StageOutcome.success(stagedBlocks, deferredParts, replacementDrops);
    }

    private static PlacementReadiness placementReadiness(Context context, Placement placement) {
        ItemStack stack = placement.stack();
        BlockState stagingState = placement.stagingState();
        if (stagingState == null) {
            throw new IllegalStateException("Validated placement has no staging state");
        }
        if (stack.getItem() instanceof IPartItem<?>) {
            Direction partSide = placement.partSide();
            if (partSide == null || !PartPlacement.canPlacePartOnBlock(
                    context.player(), context.level(), stack, placement.position(), partSide)) {
                return PlacementReadiness.deferred("AE2 part cannot yet use the selected host side");
            }
            BlockState currentState = context.level().getBlockState(placement.position());
            if (!currentState.equals(stagingState) && !currentState.isAir() && !currentState.canBeReplaced()) {
                return PlacementReadiness.deferred("AE2 part host position is occupied");
            }
            if (!stagingState.canSurvive(context.level(), placement.position())) {
                return PlacementReadiness.deferred("AE2 part host cannot survive until its support is staged");
            }
            return PlacementReadiness.readyPlacement();
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            throw new IllegalStateException("Validated placement candidate is not a block or AE2 part");
        }

        if (!stagingState.canSurvive(context.level(), placement.position())) {
            return PlacementReadiness.deferred(
                    "Candidate block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                            " cannot survive until its support is placed");
        }
        if (!placement.replacesExistingTier() && !context.level().isUnobstructed(
                stagingState, placement.position(), CollisionContext.of(context.player()))) {
            return PlacementReadiness.deferred(
                    "Candidate block " + BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()) +
                            " is obstructed");
        }
        return PlacementReadiness.readyPlacement();
    }

    private static StagingCommit stagePlacement(Context context,
                                                Placement placement,
                                                Map<BlockPos, WorldSnapshot> snapshotsByPosition,
                                                Map<BlockPos, BlockState> stagedStates,
                                                StagingProgress stagingProgress) {
        BlockState stagingState = placement.stagingState();
        if (stagingState == null) {
            return StagingCommit.failed();
        }
        List<ReplacementDrop> replacementDrops = replacementDrops(context, placement);
        BlockState currentState = context.level().getBlockState(placement.position());
        StagedBlock stagedBlock = null;
        if (!currentState.equals(stagingState)) {
            WorldSnapshot snapshot = snapshotsByPosition.get(placement.position());
            if (snapshot == null) {
                throw new IllegalStateException("Missing world snapshot for staged position " + placement.position());
            }
            boolean physicallyStaged = currentState.is(Blocks.AIR) &&
                    !placement.requiresPart() &&
                    !stagingState.hasBlockEntity() &&
                    context.stagingPolicy().canPhysicallyStageBlock(
                            placement.position(), placement.stack(), stagingState);
            stagedBlock = new StagedBlock(placement, snapshot, stagingState, physicallyStaged, placement.requiresPart());
            if (physicallyStaged) {
                stagingProgress.recordPhysicalSnapshot(snapshot);
                if (!context.level().setBlock(placement.position(), stagingState, QUIET_UPDATE_FLAGS)) {
                    return StagingCommit.failed();
                }
            }
        }
        stagedStates.put(placement.position(), stagingState);
        if (!verifyStagedPlacement(context, placement, stagedStates)) {
            return StagingCommit.failed();
        }
        return StagingCommit.success(stagedBlock, replacementDrops);
    }

    private static List<ReplacementDrop> replacementDrops(Context context, Placement placement) {
        if (!placement.replacesExistingTier()) {
            return List.of();
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
        if (drops.isEmpty()) {
            return List.of();
        }
        return List.of(new ReplacementDrop(placement.position(), drops));
    }

    private static Failure verifyStagedPlacements(Context context,
                                                  List<Placement> placements,
                                                  Map<BlockPos, BlockState> stagedStates) {
        for (Placement placement : placements) {
            if (!verifyStagedPlacement(context, placement, stagedStates)) {
                return failure(FailureType.PLACE_FAILED, placement.position(),
                        "Staged state no longer satisfies its original structure predicate");
            }
        }
        return null;
    }

    private static boolean verifyStagedPlacement(Context context,
                                                 Placement placement,
                                                 Map<BlockPos, BlockState> stagedStates) {
        MultiblockState verification = new MultiblockState(
                new StagedStructureWorldView(context.world(), stagedStates),
                context.origin(),
                context.structureName());
        if (!verification.update(placement.position(), placement.predicate())) {
            return false;
        }
        if (!placement.predicate().test(verification)) {
            return false;
        }
        if (!placement.requiresPart()) {
            return true;
        }
        Direction side = placement.partSide();
        return side != null && PartPlacement.canPlacePartOnBlock(
                context.player(), context.level(), placement.stack(), placement.position(), side);
    }

    private static PublicationOutcome publishAll(Context context,
                                                 List<Placement> placements,
                                                 StageOutcome stageOutcome) {
        int placed = 0;
        ArrayList<ReplacementDrop> releasedReplacementDrops = new ArrayList<>();
        ArrayList<Placement> consumedPlacements = new ArrayList<>();
        for (StagedBlock stagedBlock : stageOutcome.stagedBlocks()) {
            if (stagedBlock.physicallyStaged()) {
                markMaterialConsumed(consumedPlacements, stagedBlock.placement());
            }
        }
        for (StagedBlock stagedBlock : stageOutcome.stagedBlocks()) {
            BlockPos position = stagedBlock.snapshot().position();
            try {
                BlockState currentState = context.level().getBlockState(position);
                if (stagedBlock.physicallyStaged()) {
                    if (!currentState.equals(stagedBlock.stagedState())) {
                        LOGGER.error("Auto-build publication lost staged state for {} at {}", context.structureName(),
                                position);
                        return PublicationOutcome.failure(placed, failure(
                                FailureType.PUBLISH_FAILED,
                                position,
                                "Staged block state changed before publication"), releasedReplacementDrops,
                                consumedPlacements);
                    }
                    currentState.onPlace(context.level(), position, stagedBlock.snapshot().state(), false);
                    LevelChunk chunk = context.level().getChunkAt(position);
                    context.level().markAndNotifyBlock(
                            position,
                            chunk,
                            stagedBlock.snapshot().state(),
                            currentState,
                            PUBLISH_UPDATE_FLAGS,
                            MAX_UPDATE_DEPTH);
                } else {
                    if (!currentState.equals(stagedBlock.snapshot().state())) {
                        LOGGER.error("Auto-build publication target changed before publish for {} at {}",
                                context.structureName(), position);
                        return PublicationOutcome.failure(placed, failure(
                                FailureType.PUBLISH_FAILED,
                                position,
                                "Publication target changed after pre-commit validation"), releasedReplacementDrops,
                                consumedPlacements);
                    }
                    if (!context.level().setBlock(position, stagedBlock.stagedState(), PUBLISH_UPDATE_FLAGS) &&
                            !context.level().getBlockState(position).equals(stagedBlock.stagedState())) {
                        return PublicationOutcome.failure(placed, failure(
                                FailureType.PUBLISH_FAILED,
                                position,
                                "Controlled state publication did not apply its target state"), releasedReplacementDrops,
                                consumedPlacements);
                    }
                }

                appendPublishedReplacementDrops(context, stageOutcome, stagedBlock, releasedReplacementDrops);
                if (!stagedBlock.deferredPartHost()) {
                    markMaterialConsumed(consumedPlacements, stagedBlock.placement());
                    placed++;
                }
                stagedBlock.stagedState().getBlock().setPlacedBy(
                        context.level(), position, stagedBlock.stagedState(), context.player(), stagedBlock.placement().stack());
            } catch (RuntimeException exception) {
                appendPublishedReplacementDrops(context, stageOutcome, stagedBlock, releasedReplacementDrops);
                if (!stagedBlock.deferredPartHost() &&
                        context.level().getBlockState(position).equals(stagedBlock.stagedState())) {
                    markMaterialConsumed(consumedPlacements, stagedBlock.placement());
                }
                LOGGER.error("Auto-build publication failed for {} at {}", context.structureName(), position, exception);
                return PublicationOutcome.failure(placed, failure(
                        FailureType.PUBLISH_FAILED,
                        position,
                        "Block publication raised an exception; the world was not rolled back"), releasedReplacementDrops,
                        consumedPlacements);
            }
        }

        for (DeferredPartPlacement deferredPart : stageOutcome.deferredParts()) {
            Placement placement = deferredPart.placement();
            ItemStack stack = placement.stack();
            if (!(stack.getItem() instanceof IPartItem<?> partItem) || placement.partSide() == null) {
                LOGGER.error("Auto-build publication has an invalid deferred part for {} at {}",
                        context.structureName(), placement.position());
                return PublicationOutcome.failure(placed, failure(
                        FailureType.PUBLISH_FAILED,
                        placement.position(),
                        "Deferred AE2 part lost its resolved publication data"), releasedReplacementDrops,
                        consumedPlacements);
            }
            try {
                if (PartPlacement.placePart(
                        context.player(),
                        context.level(),
                        partItem,
                        stack.getComponents(),
                        placement.position(),
                        placement.partSide()) == null) {
                    LOGGER.error("Auto-build publication could not attach AE2 part for {} at {}",
                            context.structureName(), placement.position());
                    return PublicationOutcome.failure(placed, failure(
                            FailureType.PUBLISH_FAILED,
                            placement.position(),
                            "Deferred AE2 part could not be attached after publication began"), releasedReplacementDrops,
                            consumedPlacements);
                }
                markMaterialConsumed(consumedPlacements, placement);
                placed++;
            } catch (RuntimeException exception) {
                try {
                    if (hasMatchingRequiredPart(context, placement.predicate(), placement.position())) {
                        markMaterialConsumed(consumedPlacements, placement);
                        placed++;
                    }
                } catch (RuntimeException inspectionException) {
                    exception.addSuppressed(inspectionException);
                }
                LOGGER.error("Auto-build AE2 part publication failed for {} at {}", context.structureName(),
                        placement.position(), exception);
                return PublicationOutcome.failure(placed, failure(
                        FailureType.PUBLISH_FAILED,
                        placement.position(),
                        "Deferred AE2 part raised an exception after publication began"), releasedReplacementDrops,
                        consumedPlacements);
            }
        }

        try {
            for (Placement placement : placements) {
                if (!verifyPublishedPlacement(context, placement)) {
                    LOGGER.error("Auto-build publication did not satisfy the structure predicate for {} at {}",
                            context.structureName(), placement.position());
                    return PublicationOutcome.failure(placed, failure(
                            FailureType.PUBLISH_FAILED,
                            placement.position(),
                            "Published state does not satisfy its original structure predicate"), releasedReplacementDrops,
                            consumedPlacements);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Auto-build final publication verification failed for {}", context.structureName(), exception);
            return PublicationOutcome.failure(placed, failure(
                    FailureType.PUBLISH_FAILED,
                    context.origin(),
                    "Published state verification raised an exception; the world was not rolled back"),
                    releasedReplacementDrops, consumedPlacements);
        }
        return PublicationOutcome.success(placed, releasedReplacementDrops, consumedPlacements);
    }

    private static void markMaterialConsumed(List<Placement> consumedPlacements, Placement placement) {
        for (Placement consumedPlacement : consumedPlacements) {
            if (consumedPlacement.position().equals(placement.position())) {
                return;
            }
        }
        consumedPlacements.add(placement);
    }

    private static void appendPublishedReplacementDrops(Context context,
                                                        StageOutcome stageOutcome,
                                                        StagedBlock stagedBlock,
                                                        List<ReplacementDrop> releasedReplacementDrops) {
        if (!stagedBlock.placement().replacesExistingTier() ||
                context.level().getBlockState(stagedBlock.snapshot().position()).equals(stagedBlock.snapshot().state())) {
            return;
        }
        for (ReplacementDrop replacementDrop : stageOutcome.replacementDrops()) {
            if (replacementDrop.position().equals(stagedBlock.snapshot().position()) &&
                    !releasedReplacementDrops.contains(replacementDrop)) {
                releasedReplacementDrops.add(replacementDrop);
            }
        }
    }

    private static boolean verifyPublishedPlacement(Context context, Placement placement) {
        MultiblockState verification = new MultiblockState(context.world(), context.origin(), context.structureName());
        return verification.update(placement.position(), placement.predicate()) &&
                placement.predicate().test(verification) &&
                (!placement.requiresPart() || hasMatchingRequiredPart(
                        context, placement.predicate(), placement.position()));
    }

    private static String rollbackFailureDetail(boolean worldRestored, RefundOutcome refundOutcome) {
        if (!worldRestored && !refundOutcome.completed()) {
            return "A staging operation failed, at least one captured world position could not be restored, and " +
                    refundOutcome.detail();
        }
        if (!worldRestored) {
            return "A staging operation failed and at least one captured world position could not be restored";
        }
        return "A staging operation failed and " + refundOutcome.detail();
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

    /**
     * Owns NeoForge's server-level snapshot capture flag for the silent pre-commit interval.
     *
     * <p>
     * Snapshot capture suppresses {@code markAndNotifyBlock} and the new state {@code onPlace} callback while a
     * staging write changes the target state. The transaction maintains its own exact snapshots and discards the
     * framework snapshots before publication, so no external snapshot transaction can be merged accidentally.
     * </p>
     */
    private static final class SnapshotCaptureSession {

        private final Level level;
        private boolean closed;

        private SnapshotCaptureSession(Level level) {
            this.level = level;
            this.level.captureBlockSnapshots = true;
        }

        private static boolean canBegin(Level level) {
            return !level.captureBlockSnapshots && !level.restoringBlockSnapshots && level.capturedBlockSnapshots.isEmpty();
        }

        private static SnapshotCaptureSession begin(Level level) {
            if (!canBegin(level)) {
                throw new IllegalStateException("Cannot start auto-build staging while block snapshot capture is active");
            }
            return new SnapshotCaptureSession(level);
        }

        private void close() {
            if (this.closed) {
                return;
            }
            this.level.captureBlockSnapshots = false;
            this.level.capturedBlockSnapshots.clear();
            this.closed = true;
        }
    }

    /**
     * Exposes staged target states to MDLib validation without exposing virtual block entities from replaced states.
     */
    private record StagedStructureWorldView(StructureWorldView base,
                                            Map<BlockPos, BlockState> stagedStates)
            implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.base.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.stagedStates.getOrDefault(pos, this.base.getBlockState(pos));
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            BlockState stagedState = this.stagedStates.get(pos);
            if (stagedState != null && !stagedState.equals(this.base.getBlockState(pos))) {
                return null;
            }
            return this.base.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.base.registryAccess();
        }
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

    private record ExplicitCandidateOutcome(@Nullable Candidate candidate,
                                            boolean empty,
                                            @Nullable Failure failure) {

        private static ExplicitCandidateOutcome selected(Candidate candidate) {
            return new ExplicitCandidateOutcome(candidate, false, null);
        }

        private static ExplicitCandidateOutcome emptySelection() {
            return new ExplicitCandidateOutcome(null, true, null);
        }

        private static ExplicitCandidateOutcome failure(Failure failure) {
            return new ExplicitCandidateOutcome(null, false, failure);
        }
    }

    private record AllocationOutcome(List<Placement> placements, @Nullable Failure failure) {}

    private record CandidateSelection(@Nullable Candidate candidate,
                                      int inventorySlot,
                                      @Nullable PlacementValidation validation,
                                      @Nullable Failure failure) {

        private static CandidateSelection success(Candidate candidate,
                                                  int inventorySlot,
                                                  PlacementValidation validation) {
            return new CandidateSelection(candidate, inventorySlot, validation, null);
        }

        private static CandidateSelection failure(Failure failure) {
            return new CandidateSelection(null, -1, null, failure);
        }
    }

    private record PlacementValidation(@Nullable Direction partSide,
                                       @Nullable BlockState stagingState,
                                       @Nullable Failure failure) {

        private static PlacementValidation success(@Nullable Direction partSide, BlockState stagingState) {
            return new PlacementValidation(partSide, stagingState, null);
        }

        private static PlacementValidation failed(Failure failure) {
            return new PlacementValidation(null, null, failure);
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

    private record StageOutcome(@Nullable Failure failure,
                                List<StagedBlock> stagedBlocks,
                                List<DeferredPartPlacement> deferredParts,
                                List<ReplacementDrop> replacementDrops) {

        private static StageOutcome success(List<StagedBlock> stagedBlocks,
                                            List<DeferredPartPlacement> deferredParts,
                                            List<ReplacementDrop> replacementDrops) {
            return new StageOutcome(null, List.copyOf(stagedBlocks), List.copyOf(deferredParts),
                    List.copyOf(replacementDrops));
        }

        private static StageOutcome failure(Failure failure) {
            return new StageOutcome(failure, List.of(), List.of(), List.of());
        }
    }

    private record StagingCommit(boolean success,
                                 @Nullable StagedBlock stagedBlock,
                                 List<ReplacementDrop> replacementDrops) {

        private static StagingCommit success(@Nullable StagedBlock stagedBlock, List<ReplacementDrop> replacementDrops) {
            return new StagingCommit(true, stagedBlock, List.copyOf(replacementDrops));
        }

        private static StagingCommit failed() {
            return new StagingCommit(false, null, List.of());
        }
    }

    private record PublicationOutcome(int placed,
                                      @Nullable Failure failure,
                                      List<ReplacementDrop> releasedReplacementDrops,
                                      List<Placement> consumedPlacements) {

        private static PublicationOutcome success(int placed,
                                                  List<ReplacementDrop> releasedReplacementDrops,
                                                  List<Placement> consumedPlacements) {
            return new PublicationOutcome(placed, null, List.copyOf(releasedReplacementDrops),
                    List.copyOf(consumedPlacements));
        }

        private static PublicationOutcome failure(int placed,
                                                  Failure failure,
                                                  List<ReplacementDrop> releasedReplacementDrops,
                                                  List<Placement> consumedPlacements) {
            return new PublicationOutcome(placed, failure, List.copyOf(releasedReplacementDrops),
                    List.copyOf(consumedPlacements));
        }
    }

    private record ReplacementDrop(BlockPos position, List<ItemStack> stacks) {}

    private record ReplacementOverflow(BlockPos position, ItemStack stack) {}

    private record StagedBlock(Placement placement,
                               WorldSnapshot snapshot,
                               BlockState stagedState,
                               boolean physicallyStaged,
                               boolean deferredPartHost) {}

    /**
     * Tracks only positions whose state was physically changed before publication.
     */
    private static final class StagingProgress {

        private final Map<BlockPos, WorldSnapshot> physicalSnapshots = new LinkedHashMap<>();

        private void recordPhysicalSnapshot(WorldSnapshot snapshot) {
            this.physicalSnapshots.putIfAbsent(snapshot.position(), snapshot);
        }

        private List<WorldSnapshot> physicalSnapshots() {
            return List.copyOf(this.physicalSnapshots.values());
        }
    }

    private record DeferredPartPlacement(Placement placement) {}

    private record Placement(BlockPos position,
                             TraceabilityPredicate predicate,
                             ItemStack stack,
                             @Nullable BlockState desiredState,
                             @Nullable BlockState stagingState,
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
            boolean stateRestored = level.setBlock(this.position, this.state, QUIET_UPDATE_FLAGS) ||
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

    /**
     * One planned material deduction with its source slot and durable publication target.
     */
    static record MaterialReservation(BlockPos position, int inventorySlot, ItemStack stack) {

        MaterialReservation {
            position = position.immutable();
            stack = stack.copyWithCount(1);
        }
    }

    /**
     * Reports whether every deducted reservation was either returned or deliberately retained after publication.
     */
    static record RefundOutcome(boolean completed, @Nullable String detail) {

        private static RefundOutcome success() {
            return new RefundOutcome(true, null);
        }

        private static RefundOutcome failure(String detail) {
            return new RefundOutcome(false, detail);
        }
    }

    /**
     * Owns only the material actually deducted by this auto-build request.
     */
    static final class InventoryTransaction {

        private final Inventory inventory;
        private final String structureName;
        private final List<MaterialReservation> reservations;
        private final boolean creative;
        private final List<ReservationLine> ledger = new ArrayList<>();
        private final Map<BlockPos, ReservationLine> reservationLinesByPosition = new LinkedHashMap<>();
        private boolean committed;
        private boolean closed;
        private boolean refunding;

        InventoryTransaction(String structureName,
                             Inventory inventory,
                             List<MaterialReservation> reservations,
                             boolean creative) {
            this.structureName = structureName;
            this.inventory = inventory;
            this.reservations = List.copyOf(reservations);
            this.creative = creative;
        }

        boolean commit() {
            if (this.closed || this.committed || !this.ledger.isEmpty()) {
                throw new IllegalStateException("Auto-build inventory transaction cannot be committed more than once");
            }
            if (this.creative) {
                this.committed = true;
                return true;
            }
            LinkedHashMap<Integer, List<MaterialReservation>> reservationsBySlot = new LinkedHashMap<>();
            for (MaterialReservation reservation : this.reservations) {
                reservationsBySlot.computeIfAbsent(reservation.inventorySlot(), ignored -> new ArrayList<>()).add(reservation);
            }
            LinkedHashSet<BlockPos> reservationPositions = new LinkedHashSet<>();
            for (Map.Entry<Integer, List<MaterialReservation>> entry : reservationsBySlot.entrySet()) {
                ItemStack current = this.inventory.getItem(entry.getKey());
                ItemStack expected = entry.getValue().getFirst().stack();
                if (!ItemStack.isSameItemSameComponents(current, expected) ||
                        current.getCount() < entry.getValue().size()) {
                    return false;
                }
                for (MaterialReservation reservation : entry.getValue()) {
                    if (!ItemStack.isSameItemSameComponents(reservation.stack(), expected)) {
                        return false;
                    }
                    if (!reservationPositions.add(reservation.position())) {
                        throw new IllegalStateException("Duplicate material reservation position " + reservation.position());
                    }
                }
            }
            for (Map.Entry<Integer, List<MaterialReservation>> entry : reservationsBySlot.entrySet()) {
                int actualDeducted = entry.getValue().size();
                ItemStack expectedStack = entry.getValue().getFirst().stack();
                ItemStack remaining = this.inventory.getItem(entry.getKey()).copy();
                remaining.shrink(actualDeducted);
                ReservationLine line = new ReservationLine(
                        entry.getKey(), expectedStack, actualDeducted, remaining, entry.getValue());
                this.inventory.setItem(entry.getKey(), remaining);
                this.ledger.add(line);
                for (MaterialReservation reservation : entry.getValue()) {
                    this.reservationLinesByPosition.put(reservation.position(), line);
                }
            }
            this.inventory.setChanged();
            this.committed = true;
            return true;
        }

        RefundOutcome rollback(Player player) {
            if (this.closed) {
                return RefundOutcome.success();
            }
            if (this.creative) {
                this.complete();
                return RefundOutcome.success();
            }
            return this.refund(player);
        }

        private RefundOutcome settlePublicationFailure(Player player, List<Placement> consumedPlacements) {
            if (this.closed) {
                return RefundOutcome.success();
            }
            if (this.creative) {
                this.complete();
                return RefundOutcome.success();
            }
            for (Placement consumedPlacement : consumedPlacements) {
                ReservationLine line = this.reservationLinesByPosition.get(consumedPlacement.position());
                if (line == null || line.sourceSlot() != consumedPlacement.inventorySlot() ||
                        !line.markPublished(consumedPlacement.position())) {
                    throw new IllegalStateException("Published placement does not match its material reservation at " +
                            consumedPlacement.position());
                }
            }
            return this.refund(player);
        }

        private RefundOutcome refund(Player player) {
            if (this.refunding) {
                return RefundOutcome.failure("auto-build material refund is already in progress");
            }
            this.refunding = true;
            try {
                return this.refundOutstanding(player);
            } finally {
                this.refunding = false;
            }
        }

        private RefundOutcome refundOutstanding(Player player) {
            boolean inventoryChanged = false;
            boolean refundFailed = false;
            for (ReservationLine line : this.ledger) {
                if (!line.hasOutstanding()) {
                    continue;
                }
                int outstandingBefore = line.outstanding();
                try {
                    this.refundReservation(player, line);
                } catch (RuntimeException exception) {
                    refundFailed = true;
                    LOGGER.error("Unable to refund auto-build material for structure {} at {}: stack {}, count {}, " +
                            "source slot {}, expected remaining {}, player {}",
                            this.structureName,
                            line.firstPosition(),
                            line.stack(),
                            line.outstanding(),
                            line.sourceSlot(),
                            line.expectedRemaining(),
                            player.getGameProfile().getName(),
                            exception);
                }
                inventoryChanged |= line.outstanding() != outstandingBefore;
            }
            if (inventoryChanged) {
                this.inventory.setChanged();
            }
            if (refundFailed) {
                return RefundOutcome.failure("one or more deducted materials could not be refunded; see server log");
            }
            this.complete();
            return RefundOutcome.success();
        }

        private void refundReservation(Player player, ReservationLine line) {
            ItemStack remaining = line.refundableStack();
            this.restoreOriginalSlot(line, remaining);
            if (!remaining.isEmpty()) {
                this.insertRefund(line, remaining, player);
            }
            if (!remaining.isEmpty()) {
                this.dropRefund(player, line, remaining);
            }
            if (line.hasOutstanding()) {
                throw new IllegalStateException("Auto-build refund finished without settling its material ledger");
            }
        }

        private void restoreOriginalSlot(ReservationLine line, ItemStack remaining) {
            ItemStack current = this.inventory.getItem(line.sourceSlot());
            if (current.isEmpty()) {
                int restored = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                this.inventory.setItem(line.sourceSlot(), remaining.copyWithCount(restored));
                remaining.shrink(restored);
                line.recordRefunded(restored);
                return;
            }
            if (!ItemStack.isSameItemSameComponents(current, remaining)) {
                return;
            }
            int freeSpace = current.getMaxStackSize() - current.getCount();
            if (freeSpace <= 0) {
                return;
            }
            int restored = Math.min(freeSpace, remaining.getCount());
            ItemStack merged = current.copy();
            merged.grow(restored);
            this.inventory.setItem(line.sourceSlot(), merged);
            remaining.shrink(restored);
            line.recordRefunded(restored);
        }

        private void insertRefund(ReservationLine line, ItemStack remaining, Player player) {
            int countBeforeInsertion = remaining.getCount();
            try {
                this.inventory.add(remaining);
            } catch (RuntimeException exception) {
                line.recordRefunded(countBeforeInsertion - remaining.getCount());
                LOGGER.error("Unable to insert auto-build refund for structure {} at {}: stack {}, count {}, " +
                        "source slot {}, player {}; dropping its remainder",
                        this.structureName,
                        line.firstPosition(),
                        line.stack(),
                        remaining.getCount(),
                        line.sourceSlot(),
                        player.getGameProfile().getName(),
                        exception);
                return;
            }
            line.recordRefunded(countBeforeInsertion - remaining.getCount());
        }

        private void dropRefund(Player player, ReservationLine line, ItemStack remaining) {
            while (!remaining.isEmpty()) {
                int count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack stack = remaining.copyWithCount(count);
                this.dropRefundStack(player, line, stack);
                remaining.shrink(count);
                line.recordRefunded(count);
            }
        }

        private void dropRefundStack(Player player, ReservationLine line, ItemStack stack) {
            try {
                ItemEntity refundEntity = new ItemEntity(
                        player.level(), player.getX(), player.getY(), player.getZ(), stack.copy());
                if (player.level().addFreshEntity(refundEntity)) {
                    return;
                }
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Unable to deliver final auto-build refund " + stack, exception);
            }
            throw new IllegalStateException("World rejected final auto-build refund " + stack);
        }

        private void complete() {
            if (this.closed) {
                return;
            }
            if (!this.creative && this.committed) {
                this.inventory.setChanged();
            }
            this.ledger.clear();
            this.reservationLinesByPosition.clear();
            this.committed = false;
            this.closed = true;
        }

        private static final class ReservationLine {

            private final int sourceSlot;
            private final ItemStack stack;
            private final int actualDeducted;
            private final ItemStack expectedRemaining;
            private final BlockPos firstPosition;
            private final Map<BlockPos, MaterialReservation> outstandingReservations = new LinkedHashMap<>();
            private int outstanding;

            private ReservationLine(int sourceSlot,
                                    ItemStack stack,
                                    int actualDeducted,
                                    ItemStack expectedRemaining,
                                    List<MaterialReservation> reservations) {
                if (actualDeducted <= 0 || actualDeducted != reservations.size()) {
                    throw new IllegalArgumentException("Material reservation line must contain every actual deduction");
                }
                this.sourceSlot = sourceSlot;
                this.stack = stack.copyWithCount(1);
                this.actualDeducted = actualDeducted;
                this.expectedRemaining = expectedRemaining.copy();
                this.firstPosition = reservations.getFirst().position();
                this.outstanding = this.actualDeducted;
                for (MaterialReservation reservation : reservations) {
                    if (reservation.inventorySlot() != sourceSlot ||
                            !ItemStack.isSameItemSameComponents(reservation.stack(), this.stack)) {
                        throw new IllegalArgumentException("Material reservation does not match its source-slot ledger line");
                    }
                    if (this.outstandingReservations.put(reservation.position(), reservation) != null) {
                        throw new IllegalArgumentException("Material reservation position was assigned more than once");
                    }
                }
            }

            private int sourceSlot() {
                return this.sourceSlot;
            }

            private ItemStack stack() {
                return this.stack.copy();
            }

            private ItemStack expectedRemaining() {
                return this.expectedRemaining.copy();
            }

            private int outstanding() {
                return this.outstanding;
            }

            private boolean hasOutstanding() {
                return this.outstanding > 0;
            }

            private ItemStack refundableStack() {
                return this.stack.copyWithCount(this.outstanding);
            }

            private BlockPos firstPosition() {
                return this.firstPosition;
            }

            private boolean markPublished(BlockPos position) {
                if (this.outstandingReservations.remove(position) == null) {
                    return false;
                }
                this.outstanding--;
                return true;
            }

            private void recordRefunded(int count) {
                if (count <= 0) {
                    return;
                }
                if (count > this.outstanding) {
                    throw new IllegalArgumentException("Refund exceeds outstanding auto-build material");
                }
                this.outstanding -= count;
                if (this.outstanding == 0) {
                    this.outstandingReservations.clear();
                }
            }
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

        private PreviewPredicateKey predicateKey(int z, int yOffset, int xOffset) {
            return new PreviewPredicateKey(z, yOffset - this.minY, xOffset - this.minX);
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
}
