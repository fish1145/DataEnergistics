package com.fish_dan_.data_energistics.blockentity;

import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.util.OreDataCarrierData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataMimeticFieldBlockEntity extends AENetworkedPoweredBlockEntity implements IUpgradeableObject {
    public static final int SLOT_COUNT = 36;
    public static final int BASE_ACTIVE_ROWS = 1;
    public static final int MAX_ACTIVE_ROWS = 4;
    public static final double ENERGY_CACHE_CAPACITY = 1600.0;
    private static final int HIDDEN_BUFFER_SLOTS = 64;
    private static final double POWER_PER_ACTIVE_CARRIER = 500.0;
    private static final long DATA_FLOW_PER_CARRIER_PER_SECOND = 100L;
    private static final int BASE_WORK_INTERVAL_SECONDS = 4;
    private static final int MIN_WORK_INTERVAL_SECONDS = 1;
    private static final int BASE_BIOLOGY_LOOT_ROLLS_PER_CYCLE = 8;
    private static final int BASE_ORE_OUTPUT_ROLLS_PER_CYCLE = 8;
    private static final int UPGRADE_SLOTS = 4;
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";
    private static final String DROP_ROUTING_MODE_TAG = "drop_routing_mode";
    private static final String WORK_TICKS_TAG = "work_ticks";
    private static final String HIDDEN_BUFFER_TAG = "hidden_buffer";

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, SLOT_COUNT);
    private final AppEngInternalInventory hiddenBuffer = new AppEngInternalInventory(this, HIDDEN_BUFFER_SLOTS);
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(ModBlocks.DATA_MIMETIC_FIELD.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private boolean redstoneControlled;
    private DataExtractorDropRoutingMode dropRoutingMode = DataExtractorDropRoutingMode.OFF;
    private int workTicks;

    public DataMimeticFieldBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_MIMETIC_FIELD.get())
                .setIdlePowerUsage(0.0);
        this.setInternalMaxPower(ENERGY_CACHE_CAPACITY);
        this.storage.setFilter(new CarrierOnlyFilter());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.storage;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.hiddenBuffer.readFromNBT(data, HIDDEN_BUFFER_TAG, registries);
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.dropRoutingMode = DataExtractorDropRoutingMode.fromOrdinal(data.getInt(DROP_ROUTING_MODE_TAG));
        this.workTicks = Math.max(0, data.getInt(WORK_TICKS_TAG));
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        this.hiddenBuffer.writeToNBT(data, HIDDEN_BUFFER_TAG, registries);
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        data.putInt(DROP_ROUTING_MODE_TAG, this.dropRoutingMode.ordinal());
        data.putInt(WORK_TICKS_TAG, this.workTicks);
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.storage;
        }
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    public void serverTick() {
        updatePowerUsage();
        flushHiddenBuffer();
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            return;
        }
        if (isHiddenBufferFull()) {
            return;
        }
        if (!consumeDataFlowPerSecond()) {
            return;
        }
        this.workTicks++;
        if (this.workTicks < computeWorkIntervalTicks()) {
            return;
        }
        this.workTicks = 0;
        performBiologyMimeticWork();
        performOreMimeticWork();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePowerUsage();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    public DataExtractorDropRoutingMode getDropRoutingMode() {
        return this.dropRoutingMode;
    }

    public boolean setRedstoneControlled(boolean enabled) {
        if (this.redstoneControlled == enabled) {
            return this.redstoneControlled;
        }

        this.redstoneControlled = enabled;
        this.setChanged();
        updatePowerUsage();
        this.markForClientUpdate();
        return this.redstoneControlled;
    }

    public DataExtractorDropRoutingMode setDropRoutingMode(DataExtractorDropRoutingMode mode) {
        DataExtractorDropRoutingMode resolvedMode = mode == null ? DataExtractorDropRoutingMode.OFF : mode;
        if (this.dropRoutingMode == resolvedMode) {
            return this.dropRoutingMode;
        }

        this.dropRoutingMode = resolvedMode;
        this.setChanged();
        this.markForClientUpdate();
        return this.dropRoutingMode;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.hiddenBuffer) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.hiddenBuffer.clear();
        this.upgrades.clear();
    }

    public void dropContents(Level level, BlockPos pos) {
        ArrayList<ItemStack> drops = new ArrayList<>();
        this.addAdditionalDrops(level, pos, drops);
        this.clearContent();
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    private static final class CarrierOnlyFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return stack.is(ModItems.BIOLOGY_DATA_CARRIER.get()) || stack.is(ModItems.ORE_DATA_CARRIER.get());
        }
    }

    private void onUpgradesChanged() {
        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        updatePowerUsage();
    }

    private void updatePowerUsage() {
        this.getMainNode().setIdlePowerUsage(computeIdlePowerUsage());
    }

    private double computeIdlePowerUsage() {
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            return 0.0;
        }

        return countActiveCarriers() * POWER_PER_ACTIVE_CARRIER;
    }

    private int countActiveCarriers() {
        int count = 0;
        for (int i = 0; i < getActiveSlotCount(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (hasRecordedData(stack)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRecordedData(ItemStack stack) {
        return !stack.isEmpty()
                && (BiologyDataCarrierData.hasRecordedEntity(stack) || OreDataCarrierData.hasRecordedOre(stack));
    }

    private void performBiologyMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
            return;
        }

        List<ItemStack> generated = new ArrayList<>();
        for (int i = 0; i < getActiveSlotCount(); i++) {
            ItemStack carrier = this.storage.getStackInSlot(i);
            if (BiologyDataCarrierData.hasRecordedEntity(carrier)) {
                generated.addAll(generateBiologyLoot(serverLevel, carrier));
            }
        }

        if (generated.isEmpty() || !canHiddenBufferAcceptAll(generated)) {
            return;
        }

        insertAllIntoHiddenBuffer(generated);
    }

    private void performOreMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
            return;
        }

        List<ItemStack> generated = new ArrayList<>();
        for (int i = 0; i < getActiveSlotCount(); i++) {
            ItemStack carrier = this.storage.getStackInSlot(i);
            if (!OreDataCarrierData.hasRecordedOre(carrier)) {
                continue;
            }

            for (int roll = 0; roll < getOreOutputRollsPerCycle(); roll++) {
                generated.addAll(generateOreLoot(serverLevel, carrier));
            }
        }

        if (generated.isEmpty() || !canHiddenBufferAcceptAll(generated)) {
            return;
        }

        insertAllIntoHiddenBuffer(generated);
    }

    private void flushHiddenBuffer() {
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.OFF || this.level == null) {
            return;
        }
        flushHiddenBuffer(getAdjacentItemHandlers(), getConnectedItemNetwork());
    }

    private void flushHiddenBuffer(List<IItemHandler> adjacentHandlers, @Nullable MEStorage networkStorage) {
        for (int i = 0; i < this.hiddenBuffer.size(); i++) {
            ItemStack currentStack = this.hiddenBuffer.getStackInSlot(i);
            if (currentStack.isEmpty()) {
                continue;
            }

            ItemStack remaining = routeGeneratedItem(currentStack, adjacentHandlers, networkStorage);
            if (remaining.getCount() < currentStack.getCount()) {
                this.hiddenBuffer.setItemDirect(i, remaining);
            }
        }
    }

    private ItemStack routeGeneratedItem(ItemStack stack, List<IItemHandler> adjacentHandlers, @Nullable MEStorage networkStorage) {
        ItemStack remaining = stack.copy();
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            remaining = insertIntoNetwork(remaining, networkStorage);
            return insertIntoAdjacentContainers(remaining, adjacentHandlers);
        }

        remaining = insertIntoAdjacentContainers(remaining, adjacentHandlers);
        return insertIntoNetwork(remaining, networkStorage);
    }

    private ItemStack insertIntoAdjacentContainers(ItemStack stack, List<IItemHandler> adjacentHandlers) {
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : adjacentHandlers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
        }
        return remaining;
    }

    private ItemStack insertIntoNetwork(ItemStack stack, @Nullable MEStorage networkStorage) {
        if (stack.isEmpty() || networkStorage == null) {
            return stack;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return stack;
        }

        long inserted = networkStorage.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this));
        if (inserted <= 0) {
            return stack;
        }

        ItemStack remaining = stack.copy();
        remaining.shrink((int) Math.min(inserted, stack.getCount()));
        return remaining;
    }

    private List<IItemHandler> getAdjacentItemHandlers() {
        if (this.level == null) {
            return List.of();
        }

        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            IItemHandler handler = this.level.getCapability(
                    Capabilities.ItemHandler.BLOCK,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite()
            );
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    @Nullable
    private MEStorage getConnectedItemNetwork() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return null;
        }

        var storageService = node.getGrid().getStorageService();
        return storageService == null ? null : storageService.getInventory();
    }

    private List<ItemStack> generateBiologyLoot(ServerLevel serverLevel, ItemStack carrier) {
        ResourceLocation entityId = BiologyDataCarrierData.getEntityTypeId(carrier);
        if (entityId == null) {
            return List.of();
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            return List.of();
        }

        Entity entity = entityType.create(serverLevel);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return List.of();
        }

        livingEntity.setPos(Vec3.atCenterOf(this.worldPosition));
        livingEntity.setSilent(true);
        if (livingEntity instanceof Mob mob) {
            mob.setNoAi(true);
        }

        var lootTableId = livingEntity.getLootTable();
        if (lootTableId == null) {
            entity.discard();
            return List.of();
        }

        Player fakePlayer = Platform.getFakePlayer(serverLevel, null);
        fakePlayer.moveTo(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 1.0,
                this.worldPosition.getZ() + 0.5,
                fakePlayer.getYRot(),
                fakePlayer.getXRot()
        );

        ArrayList<ItemStack> drops = new ArrayList<>();
        for (int roll = 0; roll < getBiologyLootRollsPerCycle(); roll++) {
            LootParams.Builder builder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.THIS_ENTITY, livingEntity)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                    .withParameter(LootContextParams.DAMAGE_SOURCE, serverLevel.damageSources().playerAttack(fakePlayer))
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fakePlayer)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, fakePlayer)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, fakePlayer);

            drops.addAll(serverLevel.getServer().reloadableRegistries().getLootTable(lootTableId)
                    .getRandomItems(builder.create(LootContextParamSets.ENTITY))
                    .stream()
                    .filter(stack -> !stack.isEmpty())
                    .toList());
        }
        entity.discard();
        return drops;
    }

    private List<ItemStack> generateOreLoot(ServerLevel serverLevel, ItemStack carrier) {
        ResourceLocation oreItemId = OreDataCarrierData.getOreItemId(carrier);
        if (oreItemId == null) {
            return List.of();
        }

        Item oreItem = BuiltInRegistries.ITEM.getOptional(oreItemId).orElse(null);
        if (oreItem == null) {
            return List.of();
        }

        return List.of(new ItemStack(oreItem));
    }

    private boolean isHiddenBufferFull() {
        for (int i = 0; i < this.hiddenBuffer.size(); i++) {
            ItemStack stack = this.hiddenBuffer.getStackInSlot(i);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), this.hiddenBuffer.getSlotLimit(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean canHiddenBufferAcceptAll(List<ItemStack> stacks) {
        ItemStack[] simulated = new ItemStack[this.hiddenBuffer.size()];
        for (int i = 0; i < this.hiddenBuffer.size(); i++) {
            simulated[i] = this.hiddenBuffer.getStackInSlot(i).copy();
        }

        for (ItemStack source : stacks) {
            if (source.isEmpty()) {
                continue;
            }

            ItemStack remaining = source.copy();

            for (int i = 0; i < simulated.length; i++) {
                ItemStack existing = simulated[i];
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                    continue;
                }

                int limit = Math.min(existing.getMaxStackSize(), this.hiddenBuffer.getSlotLimit(i));
                int free = limit - existing.getCount();
                if (free <= 0) {
                    continue;
                }

                int moved = Math.min(free, remaining.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) {
                    break;
                }
            }

            if (!remaining.isEmpty()) {
                for (int i = 0; i < simulated.length; i++) {
                    if (!simulated[i].isEmpty()) {
                        continue;
                    }

                    int limit = Math.min(remaining.getMaxStackSize(), this.hiddenBuffer.getSlotLimit(i));
                    ItemStack inserted = remaining.copy();
                    inserted.setCount(Math.min(inserted.getCount(), limit));
                    simulated[i] = inserted;
                    remaining.shrink(inserted.getCount());
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }

            if (!remaining.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private void insertAllIntoHiddenBuffer(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remaining = this.hiddenBuffer.addItems(stack);
            if (!remaining.isEmpty()) {
                throw new IllegalStateException("Hidden buffer overflowed after acceptance check");
            }
        }
    }

    private boolean isReceivingRedstonePower() {
        return this.level != null && this.level.hasNeighborSignal(this.worldPosition);
    }

    public int getActiveRows() {
        int capacityCards = Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD));
        return Math.min(MAX_ACTIVE_ROWS, BASE_ACTIVE_ROWS + capacityCards);
    }

    public int getActiveSlotCount() {
        return getActiveRows() * 9;
    }

    public int getActiveCarrierCount() {
        int count = 0;
        for (int i = 0; i < getActiveSlotCount(); i++) {
            if (hasRecordedData(this.storage.getStackInSlot(i))) {
                count++;
            }
        }
        return count;
    }

    public int getWorkIntervalSeconds() {
        int speedCards = Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        int effectiveSpeedCards = Math.min(speedCards, BASE_WORK_INTERVAL_SECONDS - MIN_WORK_INTERVAL_SECONDS);
        return BASE_WORK_INTERVAL_SECONDS - effectiveSpeedCards;
    }

    public int getBiologyLootRollsPerCycle() {
        return BASE_BIOLOGY_LOOT_ROLLS_PER_CYCLE + Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) * 4;
    }

    public int getOreOutputRollsPerCycle() {
        return BASE_ORE_OUTPUT_ROLLS_PER_CYCLE + Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) * 4;
    }

    private int computeWorkIntervalTicks() {
        return getWorkIntervalSeconds() * 20;
    }

    private boolean consumeDataFlowPerSecond() {
        int activeCarrierCount = getActiveCarrierCount();
        if (activeCarrierCount <= 0) {
            return true;
        }

        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return false;
        }

        var inventory = node.getGrid().getStorageService().getInventory();
        long required = activeCarrierCount * DATA_FLOW_PER_CARRIER_PER_SECOND;
        long simulated = inventory.extract(DataFlowKey.of(), required, Actionable.SIMULATE, IActionSource.ofMachine(this));
        if (simulated < required) {
            return false;
        }

        inventory.extract(DataFlowKey.of(), required, Actionable.MODULATE, IActionSource.ofMachine(this));
        return true;
    }
}
