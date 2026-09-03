package com.fish_dan_.data_energistics.blockentity.multiblock;

import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPart;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRuntimeBinding;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRuntimeState;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockScanner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared controller and part runtime for vertical multiblock block entities.
 */
public abstract class AbstractVerticalMultiBlockBlockEntity extends AENetworkedBlockEntity implements MultiBlockStatusProvider, VerticalMultiBlockController, VerticalMultiBlockPart {

    private final Map<String, VerticalMultiBlockRuntimeState> verticalMultiBlockStates = new Object2ObjectOpenHashMap<>();
    /**
     * Last issued callback identity for each named structure, retained after that structure becomes unformed.
     */
    private final Object2LongOpenHashMap<String> verticalMultiBlockBindingEpochs = new Object2LongOpenHashMap<>();
    private boolean verticalMultiBlockRecheckRequested = true;
    private boolean defaultStructureController;

    protected AbstractVerticalMultiBlockBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    public final boolean isVerticalMultiBlockFormed() {
        return verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME).formed();
    }

    public final int getVerticalMultiBlockHeight() {
        return verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME).height();
    }

    public final boolean isVerticalMultiBlockController() {
        return isVerticalMultiBlockFormed() && this.defaultStructureController;
    }

    @Override
    public final boolean multiBlock$isOnline() {
        return this.getMainNode().isOnline();
    }

    @Override
    public final boolean multiBlock$isFormed() {
        return isVerticalMultiBlockFormed();
    }

    @Override
    public final boolean multiBlock$isController() {
        return isVerticalMultiBlockController();
    }

    @Override
    public final int multiBlock$getHeight() {
        return getVerticalMultiBlockHeight();
    }

    @Override
    public final String multiBlock$getLastFailureReason() {
        return "";
    }

    @Override
    public final BlockPos multiBlock$getLastFailurePosition() {
        return null;
    }

    @Override
    public final void verticalMultiBlock$onStructureFormed(VerticalMultiBlockContext<?> context) {
        verticalMultiBlock$onStructureFormed(context.structureName(), context);
    }

    @Override
    public final void verticalMultiBlock$onStructureFormed(String structureName, VerticalMultiBlockContext<?> context) {
        requireStructureName(structureName);
        if (VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME.equals(structureName)) {
            this.defaultStructureController = true;
        }
        onVerticalMultiBlockStructureFormed(structureName, context);
        setChanged();
    }

    @Override
    public final void verticalMultiBlock$onStructureInvalid(String reason) {
        verticalMultiBlock$onStructureInvalid(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME, reason);
    }

    @Override
    public final void verticalMultiBlock$onStructureInvalid(String structureName, String reason) {
        requireStructureName(structureName);
        if (VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME.equals(structureName)) {
            this.defaultStructureController = false;
        }
        onVerticalMultiBlockStructureInvalid(structureName, reason);
        setChanged();
    }

    @Override
    public final void verticalMultiBlock$requestRecheck() {
        this.verticalMultiBlockRecheckRequested = true;
    }

    @Override
    public final VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState() {
        return verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME);
    }

    @Override
    public final void verticalMultiBlock$setRuntimeState(VerticalMultiBlockRuntimeState state) {
        verticalMultiBlock$setRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME, state);
    }

    @Override
    public final VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState(String structureName) {
        requireStructureName(structureName);
        return this.verticalMultiBlockStates.getOrDefault(
                structureName,
                VerticalMultiBlockRuntimeState.unformed(this.verticalMultiBlockBindingEpochs.getLong(structureName)));
    }

    @Override
    public final void verticalMultiBlock$setRuntimeState(String structureName, VerticalMultiBlockRuntimeState state) {
        requireStructureName(structureName);
        long previousBindingEpoch = this.verticalMultiBlockBindingEpochs.getLong(structureName);
        if (state.bindingEpoch() < previousBindingEpoch) {
            throw new IllegalArgumentException("Vertical multiblock binding epoch cannot move backwards for " + structureName);
        }
        this.verticalMultiBlockBindingEpochs.put(structureName, state.bindingEpoch());
        if (state.formed()) {
            this.verticalMultiBlockStates.put(structureName, state);
        } else {
            this.verticalMultiBlockStates.remove(structureName);
        }
        if (!verticalMultiBlock$getRuntimeState(VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME).formed()) {
            this.defaultStructureController = false;
        }
        setChanged();
    }

    @Override
    public final Set<String> verticalMultiBlock$getFormedStructureNames() {
        return Set.copyOf(this.verticalMultiBlockStates.keySet());
    }

    @Override
    public final Map<String, VerticalMultiBlockRuntimeState> verticalMultiBlock$getRuntimeStates() {
        return Collections.unmodifiableMap(new Object2ObjectOpenHashMap<>(this.verticalMultiBlockStates));
    }

    @Override
    public final void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                           String structureName,
                                                           VerticalMultiBlockContext<?> context,
                                                           long bindingEpoch) {
        requireStructureName(structureName);
        if (VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME.equals(structureName)) {
            this.defaultStructureController = controller == this;
        }
        verticalMultiBlock$setRuntimeState(structureName, new VerticalMultiBlockRuntimeState(
                true,
                context.definition().id(),
                structureName,
                context.height(),
                List.copyOf(context.matchedPositions()),
                bindingEpoch));
        onVerticalMultiBlockAddedToController(controller, structureName, context);
    }

    @Override
    public final void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                               String structureName,
                                                               long bindingEpoch) {
        requireStructureName(structureName);
        VerticalMultiBlockRuntimeState previousState = verticalMultiBlock$getRuntimeState(structureName);
        if (!previousState.formed() || previousState.bindingEpoch() != bindingEpoch) {
            return;
        }
        verticalMultiBlock$setRuntimeState(structureName, VerticalMultiBlockRuntimeState.unformed(bindingEpoch));
        onVerticalMultiBlockRemovedFromController(controller, structureName, previousState);
    }

    protected final void onVerticalMultiBlockReady() {
        verticalMultiBlock$requestRecheck();
    }

    protected final void serverTickVerticalMultiBlock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (this.verticalMultiBlockRecheckRequested) {
            this.verticalMultiBlockRecheckRequested = false;
            checkVerticalMultiBlock();
        }
    }

    @SuppressWarnings("unused")
    protected void onVerticalMultiBlockStructureFormed(String structureName, VerticalMultiBlockContext<?> context) {}

    @SuppressWarnings("unused")
    protected void onVerticalMultiBlockStructureInvalid(String structureName, String reason) {}

    @SuppressWarnings("unused")
    protected void onVerticalMultiBlockAddedToController(VerticalMultiBlockController controller,
                                                         String structureName,
                                                         VerticalMultiBlockContext<?> context) {}

    @SuppressWarnings("unused")
    protected void onVerticalMultiBlockRemovedFromController(VerticalMultiBlockController controller,
                                                             String structureName,
                                                             VerticalMultiBlockRuntimeState previousState) {}

    protected abstract VerticalMultiBlockDefinition<BlockState> verticalMultiBlock$getDefinition();

    protected abstract boolean verticalMultiBlock$isControllerBlockedBy(BlockState belowState);

    protected abstract String verticalMultiBlock$getControllerBlockedInvalidReason();

    protected Collection<VerticalMultiBlockPart> resolveParts(List<VerticalMultiBlockPos> matchedPositions) {
        if (this.level == null) {
            throw new IllegalStateException("Cannot resolve vertical multiblock parts without a level");
        }
        Map<VerticalMultiBlockPos, VerticalMultiBlockPart> parts = new Object2ObjectOpenHashMap<>();
        for (VerticalMultiBlockPos pos : matchedPositions) {
            BlockEntity blockEntity = this.level.getBlockEntity(toBlockPos(pos));
            if (blockEntity instanceof VerticalMultiBlockPart part) {
                parts.put(pos, part);
            }
        }
        return parts.values();
    }

    protected VerticalMultiBlockRuntimeBinding<BlockState> createVerticalMultiBlockBinding() {
        return new VerticalMultiBlockRuntimeBinding<>(createVerticalMultiBlockScanner());
    }

    protected VerticalMultiBlockScanner<BlockState> createVerticalMultiBlockScanner() {
        if (this.level == null) {
            throw new IllegalStateException("Cannot create vertical multiblock scanner without a level");
        }
        return new VerticalMultiBlockScanner<>(pos -> this.level.getBlockState(toBlockPos(pos)));
    }

    @SuppressWarnings("SameParameterValue")
    protected static void requestVerticalMultiBlockRecheckAround(Level level,
                                                                 BlockPos origin,
                                                                 int radius,
                                                                 Predicate<BlockEntity> blockEntityFilter) {
        if (radius < 0) {
            throw new IllegalArgumentException("Vertical multiblock recheck radius must not be negative");
        }
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }

        for (int offset = -radius; offset <= radius; offset++) {
            BlockEntity blockEntity = level.getBlockEntity(origin.above(offset));
            if (blockEntity instanceof AbstractVerticalMultiBlockBlockEntity verticalMultiBlock && blockEntityFilter.test(blockEntity)) {
                verticalMultiBlock.verticalMultiBlock$requestRecheck();
            }
        }
    }

    protected static VerticalMultiBlockPos toVerticalPos(BlockPos pos) {
        return new VerticalMultiBlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    protected static BlockPos toBlockPos(VerticalMultiBlockPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private void checkVerticalMultiBlock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        if (verticalMultiBlock$isControllerBlockedBy(this.level.getBlockState(this.worldPosition.below()))) {
            if (this.defaultStructureController) {
                createVerticalMultiBlockBinding().invalidate(
                        this,
                        this::resolveParts,
                        verticalMultiBlock$getControllerBlockedInvalidReason());
            }
            return;
        }

        createVerticalMultiBlockBinding().requestRecheck(
                this,
                verticalMultiBlock$getDefinition(),
                toVerticalPos(this.worldPosition),
                this::resolveParts);
    }

    private static void requireStructureName(String structureName) {
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("Vertical multiblock structure name must not be blank");
        }
    }
}
