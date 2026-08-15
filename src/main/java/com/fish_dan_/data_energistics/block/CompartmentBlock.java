package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.storage.CompartmentBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.CompositeWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeInputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MeCompositeOutputWarehouseBlockEntity;
import com.fish_dan_.data_energistics.blockentity.storage.MePatternBufferBlockEntity;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityInformationExchangeDepotBlockEntity;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredHolder;

import appeng.block.AEBaseBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jspecify.annotations.Nullable;

/**
 * Block shell for a compartment part that only works when bound to a valid multiblock.
 */
public class CompartmentBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    private final CompartmentType compartmentType;

    public CompartmentBlock(CompartmentType compartmentType, BlockBehaviour.Properties properties) {
        super(properties);
        this.compartmentType = compartmentType;
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    public CompartmentType compartmentType() {
        return this.compartmentType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ACTIVE);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (this.compartmentType) {
            case INPUT, OUTPUT -> new CompositeWarehouseBlockEntity(pos, state);
            case ME_INPUT -> new MeCompositeInputWarehouseBlockEntity(pos, state);
            case ME_OUTPUT -> new MeCompositeOutputWarehouseBlockEntity(pos, state);
            case PATTERN_BUFFER -> new MePatternBufferBlockEntity(pos, state);
            case TRINITY_INFORMATION_EXCHANGE -> new TrinityInformationExchangeDepotBlockEntity(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || !DEBlockEntities.isCompartmentBlockEntityType(blockEntityType)) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof CompartmentBlockEntity compartment) {
                compartment.serverTick();
            } else if (blockEntity instanceof TrinityInformationExchangeDepotBlockEntity hatch) {
                hatch.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TrinityInformationExchangeDepotBlockEntity hatch) {
                MenuOpener.open(
                        DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT.get(),
                        player,
                        MenuLocators.forBlockEntity(hatch));
            } else if (blockEntity instanceof CompartmentBlockEntity compartment) {
                MenuOpener.open(
                        menuTypeFor(compartment.compartmentType()).get(),
                        player,
                        MenuLocators.forBlockEntity(compartment));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static DeferredHolder<MenuType<?>, ? extends MenuType<?>> menuTypeFor(CompartmentType type) {
        return switch (type) {
            case INPUT, OUTPUT -> DEMenus.COMPOSITE_WAREHOUSE;
            case ME_INPUT -> DEMenus.ME_COMPOSITE_INPUT_WAREHOUSE;
            case ME_OUTPUT -> DEMenus.ME_COMPOSITE_OUTPUT_WAREHOUSE;
            case PATTERN_BUFFER -> DEMenus.ME_PATTERN_BUFFER;
            case TRINITY_INFORMATION_EXCHANGE -> DEMenus.TRINITY_INFORMATION_EXCHANGE_DEPOT;
        };
    }
}
