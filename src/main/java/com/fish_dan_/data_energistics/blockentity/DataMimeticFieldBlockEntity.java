package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.util.CropDataCarrierData;
import com.fish_dan_.data_energistics.util.OreDataCarrierData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataMimeticFieldBlockEntity extends AENetworkedPoweredBlockEntity implements IUpgradeableObject {

    public static final int BASE_ACTIVE_SLOTS = 4;
    public static final int EXTRA_SLOTS_PER_CAPACITY_CARD = 4;
    public static final int MAX_CAPACITY_CARDS = 1;
    public static final int SLOT_COUNT = BASE_ACTIVE_SLOTS + EXTRA_SLOTS_PER_CAPACITY_CARD * MAX_CAPACITY_CARDS;
    public static final double ENERGY_CACHE_CAPACITY = 1600.0;
    public static final long KEY_INPUT_CAPACITY = 64_000L;
    private static final int HIDDEN_BUFFER_SLOTS = 64;
    private static final double POWER_PER_ACTIVE_CARRIER = 500.0;
    private static final long DATA_FLOW_PER_WORK_CYCLE = 150L;
    private static final int BASE_WORK_INTERVAL_TICKS = 200;
    private static final int BASE_BIOLOGY_LOOT_ROLLS_PER_CYCLE = 48;
    private static final int BASE_ORE_OUTPUT_ROLLS_PER_CYCLE = 48;
    private static final int HIDDEN_BUFFER_FLUSH_INTERVAL_TICKS = 5;
    private static final int UPGRADE_SLOTS = 6;
    private static final long DATA_FLOW_PER_CONVERTED_ITEM = 1L;
    private static final long DATA_FLOW_PER_CONVERTED_EXPERIENCE = 1L;
    private static final String UPGRADES_TAG = "upgrades";
    private static final String REDSTONE_CONTROLLED_TAG = "redstone_controlled";
    private static final String AUTO_PULL_KEY_INPUT_TAG = "auto_pull_key_input";
    private static final String DROP_ROUTING_MODE_TAG = "drop_routing_mode";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final String KEY_INPUT_TAG = "key_input";
    private static final String WORK_TICKS_TAG = "work_ticks";
    private static final String HIDDEN_BUFFER_TAG = "hidden_buffer";

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, SLOT_COUNT);
    private final AppEngInternalInventory hiddenBuffer = new AppEngInternalInventory(this, HIDDEN_BUFFER_SLOTS);
    private final GenericStackInv keyMenuInventory = createKeyMenuInventory();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DATA_MIMETIC_FIELD.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private boolean redstoneControlled;
    private boolean autoPullKeyInput;
    private DataExtractorDropRoutingMode dropRoutingMode = DataExtractorDropRoutingMode.OFF;
    private int workTicks;
    private int hiddenBufferFlushCooldown;
    private boolean powerUsageDirty = true;
    private int cachedActiveCarrierCount;
    private int clientActiveSlotCount = BASE_ACTIVE_SLOTS;
    private final Set<Direction> outputSides = EnumSet.allOf(Direction.class);
    private boolean syncingKeyMenu;
    private GenericStack keyInputStack;
    private int cachedSpeedCardCount = -1;
    private List<IItemHandler> cachedAdjacentHandlers;
    private boolean adjacentHandlersDirty = true;
    private Player cachedFakePlayer;
    private final Map<Block, BlockState> cachedCropLootStates = new HashMap<>();

    public DataMimeticFieldBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_MIMETIC_FIELD.get())
                .setExposedOnSides(getCableExposedSides(blockState))
                .setIdlePowerUsage(0.0);
        this.setInternalMaxPower(ENERGY_CACHE_CAPACITY);
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.storage.setMaxStackSize(i, 1);
        }
        this.storage.setFilter(new CarrierOnlyFilter());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        if (!isCableSideExposed(dir)) {
            return AECableType.NONE;
        }
        return AECableType.COVERED;
    }

    private boolean isCableSideExposed(Direction dir) {
        Direction front = this.getBlockState().getValue(DataMimeticFieldBlock.FACING);
        return dir != Direction.UP && dir != front;
    }

    private static Set<Direction> getCableExposedSides(BlockState blockState) {
        Direction front = blockState.getValue(DataMimeticFieldBlock.FACING);
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        sides.remove(front);
        return sides;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.storage;
    }

    public ConfigMenuInventory getKeyMenuInventory() {
        return this.keyMenuInventory.createMenuWrapper();
    }

    public @Nullable GenericStack getKeyInputStack() {
        return this.keyInputStack;
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
        this.keyInputStack = data.contains(KEY_INPUT_TAG) ? GenericStack.readTag(registries, data.getCompound(KEY_INPUT_TAG)) : null;
        this.redstoneControlled = data.getBoolean(REDSTONE_CONTROLLED_TAG);
        this.autoPullKeyInput = data.getBoolean(AUTO_PULL_KEY_INPUT_TAG);
        this.dropRoutingMode = DataExtractorDropRoutingMode.fromOrdinal(data.getInt(DROP_ROUTING_MODE_TAG));
        this.outputSides.clear();
        if (data.contains(OUTPUT_SIDES_TAG)) {
            for (Tag name : data.getList(OUTPUT_SIDES_TAG, Tag.TAG_STRING)) {
                Direction side = Direction.byName(name.getAsString());
                if (side != null) {
                    this.outputSides.add(side);
                }
            }
        } else {
            this.outputSides.addAll(EnumSet.allOf(Direction.class));
        }
        this.workTicks = Math.max(0, data.getInt(WORK_TICKS_TAG));
        this.hiddenBufferFlushCooldown = 0;
        this.powerUsageDirty = true;
        this.cachedActiveCarrierCount = 0;
        syncKeyMenuFromStack();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        this.hiddenBuffer.writeToNBT(data, HIDDEN_BUFFER_TAG, registries);
        if (this.keyInputStack != null && this.keyInputStack.amount() > 0) {
            data.put(KEY_INPUT_TAG, GenericStack.writeTag(registries, this.keyInputStack));
        }
        data.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        data.putBoolean(AUTO_PULL_KEY_INPUT_TAG, this.autoPullKeyInput);
        data.putInt(DROP_ROUTING_MODE_TAG, this.dropRoutingMode.ordinal());
        ListTag sides = new ListTag();
        for (Direction side : this.outputSides) {
            sides.add(StringTag.valueOf(side.getName()));
        }
        data.put(OUTPUT_SIDES_TAG, sides);
        data.putInt(WORK_TICKS_TAG, this.workTicks);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        int activeSlotCount = getActiveSlotCount();
        data.writeVarInt(activeSlotCount);
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            data.writeBoolean(!stack.isEmpty());
            if (!stack.isEmpty()) {
                ItemStack.STREAM_CODEC.encode(data, stack);
            }
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        int activeSlotCount = Math.min(data.readVarInt(), SLOT_COUNT);
        int currentActiveSlotCount = this.clientActiveSlotCount;
        if (activeSlotCount != currentActiveSlotCount) {
            this.clientActiveSlotCount = activeSlotCount;
            changed = true;
        }
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack syncedStack = data.readBoolean() ? ItemStack.STREAM_CODEC.decode(data) : ItemStack.EMPTY;
            ItemStack existingStack = this.storage.getStackInSlot(i);
            if (!ItemStack.matches(existingStack, syncedStack)) {
                this.storage.setItemDirect(i, syncedStack);
                changed = true;
            }
        }
        for (int i = activeSlotCount; i < SLOT_COUNT; i++) {
            ItemStack existingStack = this.storage.getStackInSlot(i);
            if (!existingStack.isEmpty()) {
                this.storage.setItemDirect(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        return changed;
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
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        this.adjacentHandlersDirty = true;
        updatePowerUsageIfNeeded();
        tickHiddenBufferFlush();
        refillEnergyCache();
        if (this.autoPullKeyInput) {
            refillKeyFromNetwork();
        }
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        if (!hasOverflowDestructionCard() && isHiddenBufferFull()) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        if (getActiveCarrierCount() <= 0) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        if (!hasEnoughDataFlowForWorkCycle()) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        this.workTicks++;
        if (this.workTicks < computeWorkIntervalTicks()) {
            updateOnlineState();
            return;
        }
        if (!consumeDataFlowPerWorkCycle()) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        this.workTicks = 0;
        performBiologyMimeticWork();
        performOreMimeticWork();
        performCropMimeticWork();
        updateOnlineState();
    }

    @Override
    public void onReady() {
        super.onReady();
        updatePowerUsage();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean isRedstoneControlled() {
        return this.redstoneControlled;
    }

    public boolean isAutoPullKeyInput() {
        return this.autoPullKeyInput;
    }

    public DataExtractorDropRoutingMode getDropRoutingMode() {
        return this.dropRoutingMode;
    }

    public Set<Direction> getOutputSides() {
        if (this.outputSides.isEmpty()) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.copyOf(this.outputSides);
    }

    public boolean setRedstoneControlled(boolean enabled) {
        if (this.redstoneControlled == enabled) {
            return this.redstoneControlled;
        }

        this.redstoneControlled = enabled;
        this.setChanged();
        markPowerUsageDirty();
        updatePowerUsage();
        this.markForClientUpdate();
        return this.redstoneControlled;
    }

    public boolean setAutoPullKeyInput(boolean enabled) {
        if (this.autoPullKeyInput == enabled) {
            return this.autoPullKeyInput;
        }

        this.autoPullKeyInput = enabled;
        this.setChanged();
        this.markForClientUpdate();
        return this.autoPullKeyInput;
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

    public void setOutputSideEnabled(Direction side, boolean enabled) {
        boolean changed = enabled ? this.outputSides.add(side) : this.outputSides.remove(side);
        if (!changed) {
            return;
        }

        this.adjacentHandlersDirty = true;
        this.setChanged();
        this.markForClientUpdate();
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
        this.keyInputStack = null;
        syncKeyMenuFromStack();
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
            return stack.is(ModItems.MOB_DATA_CARRIER.get()) || stack.is(ModItems.ORE_DATA_CARRIER.get()) || stack.is(ModItems.CROP_DATA_CARRIER.get());
        }
    }

    private void onUpgradesChanged() {
        this.cachedSpeedCardCount = -1;
        markPowerUsageDirty();
        this.saveChanges();
        this.markForClientUpdate();
    }

    public List<ItemStack> extractOverflowCarriers() {
        int activeSlotCount = BASE_ACTIVE_SLOTS + getInstalledCapacityCardCount() * EXTRA_SLOTS_PER_CAPACITY_CARD;
        List<ItemStack> overflow = new ArrayList<>();
        boolean changed = false;

        for (int i = activeSlotCount; i < SLOT_COUNT; i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            overflow.add(stack.copy());
            this.storage.setItemDirect(i, ItemStack.EMPTY);
            changed = true;
        }

        if (changed) {
            this.saveChanges();
            this.markForClientUpdate();
        }

        return overflow;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        markPowerUsageDirty();
        if (inv == this.hiddenBuffer) {
            this.hiddenBufferFlushCooldown = 0;
        }
        if (inv == this.storage) {
            this.saveChanges();
            this.markForClientUpdate();
        }
        updatePowerUsageIfNeeded();
    }

    private void updatePowerUsage() {
        this.cachedActiveCarrierCount = countActiveCarriers();
        this.powerUsageDirty = false;
        this.getMainNode().setIdlePowerUsage(computeIdlePowerUsage(this.cachedActiveCarrierCount));
    }

    private void updatePowerUsageIfNeeded() {
        if (this.powerUsageDirty) {
            updatePowerUsage();
        }
    }

    private double computeIdlePowerUsage(int activeCarrierCount) {
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
            return 0.0;
        }

        return activeCarrierCount * POWER_PER_ACTIVE_CARRIER;
    }

    private int countActiveCarriers() {
        int count = 0;
        int activeSlotCount = getActiveSlotCount();
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (hasRecordedData(stack)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRecordedData(ItemStack stack) {
        return !stack.isEmpty() && (BiologyDataCarrierData.isComplete(stack) || OreDataCarrierData.isComplete(stack) || CropDataCarrierData.isComplete(stack));
    }

    private void performBiologyMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        GeneratedLoot generatedLoot = GeneratedLoot.empty();
        int activeSlotCount = getActiveSlotCount();
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack carrier = this.storage.getStackInSlot(i);
            if (BiologyDataCarrierData.isComplete(carrier)) {
                generatedLoot = generatedLoot.merge(generateBiologyLoot(serverLevel, carrier));
            }
        }

        submitGeneratedLoot(generatedLoot);
    }

    private void performOreMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> generated = new ArrayList<>();
        int activeSlotCount = getActiveSlotCount();
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack carrier = this.storage.getStackInSlot(i);
            if (!OreDataCarrierData.isComplete(carrier)) {
                continue;
            }

            for (int roll = 0; roll < getOreOutputRollsPerCycle(); roll++) {
                generated.addAll(generateOreLoot(serverLevel, carrier));
            }
        }

        submitGeneratedLoot(generated);
    }

    private void performCropMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ItemStack> generated = new ArrayList<>();
        int activeSlotCount = getActiveSlotCount();
        for (int i = 0; i < activeSlotCount; i++) {
            ItemStack carrier = this.storage.getStackInSlot(i);
            if (!CropDataCarrierData.isComplete(carrier)) {
                continue;
            }

            for (int roll = 0; roll < getOreOutputRollsPerCycle(); roll++) {
                generated.addAll(generateCropLoot(serverLevel, carrier));
            }
        }

        submitGeneratedLoot(generated);
    }

    private void flushHiddenBuffer() {
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.OFF || this.level == null) {
            return;
        }
        if (isHiddenBufferEmpty()) {
            return;
        }
        flushHiddenBuffer(getAdjacentItemHandlers(), getConnectedItemNetwork());
    }

    private void tickHiddenBufferFlush() {
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
            this.hiddenBufferFlushCooldown = 0;
            return;
        }

        if (this.hiddenBufferFlushCooldown > 0) {
            this.hiddenBufferFlushCooldown--;
            return;
        }

        this.hiddenBufferFlushCooldown = HIDDEN_BUFFER_FLUSH_INTERVAL_TICKS - 1;
        flushHiddenBuffer();
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

    private boolean isHiddenBufferEmpty() {
        for (int i = 0; i < this.hiddenBuffer.size(); i++) {
            if (!this.hiddenBuffer.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack routeGeneratedItem(ItemStack stack, List<IItemHandler> adjacentHandlers, @Nullable MEStorage networkStorage) {
        ItemStack remaining = stack.copy();
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            return insertIntoNetwork(remaining, networkStorage);
        }

        return insertIntoAdjacentContainers(remaining, adjacentHandlers);
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
        if (!this.adjacentHandlersDirty && this.cachedAdjacentHandlers != null) {
            return this.cachedAdjacentHandlers;
        }
        this.adjacentHandlersDirty = false;
        if (this.level == null) {
            this.cachedAdjacentHandlers = List.of();
            return List.of();
        }

        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : this.outputSides) {
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
                    direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        this.cachedAdjacentHandlers = handlers.isEmpty() ? List.of() : List.copyOf(handlers);
        return this.cachedAdjacentHandlers;
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

    private GeneratedLoot generateBiologyLoot(ServerLevel serverLevel, ItemStack carrier) {
        ResourceLocation entityId = BiologyDataCarrierData.getEntityTypeId(carrier);
        if (entityId == null) {
            return GeneratedLoot.empty();
        }

        List<ItemStack> configuredOutputs = DataExtractorRuleTable.getConfiguredOutputs(DataExtractorRuleTable.DataType.MOB, entityId);
        if (!configuredOutputs.isEmpty() && !hasOverflowDestructionCard()) {
            return new GeneratedLoot(configuredOutputs, 0);
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            return new GeneratedLoot(configuredOutputs, 0);
        }

        Entity entity = entityType.create(serverLevel);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return new GeneratedLoot(configuredOutputs, 0);
        }

        livingEntity.setPos(Vec3.atCenterOf(this.worldPosition));
        livingEntity.setSilent(true);
        if (livingEntity instanceof Mob mob) {
            mob.finalizeSpawn(
                    serverLevel,
                    serverLevel.getCurrentDifficultyAt(this.worldPosition),
                    MobSpawnType.COMMAND,
                    null);
            mob.setNoAi(true);
        }

        Player fakePlayer = getFakePlayer(serverLevel);
        fakePlayer.moveTo(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 1.0,
                this.worldPosition.getZ() + 0.5,
                fakePlayer.getYRot(),
                fakePlayer.getXRot());

        List<LivingEntity> simulatedEntities = collectSimulatedLivingEntities(livingEntity);
        ArrayList<ItemStack> drops = new ArrayList<>();
        long experience = 0L;
        for (int roll = 0; roll < getBiologyLootRollsPerCycle(); roll++) {
            if (configuredOutputs.isEmpty()) {
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    GeneratedLoot generatedLoot = simulateEntityDrops(serverLevel, simulatedEntity, fakePlayer);
                    drops.addAll(generatedLoot.stacks());
                    experience = saturatedAdd(experience, generatedLoot.experience());
                }
            } else {
                drops.addAll(configuredOutputs);
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    GeneratedLoot generatedLoot = simulateEntityExperience(serverLevel, simulatedEntity, fakePlayer);
                    experience = saturatedAdd(experience, generatedLoot.experience());
                }
            }
        }
        for (LivingEntity simulatedEntity : simulatedEntities) {
            simulatedEntity.discard();
        }
        return new GeneratedLoot(drops, experience);
    }

    private List<LivingEntity> collectSimulatedLivingEntities(LivingEntity rootEntity) {
        LinkedHashSet<LivingEntity> result = new LinkedHashSet<>();
        collectSimulatedLivingEntities(rootEntity, result, new HashSet<>());
        return List.copyOf(result);
    }

    private void collectSimulatedLivingEntities(@Nullable Entity entity, Set<LivingEntity> result, Set<Entity> visited) {
        if (entity == null || !visited.add(entity)) {
            return;
        }

        if (entity instanceof LivingEntity livingEntity && result.add(livingEntity)) {
            livingEntity.setSilent(true);
            if (livingEntity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }

        collectSimulatedLivingEntities(entity.getVehicle(), result, visited);
        for (Entity passenger : entity.getPassengers()) {
            collectSimulatedLivingEntities(passenger, result, visited);
        }
    }

    private GeneratedLoot simulateEntityDrops(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
        var damageSource = serverLevel.damageSources().playerAttack(fakePlayer);
        ArrayList<ItemStack> drops = new ArrayList<>(generateEntityLootTableDrops(serverLevel, livingEntity, fakePlayer, damageSource));
        collectEquipmentDrops(livingEntity, drops);
        int experience = Math.max(0, livingEntity.getExperienceReward(serverLevel, fakePlayer));
        return new GeneratedLoot(drops, experience);
    }

    private GeneratedLoot simulateEntityExperience(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
        int experience = Math.max(0, livingEntity.getExperienceReward(serverLevel, fakePlayer));
        return new GeneratedLoot(List.of(), experience);
    }

    private List<ItemStack> generateEntityLootTableDrops(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer,
                                                         net.minecraft.world.damagesource.DamageSource damageSource) {
        LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, livingEntity)
                .withParameter(LootContextParams.ORIGIN, livingEntity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, fakePlayer)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, fakePlayer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, fakePlayer);

        return serverLevel.getServer()
                .reloadableRegistries()
                .getLootTable(livingEntity.getLootTable())
                .getRandomItems(builder.create(LootContextParamSets.ENTITY), livingEntity.getLootTableSeed());
    }

    private void collectEquipmentDrops(LivingEntity livingEntity, List<ItemStack> drops) {
        if (!(livingEntity instanceof Mob mob)) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack copy = stack.copy();
            if (!copy.isEmpty()) {
                drops.add(copy);
            }
        }
    }

    private List<ItemStack> generateOreLoot(ServerLevel serverLevel, ItemStack carrier) {
        ResourceLocation oreItemId = OreDataCarrierData.getOreItemId(carrier);
        if (oreItemId == null) {
            return List.of();
        }

        List<ItemStack> configuredOutputs = DataExtractorRuleTable.getConfiguredOutputs(DataExtractorRuleTable.DataType.ORE, oreItemId);
        if (!configuredOutputs.isEmpty()) {
            return configuredOutputs;
        }

        Item oreItem = BuiltInRegistries.ITEM.getOptional(oreItemId).orElse(null);
        if (oreItem == null) {
            return List.of();
        }

        return List.of(new ItemStack(oreItem));
    }

    private List<ItemStack> generateCropLoot(ServerLevel serverLevel, ItemStack carrier) {
        ResourceLocation cropItemId = CropDataCarrierData.getCropItemId(carrier);
        if (cropItemId != null) {
            List<ItemStack> configuredOutputs = DataExtractorRuleTable.getConfiguredOutputs(DataExtractorRuleTable.DataType.CROP, cropItemId);
            if (!configuredOutputs.isEmpty()) {
                return configuredOutputs;
            }
        }

        ResourceLocation lootTableId = CropDataCarrierData.getLootTableId(carrier);
        if (lootTableId != null) {
            List<ItemStack> treeLoot = generateConfiguredLootTableDrops(serverLevel, lootTableId);
            if (!treeLoot.isEmpty()) {
                return treeLoot;
            }
        }

        ResourceLocation sourceBlockId = CropDataCarrierData.getSourceBlockId(carrier);
        if (sourceBlockId != null) {
            Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(sourceBlockId).orElse(null);
            if (sourceBlock != null) {
                List<ItemStack> lootTableDrops = generateBlockLootDrops(serverLevel, getRecordedCropLootState(sourceBlock));
                if (!lootTableDrops.isEmpty()) {
                    return lootTableDrops;
                }
            }
        }

        if (cropItemId == null) {
            return List.of();
        }

        Item cropItem = BuiltInRegistries.ITEM.getOptional(cropItemId).orElse(null);
        if (cropItem == null) {
            return List.of();
        }

        return List.of(new ItemStack(cropItem));
    }

    private List<ItemStack> generateConfiguredLootTableDrops(ServerLevel serverLevel, ResourceLocation lootTableId) {
        LootTable lootTable = serverLevel.getServer()
                .reloadableRegistries()
                .getLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, lootTableId));
        LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition));
        return lootTable.getRandomItems(builder.create(LootContextParamSets.CHEST)).stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    private List<ItemStack> generateBlockLootDrops(ServerLevel serverLevel, BlockState state) {
        if (state == null || state.isAir()) {
            return List.of();
        }

        return Block.getDrops(state, serverLevel, this.worldPosition, null).stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    private BlockState getRecordedCropLootState(Block cropBlock) {
        BlockState cached = this.cachedCropLootStates.get(cropBlock);
        if (cached != null) {
            return cached;
        }
        BlockState result = computeCropLootState(cropBlock);
        this.cachedCropLootStates.put(cropBlock, result);
        return result;
    }

    private static BlockState computeCropLootState(Block cropBlock) {
        if (cropBlock == Blocks.WHEAT) {
            return getMaxAgeCropState(Blocks.WHEAT);
        }
        if (cropBlock == Blocks.CARROTS) {
            return getMaxAgeCropState(Blocks.CARROTS);
        }
        if (cropBlock == Blocks.POTATOES) {
            return getMaxAgeCropState(Blocks.POTATOES);
        }
        if (cropBlock == Blocks.BEETROOTS) {
            return getMaxAgeCropState(Blocks.BEETROOTS);
        }
        if (cropBlock == Blocks.NETHER_WART) {
            return Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE);
        }
        if (cropBlock instanceof CropBlock crop) {
            return crop.getStateForAge(crop.getMaxAge());
        }
        if (cropBlock instanceof StemBlock) {
            return cropBlock.defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE);
        }
        if (cropBlock instanceof CocoaBlock) {
            return cropBlock.defaultBlockState().setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE);
        }
        if (cropBlock instanceof SweetBerryBushBlock) {
            return cropBlock.defaultBlockState().setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE);
        }
        if (hasAgeProperty(cropBlock)) {
            return applyMaxAge(cropBlock);
        }
        return cropBlock.defaultBlockState();
    }

    private static boolean hasAgeProperty(Block block) {
        return block.defaultBlockState().getProperties().stream()
                .anyMatch(prop -> prop.getName().equals("age"));
    }

    @SuppressWarnings("unchecked")
    private static BlockState applyMaxAge(Block block) {
        BlockState state = block.defaultBlockState();
        for (var prop : state.getProperties()) {
            if (prop.getName().equals("age") && prop instanceof IntegerProperty intProp) {
                int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                return state.setValue(intProp, max);
            }
        }
        return state;
    }

    private static BlockState getMaxAgeCropState(Block block) {
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.getStateForAge(cropBlock.getMaxAge());
        }
        return block.defaultBlockState();
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

    private void submitGeneratedLoot(List<ItemStack> generated) {
        submitGeneratedLoot(new GeneratedLoot(generated, 0));
    }

    private void submitGeneratedLoot(GeneratedLoot generated) {
        if (hasOverflowDestructionCard()) {
            convertGeneratedLootToDataFlow(generated);
            return;
        }

        submitGeneratedItems(generated.stacks());
    }

    private void submitGeneratedItems(List<ItemStack> generated) {
        if (generated.isEmpty() || !canHiddenBufferAcceptAll(generated)) {
            return;
        }

        insertAllIntoHiddenBuffer(generated);
    }

    private void convertGeneratedLootToDataFlow(GeneratedLoot generated) {
        long amount = saturatedAdd(
                getConvertedItemDataFlow(generated.stacks()),
                saturatedMultiply(Math.max(0L, generated.experience()), DATA_FLOW_PER_CONVERTED_EXPERIENCE));
        if (amount <= 0) {
            return;
        }

        long inserted = insertDataFlowIntoNetwork(amount);
        long remaining = amount - inserted;
        if (remaining > 0) {
            insertDataFlowIntoKeyInput(remaining);
        }
    }

    private long getConvertedItemDataFlow(List<ItemStack> stacks) {
        long amount = 0L;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            amount = saturatedAdd(amount, saturatedMultiply(stack.getCount(), DATA_FLOW_PER_CONVERTED_ITEM));
        }
        return amount;
    }

    private long insertDataFlowIntoNetwork(long amount) {
        if (amount <= 0) {
            return 0L;
        }

        MEStorage networkStorage = getConnectedItemNetwork();
        if (networkStorage == null) {
            return 0L;
        }

        return networkStorage.insert(DataFlowKey.of(), amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private long insertDataFlowIntoKeyInput(long amount) {
        if (amount <= 0) {
            return 0L;
        }

        long stored = this.keyInputStack != null && this.keyInputStack.what() instanceof DataFlowKey ? this.keyInputStack.amount() : 0L;
        long accepted = Math.min(amount, KEY_INPUT_CAPACITY - stored);
        if (accepted <= 0) {
            return 0L;
        }

        this.keyInputStack = new GenericStack(DataFlowKey.of(), stored + accepted);
        syncKeyMenuFromStack();
        setChanged();
        markForClientUpdate();
        return accepted;
    }

    private boolean hasOverflowDestructionCard() {
        return this.upgrades.getInstalledUpgrades(AEItems.VOID_CARD) > 0;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
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

    public int getInstalledCapacityCardCount() {
        return Math.min(MAX_CAPACITY_CARDS, Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD)));
    }

    public int getActiveSlotCount() {
        if (this.level != null && this.level.isClientSide()) {
            return this.clientActiveSlotCount;
        }
        return BASE_ACTIVE_SLOTS + getInstalledCapacityCardCount() * EXTRA_SLOTS_PER_CAPACITY_CARD;
    }

    public int getActiveCarrierCount() {
        updatePowerUsageIfNeeded();
        return this.cachedActiveCarrierCount;
    }

    public int getWorkIntervalSeconds() {
        return Math.max(1, (computeWorkIntervalTicks() + 19) / 20);
    }

    public int getWorkProgress() {
        return this.workTicks;
    }

    public int getWorkMaxProgress() {
        return computeWorkIntervalTicks();
    }

    public int getBiologyLootRollsPerCycle() {
        return BASE_BIOLOGY_LOOT_ROLLS_PER_CYCLE + getInstalledSpeedCardCount() * 16;
    }

    public int getOreOutputRollsPerCycle() {
        return BASE_ORE_OUTPUT_ROLLS_PER_CYCLE + getInstalledSpeedCardCount() * 16;
    }

    private int computeWorkIntervalTicks() {
        return Math.max(1, BASE_WORK_INTERVAL_TICKS - getInstalledSpeedCardCount() * 40);
    }

    private boolean hasEnoughDataFlowForWorkCycle() {
        int activeCarrierCount = getActiveCarrierCount();
        if (activeCarrierCount <= 0) {
            return true;
        }

        long required = getDataFlowCostPerWorkCycle(activeCarrierCount);
        return this.keyInputStack != null && this.keyInputStack.what() instanceof DataFlowKey && this.keyInputStack.amount() >= required;
    }

    private boolean consumeDataFlowPerWorkCycle() {
        int activeCarrierCount = getActiveCarrierCount();
        if (activeCarrierCount <= 0) {
            return true;
        }

        long required = getDataFlowCostPerWorkCycle(activeCarrierCount);
        if (this.keyInputStack == null || !(this.keyInputStack.what() instanceof DataFlowKey) || this.keyInputStack.amount() < required) {
            return false;
        }

        long remaining = this.keyInputStack.amount() - required;
        this.keyInputStack = remaining > 0 ? new GenericStack(DataFlowKey.of(), remaining) : null;
        syncKeyMenuFromStack();
        setChanged();
        markForClientUpdate();
        return true;
    }

    private int getInstalledSpeedCardCount() {
        if (this.cachedSpeedCardCount < 0) {
            this.cachedSpeedCardCount = Math.max(0, this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        }
        return this.cachedSpeedCardCount;
    }

    private long getDataFlowCostPerWorkCycle(int activeCarrierCount) {
        return (long) activeCarrierCount * (DATA_FLOW_PER_WORK_CYCLE + getInstalledSpeedCardCount() * 50L);
    }

    private void refillEnergyCache() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 0.0001D) {
            return;
        }

        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void refillKeyFromNetwork() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        long stored = this.keyInputStack != null && this.keyInputStack.what() instanceof DataFlowKey ? this.keyInputStack.amount() : 0L;
        long missing = KEY_INPUT_CAPACITY - stored;
        if (missing <= 0) {
            return;
        }

        var inventory = node.getGrid().getStorageService().getInventory();
        if (inventory == null) {
            return;
        }

        long extracted = inventory.extract(DataFlowKey.of(), missing, Actionable.MODULATE, IActionSource.ofMachine(this));
        if (extracted <= 0) {
            return;
        }

        long updated = stored + extracted;
        this.keyInputStack = new GenericStack(DataFlowKey.of(), Math.min(KEY_INPUT_CAPACITY, updated));
        syncKeyMenuFromStack();
        setChanged();
        markForClientUpdate();
    }

    private GenericStackInv createKeyMenuInventory() {
        var inv = new GenericStackInv(java.util.Set.of(DataFlowKeyType.TYPE), this::syncStackFromKeyMenu, GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> what instanceof DataFlowKey);
            }
        };
        inv.setCapacity(DataFlowKeyType.TYPE, KEY_INPUT_CAPACITY);
        return inv;
    }

    private void syncKeyMenuFromStack() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            this.keyMenuInventory.setStack(0, this.keyInputStack);
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private void syncStackFromKeyMenu() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            var stack = this.keyMenuInventory.getStack(0);
            if (stack == null || !(stack.what() instanceof DataFlowKey) || stack.amount() <= 0) {
                this.keyInputStack = null;
            } else {
                this.keyInputStack = new GenericStack(DataFlowKey.of(), Math.min(KEY_INPUT_CAPACITY, stack.amount()));
            }
            saveChanges();
            markForClientUpdate();
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private void updateOnlineState() {
        updateBlockState(isOnline());
    }

    private void updateBlockState(boolean online) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataMimeticFieldBlock)) {
            return;
        }

        if (state.hasProperty(DataMimeticFieldBlock.LIT) && state.getValue(DataMimeticFieldBlock.LIT) != online) {
            this.level.setBlock(this.worldPosition, state.setValue(DataMimeticFieldBlock.LIT, online), 3);
        }
    }

    private Player getFakePlayer(ServerLevel serverLevel) {
        if (this.cachedFakePlayer == null) {
            this.cachedFakePlayer = Platform.getFakePlayer(serverLevel, null);
        }
        return this.cachedFakePlayer;
    }

    private void markPowerUsageDirty() {
        this.powerUsageDirty = true;
    }

    private void resetWorkProgress() {
        if (this.workTicks != 0) {
            this.workTicks = 0;
            setChanged();
            markForClientUpdate();
        }
    }

    private record GeneratedLoot(List<ItemStack> stacks, long experience) {

        private static GeneratedLoot empty() {
            return new GeneratedLoot(List.of(), 0L);
        }

        private GeneratedLoot merge(GeneratedLoot other) {
            if (this.stacks.isEmpty() && other.stacks.isEmpty()) {
                return new GeneratedLoot(List.of(), saturatedAdd(this.experience, other.experience));
            }

            ArrayList<ItemStack> mergedStacks = new ArrayList<>(this.stacks.size() + other.stacks.size());
            mergedStacks.addAll(this.stacks);
            mergedStacks.addAll(other.stacks);
            return new GeneratedLoot(mergedStacks, saturatedAdd(this.experience, other.experience));
        }
    }
}
