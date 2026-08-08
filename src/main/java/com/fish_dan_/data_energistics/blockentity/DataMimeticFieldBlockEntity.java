package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.util.CropDataCarrierData;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.util.OreDataCarrierData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
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
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    public static final long KEY_INPUT_CAPACITY = 640_000L;
    /** Maximum hidden output slots: each carrier reserves 64 slots. */
    private static final int HIDDEN_SLOTS_PER_CARRIER = 64;
    private static final int HIDDEN_BUFFER_SLOTS = SLOT_COUNT * HIDDEN_SLOTS_PER_CARRIER;
    private static final double POWER_PER_ACTIVE_CARRIER = 500.0;
    private static final long DATA_FLOW_PER_WORK_CYCLE = 3_200L;
    private static final int BASE_WORK_INTERVAL_TICKS = 200;
    private static final int OUTPUT_PER_SPEED_CARD = 800;
    private static final int OUTPUT_REDUCTION_DIVISOR = 7;
    private static final int OUTPUT_SCALE = 2;
    /** Number of real biology loot simulations sampled before scaling a work-cycle result. */
    private static final int BIOLOGY_LOOT_SAMPLE_ROLLS = 10;
    /** Server ticks before cached biology loot samples are refreshed. */
    private static final int BIOLOGY_LOOT_SAMPLE_REFRESH_INTERVAL_TICKS = 200;
    private static final int PENDING_OUTPUT_FLUSH_INTERVAL_TICKS = 5;
    private static final int PENDING_OUTPUT_OFFER_BUDGET = 64;
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
    private static final String PENDING_OUTPUTS_TAG = "pending_outputs";
    private static final ResourceLocation GOAT_ENTITY_ID = ResourceLocation.parse("minecraft:goat");
    private static final ResourceLocation ARMADILLO_ENTITY_ID = ResourceLocation.parse("minecraft:armadillo");
    private static final ResourceLocation TURTLE_ENTITY_ID = ResourceLocation.parse("minecraft:turtle");
    private static final double SIMULATED_DROP_CAPTURE_RADIUS_SQUARED = 16.0D;
    private static final ThreadLocal<SimulatedDeathDrops> SIMULATED_DEATH_DROPS = new ThreadLocal<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    /** Produces actual death drops without notifying unrelated real-world death listeners. */
    private static final BiologyDeathDropSimulation BIOLOGY_DEATH_DROP_SIMULATION = new BiologyDeathDropSimulationImpl();
    private static final List<ResourceKey<Instrument>> GOAT_HORN_INSTRUMENTS = List.of(
            Instruments.PONDER_GOAT_HORN,
            Instruments.SING_GOAT_HORN,
            Instruments.SEEK_GOAT_HORN,
            Instruments.FEEL_GOAT_HORN,
            Instruments.ADMIRE_GOAT_HORN,
            Instruments.CALL_GOAT_HORN,
            Instruments.YEARN_GOAT_HORN,
            Instruments.DREAM_GOAT_HORN);

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, SLOT_COUNT);
    private final AppEngInternalInventory hiddenBuffer = new AppEngInternalInventory(this, HIDDEN_BUFFER_SLOTS);
    private final MimeticPendingOutput pendingOutput = new MimeticPendingOutputImpl(this::setChanged);
    /** Keeps each component-sensitive item moving independently through bounded container slot attempts. */
    private final Map<AEItemKey, AdjacentContainerInsertionCursor> adjacentInsertionCursors = new HashMap<>();
    private final GenericStackInv keyMenuInventory = createKeyMenuInventory();
    @Getter
    private final GenericInternalInventory externalKeyInventory = new DataFlowExternalInventory();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DATA_MIMETIC_FIELD.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private boolean redstoneControlled;
    private boolean autoPullKeyInput;
    private DataExtractorDropRoutingMode dropRoutingMode = DataExtractorDropRoutingMode.OFF;
    private int workTicks;
    private int pendingOutputFlushCooldown;
    private boolean powerUsageDirty = true;
    private int cachedActiveCarrierCount;
    private int clientActiveSlotCount = BASE_ACTIVE_SLOTS;
    private final Set<Direction> outputSides = EnumSet.allOf(Direction.class);
    private boolean syncingKeyMenu;
    private GenericStack keyInputStack;
    private int cachedSpeedCardCount = -1;
    private AdjacentBlockCapabilityCache<IItemHandler> adjacentItemHandlers;
    private Player cachedFakePlayer;
    private final Map<Block, BlockState> cachedCropLootStates = new HashMap<>();
    /** Reuses sampled biology results between refreshes to keep entity simulation off the hot work-cycle path. */
    private final Map<BiologyLootSampleKey, BiologyLootSamples> biologyLootSamples = new HashMap<>();

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
        this.adjacentInsertionCursors.clear();
        this.pendingOutput.readFromNbt(registries, data.getList(PENDING_OUTPUTS_TAG, Tag.TAG_COMPOUND));
        this.hiddenBuffer.readFromNBT(data, HIDDEN_BUFFER_TAG, registries);
        List<ItemStack> legacyPending = new ArrayList<>();
        for (ItemStack stack : this.hiddenBuffer) {
            if (!stack.isEmpty()) {
                legacyPending.add(stack.copy());
            }
        }
        if (!legacyPending.isEmpty()) {
            this.pendingOutput.append(legacyPending);
            this.hiddenBuffer.clear();
        }
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
        this.pendingOutputFlushCooldown = 0;
        this.powerUsageDirty = true;
        this.cachedActiveCarrierCount = 0;
        syncKeyMenuFromStack();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        this.hiddenBuffer.writeToNBT(data, HIDDEN_BUFFER_TAG, registries);
        data.put(PENDING_OUTPUTS_TAG, this.pendingOutput.writeToNbt(registries));
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
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putBoolean(REDSTONE_CONTROLLED_TAG, this.redstoneControlled);
        settings.putBoolean(AUTO_PULL_KEY_INPUT_TAG, this.autoPullKeyInput);
        settings.putInt(DROP_ROUTING_MODE_TAG, this.dropRoutingMode.ordinal());
        settings.putInt(OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.outputSides));
        builder.set(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(ModDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
        if (settings != null) {
            applyMemoryCardSettings(settings);
        }
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

        updatePowerUsageIfNeeded();
        tickPendingOutputFlush();
        refillEnergyCache();
        if (this.autoPullKeyInput) {
            refillKeyFromNetwork();
        }
        if (!this.pendingOutput.isEmpty()) {
            resetWorkProgress();
            updateOnlineState();
            return;
        }
        if (this.redstoneControlled && !isReceivingRedstonePower()) {
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
        if (resolvedMode != DataExtractorDropRoutingMode.CONTAINER) {
            this.adjacentInsertionCursors.clear();
        }
        this.pendingOutputFlushCooldown = 0;
        this.setChanged();
        this.markForClientUpdate();
        return this.dropRoutingMode;
    }

    public void setOutputSideEnabled(Direction side, boolean enabled) {
        boolean changed = enabled ? this.outputSides.add(side) : this.outputSides.remove(side);
        if (!changed) {
            return;
        }

        this.setChanged();
        this.markForClientUpdate();
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        boolean changed = false;
        boolean powerUsageChanged = false;
        if (settings.contains(REDSTONE_CONTROLLED_TAG)) {
            boolean redstoneControlled = settings.getBoolean(REDSTONE_CONTROLLED_TAG);
            if (this.redstoneControlled != redstoneControlled) {
                this.redstoneControlled = redstoneControlled;
                changed = true;
                powerUsageChanged = true;
            }
        }
        if (settings.contains(AUTO_PULL_KEY_INPUT_TAG)) {
            boolean autoPullKeyInput = settings.getBoolean(AUTO_PULL_KEY_INPUT_TAG);
            if (this.autoPullKeyInput != autoPullKeyInput) {
                this.autoPullKeyInput = autoPullKeyInput;
                changed = true;
            }
        }
        if (settings.contains(DROP_ROUTING_MODE_TAG)) {
            DataExtractorDropRoutingMode dropRoutingMode = DataExtractorDropRoutingMode.fromOrdinal(settings.getInt(DROP_ROUTING_MODE_TAG));
            if (this.dropRoutingMode != dropRoutingMode) {
                this.dropRoutingMode = dropRoutingMode;
                if (dropRoutingMode != DataExtractorDropRoutingMode.CONTAINER) {
                    this.adjacentInsertionCursors.clear();
                }
                this.pendingOutputFlushCooldown = 0;
                changed = true;
            }
        }
        if (settings.contains(OUTPUT_SIDES_TAG) && MemoryCardSettingsHelper.replaceSides(this.outputSides, settings.getInt(OUTPUT_SIDES_TAG))) {
            changed = true;
        }
        if (powerUsageChanged) {
            markPowerUsageDirty();
            updatePowerUsage();
        }
        if (changed) {
            this.setChanged();
            this.markForClientUpdate();
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.hiddenBuffer) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        drops.addAll(this.pendingOutput.toItemStacks());
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
        this.pendingOutput.clear();
        this.adjacentInsertionCursors.clear();
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

    private void tickPendingOutputFlush() {
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.OFF || this.pendingOutput.isEmpty()) {
            this.pendingOutputFlushCooldown = 0;
            return;
        }

        if (this.pendingOutputFlushCooldown > 0) {
            this.pendingOutputFlushCooldown--;
            return;
        }

        this.pendingOutputFlushCooldown = PENDING_OUTPUT_FLUSH_INTERVAL_TICKS - 1;
        flushPendingOutput();
    }

    private void flushPendingOutput() {
        if (this.level == null || this.pendingOutput.isEmpty() || this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
            return;
        }
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            MEStorage networkStorage = getConnectedItemNetwork();
            this.pendingOutput.flush(stack -> acceptedAmount(
                    stack,
                    insertIntoNetwork(stack, networkStorage),
                    "AE network"),
                    PENDING_OUTPUT_OFFER_BUDGET);
            return;
        }

        flushIntoAdjacentContainers(
                this.pendingOutput,
                getAdjacentItemHandlers(),
                this.adjacentInsertionCursors,
                PENDING_OUTPUT_OFFER_BUDGET);
    }

    /**
     * Applies a fixed number of single-slot container attempts while retaining per-item progress between calls.
     *
     * @param pendingOutput    authoritative generated-item ledger
     * @param adjacentHandlers current handlers keyed by stable block direction
     * @param insertionCursors per-item scan positions retained across ticks
     * @param offerBudget      positive maximum number of real slot attempts
     * @return exact number of items accepted by adjacent handlers
     */
    static long flushIntoAdjacentContainers(
                                            MimeticPendingOutput pendingOutput,
                                            Map<Direction, IItemHandler> adjacentHandlers,
                                            Map<AEItemKey, AdjacentContainerInsertionCursor> insertionCursors,
                                            int offerBudget) {
        if (offerBudget <= 0) {
            throw new IllegalArgumentException("offerBudget must be positive");
        }

        long totalAccepted = 0L;
        AEItemKey[] offeredKey = new AEItemKey[1];
        for (int offer = 0; offer < offerBudget && !pendingOutput.isEmpty(); offer++) {
            offeredKey[0] = null;
            long accepted = pendingOutput.flush(stack -> {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) {
                    throw new IllegalArgumentException("Non-empty mimetic output stack has no AE item key");
                }

                offeredKey[0] = key;
                AdjacentContainerInsertionCursor cursor = insertionCursors.computeIfAbsent(key, ignored -> new AdjacentContainerInsertionCursor());
                return acceptedAmount(
                        stack,
                        insertIntoNextAdjacentContainerSlot(stack, adjacentHandlers, cursor),
                        "adjacent container");
            }, 1);
            totalAccepted = Math.addExact(totalAccepted, accepted);
            if (offeredKey[0] == null) {
                break;
            }
            if (pendingOutput.amount(offeredKey[0]) == 0L) {
                insertionCursors.remove(offeredKey[0]);
            }
        }
        return totalAccepted;
    }

    /**
     * Attempts one stack against at most one real slot, advancing before any third-party call can fail.
     *
     * @param stack            offered component-preserving stack
     * @param adjacentHandlers current handlers keyed by stable block direction
     * @param cursor           per-item scan position
     * @return unconfirmed remainder after the one bounded attempt
     */
    static ItemStack insertIntoNextAdjacentContainerSlot(
                                                         ItemStack stack,
                                                         Map<Direction, IItemHandler> adjacentHandlers,
                                                         AdjacentContainerInsertionCursor cursor) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (int checkedSides = 0; checkedSides < DIRECTIONS.length; checkedSides++) {
            Direction direction = DIRECTIONS[cursor.nextSideIndex];
            cursor.nextSideIndex = (cursor.nextSideIndex + 1) % DIRECTIONS.length;
            IItemHandler handler = adjacentHandlers.get(direction);
            if (handler == null) {
                continue;
            }

            int slotCount;
            try {
                slotCount = handler.getSlots();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to query data mimetic field output handler {}; trying the next adjacent container",
                        handler.getClass().getName(),
                        exception);
                continue;
            }
            if (slotCount <= 0) {
                if (slotCount < 0) {
                    Data_Energistics.LOGGER.error(
                            "Data mimetic field output handler {} reported invalid slot count {}",
                            handler.getClass().getName(),
                            slotCount);
                }
                continue;
            }

            int storedSlot = cursor.nextSlots.getOrDefault(direction, 0);
            int slot = storedSlot < slotCount ? storedSlot : storedSlot % slotCount;
            cursor.nextSlots.put(direction, slot == slotCount - 1 ? 0 : slot + 1);
            ItemStack offeredToSlot = stack.copy();
            ItemStack slotRemainder;
            try {
                slotRemainder = handler.insertItem(slot, offeredToSlot.copy(), false);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to insert data mimetic field output {} into slot {} of {}; retaining the unconfirmed remainder",
                        offeredToSlot,
                        slot,
                        handler.getClass().getName(),
                        exception);
                return stack;
            }
            if (!isValidRemainder(offeredToSlot, slotRemainder)) {
                Data_Energistics.LOGGER.error(
                        "Data mimetic field output handler {} returned invalid remainder {} for slot {} offer {}; retaining the unconfirmed remainder",
                        handler.getClass().getName(),
                        slotRemainder,
                        slot,
                        offeredToSlot);
                return stack;
            }
            return slotRemainder.copy();
        }
        return stack;
    }

    static int acceptedAmount(ItemStack offered, ItemStack remaining, String destination) {
        if (remaining.isEmpty()) {
            return offered.getCount();
        }
        if (!ItemStack.isSameItemSameComponents(offered, remaining) || remaining.getCount() > offered.getCount()) {
            throw new IllegalStateException(
                    "Data mimetic field " + destination + " returned an invalid remainder for " + offered);
        }
        return offered.getCount() - remaining.getCount();
    }

    private static boolean isValidRemainder(ItemStack offered, @Nullable ItemStack remaining) {
        return remaining != null && (remaining.isEmpty() || ItemStack.isSameItemSameComponents(offered, remaining) && remaining.getCount() <= offered.getCount());
    }

    private ItemStack insertIntoNetwork(ItemStack stack, @Nullable MEStorage networkStorage) {
        if (stack.isEmpty() || networkStorage == null) {
            return stack;
        }

        AEItemKey key = AEItemKey.of(stack);
        long inserted;
        try {
            inserted = requireValidAcceptedAmount(
                    networkStorage.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.ofMachine(this)),
                    stack.getCount(),
                    "AE network insertion");
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert data mimetic field output {} into the connected AE network; retaining it for retry",
                    stack,
                    exception);
            return stack;
        }
        if (inserted <= 0) {
            return stack;
        }

        ItemStack remaining = stack.copy();
        remaining.shrink((int) inserted);
        return remaining;
    }

    static long requireValidAcceptedAmount(long accepted, long offered, String destination) {
        if (offered < 0L) {
            throw new IllegalArgumentException("offered must be non-negative");
        }
        if (accepted < 0L || accepted > offered) {
            throw new IllegalStateException(destination + " accepted invalid amount " + accepted + " for offer " + offered);
        }
        return accepted;
    }

    private Map<Direction, IItemHandler> getAdjacentItemHandlers() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return Map.of();
        }
        if (this.adjacentItemHandlers == null) {
            this.adjacentItemHandlers = new AdjacentBlockCapabilityCache<>(
                    Capabilities.ItemHandler.BLOCK,
                    serverLevel,
                    this.worldPosition,
                    () -> !this.isRemoved());
        }
        return this.adjacentItemHandlers.getAllBySide(this.outputSides);
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

        List<ItemStack> fixedOutputs = DataExtractorRuleTable.getConfiguredOutputs(DataExtractorRuleTable.DataType.MOB, entityId);
        if (fixedOutputs.isEmpty()) {
            fixedOutputs = getBuiltInBiologyMimeticOutputs(serverLevel, entityId);
        }
        if (!fixedOutputs.isEmpty() && !hasOverflowDestructionCard()) {
            return new GeneratedLoot(repeatStacks(fixedOutputs, getBiologyLootRollsPerCycle()), 0);
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            return new GeneratedLoot(repeatStacks(fixedOutputs, getBiologyLootRollsPerCycle()), 0);
        }

        int targetRolls = getBiologyLootRollsPerCycle();
        List<GeneratedLoot> samples = getBiologyLootSamples(serverLevel, entityId, entityType, fixedOutputs);
        return scaleGeneratedLoot(samples, targetRolls);
    }

    private List<GeneratedLoot> getBiologyLootSamples(
                                                      ServerLevel serverLevel,
                                                      ResourceLocation entityId,
                                                      EntityType<?> entityType,
                                                      List<ItemStack> fixedOutputs) {
        BiologyLootSampleKey cacheKey = new BiologyLootSampleKey(entityId, !fixedOutputs.isEmpty());
        long gameTime = serverLevel.getGameTime();
        BiologyLootSamples cached = this.biologyLootSamples.get(cacheKey);
        if (cached != null && gameTime >= cached.refreshedAt() && gameTime - cached.refreshedAt() < BIOLOGY_LOOT_SAMPLE_REFRESH_INTERVAL_TICKS) {
            return cached.samples();
        }

        Player fakePlayer = getFakePlayer(serverLevel);
        fakePlayer.moveTo(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 1.0,
                this.worldPosition.getZ() + 0.5,
                fakePlayer.getYRot(),
                fakePlayer.getXRot());

        int sampleRolls = Math.min(BIOLOGY_LOOT_SAMPLE_ROLLS, getBiologyLootRollsPerCycle());
        List<GeneratedLoot> samples = new ArrayList<>(sampleRolls);
        for (int roll = 0; roll < sampleRolls; roll++) {
            GeneratedLoot rollLoot = GeneratedLoot.empty();
            Entity entity = entityType.create(serverLevel);
            if (!(entity instanceof LivingEntity livingEntity)) {
                samples.add(rollLoot);
                continue;
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

            List<LivingEntity> simulatedEntities = collectSimulatedLivingEntities(livingEntity);
            if (fixedOutputs.isEmpty()) {
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    rollLoot = rollLoot.merge(simulateEntityDrops(serverLevel, simulatedEntity, fakePlayer));
                }
            } else {
                rollLoot = new GeneratedLoot(List.copyOf(fixedOutputs), 0L);
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    rollLoot = rollLoot.merge(simulateEntityExperience(serverLevel, simulatedEntity, fakePlayer));
                }
            }
            for (LivingEntity simulatedEntity : simulatedEntities) {
                simulatedEntity.discard();
            }
            samples.add(rollLoot);
        }
        List<GeneratedLoot> refreshed = List.copyOf(samples);
        this.biologyLootSamples.put(cacheKey, new BiologyLootSamples(refreshed, gameTime));
        return refreshed;
    }

    private static List<ItemStack> getBuiltInBiologyMimeticOutputs(ServerLevel serverLevel, ResourceLocation entityId) {
        if (GOAT_ENTITY_ID.equals(entityId)) {
            return createGoatHornOutputs(serverLevel);
        }
        if (ARMADILLO_ENTITY_ID.equals(entityId)) {
            return List.of(new ItemStack(Items.ARMADILLO_SCUTE));
        }
        if (TURTLE_ENTITY_ID.equals(entityId)) {
            return List.of(new ItemStack(Items.TURTLE_SCUTE));
        }
        return List.of();
    }

    private static List<ItemStack> createGoatHornOutputs(ServerLevel serverLevel) {
        var instruments = serverLevel.registryAccess().lookupOrThrow(Registries.INSTRUMENT);
        List<ItemStack> outputs = new ArrayList<>(GOAT_HORN_INSTRUMENTS.size());
        for (ResourceKey<Instrument> instrumentKey : GOAT_HORN_INSTRUMENTS) {
            ItemStack horn = new ItemStack(Items.GOAT_HORN);
            horn.set(DataComponents.INSTRUMENT, instruments.getOrThrow(instrumentKey));
            outputs.add(horn);
        }
        return List.copyOf(outputs);
    }

    private static List<ItemStack> repeatStacks(List<ItemStack> stacks, int repetitions) {
        if (stacks.isEmpty() || repetitions <= 0) {
            return List.of();
        }

        List<ItemStack> copies = new ArrayList<>(stacks.size() * repetitions);
        for (int i = 0; i < repetitions; i++) {
            addCopies(copies, stacks);
        }
        return List.copyOf(copies);
    }

    private static void addCopies(List<ItemStack> target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    /**
     * Scales sampled biology rolls to the configured work-cycle count while retaining each sample's output shape.
     *
     * @param samples     one generated result per real sample roll
     * @param targetRolls configured number of logical rolls in the work cycle
     * @return scaled item and experience output
     */
    private static GeneratedLoot scaleGeneratedLoot(List<GeneratedLoot> samples, int targetRolls) {
        if (samples.isEmpty() || targetRolls <= 0) {
            return GeneratedLoot.empty();
        }

        int baseRepetitions = targetRolls / samples.size();
        int remainder = targetRolls % samples.size();
        GeneratedLoot scaled = GeneratedLoot.empty();
        for (int sample = 0; sample < samples.size(); sample++) {
            int repetitions = baseRepetitions + (sample < remainder ? 1 : 0);
            scaled = scaled.merge(samples.get(sample).repeat(repetitions));
        }
        return scaled;
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

    static GeneratedLoot simulateEntityDrops(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
        int experience = Math.max(0, livingEntity.getExperienceReward(serverLevel, fakePlayer));
        SimulatedDeathDrops captured = new SimulatedDeathDrops(livingEntity);
        SIMULATED_DEATH_DROPS.set(captured);
        try {
            // Match a player-caused death so loot tables using killed_by_player can roll.
            BIOLOGY_DEATH_DROP_SIMULATION.generateDrops(serverLevel, livingEntity, fakePlayer);
        } finally {
            SIMULATED_DEATH_DROPS.remove();
        }
        if (livingEntity instanceof Witch) {
            captured.stacks().add(new ItemStack(Items.GLOWSTONE_DUST));
        }
        return new GeneratedLoot(List.copyOf(captured.stacks()), experience);
    }

    public static void captureSimulatedDeathDrops(LivingDropsEvent event) {
        SimulatedDeathDrops captured = SIMULATED_DEATH_DROPS.get();
        if (captured == null || captured.entity() != event.getEntity()) {
            return;
        }

        for (ItemEntity drop : event.getDrops()) {
            captured.capture(drop);
        }
        event.setCanceled(true);
    }

    /**
     * Redirects item entities spawned directly by simulated drop listeners into the current generated result.
     *
     * @param event entity admission attempt raised before the item is added to the level
     */
    public static void captureSimulatedSpawnedDrops(EntityJoinLevelEvent event) {
        SimulatedDeathDrops captured = SIMULATED_DEATH_DROPS.get();
        if (captured == null || event.isCanceled() || event.loadedFromDisk() || !(event.getEntity() instanceof ItemEntity itemEntity) || !captured.acceptsSpawnedDrop(itemEntity, event.getLevel())) {
            return;
        }

        captured.capture(itemEntity);
        event.setCanceled(true);
    }

    private GeneratedLoot simulateEntityExperience(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
        int experience = Math.max(0, livingEntity.getExperienceReward(serverLevel, fakePlayer));
        return new GeneratedLoot(List.of(), experience);
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

            if (cropItemId.equals(BuiltInRegistries.ITEM.getKey(Items.CHORUS_FLOWER))) {
                return List.of(new ItemStack(Items.CHORUS_FLOWER), new ItemStack(Items.CHORUS_FRUIT));
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
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableId));
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
        if (generated.isEmpty()) {
            return;
        }
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            submitGeneratedItemsToNetwork(generated);
            return;
        }
        appendPendingOutput(generated);
    }

    private void submitGeneratedItemsToNetwork(List<ItemStack> generated) {
        MEStorage networkStorage = getConnectedItemNetwork();
        if (networkStorage == null) {
            appendPendingOutput(generated);
            return;
        }

        if (!canNetworkAcceptAll(generated, networkStorage)) {
            appendPendingOutput(generated);
            return;
        }

        List<ItemStack> remaining = getNetworkInsertRemainders(generated, networkStorage);
        if (!remaining.isEmpty()) {
            appendPendingOutput(remaining);
        }
    }

    private void appendPendingOutput(List<ItemStack> stacks) {
        this.pendingOutput.append(stacks);
        this.pendingOutputFlushCooldown = 0;
    }

    private boolean canNetworkAcceptAll(List<ItemStack> stacks, MEStorage networkStorage) {
        Map<AEItemKey, Long> requiredAmounts = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            AEItemKey key = AEItemKey.of(stack);
            requiredAmounts.merge(key, (long) stack.getCount(), DataMimeticFieldBlockEntity::saturatedAdd);
        }

        for (Map.Entry<AEItemKey, Long> entry : requiredAmounts.entrySet()) {
            long accepted;
            try {
                accepted = requireValidAcceptedAmount(
                        networkStorage.insert(entry.getKey(), entry.getValue(), Actionable.SIMULATE, IActionSource.ofMachine(this)),
                        entry.getValue(),
                        "AE network simulation");
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to simulate data mimetic field output {} x{} in the connected AE network; retaining the generated batch",
                        entry.getKey(),
                        entry.getValue(),
                        exception);
                return false;
            }
            if (accepted < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> getNetworkInsertRemainders(List<ItemStack> stacks, @Nullable MEStorage networkStorage) {
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack networkRemaining = insertIntoNetwork(stack, networkStorage);
            if (!networkRemaining.isEmpty()) {
                remaining.add(networkRemaining);
            }
        }
        return remaining;
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
        return (OUTPUT_PER_SPEED_CARD * (getInstalledSpeedCardCount() + 1) * OUTPUT_SCALE) / OUTPUT_REDUCTION_DIVISOR;
    }

    public int getOreOutputRollsPerCycle() {
        return (OUTPUT_PER_SPEED_CARD * (getInstalledSpeedCardCount() + 1) * OUTPUT_SCALE) / OUTPUT_REDUCTION_DIVISOR;
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
        return (long) activeCarrierCount * (DATA_FLOW_PER_WORK_CYCLE + getInstalledSpeedCardCount() * 500L);
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
        var inv = new GenericStackInv(Set.of(DataFlowKeyType.TYPE), this::syncStackFromKeyMenu, GenericStackInv.Mode.STORAGE, 1) {

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

    private final class DataFlowExternalInventory implements GenericInternalInventory {

        private boolean batchDirty;
        private int batchDepth;

        @Override
        public int size() {
            return 1;
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            return isValidSlot(slot) ? keyInputStack : null;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? null : stack.what();
        }

        @Override
        public long getAmount(int slot) {
            GenericStack stack = getStack(slot);
            return stack == null ? 0L : stack.amount();
        }

        @Override
        public long getMaxAmount(AEKey key) {
            return isSupportedType(key) ? KEY_INPUT_CAPACITY : 0L;
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            return isSupportedType(keyType) ? KEY_INPUT_CAPACITY : 0L;
        }

        @Override
        public boolean canInsert() {
            return true;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack newStack) {
            if (!isValidSlot(slot)) {
                return;
            }
            if (newStack != null && !isAllowedIn(slot, newStack.what())) {
                return;
            }

            GenericStack clamped = clampDataFlowStack(newStack);
            GenericStack current = keyInputStack;
            boolean changed = current == null ? clamped != null : !current.equals(clamped);
            if (!changed) {
                return;
            }

            keyInputStack = clamped;
            onChange();
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type == DataFlowKeyType.TYPE;
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            return isValidSlot(slot) && what instanceof DataFlowKey;
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what) || amount <= 0L) {
                return 0L;
            }

            long stored = keyInputStack != null && keyInputStack.what() instanceof DataFlowKey ? keyInputStack.amount() : 0L;
            long inserted = Math.min(amount, KEY_INPUT_CAPACITY - stored);
            if (inserted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                keyInputStack = new GenericStack(DataFlowKey.of(), stored + inserted);
                onChange();
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what) || amount <= 0L || keyInputStack == null || !(keyInputStack.what() instanceof DataFlowKey)) {
                return 0L;
            }

            long extracted = Math.min(amount, keyInputStack.amount());
            if (extracted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                long remaining = keyInputStack.amount() - extracted;
                keyInputStack = remaining > 0L ? new GenericStack(DataFlowKey.of(), remaining) : null;
                onChange();
            }
            return extracted;
        }

        @Override
        public void beginBatch() {
            this.batchDepth++;
        }

        @Override
        public void endBatch() {
            if (this.batchDepth > 0) {
                this.batchDepth--;
            }
            if (this.batchDepth == 0 && this.batchDirty) {
                this.batchDirty = false;
                syncKeyMenuFromStack();
                setChanged();
                markForClientUpdate();
            }
        }

        @Override
        public void endBatchSuppressed() {
            if (this.batchDepth > 0) {
                this.batchDepth--;
            }
            if (this.batchDepth == 0) {
                this.batchDirty = false;
            }
        }

        @Override
        public void onChange() {
            if (this.batchDepth > 0) {
                this.batchDirty = true;
                return;
            }

            syncKeyMenuFromStack();
            setChanged();
            markForClientUpdate();
        }

        private boolean isValidSlot(int slot) {
            return slot == 0;
        }

        private @Nullable GenericStack clampDataFlowStack(@Nullable GenericStack stack) {
            if (stack == null || stack.amount() <= 0L || !(stack.what() instanceof DataFlowKey)) {
                return null;
            }
            return new GenericStack(DataFlowKey.of(), Math.min(KEY_INPUT_CAPACITY, stack.amount()));
        }
    }

    /** Stores the next direction and slot for one component-sensitive pending item. */
    static final class AdjacentContainerInsertionCursor {

        /** Next stable direction index to inspect. */
        private int nextSideIndex;

        /** Next slot per direction, normalized whenever a handler changes its reported size. */
        private final EnumMap<Direction, Integer> nextSlots = new EnumMap<>(Direction.class);
    }

    /** Identifies one cached biology sample set by entity and fixed-output mode. */
    private record BiologyLootSampleKey(ResourceLocation entityId, boolean hasFixedOutputs) {}

    /** Caches sampled biology results until the next refresh timestamp. */
    private record BiologyLootSamples(List<GeneratedLoot> samples, long refreshedAt) {}

    /**
     * Captures the item and experience output of one or more simulated loot rolls.
     *
     * @param stacks     generated item stacks
     * @param experience generated experience amount
     */
    record GeneratedLoot(List<ItemStack> stacks, long experience) {

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

        private GeneratedLoot repeat(int repetitions) {
            if (repetitions <= 0) {
                return empty();
            }
            return new GeneratedLoot(repeatStacks(this.stacks, repetitions), saturatedMultiply(this.experience, repetitions));
        }
    }

    private static final class SimulatedDeathDrops {

        private final LivingEntity entity;
        private final List<ItemStack> stacks = new ArrayList<>();
        private final Set<ItemEntity> capturedItemEntities = Collections.newSetFromMap(new IdentityHashMap<>());

        private SimulatedDeathDrops(LivingEntity entity) {
            this.entity = entity;
        }

        private LivingEntity entity() {
            return this.entity;
        }

        private List<ItemStack> stacks() {
            return this.stacks;
        }

        private boolean acceptsSpawnedDrop(ItemEntity itemEntity, Level eventLevel) {
            return eventLevel == this.entity.level() && itemEntity.level() == eventLevel && !itemEntity.getItem().isEmpty() && itemEntity.distanceToSqr(this.entity) <= SIMULATED_DROP_CAPTURE_RADIUS_SQUARED;
        }

        private void capture(ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty() || !this.capturedItemEntities.add(itemEntity)) {
                return;
            }

            this.stacks.add(stack.copy());
        }
    }
}
