package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.block.TuningForkBaseBlock;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Channel-using AE network base that inserts Echo produced by the fork above it.
 */
public class TuningForkBaseBlockEntity extends AENetworkedBlockEntity {

    private final MachineSource actionSource = new MachineSource(this);

    public TuningForkBaseBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.TUNING_FORK_BASE_BLOCK_ENTITY.get(), pos, state);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.PREFERRED)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(DEBlocks.TUNING_FORK_BASE.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        updateOnlineState();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean canProduceEcho() {
        return isOnline();
    }

    private void updateOnlineState() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState state = this.getBlockState();
        boolean online = isOnline();
        if (state.is(DEBlocks.TUNING_FORK_BASE.get()) && state.hasProperty(TuningForkBaseBlock.ONLINE) && state.getValue(TuningForkBaseBlock.ONLINE) != online) {
            serverLevel.setBlock(this.worldPosition, state.setValue(TuningForkBaseBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Simulates the complete request, then inserts exactly the accepted partial amount.
     */
    public boolean tryInsertEcho(TuningForkVariant variant, int requestedAmount) {
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("Echo request must be positive: " + requestedAmount);
        }
        if (!canProduceEcho()) {
            return false;
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        long simulated = storage.insert(EchoKey.of(), requestedAmount, Actionable.SIMULATE, this.actionSource);
        if (simulated < 0 || simulated > requestedAmount) {
            Data_Energistics.LOGGER.error(
                    "Tuning fork base at {} received invalid simulated Echo insertion for variant {}: expected at most {}, actual {}",
                    this.worldPosition,
                    variant.serializedName(),
                    requestedAmount,
                    simulated);
            return false;
        }
        if (simulated == 0) {
            return false;
        }

        long inserted = storage.insert(EchoKey.of(), simulated, Actionable.MODULATE, this.actionSource);
        if (inserted != simulated) {
            Data_Energistics.LOGGER.error(
                    "Tuning fork base at {} produced an inconsistent Echo insertion for variant {}: expected {}, actual {}",
                    this.worldPosition,
                    variant.serializedName(),
                    simulated,
                    inserted);
        }
        return inserted > 0;
    }
}
