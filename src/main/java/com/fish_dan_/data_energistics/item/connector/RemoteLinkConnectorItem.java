package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.connector.EnergyTransferDirection;
import com.fish_dan_.data_energistics.block.tower.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumInterfaceBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.blockentity.networking.CableBusBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class RemoteLinkConnectorItem extends Item {

    private static final String KEY_PREFIX = "item.data_energistics.data_distribution_connector";

    public RemoteLinkConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RemoteLinkConnectorData data = getConnectorData(stack);
        if (player.isShiftKeyDown() && data.hasSelection()) {
            if (level.isClientSide()) {
                return InteractionResultHolder.success(stack);
            }
            stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.clear());
            player.displayClientMessage(Component.translatable(KEY_PREFIX + ".unbound_current"), true);
            return InteractionResultHolder.success(stack);
        }
        if (data.targetType() == ConnectorHostType.TOWER && !player.isShiftKeyDown()) {
            DataDistributionTowerBlockEntity tower = resolveSelectedTower(level, data);
            if (tower == null || !tower.connectionMode().allowsFeTargets()) {
                return InteractionResultHolder.pass(stack);
            }
            EnergyTransferDirection next = data.energyDirection().opposite();
            stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(), data.withEnergyDirection(next));
            player.displayClientMessage(Component.translatable(KEY_PREFIX + ".energy_direction." + next.name().toLowerCase(Locale.ROOT)), true);
            return InteractionResultHolder.success(stack);
        }
        ConnectorEndpoint endpoint = resolveEndpoint(level, data);
        if (endpoint == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ConnectorMode next = switch (endpoint.mode()) {
            case INPUT -> ConnectorMode.PULL;
            case PULL -> ConnectorMode.BOTH;
            case BOTH -> ConnectorMode.INPUT;
        };
        endpoint.setMode(next);
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + ".mode_changed",
                Component.translatable(KEY_PREFIX + ".mode." + next.name().toLowerCase(Locale.ROOT))), true);
        return InteractionResultHolder.success(stack);
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

        if (player.isShiftKeyDown() && level.getBlockEntity(clickedPos) instanceof CableBusBlockEntity bus && (bus.getPart(context.getClickedFace().getOpposite()) instanceof AdaptivePatternProviderPart || bus.getPart(context.getClickedFace().getOpposite()) instanceof DataSanctumLargeInterfaceHost || bus.getPart(context.getClickedFace()) instanceof AdaptivePatternProviderPart || bus.getPart(context.getClickedFace()) instanceof DataSanctumLargeInterfaceHost)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".block_only"), true);
            }
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown() && level.getBlockEntity(clickedPos) instanceof DataSanctumInterfaceBlockEntity) {
            if (!level.isClientSide()) {
                stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(),
                        getConnectorData(stack).withInterface(level.dimension().location().toString(), clickedPos, -1));
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".bound_interface"), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown() && level.getBlockEntity(clickedPos) instanceof AdaptivePatternProviderBlockEntity) {
            return bindAdaptiveProvider(stack, player, level, clickedPos);
        }
        RemoteLinkConnectorData selection = getConnectorData(stack);
        if (player.isShiftKeyDown() && selection.targetType() == ConnectorHostType.TOWER && selection.hasSelection() && !clickedState.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get())) {
            DataDistributionTowerBlockEntity tower = resolveSelectedTower(level, selection);
            if (tower != null && tower.removeTargetFromConnector(clickedPos)) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".unbound_target"), true);
                return InteractionResult.SUCCESS;
            }
        }
        if (clickedState.is(DEBlocks.DATA_DISTRIBUTION_TOWER.get()) && player.isShiftKeyDown()) {
            return bindTower(stack, player, level, clickedPos, clickedState);
        }

        if (!getConnectorData(stack).hasSelection()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return connectTarget(stack, player, level, clickedPos, context.getClickedFace(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        RemoteLinkConnectorData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            return;
        }

        if (data.isInterface()) {
            tooltipComponents.add(Component.translatable(KEY_PREFIX + ".tooltip.bound_interface",
                    data.providerDimensionId(), data.getProviderPos().getX(), data.getProviderPos().getY(), data.getProviderPos().getZ()));
            tooltipComponents.add(Component.translatable(KEY_PREFIX + ".slot_selected", data.selectedSlot() + 1));
            return;
        }
        if (data.isAdaptiveProvider()) {
            tooltipComponents.add(Component.translatable(
                    KEY_PREFIX + ".tooltip.bound_provider",
                    data.providerDimensionId(),
                    data.getProviderPos().getX(),
                    data.getProviderPos().getY(),
                    data.getProviderPos().getZ()));
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
        connectTarget(stack, player, level, placedPos, Direction.UP, false);
    }

    private InteractionResult connectTarget(ItemStack stack, Player player, Level level, BlockPos clickedPos,
                                            Direction clickedFace, boolean showFailureMessages) {
        RemoteLinkConnectorData data = getConnectorData(stack);
        if (!data.hasSelection()) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".unbound"), true);
            }
            return InteractionResult.FAIL;
        }

        if (data.isInterface()) {
            return connectInterface(stack, player, level, clickedPos, clickedFace, showFailureMessages);
        }
        if (data.isAdaptiveProvider()) {
            return connectAdaptiveProvider(stack, player, level, clickedPos, clickedFace, showFailureMessages);
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

        DataDistributionTowerBlockEntity.ConnectorBindResult result = tower.bindTargetFromConnector(
                clickedPos, data.energyDirection());
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

    private InteractionResult bindAdaptiveProvider(ItemStack stack, Player player, Level level,
                                                   BlockPos position) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get(),
                getConnectorData(stack).withAdaptiveProvider(
                        level.dimension().location().toString(), position, -1));
        player.displayClientMessage(Component.translatable(KEY_PREFIX + ".bound_provider"), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult connectAdaptiveProvider(ItemStack stack, Player player, Level level,
                                                      BlockPos clickedPos, Direction clickedFace,
                                                      boolean showFailureMessages) {
        RemoteLinkConnectorData data = getConnectorData(stack);
        if (data.providerSide() != -1) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".block_only"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.dimension().location().toString().equals(data.providerDimensionId()) || !level.isLoaded(data.getProviderPos())) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".provider_missing"), true);
            }
            return InteractionResult.FAIL;
        }
        BlockEntity blockEntity = level.getBlockEntity(data.getProviderPos());
        if (!(blockEntity instanceof AdaptivePatternProviderBlockEntity provider) || !AdaptivePatternProviderResolver.isSupportedProviderStack(provider.getProviderStack()) || !(provider.getLogic() instanceof AdaptivePatternProviderLogic logic)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".provider_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        // The persisted side is the actual capability face on the target block. The clicked face is
        // the face whose capability was selected; inverting it queries the far side and breaks remote links.
        if (!hasTargetCapability(level, clickedPos, clickedFace)) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".target_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        Direction targetSide = clickedFace;
        boolean wasBound = logic.hasConnectorTarget(clickedPos, targetSide);
        boolean changed = wasBound ? logic.unbindConnectorTarget(clickedPos, targetSide) : logic.bindConnectorTarget(clickedPos, targetSide);
        if (!changed) {
            if (showFailureMessages) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".target_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable(
                KEY_PREFIX + (wasBound ? ".unbound_target" : ".bound_target"),
                clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ(),
                Component.translatable(KEY_PREFIX + ".face." + targetSide.getName())), true);
        return InteractionResult.SUCCESS;
    }

    private static boolean hasTargetCapability(Level level, BlockPos position, Direction side) {
        BlockState state = level.getBlockState(position);
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (level.getCapability(AECapabilities.ME_STORAGE, position, state, blockEntity, side) != null) {
            return true;
        }
        GenericInternalInventory generic = level.getCapability(
                AECapabilities.GENERIC_INTERNAL_INV, position, state, blockEntity, side);
        if (generic != null) {
            return true;
        }
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, position, state, blockEntity, side);
        if (items != null) {
            return true;
        }
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, position, state, blockEntity, side);
        return fluids != null;
    }

    @Nullable
    private static DataDistributionTowerBlockEntity resolveSelectedTower(Level level, RemoteLinkConnectorData data) {
        if (!level.dimension().location().toString().equals(data.dimensionId()) || !level.isLoaded(data.getTowerPos())) {
            return null;
        }
        return level.getBlockEntity(data.getTowerPos()) instanceof DataDistributionTowerBlockEntity tower ? tower : null;
    }

    private InteractionResult connectInterface(ItemStack stack, Player player, Level level, BlockPos target,
                                               Direction side, boolean feedback) {
        var data = getConnectorData(stack);
        var endpoint = resolveEndpoint(level, data);
        if (endpoint == null || target.equals(data.getProviderPos()) || !level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(target.getX()), SectionPos.blockToSectionCoord(target.getZ())) || !hasTargetCapability(level, target, side)) {
            if (feedback) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".target_invalid"), true);
            }
            return InteractionResult.FAIL;
        }
        if (data.selectedSlot() >= endpoint.slotCount()) {
            if (feedback) {
                player.displayClientMessage(Component.translatable(KEY_PREFIX + ".slot_locked", data.selectedSlot() + 1), true);
            }
            return InteractionResult.FAIL;
        }
        boolean added = endpoint.toggle(target, side, data.selectedSlot());
        if (feedback) {
            player.displayClientMessage(Component.translatable(KEY_PREFIX + (added ? ".bound_interface_target" : ".unbound_interface_target"),
                    data.selectedSlot() + 1, target.toShortString(), Component.translatable(KEY_PREFIX + ".face." + side.getName())), true);
        }
        return InteractionResult.SUCCESS;
    }

    public static @Nullable ConnectorEndpoint resolveEndpoint(Level level, RemoteLinkConnectorData data) {
        if (data.targetType() == ConnectorHostType.TOWER) {
            return resolveSelectedTower(level, data) == null ? null : new TowerConnectorEndpoint(resolveSelectedTower(level, data), data);
        }
        if (!data.hasSelection() || data.providerSide() != -1 || !level.dimension().location().toString().equals(data.providerDimensionId()) ||
                !level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(data.getProviderPos().getX()), SectionPos.blockToSectionCoord(data.getProviderPos().getZ()))) {
            return null;
        }
        if (data.isInterface()) {
            return level.getBlockEntity(data.getProviderPos()) instanceof DataSanctumInterfaceBlockEntity host ? host.getRemoteLinks() : null;
        }
        var logic = resolveProviderLogic(level, data);
        return logic == null ? null : new AdaptiveProviderConnectorEndpoint(logic);
    }

    public static boolean isConnectorStack(ItemStack stack) {
        return stack.getItem() instanceof RemoteLinkConnectorItem;
    }

    public static RemoteLinkConnectorData readData(ItemStack stack) {
        RemoteLinkConnectorData data = stack.get(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR.get());
        return data != null ? data : RemoteLinkConnectorData.EMPTY;
    }

    public static @Nullable AdaptivePatternProviderLogic resolveProviderLogic(
                                                                              Level level, RemoteLinkConnectorData data) {
        if (!data.isAdaptiveProvider() || data.providerSide() != -1 || !data.hasSelection() || !level.dimension().location().toString().equals(data.providerDimensionId()) || !level.isLoaded(data.getProviderPos())) {
            return null;
        }
        return level.getBlockEntity(data.getProviderPos()) instanceof AdaptivePatternProviderBlockEntity provider && provider.getLogic() instanceof AdaptivePatternProviderLogic logic ? logic : null;
    }

    private static RemoteLinkConnectorData getConnectorData(ItemStack stack) {
        return readData(stack);
    }
}
