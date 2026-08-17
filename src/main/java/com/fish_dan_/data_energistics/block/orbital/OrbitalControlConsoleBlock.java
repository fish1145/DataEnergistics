package com.fish_dan_.data_energistics.block.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlUiFactory;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLimitException;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.provisioning.ConsoleWeaponProvisioner;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;

import appeng.block.AEBaseBlock;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.jspecify.annotations.Nullable;

/**
 * Player-placed control endpoint that provisions and binds the placing player's orbital weapon.
 */
public final class OrbitalControlConsoleBlock extends AEBaseBlock implements EntityBlock, BlockUIMenuType.BlockUI {

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
    protected InteractionResult useWithoutItem(
                                               BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !canView(level, pos, serverPlayer)) {
            return InteractionResult.FAIL;
        }
        return BlockUIMenuType.openUI(serverPlayer, pos) ? InteractionResult.sidedSuccess(false) : InteractionResult.FAIL;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return OrbitalControlUiFactory.create(holder.player, () -> snapshot(holder).toComponent());
    }

    @Override
    public boolean stillValid(BlockUIMenuType.BlockUIHolder holder) {
        if (!BlockUIMenuType.BlockUI.super.stillValid(holder)) {
            return false;
        }
        if (!(holder.player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (serverPlayer.distanceToSqr(
                holder.pos.getX() + 0.5D,
                holder.pos.getY() + 0.5D,
                holder.pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        return canView(holder.player.level(), holder.pos, serverPlayer);
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
        console.setOwner(player);

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

    private static boolean canView(Level level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof OrbitalControlConsoleBlockEntity console)) {
            return false;
        }
        var weaponId = console.getWeaponId();
        var server = player.getServer();
        if (weaponId.isEmpty() || server == null) {
            return false;
        }
        return OrbitalWeaponSavedData.get(server)
                .find(weaponId.orElseThrow())
                .map(weapon -> weapon.canPerform(player.getUUID(), OrbitalWeaponAction.VIEW_STATUS))
                .orElse(false);
    }

    private static OrbitalControlTerminalSnapshot snapshot(BlockUIMenuType.BlockUIHolder holder) {
        ServerPlayer player = (ServerPlayer) holder.player;
        if (player.getServer() == null) {
            throw new IllegalStateException("An orbital control console requires an attached server player");
        }
        return OrbitalControlTerminalSnapshot.capture(player.getServer(), player.getUUID());
    }
}
