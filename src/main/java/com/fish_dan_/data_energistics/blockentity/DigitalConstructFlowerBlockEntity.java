package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.PatternBufferCompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonDeclaredCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPatternMatcher;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerCraftingStatus;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenuHost;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DigitalConstructFlowerBlockEntity extends AENetworkedBlockEntity
                                               implements MultiBlockStatusProvider, CompartmentHost, DigitalConstructFlowerMenuHost {

    private static final int RECHECK_RADIUS = 24;
    private static final int RECHECK_INTERVAL_TICKS = 100;
    private static final String FORMED_TAG = "formed";
    private static final String MATCHED_POSITIONS_TAG = "matched_positions";
    private static final String LAST_FAILURE_REASON_TAG = "last_failure_reason";
    private static final String LAST_FAILURE_POSITION_TAG = "last_failure_position";
    private static final String CRAFTING_RUNTIME_TAG = "crafting_runtime";
    private static final String NO_FAILURE = "";
    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private boolean formed;
    private List<BlockPos> matchedPositions = List.of();
    private String lastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos lastFailurePosition;
    private boolean recheckRequested = true;
    private final CompartmentHostState compartmentHostState = new CompartmentHostState();
    private final JsonMultiBlockCompartmentBinder compartmentBinder = new JsonDeclaredCompartmentBinder();
    private final DigitalConstructFlowerCraftingRuntime craftingRuntime = new DigitalConstructFlowerCraftingRuntime(this);

    public DigitalConstructFlowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DIGITAL_CONSTRUCT_FLOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get())
                .setExposedOnSides(getCableExposedSides(blockState))
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.craftingRuntime.restorePendingPartitionLogic(level.registryAccess());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        if (!isCableSideExposed(dir)) {
            return AECableType.NONE;
        }
        return AECableType.COVERED;
    }

    private boolean isCableSideExposed(Direction dir) {
        Direction front = this.getBlockState().getValue(DataRipperReassemblerBlock.FACING);
        return dir != Direction.UP && dir != front;
    }

    private static Set<Direction> getCableExposedSides(BlockState blockState) {
        Direction front = blockState.getValue(DataRipperReassemblerBlock.FACING);
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        sides.remove(front);
        return sides;
    }

    @Override
    public void onReady() {
        super.onReady();
        requireMainJsonDefinitionKey();
        requestStructureRecheck();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (!this.recheckRequested && Math.floorMod(this.level.getGameTime() + this.worldPosition.asLong(), RECHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        this.recheckRequested = false;
        updateStructureMatch(this.level);
    }

    @Override
    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    @Override
    public boolean multiBlock$isOnline() {
        return isOnline();
    }

    @Override
    public boolean isStructureFormed() {
        return this.formed;
    }

    @Override
    public boolean multiBlock$isFormed() {
        return isStructureFormed();
    }

    @Override
    public boolean multiBlock$isController() {
        return this.formed;
    }

    @Override
    public int multiBlock$getHeight() {
        return 0;
    }

    @Override
    public int multiBlock$getMatchedBlockCount() {
        return getMatchedBlockCount();
    }

    @Override
    public int getMatchedBlockCount() {
        return this.matchedPositions.size();
    }

    @Override
    public int getPatternBufferCount() {
        return patternBuffers().size();
    }

    public List<BlockPos> getMatchedPositions() {
        return this.matchedPositions;
    }

    @Override
    public String getLastFailureReason() {
        return this.lastFailureReason;
    }

    @Override
    public String multiBlock$getLastFailureReason() {
        return getLastFailureReason();
    }

    @Override
    public @Nullable BlockPos getLastFailurePosition() {
        return this.lastFailurePosition;
    }

    @Override
    public @Nullable BlockPos multiBlock$getLastFailurePosition() {
        return getLastFailurePosition();
    }

    public void requestStructureRecheck() {
        this.recheckRequested = true;
    }

    /**
     * Returns the main structure's aggregate input view without exposing concrete compartment block entities.
     */
    public CompartmentStorage compartmentInputStorage() {
        if (!this.formed) {
            return UnavailableCompartmentStorage.INSTANCE;
        }
        return compartmentHost$inputStorage(mainDefinitionKey().structureName());
    }

    /**
     * Returns the main structure's aggregate output view without exposing concrete compartment block entities.
     */
    public CompartmentStorage compartmentOutputStorage() {
        if (!this.formed) {
            return UnavailableCompartmentStorage.INSTANCE;
        }
        return compartmentHost$outputStorage(mainDefinitionKey().structureName());
    }

    /**
     * Returns the main structure's aggregate pattern buffer view without exposing concrete compartment block entities.
     */
    public CompartmentStorage patternBufferStorage() {
        if (!this.formed) {
            return UnavailableCompartmentStorage.INSTANCE;
        }
        return compartmentHost$patternBufferStorage(mainDefinitionKey().structureName());
    }

    /**
     * Returns main structure pattern buffers for recipe logic without depending on concrete block entities.
     */
    public Collection<PatternBufferCompartmentPart> patternBuffers() {
        if (!this.formed) {
            return List.of();
        }
        return compartmentHost$getPatternBuffers(mainDefinitionKey().structureName());
    }

    /**
     * Replaces CPU data contributed by a named child structure.
     *
     * @param structureName child structure name that owns the contribution
     * @param contribution  CPU data contributed by the child structure
     */
    public void setCpuContribution(String structureName, DigitalConstructFlowerCpuContribution contribution) {
        this.craftingRuntime.setContribution(structureName, contribution);
        notifyCraftingCpuChanged();
        setChanged();
    }

    /**
     * Clears CPU data contributed by a named child structure.
     *
     * @param structureName child structure name to remove
     */
    public void clearCpuContribution(String structureName) {
        this.craftingRuntime.clearContribution(structureName);
        notifyCraftingCpuChanged();
        setChanged();
    }

    /**
     * @return virtual CPU partitions currently exposed by this formed structure
     */
    public List<DigitalConstructFlowerVirtualCpu> getCpuPartitions() {
        return this.craftingRuntime.partitions();
    }

    /**
     * @return crafting runtime used by AE2 CraftingService mixins
     */
    public DigitalConstructFlowerCraftingRuntime getCraftingRuntime() {
        return this.craftingRuntime;
    }

    @Override
    public DigitalConstructFlowerCraftingStatus getCraftingStatus() {
        var node = this.getMainNode().getNode();
        if (node == null || !node.isActive() || node.getGrid() == null) {
            return DigitalConstructFlowerCraftingStatus.EMPTY;
        }

        int busyCpuCount = 0;
        CraftingJobStatus selectedJob = null;
        for (ICraftingCPU cpu : node.getGrid().getCraftingService().getCpus()) {
            if (!cpu.isBusy()) {
                continue;
            }

            busyCpuCount++;
            CraftingJobStatus jobStatus = cpu.getJobStatus();
            if (jobStatus == null || !hasCraftingTarget(jobStatus.crafting())) {
                continue;
            }
            if (selectedJob == null || jobStatus.elapsedTimeNanos() > selectedJob.elapsedTimeNanos()) {
                selectedJob = jobStatus;
            }
        }

        if (selectedJob == null) {
            return new DigitalConstructFlowerCraftingStatus(null, busyCpuCount);
        }
        return new DigitalConstructFlowerCraftingStatus(selectedJob.crafting(), busyCpuCount);
    }

    private static boolean hasCraftingTarget(@Nullable GenericStack stack) {
        return stack != null && stack.what() != null && stack.amount() > 0;
    }

    public static void requestRecheckAround(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }

        BlockPos min = origin.offset(-RECHECK_RADIUS, -RECHECK_RADIUS, -RECHECK_RADIUS);
        BlockPos max = origin.offset(RECHECK_RADIUS, RECHECK_RADIUS, RECHECK_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DigitalConstructFlowerBlockEntity flower) {
                flower.requestStructureRecheck();
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.formed = data.getBoolean(FORMED_TAG);
        this.matchedPositions = readMatchedPositions(data);
        this.lastFailureReason = data.getString(LAST_FAILURE_REASON_TAG);
        if (data.contains(LAST_FAILURE_POSITION_TAG)) {
            this.lastFailurePosition = BlockPos.of(data.getLong(LAST_FAILURE_POSITION_TAG));
        } else {
            this.lastFailurePosition = null;
        }
        this.craftingRuntime.setMainStructureFormed(this.formed);
        if (data.contains(CRAFTING_RUNTIME_TAG, Tag.TAG_COMPOUND)) {
            this.craftingRuntime.readFromTag(data.getCompound(CRAFTING_RUNTIME_TAG), registries);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putBoolean(FORMED_TAG, this.formed);
        data.put(MATCHED_POSITIONS_TAG, createMatchedPositionsTag());
        data.putString(LAST_FAILURE_REASON_TAG, this.lastFailureReason);
        if (this.lastFailurePosition != null) {
            data.putLong(LAST_FAILURE_POSITION_TAG, this.lastFailurePosition.asLong());
        }
        CompoundTag runtimeTag = new CompoundTag();
        this.craftingRuntime.writeToTag(runtimeTag, registries);
        data.put(CRAFTING_RUNTIME_TAG, runtimeTag);
    }

    private void updateStructureMatch(Level level) {
        JsonMultiBlockDefinition definition = requireMainJsonDefinition();
        Direction preferredFrontFacing = getStructureFrontFacing(level);
        StructureWorldView world = new LevelStructureWorldView(level);
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world,
                this.worldPosition,
                preferredFrontFacing,
                mainDefinitionKey().structureName());

        if (result.matched()) {
            Map<BlockPos, CompartmentType> declaredCompartments = JsonMultiBlockCompartmentPredicate.declaredCompartments(
                    result.context());
            PatternDiagnostic compartmentFailure = this.compartmentBinder.validate(world, result, declaredCompartments);
            if (compartmentFailure != null) {
                applyFailure(compartmentFailure, mainDefinitionKey().structureName());
                return;
            }
            normalizeHostFacing(level, preferredFrontFacing, result.frontFacing());
            applyMatch(world, result.positions(), declaredCompartments, mainDefinitionKey().structureName());
        } else {
            applyFailure(result.diagnostic(), mainDefinitionKey().structureName());
        }
    }

    private Direction getStructureFrontFacing(Level level) {
        BlockState state = level.getBlockState(this.worldPosition);
        return JsonMultiBlockFrontFacing.fromPlacedHost(
                state,
                DataRipperReassemblerBlock.FACING,
                this.worldPosition,
                "Digital Construct Flower");
    }

    private void normalizeHostFacing(Level level, Direction preferredFrontFacing, Direction matchedFrontFacing) {
        if (preferredFrontFacing == matchedFrontFacing) {
            return;
        }
        BlockState state = level.getBlockState(this.worldPosition);
        Direction hostFacing = JsonMultiBlockFrontFacing.toPlacedHostFacing(matchedFrontFacing);
        if (state.hasProperty(DataRipperReassemblerBlock.FACING) &&
                state.getValue(DataRipperReassemblerBlock.FACING) != hostFacing) {
            BlockState updatedState = state.setValue(DataRipperReassemblerBlock.FACING, hostFacing);
            this.getMainNode().setExposedOnSides(getCableExposedSides(updatedState));
            level.setBlock(
                    this.worldPosition,
                    updatedState,
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private void applyMatch(StructureWorldView world,
                            List<BlockPos> positions,
                            Map<BlockPos, CompartmentType> declaredCompartments,
                            String structureName) {
        List<BlockPos> nextPositions = List.copyOf(positions);
        if (this.formed && this.matchedPositions.equals(nextPositions) && NO_FAILURE.equals(this.lastFailureReason) && this.lastFailurePosition == null) {
            this.compartmentBinder.ensureBound(world, structureName, this, declaredCompartments);
            return;
        }
        clearCompartmentBindings(structureName);
        this.formed = true;
        this.matchedPositions = nextPositions;
        this.compartmentBinder.bind(world, structureName, this, declaredCompartments);
        this.lastFailureReason = NO_FAILURE;
        this.lastFailurePosition = null;
        this.craftingRuntime.setMainStructureFormed(true);
        notifyCraftingCpuChanged();
        setChanged();
    }

    private void applyFailure(PatternDiagnostic diagnostic, String structureName) {
        String nextFailureReason;
        BlockPos nextFailurePosition;
        if (diagnostic == null) {
            nextFailureReason = "Structure pattern did not match";
            nextFailurePosition = null;
        } else {
            nextFailureReason = diagnostic.message();
            nextFailurePosition = diagnostic.position();
        }
        if (!this.formed && this.matchedPositions.isEmpty() && Objects.equals(this.lastFailureReason, nextFailureReason) &&
                Objects.equals(this.lastFailurePosition, nextFailurePosition)) {
            return;
        }
        LOGGER.warn(
                "Digital Construct Flower structure '{}' failed at {}: {}",
                structureName,
                nextFailurePosition,
                nextFailureReason);
        clearCompartmentBindings(structureName);
        this.formed = false;
        this.matchedPositions = List.of();
        this.lastFailureReason = nextFailureReason;
        this.lastFailurePosition = nextFailurePosition;
        this.craftingRuntime.setMainStructureFormed(false);
        notifyCraftingCpuChanged();
        setChanged();
    }

    private void clearCompartmentBindings(String structureName) {
        this.compartmentBinder.unbind(structureName, this);
        this.compartmentHostState.clear(structureName);
    }

    @Override
    public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
        this.compartmentHostState.addCompartment(structureName, part);
    }

    @Override
    public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
        this.compartmentHostState.removeCompartment(structureName, part);
    }

    @Override
    public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
        return this.compartmentHostState.compartments(structureName);
    }

    private static JsonMultiBlockDefinition requireMainJsonDefinition() {
        JsonMultiBlockStructureKey key = mainDefinitionKey();
        return ModVerticalMultiBlocks.JSON_MULTI_BLOCKS
                .get(key)
                .orElseThrow(() -> new IllegalStateException("Missing JSON multiblock definition: " + key));
    }

    private static void requireMainJsonDefinitionKey() {
        requireMainJsonDefinition();
    }

    private static JsonMultiBlockStructureKey mainDefinitionKey() {
        return JsonMultiBlockStructureKey.main(ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_ID));
    }

    private static List<BlockPos> readMatchedPositions(CompoundTag data) {
        ListTag positions = data.getList(MATCHED_POSITIONS_TAG, Tag.TAG_LONG);
        return positions.stream()
                .map(tag -> BlockPos.of(((LongTag) tag).getAsLong()))
                .toList();
    }

    private ListTag createMatchedPositionsTag() {
        ListTag positions = new ListTag();
        for (BlockPos pos : this.matchedPositions) {
            positions.add(LongTag.valueOf(pos.asLong()));
        }
        return positions;
    }

    private void notifyCraftingCpuChanged() {
        var node = this.getMainNode().getNode();
        if (node != null) {
            node.getGrid().postEvent(new GridCraftingCpuChange(node));
        }
    }

    private record LevelStructureWorldView(Level level) implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }
    }
}
