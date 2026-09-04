package com.fish_dan_.data_energistics.block.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jspecify.annotations.Nullable;

public class DataSolarPanelBlock extends AEBaseBlock implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty CONNECT_NORTH = BooleanProperty.create("connect_north");
    public static final BooleanProperty CONNECT_EAST = BooleanProperty.create("connect_east");
    public static final BooleanProperty CONNECT_SOUTH = BooleanProperty.create("connect_south");
    public static final BooleanProperty CONNECT_WEST = BooleanProperty.create("connect_west");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 5.0D, 16.0D);

    public DataSolarPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECT_NORTH, false)
                .setValue(CONNECT_EAST, false)
                .setValue(CONNECT_SOUTH, false)
                .setValue(CONNECT_WEST, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataSolarPanelBlockEntity(blockPos, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, FACING, CONNECT_NORTH, CONNECT_EAST, CONNECT_SOUTH, CONNECT_WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState()
                .setValue(LIT, false)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(connectionProperty(direction),
                    context.getLevel().getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty property = connectionProperty(direction);
        return property == null ? state : state.setValue(property, neighborState.is(this));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return rotateConnections(state.setValue(FACING, rotation.rotate(state.getValue(FACING))), rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirrorConnections(state.setValue(FACING, mirror.mirror(state.getValue(FACING))), mirror);
    }

    private static BlockState rotateConnections(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return state;
        }

        boolean north = state.getValue(CONNECT_NORTH);
        boolean east = state.getValue(CONNECT_EAST);
        boolean south = state.getValue(CONNECT_SOUTH);
        boolean west = state.getValue(CONNECT_WEST);
        return state
                .setValue(connectionProperty(rotation.rotate(Direction.NORTH)), north)
                .setValue(connectionProperty(rotation.rotate(Direction.EAST)), east)
                .setValue(connectionProperty(rotation.rotate(Direction.SOUTH)), south)
                .setValue(connectionProperty(rotation.rotate(Direction.WEST)), west);
    }

    private static BlockState mirrorConnections(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }

        boolean north = state.getValue(CONNECT_NORTH);
        boolean east = state.getValue(CONNECT_EAST);
        boolean south = state.getValue(CONNECT_SOUTH);
        boolean west = state.getValue(CONNECT_WEST);
        return state
                .setValue(connectionProperty(mirror.mirror(Direction.NORTH)), north)
                .setValue(connectionProperty(mirror.mirror(Direction.EAST)), east)
                .setValue(connectionProperty(mirror.mirror(Direction.SOUTH)), south)
                .setValue(connectionProperty(mirror.mirror(Direction.WEST)), west);
    }

    private static BooleanProperty connectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CONNECT_NORTH;
            case EAST -> CONNECT_EAST;
            case SOUTH -> CONNECT_SOUTH;
            case WEST -> CONNECT_WEST;
            default -> null;
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataSolarPanelBlockEntity solarPanel) {
            MenuOpener.open(DEMenus.DATA_SOLAR_PANEL.get(), player, MenuLocators.forBlockEntity(solarPanel));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != DEBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataSolarPanelBlockEntity solarPanel) {
                solarPanel.serverTick();
            }
        };
    }
}
