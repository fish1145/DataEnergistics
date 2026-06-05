package com.fish_dan_.data_energistics.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;

public class DollBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(3.3428D, 0.0D, 4.40219D, 12.6572D, 13.5D, 14.7D);
    private static final EnumMap<Direction, VoxelShape> SHAPES = createHorizontalFacingShapes(SHAPE);

    public DollBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    private static VoxelShape getShapeForFacing(Direction direction) {
        return SHAPES.get(direction);
    }

    private static EnumMap<Direction, VoxelShape> createHorizontalFacingShapes(VoxelShape shape) {
        EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            shapes.put(direction, rotateFromNorth(shape, direction));
        }
        return shapes;
    }

    private static VoxelShape rotateFromNorth(VoxelShape shape, Direction direction) {
        if (direction == Direction.NORTH) {
            return shape;
        }

        VoxelShape[] rotatedShape = { Shapes.empty() };
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> rotatedShape[0] = Shapes.or(rotatedShape[0], switch (direction) {
            case EAST -> Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX);
            case SOUTH -> Shapes.box(1.0D - maxX, minY, 1.0D - maxZ, 1.0D - minX, maxY, 1.0D - minZ);
            case WEST -> Shapes.box(minZ, minY, 1.0D - maxX, maxZ, maxY, 1.0D - minX);
            default -> throw new IllegalArgumentException(direction.toString());
        }));
        return rotatedShape[0];
    }
}
