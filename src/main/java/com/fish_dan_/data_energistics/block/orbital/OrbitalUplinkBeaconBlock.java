package com.fish_dan_.data_energistics.block.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalUplinkBeaconBlockEntity;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLimitException;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

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

import java.util.Optional;

/**
 * Player-placed AE uplink that extends an already provisioned orbital weapon into its dimension.
 */
public final class OrbitalUplinkBeaconBlock extends AEBaseBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public OrbitalUplinkBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbitalUplinkBeaconBlockEntity(pos, state);
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
        if (!(level.getBlockEntity(pos) instanceof OrbitalUplinkBeaconBlockEntity beacon)) {
            Data_Energistics.LOGGER.error("Orbital uplink beacon at {} was placed without its block entity", pos);
            return;
        }
        beacon.setOwner(player);

        OrbitalEndpointLocation location = new OrbitalEndpointLocation(serverLevel.dimension().location(), pos);
        try {
            Optional<OrbitalWeaponRecord> weapon = OrbitalWeaponSavedData.get(serverLevel.getServer())
                    .bindExistingForOwner(
                            serverLevel.getServer(),
                            player.getUUID(),
                            location,
                            OrbitalEndpointKind.UPLINK_BEACON);
            if (weapon.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital_uplink_beacon.weapon_required"),
                        true);
                return;
            }
            beacon.bindTo(weapon.orElseThrow().weaponId());
        } catch (OrbitalEndpointLimitException exception) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_uplink_beacon.endpoint_limit_reached"),
                    true);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to bind orbital uplink beacon at {} for player {}",
                    location,
                    player.getUUID(),
                    exception);
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_uplink_beacon.binding_failed"),
                    true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof OrbitalUplinkBeaconBlockEntity beacon) {
            beacon.releaseBinding(serverLevel);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
