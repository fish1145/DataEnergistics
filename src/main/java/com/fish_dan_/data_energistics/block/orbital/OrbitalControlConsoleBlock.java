package com.fish_dan_.data_energistics.block.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLimitException;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.provisioning.ConsoleWeaponProvisioner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import appeng.block.AEBaseBlock;
import org.jspecify.annotations.Nullable;

/**
 * Player-placed control endpoint that provisions and binds the placing player's orbital weapon.
 */
public final class OrbitalControlConsoleBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public OrbitalControlConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbitalControlConsoleBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public void setPlacedBy(
                            Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof OrbitalControlConsoleBlockEntity console)) {
            Data_Energistics.LOGGER.error("Orbital control console at {} was placed without its block entity", pos);
            return;
        }

        OrbitalEndpointLocation location = new OrbitalEndpointLocation(serverLevel.dimension().location(), pos);
        try {
            OrbitalWeaponRecord weapon = ConsoleWeaponProvisioner.INSTANCE.provision(
                    serverLevel.getServer(),
                    player.getUUID(),
                    location);
            console.bindTo(weapon.weaponId());
        } catch (OrbitalEndpointLimitException exception) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_console.endpoint_limit_reached"),
                    true);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to bind orbital control console at {} for player {}",
                    location,
                    player.getUUID(),
                    exception);
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_console.binding_failed"),
                    true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof OrbitalControlConsoleBlockEntity console) {
            console.releaseBinding(serverLevel);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
