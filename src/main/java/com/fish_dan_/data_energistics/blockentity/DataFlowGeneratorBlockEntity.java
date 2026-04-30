package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.block.DataFlowGeneratorBlock;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class DataFlowGeneratorBlockEntity extends AENetworkedBlockEntity implements IActionHost, IGridTickable {
    private static final long GENERATED_PER_TICK = 1L;
    private static final TickingRequest TICKING_REQUEST = new TickingRequest(1, 1, false);

    public DataFlowGeneratorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_FLOW_GENERATOR_BLOCK_ENTITY.get(), blockPos, blockState);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModBlocks.DATA_FLOW_GENERATOR.get())
                .setIdlePowerUsage(0.0)
                .addService(IGridTickable.class, this);
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return TICKING_REQUEST;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        boolean online = this.getMainNode().isOnline();
        updateBlockState(online);

        if (online) {
            var inventory = node.getGrid().getStorageService().getInventory();
            inventory.insert(DataFlowKey.of(), GENERATED_PER_TICK, Actionable.MODULATE, IActionSource.ofMachine(this));
        }

        return TickRateModulation.SAME;
    }

    @Override
    public IGridNode getActionableNode() {
        return this.getMainNode().getNode();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DataFlowGeneratorBlockEntity blockEntity) {
        if (!level.isClientSide) {
            blockEntity.updateOnlineState();
        }
    }

    private void updateOnlineState() {
        updateBlockState(this.getMainNode().isOnline());
    }

    private void updateBlockState(boolean online) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataFlowGeneratorBlock)) {
            return;
        }

        if (state.hasProperty(DataFlowGeneratorBlock.LIT) && state.getValue(DataFlowGeneratorBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataFlowGeneratorBlock.LIT, online), 3);
        }
    }
}
