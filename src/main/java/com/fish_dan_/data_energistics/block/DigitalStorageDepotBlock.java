package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.util.BlockMemoryCardInteractionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DigitalStorageDepotBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D),
            Block.box(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 1.0D, 0.0D, 16.0D, 15.0D, 1.0D),
            Block.box(0.0D, 1.0D, 15.0D, 16.0D, 15.0D, 16.0D),
            Block.box(15.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D),
            Block.box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D));

    public DigitalStorageDepotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DigitalStorageDepotBlockEntity(blockPos, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult memoryCardResult = BlockMemoryCardInteractionHelper.useOnBlockEntity(stack, level, pos, player);
        if (memoryCardResult.consumesAction()) {
            return memoryCardResult;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DigitalStorageDepotBlockEntity depot) {
            if (player.isShiftKeyDown()) {
                boolean exported = depot.exportContentsToNetwork(player);
                player.displayClientMessage(Component.literal(exported ? "已导入 ME 网络" : "没有可导入内容或 ME 网络未接收"), true);
                return InteractionResult.SUCCESS;
            }
            MenuOpener.open(DEMenus.DIGITAL_STORAGE_DEPOT.get(), player, MenuLocators.forBlockEntity(depot));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof DigitalStorageDepotBlockEntity depot) {
            CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            depot.loadTag(blockEntityTag, level.registryAccess());
            DigitalStorageDepotBlockItem.applyStoredFluidsToBlockEntity(stack, depot, level.registryAccess());
            depot.setChanged();
            depot.markForClientUpdate();
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return adjacentBlockState.is(this) || super.skipRendering(state, adjacentBlockState, side);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.getAbilities().instabuild && level.getBlockEntity(pos) instanceof DigitalStorageDepotBlockEntity depot) {
            depot.clearContent();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return List.of(createDepotItemDrop(blockEntity));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != DEBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DigitalStorageDepotBlockEntity depot) {
                depot.serverTick();
            }
        };
    }

    private ItemStack createDepotItemDrop(@Nullable BlockEntity blockEntity) {
        ItemStack stack = new ItemStack(this);
        if (blockEntity instanceof DigitalStorageDepotBlockEntity depot && depot.getLevel() != null) {
            depot.saveToItem(stack, depot.getLevel().registryAccess());
        }
        return stack;
    }
}
