package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.item.PoweredCuttingKnifeItem;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.util.CuttingKnifeTeleportData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.config.Actionable;
import appeng.api.util.AEColor;
import appeng.block.AEBaseBlock;
import appeng.items.tools.powered.ColorApplicatorItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

public class DataTeleportAnchorBlock extends AEBaseBlock implements EntityBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ColorVariant> COLOR = EnumProperty.create("color", ColorVariant.class);

    public DataTeleportAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(LIT, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(COLOR, ColorVariant.DEFAULT));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new DataTeleportAnchorBlockEntity(blockPos, blockState);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, FACING, COLOR);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(LIT, false)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(COLOR, ColorVariant.DEFAULT);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DataTeleportAnchorBlockEntity anchor) {
            MenuOpener.open(ModMenus.DATA_TELEPORT_ANCHOR.get(), player, MenuLocators.forBlockEntity(anchor));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof PoweredCuttingKnifeItem && level.getBlockEntity(pos) instanceof DataTeleportAnchorBlockEntity anchor) {
            return tryHandleCuttingKnifeTeleportToAnchor(stack, level, pos, player, anchor);
        }
        if (stack.getItem() instanceof DyeItem dyeItem) {
            ColorVariant variant = ColorVariant.fromDyeColor(dyeItem.getDyeColor());
            return applyColor(state, level, pos, player, stack, variant, false);
        }
        if (stack.getItem() instanceof ColorApplicatorItem colorApplicatorItem) {
            AEColor aeColor = colorApplicatorItem.getActiveColor(stack);
            ColorVariant variant = ColorVariant.fromAEColor(aeColor);
            if (variant == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return applyColor(state, level, pos, player, stack, variant, true);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    public static ItemInteractionResult tryHandleCuttingKnifeTeleportToAnchor(ItemStack stack, Level level, BlockPos pos, Player player,
                                                                              DataTeleportAnchorBlockEntity anchor) {
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        if (!CuttingKnifeTeleportData.hasEnoughDataFlow(stack)) {
            player.displayClientMessage(Component.translatable(
                    "message.data_energistics.data_teleport_anchor.data_flow_insufficient"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!CuttingKnifeTeleportData.canConsumeDataFlow(stack)) {
            player.displayClientMessage(Component.translatable(
                    "message.data_energistics.data_teleport_anchor.data_flow_insufficient"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!(stack.getItem() instanceof PoweredCuttingKnifeItem poweredCuttingKnifeItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (poweredCuttingKnifeItem.extractAEPower(stack, CuttingKnifeTeleportData.AE_POWER_COST, Actionable.SIMULATE) < CuttingKnifeTeleportData.AE_POWER_COST) {
            player.displayClientMessage(Component.translatable(
                    "message.data_energistics.data_teleport_anchor.knife_insufficient_power"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!anchor.isOnline()) {
            player.displayClientMessage(Component.translatable(
                    "message.data_energistics.data_teleport_anchor.target_offline"), true);
            return ItemInteractionResult.CONSUME;
        }

        if (!CuttingKnifeTeleportData.consumeDataFlow(stack)) {
            player.displayClientMessage(Component.translatable(
                    "message.data_energistics.data_teleport_anchor.data_flow_insufficient"), true);
            return ItemInteractionResult.CONSUME;
        }
        poweredCuttingKnifeItem.extractAEPower(stack, CuttingKnifeTeleportData.AE_POWER_COST, Actionable.MODULATE);

        double targetX = pos.getX() + 0.5D;
        double targetY = pos.getY() + 1.1D;
        double targetZ = pos.getZ() + 0.5D;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(serverPlayer.serverLevel(), targetX, targetY, targetZ, java.util.Set.of(),
                    serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            player.teleportTo(targetX, targetY, targetZ);
        }
        player.fallDistance = 0.0F;
        player.displayClientMessage(Component.translatable(
                "message.data_energistics.data_teleport_anchor.self_teleported",
                pos.getX(), pos.getY(), pos.getZ()), true);
        return ItemInteractionResult.CONSUME;
    }

    private ItemInteractionResult applyColor(BlockState state, Level level, BlockPos pos, Player player, ItemStack stack,
                                             ColorVariant variant, boolean fromApplicator) {
        if (variant == null || state.getValue(COLOR) == variant) {
            return ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(COLOR, variant), 3);
            if (!player.getAbilities().instabuild) {
                if (fromApplicator && stack.getItem() instanceof ColorApplicatorItem colorApplicatorItem) {
                    AEColor aeColor = variant.toAEColor();
                    if (aeColor != null) {
                        colorApplicatorItem.consumeColor(stack, aeColor, false);
                    }
                } else {
                    stack.shrink(1);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DataTeleportAnchorBlockEntity anchor) {
            anchor.removePersistedAnchor();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != ModBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get()) {
            return null;
        }

        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DataTeleportAnchorBlockEntity anchor) {
                anchor.serverTick();
            }
        };
    }

    public enum ColorVariant implements StringRepresentable {

        DEFAULT("default", null),
        BLACK("black", net.minecraft.world.item.DyeColor.BLACK),
        BLUE("blue", net.minecraft.world.item.DyeColor.BLUE),
        BROWN("brown", net.minecraft.world.item.DyeColor.BROWN),
        CYAN("cyan", net.minecraft.world.item.DyeColor.CYAN),
        GRAY("gray", net.minecraft.world.item.DyeColor.GRAY),
        GREEN("green", net.minecraft.world.item.DyeColor.GREEN),
        LIGHT_BLUE("light_blue", net.minecraft.world.item.DyeColor.LIGHT_BLUE),
        LIGHT_GRAY("light_gray", net.minecraft.world.item.DyeColor.LIGHT_GRAY),
        LIME("lime", net.minecraft.world.item.DyeColor.LIME),
        MAGENTA("magenta", net.minecraft.world.item.DyeColor.MAGENTA),
        ORANGE("orange", net.minecraft.world.item.DyeColor.ORANGE),
        PINK("pink", net.minecraft.world.item.DyeColor.PINK),
        PURPLE("purple", net.minecraft.world.item.DyeColor.PURPLE),
        RED("red", net.minecraft.world.item.DyeColor.RED),
        WHITE("white", net.minecraft.world.item.DyeColor.WHITE),
        YELLOW("yellow", net.minecraft.world.item.DyeColor.YELLOW);

        private final String name;
        private final net.minecraft.world.item.DyeColor dyeColor;

        ColorVariant(String name, net.minecraft.world.item.DyeColor dyeColor) {
            this.name = name;
            this.dyeColor = dyeColor;
        }

        public static ColorVariant fromDyeColor(net.minecraft.world.item.DyeColor dyeColor) {
            for (ColorVariant value : values()) {
                if (value.dyeColor == dyeColor) {
                    return value;
                }
            }
            return DEFAULT;
        }

        public static @Nullable ColorVariant fromAEColor(@Nullable AEColor aeColor) {
            if (aeColor == null || aeColor == AEColor.TRANSPARENT || aeColor.dye == null) {
                return null;
            }
            return fromDyeColor(aeColor.dye);
        }

        public @Nullable AEColor toAEColor() {
            if (this.dyeColor == null) {
                return null;
            }
            return AEColor.fromDye(this.dyeColor);
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
