package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import appeng.hooks.WrenchHook;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TrinityDataCoreBlock extends DataRipperReassemblerBlock implements EntityBlock {

    public TrinityDataCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new TrinityDataCoreBlockEntity(blockPos, blockState);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer &&
                level.getBlockEntity(pos) instanceof TrinityDataCoreBlockEntity host) {
            TrinityDataCoreMenu.open(serverPlayer, host);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrinityDataCoreBlockEntity host) {
            host.restoreIdentityFromItem(stack);
        }
        TrinityDataCoreBlockEntity.requestRecheckAt(level, pos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack drop = createHostDrop(blockEntity);
        return drop.isEmpty() ? List.of() : List.of(drop);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.getAbilities().instabuild && !WrenchHook.isDisassembling()) {
            ItemStack drop = createHostDrop(level.getBlockEntity(pos));
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrinityDataCoreBlockEntity host) {
                host.onPermanentRemoval();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        TrinityDataCoreBlockEntity.requestRecheckAt(level, pos);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                            BlockState state,
                                                                            BlockEntityType<T> blockEntityType) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        if (blockEntityType != DEBlockEntities.TRINITY_DATA_CORE_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickerLevel, tickerPos, tickerState, tickerBlockEntity) -> ((TrinityDataCoreBlockEntity) tickerBlockEntity).serverTick();
    }

    private ItemStack createHostDrop(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof TrinityDataCoreBlockEntity host)) {
            Data_Energistics.LOGGER.error("Cannot create a stateful Trinity Data Core drop without its block entity");
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this);
        host.saveIdentityToItem(stack);
        return stack;
    }
}
