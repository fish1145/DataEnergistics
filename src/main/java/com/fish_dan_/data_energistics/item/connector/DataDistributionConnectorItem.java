package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class DataDistributionConnectorItem extends Item {

    private static final String KEY_PREFIX = "item.data_energistics.data_distribution_connector";

    public DataDistributionConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        ItemStack stack = context.getItemInHand();

        if (clickedState.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get()) && player.isShiftKeyDown()) {
            return bindTower(stack, player, level, clickedPos, clickedState);
        }

        if (!getConnectorData(stack).hasSelection()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return connectTarget(stack, player, level, clickedPos, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        DataDistributionConnectorItemData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            return;
        }

        BlockPos pos = data.getTowerPos();
        tooltipComponents.add(Component.translatable(
                KEY_PREFIX + ".tooltip.bound",
                data.dimensionId(),
                pos.getX(),
                pos.getY(),
                pos.getZ()));
    }

    /**
     * Binds the supplied connector stack to the clicked distribution tower for both held and equipped workflows.
     * Client calls consume the interaction immediately, while the server validates point mode and persists the
     * selected tower into the original mutable stack.
     *
     * @param stack        original connector stack that receives the tower selection component
     * @param player       player selecting the tower and receiving success or failure feedback
     * @param level        level containing the clicked tower
     * @param clickedPos   position of any clicked tower part
     * @param clickedState state of the clicked tower part used to resolve its base position
     * @return {@link InteractionResult#SUCCESS} when the selection is accepted, {@link InteractionResult#FAIL} for a
     *         tower outside point-to-point mode, or {@link InteractionResult#PASS} when the base block entity is
     *         unavailable
     */
    public InteractionResult bindTower(ItemStack stack, Player player, Level level, BlockPos clickedPos,
                                       BlockState clickedState) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos basePos = DataDistributionTowerBlock.getBasePos(clickedPos, clickedState);
        BlockEntity blockEntity = level.getBlockEntity(basePos);
        if (!(blockEntity instanceof DataDistributionTowerBlockEntity tower)) {
            return InteractionResult.PASS;
        }
        if (!tower.isPointToPointMode()) {
            player.displayClientMessage(Component.translatable(KEY_PREFIX + ".point_mode_only"), true);
            return InteractionResult.FAIL;
        }

        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(),
                getConnectorData(stack).withTower(level.dimension().location().toString(), basePos));
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + ".bound",
                basePos.getX(),
                basePos.getY(),
                basePos.getZ()), true);
        return InteractionResult.SUCCESS;
    }

    public void autoConnectPlacedBlock(ItemStack stack, Player player, Level level, BlockPos placedPos) {
        if (level.isClientSide() || !getConnectorData(stack).hasSelection()) {
            return;
        }
        connectTarget(stack, player, level, placedPos, false);
    }

    private InteractionResult connectTarget(ItemStack stack, Player player, Level level, BlockPos clickedPos,
                                            boolean showFailureMessages) {
        DataDistributionConnectorItemData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".unbound"), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.dimension().location().toString().equals(data.dimensionId())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(data.dimensionId()));
        if (!level.dimension().equals(dimensionKey)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        BlockPos towerPos = data.getTowerPos();
        if (!level.isLoaded(towerPos)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }

        BlockEntity blockEntity = level.getBlockEntity(towerPos);
        if (!(blockEntity instanceof DataDistributionTowerBlockEntity tower)) {
            stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.clear());
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".tower_missing"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!tower.isPointToPointMode()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".point_mode_only"), true);
            }
            return InteractionResult.FAIL;
        }

        DataDistributionTowerBlockEntity.ConnectorBindResult result = tower.bindTargetFromConnector(clickedPos);
        if (!result.success()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(switch (result.failure()) {
                    case NOT_POINT_MODE -> KEY_PREFIX + ".point_mode_only";
                    case OUT_OF_RANGE -> KEY_PREFIX + ".target_out_of_range";
                    case SELF_TARGET -> KEY_PREFIX + ".target_self";
                    case UNSUPPORTED -> KEY_PREFIX + ".target_invalid";
                }), true);
            }
            return InteractionResult.FAIL;
        }

        String suffix = result.aeSupported() && result.feSupported() ? ".connected.af" : result.aeSupported() ? ".connected.ae" : ".connected.fe";
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + suffix,
                clickedPos.getX(),
                clickedPos.getY(),
                clickedPos.getZ()), true);
        return InteractionResult.SUCCESS;
    }

    private static DataDistributionConnectorItemData getConnectorData(ItemStack stack) {
        DataDistributionConnectorItemData data = stack.get(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get());
        return data != null ? data : DataDistributionConnectorItemData.EMPTY;
    }
}
