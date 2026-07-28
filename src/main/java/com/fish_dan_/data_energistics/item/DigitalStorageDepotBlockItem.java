package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotBlockEntity;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DigitalStorageDepotBlockItem extends BlockItem {

    private static final int MARK_MODE_FLUID = 0;
    private static final int MARK_MODE_KEY = 1;
    private static final HolderLookup.Provider ITEM_CAPABILITY_REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    public DigitalStorageDepotBlockItem(Block block, Properties properties) {
        super(block, properties);
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
        return clampSlot(getDepotData(stack).selectedFluidSlot(), DigitalStorageDepotBlockEntity.FLUID_SLOTS);
    }

    public static int cycleSelectedFluidSlot(ItemStack stack, boolean reverse) {
        int current = getSelectedFluidSlot(stack);
        int updated = Math.floorMod(current + (reverse ? -1 : 1), DigitalStorageDepotBlockEntity.FLUID_SLOTS);
        setDepotData(stack, getDepotData(stack).withSelectedFluidSlot(updated).withMarkMode(MARK_MODE_FLUID));
        return updated;
    }

    public static int getSelectedKeySlot(ItemStack stack) {
        return clampSlot(getDepotData(stack).selectedKeySlot(), DigitalStorageDepotBlockEntity.KEY_SLOTS);
    }

    public static int cycleSelectedKeySlot(ItemStack stack, boolean reverse) {
        int current = getSelectedKeySlot(stack);
        int updated = Math.floorMod(current + (reverse ? -1 : 1), DigitalStorageDepotBlockEntity.KEY_SLOTS);
        setDepotData(stack, getDepotData(stack).withSelectedKeySlot(updated).withMarkMode(MARK_MODE_KEY));
        return updated;
    }

    public static @Nullable GenericStack getSelectedMarkedStack(ItemStack stack, HolderLookup.Provider registries) {
        if (getDepotData(stack).markMode() == MARK_MODE_KEY) {
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

    public static @Nullable GenericStack getSelectedKeyStack(ItemStack stack) {
        return getSelectedKeyStack(stack, ITEM_CAPABILITY_REGISTRIES);
    }

    public static boolean isBucketMode(ItemStack stack) {
        return getDepotData(stack).bucketMode();
    }

    public static boolean isKeySlotMarked(ItemStack stack) {
        return getDepotData(stack).markMode() == MARK_MODE_KEY;
    }

    public static boolean toggleBucketMode(ItemStack stack) {
        boolean updated = !isBucketMode(stack);
        setDepotData(stack, getDepotData(stack).withBucketMode(updated));
        return updated;
    }

    public static boolean isDepotStack(ItemStack stack) {
        return stack.getItem() instanceof DigitalStorageDepotBlockItem;
    }

    public static long insertIntoSelectedTerminalSlot(ItemStack stack, HolderLookup.Provider registries, AEKey what,
                                                      long amount, Actionable mode) {
        if (!isBucketMode(stack) || amount <= 0 || what instanceof AEItemKey) {
            return 0L;
        }
        if (what instanceof AEFluidKey fluidKey) {
            return insertIntoSelectedFluidSlot(stack, registries, fluidKey, amount, mode);
        }
        return insertIntoSelectedKeySlot(stack, registries, what, amount, mode);
    }

    public static long extractFromSelectedTerminalSlot(ItemStack stack, HolderLookup.Provider registries,
                                                       @Nullable AEKey requested, long amount, Actionable mode) {
        if (!isBucketMode(stack) || amount <= 0) {
            return 0L;
        }

        if (getDepotData(stack).markMode() == MARK_MODE_KEY) {
            return extractFromSelectedKeySlot(stack, registries, requested, amount, mode);
        }
        return extractFromSelectedFluidSlot(stack, registries, requested, amount, mode);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        DigitalStorageDepotTooltipComponent component = createTooltipComponent(stack, ITEM_CAPABILITY_REGISTRIES);
        return component.isEmpty() ? Optional.empty() : Optional.of(component);
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

        if (!canStoreFluidInSelectedSlot(storedFluids, selectedSlot, candidate, getFluidCapacity(stack))) {
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
                .map(handler -> handler.drain(FluidType.BUCKET_VOLUME, FluidAction.SIMULATE))
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
                .map(handler -> handler.drain(expectedFluid.copy(), FluidAction.EXECUTE))
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

    public static FluidStack getSelectedStoredFluid(ItemStack stack) {
        return getSelectedStoredFluid(stack, ITEM_CAPABILITY_REGISTRIES);
    }

    public static int getFluidCapacity(ItemStack stack) {
        return DigitalStorageDepotBlockEntity.computeFluidCapacity(getInstalledCapacityCardCount(stack));
    }

    public static long getKeyCapacity(ItemStack stack) {
        return DigitalStorageDepotBlockEntity.computeKeyCapacity(getInstalledCapacityCardCount(stack));
    }

    public static int fillSelectedFluidSlot(ItemStack stack, FluidStack resource, FluidAction action) {
        if (!isBucketMode(stack) || resource.isEmpty()) {
            return 0;
        }

        AEFluidKey fluidKey = AEFluidKey.of(resource);
        if (fluidKey == null) {
            return 0;
        }

        long inserted = insertIntoSelectedTerminalSlot(
                stack,
                ITEM_CAPABILITY_REGISTRIES,
                fluidKey,
                resource.getAmount(),
                action.execute() ? Actionable.MODULATE : Actionable.SIMULATE);
        return (int) Math.min(Integer.MAX_VALUE, inserted);
    }

    public static FluidStack drainSelectedFluidSlot(ItemStack stack, int maxDrain, FluidAction action) {
        if (!isBucketMode(stack) || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack current = getSelectedStoredFluid(stack);
        if (current.isEmpty()) {
            return FluidStack.EMPTY;
        }

        AEFluidKey fluidKey = AEFluidKey.of(current);
        if (fluidKey == null) {
            return FluidStack.EMPTY;
        }

        int drainAmount = Math.min(current.getAmount(), maxDrain);
        long extracted = extractFromSelectedTerminalSlot(
                stack,
                ITEM_CAPABILITY_REGISTRIES,
                fluidKey,
                drainAmount,
                action.execute() ? Actionable.MODULATE : Actionable.SIMULATE);
        return extracted <= 0 ? FluidStack.EMPTY : current.copyWithAmount((int) Math.min(Integer.MAX_VALUE, extracted));
    }

    public static FluidStack drainSelectedFluidSlot(ItemStack stack, FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        FluidStack current = getSelectedStoredFluid(stack);
        if (current.isEmpty() || !FluidStack.isSameFluidSameComponents(current, resource)) {
            return FluidStack.EMPTY;
        }
        return drainSelectedFluidSlot(stack, resource.getAmount(), action);
    }

    public static long insertIntoSelectedKeySlot(ItemStack stack, AEKey what, long amount, Actionable mode) {
        return insertIntoSelectedKeySlot(stack, ITEM_CAPABILITY_REGISTRIES, what, amount, mode);
    }

    public static long extractFromSelectedKeySlot(ItemStack stack, AEKey requested, long amount, Actionable mode) {
        return extractFromSelectedKeySlot(stack, ITEM_CAPABILITY_REGISTRIES, requested, amount, mode);
    }

    private static boolean canStoreFluidInSelectedSlot(FluidStack[] fluids, int selectedSlot, FluidStack candidate, int capacity) {
        if (candidate.isEmpty()) {
            return false;
        }
        FluidStack currentFluid = fluids[selectedSlot];
        if (currentFluid.isEmpty()) {
            return !DigitalStorageDepotBlockEntity.hasConflictingFluid(fluids, selectedSlot, candidate) && candidate.getAmount() <= capacity;
        }
        if (!FluidStack.isSameFluidSameComponents(currentFluid, candidate)) {
            return false;
        }
        return currentFluid.getAmount() + candidate.getAmount() <= capacity;
    }

    private static long insertIntoSelectedFluidSlot(ItemStack stack, HolderLookup.Provider registries, AEFluidKey fluidKey,
                                                    long amount, Actionable mode) {
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] fluids = readStoredFluids(stack, registries);
        FluidStack current = fluids[selectedSlot];
        FluidStack incoming = fluidKey.toStack(1);
        int capacity = getFluidCapacity(stack);
        if (!canStoreFluidInSelectedSlot(fluids, selectedSlot, incoming, capacity)) {
            return 0L;
        }

        long currentAmount = current.isEmpty() ? 0L : current.getAmount();
        long inserted = Math.min(amount, capacity - currentAmount);
        if (inserted <= 0) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            int updatedAmount = (int) Math.min(capacity, currentAmount + inserted);
            fluids[selectedSlot] = fluidKey.toStack(updatedAmount);
            writeStoredFluids(stack, registries, fluids);
        }
        return inserted;
    }

    private static long extractFromSelectedFluidSlot(ItemStack stack, HolderLookup.Provider registries,
                                                     @Nullable AEKey requested, long amount, Actionable mode) {
        int selectedSlot = getSelectedFluidSlot(stack);
        FluidStack[] fluids = readStoredFluids(stack, registries);
        FluidStack current = fluids[selectedSlot];
        if (current.isEmpty()) {
            return 0L;
        }

        AEFluidKey currentKey = AEFluidKey.of(current);
        if (currentKey == null || requested != null && !currentKey.equals(requested)) {
            return 0L;
        }

        long extracted = Math.min(amount, current.getAmount());
        if (extracted <= 0) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            FluidStack updated = current.copy();
            updated.shrink((int) extracted);
            fluids[selectedSlot] = updated.isEmpty() ? FluidStack.EMPTY : updated;
            writeStoredFluids(stack, registries, fluids);
        }
        return extracted;
    }

    private static long insertIntoSelectedKeySlot(ItemStack stack, HolderLookup.Provider registries, AEKey what,
                                                  long amount, Actionable mode) {
        if (what instanceof AEFluidKey || !isAllowedTerminalKey(what)) {
            return 0L;
        }

        int selectedSlot = getSelectedKeySlot(stack);
        GenericStack[] keys = readStoredKeys(stack, registries);
        GenericStack current = keys[selectedSlot];
        if (current != null && current.what() != null && !current.what().equals(what)) {
            return 0L;
        }
        if ((current == null || current.what() == null || current.amount() <= 0) && DigitalStorageDepotBlockEntity.hasConflictingKey(keys, selectedSlot, what)) {
            return 0L;
        }

        long currentAmount = current == null ? 0L : current.amount();
        long inserted = Math.min(amount, getKeyCapacity(stack) - currentAmount);
        if (inserted <= 0) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            writeSelectedKeyStack(stack, registries, new GenericStack(what, currentAmount + inserted));
        }
        return inserted;
    }

    private static long extractFromSelectedKeySlot(ItemStack stack, HolderLookup.Provider registries,
                                                   @Nullable AEKey requested, long amount, Actionable mode) {
        GenericStack current = getSelectedKeyStack(stack, registries);
        if (current == null || current.what() == null || current.amount() <= 0) {
            return 0L;
        }
        if (requested != null && !current.what().equals(requested)) {
            return 0L;
        }

        long extracted = Math.min(amount, current.amount());
        if (extracted <= 0) {
            return 0L;
        }

        if (mode == Actionable.MODULATE) {
            long remaining = current.amount() - extracted;
            writeSelectedKeyStack(stack, registries, remaining <= 0 ? null : new GenericStack(current.what(), remaining));
        }
        return extracted;
    }

    private static boolean isAllowedTerminalKey(AEKey what) {
        return what != null && !(what instanceof AEItemKey) && !(what instanceof AEFluidKey);
    }

    private static DigitalStorageDepotTooltipComponent createTooltipComponent(ItemStack stack, HolderLookup.Provider registries) {
        return new DigitalStorageDepotTooltipComponent(
                readStoredItemStacks(stack, registries),
                readStoredFluidStacks(stack, registries),
                readStoredKeyStacks(stack, registries));
    }

    private static List<GenericStack> readStoredItemStacks(ItemStack stack, HolderLookup.Provider registries) {
        AppEngInternalInventory inventory = new AppEngInternalInventory(DigitalStorageDepotBlockEntity.STORAGE_SLOTS);
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        inventory.readFromNBT(blockEntityTag, DigitalStorageDepotBlockEntity.getStorageTagKey(), registries);

        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.getStackInSlot(i);
            AEItemKey itemKey = AEItemKey.of(itemStack);
            if (itemKey != null) {
                stacks.add(new GenericStack(itemKey, itemStack.getCount()));
            }
        }
        return stacks;
    }

    private static List<GenericStack> readStoredFluidStacks(ItemStack stack, HolderLookup.Provider registries) {
        List<GenericStack> stacks = new ArrayList<>();
        for (FluidStack fluid : readStoredFluids(stack, registries)) {
            AEFluidKey fluidKey = AEFluidKey.of(fluid);
            if (fluidKey != null) {
                stacks.add(new GenericStack(fluidKey, fluid.getAmount()));
            }
        }
        return stacks;
    }

    private static List<GenericStack> readStoredKeyStacks(ItemStack stack, HolderLookup.Provider registries) {
        List<GenericStack> stacks = new ArrayList<>();
        for (GenericStack keyStack : readStoredKeys(stack, registries)) {
            if (keyStack != null && keyStack.what() != null && keyStack.amount() > 0) {
                stacks.add(keyStack);
            }
        }
        return stacks;
    }

    private static FluidStack[] readStoredFluids(ItemStack stack, HolderLookup.Provider registries) {
        FluidStack[] fluids = new FluidStack[DigitalStorageDepotBlockEntity.FLUID_SLOTS];
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            fluids[i] = DigitalStorageDepotBlockEntity.readFluidFromTag(registries, blockEntityTag, i);
        }
        return fluids;
    }

    private static GenericStack[] readStoredKeys(ItemStack stack, HolderLookup.Provider registries) {
        GenericStack[] keys = new GenericStack[DigitalStorageDepotBlockEntity.KEY_SLOTS];
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < DigitalStorageDepotBlockEntity.KEY_SLOTS; i++) {
            String tagKey = DigitalStorageDepotBlockEntity.getKeyTagKey(i);
            keys[i] = blockEntityTag.contains(tagKey) ? GenericStack.readTag(registries, blockEntityTag.getCompound(tagKey)) : null;
        }
        return keys;
    }

    private static int getInstalledCapacityCardCount(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        IUpgradeInventory upgrades = UpgradeInventories.forMachine(stack.getItem(), DigitalStorageDepotBlockEntity.UPGRADE_SLOTS, () -> {});
        upgrades.readFromNBT(blockEntityTag, DigitalStorageDepotBlockEntity.getUpgradesTagKey(), ITEM_CAPABILITY_REGISTRIES);
        return Math.max(0, upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
    }

    private static DigitalStorageDepotItemData getDepotData(ItemStack stack) {
        DigitalStorageDepotItemData data = stack.get(ModDataComponents.DIGITAL_STORAGE_DEPOT.get());
        return data != null ? data : DigitalStorageDepotItemData.DEFAULT;
    }

    private static void setDepotData(ItemStack stack, DigitalStorageDepotItemData data) {
        stack.set(ModDataComponents.DIGITAL_STORAGE_DEPOT.get(), data);
    }

    private static void writeStoredFluids(ItemStack stack, HolderLookup.Provider registries, FluidStack[] fluids) {
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        int capacity = getFluidCapacity(stack);
        for (int i = 0; i < DigitalStorageDepotBlockEntity.FLUID_SLOTS; i++) {
            FluidStack fluid = i < fluids.length ? fluids[i] : FluidStack.EMPTY;
            DigitalStorageDepotBlockEntity.writeFluidToTag(registries, blockEntityTag, i, fluid, capacity);
        }
        BlockItem.setBlockEntityData(stack, ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockEntityTag);
    }

    private static void writeSelectedKeyStack(ItemStack stack, HolderLookup.Provider registries, @Nullable GenericStack keyStack) {
        int selectedSlot = getSelectedKeySlot(stack);
        CompoundTag blockEntityTag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        String tagKey = DigitalStorageDepotBlockEntity.getKeyTagKey(selectedSlot);
        if (keyStack == null || keyStack.what() == null || keyStack.amount() <= 0) {
            blockEntityTag.remove(tagKey);
        } else {
            blockEntityTag.put(tagKey, GenericStack.writeTag(registries, keyStack));
        }
        BlockItem.setBlockEntityData(stack, ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(), blockEntityTag);
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
