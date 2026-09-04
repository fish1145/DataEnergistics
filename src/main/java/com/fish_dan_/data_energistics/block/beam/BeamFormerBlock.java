package com.fish_dan_.data_energistics.block.beam;

import com.fish_dan_.data_energistics.blockentity.beam.BeamFormerBlockEntity;
import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.item.beam.BeamBindingToolItem;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;

/** Directional housing shared by the straight and manually bound omni devices. */
public final class BeamFormerBlock extends AEBaseEntityBlock<BeamFormerBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    private final BeamDeviceKind kind;

    public BeamFormerBlock(BlockBehaviour.Properties properties, BeamDeviceKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(STATUS, Status.OFF));
    }

    public BeamDeviceKind kind() {
        return this.kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BeamBindingToolItem ||
                (stack.isEmpty() && player.getOffhandItem().getItem() instanceof BeamBindingToolItem)) {
            // The item must handle selecting/linking before the default block action opens the upgrade menu.
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BeamFormerBlockEntity beam) {
            if (player.isShiftKeyDown()) {
                beam.beamState().toggleHidden();
            } else {
                MenuOpener.open(DEMenus.BEAM_FORMER.get(), player, MenuLocators.forBlockEntity(beam));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BeamFormerBlockEntity beam) {
            beam.beamState().requestCheck();
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BeamFormerShapes.forFacing(this.kind, state.getValue(FACING));
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, BeamFormerBlockEntity entity) {
        Status status = !entity.getMainNode().isPowered() ? Status.OFF :
                entity.beamState().connectionCount() > 0 ? Status.BEAMING : Status.ON;
        return state.setValue(STATUS, status);
    }

    public enum Status implements StringRepresentable {

        OFF("off"),
        ON("on"),
        BEAMING("beaming");

        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
