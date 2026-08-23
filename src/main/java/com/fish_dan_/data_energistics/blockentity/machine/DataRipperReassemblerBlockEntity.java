package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.block.machine.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.common.acceleration.BatchTickProgression;
import com.fish_dan_.data_energistics.common.acceleration.DataRipperBatchTickable;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.common.memorycard.MemoryCardSettingsHelper;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipeInput;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigManager;
import appeng.util.ConfigMenuInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DataRipperReassemblerBlockEntity extends AENetworkedPoweredBlockEntity
                                              implements InternalInventoryHost, IConfigurableObject, IUpgradeableObject, ICraftingMachine,
                                              DataRipperBatchTickable {

    public static final int ITEM_INPUT_START_SLOT = 0;
    public static final int ITEM_INPUT_SLOT_COUNT = 9;
    public static final int ITEM_OUTPUT_START_SLOT = 9;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 3;
    public static final int STORAGE_SLOTS = 12;
    public static final int KEY_INPUT_SLOT = 0;
    public static final int KEY_OUTPUT_SLOT = 1;
    public static final int KEY_SLOT_COUNT = 2;
    public static final int FLUID_INPUT_CAPACITY = 51_200;
    public static final int FLUID_OUTPUT_CAPACITY = 51_200;
    public static final long KEY_INPUT_CAPACITY = 51_200_000L;
    public static final long KEY_OUTPUT_CAPACITY = 51_200_000L;
    public static final int MAX_PROGRESS = 200;
    public static final int UPGRADE_SLOTS = 6;
    public static final int BASE_PARALLEL = 1;
    public static final int MAX_ENERGY_CARDS = 2;
    public static final int PARALLEL_MULTIPLIER_PER_ENERGY_CARD = 16;
    public static final int ITEM_SLOT_CAPACITY = 512;
    public static final double ENERGY_CAPACITY = 160_000.0D;

    private static final String STORAGE_TAG = "storage";
    private static final String STORAGE_SLOT_TAG = "Slot";
    private static final String STORAGE_COUNT_TAG = "DataEnergisticsCount";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String FLUID_INPUT_A_TAG = "fluid_input_a";
    private static final String FLUID_INPUT_B_TAG = "fluid_input_b";
    private static final String FLUID_OUTPUT_A_TAG = "fluid_output_a";
    private static final String FLUID_OUTPUT_B_TAG = "fluid_output_b";
    private static final String KEY_INPUT_TAG = "key_input";
    private static final String KEY_OUTPUT_TAG = "key_output";
    private static final String AUTO_EXPORT_TAG = "auto_export";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final String ITEM_OUTPUT_SIDES_TAG = "item_output_sides";
    private static final String FLUID_OUTPUT_SIDES_TAG = "fluid_output_sides";
    private static final String KEY_OUTPUT_SIDES_TAG = "key_output_sides";
    private static final String PROGRESS_TAG = "progress";
    private static final String MAX_PROGRESS_TAG = "max_progress";
    private static final String ACTIVE_RECIPE_TAG = "active_recipe";
    private static final String PROCESSING_CHANNELS_TAG = "processing_channels";
    private static final String ITEM_INPUT_PATTERN_COLORS_TAG = "item_input_pattern_colors";
    private static final String FLUID_INPUT_PATTERN_COLORS_TAG = "fluid_input_pattern_colors";
    private static final String KEY_INPUT_PATTERN_COLORS_TAG = "key_input_pattern_colors";

    private final IUpgradeInventory upgrades;
    private final AppEngInternalInventory storage = new ReassemblerItemInventory();
    private final int[] itemInputPatternColors = new int[getItemInputSlotCount()];
    private final int[] fluidInputPatternColors = new int[getFluidInputSlotCount()];
    private final int[] keyInputPatternColors = new int[getKeyInputSlotCount()];
    private boolean suppressAe2DefaultInventorySerialization;
    private boolean preservingInputPatternColors;
    private final InternalInventory externalInput = createExternalInput();
    private final InternalInventory externalOutput = createExternalOutput();
    @Getter
    private final InternalInventory externalInventory = new CombinedInternalInventory(this.externalInput, this.externalOutput);

    private final List<FluidTank> fluidInputTanks = createFluidTanks(getFluidInputSlotCount(), FLUID_INPUT_CAPACITY);
    private final List<FluidTank> fluidOutputTanks = createFluidTanks(getFluidOutputSlotCount(), FLUID_OUTPUT_CAPACITY);
    private final List<GenericStackInv> fluidInputMenuInventories = createFluidMenuInventories(this.fluidInputTanks,
            FLUID_INPUT_CAPACITY);
    private final List<GenericStackInv> fluidOutputMenuInventories = createFluidMenuInventories(this.fluidOutputTanks,
            FLUID_OUTPUT_CAPACITY);
    private final List<GenericStackInv> keyInputMenuInventories = createKeyMenuInventories(getKeyInputSlotCount(), true);
    private final List<GenericStackInv> keyOutputMenuInventories = createKeyMenuInventories(getKeyOutputSlotCount(), false);
    @Getter
    private final GenericInternalInventory externalKeyInventory = new ReassemblerKeyInventory();
    @Getter
    private final IFluidHandler externalFluidHandler = new ReassemblerFluidHandler();
    @Getter
    private final MEStorage externalPatternInputStorage = new PatternInputStorage();
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private boolean syncingFluidMenu;
    private boolean syncingKeyMenu;
    private final List<GenericStack> keyInputStacks = createKeyStacks(getKeyInputSlotCount());
    private final List<GenericStack> keyOutputStacks = createKeyStacks(getKeyOutputSlotCount());
    private final Set<Direction> itemOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> fluidOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> keyOutputSides = EnumSet.allOf(Direction.class);
    private AdjacentBlockCapabilityCache<IItemHandler> adjacentItemHandlers;
    private AdjacentBlockCapabilityCache<IFluidHandler> adjacentFluidHandlers;
    private AdjacentBlockCapabilityCache<GenericInternalInventory> adjacentKeyInventories;
    private final ProcessingChannelState[] processingChannels = createProcessingChannels();

    private static String getFluidInputTag(int slot) {
        return switch (slot) {
            case 0 -> FLUID_INPUT_A_TAG;
            case 1 -> FLUID_INPUT_B_TAG;
            default -> "fluid_input_" + slot;
        };
    }

    private static String getFluidOutputTag(int slot) {
        return switch (slot) {
            case 0 -> FLUID_OUTPUT_A_TAG;
            case 1 -> FLUID_OUTPUT_B_TAG;
            default -> "fluid_output_" + slot;
        };
    }

    private static String getKeyInputTag(int slot) {
        return slot == 0 ? KEY_INPUT_TAG : "key_input_" + slot;
    }

    private static String getKeyOutputTag(int slot) {
        return slot == 0 ? KEY_OUTPUT_TAG : "key_output_" + slot;
    }

    private ProcessingChannelState[] createProcessingChannels() {
        int channelCount = getProcessingChannelCount();
        if (channelCount <= 0) {
            throw new IllegalStateException("Processing channel count must be positive");
        }

        ProcessingChannelState[] channels = new ProcessingChannelState[channelCount];
        for (int channel = 0; channel < channelCount; channel++) {
            channels[channel] = new ProcessingChannelState();
        }
        return channels;
    }

    public DataRipperReassemblerBlockEntity(BlockPos blockPos, BlockState blockState) {
        this(DEBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), DEBlocks.DATA_RIPPER_REASSEMBLER.get(), blockPos, blockState);
    }

    protected DataRipperReassemblerBlockEntity(BlockEntityType<? extends DataRipperReassemblerBlockEntity> blockEntityType,
                                               Block machineBlock,
                                               BlockPos blockPos,
                                               BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
        this.upgrades = UpgradeInventories.forMachine(machineBlock, UPGRADE_SLOTS, this::onUpgradesChanged);
        this.getMainNode()
                .setVisualRepresentation(machineBlock)
                .setIdlePowerUsage(1.0D);
        this.setInternalMaxPower(ENERGY_CAPACITY);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.storage.setFilter(new IAEItemFilter() {

            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return slot >= ITEM_INPUT_START_SLOT && slot < ITEM_INPUT_START_SLOT + getItemInputSlotCount();
            }
        });
        initializeItemSlotCapacities();
        syncMenuFluidsFromTanks();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return getGridConnectableSides(getOrientation()).contains(dir) ? AECableType.COVERED : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(orientation.getSide(RelativeSide.FRONT));
        sides.remove(orientation.getSide(RelativeSide.TOP));
        return sides;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateOnlineState();
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
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        refillEnergyCache();
        int remainingTicks = tickBudget;
        boolean autoExportStalled = false;
        while (remainingTicks > 0) {
            boolean hasOutputBeforeWork = hasAnyOutput();
            int progressBudget = isAutoExportEnabled() && hasOutputBeforeWork && !autoExportStalled ? 1 : remainingTicks;
            boolean completedRecipe = false;
            for (int channel = 0; channel < this.processingChannels.length; channel++) {
                completedRecipe |= advanceProcessingChannel(channel, progressBudget);
            }
            remainingTicks -= progressBudget;

            boolean shouldAttemptExport = isAutoExportEnabled() && hasAnyOutput() &&
                    (!autoExportStalled || completedRecipe);
            if (shouldAttemptExport) {
                autoExportStalled = !tryAutoExport();
            }

        }
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean isWorking() {
        for (ProcessingChannelState channel : this.processingChannels) {
            if (channel.progress > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(getMachineBlock()), getMachineBlock().getName(), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, Direction ejectionDirection) {
        PatternPushState state = findPatternPushState(inputHolder);
        if (state == null) {
            return false;
        }

        applyPatternPushState(state, getPatternColor(patternDetails));
        saveChanges();
        markForClientUpdate();
        return true;
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
    }

    public ConfigMenuInventory getFluidInputMenuInventory(int slot) {
        return this.fluidInputMenuInventories.get(slot).createMenuWrapper();
    }

    public ConfigMenuInventory getFluidOutputMenuInventory(int slot) {
        return this.fluidOutputMenuInventories.get(slot).createMenuWrapper();
    }

    public ConfigMenuInventory getKeyInputMenuInventory(int slot) {
        return this.keyInputMenuInventories.get(slot).createMenuWrapper();
    }

    public ConfigMenuInventory getKeyOutputMenuInventory(int slot) {
        return this.keyOutputMenuInventories.get(slot).createMenuWrapper();
    }

    public ConfigMenuInventory getFluidMenuInventoryA() {
        return getFluidInputMenuInventory(0);
    }

    public ConfigMenuInventory getFluidMenuInventoryB() {
        return getFluidInputMenuInventory(1);
    }

    public ConfigMenuInventory getFluidOutputMenuInventoryA() {
        return getFluidOutputMenuInventory(0);
    }

    public ConfigMenuInventory getFluidOutputMenuInventoryB() {
        return getFluidOutputMenuInventory(1);
    }

    public ConfigMenuInventory getKeyMenuInventory() {
        return getKeyInputMenuInventory(0);
    }

    public ConfigMenuInventory getKeyOutputMenuInventory() {
        return getKeyOutputMenuInventory(0);
    }

    public FluidStack getFluidInput(int slot) {
        return this.fluidInputTanks.get(slot).getFluid();
    }

    public FluidStack getFluidOutput(int slot) {
        return this.fluidOutputTanks.get(slot).getFluid();
    }

    public FluidStack getFluidInputA() {
        return getFluidInput(0);
    }

    public FluidStack getFluidInputB() {
        return getFluidInput(1);
    }

    public FluidStack getFluidOutputA() {
        return getFluidOutput(0);
    }

    public FluidStack getFluidOutputB() {
        return getFluidOutput(1);
    }

    public int getFluidInputCapacity() {
        return this.fluidInputTanks.getFirst().getCapacity();
    }

    public int getFluidOutputCapacity() {
        return this.fluidOutputTanks.getFirst().getCapacity();
    }

    public int getParallel() {
        return computeParallel(this.upgrades.getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()));
    }

    public int getItemSlotCapacity() {
        return ITEM_SLOT_CAPACITY;
    }

    /**
     * Returns the number of independently stored fluid inputs.
     */
    public int getFluidInputSlotCount() {
        return 2;
    }

    /**
     * Returns the number of independently stored fluid outputs.
     */
    public int getFluidOutputSlotCount() {
        return 2;
    }

    /**
     * Returns the number of independently stored non-item input keys.
     */
    public int getKeyInputSlotCount() {
        return 1;
    }

    /**
     * Returns the number of independently stored non-item output keys.
     */
    public int getKeyOutputSlotCount() {
        return 1;
    }

    public int getKeyOutputStartSlot() {
        return getKeyInputSlotCount();
    }

    /**
     * Returns the number of item slots that accept recipe inputs.
     *
     * <p>
     * Subclasses may enlarge the shared item buffer, but must keep the output range directly after it so
     * inventory, capability and serialization paths continue to describe the same slots.
     * </p>
     */
    public int getItemInputSlotCount() {
        return ITEM_INPUT_SLOT_COUNT;
    }

    /**
     * Returns the first item output slot for this machine's contiguous item inventory.
     */
    public int getItemOutputStartSlot() {
        return ITEM_INPUT_START_SLOT + getItemInputSlotCount();
    }

    /**
     * Returns the number of item slots that accept completed recipe outputs.
     */
    public int getItemOutputSlotCount() {
        return ITEM_OUTPUT_SLOT_COUNT;
    }

    /**
     * Returns the total number of item slots allocated by this machine.
     */
    public int getStorageSlotCount() {
        return getItemOutputStartSlot() + getItemOutputSlotCount();
    }

    /**
     * Returns how many independent recipe-processing channels this machine has.
     * Subclasses may assign separate or shared input buffers to those channels; all output resources remain shared.
     */
    protected int getProcessingChannelCount() {
        return 1;
    }

    protected int getItemInputStartSlotForChannel(int channel) {
        requireProcessingChannel(channel);
        return ITEM_INPUT_START_SLOT;
    }

    protected int getItemInputSlotCountForChannel(int channel) {
        requireProcessingChannel(channel);
        return getItemInputSlotCount();
    }

    protected int getFluidInputStartSlotForChannel(int channel) {
        requireProcessingChannel(channel);
        return 0;
    }

    protected int getFluidInputSlotCountForChannel(int channel) {
        requireProcessingChannel(channel);
        return getFluidInputSlotCount();
    }

    protected int getKeyInputStartSlotForChannel(int channel) {
        requireProcessingChannel(channel);
        return 0;
    }

    protected int getKeyInputSlotCountForChannel(int channel) {
        requireProcessingChannel(channel);
        return getKeyInputSlotCount();
    }

    public int getProgress() {
        return getProgress(0);
    }

    public int getProgress(int channel) {
        return getProcessingChannel(channel).progress;
    }

    public int getMaxProgress() {
        return getMaxProgress(0);
    }

    public int getMaxProgress(int channel) {
        return getProcessingChannel(channel).maxProgress;
    }

    private ProcessingChannelState getProcessingChannel(int channel) {
        requireProcessingChannel(channel);
        return this.processingChannels[channel];
    }

    private void requireProcessingChannel(int channel) {
        if (channel < 0 || channel >= this.processingChannels.length) {
            throw new IndexOutOfBoundsException("Invalid processing channel: " + channel);
        }
    }

    public long getKeyInputCapacity() {
        return KEY_INPUT_CAPACITY;
    }

    public long getKeyOutputCapacity() {
        return KEY_OUTPUT_CAPACITY;
    }

    public int getItemInputPatternColor(int slot) {
        return this.itemInputPatternColors[slot];
    }

    public int getFluidInputPatternColor(int slot) {
        return this.fluidInputPatternColors[slot];
    }

    public int getKeyInputPatternColor(int slot) {
        return this.keyInputPatternColors[slot];
    }

    public static int computeParallel(int energyCardCount) {
        int installedCards = Math.min(MAX_ENERGY_CARDS, Math.max(0, energyCardCount));
        return installedCards == 0 ? BASE_PARALLEL : BASE_PARALLEL * installedCards * PARALLEL_MULTIPLIER_PER_ENERGY_CARD;
    }

    public boolean isAutoExportEnabled() {
        return this.configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    public Set<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        if (sides.isEmpty()) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.copyOf(sides);
    }

    public void setOutputSideEnabled(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        boolean changed = enabled ? sides.add(side) : sides.remove(side);
        if (!changed) {
            return;
        }

        saveChanges();
        markForClientUpdate();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.suppressAe2DefaultInventorySerialization ? InternalInventory.empty() : this.storage;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
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

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        for (int slot = 0; slot < this.fluidInputTanks.size(); slot++) {
            this.fluidInputTanks.get(slot).readFromNBT(registries, data.getCompound(getFluidInputTag(slot)));
        }
        for (int slot = 0; slot < this.fluidOutputTanks.size(); slot++) {
            this.fluidOutputTanks.get(slot).readFromNBT(registries, data.getCompound(getFluidOutputTag(slot)));
        }
        readKeyStacks(data, registries, this.keyInputStacks, true);
        readKeyStacks(data, registries, this.keyOutputStacks, false);
        readInputPatternColors(data);
        if (data.contains(AUTO_EXPORT_TAG)) {
            this.configManager.readFromNBT(data.getCompound(AUTO_EXPORT_TAG), registries);
        } else {
            this.configManager.readFromNBT(data, registries);
        }
        boolean hasTypedOutputSides = data.contains(ITEM_OUTPUT_SIDES_TAG) || data.contains(FLUID_OUTPUT_SIDES_TAG) || data.contains(KEY_OUTPUT_SIDES_TAG);
        if (hasTypedOutputSides) {
            readOutputSides(data, ITEM_OUTPUT_SIDES_TAG, this.itemOutputSides);
            readOutputSides(data, FLUID_OUTPUT_SIDES_TAG, this.fluidOutputSides);
            readOutputSides(data, KEY_OUTPUT_SIDES_TAG, this.keyOutputSides);
        } else if (data.contains(OUTPUT_SIDES_TAG)) {
            Set<Direction> legacySides = EnumSet.noneOf(Direction.class);
            readOutputSides(data, OUTPUT_SIDES_TAG, legacySides);
            migrateLegacyOutputSidesToItemOnly(legacySides);
        } else {
            copyOutputSidesToAllTypes(EnumSet.allOf(Direction.class));
        }
        readProcessingChannels(data);
        syncMenuFluidsFromTanks();
        syncKeyMenuFromStack();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        boolean previousSuppression = this.suppressAe2DefaultInventorySerialization;
        this.suppressAe2DefaultInventorySerialization = true;
        try {
            super.saveAdditional(data, registries);
        } finally {
            this.suppressAe2DefaultInventorySerialization = previousSuppression;
        }
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        for (int slot = 0; slot < this.fluidInputTanks.size(); slot++) {
            data.put(getFluidInputTag(slot), this.fluidInputTanks.get(slot).writeToNBT(registries, new CompoundTag()));
        }
        for (int slot = 0; slot < this.fluidOutputTanks.size(); slot++) {
            data.put(getFluidOutputTag(slot), this.fluidOutputTanks.get(slot).writeToNBT(registries, new CompoundTag()));
        }
        CompoundTag autoExportData = new CompoundTag();
        this.configManager.writeToNBT(autoExportData, registries);
        data.put(AUTO_EXPORT_TAG, autoExportData);
        data.put(ITEM_OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
        data.put(FLUID_OUTPUT_SIDES_TAG, createOutputSidesTag(this.fluidOutputSides));
        data.put(KEY_OUTPUT_SIDES_TAG, createOutputSidesTag(this.keyOutputSides));
        data.put(OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
        writeProcessingChannels(data);
        writeInputPatternColors(data);
        writeKeyStacks(data, registries, this.keyInputStacks, true);
        writeKeyStacks(data, registries, this.keyOutputStacks, false);
    }

    private void readProcessingChannels(CompoundTag data) {
        readProcessingChannel(data, this.processingChannels[0]);
        for (int channel = 1; channel < this.processingChannels.length; channel++) {
            this.processingChannels[channel].reset();
        }

        if (!data.contains(PROCESSING_CHANNELS_TAG, Tag.TAG_LIST)) {
            return;
        }

        ListTag serializedChannels = data.getList(PROCESSING_CHANNELS_TAG, Tag.TAG_COMPOUND);
        int channelsToRead = Math.min(serializedChannels.size(), this.processingChannels.length);
        for (int channel = 0; channel < channelsToRead; channel++) {
            readProcessingChannel(serializedChannels.getCompound(channel), this.processingChannels[channel]);
        }
    }

    private void writeProcessingChannels(CompoundTag data) {
        writeProcessingChannel(data, this.processingChannels[0]);
        if (this.processingChannels.length == 1) {
            return;
        }

        ListTag serializedChannels = new ListTag();
        for (ProcessingChannelState channel : this.processingChannels) {
            CompoundTag serializedChannel = new CompoundTag();
            writeProcessingChannel(serializedChannel, channel);
            serializedChannels.add(serializedChannel);
        }
        data.put(PROCESSING_CHANNELS_TAG, serializedChannels);
    }

    private void readInputPatternColors(CompoundTag data) {
        readInputPatternColors(data, ITEM_INPUT_PATTERN_COLORS_TAG, this.itemInputPatternColors);
        readInputPatternColors(data, FLUID_INPUT_PATTERN_COLORS_TAG, this.fluidInputPatternColors);
        readInputPatternColors(data, KEY_INPUT_PATTERN_COLORS_TAG, this.keyInputPatternColors);
    }

    private static void readInputPatternColors(CompoundTag data, String tag, int[] target) {
        Arrays.fill(target, 0);
        if (!data.contains(tag, Tag.TAG_INT_ARRAY)) {
            return;
        }

        int[] storedColors = data.getIntArray(tag);
        System.arraycopy(storedColors, 0, target, 0, Math.min(storedColors.length, target.length));
    }

    private void writeInputPatternColors(CompoundTag data) {
        data.putIntArray(ITEM_INPUT_PATTERN_COLORS_TAG, this.itemInputPatternColors);
        data.putIntArray(FLUID_INPUT_PATTERN_COLORS_TAG, this.fluidInputPatternColors);
        data.putIntArray(KEY_INPUT_PATTERN_COLORS_TAG, this.keyInputPatternColors);
    }

    private void clearInputPatternColors() {
        Arrays.fill(this.itemInputPatternColors, 0);
        Arrays.fill(this.fluidInputPatternColors, 0);
        Arrays.fill(this.keyInputPatternColors, 0);
    }

    private void clearEmptyInputPatternColors() {
        for (int slot = 0; slot < this.itemInputPatternColors.length; slot++) {
            if (this.storage.getStackInSlot(ITEM_INPUT_START_SLOT + slot).isEmpty()) {
                this.itemInputPatternColors[slot] = 0;
            }
        }
        for (int slot = 0; slot < this.fluidInputPatternColors.length; slot++) {
            if (this.fluidInputTanks.get(slot).isEmpty()) {
                this.fluidInputPatternColors[slot] = 0;
            }
        }
        for (int slot = 0; slot < this.keyInputPatternColors.length; slot++) {
            if (this.keyInputStacks.get(slot) == null) {
                this.keyInputPatternColors[slot] = 0;
            }
        }
    }

    private static void readProcessingChannel(CompoundTag data, ProcessingChannelState channel) {
        channel.progress = Math.max(0, data.getInt(PROGRESS_TAG));
        channel.maxProgress = data.contains(MAX_PROGRESS_TAG) ? Math.max(1, data.getInt(MAX_PROGRESS_TAG)) : MAX_PROGRESS;
        channel.activeRecipeId = data.contains(ACTIVE_RECIPE_TAG) ? ResourceLocation.tryParse(data.getString(ACTIVE_RECIPE_TAG)) : null;
        channel.recipeMatchCache = null;
        channel.inputReservation = null;
    }

    private static void writeProcessingChannel(CompoundTag data, ProcessingChannelState channel) {
        data.putInt(PROGRESS_TAG, channel.progress);
        data.putInt(MAX_PROGRESS_TAG, channel.maxProgress);
        if (channel.activeRecipeId != null) {
            data.putString(ACTIVE_RECIPE_TAG, channel.activeRecipeId.toString());
        }
    }

    private static void readKeyStacks(CompoundTag data, HolderLookup.Provider registries, List<GenericStack> stacks,
                                      boolean input) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            String tag = input ? getKeyInputTag(slot) : getKeyOutputTag(slot);
            stacks.set(slot, data.contains(tag) ? GenericStack.readTag(registries, data.getCompound(tag)) : null);
        }
    }

    private static void writeKeyStacks(CompoundTag data, HolderLookup.Provider registries, List<GenericStack> stacks,
                                       boolean input) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            GenericStack stack = stacks.get(slot);
            if (stack == null || stack.amount() <= 0L) {
                continue;
            }
            String tag = input ? getKeyInputTag(slot) : getKeyOutputTag(slot);
            data.put(tag, GenericStack.writeTag(registries, stack));
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode != SettingsFrom.MEMORY_CARD) {
            return;
        }

        CompoundTag settings = new CompoundTag();
        settings.putInt(ITEM_OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.itemOutputSides));
        settings.putInt(FLUID_OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.fluidOutputSides));
        settings.putInt(KEY_OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.keyOutputSides));
        settings.putInt(OUTPUT_SIDES_TAG, MemoryCardSettingsHelper.encodeSides(this.itemOutputSides));
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
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.saveChanges();
        this.markForClientUpdate();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        // AEBaseInvBlockEntity already adds getInternalInventory(), which is this.storage.
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
        this.storage.clear();
        this.fluidInputTanks.forEach(tank -> tank.setFluid(FluidStack.EMPTY));
        this.fluidOutputTanks.forEach(tank -> tank.setFluid(FluidStack.EMPTY));
        clearKeyStacks(this.keyInputStacks);
        clearKeyStacks(this.keyOutputStacks);
        clearInputPatternColors();
        resetProcessingState();
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

    private void updateOnlineState() {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (!(state.getBlock() instanceof DataRipperReassemblerBlock)) {
            return;
        }

        boolean working = isWorking();
        if (state.hasProperty(DataRipperReassemblerBlock.LIT) && state.getValue(DataRipperReassemblerBlock.LIT) != working) {
            this.level.setBlock(this.worldPosition, state.setValue(DataRipperReassemblerBlock.LIT, working), 3);
        }
    }

    private void onConfigChanged(IConfigManager manager, Setting<?> setting) {
        if (setting == Settings.AUTO_EXPORT) {
            saveChanges();
            markForClientUpdate();
        }
    }

    private void applyMemoryCardSettings(CompoundTag settings) {
        boolean changed = false;
        boolean hasTypedOutputSides = settings.contains(ITEM_OUTPUT_SIDES_TAG) || settings.contains(FLUID_OUTPUT_SIDES_TAG) || settings.contains(KEY_OUTPUT_SIDES_TAG);
        if (hasTypedOutputSides) {
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.ITEMS,
                    settings.getInt(ITEM_OUTPUT_SIDES_TAG));
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.FLUIDS,
                    settings.getInt(FLUID_OUTPUT_SIDES_TAG));
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.KEYS,
                    settings.getInt(KEY_OUTPUT_SIDES_TAG));
        } else if (settings.contains(OUTPUT_SIDES_TAG)) {
            int legacyMask = settings.getInt(OUTPUT_SIDES_TAG);
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.ITEMS, legacyMask);
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.FLUIDS, MemoryCardSettingsHelper.ALL_DIRECTIONS_MASK);
            changed |= replaceOutputSides(DigitalStorageDepotOutputType.KEYS, MemoryCardSettingsHelper.ALL_DIRECTIONS_MASK);
        }
        if (!changed) {
            return;
        }

        this.saveChanges();
        this.markForClientUpdate();
    }

    private void onUpgradesChanged() {
        saveChanges();
        markForClientUpdate();
    }

    private void initializeItemSlotCapacities() {
        for (int slot = 0; slot < this.storage.size(); slot++) {
            this.storage.setMaxStackSize(slot, ITEM_SLOT_CAPACITY);
        }
    }

    private boolean advanceProcessingChannel(int channel, int tickBudget) {
        int remainingTicks = tickBudget;
        boolean completedRecipe = false;
        while (remainingTicks > 0) {
            RecipeAdvance advance = processRecipe(channel, remainingTicks);
            if (advance.elapsedTicks() <= 0) {
                throw new IllegalStateException("Processing channel did not consume its tick budget");
            }
            remainingTicks -= advance.elapsedTicks();
            completedRecipe |= advance.completedRecipe();
        }
        return completedRecipe;
    }

    private RecipeAdvance processRecipe(int channel, int tickBudget) {
        if (!isOnline()) {
            resetProcessingState(channel);
            return RecipeAdvance.blocked(tickBudget);
        }

        RecipeHolder<DataRipperReassemblerRecipe> recipeHolder = getActiveOrMatchingRecipe(channel);
        if (recipeHolder == null) {
            resetProcessingState(channel);
            return RecipeAdvance.blocked(tickBudget);
        }

        DataRipperReassemblerRecipe recipe = recipeHolder.value();
        if (!canAcceptItemOutputs(recipe, recipe.getItemOutputs())) {
            resetProcessingState(channel);
            return RecipeAdvance.blocked(tickBudget);
        }

        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        if (!recipeHolder.id().equals(processingChannel.activeRecipeId)) {
            processingChannel.activeRecipeId = recipeHolder.id();
            processingChannel.progress = 0;
            processingChannel.maxProgress = getEffectiveProcessTicks(recipe);
            processingChannel.inputReservation = null;
            setChanged();
        }
        if (!ensureRecipeInputReservation(channel, recipe)) {
            resetProcessingState(channel);
            return RecipeAdvance.blocked(tickBudget);
        }

        processingChannel.maxProgress = getEffectiveProcessTicks(recipe);
        processingChannel.progress = Math.max(0, Math.min(processingChannel.progress, processingChannel.maxProgress - 1));
        BatchTickProgression.Segment segment = BatchTickProgression.advanceToBoundary(
                processingChannel.progress,
                processingChannel.maxProgress,
                tickBudget);
        processingChannel.progress = segment.progress();
        setChanged();

        if (!segment.reachedBoundary()) {
            return RecipeAdvance.progressed(segment.elapsedTicks());
        }

        List<ItemStack> itemOutputs = recipe.getCraftedItemOutputs();
        if (!canAcceptItemOutputs(recipe, itemOutputs)) {
            resetProcessingState(channel);
            return RecipeAdvance.blocked(segment.elapsedTicks());
        }
        for (int batch = 0; batch < getParallel(); batch++) {
            List<ItemStack> batchOutputs = batch == 0 ? itemOutputs : recipe.getCraftedItemOutputs();
            if (!canAcceptItemOutputs(recipe, batchOutputs)) {
                break;
            }
            if (batch > 0 && !ensureRecipeInputReservation(channel, recipe)) {
                break;
            }

            RecipeProcessingState processingState = captureRecipeProcessingState();
            if (!consumeReservedRecipeInputs(channel) || !insertRecipeOutputs(recipe, batchOutputs)) {
                restoreRecipeProcessingState(processingState);
                break;
            }
            clearEmptyInputPatternColors();
        }

        resetProcessingState(channel);
        saveChanges();
        markForClientUpdate();
        return RecipeAdvance.completed(segment.elapsedTicks());
    }

    private void resetProcessingState() {
        for (int channel = 0; channel < this.processingChannels.length; channel++) {
            resetProcessingState(channel);
        }
    }

    private void resetProcessingState(int channel) {
        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        if (processingChannel.isIdle()) {
            return;
        }

        processingChannel.reset();
        setChanged();
    }

    private int getEffectiveProcessTicks(DataRipperReassemblerRecipe recipe) {
        int speedCards = this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        int reducedTicks = recipe.getProcessTicks() - speedCards * 40;
        return Math.max(1, reducedTicks);
    }

    private RecipeHolder<DataRipperReassemblerRecipe> getActiveOrMatchingRecipe(int channel) {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return null;
        }

        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        RecipeMatchKey cacheKey = createRecipeMatchKey(channel);
        RecipeMatchCache cached = processingChannel.recipeMatchCache;
        if (cached != null && cached.key().equals(cacheKey)) {
            ResourceLocation recipeId = cached.recipeId();
            if (recipeId == null) {
                return null;
            }

            RecipeHolder<DataRipperReassemblerRecipe> cachedRecipe = getRecipeById(currentLevel, recipeId);
            if (cachedRecipe != null) {
                return cachedRecipe;
            }
        }

        DataRipperReassemblerRecipeInput input = createRecipeInput(channel);
        RecipeHolder<DataRipperReassemblerRecipe> match = null;
        if (processingChannel.activeRecipeId != null) {
            RecipeHolder<DataRipperReassemblerRecipe> active = getRecipeById(currentLevel, processingChannel.activeRecipeId);
            if (active != null && active.value().matches(input, currentLevel)) {
                match = active;
            }
        }

        if (match == null) {
            match = findMatchingRecipe(currentLevel, input, getOtherActiveRecipeIds(channel));
        }
        if (match == null) {
            match = findMatchingRecipe(currentLevel, input, Set.of());
        }

        processingChannel.recipeMatchCache = new RecipeMatchCache(cacheKey, match == null ? null : match.id());
        return match;
    }

    private @Nullable RecipeHolder<DataRipperReassemblerRecipe> findMatchingRecipe(
                                                                                   Level level,
                                                                                   DataRipperReassemblerRecipeInput input,
                                                                                   Set<ResourceLocation> excludedRecipeIds) {
        for (RecipeHolder<DataRipperReassemblerRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get())) {
            if (!excludedRecipeIds.contains(holder.id()) && holder.value().matches(input, level)) {
                return holder;
            }
        }
        return null;
    }

    private Set<ResourceLocation> getOtherActiveRecipeIds(int excludedChannel) {
        Set<ResourceLocation> recipeIds = new HashSet<>();
        for (int channel = 0; channel < this.processingChannels.length; channel++) {
            if (channel == excludedChannel) {
                continue;
            }

            ResourceLocation activeRecipeId = this.processingChannels[channel].activeRecipeId;
            if (activeRecipeId != null) {
                recipeIds.add(activeRecipeId);
            }
        }
        return recipeIds;
    }

    private RecipeMatchKey createRecipeMatchKey(int channel) {
        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        InputReservationUsage reservedInputs = getReservedInputUsage(channel);
        List<RecipeStackIdentity> items = new ArrayList<>(getItemInputSlotCountForChannel(channel));
        for (int i = 0; i < getItemInputSlotCountForChannel(channel); i++) {
            int slot = getItemInputStartSlotForChannel(channel) + i;
            items.add(createItemStackIdentity(getAvailableItemInput(slot, reservedInputs)));
        }
        List<RecipeStackIdentity> fluids = new ArrayList<>(getFluidInputSlotCountForChannel(channel));
        for (int slot = 0; slot < getFluidInputSlotCountForChannel(channel); slot++) {
            int inputSlot = getFluidInputStartSlotForChannel(channel) + slot;
            fluids.add(createFluidStackIdentity(getAvailableFluidInput(inputSlot, reservedInputs)));
        }
        List<RecipeStackIdentity> keys = new ArrayList<>(getKeyInputSlotCountForChannel(channel));
        for (int slot = 0; slot < getKeyInputSlotCountForChannel(channel); slot++) {
            int inputSlot = getKeyInputStartSlotForChannel(channel) + slot;
            keys.add(createKeyStackIdentity(getAvailableKeyInput(inputSlot, reservedInputs)));
        }
        return new RecipeMatchKey(
                RecipeReloadEpoch.current(),
                processingChannel.activeRecipeId,
                List.copyOf(items),
                List.copyOf(fluids),
                List.copyOf(keys),
                Set.copyOf(getOtherActiveRecipeIds(channel)));
    }

    private static RecipeStackIdentity createItemStackIdentity(ItemStack stack) {
        if (stack.isEmpty()) {
            return RecipeStackIdentity.EMPTY;
        }
        return new RecipeStackIdentity(AEItemKey.of(stack), stack.getCount());
    }

    private static RecipeStackIdentity createFluidStackIdentity(FluidStack stack) {
        if (stack.isEmpty()) {
            return RecipeStackIdentity.EMPTY;
        }
        return new RecipeStackIdentity(AEFluidKey.of(stack), stack.getAmount());
    }

    private static RecipeStackIdentity createKeyStackIdentity(@Nullable GenericStack stack) {
        if (stack == null || stack.amount() <= 0L) {
            return RecipeStackIdentity.EMPTY;
        }
        return new RecipeStackIdentity(stack.what(), stack.amount());
    }

    private static @Nullable RecipeHolder<DataRipperReassemblerRecipe> getRecipeById(
                                                                                     Level level, ResourceLocation recipeId) {
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof DataRipperReassemblerRecipe)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        RecipeHolder<DataRipperReassemblerRecipe> typedHolder = (RecipeHolder<DataRipperReassemblerRecipe>) holder;
        return typedHolder;
    }

    private DataRipperReassemblerRecipeInput createRecipeInput(int channel) {
        InputReservationUsage reservedInputs = getReservedInputUsage(channel);
        List<ItemStack> inputs = new ArrayList<>(getItemInputSlotCountForChannel(channel));
        for (int i = 0; i < getItemInputSlotCountForChannel(channel); i++) {
            inputs.add(getAvailableItemInput(getItemInputStartSlotForChannel(channel) + i, reservedInputs));
        }
        List<GenericStack> fluids = new ArrayList<>(getFluidInputSlotCountForChannel(channel));
        for (int slot = 0; slot < getFluidInputSlotCountForChannel(channel); slot++) {
            GenericStack fluid = createFluidGenericStack(
                    getAvailableFluidInput(getFluidInputStartSlotForChannel(channel) + slot, reservedInputs));
            if (fluid != null) {
                fluids.add(fluid);
            }
        }
        List<GenericStack> keys = new ArrayList<>(getKeyInputSlotCountForChannel(channel));
        for (int slot = 0; slot < getKeyInputSlotCountForChannel(channel); slot++) {
            keys.add(getAvailableKeyInput(getKeyInputStartSlotForChannel(channel) + slot, reservedInputs));
        }
        return new DataRipperReassemblerRecipeInput(inputs, fluids, keys);
    }

    private boolean canAcceptItemOutputs(DataRipperReassemblerRecipe recipe, List<ItemStack> itemOutputs) {
        ItemStack[] simulated = new ItemStack[getItemOutputSlotCount()];
        for (int i = 0; i < getItemOutputSlotCount(); i++) {
            simulated[i] = this.storage.getStackInSlot(getItemOutputStartSlot() + i).copy();
        }

        for (ItemStack output : itemOutputs) {
            if (output.isEmpty()) {
                continue;
            }

            ItemStack remaining = output.copy();
            for (int i = 0; i < simulated.length && !remaining.isEmpty(); i++) {
                remaining = insertIntoOutputSlot(simulated, i, remaining, false);
            }

            if (!remaining.isEmpty()) {
                return false;
            }
        }

        return canAcceptFluidOutputs(recipe) && canAcceptKeyOutput(recipe);
    }

    private boolean ensureRecipeInputReservation(int channel, DataRipperReassemblerRecipe recipe) {
        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        if (processingChannel.inputReservation != null) {
            return isRecipeInputReservationAvailable(processingChannel.inputReservation);
        }

        RecipeInputReservation reservation = createRecipeInputReservation(channel, recipe);
        if (reservation == null) {
            return false;
        }

        processingChannel.inputReservation = reservation;
        return true;
    }

    private @Nullable RecipeInputReservation createRecipeInputReservation(int channel,
                                                                          DataRipperReassemblerRecipe recipe) {
        Map<AEFluidKey, Long> requiredFluidAmounts = recipe.getMergedFluidInputAmounts();
        if (requiredFluidAmounts == null) {
            return null;
        }
        for (long amount : requiredFluidAmounts.values()) {
            if (amount > Integer.MAX_VALUE) {
                return null;
            }
        }

        InputReservationUsage reservedInputs = getReservedInputUsage(channel);
        List<ReservedItemInput> itemInputs = new ArrayList<>();
        for (DataRipperReassemblerIngredient countedIngredient : recipe.getItemInputs()) {
            int remaining = countedIngredient.count();
            for (int i = 0; i < getItemInputSlotCountForChannel(channel) && remaining > 0; i++) {
                int slot = getItemInputStartSlotForChannel(channel) + i;
                ItemStack stack = this.storage.getStackInSlot(slot);
                if (stack.isEmpty() || !countedIngredient.ingredient().test(stack)) {
                    continue;
                }

                int available = Math.max(0, stack.getCount() - reservedInputs.itemAmounts[slot]);
                int reservedAmount = Math.min(remaining, available);
                if (reservedAmount <= 0) {
                    continue;
                }

                addReservedItemInput(itemInputs, slot, stack, reservedAmount);
                reservedInputs.itemAmounts[slot] += reservedAmount;
                remaining -= reservedAmount;
            }

            if (remaining > 0) {
                return null;
            }
        }

        List<ReservedFluidInput> fluidInputs = new ArrayList<>();
        for (Map.Entry<AEFluidKey, Long> requirement : requiredFluidAmounts.entrySet()) {
            AEFluidKey requiredFluid = requirement.getKey();
            int remaining = requirement.getValue().intValue();
            for (int offset = 0; offset < getFluidInputSlotCountForChannel(channel) && remaining > 0; offset++) {
                int slot = getFluidInputStartSlotForChannel(channel) + offset;
                FluidTank tank = this.fluidInputTanks.get(slot);
                if (!matchesFluidKey(tank.getFluid(), requiredFluid)) {
                    continue;
                }

                int available = Math.max(0, tank.getFluidAmount() - reservedInputs.fluidAmounts[slot]);
                int reservedAmount = Math.min(remaining, available);
                if (reservedAmount <= 0) {
                    continue;
                }

                fluidInputs.add(new ReservedFluidInput(slot, requiredFluid, reservedAmount));
                reservedInputs.fluidAmounts[slot] += reservedAmount;
                remaining -= reservedAmount;
            }

            if (remaining > 0) {
                return null;
            }
        }

        List<ReservedKeyInput> keyInputs = new ArrayList<>();
        GenericStack requiredKey = recipe.getKeyInput();
        if (requiredKey != null) {
            AEKey requiredKeyType = requiredKey.what();
            if (requiredKeyType == null || requiredKey.amount() <= 0L) {
                return null;
            }
            long remaining = requiredKey.amount();
            for (int offset = 0; offset < getKeyInputSlotCountForChannel(channel) && remaining > 0L; offset++) {
                int slot = getKeyInputStartSlotForChannel(channel) + offset;
                GenericStack stack = this.keyInputStacks.get(slot);
                if (stack == null || !requiredKeyType.equals(stack.what())) {
                    continue;
                }

                long available = Math.max(0L, stack.amount() - reservedInputs.keyAmounts[slot]);
                long reservedAmount = Math.min(remaining, available);
                if (reservedAmount <= 0L) {
                    continue;
                }

                keyInputs.add(new ReservedKeyInput(slot, requiredKeyType, reservedAmount));
                reservedInputs.keyAmounts[slot] += reservedAmount;
                remaining -= reservedAmount;
            }

            if (remaining > 0) {
                return null;
            }
        }

        return new RecipeInputReservation(itemInputs, fluidInputs, keyInputs);
    }

    private static void addReservedItemInput(List<ReservedItemInput> reservedInputs, int slot, ItemStack stack,
                                             int amount) {
        for (int index = 0; index < reservedInputs.size(); index++) {
            ReservedItemInput existing = reservedInputs.get(index);
            if (existing.slot != slot) {
                continue;
            }

            reservedInputs.set(index, new ReservedItemInput(slot, stack.copyWithCount(existing.stack.getCount() + amount)));
            return;
        }
        reservedInputs.add(new ReservedItemInput(slot, stack.copyWithCount(amount)));
    }

    private boolean isRecipeInputReservationAvailable(RecipeInputReservation reservation) {
        for (ReservedItemInput reservedInput : reservation.itemInputs) {
            ItemStack stack = this.storage.getStackInSlot(reservedInput.slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, reservedInput.stack) ||
                    stack.getCount() < reservedInput.stack.getCount()) {
                return false;
            }
        }
        for (ReservedFluidInput reservedInput : reservation.fluidInputs) {
            FluidTank tank = this.fluidInputTanks.get(reservedInput.slot);
            if (!matchesFluidKey(tank.getFluid(), reservedInput.key) || tank.getFluidAmount() < reservedInput.amount) {
                return false;
            }
        }
        for (ReservedKeyInput reservedInput : reservation.keyInputs) {
            GenericStack stack = this.keyInputStacks.get(reservedInput.slot);
            if (stack == null || !reservedInput.key.equals(stack.what()) || stack.amount() < reservedInput.amount) {
                return false;
            }
        }
        return true;
    }

    private boolean consumeReservedRecipeInputs(int channel) {
        ProcessingChannelState processingChannel = getProcessingChannel(channel);
        RecipeInputReservation reservation = processingChannel.inputReservation;
        if (reservation == null || !isRecipeInputReservationAvailable(reservation)) {
            return false;
        }

        boolean previousPreservation = this.preservingInputPatternColors;
        this.preservingInputPatternColors = true;
        try {
            for (ReservedItemInput reservedInput : reservation.itemInputs) {
                ItemStack stack = this.storage.getStackInSlot(reservedInput.slot);
                ItemStack updated = stack.copy();
                updated.shrink(reservedInput.stack.getCount());
                this.storage.setItemDirect(reservedInput.slot, updated);
            }
            for (ReservedFluidInput reservedInput : reservation.fluidInputs) {
                FluidTank tank = this.fluidInputTanks.get(reservedInput.slot);
                tank.drain(reservedInput.amount, IFluidHandler.FluidAction.EXECUTE);
            }

            if (!reservation.keyInputs.isEmpty()) {
                for (ReservedKeyInput reservedInput : reservation.keyInputs) {
                    GenericStack stack = this.keyInputStacks.get(reservedInput.slot);
                    long remaining = stack.amount() - reservedInput.amount;
                    this.keyInputStacks.set(reservedInput.slot,
                            remaining <= 0L ? null : new GenericStack(reservedInput.key, remaining));
                }
                syncKeyMenuFromStack();
            }
        } finally {
            this.preservingInputPatternColors = previousPreservation;
        }

        processingChannel.inputReservation = null;
        return true;
    }

    private InputReservationUsage getReservedInputUsage(int excludedChannel) {
        InputReservationUsage usage = new InputReservationUsage(
                new int[this.storage.size()],
                new int[this.fluidInputTanks.size()],
                new long[this.keyInputStacks.size()]);
        for (int channel = 0; channel < this.processingChannels.length; channel++) {
            if (channel == excludedChannel) {
                continue;
            }

            RecipeInputReservation reservation = this.processingChannels[channel].inputReservation;
            if (reservation == null) {
                continue;
            }
            for (ReservedItemInput reservedInput : reservation.itemInputs) {
                usage.itemAmounts[reservedInput.slot] += reservedInput.stack.getCount();
            }
            for (ReservedFluidInput reservedInput : reservation.fluidInputs) {
                usage.fluidAmounts[reservedInput.slot] += reservedInput.amount;
            }
            for (ReservedKeyInput reservedInput : reservation.keyInputs) {
                usage.keyAmounts[reservedInput.slot] += reservedInput.amount;
            }
        }
        return usage;
    }

    private ItemStack getAvailableItemInput(int slot, InputReservationUsage reservedInputs) {
        ItemStack stack = this.storage.getStackInSlot(slot);
        int available = Math.max(0, stack.getCount() - reservedInputs.itemAmounts[slot]);
        return available <= 0 ? ItemStack.EMPTY : stack.copyWithCount(available);
    }

    private FluidStack getAvailableFluidInput(int slot, InputReservationUsage reservedInputs) {
        FluidStack stack = this.fluidInputTanks.get(slot).getFluid();
        int available = Math.max(0, stack.getAmount() - reservedInputs.fluidAmounts[slot]);
        if (available <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack availableStack = stack.copy();
        availableStack.setAmount(available);
        return availableStack;
    }

    private @Nullable GenericStack getAvailableKeyInput(int slot, InputReservationUsage reservedInputs) {
        GenericStack stack = this.keyInputStacks.get(slot);
        if (stack == null || stack.what() == null) {
            return null;
        }

        long available = Math.max(0L, stack.amount() - reservedInputs.keyAmounts[slot]);
        return available <= 0L ? null : new GenericStack(stack.what(), available);
    }

    private boolean insertRecipeOutputs(DataRipperReassemblerRecipe recipe, List<ItemStack> itemOutputs) {
        for (ItemStack output : itemOutputs) {
            if (output.isEmpty()) {
                continue;
            }

            ItemStack remaining = output.copy();
            for (int i = 0; i < getItemOutputSlotCount() && !remaining.isEmpty(); i++) {
                remaining = insertIntoOutputSlot(null, i, remaining, true);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }

        for (GenericStack fluidOutput : recipe.getFluidOutputs()) {
            if (!(fluidOutput.what() instanceof AEFluidKey fluidKey) || fluidOutput.amount() <= 0) {
                return false;
            }
            if (!insertFluidOutput(fluidKey, fluidOutput.amount())) {
                return false;
            }
        }

        GenericStack keyOutput = recipe.getKeyOutput();
        if (keyOutput == null || keyOutput.what() == null || keyOutput.amount() <= 0) {
            return true;
        }
        return insertKeyOutput(keyOutput);
    }

    private boolean canAcceptFluidOutputs(DataRipperReassemblerRecipe recipe) {
        List<FluidStack> simulated = copyFluidStacks(this.fluidOutputTanks);

        for (GenericStack output : recipe.getFluidOutputs()) {
            if (!(output.what() instanceof AEFluidKey fluidKey) || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) {
                return false;
            }

            int amount = (int) output.amount();
            boolean inserted = false;
            for (int slot = 0; slot < simulated.size(); slot++) {
                FluidStack current = simulated.get(slot);
                if (!matchesFluidKey(current, fluidKey) && !current.isEmpty()) {
                    continue;
                }
                if ((long) current.getAmount() + amount > this.fluidOutputTanks.get(slot).getCapacity()) {
                    continue;
                }
                if (current.isEmpty()) {
                    simulated.set(slot, fluidKey.toStack(amount));
                } else {
                    current.setAmount(current.getAmount() + amount);
                }
                inserted = true;
                break;
            }
            if (!inserted) {
                return false;
            }
        }

        return true;
    }

    private boolean canAcceptKeyOutput(DataRipperReassemblerRecipe recipe) {
        GenericStack keyOutput = recipe.getKeyOutput();
        if (keyOutput == null || keyOutput.what() == null || keyOutput.amount() <= 0) {
            return true;
        }

        return canStoreKeyAmount(this.keyOutputStacks, keyOutput, getKeyOutputCapacity());
    }

    private boolean insertKeyOutput(GenericStack stack) {
        if (stack.what() == null || stack.amount() <= 0 || !canStoreKeyAmount(this.keyOutputStacks, stack,
                getKeyOutputCapacity())) {
            return false;
        }
        storeKeyAmount(this.keyOutputStacks, stack.what(), stack.amount(), getKeyOutputCapacity());
        syncKeyMenuFromStack();
        return true;
    }

    private boolean insertFluidOutput(AEFluidKey fluidKey, long amountLong) {
        if (amountLong <= 0 || amountLong > Integer.MAX_VALUE) {
            return false;
        }
        int amount = (int) amountLong;
        for (FluidTank tank : this.fluidOutputTanks) {
            if (!matchesFluidKey(tank.getFluid(), fluidKey) && !tank.isEmpty()) {
                continue;
            }
            if (tank.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE) == amount) {
                return true;
            }
        }
        return false;
    }

    private RecipeProcessingState captureRecipeProcessingState() {
        ItemStack[] itemSlots = new ItemStack[this.storage.size()];
        for (int slot = 0; slot < itemSlots.length; slot++) {
            itemSlots[slot] = this.storage.getStackInSlot(slot).copy();
        }
        return new RecipeProcessingState(itemSlots, copyFluidStacks(this.fluidInputTanks),
                copyFluidStacks(this.fluidOutputTanks), copyKeyStacks(this.keyInputStacks),
                copyKeyStacks(this.keyOutputStacks));
    }

    private void restoreRecipeProcessingState(RecipeProcessingState state) {
        boolean previousPreservation = this.preservingInputPatternColors;
        this.preservingInputPatternColors = true;
        try {
            for (int slot = 0; slot < state.itemSlots.length; slot++) {
                this.storage.setItemDirect(slot, state.itemSlots[slot].copy());
            }
            restoreFluidStacks(this.fluidInputTanks, state.fluidInputs);
            restoreFluidStacks(this.fluidOutputTanks, state.fluidOutputs);
            restoreKeyStacks(this.keyInputStacks, state.keyInputs);
            restoreKeyStacks(this.keyOutputStacks, state.keyOutputs);
        } finally {
            this.preservingInputPatternColors = previousPreservation;
        }
        syncMenuFluidsFromTanks();
        syncKeyMenuFromStack();
    }

    private ItemStack insertIntoOutputSlot(ItemStack[] simulated, int outputIndex, ItemStack stack, boolean modulate) {
        int slot = getItemOutputStartSlot() + outputIndex;
        ItemStack current = simulated != null ? simulated[outputIndex] : this.storage.getStackInSlot(slot);
        int slotLimit = this.storage.getSlotLimit(slot);

        if (current.isEmpty()) {
            int inserted = Math.min(stack.getCount(), slotLimit);
            ItemStack newStack = stack.copyWithCount(inserted);
            if (simulated != null) {
                simulated[outputIndex] = newStack;
            } else if (modulate) {
                this.storage.setItemDirect(slot, newStack);
            }

            ItemStack remaining = stack.copy();
            remaining.shrink(inserted);
            return remaining;
        }

        if (!ItemStack.isSameItemSameComponents(current, stack)) {
            return stack;
        }

        int maxCount = slotLimit;
        int free = maxCount - current.getCount();
        if (free <= 0) {
            return stack;
        }

        int inserted = Math.min(free, stack.getCount());
        ItemStack updated = current.copy();
        updated.grow(inserted);
        if (simulated != null) {
            simulated[outputIndex] = updated;
        } else if (modulate) {
            this.storage.setItemDirect(slot, updated);
        }

        ItemStack remaining = stack.copy();
        remaining.shrink(inserted);
        return remaining;
    }

    private boolean tryAutoExport() {
        if (!isAutoExportEnabled()) {
            return false;
        }

        boolean changed = false;
        if (hasItemOutput()) {
            changed = exportItemOutputs(getAdjacentItemHandlers(this.itemOutputSides));
        }
        if (hasFluidOutput()) {
            List<IFluidHandler> fluidHandlers = getAdjacentFluidHandlers(this.fluidOutputSides);
            for (FluidTank tank : this.fluidOutputTanks) {
                changed |= exportFluidOutput(tank, fluidHandlers);
            }
        }
        if (hasKeyOutput()) {
            List<GenericInternalInventory> keyInventories = getAdjacentKeyInventories(this.keyOutputSides);
            for (int slot = 0; slot < this.keyOutputStacks.size(); slot++) {
                changed |= exportKeyOutput(slot, keyInventories);
            }
        }

        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
        return changed;
    }

    private boolean hasItemOutput() {
        for (int slot = getItemOutputStartSlot(); slot < getItemOutputStartSlot() + getItemOutputSlotCount(); slot++) {
            if (!this.storage.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKeyOutput() {
        return hasStoredKeys(this.keyOutputStacks);
    }

    private boolean hasAnyOutput() {
        return hasItemOutput() || hasFluidOutput() || hasKeyOutput();
    }

    private boolean hasFluidOutput() {
        return this.fluidOutputTanks.stream().anyMatch(tank -> !tank.getFluid().isEmpty());
    }

    private boolean exportItemOutputs(List<IItemHandler> adjacentHandlers) {
        boolean changed = false;
        for (int i = 0; i < getItemOutputSlotCount(); i++) {
            int slot = getItemOutputStartSlot() + i;
            ItemStack stack = this.storage.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remaining = insertIntoAdjacentHandlers(stack, adjacentHandlers);
            if (!ItemStack.matches(stack, remaining)) {
                this.storage.setItemDirect(slot, remaining);
                changed = true;
            }
        }
        return changed;
    }

    private boolean exportFluidOutput(FluidTank tank, List<IFluidHandler> adjacentHandlers) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            return false;
        }

        int remaining = fluid.getAmount();
        for (IFluidHandler handler : adjacentHandlers) {
            if (remaining <= 0) {
                break;
            }

            FluidStack toInsert = fluid.copy();
            toInsert.setAmount(remaining);
            int accepted = handler.fill(toInsert, IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                remaining -= accepted;
            }
        }

        int exported = fluid.getAmount() - remaining;
        if (exported <= 0) {
            return false;
        }

        tank.drain(exported, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private boolean exportKeyOutput(int slot, List<GenericInternalInventory> adjacentInventories) {
        GenericStack output = this.keyOutputStacks.get(slot);
        if (output == null || output.what() == null || output.amount() <= 0L) {
            return false;
        }

        AEKey what = output.what();
        long originalAmount = output.amount();
        long remaining = originalAmount;
        for (GenericInternalInventory inventory : adjacentInventories) {
            if (remaining <= 0L) {
                break;
            }
            if (!inventory.canInsert() || !inventory.isSupportedType(what)) {
                continue;
            }

            inventory.beginBatch();
            try {
                for (int inventorySlot = 0; inventorySlot < inventory.size() && remaining > 0L; inventorySlot++) {
                    if (!inventory.isAllowedIn(inventorySlot, what)) {
                        continue;
                    }
                    long inserted = inventory.insert(inventorySlot, what, remaining, Actionable.MODULATE);
                    if (inserted > 0L) {
                        remaining -= Math.min(inserted, remaining);
                    }
                }
            } finally {
                inventory.endBatch();
            }
        }

        if (remaining == originalAmount) {
            return false;
        }

        this.keyOutputStacks.set(slot, remaining <= 0L ? null : new GenericStack(what, remaining));
        syncKeyMenuFromStack();
        return true;
    }

    private ItemStack insertIntoAdjacentHandlers(ItemStack stack, List<IItemHandler> adjacentHandlers) {
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : adjacentHandlers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = ItemHandlerHelper.insertItem(handler, remaining, false);
        }
        return remaining;
    }

    private List<IItemHandler> getAdjacentItemHandlers(Set<Direction> outputSides) {
        if (!initializeAdjacentCapabilityCaches()) {
            return List.of();
        }
        return this.adjacentItemHandlers.getAll(outputSides);
    }

    private List<IFluidHandler> getAdjacentFluidHandlers(Set<Direction> outputSides) {
        if (!initializeAdjacentCapabilityCaches()) {
            return List.of();
        }
        return this.adjacentFluidHandlers.getAll(outputSides);
    }

    private List<GenericInternalInventory> getAdjacentKeyInventories(Set<Direction> outputSides) {
        if (!initializeAdjacentCapabilityCaches()) {
            return List.of();
        }
        return this.adjacentKeyInventories.getAll(outputSides);
    }

    private boolean initializeAdjacentCapabilityCaches() {
        if (this.adjacentItemHandlers != null) {
            return true;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }

        AdjacentBlockCapabilityCache<IItemHandler> itemHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.ItemHandler.BLOCK,
                serverLevel,
                this.worldPosition,
                () -> !this.isRemoved());
        AdjacentBlockCapabilityCache<IFluidHandler> fluidHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.FluidHandler.BLOCK,
                serverLevel,
                this.worldPosition,
                () -> !this.isRemoved());
        AdjacentBlockCapabilityCache<GenericInternalInventory> keyInventories = new AdjacentBlockCapabilityCache<>(
                AECapabilities.GENERIC_INTERNAL_INV,
                serverLevel,
                this.worldPosition,
                () -> !this.isRemoved());
        this.adjacentItemHandlers = itemHandlers;
        this.adjacentFluidHandlers = fluidHandlers;
        this.adjacentKeyInventories = keyInventories;
        return true;
    }

    private Set<Direction> getOutputSidesInternal(DigitalStorageDepotOutputType outputType) {
        return switch (outputType) {
            case ITEMS -> this.itemOutputSides;
            case FLUIDS -> this.fluidOutputSides;
            case KEYS -> this.keyOutputSides;
        };
    }

    private boolean replaceOutputSides(DigitalStorageDepotOutputType outputType, int sidesMask) {
        Set<Direction> sides = getOutputSidesInternal(outputType);
        return MemoryCardSettingsHelper.replaceSides(sides, sidesMask);
    }

    private void copyOutputSidesToAllTypes(Set<Direction> sides) {
        this.itemOutputSides.clear();
        this.fluidOutputSides.clear();
        this.keyOutputSides.clear();
        this.itemOutputSides.addAll(sides);
        this.fluidOutputSides.addAll(sides);
        this.keyOutputSides.addAll(sides);
    }

    private void migrateLegacyOutputSidesToItemOnly(Set<Direction> legacySides) {
        this.itemOutputSides.clear();
        this.itemOutputSides.addAll(legacySides);
        this.fluidOutputSides.clear();
        this.fluidOutputSides.addAll(EnumSet.allOf(Direction.class));
        this.keyOutputSides.clear();
        this.keyOutputSides.addAll(EnumSet.allOf(Direction.class));
    }

    private static void readOutputSides(CompoundTag data, String tagName, Set<Direction> target) {
        target.clear();
        for (Tag name : data.getList(tagName, Tag.TAG_STRING)) {
            Direction side = Direction.byName(name.getAsString());
            if (side != null) {
                target.add(side);
            }
        }
    }

    private static ListTag createOutputSidesTag(Set<Direction> sides) {
        ListTag tag = new ListTag();
        for (Direction side : sides) {
            tag.add(StringTag.valueOf(side.getName()));
        }
        return tag;
    }

    private PatternPushState findPatternPushState(@Nullable KeyCounter[] inputHolder) {
        for (int channel = 0; channel < this.processingChannels.length; channel++) {
            PatternPushState state = createPatternPushState(channel);
            if (canAcceptPatternInputs(state, inputHolder)) {
                return state;
            }
        }
        return null;
    }

    private PatternPushState createPatternPushState(int channel) {
        return new PatternPushState(
                channel,
                copyInputSlots(channel),
                copyFluidStacks(this.fluidInputTanks, getFluidInputStartSlotForChannel(channel), getFluidInputSlotCountForChannel(channel)),
                copyKeyStacks(this.keyInputStacks, getKeyInputStartSlotForChannel(channel), getKeyInputSlotCountForChannel(channel)));
    }

    private ItemStack[] copyInputSlots(int channel) {
        ItemStack[] slots = new ItemStack[getItemInputSlotCountForChannel(channel)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = this.storage.getStackInSlot(getItemInputStartSlotForChannel(channel) + i).copy();
        }
        return slots;
    }

    private boolean canAcceptPatternInputs(PatternPushState state, KeyCounter[] inputHolder) {
        if (inputHolder == null) {
            return true;
        }

        for (KeyCounter inputs : inputHolder) {
            for (var input : inputs) {
                if (!canAcceptPatternInput(state, input.getKey(), input.getLongValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean canAcceptPatternInput(PatternPushState state, @Nullable AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return true;
        }
        if (key instanceof AEItemKey itemKey) {
            GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
            if (canAcceptWrappedKeyAsPatternInput(wrapped)) {
                return canAcceptWrappedKeyInput(state, wrapped, amount);
            }
            return canAcceptItemInput(state, itemKey, amount);
        }
        if (key instanceof AEFluidKey fluidKey) {
            return canAcceptFluidInput(state, fluidKey, amount);
        }
        return canAcceptGenericKeyInput(state, key, amount);
    }

    private static boolean canAcceptWrappedKeyAsPatternInput(@Nullable GenericStack wrapped) {
        if (wrapped == null) {
            return false;
        }

        AEKey what = wrapped.what();
        return what instanceof AEFluidKey || isAllowedMenuKey(what);
    }

    private boolean canAcceptWrappedKeyInput(PatternPushState state, GenericStack wrapped, long amount) {
        if (wrapped.amount() <= 0) {
            return false;
        }
        if (amount > Long.MAX_VALUE / wrapped.amount()) {
            return false;
        }

        long totalAmount = wrapped.amount() * amount;
        if (wrapped.what() instanceof AEFluidKey fluidKey) {
            return canAcceptFluidInput(state, fluidKey, totalAmount);
        }
        return canAcceptGenericKeyInput(state, wrapped.what(), totalAmount);
    }

    private boolean canAcceptItemInput(PatternPushState state, AEItemKey itemKey, long amount) {
        if (amount <= 0) {
            return true;
        }

        ItemStack prototype = itemKey.toStack(1);
        if (prototype.isEmpty()) {
            return false;
        }

        long remaining = amount;
        for (int i = 0; i < state.itemInputs.length && remaining > 0; i++) {
            ItemStack current = state.itemInputs[i];
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, prototype)) {
                continue;
            }

            int maxCount = this.storage.getSlotLimit(getItemInputStartSlotForChannel(state.channel) + i);
            int free = maxCount - current.getCount();
            if (free <= 0) {
                continue;
            }

            int inserted = (int) Math.min(remaining, free);
            current.grow(inserted);
            remaining -= inserted;
        }

        for (int i = 0; i < state.itemInputs.length && remaining > 0; i++) {
            ItemStack current = state.itemInputs[i];
            if (!current.isEmpty()) {
                continue;
            }

            int maxCount = this.storage.getSlotLimit(getItemInputStartSlotForChannel(state.channel) + i);
            if (maxCount <= 0) {
                continue;
            }

            int inserted = (int) Math.min(remaining, maxCount);
            state.itemInputs[i] = prototype.copyWithCount(inserted);
            remaining -= inserted;
        }

        return remaining == 0;
    }

    private boolean canAcceptFluidInput(PatternPushState state, AEFluidKey fluidKey, long amount) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            return amount <= 0;
        }

        int remaining = (int) amount;
        for (int slot = 0; slot < state.fluidInputs.size() && remaining > 0; slot++) {
            FluidStack current = state.fluidInputs.get(slot);
            if (!matchesFluidKey(current, fluidKey)) {
                continue;
            }
            remaining -= fillSimulatedTank(state, slot, fluidKey, remaining);
        }
        for (int slot = 0; slot < state.fluidInputs.size() && remaining > 0; slot++) {
            if (!state.fluidInputs.get(slot).isEmpty()) {
                continue;
            }
            remaining -= setSimulatedTank(state, slot, fluidKey, remaining);
        }
        return remaining == 0;
    }

    private int fillSimulatedTank(PatternPushState state, int slot, AEFluidKey fluidKey, int amount) {
        FluidStack current = state.fluidInputs.get(slot);
        int inputSlot = getFluidInputStartSlotForChannel(state.channel) + slot;
        int inserted = Math.min(amount, this.fluidInputTanks.get(inputSlot).getCapacity() - current.getAmount());
        if (inserted <= 0) {
            return 0;
        }
        FluidStack updated = current.copy();
        updated.setAmount(updated.getAmount() + inserted);
        state.fluidInputs.set(slot, updated);
        return inserted;
    }

    private int setSimulatedTank(PatternPushState state, int slot, AEFluidKey fluidKey, int amount) {
        int inputSlot = getFluidInputStartSlotForChannel(state.channel) + slot;
        int inserted = Math.min(amount, this.fluidInputTanks.get(inputSlot).getCapacity());
        if (inserted > 0) {
            state.fluidInputs.set(slot, fluidKey.toStack(inserted));
        }
        return inserted;
    }

    private boolean canAcceptGenericKeyInput(PatternPushState state, AEKey key, long amount) {
        if (amount <= 0) {
            return amount <= 0;
        }
        return canStoreKeyAmount(state.keyInputs, new GenericStack(key, amount), getKeyInputCapacity()) &&
                storeKeyAmount(state.keyInputs, key, amount, getKeyInputCapacity());
    }

    private static int getPatternColor(IPatternDetails patternDetails) {
        double hue = Integer.toUnsignedLong(patternDetails.getDefinition().hashCode()) / 4_294_967_296.0D;
        return Mth.hsvToRgb((float) hue, 0.68F, 0.92F);
    }

    private void applyPatternPushState(PatternPushState state, int patternColor) {
        boolean previousPreservation = this.preservingInputPatternColors;
        this.preservingInputPatternColors = true;
        try {
            for (int i = 0; i < state.itemInputs.length; i++) {
                int slot = getItemInputStartSlotForChannel(state.channel) + i;
                ItemStack updated = state.itemInputs[i];
                if (!hasSameItemInput(this.storage.getStackInSlot(slot), updated)) {
                    this.itemInputPatternColors[slot] = updated.isEmpty() ? 0 : patternColor;
                }
                this.storage.setItemDirect(slot, updated);
            }

            for (int i = 0; i < state.fluidInputs.size(); i++) {
                int slot = getFluidInputStartSlotForChannel(state.channel) + i;
                FluidStack updated = state.fluidInputs.get(i);
                if (!hasSameFluidInput(this.fluidInputTanks.get(slot).getFluid(), updated)) {
                    this.fluidInputPatternColors[slot] = updated.isEmpty() ? 0 : patternColor;
                }
            }
            for (int i = 0; i < state.keyInputs.size(); i++) {
                int slot = getKeyInputStartSlotForChannel(state.channel) + i;
                GenericStack updated = state.keyInputs.get(i);
                if (!hasSameKeyInput(this.keyInputStacks.get(slot), updated)) {
                    this.keyInputPatternColors[slot] = updated == null ? 0 : patternColor;
                }
            }

            restoreFluidStacks(this.fluidInputTanks, getFluidInputStartSlotForChannel(state.channel), state.fluidInputs);
            restoreKeyStacks(this.keyInputStacks, getKeyInputStartSlotForChannel(state.channel), state.keyInputs);
        } finally {
            this.preservingInputPatternColors = previousPreservation;
        }
        syncKeyMenuFromStack();
    }

    private static boolean hasSameItemInput(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second) && first.getCount() == second.getCount();
    }

    private static boolean hasSameFluidInput(FluidStack first, FluidStack second) {
        return FluidStack.isSameFluidSameComponents(first, second) && first.getAmount() == second.getAmount();
    }

    private static boolean hasSameKeyInput(@Nullable GenericStack first, @Nullable GenericStack second) {
        return first == null ? second == null : first.equals(second);
    }

    private static boolean matchesFluidKey(FluidStack stack, AEFluidKey key) {
        return key.equals(AEFluidKey.of(stack));
    }

    private static @Nullable GenericStack createFluidGenericStack(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        return new GenericStack(AEFluidKey.of(fluid), fluid.getAmount());
    }

    private record SlotFilter(Predicate<ItemStack> insertPredicate, boolean allowExtract) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return this.insertPredicate.test(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return this.allowExtract;
        }
    }

    private InternalInventory createExternalInput() {
        InternalInventory[] inputs = new InternalInventory[getItemInputSlotCount()];
        for (int i = 0; i < getItemInputSlotCount(); i++) {
            inputs[i] = new FilteredInternalInventory(
                    this.storage.getSlotInv(ITEM_INPUT_START_SLOT + i),
                    new SlotFilter(stack -> true, false));
        }
        return new CombinedInternalInventory(inputs);
    }

    private InternalInventory createExternalOutput() {
        InternalInventory[] outputs = new InternalInventory[getItemOutputSlotCount()];
        for (int i = 0; i < getItemOutputSlotCount(); i++) {
            outputs[i] = new FilteredInternalInventory(
                    this.storage.getSlotInv(getItemOutputStartSlot() + i),
                    new SlotFilter(stack -> false, true));
        }
        return new CombinedInternalInventory(outputs);
    }

    private List<FluidTank> createFluidTanks(int count, int capacity) {
        List<FluidTank> tanks = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            tanks.add(new SyncFluidTank(capacity));
        }
        return tanks;
    }

    private List<GenericStackInv> createFluidMenuInventories(List<FluidTank> tanks, int capacity) {
        List<GenericStackInv> menuInventories = new ArrayList<>(tanks.size());
        for (FluidTank tank : tanks) {
            GenericStackInv[] holder = new GenericStackInv[1];
            GenericStackInv menuInventory = createFluidMenuInventory(
                    () -> syncTankFromMenuFluid(tank, tanks, holder[0]), capacity, tanks, tank);
            holder[0] = menuInventory;
            menuInventories.add(menuInventory);
        }
        return menuInventories;
    }

    private GenericStackInv createFluidMenuInventory(Runnable syncAction, int capacity, List<FluidTank> tanks,
                                                     FluidTank tank) {
        var inv = new GenericStackInv(Set.of(AEKeyType.fluids()), syncAction, GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> {
                    if (!(what instanceof AEFluidKey fluidKey)) {
                        return true;
                    }
                    return !conflictsWithExistingFluid(tanks, tank, fluidKey);
                });
            }
        };
        inv.setCapacity(AEKeyType.fluids(), capacity);
        return inv;
    }

    private List<GenericStackInv> createKeyMenuInventories(int count, boolean input) {
        List<GenericStackInv> menuInventories = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            menuInventories.add(createKeyMenuInventory(slot, input));
        }
        return menuInventories;
    }

    private GenericStackInv createKeyMenuInventory(int keySlot, boolean input) {
        var inv = new GenericStackInv(AEKeyTypes.getAll(), () -> syncStackFromKeyMenu(keySlot, input),
                GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> {
                    if (!isAllowedMenuKey(what)) {
                        return false;
                    }
                    var current = this.getStack(slot);
                    return current == null || current.amount() <= 0 || current.what().equals(what);
                });
            }
        };
        applyKeyCapacities(inv, input ? getKeyInputCapacity() : getKeyOutputCapacity());
        return inv;
    }

    private static void applyKeyCapacities(GenericStackInv inv, long capacity) {
        for (AEKeyType type : AEKeyTypes.getAll()) {
            inv.setCapacity(type, capacity);
        }
    }

    private void refillEnergyCache() {
        var node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 0.0001D) {
            return;
        }

        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 0.0001D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private static boolean isAllowedMenuKey(@Nullable AEKey what) {
        return what != null && !(what instanceof AEItemKey) && !(what instanceof AEFluidKey);
    }

    private void syncMenuFluidsFromTanks() {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            syncMenuFluidsFromTanks(this.fluidInputTanks, this.fluidInputMenuInventories);
            syncMenuFluidsFromTanks(this.fluidOutputTanks, this.fluidOutputMenuInventories);
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private void syncMenuFluidFromTank(FluidTank tank, GenericStackInv menuInventory) {
        menuInventory.setStack(0, createFluidGenericStack(tank.getFluid()));
    }

    private void syncMenuFluidsFromTanks(List<FluidTank> tanks, List<GenericStackInv> menuInventories) {
        for (int slot = 0; slot < tanks.size(); slot++) {
            syncMenuFluidFromTank(tanks.get(slot), menuInventories.get(slot));
        }
    }

    private void syncTankFromMenuFluid(FluidTank tank, List<FluidTank> pairedTanks, GenericStackInv menuInventory) {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            var stack = menuInventory.getStack(0);
            if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey) || stack.amount() <= 0) {
                tank.setFluid(FluidStack.EMPTY);
            } else {
                int amount = (int) Math.min(tank.getCapacity(), stack.amount());
                FluidStack newFluid = fluidKey.toStack(amount);
                if (conflictsWithPairedTanks(pairedTanks, tank, newFluid)) {
                    syncMenuFluidFromTank(tank, menuInventory);
                    return;
                }
                tank.setFluid(newFluid);
            }
            saveChanges();
            markForClientUpdate();
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private boolean conflictsWithPairedTanks(List<FluidTank> tanks, FluidTank excluded, FluidStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        for (FluidTank tank : tanks) {
            if (tank != excluded && FluidStack.isSameFluidSameComponents(tank.getFluid(), candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithExistingFluid(List<FluidTank> tanks, FluidTank excluded, AEFluidKey candidate) {
        for (FluidTank tank : tanks) {
            if (tank != excluded && candidate.equals(AEFluidKey.of(tank.getFluid()))) {
                return true;
            }
        }
        return false;
    }

    private void syncKeyMenuFromStack() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            syncKeyMenuFromStacks(this.keyInputMenuInventories, this.keyInputStacks);
            syncKeyMenuFromStacks(this.keyOutputMenuInventories, this.keyOutputStacks);
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private void syncKeyMenuFromStacks(List<GenericStackInv> menuInventories, List<GenericStack> stacks) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            menuInventories.get(slot).setStack(0, stacks.get(slot));
        }
    }

    private void syncStackFromKeyMenu(int slot, boolean input) {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            List<GenericStack> stacks = input ? this.keyInputStacks : this.keyOutputStacks;
            GenericStackInv menuInventory = input ? this.keyInputMenuInventories.get(slot) :
                    this.keyOutputMenuInventories.get(slot);
            long capacity = input ? getKeyInputCapacity() : getKeyOutputCapacity();
            var previous = stacks.get(slot);
            var stack = menuInventory.getStack(0);
            if (!isCompatibleKeyReplacement(previous, stack)) {
                menuInventory.setStack(0, previous);
                return;
            }
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                stacks.set(slot, null);
            } else {
                stacks.set(slot, clampKeyStack(stack, capacity));
            }
            if (input) {
                this.keyInputPatternColors[slot] = 0;
            }
            saveChanges();
            markForClientUpdate();
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private static boolean isCompatibleKeyReplacement(GenericStack current, GenericStack incoming) {
        if (current == null || current.what() == null || current.amount() <= 0) {
            return true;
        }
        if (incoming == null || incoming.what() == null || incoming.amount() <= 0) {
            return true;
        }
        return current.what().equals(incoming.what());
    }

    private static GenericStack clampKeyStack(GenericStack stack, long capacity) {
        AEKey what = stack.what();
        return what == null ? null : new GenericStack(what, Math.min(capacity, stack.amount()));
    }

    private static List<GenericStack> createKeyStacks(int count) {
        List<GenericStack> stacks = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            stacks.add(null);
        }
        return stacks;
    }

    private static List<FluidStack> copyFluidStacks(List<FluidTank> tanks) {
        List<FluidStack> copies = new ArrayList<>(tanks.size());
        for (FluidTank tank : tanks) {
            copies.add(tank.getFluid().copy());
        }
        return copies;
    }

    private static List<FluidStack> copyFluidStacks(List<FluidTank> tanks, int startSlot, int slotCount) {
        List<FluidStack> copies = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            copies.add(tanks.get(startSlot + slot).getFluid().copy());
        }
        return copies;
    }

    private static List<GenericStack> copyKeyStacks(List<GenericStack> stacks) {
        List<GenericStack> copies = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            copies.add(copyKeyStack(stack));
        }
        return copies;
    }

    private static List<GenericStack> copyKeyStacks(List<GenericStack> stacks, int startSlot, int slotCount) {
        List<GenericStack> copies = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            copies.add(copyKeyStack(stacks.get(startSlot + slot)));
        }
        return copies;
    }

    private static void restoreFluidStacks(List<FluidTank> tanks, List<FluidStack> stacks) {
        for (int slot = 0; slot < tanks.size(); slot++) {
            tanks.get(slot).setFluid(stacks.get(slot).copy());
        }
    }

    private static void restoreFluidStacks(List<FluidTank> tanks, int startSlot, List<FluidStack> stacks) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            tanks.get(startSlot + slot).setFluid(stacks.get(slot).copy());
        }
    }

    private static void restoreKeyStacks(List<GenericStack> target, List<GenericStack> source) {
        for (int slot = 0; slot < target.size(); slot++) {
            target.set(slot, copyKeyStack(source.get(slot)));
        }
    }

    private static void restoreKeyStacks(List<GenericStack> target, int startSlot, List<GenericStack> source) {
        for (int slot = 0; slot < source.size(); slot++) {
            target.set(startSlot + slot, copyKeyStack(source.get(slot)));
        }
    }

    private static void clearKeyStacks(List<GenericStack> stacks) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            stacks.set(slot, null);
        }
    }

    private static boolean hasStoredKeys(List<GenericStack> stacks) {
        return stacks.stream().anyMatch(stack -> stack != null && stack.what() != null && stack.amount() > 0L);
    }

    private static boolean canStoreKeyAmount(List<GenericStack> stacks, GenericStack incoming, long capacity) {
        AEKey key = incoming.what();
        if (key == null || incoming.amount() <= 0L) {
            return false;
        }
        long remaining = incoming.amount();
        for (GenericStack stack : stacks) {
            if (stack == null || !key.equals(stack.what())) {
                continue;
            }
            remaining -= Math.min(remaining, Math.max(0L, capacity - stack.amount()));
            if (remaining <= 0L) {
                return true;
            }
        }
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0L) {
                continue;
            }
            remaining -= Math.min(remaining, capacity);
            if (remaining <= 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean storeKeyAmount(List<GenericStack> stacks, AEKey key, long amount, long capacity) {
        long remaining = amount;
        for (int slot = 0; slot < stacks.size() && remaining > 0L; slot++) {
            GenericStack stack = stacks.get(slot);
            if (stack == null || !key.equals(stack.what())) {
                continue;
            }
            long inserted = Math.min(remaining, Math.max(0L, capacity - stack.amount()));
            if (inserted > 0L) {
                stacks.set(slot, new GenericStack(key, stack.amount() + inserted));
                remaining -= inserted;
            }
        }
        for (int slot = 0; slot < stacks.size() && remaining > 0L; slot++) {
            GenericStack stack = stacks.get(slot);
            if (stack != null && stack.what() != null && stack.amount() > 0L) {
                continue;
            }
            long inserted = Math.min(remaining, capacity);
            stacks.set(slot, new GenericStack(key, inserted));
            remaining -= inserted;
        }
        return remaining == 0L;
    }

    private static @Nullable GenericStack copyKeyStack(@Nullable GenericStack stack) {
        if (stack == null || stack.what() == null || stack.amount() <= 0) {
            return null;
        }
        return new GenericStack(stack.what(), stack.amount());
    }

    private final class ReassemblerKeyInventory implements GenericInternalInventory {

        private int batchDepth;
        private boolean batchDirty;

        @Override
        public int size() {
            return getKeyInputSlotCount() + getKeyOutputSlotCount();
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            if (slot >= 0 && slot < getKeyInputSlotCount()) {
                return keyInputStacks.get(slot);
            }
            int outputSlot = slot - getKeyOutputStartSlot();
            return outputSlot >= 0 && outputSlot < getKeyOutputSlotCount() ? keyOutputStacks.get(outputSlot) : null;
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
            return isAllowedMenuKey(key) ? Math.max(getKeyInputCapacity(), getKeyOutputCapacity()) : 0L;
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            return isSupportedType(keyType) ? Math.max(getKeyInputCapacity(), getKeyOutputCapacity()) : 0L;
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
            if (slot < 0 || slot >= getKeyInputSlotCount()) {
                return;
            }

            GenericStack normalized = null;
            if (newStack != null && newStack.what() != null && newStack.amount() > 0L) {
                if (!isAllowedMenuKey(newStack.what()) || !isCompatibleKeyReplacement(keyInputStacks.get(slot), newStack)) {
                    return;
                }
                normalized = clampKeyStack(newStack, getKeyInputCapacity());
            }

            GenericStack previous = keyInputStacks.get(slot);
            boolean changed = previous == null ? normalized != null : !previous.equals(normalized);
            if (!changed) {
                return;
            }

            keyInputStacks.set(slot, normalized);
            keyInputPatternColors[slot] = 0;
            syncKeyMenuFromStack();
            onChange();
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type != null && type != AEKeyType.items() && type != AEKeyType.fluids();
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            if (slot < 0 || slot >= getKeyInputSlotCount() || !isAllowedMenuKey(what)) {
                return false;
            }
            GenericStack current = keyInputStacks.get(slot);
            return current == null || current.what() == null || current.what().equals(what);
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what) || amount <= 0L) {
                return 0L;
            }

            GenericStack current = keyInputStacks.get(slot);
            long stored = current == null ? 0L : current.amount();
            long inserted = Math.min(amount, Math.max(0L, getKeyInputCapacity() - stored));
            if (inserted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                keyInputStacks.set(slot, new GenericStack(what, stored + inserted));
                keyInputPatternColors[slot] = 0;
                syncKeyMenuFromStack();
                onChange();
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            int outputSlot = slot - getKeyOutputStartSlot();
            if (outputSlot < 0 || outputSlot >= getKeyOutputSlotCount() || what == null || amount <= 0L) {
                return 0L;
            }
            GenericStack output = keyOutputStacks.get(outputSlot);
            if (output == null || !what.equals(output.what())) {
                return 0L;
            }

            long extracted = Math.min(amount, output.amount());
            if (mode == Actionable.MODULATE) {
                long remaining = output.amount() - extracted;
                keyOutputStacks.set(outputSlot, remaining <= 0L ? null : new GenericStack(what, remaining));
                syncKeyMenuFromStack();
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
                notifyChanged();
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
            } else {
                notifyChanged();
            }
        }

        private void notifyChanged() {
            saveChanges();
            markForClientUpdate();
        }
    }

    private final class PatternInputStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            if (amount <= 0) {
                return 0L;
            }

            PatternPushState state = null;
            for (int channel = 0; channel < processingChannels.length; channel++) {
                PatternPushState candidate = createPatternPushState(channel);
                if (canAcceptPatternInput(candidate, what, amount)) {
                    state = candidate;
                    break;
                }
            }
            if (state == null) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                applyPatternPushState(state, 0);
                saveChanges();
                markForClientUpdate();
            }
            return amount;
        }

        @Override
        public Component getDescription() {
            return getMachineBlock().getName();
        }
    }

    protected Block getMachineBlock() {
        return DEBlocks.DATA_RIPPER_REASSEMBLER.get();
    }

    public GenericStack getKeyInputStack() {
        return getKeyInputStack(0);
    }

    public GenericStack getKeyOutputStack() {
        return getKeyOutputStack(0);
    }

    public GenericStack getKeyInputStack(int slot) {
        return this.keyInputStacks.get(slot);
    }

    public GenericStack getKeyOutputStack(int slot) {
        return this.keyOutputStacks.get(slot);
    }

    private final class SyncFluidTank extends FluidTank {

        private SyncFluidTank(int capacity) {
            super(capacity);
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            if (!super.isFluidValid(stack)) {
                return false;
            }

            if (fluidInputTanks.contains(this)) {
                return !conflictsWithPairedTanks(fluidInputTanks, this, stack);
            }
            if (fluidOutputTanks.contains(this)) {
                return !conflictsWithPairedTanks(fluidOutputTanks, this, stack);
            }
            return true;
        }

        @Override
        protected void onContentsChanged() {
            if (!preservingInputPatternColors) {
                int inputSlot = fluidInputTanks.indexOf(this);
                if (inputSlot >= 0) {
                    fluidInputPatternColors[inputSlot] = 0;
                }
            }
            syncMenuFluidsFromTanks();
            saveChanges();
            markForClientUpdate();
        }
    }

    private final class ReassemblerFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return fluidInputTanks.size() + fluidOutputTanks.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.getTank(tank).getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.getTank(tank).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank >= 0 && tank < fluidInputTanks.size() && this.getTank(tank).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = 0;
            for (FluidTank tank : fluidInputTanks) {
                if (filled >= resource.getAmount()) {
                    break;
                }
                FluidStack remaining = resource.copy();
                remaining.shrink(filled);
                filled += tank.fill(remaining, action);
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            for (FluidTank tank : fluidOutputTanks) {
                FluidStack drained = tank.drain(resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (FluidTank tank : fluidOutputTanks) {
                FluidStack drained = tank.drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidTank getTank(int tank) {
            if (tank >= 0 && tank < fluidInputTanks.size()) {
                return fluidInputTanks.get(tank);
            }
            int outputTank = tank - fluidInputTanks.size();
            if (outputTank >= 0 && outputTank < fluidOutputTanks.size()) {
                return fluidOutputTanks.get(outputTank);
            }
            throw new IndexOutOfBoundsException("Invalid tank index: " + tank);
        }
    }

    private final class ReassemblerItemInventory extends AppEngInternalInventory {

        private ReassemblerItemInventory() {
            super(DataRipperReassemblerBlockEntity.this, DataRipperReassemblerBlockEntity.this.getStorageSlotCount());
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            super.setItemDirect(slot, stack);
            if (!preservingInputPatternColors && slot >= ITEM_INPUT_START_SLOT &&
                    slot < ITEM_INPUT_START_SLOT + getItemInputSlotCount()) {
                itemInputPatternColors[slot - ITEM_INPUT_START_SLOT] = 0;
            }
        }

        @Override
        public void writeToNBT(CompoundTag data, String name, HolderLookup.Provider registries) {
            if (isEmpty()) {
                data.remove(name);
                return;
            }

            ListTag storedItems = new ListTag();
            for (int slot = 0; slot < size(); slot++) {
                ItemStack stack = getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }

                CompoundTag storedStack = new CompoundTag();
                storedStack.putInt(STORAGE_SLOT_TAG, slot);
                storedStack.putInt(STORAGE_COUNT_TAG, stack.getCount());
                storedItems.add(stack.copyWithCount(1).save(registries, storedStack));
            }
            data.put(name, storedItems);
        }

        @Override
        public void readFromNBT(CompoundTag data, String name, HolderLookup.Provider registries) {
            super.readFromNBT(data, name, registries);
            ListTag storedItems = data.getList(name, Tag.TAG_COMPOUND);
            for (int index = 0; index < storedItems.size(); index++) {
                CompoundTag storedStack = storedItems.getCompound(index);
                if (!storedStack.contains(STORAGE_COUNT_TAG, Tag.TAG_INT)) {
                    continue;
                }

                int slot = storedStack.getInt(STORAGE_SLOT_TAG);
                if (slot < 0 || slot >= size()) {
                    continue;
                }

                ItemStack stack = getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    stack.setCount(storedStack.getInt(STORAGE_COUNT_TAG));
                }
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }

            ItemStack inSlot = getStackInSlot(slot);
            if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, stack)) {
                return stack;
            }

            int currentAmount = inSlot.isEmpty() ? 0 : inSlot.getCount();
            int inserted = Math.min(stack.getCount(), Math.max(0, getSlotLimit(slot) - currentAmount));
            if (inserted <= 0) {
                return stack;
            }

            if (!simulate) {
                ItemStack updated = inSlot.isEmpty() ? stack.copy() : inSlot.copy();
                updated.setCount(currentAmount + inserted);
                setItemDirect(slot, updated);
            }
            if (inserted >= stack.getCount()) {
                return ItemStack.EMPTY;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = getStackInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int extracted = Math.min(stack.getCount(), amount);
            if (extracted <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                ItemStack remainder = stack.copy();
                remainder.shrink(extracted);
                boolean preservesPatternColor = slot >= ITEM_INPUT_START_SLOT &&
                        slot < ITEM_INPUT_START_SLOT + getItemInputSlotCount();
                boolean previousPreservation = preservingInputPatternColors;
                preservingInputPatternColors = preservesPatternColor || previousPreservation;
                try {
                    setItemDirect(slot, remainder);
                } finally {
                    preservingInputPatternColors = previousPreservation;
                }
                if (remainder.isEmpty() && preservesPatternColor) {
                    itemInputPatternColors[slot - ITEM_INPUT_START_SLOT] = 0;
                }
            }
            return result;
        }
    }

    private record RecipeAdvance(int elapsedTicks, boolean completedRecipe) {

        private static RecipeAdvance progressed(int elapsedTicks) {
            return new RecipeAdvance(elapsedTicks, false);
        }

        private static RecipeAdvance completed(int elapsedTicks) {
            return new RecipeAdvance(elapsedTicks, true);
        }

        private static RecipeAdvance blocked(int elapsedTicks) {
            return new RecipeAdvance(elapsedTicks, false);
        }
    }

    private static final class RecipeProcessingState {

        private final ItemStack[] itemSlots;
        private final List<FluidStack> fluidInputs;
        private final List<FluidStack> fluidOutputs;
        private final List<GenericStack> keyInputs;
        private final List<GenericStack> keyOutputs;

        private RecipeProcessingState(ItemStack[] itemSlots, List<FluidStack> fluidInputs,
                                      List<FluidStack> fluidOutputs, List<GenericStack> keyInputs,
                                      List<GenericStack> keyOutputs) {
            this.itemSlots = itemSlots;
            this.fluidInputs = fluidInputs;
            this.fluidOutputs = fluidOutputs;
            this.keyInputs = keyInputs;
            this.keyOutputs = keyOutputs;
        }
    }

    private record RecipeStackIdentity(@Nullable AEKey what, long amount) {

        private static final RecipeStackIdentity EMPTY = new RecipeStackIdentity(null, 0L);
    }

    private record RecipeMatchKey(long reloadEpoch, @Nullable ResourceLocation activeRecipeId,
                                  List<RecipeStackIdentity> itemInputs,
                                  List<RecipeStackIdentity> fluidInputs,
                                  List<RecipeStackIdentity> keyInputs,
                                  Set<ResourceLocation> otherActiveRecipeIds) {}

    private record RecipeMatchCache(RecipeMatchKey key, @Nullable ResourceLocation recipeId) {}

    private record ReservedItemInput(int slot, ItemStack stack) {}

    private record ReservedFluidInput(int slot, AEFluidKey key, int amount) {}

    private record ReservedKeyInput(int slot, AEKey key, long amount) {}

    private static final class RecipeInputReservation {

        private final List<ReservedItemInput> itemInputs;
        private final List<ReservedFluidInput> fluidInputs;
        private final List<ReservedKeyInput> keyInputs;

        private RecipeInputReservation(List<ReservedItemInput> itemInputs, List<ReservedFluidInput> fluidInputs,
                                       List<ReservedKeyInput> keyInputs) {
            this.itemInputs = List.copyOf(itemInputs);
            this.fluidInputs = List.copyOf(fluidInputs);
            this.keyInputs = List.copyOf(keyInputs);
        }
    }

    private static final class InputReservationUsage {

        private final int[] itemAmounts;
        private final int[] fluidAmounts;
        private final long[] keyAmounts;

        private InputReservationUsage(int[] itemAmounts, int[] fluidAmounts, long[] keyAmounts) {
            this.itemAmounts = itemAmounts;
            this.fluidAmounts = fluidAmounts;
            this.keyAmounts = keyAmounts;
        }
    }

    private static final class ProcessingChannelState {

        private int progress;
        private int maxProgress = MAX_PROGRESS;
        private ResourceLocation activeRecipeId;
        private RecipeMatchCache recipeMatchCache;
        private RecipeInputReservation inputReservation;

        private boolean isIdle() {
            return this.progress == 0 && this.maxProgress == MAX_PROGRESS && this.activeRecipeId == null;
        }

        private void reset() {
            this.progress = 0;
            this.maxProgress = MAX_PROGRESS;
            this.activeRecipeId = null;
            this.recipeMatchCache = null;
            this.inputReservation = null;
        }
    }

    private static final class PatternPushState {

        private final int channel;
        private final ItemStack[] itemInputs;
        private final List<FluidStack> fluidInputs;
        private final List<GenericStack> keyInputs;

        private PatternPushState(int channel, ItemStack[] itemInputs, List<FluidStack> fluidInputs,
                                 List<GenericStack> keyInputs) {
            this.channel = channel;
            this.itemInputs = itemInputs;
            this.fluidInputs = fluidInputs;
            this.keyInputs = keyInputs;
        }
    }
}
