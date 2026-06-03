package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.Optional;

public class DigitalStorageDepotBlockItem extends BlockItem {

    private static final String TAG_SELECTED_FLUID_SLOT = "selected_fluid_slot";
    private static final String TAG_BUCKET_MODE = "bucket_mode";
    private static final String TAG_ITEM_FLUID_PREFIX = "item_stored_fluid_";

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

        boolean placingFluid = player.isShiftKeyDown();
        FluidStack selectedFluid = getSelectedStoredFluid(stack, level.registryAccess());
        ClipContext.Fluid clipMode = placingFluid && !selectedFluid.isEmpty() ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY;
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, clipMode);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        AttemptResult result = placingFluid
                ? tryPlaceFluid(level, player, hand, stack, hitResult)
                : tryPickUpFluid(level, player, hand, stack, hitResult);
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
        if (!isBucketMode(context.getItemInHand())) {
            return super.useOn(context);
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockHitResult hitResult = new BlockHitResult(
                context.getClickLocation(),
                context.getClickedFace(),
                context.getClickedPos(),
                context.isInside());
        AttemptResult result = context.isSecondaryUseActive()
                ? tryPlaceFluid(context.getLevel(), player, context.getHand(), context.getItemInHand(), hitResult)
                : tryPickUpFluid(context.getLevel(), player, context.getHand(), context.getItemInHand(), hitResult);
        if (result == AttemptResult.SUCCESS) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        if (result == AttemptResult.BLOCKED) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    public static int getSelectedFluidSlot(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return clampSlot(tag.getInt(TAG_SELECTED_FLUID_SLOT));
    }

    public static int cycleSelectedFluidSlot(ItemStack stack, boolean reverse) {
        int current = getSelectedFluidSlot(stack);
        int updated = Math.floorMod(current + (reverse ? -1 : 1), DigitalStorageDepotBlockEntity.FLUID_SLOTS);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_SELECTED_FLUID_SLOT, updated));
        return updated;
    }

    public static boolean isBucketMode(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG_BUCKET_MODE);
    }

    public static boolean toggleBucketMode(ItemStack stack) {
        boolean updated = !isBucketMode(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_BUCKET_MODE, updated));
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

        if (!canStoreFluidInSlot(storedFluids, selectedSlot, currentFluid, candidate)) {
            player.displayClientMessage(Component.literal("该流体槽无法装入这种液体"), true);
            return AttemptResult.BLOCKED;
        }

        FluidActionResult pickupResult = FluidUtil.tryPickUpFluid(new ItemStack(Items.BUCKET), player, level, hitResult.getBlockPos(), hitResult.getDirection());
        if (!pickupResult.isSuccess()) {
            return AttemptResult.BLOCKED;
        }

        FluidStack pickedUpFluid = FluidUtil.getFluidContained(pickupResult.getResult()).orElse(FluidStack.EMPTY);
        if (pickedUpFluid.isEmpty()) {
            return AttemptResult.BLOCKED;
        }

        ItemStack updatedDepot = stack.copyWithCount(1);
        storedFluids[selectedSlot] = currentFluid.isEmpty()
                ? pickedUpFluid.copy()
                : currentFluid.copyWithAmount(currentFluid.getAmount() + pickedUpFluid.getAmount());
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
        FluidActionResult placeResult = FluidUtil.tryPlaceFluid(
                player,
                level,
                hand,
                placePos,
                new ItemStack(Items.BUCKET),
                currentFluid.copyWithAmount(FluidType.BUCKET_VOLUME));
        if (!placeResult.isSuccess()) {
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

    private static BlockPos resolvePlacementPos(Player player, Level level, BlockHitResult hitResult, FluidStack fluid) {
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(player, level, pos, state, fluid.getFluid())) {
            return pos;
        }
        if (state.canBeReplaced(fluid.getFluid())) {
            return pos;
        }
        return pos.relative(hitResult.getDirection());
    }

    private static FluidStack getSelectedStoredFluid(ItemStack stack, HolderLookup.Provider registries) {
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] storedFluids = readStoredFluids(stack, registries);
        return selectedSlot >= 0 && selectedSlot < storedFluids.length ? storedFluids[selectedSlot] : FluidStack.EMPTY;
    }

    private static boolean canStoreFluidInSlot(FluidStack[] storedFluids, int selectedSlot, FluidStack currentFluid, FluidStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (DigitalStorageDepotBlockEntity.hasConflictingFluid(storedFluids, selectedSlot, candidate)) {
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
            String itemFluidTagKey = getItemFluidTagKey(i);
            fluids[i] = customTag.contains(itemFluidTagKey)
                    ? FluidStack.parseOptional(registries, customTag.getCompound(itemFluidTagKey))
                    : DigitalStorageDepotBlockEntity.readFluidFromTag(registries, blockEntityTag, i);
        }
        return fluids;
    }

    private static void writeStoredFluids(ItemStack stack, HolderLookup.Provider registries, FluidStack[] fluids) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            FluidStack fluid = i < fluids.length ? fluids[i] : FluidStack.EMPTY;
            if (fluid.isEmpty()) {
                customTag.remove(getItemFluidTagKey(i));
            } else {
                customTag.put(getItemFluidTagKey(i), fluid.save(registries));
            }
            DigitalStorageDepotBlockEntity.writeFluidToTag(registries, blockEntityTag, i, fluid);
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, customTag);
        BlockItem.setBlockEntityData(stack, ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockEntityTag);
    }

    private static String getItemFluidTagKey(int slot) {
        return TAG_ITEM_FLUID_PREFIX + slot;
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

    private static int clampSlot(int slot) {
        if (slot < 0 || slot >= DigitalStorageDepotBlockEntity.FLUID_SLOTS) {
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
