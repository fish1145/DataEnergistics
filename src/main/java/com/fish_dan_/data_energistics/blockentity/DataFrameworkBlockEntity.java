package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataFrameworkBlock;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPart;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRuntimeBinding;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRuntimeState;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockScanner;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.grid.AENetworkedBlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataFrameworkBlockEntity extends AENetworkedBlockEntity implements VerticalMultiBlockController, VerticalMultiBlockPart {

    private VerticalMultiBlockRuntimeState verticalMultiBlockState = VerticalMultiBlockRuntimeState.unformed();
    private boolean verticalMultiBlockRecheckRequested = true;
    private boolean verticalMultiBlockController;

    public DataFrameworkBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_FRAMEWORK.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePoweredState();
        verticalMultiBlock$requestRecheck();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        updatePoweredState();
        if (this.verticalMultiBlockRecheckRequested) {
            this.verticalMultiBlockRecheckRequested = false;
            checkVerticalMultiBlock();
        }
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean isVerticalMultiBlockFormed() {
        return this.verticalMultiBlockState.formed();
    }

    public int getVerticalMultiBlockHeight() {
        return this.verticalMultiBlockState.height();
    }

    public boolean isVerticalMultiBlockController() {
        return this.verticalMultiBlockState.formed() && this.verticalMultiBlockController;
    }

    public static void requestRecheckAround(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }

        int radius = ModVerticalMultiBlocks.DATA_FRAMEWORK_COLUMN_MAX_HEIGHT;
        for (int offset = -radius; offset <= radius; offset++) {
            BlockEntity blockEntity = level.getBlockEntity(origin.above(offset));
            if (blockEntity instanceof DataFrameworkBlockEntity framework) {
                framework.verticalMultiBlock$requestRecheck();
            }
        }
    }

    @Override
    public String verticalMultiBlock$getDefinitionId() {
        return ModVerticalMultiBlocks.DATA_FRAMEWORK_COLUMN_ID;
    }

    @Override
    public void verticalMultiBlock$onStructureFormed(VerticalMultiBlockContext<?> context) {
        this.verticalMultiBlockController = true;
        setChanged();
    }

    @Override
    public void verticalMultiBlock$onStructureInvalid(String reason) {
        this.verticalMultiBlockController = false;
        setChanged();
    }

    @Override
    public void verticalMultiBlock$requestRecheck() {
        this.verticalMultiBlockRecheckRequested = true;
    }

    @Override
    public VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState() {
        return this.verticalMultiBlockState;
    }

    @Override
    public void verticalMultiBlock$setRuntimeState(VerticalMultiBlockRuntimeState state) {
        this.verticalMultiBlockState = state;
        if (!state.formed()) {
            this.verticalMultiBlockController = false;
        }
        setChanged();
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller, VerticalMultiBlockContext<?> context) {
        this.verticalMultiBlockController = controller == this;
        this.verticalMultiBlockState = new VerticalMultiBlockRuntimeState(
                true,
                context.definition().id(),
                context.height(),
                List.copyOf(context.matchedPositions()));
        setChanged();
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
        this.verticalMultiBlockState = VerticalMultiBlockRuntimeState.unformed();
        this.verticalMultiBlockController = false;
        setChanged();
    }

    private void updatePoweredState() {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataFrameworkBlock)) {
            return;
        }
        boolean online = this.getMainNode().isOnline();
        if (state.getValue(DataFrameworkBlock.POWERED) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataFrameworkBlock.POWERED, online), 3);
        }
    }

    private void checkVerticalMultiBlock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.level.getBlockState(this.worldPosition.below()).is(ModBlocks.DATA_FRAMEWORK.get())) {
            if (this.verticalMultiBlockController) {
                VerticalMultiBlockRuntimeBinding<BlockState> binding = new VerticalMultiBlockRuntimeBinding<>(
                        new VerticalMultiBlockScanner<>(pos -> this.level.getBlockState(toBlockPos(pos))));
                binding.invalidate(this, this::resolveParts, "Data Framework column controller is no longer the bottom block");
            }
            return;
        }

        var definition = ModVerticalMultiBlocks.VERTICAL_MULTI_BLOCKS
                .get(verticalMultiBlock$getDefinitionId())
                .orElseThrow(() -> new IllegalStateException("Missing vertical multiblock definition: " + verticalMultiBlock$getDefinitionId()));
        VerticalMultiBlockRuntimeBinding<BlockState> binding = new VerticalMultiBlockRuntimeBinding<>(
                new VerticalMultiBlockScanner<>(pos -> this.level.getBlockState(toBlockPos(pos))));
        binding.requestRecheck(this, definition, toVerticalPos(this.worldPosition), this::resolveParts);
    }

    private java.util.Collection<VerticalMultiBlockPart> resolveParts(java.util.List<VerticalMultiBlockPos> matchedPositions) {
        if (this.level == null) {
            throw new IllegalStateException("Cannot resolve Data Framework vertical multiblock parts without a level");
        }
        Map<VerticalMultiBlockPos, VerticalMultiBlockPart> parts = new HashMap<>();
        for (VerticalMultiBlockPos pos : matchedPositions) {
            BlockEntity blockEntity = this.level.getBlockEntity(toBlockPos(pos));
            if (blockEntity instanceof VerticalMultiBlockPart part) {
                parts.put(pos, part);
            }
        }
        return parts.values();
    }

    private static VerticalMultiBlockPos toVerticalPos(BlockPos pos) {
        return new VerticalMultiBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos toBlockPos(VerticalMultiBlockPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }
}
