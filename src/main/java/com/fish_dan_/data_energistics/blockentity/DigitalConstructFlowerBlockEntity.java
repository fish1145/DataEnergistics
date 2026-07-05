package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPatternMatcher;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
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

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class DigitalConstructFlowerBlockEntity extends AENetworkedBlockEntity implements MultiBlockStatusProvider {

    private static final int RECHECK_RADIUS = 24;
    private static final int RECHECK_INTERVAL_TICKS = 100;
    private static final String FORMED_TAG = "formed";
    private static final String MATCHED_POSITIONS_TAG = "matched_positions";
    private static final String LAST_FAILURE_REASON_TAG = "last_failure_reason";
    private static final String LAST_FAILURE_POSITION_TAG = "last_failure_position";
    private static final String NO_FAILURE = "";

    private boolean formed;
    private List<BlockPos> matchedPositions = List.of();
    private String lastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos lastFailurePosition;
    private boolean recheckRequested = true;

    public DigitalConstructFlowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DIGITAL_CONSTRUCT_FLOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get())
                .setIdlePowerUsage(0.0D);
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

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    @Override
    public boolean multiBlock$isOnline() {
        return isOnline();
    }

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
        return this.matchedPositions.size();
    }

    public List<BlockPos> getMatchedPositions() {
        return this.matchedPositions;
    }

    public String getLastFailureReason() {
        return this.lastFailureReason;
    }

    @Override
    public String multiBlock$getLastFailureReason() {
        return getLastFailureReason();
    }

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
    }

    private void updateStructureMatch(Level level) {
        JsonMultiBlockDefinition definition = requireMainJsonDefinition();
        Direction preferredFrontFacing = getStructureFrontFacing(level);
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                new LevelStructureWorldView(level),
                this.worldPosition,
                preferredFrontFacing,
                mainDefinitionKey().structureName());

        if (result.matched()) {
            normalizeHostFacing(level, preferredFrontFacing, result.frontFacing());
            applyMatch(result.positions());
        } else {
            applyFailure(result.diagnostic());
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
            level.setBlock(
                    this.worldPosition,
                    state.setValue(DataRipperReassemblerBlock.FACING, hostFacing),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private void applyMatch(List<BlockPos> positions) {
        List<BlockPos> nextPositions = List.copyOf(positions);
        if (this.formed && this.matchedPositions.equals(nextPositions) && NO_FAILURE.equals(this.lastFailureReason) && this.lastFailurePosition == null) {
            return;
        }
        this.formed = true;
        this.matchedPositions = nextPositions;
        this.lastFailureReason = NO_FAILURE;
        this.lastFailurePosition = null;
        setChanged();
    }

    private void applyFailure(PatternDiagnostic diagnostic) {
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
        this.formed = false;
        this.matchedPositions = List.of();
        this.lastFailureReason = nextFailureReason;
        this.lastFailurePosition = nextFailurePosition;
        setChanged();
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
        return JsonMultiBlockStructureKey.main(ResourceLocation.parse(ModVerticalMultiBlocks.DIGITAL_CONSTRUCT_FLOWER_ID));
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
