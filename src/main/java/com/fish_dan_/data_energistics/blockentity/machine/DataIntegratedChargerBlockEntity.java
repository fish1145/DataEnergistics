package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.block.machine.DataIntegratedChargerBlock;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.common.capability.AdjacentBlockCapabilityCache;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipe;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeInput;
import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressRecipeSupport;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipeInput;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.misc.InscriberRecipes;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.util.ConfigManager;
import appeng.util.ConfigMenuInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An integrated, buffered front-end for AE2 charger and inscriber recipes plus data charger recipes.
 * The installed machine block selects the active recipe family.
 */
public class DataIntegratedChargerBlockEntity extends AENetworkedPoweredBlockEntity
                                              implements InternalInventoryHost, IConfigurableObject, IUpgradeableObject {

    public static final int ITEM_INPUT_SLOT_COUNT = 6;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 6;
    // Preserve output and module indexes from the original eight-input layout.
    // This keeps previously saved output and module stacks in place as the two inputs are retired.
    public static final int ITEM_OUTPUT_START_SLOT = 8;
    public static final int MACHINE_MODULE_SLOT = ITEM_OUTPUT_START_SLOT + ITEM_OUTPUT_SLOT_COUNT;
    public static final int STORAGE_SLOTS = MACHINE_MODULE_SLOT + 1;
    public static final int ITEM_SLOT_CAPACITY = 512;
    public static final int FLUID_CAPACITY = DataChargePressRecipe.MAX_FLUID_AMOUNT;
    public static final int MAX_SPEED_CARDS = 4;
    public static final int MAX_ENERGY_CARDS = 2;
    public static final int UPGRADE_SLOTS = 5;
    public static final int MAX_PROGRESS = 200;
    public static final int BASE_PARALLEL = 1;
    public static final int PARALLEL_MULTIPLIER_PER_ENERGY_CARD = 16;
    public static final double ENERGY_CAPACITY = 160_000.0D;

    private static final double BASE_OPERATION_ENERGY = 1_600.0D;
    private static final String STORAGE_TAG = "storage";
    private static final String STORAGE_SLOT_TAG = "Slot";
    private static final String STORAGE_COUNT_TAG = "DataEnergisticsCount";
    private static final String UPGRADES_TAG = "upgrades";
    private static final String FLUID_TAG = "fluid";
    private static final String CONFIG_TAG = "config";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final String LEGACY_ITEM_OUTPUT_SIDES_TAG = "item_output_sides";
    private static final String PROGRESS_TAG = "progress";
    private static final String PROCESSING_MODE_TAG = "processing_mode";
    private static final ResourceLocation AE2_INSCRIBER = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber");
    private static final ResourceLocation AE2_CHARGER = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final ResourceLocation EXTENDED_AE_INSCRIBER = ResourceLocation.fromNamespaceAndPath("extendedae", "ex_inscriber");
    private static final ResourceLocation EXTENDED_AE_CHARGER = ResourceLocation.fromNamespaceAndPath("extendedae", "ex_charger");
    private static final ResourceLocation DATA_CHARGER = ResourceLocation.fromNamespaceAndPath("data_energistics", "data_charger");
    private static final ResourceLocation EXTENDED_DATA_CHARGER = ResourceLocation.fromNamespaceAndPath(
            "data_energistics", "extended_data_charger");
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            DEBlocks.DATA_INTEGRATED_CHARGER.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new IntegratedChargerItemInventory();
    private boolean suppressAe2DefaultInventorySerialization;
    private final InternalInventory externalInput = createExternalInput();
    private final InternalInventory externalOutput = createExternalOutput();
    private final InternalInventory externalInventory = new CombinedInternalInventory(this.externalInput, this.externalOutput);
    private final FluidTank fluidTank = new IntegratedFluidTank();
    private final IFluidHandler externalFluidInput = new FluidInputHandler();
    private final GenericStackInv fluidMenuInventory = createFluidMenuInventory();
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private boolean syncingFluidMenu;
    private final Set<Direction> outputSides = EnumSet.allOf(Direction.class);
    private AdjacentBlockCapabilityCache<GenericInternalInventory> adjacentGenericInventories;
    private AdjacentBlockCapabilityCache<IItemHandler> adjacentItemHandlers;
    private int progress;
    private MachineMode processingMode = MachineMode.NONE;

    public DataIntegratedChargerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(DEBlockEntities.DATA_INTEGRATED_CHARGER_BLOCK_ENTITY.get(), blockPos, blockState);
        var connectableSides = getGridConnectableSides(BlockOrientation.get(blockState));
        this.getMainNode()
                .setVisualRepresentation(DEBlocks.DATA_INTEGRATED_CHARGER.get())
                .setExposedOnSides(connectableSides)
                .setIdlePowerUsage(0.0D);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.storage.setFilter(new StorageFilter());
        for (int slot = 0; slot < MACHINE_MODULE_SLOT; slot++) {
            this.storage.setMaxStackSize(slot, ITEM_SLOT_CAPACITY);
        }
        this.storage.setMaxStackSize(MACHINE_MODULE_SLOT, 1);
        this.setPowerSides(connectableSides);
        updateEnergyCapacity();
        syncMenuFluidFromTank();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return getGridConnectableSides(getOrientation()).contains(dir) ? AECableType.COVERED : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(orientation.getSide(RelativeSide.FRONT));
        return sides;
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.getMainNode().setExposedOnSides(getGridConnectableSides(orientation));
        this.setPowerSides(getGridConnectableSides(orientation));
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.suppressAe2DefaultInventorySerialization ? InternalInventory.empty() : this.storage;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.configManager;
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

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
    }

    public InternalInventory getExternalInventory() {
        return this.externalInventory;
    }

    public FluidTank getFluidTank() {
        return this.fluidTank;
    }

    public IFluidHandler getExternalFluidHandler() {
        return this.externalFluidInput;
    }

    public ConfigMenuInventory getFluidMenuInventory() {
        return this.fluidMenuInventory.createMenuWrapper();
    }

    public int getFluidCapacity() {
        return this.fluidTank.getCapacity();
    }

    public boolean isAutoExportEnabled() {
        return this.configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    public Set<Direction> getOutputSides() {
        return this.outputSides.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(this.outputSides);
    }

    public Set<Direction> getOutputSides(DigitalStorageDepotOutputType outputType) {
        return outputType == DigitalStorageDepotOutputType.ITEMS ? getOutputSides() : EnumSet.noneOf(Direction.class);
    }

    public void setOutputSideEnabled(Direction side, boolean enabled) {
        if (enabled ? this.outputSides.add(side) : this.outputSides.remove(side)) {
            saveChanges();
            markForClientUpdate();
        }
    }

    public void setOutputSideEnabled(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled) {
        if (outputType == DigitalStorageDepotOutputType.ITEMS) {
            setOutputSideEnabled(side, enabled);
        }
    }

    public int getProgress() {
        return this.progress;
    }

    public int getMaxProgress() {
        return getEffectiveProcessTicks();
    }

    public int getParallel() {
        return computeParallel(this.upgrades.getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()));
    }

    public static int computeParallel(int energyCardCount) {
        int installedCards = Math.min(MAX_ENERGY_CARDS, Math.max(0, energyCardCount));
        return installedCards == 0 ? BASE_PARALLEL : BASE_PARALLEL * installedCards * PARALLEL_MULTIPLIER_PER_ENERGY_CARD;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public double getCurrentAEPower() {
        return this.getInternalCurrentPower();
    }

    public double getMaxAEPower() {
        return this.getInternalMaxPower();
    }

    public MachineMode getMachineMode() {
        ItemStack module = this.storage.getStackInSlot(MACHINE_MODULE_SLOT);
        return module.isEmpty() ? MachineMode.POWDER : getMachineMode(module);
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        refillEnergyCache();
        boolean changed = false;
        MachineMode mode = getMachineMode();
        if (mode != this.processingMode) {
            this.processingMode = mode;
            changed |= resetProgress();
        }

        boolean working = canProcessOperation(mode);
        if (working) {
            int maxProgress = getEffectiveProcessTicks();
            this.progress = Math.min(maxProgress, this.progress + 1);
            setChanged();
            if (this.progress >= maxProgress) {
                this.progress = 0;
                for (int operation = 0; operation < getParallel(); operation++) {
                    boolean completed = switch (mode) {
                        case CHARGER -> processChargerOperation();
                        case INSCRIBER -> processInscriberOperation();
                        case CRYSTAL_GROWTH -> processCrystalGrowthOperation();
                        case POWDER -> processPowderOperation();
                        case NONE -> false;
                    };
                    if (!completed) {
                        break;
                    }
                    changed = true;
                }
                setChanged();
            }
        } else {
            changed |= resetProgress();
        }

        if (isAutoExportEnabled()) {
            changed |= tryAutoExport();
        }
        updateLitState(working);
        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < this.storage.size(); slot++) {
            dropStack(level, pos, this.storage.getStackInSlot(slot));
            this.storage.setItemDirect(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < this.upgrades.size(); slot++) {
            dropStack(level, pos, this.upgrades.getStackInSlot(slot));
            this.upgrades.setItemDirect(slot, ItemStack.EMPTY);
        }
        saveChanges();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        moveRetiredInputStacks();
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.fluidTank.readFromNBT(registries, data.getCompound(FLUID_TAG));
        if (data.contains(CONFIG_TAG)) {
            this.configManager.readFromNBT(data.getCompound(CONFIG_TAG), registries);
        }
        this.outputSides.clear();
        if (data.contains(OUTPUT_SIDES_TAG)) {
            readOutputSides(data, OUTPUT_SIDES_TAG, this.outputSides);
        } else if (data.contains(LEGACY_ITEM_OUTPUT_SIDES_TAG)) {
            readOutputSides(data, LEGACY_ITEM_OUTPUT_SIDES_TAG, this.outputSides);
        } else {
            this.outputSides.addAll(EnumSet.allOf(Direction.class));
        }
        this.progress = Math.max(0, data.getInt(PROGRESS_TAG));
        this.processingMode = readProcessingMode(data.getString(PROCESSING_MODE_TAG));
        syncMenuFluidFromTank();
        updateEnergyCapacity();
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
        data.put(FLUID_TAG, this.fluidTank.writeToNBT(registries, new CompoundTag()));
        CompoundTag config = new CompoundTag();
        this.configManager.writeToNBT(config, registries);
        data.put(CONFIG_TAG, config);
        data.put(OUTPUT_SIDES_TAG, createOutputSidesTag(this.outputSides));
        data.putInt(PROGRESS_TAG, this.progress);
        data.putString(PROCESSING_MODE_TAG, this.processingMode.name());
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveChangedInventory(inventory);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.storage.clear();
        this.fluidTank.setFluid(FluidStack.EMPTY);
    }

    public static boolean isSupportedMachineModule(ItemStack stack) {
        return getMachineMode(stack) != MachineMode.NONE;
    }

    private boolean processChargerOperation() {
        DataChargePressOperation customOperation = findCustomDataChargePressOperation();
        if (customOperation != null) {
            return processDataChargePressOperation(customOperation);
        }
        if (processDataChargerRecipeOperation()) {
            return true;
        }
        for (int inputSlot = 0; inputSlot < ITEM_INPUT_SLOT_COUNT; inputSlot++) {
            ItemStack input = this.storage.getStackInSlot(inputSlot);
            if (input.isEmpty()) {
                continue;
            }

            RecipeHolder<ChargerRecipe> recipeHolder = findChargerRecipe(input);
            if (recipeHolder != null && processChargerRecipe(inputSlot, recipeHolder.value())) {
                return true;
            }
            if (DataChargerBlockEntity.canChargeAePower(input) && chargeAePower(input)) {
                if (isAePowerFullyCharged(input)) {
                    moveInputToOutput(inputSlot);
                }
                return true;
            }
            if (isAePowerFullyCharged(input) && moveInputToOutput(inputSlot)) {
                return true;
            }
        }
        return false;
    }

    /** Runs the dedicated crystal-growth recipes unlocked by the Not So Mysterious Cube module. */
    private boolean processCrystalGrowthOperation() {
        DataChargePressOperation operation = findCustomDataChargePressOperation();
        return operation != null && processDataChargePressOperation(operation);
    }

    private boolean processDataChargerRecipeOperation() {
        if (!isDataChargerModule(this.storage.getStackInSlot(MACHINE_MODULE_SLOT))) {
            return false;
        }

        for (int inputSlot = 0; inputSlot < ITEM_INPUT_SLOT_COUNT; inputSlot++) {
            ItemStack input = this.storage.getStackInSlot(inputSlot);
            DataChargerRecipe recipe = findDataChargerRecipe(input);
            if (recipe == null || !canProcessDataChargerRecipe(recipe)) {
                continue;
            }
            if (!consumeDataFlow(recipe.getDataFlow())) {
                return false;
            }

            this.extractAEPower(recipe.getPower(), Actionable.MODULATE, PowerMultiplier.ONE);
            consumeOne(inputSlot);
            addToOutput(recipe.getResult());
            return true;
        }
        return false;
    }

    private boolean processChargerRecipe(int inputSlot, ChargerRecipe recipe) {
        ItemStack result = recipe.getResultItem();
        if (result.isEmpty() || findOutputSlot(result) < 0 || !consumeOperationEnergy()) {
            return false;
        }
        consumeOne(inputSlot);
        addToOutput(result);
        return true;
    }

    private boolean processInscriberOperation() {
        if (this.level == null) {
            return false;
        }

        DataChargePressOperation dataChargePressOperation = findDataChargePressOperation();
        if (dataChargePressOperation != null) {
            return processDataChargePressOperation(dataChargePressOperation);
        }

        for (int middleSlot = 0; middleSlot < ITEM_INPUT_SLOT_COUNT; middleSlot++) {
            ItemStack middle = this.storage.getStackInSlot(middleSlot);
            if (middle.isEmpty()) {
                continue;
            }
            for (int topSlot = -1; topSlot < ITEM_INPUT_SLOT_COUNT; topSlot++) {
                if (topSlot == middleSlot || topSlot >= 0 && this.storage.getStackInSlot(topSlot).isEmpty()) {
                    continue;
                }
                ItemStack top = getInputOrEmpty(topSlot);
                for (int bottomSlot = -1; bottomSlot < ITEM_INPUT_SLOT_COUNT; bottomSlot++) {
                    if (bottomSlot == middleSlot || bottomSlot == topSlot ||
                            bottomSlot >= 0 && this.storage.getStackInSlot(bottomSlot).isEmpty()) {
                        continue;
                    }
                    InscriberRecipe recipe = InscriberRecipes.findRecipe(
                            this.level, middle, top, getInputOrEmpty(bottomSlot), true);
                    if (recipe == null || DataChargePressRecipeSupport.isCircuitBoardRecipe(recipe) ||
                            DataChargePressRecipeSupport.isPowderRecipe(recipe) ||
                            recipe.getResultItem().isEmpty() || findOutputSlot(recipe.getResultItem()) < 0) {
                        continue;
                    }
                    if (!consumeOperationEnergy()) {
                        return false;
                    }

                    consumeOne(middleSlot);
                    if (recipe.getProcessType() == InscriberProcessType.PRESS) {
                        consumeOne(topSlot);
                        consumeOne(bottomSlot);
                    }
                    addToOutput(recipe.getResultItem());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean processPowderOperation() {
        if (this.level == null) {
            return false;
        }

        for (int middleSlot = 0; middleSlot < ITEM_INPUT_SLOT_COUNT; middleSlot++) {
            ItemStack middle = this.storage.getStackInSlot(middleSlot);
            if (middle.isEmpty()) {
                continue;
            }
            InscriberRecipe recipe = findPowderRecipe(middle);
            if (recipe == null || findOutputSlot(recipe.getResultItem()) < 0) {
                continue;
            }
            if (!consumeOperationEnergy()) {
                return false;
            }

            consumeOne(middleSlot);
            addToOutput(recipe.getResultItem());
            return true;
        }
        return false;
    }

    private @Nullable InscriberRecipe findPowderRecipe(ItemStack middle) {
        if (this.level == null) {
            return null;
        }

        for (RecipeHolder<InscriberRecipe> holder : InscriberRecipes.getRecipes(this.level)) {
            InscriberRecipe recipe = holder.value();
            if (DataChargePressRecipeSupport.isPowderRecipe(recipe) && recipe.getMiddleInput().test(middle)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Executes data charge press operations before their normal AE2 machine recipes.
     */
    private boolean processDataChargePressOperation(DataChargePressOperation operation) {
        if (this.fluidTank.drain(operation.fluidAmount(), IFluidHandler.FluidAction.SIMULATE)
                .getAmount() != operation.fluidAmount() ||
                !consumeOperationEnergy()) {
            return false;
        }

        FluidStack drained = this.fluidTank.drain(
                operation.fluidAmount(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != operation.fluidAmount()) {
            return false;
        }

        for (DataChargePressRecipe.InputSlot inputSlot : operation.inputSlots()) {
            consume(inputSlot.slot(), inputSlot.count());
        }
        addToOutput(operation.result());
        return true;
    }

    private @Nullable DataChargePressOperation findDataChargePressOperation() {
        if (this.level == null) {
            return null;
        }

        List<ItemStack> inputs = new ArrayList<>(ITEM_INPUT_SLOT_COUNT);
        for (int slot = 0; slot < ITEM_INPUT_SLOT_COUNT; slot++) {
            inputs.add(this.storage.getStackInSlot(slot));
        }
        if (!DataChargePressRecipeSupport.INSCRIBER_MODULES.test(this.storage.getStackInSlot(MACHINE_MODULE_SLOT)) ||
                !DataChargePressRecipeSupport.matchesFluid(this.fluidTank.getFluid())) {
            return null;
        }
        for (RecipeHolder<InscriberRecipe> holder : this.level.getRecipeManager()
                .getAllRecipesFor(AERecipeTypes.INSCRIBER)) {
            InscriberRecipe recipe = holder.value();
            if (!DataChargePressRecipeSupport.isCircuitBoardRecipe(recipe)) {
                continue;
            }

            int templateSlot = -1;
            if (DataChargePressRecipeSupport.hasCircuitBoardTemplate(recipe)) {
                templateSlot = findInputSlot(inputs, DataChargePressRecipeSupport.getTemplate(recipe), -1);
                if (templateSlot < 0) {
                    continue;
                }
            }
            int materialSlot = findInputSlot(inputs, recipe.getMiddleInput(), templateSlot);
            ItemStack result = DataChargePressRecipeSupport.getTripleResult(recipe);
            if (materialSlot >= 0 && inputs.get(materialSlot).getCount() >=
                    DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT && findOutputSlot(result) >= 0) {
                return new DataChargePressOperation(result, DataChargePressRecipeSupport.DATA_CORROSION_AMOUNT,
                        List.of(new DataChargePressRecipe.InputSlot(materialSlot,
                                DataChargePressRecipeSupport.CIRCUIT_BOARD_MATERIAL_COUNT)));
            }
        }
        return null;
    }

    private @Nullable DataChargePressOperation findCustomDataChargePressOperation() {
        if (this.level == null) {
            return null;
        }

        List<ItemStack> inputs = new ArrayList<>(ITEM_INPUT_SLOT_COUNT);
        for (int slot = 0; slot < ITEM_INPUT_SLOT_COUNT; slot++) {
            inputs.add(this.storage.getStackInSlot(slot));
        }
        DataChargePressRecipeInput input = new DataChargePressRecipeInput(
                inputs,
                this.fluidTank.getFluid(),
                this.storage.getStackInSlot(MACHINE_MODULE_SLOT));
        for (RecipeHolder<DataChargePressRecipe> holder : this.level.getRecipeManager()
                .getAllRecipesFor(DERecipes.DATA_CHARGE_PRESS_TYPE.get())) {
            DataChargePressRecipe recipe = holder.value();
            if (!recipe.matches(input, this.level)) {
                continue;
            }
            List<DataChargePressRecipe.InputSlot> inputSlots = recipe.findMatchingInputSlots(inputs);
            ItemStack result = recipe.getResult();
            if (!inputSlots.isEmpty() && findOutputSlot(result) >= 0) {
                return new DataChargePressOperation(result, recipe.getFluidAmount(), inputSlots);
            }
        }
        return null;
    }

    private static int findInputSlot(List<ItemStack> inputs, Ingredient ingredient, int excludedSlot) {
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (slot != excludedSlot && ingredient.test(inputs.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private @Nullable RecipeHolder<ChargerRecipe> findChargerRecipe(ItemStack input) {
        if (this.level == null) {
            return null;
        }
        for (RecipeHolder<ChargerRecipe> holder : this.level.getRecipeManager().getAllRecipesFor(AERecipeTypes.CHARGER)) {
            if (holder.value().getIngredient().test(input)) {
                return holder;
            }
        }
        return null;
    }

    private @Nullable DataChargerRecipe findDataChargerRecipe(ItemStack input) {
        if (this.level == null || input.isEmpty()) {
            return null;
        }

        DataChargerRecipeInput recipeInput = new DataChargerRecipeInput(input);
        for (RecipeHolder<DataChargerRecipe> holder : this.level.getRecipeManager()
                .getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get())) {
            if (holder.value().matches(recipeInput, this.level)) {
                return holder.value();
            }
        }
        return null;
    }

    private boolean chargeAePower(ItemStack stack) {
        if (!(stack.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage powerStorage)) {
            return false;
        }
        double offered = getChargeOffer(stack, powerStorage);
        if (offered <= 1.0E-4D) {
            return false;
        }
        double accepted = Math.max(0.0D, offered - powerStorage.injectAEPower(stack, offered, Actionable.MODULATE));
        if (accepted <= 1.0E-4D) {
            return false;
        }
        this.extractAEPower(accepted, Actionable.MODULATE, PowerMultiplier.ONE);
        return true;
    }

    /** Returns enough AE to fill the storage item in this work cycle, limited by the machine's current cache. */
    private double getChargeOffer(ItemStack stack, appeng.api.implementations.items.IAEItemPowerStorage powerStorage) {
        double missing = Math.max(0.0D, powerStorage.getAEMaxPower(stack) - powerStorage.getAECurrentPower(stack));
        return Math.min(missing, this.getInternalCurrentPower());
    }

    private static boolean isAePowerFullyCharged(ItemStack stack) {
        if (!(stack.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage powerStorage)) {
            return false;
        }
        return powerStorage.getAECurrentPower(stack) + 1.0E-4D >= powerStorage.getAEMaxPower(stack);
    }

    private boolean moveInputToOutput(int inputSlot) {
        ItemStack input = this.storage.getStackInSlot(inputSlot);
        ItemStack moved = input.copyWithCount(1);
        if (findOutputSlot(moved) < 0) {
            return false;
        }
        consumeOne(inputSlot);
        addToOutput(moved);
        return true;
    }

    private boolean consumeOperationEnergy() {
        double required = BASE_OPERATION_ENERGY;
        if (this.getInternalCurrentPower() + 1.0E-4D < required) {
            return false;
        }
        this.extractAEPower(required, Actionable.MODULATE, PowerMultiplier.ONE);
        return true;
    }

    private boolean canConsumeOperationEnergy() {
        return this.getInternalCurrentPower() + 1.0E-4D >= BASE_OPERATION_ENERGY;
    }

    private boolean canProcessDataChargerRecipe(DataChargerRecipe recipe) {
        ItemStack result = recipe.getResult();
        return !result.isEmpty() && findOutputSlot(result) >= 0 &&
                this.getInternalCurrentPower() + 1.0E-4D >= recipe.getPower() &&
                canConsumeDataFlow(recipe.getDataFlow());
    }

    private boolean canConsumeDataFlow(long amount) {
        if (amount <= 0L) {
            return true;
        }

        var node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return false;
        }
        var storageService = node.getGrid().getStorageService();
        if (storageService == null || storageService.getInventory() == null) {
            return false;
        }
        return storageService.getInventory().extract(
                DataFlowKey.of(), amount, Actionable.SIMULATE, IActionSource.ofMachine(this)) == amount;
    }

    private boolean consumeDataFlow(long amount) {
        if (amount <= 0L) {
            return true;
        }

        var node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return false;
        }
        var storageService = node.getGrid().getStorageService();
        if (storageService == null || storageService.getInventory() == null) {
            return false;
        }
        return storageService.getInventory().extract(
                DataFlowKey.of(), amount, Actionable.MODULATE, IActionSource.ofMachine(this)) == amount;
    }

    private boolean canProcessOperation(MachineMode mode) {
        return switch (mode) {
            case CHARGER -> canProcessChargerOperation();
            case INSCRIBER -> canProcessInscriberOperation();
            case CRYSTAL_GROWTH -> canProcessCrystalGrowthOperation();
            case POWDER -> canProcessPowderOperation();
            case NONE -> false;
        };
    }

    private boolean canProcessCrystalGrowthOperation() {
        return findCustomDataChargePressOperation() != null && canConsumeOperationEnergy();
    }

    private boolean canProcessChargerOperation() {
        if (findCustomDataChargePressOperation() != null) {
            return true;
        }
        if (canProcessDataChargerRecipeOperation()) {
            return true;
        }
        for (int inputSlot = 0; inputSlot < ITEM_INPUT_SLOT_COUNT; inputSlot++) {
            ItemStack input = this.storage.getStackInSlot(inputSlot);
            if (input.isEmpty()) {
                continue;
            }

            RecipeHolder<ChargerRecipe> recipeHolder = findChargerRecipe(input);
            if (recipeHolder != null && !recipeHolder.value().getResultItem().isEmpty() && findOutputSlot(recipeHolder.value().getResultItem()) >= 0 && canConsumeOperationEnergy()) {
                return true;
            }
            if (canChargeAePower(input)) {
                return true;
            }
            if (isAePowerFullyCharged(input) && findOutputSlot(input.copyWithCount(1)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canProcessDataChargerRecipeOperation() {
        if (!isDataChargerModule(this.storage.getStackInSlot(MACHINE_MODULE_SLOT))) {
            return false;
        }

        for (int inputSlot = 0; inputSlot < ITEM_INPUT_SLOT_COUNT; inputSlot++) {
            DataChargerRecipe recipe = findDataChargerRecipe(this.storage.getStackInSlot(inputSlot));
            if (recipe != null && canProcessDataChargerRecipe(recipe)) {
                return true;
            }
        }
        return false;
    }

    private boolean canProcessInscriberOperation() {
        if (this.level == null || !canConsumeOperationEnergy()) {
            return false;
        }

        if (findDataChargePressOperation() != null) {
            return true;
        }

        for (int middleSlot = 0; middleSlot < ITEM_INPUT_SLOT_COUNT; middleSlot++) {
            ItemStack middle = this.storage.getStackInSlot(middleSlot);
            if (middle.isEmpty()) {
                continue;
            }
            for (int topSlot = -1; topSlot < ITEM_INPUT_SLOT_COUNT; topSlot++) {
                if (topSlot == middleSlot || topSlot >= 0 && this.storage.getStackInSlot(topSlot).isEmpty()) {
                    continue;
                }
                ItemStack top = getInputOrEmpty(topSlot);
                for (int bottomSlot = -1; bottomSlot < ITEM_INPUT_SLOT_COUNT; bottomSlot++) {
                    if (bottomSlot == middleSlot || bottomSlot == topSlot ||
                            bottomSlot >= 0 && this.storage.getStackInSlot(bottomSlot).isEmpty()) {
                        continue;
                    }
                    InscriberRecipe recipe = InscriberRecipes.findRecipe(
                            this.level, middle, top, getInputOrEmpty(bottomSlot), true);
                    if (recipe != null && !DataChargePressRecipeSupport.isCircuitBoardRecipe(recipe) &&
                            !DataChargePressRecipeSupport.isPowderRecipe(recipe) &&
                            !recipe.getResultItem().isEmpty() && findOutputSlot(recipe.getResultItem()) >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canProcessPowderOperation() {
        if (this.level == null || !canConsumeOperationEnergy()) {
            return false;
        }

        for (int middleSlot = 0; middleSlot < ITEM_INPUT_SLOT_COUNT; middleSlot++) {
            ItemStack middle = this.storage.getStackInSlot(middleSlot);
            if (middle.isEmpty()) {
                continue;
            }
            InscriberRecipe recipe = findPowderRecipe(middle);
            if (recipe != null && findOutputSlot(recipe.getResultItem()) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canChargeAePower(ItemStack stack) {
        if (!(stack.getItem() instanceof appeng.api.implementations.items.IAEItemPowerStorage powerStorage)) {
            return false;
        }
        return getChargeOffer(stack, powerStorage) > 1.0E-4D;
    }

    private int getEffectiveProcessTicks() {
        int speedCards = this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        return Math.max(1, MAX_PROGRESS - speedCards * 40);
    }

    private boolean resetProgress() {
        if (this.progress == 0) {
            return false;
        }
        this.progress = 0;
        setChanged();
        return true;
    }

    private void updateLitState(boolean working) {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof DataIntegratedChargerBlock &&
                state.hasProperty(DataIntegratedChargerBlock.LIT) &&
                state.getValue(DataIntegratedChargerBlock.LIT) != working) {
            this.level.setBlock(this.worldPosition, state.setValue(DataIntegratedChargerBlock.LIT, working), 3);
        }
    }

    private void consumeOne(int slot) {
        consume(slot, 1);
    }

    private void consume(int slot, int amount) {
        if (slot < 0) {
            return;
        }
        ItemStack current = this.storage.getStackInSlot(slot);
        if (current.getCount() <= amount) {
            this.storage.setItemDirect(slot, ItemStack.EMPTY);
        } else {
            ItemStack updated = current.copy();
            updated.shrink(amount);
            this.storage.setItemDirect(slot, updated);
        }
    }

    private ItemStack getInputOrEmpty(int slot) {
        return slot < 0 ? ItemStack.EMPTY : this.storage.getStackInSlot(slot);
    }

    private int findOutputSlot(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() > ITEM_SLOT_CAPACITY) {
            return -1;
        }
        for (int slot = ITEM_OUTPUT_START_SLOT; slot < MACHINE_MODULE_SLOT; slot++) {
            ItemStack current = this.storage.getStackInSlot(slot);
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack) &&
                    current.getCount() + stack.getCount() <= ITEM_SLOT_CAPACITY) {
                return slot;
            }
        }
        return -1;
    }

    private void addToOutput(ItemStack stack) {
        int slot = findOutputSlot(stack);
        if (slot < 0) {
            return;
        }
        ItemStack current = this.storage.getStackInSlot(slot);
        if (current.isEmpty()) {
            this.storage.setItemDirect(slot, stack.copy());
        } else {
            ItemStack updated = current.copy();
            updated.grow(stack.getCount());
            this.storage.setItemDirect(slot, updated);
        }
    }

    /**
     * Input slots 6 and 7 are no longer exposed. Move their legacy contents into the six visible input slots
     * whenever possible; any remainder stays stored safely and is still returned when the block is broken.
     */
    private void moveRetiredInputStacks() {
        for (int slot = ITEM_INPUT_SLOT_COUNT; slot < ITEM_OUTPUT_START_SLOT; slot++) {
            ItemStack remaining = this.storage.getStackInSlot(slot);
            if (remaining.isEmpty()) {
                continue;
            }

            for (int target = 0; target < ITEM_INPUT_SLOT_COUNT && !remaining.isEmpty(); target++) {
                remaining = this.storage.insertItem(target, remaining, false);
            }
            this.storage.setItemDirect(slot, remaining);
        }
    }

    private boolean tryAutoExport() {
        return exportItemOutputs(this.outputSides);
    }

    private boolean exportItemOutputs(Set<Direction> sides) {
        if (sides.isEmpty() || !initializeAdjacentCapabilityCaches()) {
            return false;
        }
        boolean changed = false;
        for (int slot = ITEM_OUTPUT_START_SLOT; slot < MACHINE_MODULE_SLOT; slot++) {
            ItemStack current = this.storage.getStackInSlot(slot);
            if (current.isEmpty()) {
                continue;
            }
            ItemStack remaining = insertIntoAdjacentTargets(current, sides);
            if (!ItemStack.matches(current, remaining)) {
                this.storage.setItemDirect(slot, remaining);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Routes one output stack to each enabled side, preferring AE's generic inventory contract when available.
     *
     * <p>
     * AE interfaces and pattern providers expose both a generic inventory and an item-handler adapter. The adapter
     * has to represent a partially accepted generic stack as a wrapped item stack, so using it for an AE target can
     * leak that implementation detail into the machine's output slot. A generic insertion keeps the key and amount
     * separate and therefore preserves the actual output resource.
     * </p>
     */
    private ItemStack insertIntoAdjacentTargets(ItemStack stack, Set<Direction> sides) {
        ItemStack remaining = stack.copy();
        for (Direction side : sides) {
            if (remaining.isEmpty()) {
                break;
            }

            GenericStack genericStack = GenericStack.fromItemStack(remaining);
            GenericInternalInventory genericInventory = this.adjacentGenericInventories.get(side);
            if (genericInventory != null && genericStack != null &&
                    genericInventory.isSupportedType(genericStack.what())) {
                long originalAmount = genericStack.amount();
                long inserted = insertIntoGenericInventory(genericInventory, genericStack.what(), originalAmount);
                if (inserted > 0L) {
                    remaining = copyWithGenericAmount(remaining, genericStack.what(), originalAmount - inserted);
                }
                // Do not fall through to this side's item-handler adapter. It may be the same inventory and would
                // turn a legitimate partial generic remainder into a Wrapped Generic Stack.
                continue;
            }

            IItemHandler itemHandler = this.adjacentItemHandlers.get(side);
            if (itemHandler != null) {
                remaining = insertIntoItemHandler(itemHandler, remaining);
            }
        }
        return remaining;
    }

    private static long insertIntoGenericInventory(GenericInternalInventory inventory, AEKey what, long amount) {
        if (amount <= 0L || !inventory.canInsert() || !inventory.isSupportedType(what)) {
            return 0L;
        }

        long remaining = amount;
        inventory.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size() && remaining > 0L; slot++) {
                if (!inventory.isAllowedIn(slot, what)) {
                    continue;
                }

                long inserted = inventory.insert(slot, what, remaining, Actionable.MODULATE);
                if (inserted < 0L || inserted > remaining) {
                    throw new IllegalStateException("Adjacent generic inventory returned an invalid insertion amount");
                }
                remaining -= inserted;
            }
        } finally {
            inventory.endBatch();
        }
        return amount - remaining;
    }

    /**
     * Applies the item-handler contract in legal item-stack-sized chunks. Machine output slots intentionally support
     * parallel results above an item's normal max stack size, while external item handlers are not required to accept
     * such over-sized stacks in one call.
     */
    private static ItemStack insertIntoItemHandler(IItemHandler handler, ItemStack stack) {
        GenericStack genericStack = GenericStack.fromItemStack(stack);
        if (genericStack == null || genericStack.amount() <= 0L) {
            return stack;
        }

        long remainingAmount = genericStack.amount();
        long chunkSize = Math.max(1L, stack.getMaxStackSize());
        while (remainingAmount > 0L) {
            int requested = (int) Math.min(remainingAmount, Math.min(chunkSize, Integer.MAX_VALUE));
            ItemStack request = copyWithGenericAmount(stack, genericStack.what(), requested);
            ItemStack remainder = ItemHandlerHelper.insertItem(handler, request, false);
            long returnedAmount = getReturnedAmount(remainder, genericStack.what());
            if (returnedAmount < 0L || returnedAmount > requested) {
                break;
            }

            long inserted = requested - returnedAmount;
            if (inserted <= 0L) {
                break;
            }
            remainingAmount -= inserted;
        }

        return copyWithGenericAmount(stack, genericStack.what(), remainingAmount);
    }

    private static long getReturnedAmount(ItemStack remainder, AEKey expectedKey) {
        if (remainder.isEmpty()) {
            return 0L;
        }

        GenericStack returned = GenericStack.fromItemStack(remainder);
        if (returned == null || !expectedKey.equals(returned.what()) || returned.amount() < 0L) {
            return -1L;
        }
        return returned.amount();
    }

    private static ItemStack copyWithGenericAmount(ItemStack original, AEKey what, long amount) {
        if (amount <= 0L) {
            return ItemStack.EMPTY;
        }
        if (amount > Integer.MAX_VALUE && !GenericStack.isWrapped(original)) {
            throw new IllegalArgumentException("Item output amount exceeds the ItemStack integer range");
        }

        if (GenericStack.isWrapped(original)) {
            return GenericStack.wrapInItemStack(what, amount);
        }
        if (what instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) amount);
        }
        return original.copyWithCount((int) amount);
    }

    private boolean initializeAdjacentCapabilityCaches() {
        if (this.adjacentItemHandlers != null) {
            return true;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        this.adjacentGenericInventories = new AdjacentBlockCapabilityCache<>(
                AECapabilities.GENERIC_INTERNAL_INV, serverLevel, this.worldPosition, () -> !this.isRemoved());
        this.adjacentItemHandlers = new AdjacentBlockCapabilityCache<>(
                Capabilities.ItemHandler.BLOCK, serverLevel, this.worldPosition, () -> !this.isRemoved());
        return true;
    }

    private void refillEnergyCache() {
        var node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }
        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 1.0E-4D) {
            return;
        }
        double extracted = node.getGrid().getEnergyService().extractAEPower(missing, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 1.0E-4D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void updateEnergyCapacity() {
        this.setInternalMaxPower(ENERGY_CAPACITY);
    }

    private void onUpgradesChanged() {
        updateEnergyCapacity();
        saveChanges();
        markForClientUpdate();
    }

    private void onConfigChanged() {
        saveChanges();
        markForClientUpdate();
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
            tag.add(net.minecraft.nbt.StringTag.valueOf(side.getName()));
        }
        return tag;
    }

    private static MachineMode readProcessingMode(String serializedMode) {
        try {
            return MachineMode.valueOf(serializedMode);
        } catch (IllegalArgumentException ignored) {
            return MachineMode.NONE;
        }
    }

    private void dropStack(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack.copy());
        }
    }

    private InternalInventory createExternalInput() {
        InternalInventory[] inputs = new InternalInventory[ITEM_INPUT_SLOT_COUNT];
        for (int slot = 0; slot < ITEM_INPUT_SLOT_COUNT; slot++) {
            inputs[slot] = new FilteredInternalInventory(this.storage.getSlotInv(slot), new SlotFilter(true, false));
        }
        return new CombinedInternalInventory(inputs);
    }

    private InternalInventory createExternalOutput() {
        InternalInventory[] outputs = new InternalInventory[ITEM_OUTPUT_SLOT_COUNT];
        for (int index = 0; index < ITEM_OUTPUT_SLOT_COUNT; index++) {
            outputs[index] = new FilteredInternalInventory(
                    this.storage.getSlotInv(ITEM_OUTPUT_START_SLOT + index), new SlotFilter(false, true));
        }
        return new CombinedInternalInventory(outputs);
    }

    private GenericStackInv createFluidMenuInventory() {
        var inventory = new GenericStackInv(
                Set.of(AEKeyType.fluids()), this::syncTankFromMenuFluid, GenericStackInv.Mode.STORAGE, 1);
        inventory.setCapacity(AEKeyType.fluids(), FLUID_CAPACITY);
        return inventory;
    }

    private void syncMenuFluidFromTank() {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            this.fluidMenuInventory.setStack(0, createFluidGenericStack(this.fluidTank.getFluid()));
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private void syncTankFromMenuFluid() {
        if (this.syncingFluidMenu) {
            return;
        }

        this.syncingFluidMenu = true;
        try {
            GenericStack stack = this.fluidMenuInventory.getStack(0);
            if (stack == null || !(stack.what() instanceof AEFluidKey fluidKey) || stack.amount() <= 0) {
                this.fluidTank.setFluid(FluidStack.EMPTY);
            } else {
                int amount = (int) Math.min(FLUID_CAPACITY, stack.amount());
                this.fluidTank.setFluid(fluidKey.toStack(amount));
            }
            saveChanges();
            markForClientUpdate();
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private static @Nullable GenericStack createFluidGenericStack(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        return new GenericStack(AEFluidKey.of(fluid), fluid.getAmount());
    }

    private static MachineMode getMachineMode(ItemStack stack) {
        if (stack.isEmpty()) {
            return MachineMode.NONE;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (AE2_INSCRIBER.equals(id) || EXTENDED_AE_INSCRIBER.equals(id)) {
            return MachineMode.INSCRIBER;
        }
        if (DataChargePressRecipeSupport.CRYSTAL_GROWTH_MODULES.test(stack)) {
            return MachineMode.CRYSTAL_GROWTH;
        }
        if (AE2_CHARGER.equals(id) || EXTENDED_AE_CHARGER.equals(id) || isDataChargerModule(stack)) {
            return MachineMode.CHARGER;
        }
        return MachineMode.NONE;
    }

    private static boolean isDataChargerModule(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return DATA_CHARGER.equals(id) || EXTENDED_DATA_CHARGER.equals(id) ||
                DataChargePressRecipeSupport.DATA_CHARGER_MODULES.test(stack);
    }

    public enum MachineMode {
        NONE,
        CHARGER,
        INSCRIBER,
        CRYSTAL_GROWTH,
        POWDER
    }

    private record DataChargePressOperation(ItemStack result, int fluidAmount,
                                            List<DataChargePressRecipe.InputSlot> inputSlots) {}

    private final class StorageFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
            return slot < ITEM_INPUT_SLOT_COUNT || slot == MACHINE_MODULE_SLOT && isSupportedMachineModule(stack);
        }
    }

    private record SlotFilter(boolean allowInsert, boolean allowExtract) implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
            return this.allowInsert;
        }

        @Override
        public boolean allowExtract(InternalInventory inventory, int slot, int amount) {
            return this.allowExtract;
        }
    }

    private final class IntegratedFluidTank extends FluidTank {

        private IntegratedFluidTank() {
            super(FLUID_CAPACITY);
        }

        @Override
        protected void onContentsChanged() {
            syncMenuFluidFromTank();
            saveChanges();
            markForClientUpdate();
        }
    }

    private final class FluidInputHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? fluidTank.getFluid() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? fluidTank.getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && fluidTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fluidTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    /**
     * Preserves large stack counts, which vanilla item NBT otherwise limits to a byte-sized count.
     */
    private final class IntegratedChargerItemInventory extends AppEngInternalInventory {

        private IntegratedChargerItemInventory() {
            super(DataIntegratedChargerBlockEntity.this, STORAGE_SLOTS, ITEM_SLOT_CAPACITY);
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
                    stack.setCount(Math.min(storedStack.getInt(STORAGE_COUNT_TAG), getSlotLimit(slot)));
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
                setItemDirect(slot, remainder);
            }
            return result;
        }
    }
}
