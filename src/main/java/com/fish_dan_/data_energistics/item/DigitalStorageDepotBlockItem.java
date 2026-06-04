package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class DigitalStorageDepotBlockItem extends BlockItem {

    private static final String TAG_SELECTED_FLUID_SLOT = "selected_fluid_slot";
    private static final String TAG_SELECTED_KEY_SLOT = "selected_key_slot";
    private static final String TAG_MARK_MODE = "mark_mode";
    private static final String TAG_BUCKET_MODE = "bucket_mode";
    private static final String LEGACY_TAG_ITEM_FLUID_PREFIX = "item_stored_fluid_";
    private static final int MARK_MODE_FLUID = 0;
    private static final int MARK_MODE_KEY = 1;

    public DigitalStorageDepotBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack placementStack = context.getItemInHand().copy();
        InteractionResult result = super.place(context);
        if (!result.consumesAction() || context.getLevel().isClientSide()) {
            return result;
        }

        restorePlacedDepot(context.getLevel(), context.getClickedPos(), placementStack);
        restorePlacedDepot(context.getLevel(), context.getClickedPos().relative(context.getClickedFace()), placementStack);
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBucketMode(stack)) {
            return super.use(level, player, hand);
        }

        AttemptResult result = tryBucketModeWorldInteraction(level, player, hand, stack, null);
        if (result == AttemptResult.NO_TARGET) {
            return InteractionResultHolder.pass(stack);
        }
        if (result == AttemptResult.SUCCESS) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!isBucketMode(stack)) {
            return super.useOn(context);
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockHitResult hitResult = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        AttemptResult result = tryBucketModeWorldInteraction(level, player, context.getHand(), stack, hitResult);
        if (result == AttemptResult.SUCCESS) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (result == AttemptResult.BLOCKED) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static int getSelectedFluidSlot(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return clampSlot(tag.getInt(TAG_SELECTED_FLUID_SLOT), DigitalStorageDepotBlockEntity.FLUID_SLOTS);
    }

    public static int cycleSelectedFluidSlot(ItemStack stack, boolean reverse) {
        int current = getSelectedFluidSlot(stack);
        int updated = Math.floorMod(current + (reverse ? -1 : 1), DigitalStorageDepotBlockEntity.FLUID_SLOTS);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TAG_SELECTED_FLUID_SLOT, updated);
            tag.putInt(TAG_MARK_MODE, MARK_MODE_FLUID);
        });
        return updated;
    }

    public static int getSelectedKeySlot(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return clampSlot(tag.getInt(TAG_SELECTED_KEY_SLOT), DigitalStorageDepotBlockEntity.KEY_SLOTS);
    }

    public static int cycleSelectedKeySlot(ItemStack stack, boolean reverse) {
        int current = getSelectedKeySlot(stack);
        int updated = Math.floorMod(current + (reverse ? -1 : 1), DigitalStorageDepotBlockEntity.KEY_SLOTS);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TAG_SELECTED_KEY_SLOT, updated);
            tag.putInt(TAG_MARK_MODE, MARK_MODE_KEY);
        });
        return updated;
    }

    public static @Nullable GenericStack getSelectedMarkedStack(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getInt(TAG_MARK_MODE) == MARK_MODE_KEY) {
            return getSelectedKeyStack(stack, registries);
        }
        return getSelectedFluidStack(stack, registries);
    }

    public static @Nullable GenericStack getSelectedFluidStack(ItemStack stack, HolderLookup.Provider registries) {
        FluidStack fluid = getSelectedStoredFluid(stack, registries);
        if (fluid.isEmpty()) {
            return null;
        }
        AEFluidKey fluidKey = AEFluidKey.of(fluid);
        return fluidKey == null ? null : new GenericStack(fluidKey, fluid.getAmount());
    }

    public static @Nullable GenericStack getSelectedKeyStack(ItemStack stack, HolderLookup.Provider registries) {
        int selectedSlot = getSelectedKeySlot(stack);
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        String tagKey = DigitalStorageDepotBlockEntity.getKeyTagKey(selectedSlot);
        if (!blockEntityTag.contains(tagKey)) {
            return null;
        }

        GenericStack keyStack = GenericStack.readTag(registries, blockEntityTag.getCompound(tagKey));
        return keyStack != null && keyStack.what() != null && keyStack.amount() > 0 ? keyStack : null;
    }

    public static boolean isBucketMode(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG_BUCKET_MODE);
    }

    public static boolean toggleBucketMode(ItemStack stack) {
        boolean updated = !isBucketMode(stack);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_BUCKET_MODE, updated);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return updated;
    }

    public static boolean isDepotStack(ItemStack stack) {
        return stack.getItem() instanceof DigitalStorageDepotBlockItem;
    }

    public static void applyStoredFluidsToBlockEntity(ItemStack stack, DigitalStorageDepotBlockEntity depot, HolderLookup.Provider registries) {
        depot.restoreStoredFluids(readStoredFluids(stack, registries));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.getBlockEntity(pos) instanceof DigitalStorageDepotBlockEntity depotBlockEntity) {
            CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            if (!blockEntityTag.isEmpty()) {
                depotBlockEntity.loadTag(blockEntityTag, level.registryAccess());
            }
            applyStoredFluidsToBlockEntity(stack, depotBlockEntity, level.registryAccess());
            depotBlockEntity.saveChanges();
            depotBlockEntity.markForClientUpdate();
        }
        return updated;
    }

    private AttemptResult tryBucketModeWorldInteraction(Level level, Player player, InteractionHand hand, ItemStack stack, @Nullable BlockHitResult placeHitResult) {
        BlockHitResult pickupHitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (pickupHitResult.getType() == HitResult.Type.BLOCK && previewPickupFluid(level, pickupHitResult).isPresent()) {
            AttemptResult pickupResult = tryPickUpFluid(level, player, hand, stack, pickupHitResult);
            if (pickupResult != AttemptResult.NO_TARGET) {
                return pickupResult;
            }
        }

        BlockHitResult hitResult = placeHitResult == null ? getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE) : placeHitResult;
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return AttemptResult.NO_TARGET;
        }
        return tryPlaceFluid(level, player, hand, stack, hitResult);
    }

    private AttemptResult tryPickUpFluid(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        Optional<FluidStack> pickupPreview = previewPickupFluid(level, hitResult);
        if (pickupPreview.isEmpty()) {
            return AttemptResult.NO_TARGET;
        }

        HolderLookup.Provider registries = level.registryAccess();
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] storedFluids = readStoredFluids(stack, registries);
        FluidStack currentFluid = storedFluids[selectedSlot];
        FluidStack candidate = pickupPreview.get().copyWithAmount(FluidType.BUCKET_VOLUME);

        if (!canStoreFluidInSlot(currentFluid, candidate)) {
            player.displayClientMessage(Component.literal("该流体槽无法装入这种液体"), true);
            return AttemptResult.BLOCKED;
        }
        if (!level.mayInteract(player, hitResult.getBlockPos()) || !player.mayUseItemAt(hitResult.getBlockPos(), hitResult.getDirection(), stack)) {
            return AttemptResult.BLOCKED;
        }
        if (level.isClientSide()) {
            return AttemptResult.SUCCESS;
        }

        if (!tryPickUpWorldFluid(level, player, hitResult, candidate)) {
            return AttemptResult.BLOCKED;
        }

        ItemStack updatedDepot = stack.copyWithCount(1);
        storedFluids[selectedSlot] = currentFluid.isEmpty() ? candidate.copy() : currentFluid.copyWithAmount(currentFluid.getAmount() + candidate.getAmount());
        writeStoredFluids(updatedDepot, registries, storedFluids);

        if (player.hasInfiniteMaterials()) {
            stack.applyComponents(updatedDepot.getComponentsPatch());
        } else if (stack.getCount() == 1) {
            player.setItemInHand(hand, updatedDepot);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(updatedDepot)) {
                player.drop(updatedDepot, false);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return AttemptResult.SUCCESS;
    }

    private AttemptResult tryPlaceFluid(Level level, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
        HolderLookup.Provider registries = level.registryAccess();
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] storedFluids = readStoredFluids(stack, registries);
        FluidStack currentFluid = storedFluids[selectedSlot];
        if (currentFluid.isEmpty()) {
            return AttemptResult.NO_TARGET;
        }
        if (currentFluid.getAmount() < FluidType.BUCKET_VOLUME) {
            player.displayClientMessage(Component.literal("当前流体槽液体不足一桶"), true);
            return AttemptResult.BLOCKED;
        }

        BlockPos placePos = resolvePlacementPos(player, level, hitResult, currentFluid);
        if (!level.mayInteract(player, hitResult.getBlockPos()) || !player.mayUseItemAt(placePos, hitResult.getDirection(), stack)) {
            return AttemptResult.BLOCKED;
        }
        if (!canPlaceWorldFluid(level, player, placePos, currentFluid)) {
            return AttemptResult.NO_TARGET;
        }
        if (level.isClientSide()) {
            return AttemptResult.SUCCESS;
        }
        if (!tryPlaceWorldFluid(level, player, hitResult, currentFluid)) {
            return AttemptResult.NO_TARGET;
        }

        ItemStack updatedDepot = stack.copyWithCount(1);
        FluidStack updatedFluid = currentFluid.copy();
        updatedFluid.shrink(FluidType.BUCKET_VOLUME);
        storedFluids[selectedSlot] = updatedFluid;
        writeStoredFluids(updatedDepot, registries, storedFluids);

        if (player.hasInfiniteMaterials()) {
            stack.applyComponents(updatedDepot.getComponentsPatch());
        } else if (stack.getCount() == 1) {
            player.setItemInHand(hand, updatedDepot);
        } else {
            stack.shrink(1);
            if (!player.getInventory().add(updatedDepot)) {
                player.drop(updatedDepot, false);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return AttemptResult.SUCCESS;
    }

    private static Optional<FluidStack> previewPickupFluid(Level level, BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() && state.getFluidState().isSource()) {
            return Optional.of(new FluidStack(state.getFluidState().getType(), FluidType.BUCKET_VOLUME));
        }

        return FluidUtil.getFluidHandler(level, pos, hitResult.getDirection())
                .map(handler -> handler.drain(FluidType.BUCKET_VOLUME, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE))
                .filter(fluid -> !fluid.isEmpty());
    }

    private static boolean tryPickUpWorldFluid(Level level, Player player, BlockHitResult hitResult, FluidStack expectedFluid) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BucketPickup bucketPickup) {
            ItemStack pickedStack = bucketPickup.pickupBlock(player, level, pos, state);
            if (pickedStack.isEmpty()) {
                return false;
            }
            bucketPickup.getPickupSound(state).ifPresent(sound -> level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F));
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            return true;
        }
        Optional<FluidStack> drained = FluidUtil.getFluidHandler(level, pos, hitResult.getDirection())
                .map(handler -> handler.drain(expectedFluid.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE))
                .filter(fluid -> !fluid.isEmpty() && FluidStack.isSameFluidSameComponents(fluid, expectedFluid) && fluid.getAmount() == expectedFluid.getAmount())
                .stream()
                .findFirst();
        if (drained.isPresent()) {
            playPickupFeedback(level, player, pos, drained.get());
            return true;
        }

        if (state.liquid() && state.getFluidState().isSource() && FluidStack.isSameFluidSameComponents(expectedFluid, new FluidStack(state.getFluidState().getType(), expectedFluid.getAmount()))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            playPickupFeedback(level, player, pos, expectedFluid);
            return true;
        }

        return false;
    }

    private static void playPickupFeedback(Level level, Player player, BlockPos pos, FluidStack fluid) {
        SoundEvent sound = fluid.getFluidType().getSound(fluid, SoundActions.BUCKET_FILL);
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
    }

    private static boolean tryPlaceWorldFluid(Level level, Player player, BlockHitResult hitResult, FluidStack fluid) {
        BlockPos placePos = resolvePlacementPos(player, level, hitResult, fluid);
        if (!canPlaceWorldFluid(level, player, placePos, fluid)) {
            return false;
        }
        BlockState state = level.getBlockState(placePos);
        var fluidState = fluid.getFluid().defaultFluidState();
        boolean placed;
        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer && liquidContainer.canPlaceLiquid(player, level, placePos, state, fluid.getFluid())) {
            placed = liquidContainer.placeLiquid(level, placePos, state, fluidState);
        } else {
            placed = level.setBlock(placePos, fluidState.createLegacyBlock(), Block.UPDATE_ALL);
        }
        if (placed) {
            SoundEvent sound = fluid.getFluidType().getSound(fluid, SoundActions.BUCKET_EMPTY);
            if (sound != null) {
                level.playSound(null, placePos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            level.gameEvent(player, GameEvent.FLUID_PLACE, placePos);
        }
        return placed;
    }

    private static boolean canPlaceWorldFluid(Level level, Player player, BlockPos placePos, FluidStack fluid) {
        BlockState state = level.getBlockState(placePos);
        return state.getBlock() instanceof LiquidBlockContainer liquidContainer && liquidContainer.canPlaceLiquid(player, level, placePos, state, fluid.getFluid()) || state.canBeReplaced(fluid.getFluid());
    }

    private static BlockPos resolvePlacementPos(Player player, Level level, BlockHitResult hitResult, FluidStack fluid) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid.getFluid())) {
            return pos;
        }
        if (state.canBeReplaced(fluid.getFluid())) {
            return pos;
        }
        return pos.relative(hitResult.getDirection());
    }

    public static FluidStack getSelectedStoredFluid(ItemStack stack, HolderLookup.Provider registries) {
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] storedFluids = readStoredFluids(stack, registries);
        return selectedSlot >= 0 && selectedSlot < storedFluids.length ? storedFluids[selectedSlot] : FluidStack.EMPTY;
    }

    private static boolean canStoreFluidInSlot(FluidStack currentFluid, FluidStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (currentFluid.isEmpty()) {
            return candidate.getAmount() <= DigitalStorageDepotBlockEntity.FLUID_CAPACITY;
        }
        if (!FluidStack.isSameFluidSameComponents(currentFluid, candidate)) {
            return false;
        }
        return currentFluid.getAmount() + candidate.getAmount() <= DigitalStorageDepotBlockEntity.FLUID_CAPACITY;
    }

    private static FluidStack[] readStoredFluids(ItemStack stack, HolderLookup.Provider registries) {
        FluidStack[] fluids = new FluidStack[DigitalStorageDepotBlockEntity.FLUID_SLOTS];
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            fluids[i] = DigitalStorageDepotBlockEntity.readFluidFromTag(registries, blockEntityTag, i);
            if (fluids[i].isEmpty() && customTag.contains(getLegacyItemFluidTagKey(i))) {
                fluids[i] = FluidStack.parseOptional(registries, customTag.getCompound(getLegacyItemFluidTagKey(i)));
            }
        }
        return fluids;
    }

    private static void writeStoredFluids(ItemStack stack, HolderLookup.Provider registries, FluidStack[] fluids) {
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            FluidStack fluid = i < fluids.length ? fluids[i] : FluidStack.EMPTY;
            DigitalStorageDepotBlockEntity.writeFluidToTag(registries, blockEntityTag, i, fluid);
        }
        clearLegacyStoredFluidTags(stack);
        BlockItem.setBlockEntityData(stack, ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockEntityTag);
    }

    private static void clearLegacyStoredFluidTags(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean changed = false;
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            String key = getLegacyItemFluidTagKey(i);
            if (customTag.contains(key)) {
                customTag.remove(key);
                changed = true;
            }
        }
        if (changed) {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, customTag);
        }
    }

    private static String getLegacyItemFluidTagKey(int slot) {
        return LEGACY_TAG_ITEM_FLUID_PREFIX + slot;
    }

    private static void restorePlacedDepot(Level level, BlockPos pos, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof DigitalStorageDepotBlockEntity depot)) {
            return;
        }

        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        if (!blockEntityTag.isEmpty()) {
            depot.loadTag(blockEntityTag, registries);
        }
        applyStoredFluidsToBlockEntity(stack, depot, registries);
        depot.saveChanges();
        depot.markForClientUpdate();
    }

    private static int clampSlot(int slot, int maxSlots) {
        if (slot < 0 || slot >= maxSlots) {
            return 0;
        }
        return slot;
    }

    private enum AttemptResult {
        NO_TARGET,
        BLOCKED,
        SUCCESS
    }
}
