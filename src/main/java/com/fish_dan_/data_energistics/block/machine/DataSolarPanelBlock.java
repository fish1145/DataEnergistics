package com.fish_dan_.data_energistics.block.machine;

import com.fish_dan_.data_energistics.blockentity.machine.DataSolarPanelBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.block.AEBaseBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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

import org.jspecify.annotations.Nullable;

public class DataSolarPanelBlock extends AEBaseBlock implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty CONNECT_NORTH = BooleanProperty.create("connect_north");
    public static final BooleanProperty CONNECT_EAST = BooleanProperty.create("connect_east");
    public static final BooleanProperty CONNECT_SOUTH = BooleanProperty.create("connect_south");
    public static final BooleanProperty CONNECT_WEST = BooleanProperty.create("connect_west");
    public static final BooleanProperty CONNECT_NORTH_EAST = BooleanProperty.create("connect_north_east");
    public static final BooleanProperty CONNECT_SOUTH_EAST = BooleanProperty.create("connect_south_east");
    public static final BooleanProperty CONNECT_SOUTH_WEST = BooleanProperty.create("connect_south_west");
    public static final BooleanProperty CONNECT_NORTH_WEST = BooleanProperty.create("connect_north_west");
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
                .setValue(CONNECT_WEST, false)
                .setValue(CONNECT_NORTH_EAST, false)
                .setValue(CONNECT_SOUTH_EAST, false)
                .setValue(CONNECT_SOUTH_WEST, false)
                .setValue(CONNECT_NORTH_WEST, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataSolarPanelBlockEntity(blockPos, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, FACING, CONNECT_NORTH, CONNECT_EAST, CONNECT_SOUTH, CONNECT_WEST,
                CONNECT_NORTH_EAST, CONNECT_SOUTH_EAST, CONNECT_SOUTH_WEST, CONNECT_NORTH_WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState()
                .setValue(LIT, false)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
        return connectedState(state, context.getLevel(), context.getClickedPos());
    }

    private static BlockState connectedState(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            state = state.setValue(connectionProperty(direction), matchesLoadedPanel(level, adjacent))
                    .setValue(diagonalProperty(direction), matchesLoadedPanel(level, adjacent.relative(direction.getClockWise())));
        }
        return state;
    }

    private static boolean matchesLoadedPanel(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos) && connectsVisually(level.getBlockState(pos));
    }

    /** Both solar variants share an outer frame, without sharing power storage or their AE network nodes. */
    private static boolean connectsVisually(BlockState state) {
        return state.getBlock() instanceof DataSolarPanelBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isVertical()) {
            return state;
        }
        return connectedState(state, level, pos).setValue(connectionProperty(direction), connectsVisually(neighborState));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            refreshDiagonalNeighbors(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(this)) {
            refreshDiagonalNeighbors(level, pos);
        }
    }

    private static void refreshDiagonalNeighbors(Level level, BlockPos pos) {
        // Vanilla shape notifications only reach cardinal neighbors, not the panel across a concave corner.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            refreshPanelAt(level, pos.relative(direction).relative(direction.getClockWise()));
        }
    }

    /**
     * Reconciles loaded panels when an AE2 block entity becomes ready, including old states with missing connection
     * fields and panels on the loaded side of a chunk boundary. Called only on the server; never loads more chunks.
     */
    public static void refreshLoadedConnections(Level level, BlockPos pos) {
        refreshPanelAt(level, pos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            refreshPanelAt(level, pos.relative(direction));
        }
        refreshDiagonalNeighbors(level, pos);
    }

    private static void refreshPanelAt(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (connectsVisually(state)) {
            BlockState connected = connectedState(state, level, pos);
            if (connected != state) {
                // Only visual fields changed; avoid recursively notifying an entire array of panels.
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
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

        BlockState rotated = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction target = rotation.rotate(direction);
            rotated = rotated.setValue(connectionProperty(target), state.getValue(connectionProperty(direction)))
                    .setValue(diagonalProperty(target), state.getValue(diagonalProperty(direction)));
        }
        return rotated;
    }

    private static BlockState mirrorConnections(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }

        BlockState mirrored = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction target = mirror.mirror(direction);
            // A reflection reverses the clockwise order of the two sides defining a diagonal.
            mirrored = mirrored.setValue(connectionProperty(target), state.getValue(connectionProperty(direction)))
                    .setValue(diagonalProperty(target.getCounterClockWise()), state.getValue(diagonalProperty(direction)));
        }
        return mirrored;
    }

    private static BooleanProperty connectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CONNECT_NORTH;
            case EAST -> CONNECT_EAST;
            case SOUTH -> CONNECT_SOUTH;
            case WEST -> CONNECT_WEST;
            default -> throw new IllegalArgumentException("A solar panel connection must be horizontal: " + direction);
        };
    }

    /** The diagonal between a horizontal side and its clockwise neighbor. */
    private static BooleanProperty diagonalProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CONNECT_NORTH_EAST;
            case EAST -> CONNECT_SOUTH_EAST;
            case SOUTH -> CONNECT_SOUTH_WEST;
            case WEST -> CONNECT_NORTH_WEST;
            default -> throw new IllegalArgumentException("A solar panel diagonal must be horizontal: " + direction);
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
