package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.menu.DataSanctumStatusMenu;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.util.BlockMemoryCardInteractionHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseBlock;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class DataSanctumBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, 4);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, 4);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, 3);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    private static final double MODEL_OFFSET_X = 0.0D;
    private static final double MODEL_OFFSET_Z = 0.0D;
    private static final ModelBox[] MODEL_BOXES = {
            modelBox(-1.000000D, 0.000000D, -1.000000D, 2.000000D, 1.000000D, 2.000000D),
            modelBox(-1.000000D, 0.000000D, -2.000000D, 2.000000D, 0.500000D, -1.000000D),
            modelBox(-2.000000D, 0.000000D, -1.000000D, -1.000000D, 0.500000D, 2.000000D),
            modelBox(2.000000D, 0.000000D, -1.000000D, 3.000000D, 0.500000D, 2.000000D),
            modelBox(-1.000000D, 0.000000D, 2.000000D, 2.000000D, 0.500000D, 3.000000D),
            modelBox(-2.000000D, 0.000000D, -2.000000D, -1.000000D, 1.000000D, -1.000000D),
            modelBox(2.000000D, 0.000000D, -2.000000D, 3.000000D, 1.000000D, -1.000000D),
            modelBox(-2.000000D, 0.000000D, 2.000000D, -1.000000D, 1.000000D, 3.000000D),
            modelBox(2.000000D, 0.000000D, 2.000000D, 3.000000D, 1.000000D, 3.000000D),
            modelBox(0.187500D, 0.921875D, 2.187500D, 0.812500D, 1.000000D, 2.812500D),
            modelBox(-1.750000D, 2.062500D, -1.687500D, -1.250000D, 3.125000D, -1.312500D),
            modelBox(2.250000D, 2.062500D, -1.687500D, 2.750000D, 3.125000D, -1.312500D),
            modelBox(-1.750000D, 2.062500D, 2.312500D, -1.250000D, 3.125000D, 2.687500D),
            modelBox(2.250000D, 2.062500D, 2.312500D, 2.750000D, 3.125000D, 2.687500D),
    };
    private static final ModelBox[] COLLISION_FILL_BOXES = {
            modelBox(2.000000D, 0.000000D, -2.000000D, 3.000000D, 3.125000D, -1.000000D),
            modelBox(2.000000D, 0.000000D, 2.000000D, 3.000000D, 3.125000D, 3.000000D),
            modelBox(-2.000000D, 0.000000D, 2.000000D, -1.000000D, 3.125000D, 3.000000D),
            modelBox(-2.000000D, 0.000000D, -2.000000D, -1.000000D, 3.125000D, -1.000000D),
    };
    private static final ModelBox[] PORT_INTERACTION_BOXES = {
            modelBox(0.125000D, 0.875000D, 2.125000D, 0.875000D, 1.062500D, 2.875000D),
    };
    private static final ModelBox[] SCREEN_INTERACTION_BOXES = {
            modelBox(-0.977400D, 1.250000D, -1.885200D, -0.236700D, 2.000000D, -1.482700D),
            modelBox(-0.885900D, 1.375000D, -1.721900D, -0.400000D, 1.875000D, -1.472800D),
            modelBox(-0.250000D, 1.250000D, -1.625000D, 0.500000D, 2.000000D, -1.500000D),
            modelBox(-0.125000D, 1.375000D, -1.500000D, 0.375000D, 1.875000D, -1.437500D),
            modelBox(0.500000D, 1.250000D, -1.625000D, 1.250000D, 2.000000D, -1.500000D),
            modelBox(0.625000D, 1.375000D, -1.500000D, 1.125000D, 1.875000D, -1.437500D),
            modelBox(1.236700D, 1.250000D, -1.885200D, 1.977400D, 2.000000D, -1.482700D),
            modelBox(1.400000D, 1.375000D, -1.721900D, 1.885900D, 1.875000D, -1.472800D),
    };
    private static final EnumMap<Direction, VoxelShape[][][]> MODEL_PART_SHAPES = createPartShapes(false);
    private static final EnumMap<Direction, VoxelShape[][][]> COLLISION_PART_SHAPES = createPartShapes(true);

    public DataSanctumBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(OFFSET_X, 2)
                .setValue(OFFSET_Z, 2)
                .setValue(OFFSET_Y, 0)
                .setValue(ACTIVE, false)
                .setValue(MODE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, OFFSET_X, OFFSET_Z, OFFSET_Y, ACTIVE, MODE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos mainPos = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection().getOpposite();

        for (PlacementPart part : iterPlacementParts(mainPos, facing)) {
            if (!level.isInWorldBounds(part.pos()) || !level.getBlockState(part.pos()).canBeReplaced(context)) {
                return null;
            }
        }

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OFFSET_X, 2)
                .setValue(OFFSET_Z, 2)
                .setValue(OFFSET_Y, 0)
                .setValue(ACTIVE, false)
                .setValue(MODE, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Direction facing = state.getValue(FACING);
        for (PlacementPart part : iterPlacementParts(pos, facing)) {
            if (part.offsetX() == 0 && part.offsetZ() == 0 && part.offsetY() == 0) {
                continue;
            }

            level.setBlock(part.pos(), state
                    .setValue(OFFSET_X, DataSanctumBlockEntity.encodeOffsetX(part.offsetX()))
                    .setValue(OFFSET_Z, DataSanctumBlockEntity.encodeOffsetZ(part.offsetZ()))
                    .setValue(OFFSET_Y, part.offsetY()), Block.UPDATE_ALL);
        }
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
        ItemInteractionResult memoryCardResult = BlockMemoryCardInteractionHelper.useOnBlockEntity(
                stack,
                level,
                DataSanctumBlockEntity.getMainPos(pos, state),
                player);
        if (memoryCardResult.consumesAction()) {
            return memoryCardResult;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos mainPos = DataSanctumBlockEntity.getMainPos(pos, state);
        if (!level.isClientSide() && level.getBlockEntity(mainPos) instanceof DataSanctumBlockEntity sanctum) {
            if (DataSanctumBlockEntity.isScreenPart(state) && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, playerInventory, menuPlayer) -> new DataSanctumStatusMenu(
                                containerId,
                                playerInventory,
                                sanctum),
                        Component.empty()), buffer -> buffer.writeBlockPos(mainPos));
            } else if (DataSanctumBlockEntity.isNetworkPortPart(state)) {
                sanctum.openMenu(player, MenuLocators.forBlockEntity(sanctum));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            removeOtherParts(level, DataSanctumBlockEntity.getMainPos(pos, state), pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !DataSanctumBlockEntity.isMainPart(state)) {
            BlockPos mainPos = DataSanctumBlockEntity.getMainPos(pos, state);
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.is(this) && DataSanctumBlockEntity.isMainPart(mainState)) {
                if (player.getAbilities().instabuild) {
                    level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                } else {
                    level.destroyBlock(mainPos, true, player);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos currentPos, BlockPos neighborPos) {
        if (!DataSanctumBlockEntity.isMainPart(state) && !isMainPartPresent(level, currentPos, state)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (!DataSanctumBlockEntity.isMainPart(state)) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getModelPartShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getCollisionPartShape(state);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getModelPartShape(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return DataSanctumBlockEntity.isMainPart(state) ? new DataSanctumBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || !DataSanctumBlockEntity.isMainPart(state) || blockEntityType != DEBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataSanctumBlockEntity sanctum) {
                sanctum.serverTick();
            }
        };
    }

    public static @Nullable DataSanctumBlockEntity getMainBlockEntity(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos mainPos = DataSanctumBlockEntity.getMainPos(pos, state);
        return level.getBlockEntity(mainPos) instanceof DataSanctumBlockEntity sanctum ? sanctum : null;
    }

    private void removeOtherParts(Level level, BlockPos mainPos, BlockPos removedPos) {
        BlockState mainState = level.getBlockState(mainPos);
        Direction facing = mainState.is(this) ? mainState.getValue(FACING) : Direction.NORTH;
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                for (int offsetY = 0; offsetY <= 3; offsetY++) {
                    BlockPos partPos = DataSanctumBlockEntity.getPartPos(mainPos, facing, offsetX, offsetZ, offsetY);
                    if (partPos.equals(removedPos)) {
                        continue;
                    }

                    BlockState partState = level.getBlockState(partPos);
                    if (partState.is(this) && DataSanctumBlockEntity.getMainPos(partPos, partState).equals(mainPos) && partState.getValue(FACING) == facing) {
                        level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
    }

    private boolean isMainPartPresent(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockPos mainPos = DataSanctumBlockEntity.getMainPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        return mainState.is(this) && DataSanctumBlockEntity.isMainPart(mainState) && mainState.getValue(FACING) == state.getValue(FACING);
    }

    private static VoxelShape getModelPartShape(BlockState state) {
        VoxelShape[][][] shapes = MODEL_PART_SHAPES.get(state.getValue(FACING));
        return shapes[state.getValue(OFFSET_X)][state.getValue(OFFSET_Z)][state.getValue(OFFSET_Y)];
    }

    private static VoxelShape getCollisionPartShape(BlockState state) {
        VoxelShape[][][] shapes = COLLISION_PART_SHAPES.get(state.getValue(FACING));
        return shapes[state.getValue(OFFSET_X)][state.getValue(OFFSET_Z)][state.getValue(OFFSET_Y)];
    }

    private static EnumMap<Direction, VoxelShape[][][]> createPartShapes(boolean includeCollisionFill) {
        EnumMap<Direction, VoxelShape[][][]> shapesByFacing = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[][][] shapes = new VoxelShape[5][5][4];
            for (int offsetX = 0; offsetX < 5; offsetX++) {
                for (int offsetZ = 0; offsetZ < 5; offsetZ++) {
                    for (int offsetY = 0; offsetY < 4; offsetY++) {
                        BlockPos partOffset = DataSanctumBlockEntity.getPartPos(
                                BlockPos.ZERO, facing, offsetX - 2, offsetZ - 2, offsetY);
                        VoxelShape shape = Shapes.empty();
                        for (ModelBox box : MODEL_BOXES) {
                            shape = addBoxToShape(shape, box, facing, partOffset);
                        }
                        if (includeCollisionFill) {
                            for (ModelBox box : COLLISION_FILL_BOXES) {
                                shape = addBoxToShape(shape, box, facing, partOffset);
                            }
                            for (ModelBox box : SCREEN_INTERACTION_BOXES) {
                                shape = addBoxToShape(shape, box, facing, partOffset);
                            }
                        } else {
                            for (ModelBox box : PORT_INTERACTION_BOXES) {
                                shape = addBoxToShape(shape, box, facing, partOffset);
                            }
                            for (ModelBox box : SCREEN_INTERACTION_BOXES) {
                                shape = addBoxToShape(shape, box, facing, partOffset);
                            }
                        }
                        shapes[offsetX][offsetZ][offsetY] = shape;
                    }
                }
            }
            shapesByFacing.put(facing, shapes);
        }
        return shapesByFacing;
    }

    private static VoxelShape addBoxToShape(VoxelShape shape, ModelBox box, Direction facing, BlockPos partOffset) {
        Bounds rotated = rotateModelBoxFromNorth(box, facing);
        double minX = (rotated.minX() - partOffset.getX()) * 16.0D;
        double minY = (rotated.minY() - partOffset.getY()) * 16.0D;
        double minZ = (rotated.minZ() - partOffset.getZ()) * 16.0D;
        double maxX = (rotated.maxX() - partOffset.getX()) * 16.0D;
        double maxY = (rotated.maxY() - partOffset.getY()) * 16.0D;
        double maxZ = (rotated.maxZ() - partOffset.getZ()) * 16.0D;
        double clippedMinX = Math.max(0.0D, minX);
        double clippedMinY = Math.max(0.0D, minY);
        double clippedMinZ = Math.max(0.0D, minZ);
        double clippedMaxX = Math.min(16.0D, maxX);
        double clippedMaxY = Math.min(16.0D, maxY);
        double clippedMaxZ = Math.min(16.0D, maxZ);
        if (clippedMinX >= clippedMaxX || clippedMinY >= clippedMaxY || clippedMinZ >= clippedMaxZ) {
            return shape;
        }

        return Shapes.or(shape, voxel(
                clippedMinX,
                clippedMinY,
                clippedMinZ,
                clippedMaxX,
                clippedMaxY,
                clippedMaxZ));
    }

    private static Iterable<PlacementPart> iterPlacementParts(BlockPos mainPos, Direction facing) {
        List<PlacementPart> parts = new ArrayList<>(64);
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                parts.add(new PlacementPart(
                        DataSanctumBlockEntity.getPartPos(mainPos, facing, offsetX, offsetZ),
                        offsetX,
                        offsetZ,
                        0));
                for (int offsetY = 1; offsetY <= 3; offsetY++) {
                    if (isRequiredUpperPart(offsetX, offsetZ, offsetY)) {
                        parts.add(new PlacementPart(
                                DataSanctumBlockEntity.getPartPos(mainPos, facing, offsetX, offsetZ, offsetY),
                                offsetX,
                                offsetZ,
                                offsetY));
                    }
                }
            }
        }
        return parts;
    }

    private static boolean isRequiredUpperPart(int offsetX, int offsetZ, int offsetY) {
        if (offsetY == 1 && offsetZ == -2 && Math.abs(offsetX) <= 1) {
            return true;
        }
        return offsetY <= 3 && Math.abs(offsetX) == 2 && Math.abs(offsetZ) == 2;
    }

    private static Bounds rotateModelBoxFromNorth(ModelBox box, Direction facing) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double[] xs = { box.minX() + MODEL_OFFSET_X, box.maxX() + MODEL_OFFSET_X };
        double[] zs = { box.minZ() + MODEL_OFFSET_Z, box.maxZ() + MODEL_OFFSET_Z };
        for (double x : xs) {
            for (double z : zs) {
                double[] rotated = rotatePointFromNorth(x, z, facing);
                minX = Math.min(minX, rotated[0]);
                minZ = Math.min(minZ, rotated[1]);
                maxX = Math.max(maxX, rotated[0]);
                maxZ = Math.max(maxZ, rotated[1]);
            }
        }

        return new Bounds(minX, box.minY(), minZ, maxX, box.maxY(), maxZ);
    }

    private static double[] rotatePointFromNorth(double x, double z, Direction facing) {
        double localX = x - 0.5D;
        double localZ = z - 0.5D;
        return switch (facing) {
            case EAST -> new double[] { 0.5D - localZ, 0.5D + localX };
            case SOUTH -> new double[] { 0.5D - localX, 0.5D - localZ };
            case WEST -> new double[] { 0.5D + localZ, 0.5D - localX };
            default -> new double[] { x, z };
        };
    }

    private static ModelBox modelBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new ModelBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record ModelBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private record PlacementPart(BlockPos pos, int offsetX, int offsetZ, int offsetY) {}

    private static VoxelShape voxel(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
