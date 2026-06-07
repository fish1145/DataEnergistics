package com.fish_dan_.data_energistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BlockAndPartUpgradeItem extends Item {

    private final Map<Class<? extends BlockEntity>, BlockUpgradeTarget> blockTargets = new HashMap<>();
    private final Map<Class<? extends IPart>, Supplier<? extends IPartItem<? extends IPart>>> partTargets = new HashMap<>();

    public BlockAndPartUpgradeItem(Properties properties) {
        super(properties);
    }

    protected <T extends BlockEntity> void addBlock(
                                                    Class<T> sourceClass,
                                                    Supplier<? extends Block> targetBlock,
                                                    Supplier<? extends BlockEntityType<? extends BlockEntity>> targetType) {
        this.blockTargets.put(sourceClass, new BlockUpgradeTarget(targetBlock, targetType));
    }

    protected <T extends IPart> void addPart(Class<T> sourceClass, Supplier<? extends IPartItem<? extends IPart>> targetItem) {
        this.partTargets.put(sourceClass, targetItem);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        if (tryUpgradeBlock(context, level, pos, blockEntity)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (tryUpgradePart(context, level, pos, blockEntity)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }

    private boolean tryUpgradeBlock(UseOnContext context, Level level, BlockPos pos, BlockEntity sourceBlockEntity) {
        BlockUpgradeTarget target = this.blockTargets.get(sourceBlockEntity.getClass());
        if (target == null) {
            return false;
        }

        if (!level.isClientSide()) {
            Block targetBlock = target.targetBlock().get();
            BlockEntityType<? extends BlockEntity> targetType = target.targetType().get();
            BlockState oldState = level.getBlockState(pos);
            BlockState newState = targetBlock.getStateForPlacement(new BlockPlaceContext(context));
            if (newState == null) {
                newState = targetBlock.defaultBlockState();
            }
            newState = copyCompatibleProperties(oldState, newState);

            CompoundTag data = sourceBlockEntity.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(pos);
            level.removeBlock(pos, false);
            level.setBlock(pos, newState, Block.UPDATE_ALL);

            BlockEntity targetBlockEntity = targetType.create(pos, newState);
            if (targetBlockEntity == null) {
                return false;
            }
            level.setBlockEntity(targetBlockEntity);
            targetBlockEntity.loadWithComponents(data, level.registryAccess());
            markChanged(targetBlockEntity);
            shrinkUpgradeItem(context);
        }
        return true;
    }

    private boolean tryUpgradePart(UseOnContext context, Level level, BlockPos pos, BlockEntity blockEntity) {
        if (!(blockEntity instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        Vec3 hitLocation = context.getClickLocation();
        Vec3 localHit = hitLocation.subtract(pos.getX(), pos.getY(), pos.getZ());
        IPart selectedPart = cableBusBlockEntity.getCableBus().selectPartLocal(localHit).part;
        if (selectedPart == null) {
            return false;
        }

        Supplier<? extends IPartItem<? extends IPart>> targetItem = this.partTargets.get(selectedPart.getClass());
        if (targetItem == null) {
            return false;
        }

        if (!level.isClientSide()) {
            Direction side = selectedPart instanceof AEBasePart basePart ? basePart.getSide() : null;
            CompoundTag data = new CompoundTag();
            selectedPart.writeToNBT(data, level.registryAccess());
            IPart targetPart = replacePart(cableBusBlockEntity, targetItem.get(), side, context);
            if (targetPart == null) {
                return false;
            }

            targetPart.readFromNBT(data, level.registryAccess());
            targetPart.addToWorld();
            cableBusBlockEntity.markForUpdate();
            cableBusBlockEntity.markForSave();
            shrinkUpgradeItem(context);
        }
        return true;
    }

    private static void shrinkUpgradeItem(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
    }

    private static void markChanged(BlockEntity blockEntity) {
        blockEntity.setChanged();
        if (blockEntity instanceof AEBaseBlockEntity aeBlockEntity) {
            aeBlockEntity.markForUpdate();
        }
    }

    private static BlockState copyCompatibleProperties(BlockState source, BlockState target) {
        BlockState result = target;
        for (Map.Entry<Property<?>, Comparable<?>> entry : source.getValues().entrySet()) {
            result = copyProperty(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
                                                                     BlockState target,
                                                                     Property<T> property,
                                                                     Comparable<?> value) {
        if (!target.hasProperty(property)) {
            return target;
        }
        return target.setValue(property, property.getValueClass().cast(value));
    }

    @SuppressWarnings("unchecked")
    private static @Nullable IPart replacePart(
                                               CableBusBlockEntity cableBusBlockEntity,
                                               IPartItem<? extends IPart> targetItem,
                                               @Nullable Direction side,
                                               UseOnContext context) {
        return cableBusBlockEntity.replacePart((IPartItem<IPart>) targetItem, side, context.getPlayer(), context.getHand());
    }

    private record BlockUpgradeTarget(
                                      Supplier<? extends Block> targetBlock,
                                      Supplier<? extends BlockEntityType<? extends BlockEntity>> targetType) {}
}
