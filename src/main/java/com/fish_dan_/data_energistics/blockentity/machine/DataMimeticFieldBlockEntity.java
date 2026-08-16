package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.block.machine.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.blockentity.machine.mimetic.MimeticCarrierPlan;
import com.fish_dan_.data_energistics.blockentity.machine.mimetic.MimeticExternalIoBudget;
import com.fish_dan_.data_energistics.blockentity.machine.mimetic.MimeticGeneratedOutput;
import com.fish_dan_.data_energistics.common.acceleration.BatchTickProgression;
import com.fish_dan_.data_energistics.common.acceleration.DataRipperBatchTickable;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.common.memorycard.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.item.carrier.BiologyDataCarrierData;
import com.fish_dan_.data_energistics.item.carrier.CropDataCarrierData;
import com.fish_dan_.data_energistics.item.carrier.OreDataCarrierData;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;

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
import appeng.api.networking.IGridNodeListener;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataMimeticFieldBlockEntity extends AENetworkedPoweredBlockEntity
                                         implements IUpgradeableObject, DataRipperBatchTickable {

    public static final int BASE_ACTIVE_SLOTS = 4;
    public static final int EXTRA_SLOTS_PER_CAPACITY_CARD = 4;
    public static final int MAX_CAPACITY_CARDS = 1;
    public static final int SLOT_COUNT = BASE_ACTIVE_SLOTS + EXTRA_SLOTS_PER_CAPACITY_CARD * MAX_CAPACITY_CARDS;
    public static final double ENERGY_CACHE_CAPACITY = 1600.0;
    public static final long KEY_INPUT_CAPACITY = 640_000L;
    private static final int LEGACY_HIDDEN_BUFFER_SLOTS = SLOT_COUNT * 64;
    private static final double POWER_PER_ACTIVE_CARRIER = 500.0;
    private static final long DATA_FLOW_PER_WORK_CYCLE = 3_200L;
    private static final int BASE_WORK_INTERVAL_TICKS = 200;
    private static final int OUTPUT_PER_SPEED_CARD = 800;
    private static final int OUTPUT_REDUCTION_DIVISOR = 7;
    private static final int OUTPUT_SCALE = 2;
    /**
     * Number of real biology loot simulations sampled before scaling a work-cycle result.
     */
    private static final int BIOLOGY_LOOT_SAMPLE_ROLLS = 10;
    /**
     * Server ticks before cached biology loot samples are refreshed.
     */
    private static final int BIOLOGY_LOOT_SAMPLE_REFRESH_INTERVAL_TICKS = 200;
    private static final int PENDING_OUTPUT_FLUSH_INTERVAL_TICKS = 5;
    private static final int PENDING_OUTPUT_OFFER_BUDGET = 64;
    private static final int MAX_EXTERNAL_IO_OPERATIONS_PER_TICK = 64;
    private static final long MAX_EXTERNAL_IO_NANOS_PER_TICK = 2_000_000L;
    private static final int MAX_AUTO_PULL_BACKOFF_TICKS = 20;
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
    private static final ThreadLocal<@Nullable SimulatedDeathDrops> SIMULATED_DEATH_DROPS = new ThreadLocal<>();
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * Produces actual death drops without notifying unrelated real-world death listeners.
     */
    private static final VanillaBiologyDeathDropSimulation BIOLOGY_DEATH_DROP_SIMULATION = new VanillaBiologyDeathDropSimulation();
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
    private final MimeticPendingOutputLedger pendingOutput = new MimeticPendingOutputLedger(this::markRuntimePersistenceDirty);
    private final MimeticExternalIoBudget externalIoBudget = new MimeticExternalIoBudget(MAX_EXTERNAL_IO_OPERATIONS_PER_TICK, MAX_EXTERNAL_IO_NANOS_PER_TICK);
    /**
     * Keeps each component-sensitive item moving independently through bounded container slot attempts.
     */
    private final Map<AEItemKey, AdjacentContainerInsertionCursor> adjacentInsertionCursors = new HashMap<>();
    private final Map<Direction, AdjacentContainerTarget> adjacentContainerTargets = new EnumMap<>(Direction.class);
    private final GenericStackInv keyMenuInventory = createKeyMenuInventory();
    @Getter
    private final GenericInternalInventory externalKeyInventory = new DataFlowExternalInventory();
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(DEBlocks.DATA_MIMETIC_FIELD.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
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
    private int runtimeBatchDepth;
    private boolean runtimePersistenceDirty;
    private boolean runtimeKeyMenuDirty;
    private long lastAutoPullAttemptGameTime = Long.MIN_VALUE;
    private long nextAutoPullAttemptGameTime = Long.MIN_VALUE;
    private int autoPullBackoffTicks = 1;
    private @Nullable GenericStack keyInputStack;
    private int cachedSpeedCardCount = -1;
    private @Nullable AdjacentBlockCapabilityCache<IItemHandler> adjacentItemHandlers;
    private @Nullable Player cachedFakePlayer;
    private final Map<Block, BlockState> cachedCropLootStates = new HashMap<>();
    private final Map<Integer, MimeticCarrierPlan> carrierPlans = new HashMap<>();
    private @Nullable LoadedRules carrierPlanRules;
    /**
     * Reuses sampled biology results between refreshes to keep entity simulation off the hot work-cycle path.
     */
    private final Map<BiologyLootSampleKey, BiologyLootSamples> biologyLootSamples = new HashMap<>();

    public DataMimeticFieldBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_MIMETIC_FIELD.get())
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
        this.carrierPlans.clear();
        this.carrierPlanRules = null;
        this.biologyLootSamples.clear();
        this.cachedCropLootStates.clear();
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.adjacentInsertionCursors.clear();
        this.adjacentContainerTargets.clear();
        this.pendingOutput.readFromNbt(registries, data.getList(PENDING_OUTPUTS_TAG, Tag.TAG_COMPOUND));
        migrateLegacyHiddenBuffer(data, registries);
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
        clampWorkProgressToCurrentInterval();
        this.pendingOutputFlushCooldown = 0;
        resetAutoPullBackoff();
        this.powerUsageDirty = true;
        this.cachedActiveCarrierCount = 0;
        syncKeyMenuFromStack();
    }

    private void migrateLegacyHiddenBuffer(CompoundTag data, HolderLookup.Provider registries) {
        if (!data.contains(HIDDEN_BUFFER_TAG, Tag.TAG_LIST)) {
            return;
        }

        AppEngInternalInventory legacyBuffer = new AppEngInternalInventory(null, LEGACY_HIDDEN_BUFFER_SLOTS);
        legacyBuffer.readFromNBT(data, HIDDEN_BUFFER_TAG, registries);
        List<ItemStack> legacyPending = new ArrayList<>();
        for (ItemStack stack : legacyBuffer) {
            if (!stack.isEmpty()) {
                legacyPending.add(stack.copy());
            }
        }
        if (!legacyPending.isEmpty()) {
            this.pendingOutput.append(legacyPending);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.remove(HIDDEN_BUFFER_TAG);
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
        builder.set(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get(), settings);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = input.get(DEDataComponents.MACHINE_MEMORY_CARD_SETTINGS.get());
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
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.storage;
        }
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return super.getSubInventory(id);
    }

    public void serverTick() {
        advanceServerTicks(1);
    }

    @Override
    public void advanceAdditionalTicks(int additionalTicks) {
        if (additionalTicks <= 0) {
            throw new IllegalArgumentException("additionalTicks must be positive");
        }
        advanceServerTicks(additionalTicks);
    }

    private void advanceServerTicks(int tickBudget) {
        Level currentLevel = this.level;
        if (currentLevel == null || currentLevel.isClientSide()) {
            return;
        }

        this.externalIoBudget.begin(currentLevel.getGameTime());
        beginRuntimeBatch();
        try {
            updatePowerUsageIfNeeded();
            refillInputsForWork(currentLevel.getGameTime());
            int remainingTicks = tickBudget;
            while (remainingTicks > 0) {
                if (!this.pendingOutput.isEmpty()) {
                    if (this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
                        this.pendingOutputFlushCooldown = 0;
                        resetWorkProgress();
                        break;
                    }
                    if (this.pendingOutputFlushCooldown > 0) {
                        int pausedTicks = Math.min(remainingTicks, this.pendingOutputFlushCooldown);
                        this.pendingOutputFlushCooldown -= pausedTicks;
                        remainingTicks -= pausedTicks;
                        resetWorkProgress();
                        continue;
                    }

                    this.pendingOutputFlushCooldown = PENDING_OUTPUT_FLUSH_INTERVAL_TICKS - 1;
                    long accepted = flushPendingOutput();
                    if (!this.pendingOutput.isEmpty()) {
                        remainingTicks--;
                        resetWorkProgress();
                        if (accepted == 0L && remainingTicks > 0) {
                            skipStalledPendingOutputTicks(remainingTicks);
                            remainingTicks = 0;
                        }
                        continue;
                    }
                } else {
                    this.pendingOutputFlushCooldown = 0;
                }

                if (this.redstoneControlled && !isReceivingRedstonePower()) {
                    resetWorkProgress();
                    break;
                }
                if (getActiveCarrierCount() <= 0) {
                    resetWorkProgress();
                    break;
                }
                if (!hasEnoughDataFlowForWorkCycle()) {
                    resetWorkProgress();
                    break;
                }

                int workInterval = computeWorkIntervalTicks();
                BatchTickProgression.Segment segment = BatchTickProgression.advanceToBoundary(
                        this.workTicks,
                        workInterval,
                        remainingTicks);
                this.workTicks = segment.progress();
                remainingTicks -= segment.elapsedTicks();
                if (segment.elapsedTicks() > 0) {
                    markRuntimePersistenceDirty();
                }
                if (!segment.reachedBoundary()) {
                    break;
                }
                if (!consumeDataFlowPerWorkCycle()) {
                    resetWorkProgress();
                    break;
                }

                performMimeticWork();
            }
            updateOnlineState();
        } finally {
            endRuntimeBatch();
        }
    }

    private void skipStalledPendingOutputTicks(int skippedTicks) {
        int ticksSinceLastFlushAttempt = skippedTicks % PENDING_OUTPUT_FLUSH_INTERVAL_TICKS;
        this.pendingOutputFlushCooldown = PENDING_OUTPUT_FLUSH_INTERVAL_TICKS - 1 - ticksSinceLastFlushAttempt;
    }

    private void refillInputsForWork(long currentGameTime) {
        refillEnergyCache();
        if (this.autoPullKeyInput) {
            refillKeyFromNetwork(currentGameTime);
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        resetAutoPullBackoff();
        updatePowerUsage();
        updateOnlineState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        resetAutoPullBackoff();
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
        if (enabled) {
            resetAutoPullBackoff();
        }
        this.setChanged();
        this.markForClientUpdate();
        return this.autoPullKeyInput;
    }

    public DataExtractorDropRoutingMode setDropRoutingMode(DataExtractorDropRoutingMode mode) {
        if (this.dropRoutingMode == mode) {
            return this.dropRoutingMode;
        }

        this.dropRoutingMode = mode;
        if (mode != DataExtractorDropRoutingMode.CONTAINER) {
            this.adjacentInsertionCursors.clear();
            this.adjacentContainerTargets.clear();
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

        if (!enabled) {
            this.adjacentContainerTargets.remove(side);
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
                if (autoPullKeyInput) {
                    resetAutoPullBackoff();
                }
                changed = true;
            }
        }
        if (settings.contains(DROP_ROUTING_MODE_TAG)) {
            DataExtractorDropRoutingMode dropRoutingMode = DataExtractorDropRoutingMode.fromOrdinal(settings.getInt(DROP_ROUTING_MODE_TAG));
            if (this.dropRoutingMode != dropRoutingMode) {
                this.dropRoutingMode = dropRoutingMode;
                if (dropRoutingMode != DataExtractorDropRoutingMode.CONTAINER) {
                    this.adjacentInsertionCursors.clear();
                    this.adjacentContainerTargets.clear();
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
        this.carrierPlans.clear();
        this.biologyLootSamples.clear();
        this.cachedCropLootStates.clear();
        this.pendingOutput.clear();
        this.adjacentInsertionCursors.clear();
        this.adjacentContainerTargets.clear();
        this.upgrades.clear();
        this.keyInputStack = null;
        resetAutoPullBackoff();
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
            return stack.is(DEItems.MOB_DATA_CARRIER.get()) || stack.is(DEItems.ORE_DATA_CARRIER.get()) || stack.is(DEItems.CROP_DATA_CARRIER.get());
        }
    }

    private void onUpgradesChanged() {
        this.cachedSpeedCardCount = -1;
        clampWorkProgressToCurrentInterval();
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
            this.carrierPlans.remove(i);
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
            this.carrierPlans.remove(slot);
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

    private void performMimeticWork() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        refreshCarrierPlans();
        MimeticGeneratedOutput.Accumulator biologyOutput = MimeticGeneratedOutput.accumulator();
        MimeticGeneratedOutput.Accumulator oreOutput = MimeticGeneratedOutput.accumulator();
        MimeticGeneratedOutput.Accumulator cropOutput = MimeticGeneratedOutput.accumulator();
        Set<BiologyLootSampleKey> activeBiologySamples = new HashSet<>();
        Set<Block> activeCropLootStates = new HashSet<>();
        int biologyRolls = getBiologyLootRollsPerCycle();
        int itemRolls = getOreOutputRollsPerCycle();
        boolean convertOverflow = hasOverflowDestructionCard();
        int activeSlotCount = getActiveSlotCount();
        for (int slot = 0; slot < activeSlotCount; slot++) {
            MimeticCarrierPlan plan = this.carrierPlans.computeIfAbsent(
                    slot,
                    index -> resolveCarrierPlan(serverLevel, this.storage.getStackInSlot(index)));
            if (plan instanceof MimeticCarrierPlan.Biology biology) {
                if (biology.entityType() != null && (biology.fixedOutput().isEmpty() || convertOverflow)) {
                    activeBiologySamples.add(new BiologyLootSampleKey(biology.entityId(), !biology.fixedOutput().isEmpty()));
                }
                biologyOutput.add(generateBiologyLoot(serverLevel, biology, biologyRolls, convertOverflow));
            } else if (plan instanceof MimeticCarrierPlan.Ore ore) {
                oreOutput.addRepeated(ore.output(), itemRolls);
            } else if (plan instanceof MimeticCarrierPlan.Crop crop) {
                if (crop.fixedOutput().isEmpty() && crop.sourceBlock() != null) {
                    activeCropLootStates.add(crop.sourceBlock());
                }
                if (!crop.fixedOutput().isEmpty()) {
                    cropOutput.addRepeated(crop.fixedOutput(), itemRolls);
                    continue;
                }
                for (int roll = 0; roll < itemRolls; roll++) {
                    cropOutput.add(generateCropLoot(serverLevel, crop));
                }
            }
        }

        this.biologyLootSamples.keySet().retainAll(activeBiologySamples);
        this.cachedCropLootStates.keySet().retainAll(activeCropLootStates);

        submitGeneratedLoot(biologyOutput.build(), convertOverflow);
        submitGeneratedLoot(oreOutput.build(), convertOverflow);
        submitGeneratedLoot(cropOutput.build(), convertOverflow);
    }

    private void refreshCarrierPlans() {
        LoadedRules published = DataExtractorRuleTable.snapshot();
        if (published == this.carrierPlanRules) {
            return;
        }

        this.carrierPlanRules = published;
        this.carrierPlans.clear();
        this.biologyLootSamples.clear();
    }

    private MimeticCarrierPlan resolveCarrierPlan(ServerLevel serverLevel, ItemStack carrier) {
        if (BiologyDataCarrierData.isComplete(carrier)) {
            ResourceLocation entityId = BiologyDataCarrierData.getEntityTypeId(carrier);
            if (entityId == null) {
                return MimeticCarrierPlan.Empty.INSTANCE;
            }
            List<ItemStack> fixedOutputs = DataExtractorRuleTable.getConfiguredOutputs(
                    DataExtractorRuleTable.DataType.MOB,
                    entityId);
            if (fixedOutputs.isEmpty()) {
                fixedOutputs = getBuiltInBiologyMimeticOutputs(serverLevel, entityId);
            }
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
            return new MimeticCarrierPlan.Biology(
                    entityId,
                    entityType,
                    MimeticGeneratedOutput.fromStacks(fixedOutputs));
        }

        if (OreDataCarrierData.isComplete(carrier)) {
            ResourceLocation oreItemId = OreDataCarrierData.getOreItemId(carrier);
            if (oreItemId == null) {
                return MimeticCarrierPlan.Empty.INSTANCE;
            }
            List<ItemStack> outputs = DataExtractorRuleTable.getConfiguredOutputs(
                    DataExtractorRuleTable.DataType.ORE,
                    oreItemId);
            if (outputs.isEmpty()) {
                Item oreItem = BuiltInRegistries.ITEM.getOptional(oreItemId).orElse(null);
                if (oreItem != null) {
                    outputs = List.of(new ItemStack(oreItem));
                }
            }
            return new MimeticCarrierPlan.Ore(MimeticGeneratedOutput.fromStacks(outputs));
        }

        if (CropDataCarrierData.isComplete(carrier)) {
            ResourceLocation cropItemId = CropDataCarrierData.getCropItemId(carrier);
            List<ItemStack> fixedOutputs = cropItemId == null ? List.of() : DataExtractorRuleTable.getConfiguredOutputs(DataExtractorRuleTable.DataType.CROP, cropItemId);
            if (fixedOutputs.isEmpty() && cropItemId != null && cropItemId.equals(BuiltInRegistries.ITEM.getKey(Items.CHORUS_FLOWER))) {
                fixedOutputs = List.of(new ItemStack(Items.CHORUS_FLOWER), new ItemStack(Items.CHORUS_FRUIT));
            }

            ResourceLocation lootTableId = CropDataCarrierData.getLootTableId(carrier);
            ResourceLocation sourceBlockId = CropDataCarrierData.getSourceBlockId(carrier);
            Block sourceBlock = sourceBlockId == null ? null : BuiltInRegistries.BLOCK.getOptional(sourceBlockId).orElse(null);
            Item cropItem = cropItemId == null ? null : BuiltInRegistries.ITEM.getOptional(cropItemId).orElse(null);
            MimeticGeneratedOutput fallback = cropItem == null ? MimeticGeneratedOutput.empty() : MimeticGeneratedOutput.fromStacks(List.of(new ItemStack(cropItem)));
            return new MimeticCarrierPlan.Crop(
                    MimeticGeneratedOutput.fromStacks(fixedOutputs),
                    lootTableId,
                    sourceBlock,
                    fallback);
        }

        return MimeticCarrierPlan.Empty.INSTANCE;
    }

    private MimeticGeneratedOutput generateCropLoot(ServerLevel serverLevel, MimeticCarrierPlan.Crop plan) {
        if (plan.lootTableId() != null) {
            MimeticGeneratedOutput treeLoot = MimeticGeneratedOutput.fromStacks(
                    generateConfiguredLootTableDrops(serverLevel, plan.lootTableId()));
            if (!treeLoot.isEmpty()) {
                return treeLoot;
            }
        }

        if (plan.sourceBlock() != null) {
            MimeticGeneratedOutput blockLoot = MimeticGeneratedOutput.fromStacks(
                    generateBlockLootDrops(serverLevel, getRecordedCropLootState(plan.sourceBlock())));
            if (!blockLoot.isEmpty()) {
                return blockLoot;
            }
        }
        return plan.fallback();
    }

    private long flushPendingOutput() {
        if (this.level == null || this.pendingOutput.isEmpty() || this.dropRoutingMode == DataExtractorDropRoutingMode.OFF) {
            return 0L;
        }
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            MEStorage networkStorage = getConnectedItemNetwork();
            if (networkStorage == null) {
                return 0L;
            }
            IActionSource actionSource = IActionSource.ofMachine(this);
            return this.pendingOutput.flushAmounts(
                    (key, amount) -> insertIntoNetwork(key, amount, networkStorage, actionSource),
                    PENDING_OUTPUT_OFFER_BUDGET);
        }

        return flushIntoAdjacentContainers(
                this.pendingOutput,
                getAdjacentContainerTargets(),
                this.adjacentInsertionCursors,
                this.externalIoBudget);
    }

    /**
     * Applies a fixed number of single-slot container attempts while retaining per-item progress between calls.
     *
     * @param pendingOutput    authoritative generated-item ledger
     * @param adjacentTargets  current handlers and stable slot counts keyed by direction
     * @param insertionCursors per-item scan positions retained across ticks
     * @param externalIoBudget shared real-tick external call allowance
     * @return exact number of items accepted by adjacent handlers
     */
    private static long flushIntoAdjacentContainers(
                                                    MimeticPendingOutputLedger pendingOutput,
                                                    Map<Direction, AdjacentContainerTarget> adjacentTargets,
                                                    Map<AEItemKey, AdjacentContainerInsertionCursor> insertionCursors,
                                                    MimeticExternalIoBudget externalIoBudget) {
        long totalAccepted = 0L;
        @Nullable
        AEItemKey[] offeredKey = new AEItemKey[1];
        for (int offer = 0; offer < PENDING_OUTPUT_OFFER_BUDGET && !pendingOutput.isEmpty(); offer++) {
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
                        insertIntoNextAdjacentContainerSlot(stack, adjacentTargets, cursor, externalIoBudget));
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
     * @param adjacentTargets  current handlers and stable slot counts keyed by direction
     * @param cursor           per-item scan position
     * @param externalIoBudget shared real-tick external call allowance
     * @return unconfirmed remainder after the one bounded attempt
     */
    private static ItemStack insertIntoNextAdjacentContainerSlot(
                                                                 ItemStack stack,
                                                                 Map<Direction, AdjacentContainerTarget> adjacentTargets,
                                                                 AdjacentContainerInsertionCursor cursor,
                                                                 MimeticExternalIoBudget externalIoBudget) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (int checkedSides = 0; checkedSides < DIRECTIONS.length; checkedSides++) {
            Direction direction = DIRECTIONS[cursor.nextSideIndex];
            cursor.nextSideIndex = (cursor.nextSideIndex + 1) % DIRECTIONS.length;
            AdjacentContainerTarget target = adjacentTargets.get(direction);
            if (target == null) {
                continue;
            }

            IItemHandler handler = target.handler();
            int slotCount = target.slotCount();
            int storedSlot = cursor.nextSlots.getOrDefault(direction, 0);
            int slot = storedSlot < slotCount ? storedSlot : storedSlot % slotCount;
            cursor.nextSlots.put(direction, slot == slotCount - 1 ? 0 : slot + 1);
            ItemStack offeredToSlot = stack.copy();
            ItemStack slotRemainder;
            if (!externalIoBudget.tryAcquire()) {
                return stack;
            }
            try {
                slotRemainder = handler.insertItem(slot, offeredToSlot.copy(), false);
            } catch (RuntimeException exception) {
                adjacentTargets.remove(direction, target);
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

    private static int acceptedAmount(ItemStack offered, ItemStack remaining) {
        if (remaining.isEmpty()) {
            return offered.getCount();
        }
        if (!ItemStack.isSameItemSameComponents(offered, remaining) || remaining.getCount() > offered.getCount()) {
            throw new IllegalStateException(
                    "Data mimetic field adjacent container returned an invalid remainder for " + offered);
        }
        return offered.getCount() - remaining.getCount();
    }

    private static boolean isValidRemainder(ItemStack offered, @Nullable ItemStack remaining) {
        return remaining != null && (remaining.isEmpty() || ItemStack.isSameItemSameComponents(offered, remaining) && remaining.getCount() <= offered.getCount());
    }

    private long insertIntoNetwork(AEItemKey key, long amount, MEStorage networkStorage, IActionSource actionSource) {
        if (!this.externalIoBudget.tryAcquire()) {
            return 0L;
        }
        try {
            return requireValidAcceptedAmount(
                    networkStorage.insert(key, amount, Actionable.MODULATE, actionSource),
                    amount,
                    "AE network insertion");
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert data mimetic field output {} x{} into the connected AE network; retaining it for retry",
                    key,
                    amount,
                    exception);
            return 0L;
        }
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

    private Map<Direction, AdjacentContainerTarget> getAdjacentContainerTargets() {
        Map<Direction, IItemHandler> handlers = getAdjacentItemHandlers();
        this.adjacentContainerTargets.entrySet().removeIf(
                entry -> handlers.get(entry.getKey()) != entry.getValue().handler());
        for (Map.Entry<Direction, IItemHandler> entry : handlers.entrySet()) {
            AdjacentContainerTarget current = this.adjacentContainerTargets.get(entry.getKey());
            if (current != null && current.handler() == entry.getValue()) {
                continue;
            }
            if (!this.externalIoBudget.tryAcquire()) {
                break;
            }

            int slotCount;
            try {
                slotCount = entry.getValue().getSlots();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to query data mimetic field output handler {}",
                        entry.getValue().getClass().getName(),
                        exception);
                continue;
            }
            if (slotCount < 0) {
                Data_Energistics.LOGGER.error(
                        "Data mimetic field output handler {} reported invalid slot count {}",
                        entry.getValue().getClass().getName(),
                        slotCount);
                continue;
            }
            if (slotCount > 0) {
                this.adjacentContainerTargets.put(
                        entry.getKey(),
                        new AdjacentContainerTarget(entry.getValue(), slotCount));
            }
        }
        return this.adjacentContainerTargets;
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

    private MimeticGeneratedOutput generateBiologyLoot(
                                                       ServerLevel serverLevel,
                                                       MimeticCarrierPlan.Biology plan,
                                                       int targetRolls,
                                                       boolean convertOverflow) {
        if (!plan.fixedOutput().isEmpty() && !convertOverflow) {
            return plan.fixedOutput().repeat(targetRolls);
        }

        EntityType<?> entityType = plan.entityType();
        if (entityType == null) {
            return plan.fixedOutput().repeat(targetRolls);
        }

        List<MimeticGeneratedOutput> samples = getBiologyLootSamples(serverLevel, plan, entityType);
        return scaleGeneratedLoot(samples, targetRolls);
    }

    private List<MimeticGeneratedOutput> getBiologyLootSamples(
                                                               ServerLevel serverLevel,
                                                               MimeticCarrierPlan.Biology plan,
                                                               EntityType<?> entityType) {
        BiologyLootSampleKey cacheKey = new BiologyLootSampleKey(plan.entityId(), !plan.fixedOutput().isEmpty());
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
        List<MimeticGeneratedOutput> samples = new ArrayList<>(sampleRolls);
        for (int roll = 0; roll < sampleRolls; roll++) {
            MimeticGeneratedOutput rollLoot = MimeticGeneratedOutput.empty();
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
            if (plan.fixedOutput().isEmpty()) {
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    rollLoot = rollLoot.merge(simulateEntityDrops(serverLevel, simulatedEntity, fakePlayer));
                }
            } else {
                rollLoot = plan.fixedOutput();
                for (LivingEntity simulatedEntity : simulatedEntities) {
                    rollLoot = rollLoot.merge(simulateEntityExperience(serverLevel, simulatedEntity, fakePlayer));
                }
            }
            for (LivingEntity simulatedEntity : simulatedEntities) {
                simulatedEntity.discard();
            }
            samples.add(rollLoot);
        }
        List<MimeticGeneratedOutput> refreshed = List.copyOf(samples);
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

    /**
     * Scales sampled biology rolls to the configured work-cycle count while retaining each sample's output shape.
     *
     * @param samples     one generated result per real sample roll
     * @param targetRolls configured number of logical rolls in the work cycle
     * @return scaled item and experience output
     */
    private static MimeticGeneratedOutput scaleGeneratedLoot(List<MimeticGeneratedOutput> samples, int targetRolls) {
        if (samples.isEmpty() || targetRolls <= 0) {
            return MimeticGeneratedOutput.empty();
        }

        int baseRepetitions = targetRolls / samples.size();
        int remainder = targetRolls % samples.size();
        MimeticGeneratedOutput.Accumulator scaled = MimeticGeneratedOutput.accumulator();
        for (int sample = 0; sample < samples.size(); sample++) {
            int repetitions = baseRepetitions + (sample < remainder ? 1 : 0);
            scaled.add(samples.get(sample).repeat(repetitions));
        }
        return scaled.build();
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

    static MimeticGeneratedOutput simulateEntityDrops(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
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
        return MimeticGeneratedOutput.fromStacks(captured.stacks(), experience);
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

    private MimeticGeneratedOutput simulateEntityExperience(ServerLevel serverLevel, LivingEntity livingEntity, Player fakePlayer) {
        int experience = Math.max(0, livingEntity.getExperienceReward(serverLevel, fakePlayer));
        return MimeticGeneratedOutput.fromStacks(List.of(), experience);
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
        if (state.isAir()) {
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

    private void submitGeneratedLoot(MimeticGeneratedOutput generated, boolean convertOverflow) {
        if (generated.isEmpty()) {
            return;
        }
        if (convertOverflow) {
            convertGeneratedLootToDataFlow(generated);
            return;
        }

        submitGeneratedItems(generated.items());
    }

    private void submitGeneratedItems(Map<AEItemKey, Long> generated) {
        if (generated.isEmpty()) {
            return;
        }
        if (this.dropRoutingMode == DataExtractorDropRoutingMode.AE) {
            submitGeneratedItemsToNetwork(generated);
            return;
        }
        appendPendingOutput(generated);
    }

    private void submitGeneratedItemsToNetwork(Map<AEItemKey, Long> generated) {
        MEStorage networkStorage = getConnectedItemNetwork();
        if (networkStorage == null) {
            appendPendingOutput(generated);
            return;
        }
        IActionSource actionSource = IActionSource.ofMachine(this);

        if (!canNetworkAcceptAll(generated, networkStorage, actionSource)) {
            appendPendingOutput(generated);
            return;
        }

        Map<AEItemKey, Long> remaining = getNetworkInsertRemainders(generated, networkStorage, actionSource);
        if (!remaining.isEmpty()) {
            appendPendingOutput(remaining);
        }
    }

    private void appendPendingOutput(Map<AEItemKey, Long> amounts) {
        this.pendingOutput.appendAmounts(amounts);
        this.pendingOutputFlushCooldown = 0;
    }

    private boolean canNetworkAcceptAll(
                                        Map<AEItemKey, Long> amounts,
                                        MEStorage networkStorage,
                                        IActionSource actionSource) {
        for (Map.Entry<AEItemKey, Long> entry : amounts.entrySet()) {
            if (!this.externalIoBudget.tryAcquire()) {
                return false;
            }
            long accepted;
            try {
                accepted = requireValidAcceptedAmount(
                        networkStorage.insert(entry.getKey(), entry.getValue(), Actionable.SIMULATE, actionSource),
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

    private Map<AEItemKey, Long> getNetworkInsertRemainders(
                                                            Map<AEItemKey, Long> amounts,
                                                            MEStorage networkStorage,
                                                            IActionSource actionSource) {
        LinkedHashMap<AEItemKey, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<AEItemKey, Long> entry : amounts.entrySet()) {
            long accepted = insertIntoNetwork(entry.getKey(), entry.getValue(), networkStorage, actionSource);
            long remainder = entry.getValue() - accepted;
            if (remainder > 0L) {
                remaining.put(entry.getKey(), remainder);
            }
        }
        return remaining;
    }

    private void convertGeneratedLootToDataFlow(MimeticGeneratedOutput generated) {
        long amount = saturatedAdd(
                saturatedMultiply(generated.itemAmount(), DATA_FLOW_PER_CONVERTED_ITEM),
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

    private long insertDataFlowIntoNetwork(long amount) {
        if (amount <= 0) {
            return 0L;
        }

        MEStorage networkStorage = getConnectedItemNetwork();
        if (networkStorage == null || !this.externalIoBudget.tryAcquire()) {
            return 0L;
        }

        return networkStorage.insert(DataFlowKey.of(), amount, Actionable.MODULATE, IActionSource.ofMachine(this));
    }

    private void insertDataFlowIntoKeyInput(long amount) {
        if (amount <= 0) {
            return;
        }

        long stored = dataFlowAmount(this.keyInputStack);
        long accepted = Math.min(amount, KEY_INPUT_CAPACITY - stored);
        if (accepted <= 0) {
            return;
        }

        this.keyInputStack = new GenericStack(DataFlowKey.of(), stored + accepted);
        markKeyInputChanged();
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

    private void clampWorkProgressToCurrentInterval() {
        this.workTicks = Math.min(this.workTicks, computeWorkIntervalTicks() - 1);
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
        resetAutoPullBackoff();
        markKeyInputChanged();
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

        if (!this.externalIoBudget.tryAcquire()) {
            return;
        }
        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void refillKeyFromNetwork(long currentGameTime) {
        if (this.lastAutoPullAttemptGameTime == currentGameTime || currentGameTime < this.nextAutoPullAttemptGameTime) {
            return;
        }

        long stored = dataFlowAmount(this.keyInputStack);
        long missing = KEY_INPUT_CAPACITY - stored;
        if (missing <= 0) {
            return;
        }

        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            recordAutoPullFailure(currentGameTime);
            return;
        }

        var inventory = node.getGrid().getStorageService().getInventory();
        if (!this.externalIoBudget.tryAcquire()) {
            deferAutoPullUntilNextTick(currentGameTime);
            return;
        }
        this.lastAutoPullAttemptGameTime = currentGameTime;
        long extracted = inventory.extract(DataFlowKey.of(), missing, Actionable.MODULATE, IActionSource.ofMachine(this));
        if (extracted <= 0) {
            recordAutoPullFailure(currentGameTime);
            return;
        }

        long updated = stored + extracted;
        this.keyInputStack = new GenericStack(DataFlowKey.of(), Math.min(KEY_INPUT_CAPACITY, updated));
        markKeyInputChanged();
        recordAutoPullSuccess(currentGameTime);
    }

    private void resetAutoPullBackoff() {
        this.nextAutoPullAttemptGameTime = Long.MIN_VALUE;
        this.autoPullBackoffTicks = 1;
    }

    private void recordAutoPullSuccess(long currentGameTime) {
        this.lastAutoPullAttemptGameTime = currentGameTime;
        this.nextAutoPullAttemptGameTime = currentGameTime + 1L;
        this.autoPullBackoffTicks = 1;
    }

    private void recordAutoPullFailure(long currentGameTime) {
        this.lastAutoPullAttemptGameTime = currentGameTime;
        this.nextAutoPullAttemptGameTime = currentGameTime + this.autoPullBackoffTicks;
        this.autoPullBackoffTicks = Math.min(MAX_AUTO_PULL_BACKOFF_TICKS, this.autoPullBackoffTicks * 2);
    }

    private void deferAutoPullUntilNextTick(long currentGameTime) {
        this.lastAutoPullAttemptGameTime = currentGameTime;
        this.nextAutoPullAttemptGameTime = Math.max(this.nextAutoPullAttemptGameTime, currentGameTime + 1L);
    }

    private void resetAutoPullBackoffIfCapacityOpened(long previousAmount, long currentAmount) {
        if (currentAmount < previousAmount) {
            resetAutoPullBackoff();
        }
    }

    private static long dataFlowAmount(@Nullable GenericStack stack) {
        return stack != null && stack.what() instanceof DataFlowKey ? stack.amount() : 0L;
    }

    private GenericStackInv createKeyMenuInventory() {
        var inv = new GenericStackInv(Set.of(DigitalizationKeyType.TYPE), this::syncStackFromKeyMenu, GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> what instanceof DataFlowKey);
            }
        };
        inv.setCapacity(DigitalizationKeyType.TYPE, KEY_INPUT_CAPACITY);
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
            long previousAmount = dataFlowAmount(this.keyInputStack);
            var stack = this.keyMenuInventory.getStack(0);
            if (stack == null || !(stack.what() instanceof DataFlowKey) || stack.amount() <= 0) {
                this.keyInputStack = null;
            } else {
                this.keyInputStack = new GenericStack(DataFlowKey.of(), Math.min(KEY_INPUT_CAPACITY, stack.amount()));
            }
            resetAutoPullBackoffIfCapacityOpened(previousAmount, dataFlowAmount(this.keyInputStack));
            markRuntimePersistenceDirty();
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
            markRuntimePersistenceDirty();
        }
    }

    private void beginRuntimeBatch() {
        this.runtimeBatchDepth++;
    }

    private void endRuntimeBatch() {
        if (this.runtimeBatchDepth <= 0) {
            throw new IllegalStateException("Data mimetic field runtime batch was not started");
        }
        this.runtimeBatchDepth--;
        if (this.runtimeBatchDepth > 0) {
            return;
        }

        if (this.runtimeKeyMenuDirty) {
            syncKeyMenuFromStack();
            this.runtimeKeyMenuDirty = false;
        }
        if (this.runtimePersistenceDirty) {
            saveChanges();
            this.runtimePersistenceDirty = false;
        }
    }

    private void markRuntimePersistenceDirty() {
        if (this.runtimeBatchDepth > 0) {
            this.runtimePersistenceDirty = true;
            return;
        }
        saveChanges();
    }

    private void markKeyInputChanged() {
        if (this.runtimeBatchDepth > 0) {
            this.runtimeKeyMenuDirty = true;
            this.runtimePersistenceDirty = true;
            return;
        }
        syncKeyMenuFromStack();
        saveChanges();
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

            resetAutoPullBackoffIfCapacityOpened(dataFlowAmount(current), dataFlowAmount(clamped));
            keyInputStack = clamped;
            onChange();
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type == DigitalizationKeyType.TYPE;
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

            long stored = dataFlowAmount(keyInputStack);
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
                resetAutoPullBackoff();
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
                markKeyInputChanged();
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

            markKeyInputChanged();
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

    private record AdjacentContainerTarget(IItemHandler handler, int slotCount) {}

    /** Stores the next direction and slot for one component-sensitive pending item. */
    private static final class AdjacentContainerInsertionCursor {

        /**
         * Next stable direction index to inspect.
         */
        private int nextSideIndex;

        /**
         * Next slot per direction, normalized whenever a handler changes its reported size.
         */
        private final EnumMap<Direction, Integer> nextSlots = new EnumMap<>(Direction.class);
    }

    /**
     * Identifies one cached biology sample set by entity and fixed-output mode.
     */
    private record BiologyLootSampleKey(ResourceLocation entityId, boolean hasFixedOutputs) {}

    /**
     * Caches sampled biology results until the next refresh timestamp.
     */
    private record BiologyLootSamples(List<MimeticGeneratedOutput> samples, long refreshedAt) {}

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
