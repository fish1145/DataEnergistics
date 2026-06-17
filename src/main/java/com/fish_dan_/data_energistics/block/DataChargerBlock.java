package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DataChargerBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.block.AEBaseBlock;
import appeng.hooks.WrenchHook;
import org.jetbrains.annotations.Nullable;

public class DataChargerBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty SPIN = IOrientationStrategy.SPIN;
    private static final double TWO_PIXELS = 2.0D / 16.0D;
    private static final VoxelShape FULL_COLLISION_SHAPE = Shapes.block();

    public DataChargerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SPIN, 0));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction up = Direction.UP;
        Direction front = context.getHorizontalDirection().getOpposite();
        Player player = context.getPlayer();
        if (player != null) {
            if (player.getXRot() > 65.0F) {
                up = front.getOpposite();
                front = Direction.UP;
            } else if (player.getXRot() < -65.0F) {
                up = front.getOpposite();
                front = Direction.DOWN;
            }
        }

        BlockOrientation orientation = BlockOrientation.get(front, up);
        return this.defaultBlockState()
                .setValue(FACING, front)
                .setValue(SPIN, orientation.getSpin());
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
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 2;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isExtended(state) ? getExtendedShape(state) : getRegularShape(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return isExtended(state) ? getExtendedShape(state) : getRegularShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return FULL_COLLISION_SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!heldItem.isEmpty() && level.getBlockEntity(pos) instanceof DataChargerBlockEntity charger) {
            if (charger.canAcceptStack(heldItem)) {
                if (!level.isClientSide() && charger.tryInsertDisplayStack(heldItem)) {
                    player.setItemInHand(hand, heldItem);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof DataChargerBlockEntity charger) {
            if (!level.isClientSide()) {
                ItemStack extracted = charger.extractFirstDisplayStack();
                if (!extracted.isEmpty() && !player.getInventory().add(extracted)) {
                    Block.popResource(level, pos.relative(state.getValue(FACING)), extracted);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && !WrenchHook.isDisassembling() && level.getBlockEntity(pos) instanceof DataChargerBlockEntity charger) {
            charger.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataChargerBlockEntity charger) {
                charger.serverTick();
            }
        };
    }

    public static boolean isExtended(BlockState state) {
        return state.is(ModBlocks.EXTENDED_DATA_CHARGER.get());
    }

    private static VoxelShape getRegularShape(BlockState state) {
        BlockOrientation orientation = BlockOrientation.get(state);
        Direction up = orientation.getSide(RelativeSide.TOP);
        Direction front = orientation.getSide(RelativeSide.FRONT);

        double minX = TWO_PIXELS;
        double minY = TWO_PIXELS;
        double minZ = TWO_PIXELS;
        double maxX = 1.0D - TWO_PIXELS;
        double maxY = 1.0D - TWO_PIXELS;
        double maxZ = 1.0D - TWO_PIXELS;

        if (up.getStepX() != 0) {
            minX = 0.0D;
            maxX = 1.0D;
        }
        if (up.getStepY() != 0) {
            minY = 0.0D;
            maxY = 1.0D;
        }
        if (up.getStepZ() != 0) {
            minZ = 0.0D;
            maxZ = 1.0D;
        }

        switch (front) {
            case DOWN -> maxY = 1.0D;
            case UP -> minY = 0.0D;
            case NORTH -> maxZ = 1.0D;
            case SOUTH -> minZ = 0.0D;
            case EAST -> minX = 0.0D;
            case WEST -> maxX = 1.0D;
        }

        return Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static VoxelShape getExtendedShape(BlockState state) {
        Direction front = state.getValue(FACING);
        double minX = 0.0D;
        double minY = 0.0D;
        double minZ = 0.0D;
        double maxX = 1.0D;
        double maxY = 1.0D;
        double maxZ = 1.0D;

        switch (front.getAxis()) {
            case X -> {
                minX = TWO_PIXELS;
                maxX = 1.0D - TWO_PIXELS;
            }
            case Y -> {
                minY = TWO_PIXELS;
                maxY = 1.0D - TWO_PIXELS;
            }
            case Z -> {
                minZ = TWO_PIXELS;
                maxZ = 1.0D - TWO_PIXELS;
            }
        }

        return Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
