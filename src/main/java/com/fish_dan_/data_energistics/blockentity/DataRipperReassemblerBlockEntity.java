package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipeInput;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.MemoryCardSettingsHelper;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DataRipperReassemblerBlockEntity extends AENetworkedPoweredBlockEntity
                                              implements InternalInventoryHost, IConfigurableObject, IUpgradeableObject, ICraftingMachine {

    public static final int ITEM_INPUT_START_SLOT = 0;
    public static final int ITEM_INPUT_SLOT_COUNT = 9;
    public static final int ITEM_OUTPUT_START_SLOT = 9;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 3;
    public static final int STORAGE_SLOTS = 12;
    public static final int KEY_INPUT_SLOT = 0;
    public static final int KEY_OUTPUT_SLOT = 1;
    public static final int KEY_SLOT_COUNT = 2;
    public static final int FLUID_INPUT_CAPACITY = 64_000;
    public static final int FLUID_OUTPUT_CAPACITY = 64_000;
    public static final long KEY_INPUT_CAPACITY = 6_400_000L;
    public static final long KEY_OUTPUT_CAPACITY = 6_400_000L;
    public static final int MAX_PROGRESS = 200;
    public static final int UPGRADE_SLOTS = 4;
    public static final double ENERGY_CAPACITY = 160_000.0D;

    private static final String STORAGE_TAG = "storage";
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

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DATA_RIPPER_REASSEMBLER.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private final InternalInventory externalInput = createExternalInput();
    private final InternalInventory externalOutput = createExternalOutput();
    @Getter
    private final InternalInventory externalInventory = new CombinedInternalInventory(this.externalInput, this.externalOutput);

    private final FluidTank fluidInputTankA = new SyncFluidTank(FLUID_INPUT_CAPACITY);
    private final FluidTank fluidInputTankB = new SyncFluidTank(FLUID_INPUT_CAPACITY);
    private final FluidTank fluidOutputTankA = new SyncFluidTank(FLUID_OUTPUT_CAPACITY);
    private final FluidTank fluidOutputTankB = new SyncFluidTank(FLUID_OUTPUT_CAPACITY);
    private final GenericStackInv fluidMenuInventoryA = createFluidMenuInventory(this::syncTankAFromMenuFluid, FLUID_INPUT_CAPACITY, this.fluidInputTankB::getFluid);
    private final GenericStackInv fluidMenuInventoryB = createFluidMenuInventory(this::syncTankBFromMenuFluid, FLUID_INPUT_CAPACITY, this.fluidInputTankA::getFluid);
    private final GenericStackInv fluidOutputMenuInventoryA = createFluidMenuInventory(this::syncOutputTankAFromMenuFluid, FLUID_OUTPUT_CAPACITY, this.fluidOutputTankB::getFluid);
    private final GenericStackInv fluidOutputMenuInventoryB = createFluidMenuInventory(this::syncOutputTankBFromMenuFluid, FLUID_OUTPUT_CAPACITY, this.fluidOutputTankA::getFluid);
    private final GenericStackInv keyMenuInventory = createKeyMenuInventory();
    private final GenericStackInv keyOutputMenuInventory = createKeyOutputMenuInventory();
    @Getter
    private final GenericInternalInventory externalKeyInventory = new ReassemblerKeyInventory();
    @Getter
    private final IFluidHandler externalFluidHandler = new ReassemblerFluidHandler();
    @Getter
    private final MEStorage externalPatternInputStorage = new PatternInputStorage();
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private boolean syncingFluidMenu;
    private boolean syncingKeyMenu;
    private GenericStack keyInputStack;
    private GenericStack keyOutputStack;
    private final Set<Direction> itemOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> fluidOutputSides = EnumSet.allOf(Direction.class);
    private final Set<Direction> keyOutputSides = EnumSet.allOf(Direction.class);
    @Getter
    private int progress;
    @Getter
    private int maxProgress = MAX_PROGRESS;
    private ResourceLocation activeRecipeId;

    public DataRipperReassemblerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_RIPPER_REASSEMBLER.get())
                .setIdlePowerUsage(1.0D);
        this.setInternalMaxPower(ENERGY_CAPACITY);
        this.configManager.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.storage.setFilter(new IAEItemFilter() {

            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return slot >= ITEM_INPUT_START_SLOT && slot < ITEM_INPUT_START_SLOT + ITEM_INPUT_SLOT_COUNT;
            }
        });
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
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        refillEnergyCache();
        processRecipe();
        tryAutoExport();
        updateOnlineState();
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean isWorking() {
        return this.getProgress() > 0;
    }

    @Override
    public boolean acceptsPlans() {
        return true;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(AEItemKey.of(ModBlocks.DATA_RIPPER_REASSEMBLER.get()),
                ModBlocks.DATA_RIPPER_REASSEMBLER.get().getName(), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, Direction ejectionDirection) {
        PatternPushState state = new PatternPushState(copyInputSlots(), this.fluidInputTankA.getFluid().copy(),
                this.fluidInputTankB.getFluid().copy(), copyKeyStack(this.keyInputStack));
        if (!canAcceptPatternInputs(state, inputHolder)) {
            return false;
        }

        applyPatternPushState(state);
        saveChanges();
        markForClientUpdate();
        return true;
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storage;
    }

    public ConfigMenuInventory getFluidMenuInventoryA() {
        return this.fluidMenuInventoryA.createMenuWrapper();
    }

    public ConfigMenuInventory getFluidMenuInventoryB() {
        return this.fluidMenuInventoryB.createMenuWrapper();
    }

    public ConfigMenuInventory getFluidOutputMenuInventoryA() {
        return this.fluidOutputMenuInventoryA.createMenuWrapper();
    }

    public ConfigMenuInventory getFluidOutputMenuInventoryB() {
        return this.fluidOutputMenuInventoryB.createMenuWrapper();
    }

    public ConfigMenuInventory getKeyMenuInventory() {
        return this.keyMenuInventory.createMenuWrapper();
    }

    public ConfigMenuInventory getKeyOutputMenuInventory() {
        return this.keyOutputMenuInventory.createMenuWrapper();
    }

    public FluidStack getFluidInputA() {
        return this.fluidInputTankA.getFluid();
    }

    public FluidStack getFluidInputB() {
        return this.fluidInputTankB.getFluid();
    }

    public FluidStack getFluidOutputA() {
        return this.fluidOutputTankA.getFluid();
    }

    public FluidStack getFluidOutputB() {
        return this.fluidOutputTankB.getFluid();
    }

    public int getFluidInputCapacity() {
        return this.fluidInputTankA.getCapacity();
    }

    public int getFluidOutputCapacity() {
        return this.fluidOutputTankA.getCapacity();
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
        return this.storage;
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
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        this.upgrades.readFromNBT(data, UPGRADES_TAG, registries);
        this.fluidInputTankA.readFromNBT(registries, data.getCompound(FLUID_INPUT_A_TAG));
        this.fluidInputTankB.readFromNBT(registries, data.getCompound(FLUID_INPUT_B_TAG));
        this.fluidOutputTankA.readFromNBT(registries, data.getCompound(FLUID_OUTPUT_A_TAG));
        this.fluidOutputTankB.readFromNBT(registries, data.getCompound(FLUID_OUTPUT_B_TAG));
        this.keyInputStack = data.contains(KEY_INPUT_TAG) ? GenericStack.readTag(registries, data.getCompound(KEY_INPUT_TAG)) : null;
        this.keyOutputStack = data.contains(KEY_OUTPUT_TAG) ? GenericStack.readTag(registries, data.getCompound(KEY_OUTPUT_TAG)) : null;
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
        this.progress = data.getInt(PROGRESS_TAG);
        this.maxProgress = data.contains(MAX_PROGRESS_TAG) ? Math.max(1, data.getInt(MAX_PROGRESS_TAG)) : MAX_PROGRESS;
        this.activeRecipeId = data.contains(ACTIVE_RECIPE_TAG) ? ResourceLocation.tryParse(data.getString(ACTIVE_RECIPE_TAG)) : null;
        syncMenuFluidsFromTanks();
        syncKeyMenuFromStack();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        this.upgrades.writeToNBT(data, UPGRADES_TAG, registries);
        data.put(FLUID_INPUT_A_TAG, this.fluidInputTankA.writeToNBT(registries, new CompoundTag()));
        data.put(FLUID_INPUT_B_TAG, this.fluidInputTankB.writeToNBT(registries, new CompoundTag()));
        data.put(FLUID_OUTPUT_A_TAG, this.fluidOutputTankA.writeToNBT(registries, new CompoundTag()));
        data.put(FLUID_OUTPUT_B_TAG, this.fluidOutputTankB.writeToNBT(registries, new CompoundTag()));
        CompoundTag autoExportData = new CompoundTag();
        this.configManager.writeToNBT(autoExportData, registries);
        data.put(AUTO_EXPORT_TAG, autoExportData);
        data.put(ITEM_OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
        data.put(FLUID_OUTPUT_SIDES_TAG, createOutputSidesTag(this.fluidOutputSides));
        data.put(KEY_OUTPUT_SIDES_TAG, createOutputSidesTag(this.keyOutputSides));
        data.put(OUTPUT_SIDES_TAG, createOutputSidesTag(this.itemOutputSides));
        data.putInt(PROGRESS_TAG, this.progress);
        data.putInt(MAX_PROGRESS_TAG, this.maxProgress);
        if (this.activeRecipeId != null) {
            data.putString(ACTIVE_RECIPE_TAG, this.activeRecipeId.toString());
        }
        if (this.keyInputStack != null && this.keyInputStack.amount() > 0) {
            data.put(KEY_INPUT_TAG, GenericStack.writeTag(registries, this.keyInputStack));
        }
        if (this.keyOutputStack != null && this.keyOutputStack.amount() > 0) {
            data.put(KEY_OUTPUT_TAG, GenericStack.writeTag(registries, this.keyOutputStack));
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
        this.fluidInputTankA.setFluid(FluidStack.EMPTY);
        this.fluidInputTankB.setFluid(FluidStack.EMPTY);
        this.fluidOutputTankA.setFluid(FluidStack.EMPTY);
        this.fluidOutputTankB.setFluid(FluidStack.EMPTY);
        this.keyInputStack = null;
        this.keyOutputStack = null;
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

    private void processRecipe() {
        if (!isOnline()) {
            resetProcessingState();
            return;
        }

        RecipeHolder<DataRipperReassemblerRecipe> recipeHolder = getActiveOrMatchingRecipe();
        if (recipeHolder == null) {
            resetProcessingState();
            return;
        }

        DataRipperReassemblerRecipe recipe = recipeHolder.value();
        List<ItemStack> itemOutputs = recipe.getCraftedItemOutputs();
        if (!canAcceptItemOutputs(recipe, itemOutputs)) {
            resetProcessingState();
            return;
        }

        if (!recipeHolder.id().equals(this.activeRecipeId)) {
            this.activeRecipeId = recipeHolder.id();
            this.progress = 0;
            this.maxProgress = getEffectiveProcessTicks(recipe);
            setChanged();
        }

        this.maxProgress = getEffectiveProcessTicks(recipe);
        this.progress++;
        setChanged();

        if (this.progress < this.maxProgress) {
            return;
        }

        RecipeProcessingState processingState = captureRecipeProcessingState();
        if (!consumeRecipeInputs(recipe) || !insertRecipeOutputs(recipe, itemOutputs)) {
            restoreRecipeProcessingState(processingState);
            resetProcessingState();
            saveChanges();
            markForClientUpdate();
            return;
        }

        resetProcessingState();
        saveChanges();
        markForClientUpdate();
    }

    private void resetProcessingState() {
        if (this.progress == 0 && this.maxProgress == MAX_PROGRESS && this.activeRecipeId == null) {
            return;
        }

        this.progress = 0;
        this.maxProgress = MAX_PROGRESS;
        this.activeRecipeId = null;
        setChanged();
    }

    private int getEffectiveProcessTicks(DataRipperReassemblerRecipe recipe) {
        int speedCards = this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        int reducedTicks = recipe.getProcessTicks() - speedCards * 40;
        return Math.max(1, reducedTicks);
    }

    private RecipeHolder<DataRipperReassemblerRecipe> getActiveOrMatchingRecipe() {
        if (this.level == null) {
            return null;
        }

        DataRipperReassemblerRecipeInput input = createRecipeInput();
        if (this.activeRecipeId != null) {
            RecipeHolder<DataRipperReassemblerRecipe> active = this.level.getRecipeManager()
                    .byKey(this.activeRecipeId)
                    .filter(holder -> holder.value() instanceof DataRipperReassemblerRecipe)
                    .map(holder -> (RecipeHolder<DataRipperReassemblerRecipe>) holder)
                    .orElse(null);
            if (active != null && active.value().matches(input, this.level)) {
                return active;
            }
        }

        for (RecipeHolder<DataRipperReassemblerRecipe> holder : this.level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get())) {
            if (holder.value().matches(input, this.level)) {
                return holder;
            }
        }
        return null;
    }

    private DataRipperReassemblerRecipeInput createRecipeInput() {
        List<ItemStack> inputs = new ArrayList<>(ITEM_INPUT_SLOT_COUNT);
        for (int i = 0; i < ITEM_INPUT_SLOT_COUNT; i++) {
            inputs.add(this.storage.getStackInSlot(ITEM_INPUT_START_SLOT + i).copy());
        }
        List<GenericStack> fluids = new ArrayList<>(DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS);
        GenericStack fluidA = createFluidGenericStack(this.fluidInputTankA.getFluid());
        GenericStack fluidB = createFluidGenericStack(this.fluidInputTankB.getFluid());
        if (fluidA != null) {
            fluids.add(fluidA);
        }
        if (fluidB != null) {
            fluids.add(fluidB);
        }
        return new DataRipperReassemblerRecipeInput(inputs, fluids, copyKeyStack(this.keyInputStack));
    }

    private boolean canAcceptItemOutputs(DataRipperReassemblerRecipe recipe, List<ItemStack> itemOutputs) {
        ItemStack[] simulated = new ItemStack[ITEM_OUTPUT_SLOT_COUNT];
        for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT; i++) {
            simulated[i] = this.storage.getStackInSlot(ITEM_OUTPUT_START_SLOT + i).copy();
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

    private boolean consumeRecipeInputs(DataRipperReassemblerRecipe recipe) {
        Map<AEFluidKey, Long> requiredFluidAmounts = recipe.getMergedFluidInputAmounts();
        if (requiredFluidAmounts == null) {
            return false;
        }
        for (long amount : requiredFluidAmounts.values()) {
            if (amount > Integer.MAX_VALUE) {
                return false;
            }
        }

        for (DataRipperReassemblerIngredient countedIngredient : recipe.getItemInputs()) {
            int remaining = countedIngredient.count();
            for (int i = 0; i < ITEM_INPUT_SLOT_COUNT && remaining > 0; i++) {
                int slot = ITEM_INPUT_START_SLOT + i;
                ItemStack stack = this.storage.getStackInSlot(slot);
                if (stack.isEmpty() || !countedIngredient.ingredient().test(stack)) {
                    continue;
                }

                int consumed = Math.min(remaining, stack.getCount());
                ItemStack updated = stack.copy();
                updated.shrink(consumed);
                this.storage.setItemDirect(slot, updated);
                remaining -= consumed;
            }

            if (remaining > 0) {
                return false;
            }
        }

        GenericStack requiredKey = recipe.getKeyInput();
        if (requiredKey != null) {
            if (this.keyInputStack == null || !requiredKey.what().equals(this.keyInputStack.what()) || this.keyInputStack.amount() < requiredKey.amount()) {
                return false;
            }

            long remaining = this.keyInputStack.amount() - requiredKey.amount();
            this.keyInputStack = remaining > 0 ? new GenericStack(this.keyInputStack.what(), remaining) : null;
            syncKeyMenuFromStack();
        }

        for (Map.Entry<AEFluidKey, Long> requirement : requiredFluidAmounts.entrySet()) {
            AEFluidKey requiredKeyFluid = requirement.getKey();
            int remaining = requirement.getValue().intValue();

            if (matchesFluidKey(this.fluidInputTankA.getFluid(), requiredKeyFluid)) {
                int drained = this.fluidInputTankA.drain(Math.min(remaining, this.fluidInputTankA.getFluidAmount()),
                        IFluidHandler.FluidAction.EXECUTE).getAmount();
                remaining -= drained;
            }
            if (remaining > 0 && matchesFluidKey(this.fluidInputTankB.getFluid(), requiredKeyFluid)) {
                int drained = this.fluidInputTankB.drain(Math.min(remaining, this.fluidInputTankB.getFluidAmount()),
                        IFluidHandler.FluidAction.EXECUTE).getAmount();
                remaining -= drained;
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    private boolean insertRecipeOutputs(DataRipperReassemblerRecipe recipe, List<ItemStack> itemOutputs) {
        for (ItemStack output : itemOutputs) {
            if (output.isEmpty()) {
                continue;
            }

            ItemStack remaining = output.copy();
            for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT && !remaining.isEmpty(); i++) {
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
        FluidStack simulatedA = this.fluidOutputTankA.getFluid().copy();
        FluidStack simulatedB = this.fluidOutputTankB.getFluid().copy();

        for (GenericStack output : recipe.getFluidOutputs()) {
            if (!(output.what() instanceof AEFluidKey fluidKey) || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) {
                return false;
            }

            int amount = (int) output.amount();
            if (matchesFluidKey(simulatedA, fluidKey)) {
                if ((long) simulatedA.getAmount() + amount > FLUID_OUTPUT_CAPACITY) {
                    return false;
                }
                simulatedA.setAmount(simulatedA.getAmount() + amount);
                continue;
            }
            if (matchesFluidKey(simulatedB, fluidKey)) {
                if ((long) simulatedB.getAmount() + amount > FLUID_OUTPUT_CAPACITY) {
                    return false;
                }
                simulatedB.setAmount(simulatedB.getAmount() + amount);
                continue;
            }
            if (simulatedA.isEmpty()) {
                if ((long) simulatedA.getAmount() + amount > FLUID_OUTPUT_CAPACITY) {
                    return false;
                }
                simulatedA = fluidKey.toStack(amount);
                continue;
            }
            if (simulatedB.isEmpty()) {
                if ((long) simulatedB.getAmount() + amount > FLUID_OUTPUT_CAPACITY) {
                    return false;
                }
                simulatedB = fluidKey.toStack(amount);
                continue;
            }
            return false;
        }

        return true;
    }

    private boolean canAcceptKeyOutput(DataRipperReassemblerRecipe recipe) {
        GenericStack keyOutput = recipe.getKeyOutput();
        if (keyOutput == null || keyOutput.what() == null || keyOutput.amount() <= 0) {
            return true;
        }

        if (this.keyOutputStack == null || this.keyOutputStack.what() == null || this.keyOutputStack.amount() <= 0) {
            return keyOutput.amount() <= KEY_OUTPUT_CAPACITY;
        }

        if (!this.keyOutputStack.what().equals(keyOutput.what())) {
            return false;
        }

        return keyOutput.amount() <= KEY_OUTPUT_CAPACITY - this.keyOutputStack.amount();
    }

    private boolean insertKeyOutput(GenericStack stack) {
        if (stack.what() == null || stack.amount() <= 0 || stack.amount() > KEY_OUTPUT_CAPACITY) {
            return false;
        }
        if (this.keyOutputStack == null || this.keyOutputStack.what() == null || this.keyOutputStack.amount() <= 0) {
            this.keyOutputStack = new GenericStack(stack.what(), stack.amount());
        } else {
            if (!this.keyOutputStack.what().equals(stack.what()) || stack.amount() > KEY_OUTPUT_CAPACITY - this.keyOutputStack.amount()) {
                return false;
            }
            this.keyOutputStack = new GenericStack(stack.what(), this.keyOutputStack.amount() + stack.amount());
        }
        syncKeyMenuFromStack();
        return true;
    }

    private boolean insertFluidOutput(AEFluidKey fluidKey, long amountLong) {
        if (amountLong <= 0 || amountLong > Integer.MAX_VALUE) {
            return false;
        }
        int amount = (int) amountLong;
        if (matchesFluidKey(this.fluidOutputTankA.getFluid(), fluidKey) || this.fluidOutputTankA.isEmpty()) {
            return this.fluidOutputTankA.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE) == amount;
        }
        if (matchesFluidKey(this.fluidOutputTankB.getFluid(), fluidKey) || this.fluidOutputTankB.isEmpty()) {
            return this.fluidOutputTankB.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE) == amount;
        }
        return false;
    }

    private RecipeProcessingState captureRecipeProcessingState() {
        ItemStack[] itemSlots = new ItemStack[STORAGE_SLOTS];
        for (int slot = 0; slot < itemSlots.length; slot++) {
            itemSlots[slot] = this.storage.getStackInSlot(slot).copy();
        }
        return new RecipeProcessingState(itemSlots,
                this.fluidInputTankA.getFluid().copy(), this.fluidInputTankB.getFluid().copy(),
                this.fluidOutputTankA.getFluid().copy(), this.fluidOutputTankB.getFluid().copy(),
                copyKeyStack(this.keyInputStack), copyKeyStack(this.keyOutputStack));
    }

    private void restoreRecipeProcessingState(RecipeProcessingState state) {
        for (int slot = 0; slot < state.itemSlots.length; slot++) {
            this.storage.setItemDirect(slot, state.itemSlots[slot].copy());
        }
        this.fluidInputTankA.setFluid(state.fluidInputA.copy());
        this.fluidInputTankB.setFluid(state.fluidInputB.copy());
        this.fluidOutputTankA.setFluid(state.fluidOutputA.copy());
        this.fluidOutputTankB.setFluid(state.fluidOutputB.copy());
        this.keyInputStack = copyKeyStack(state.keyInput);
        this.keyOutputStack = copyKeyStack(state.keyOutput);
        syncMenuFluidsFromTanks();
        syncKeyMenuFromStack();
    }

    private ItemStack insertIntoOutputSlot(ItemStack[] simulated, int outputIndex, ItemStack stack, boolean modulate) {
        int slot = ITEM_OUTPUT_START_SLOT + outputIndex;
        ItemStack current = simulated != null ? simulated[outputIndex] : this.storage.getStackInSlot(slot);
        int slotLimit = this.storage.getSlotLimit(slot);

        if (current.isEmpty()) {
            int inserted = Math.min(stack.getCount(), Math.min(slotLimit, stack.getMaxStackSize()));
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

        int maxCount = Math.min(slotLimit, current.getMaxStackSize());
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

    private void tryAutoExport() {
        if (!isAutoExportEnabled()) {
            return;
        }

        List<IItemHandler> itemHandlers = getAdjacentItemHandlers(this.itemOutputSides);
        List<IFluidHandler> fluidHandlers = getAdjacentFluidHandlers(this.fluidOutputSides);
        List<GenericInternalInventory> keyInventories = getAdjacentKeyInventories(this.keyOutputSides);

        boolean changed = exportItemOutputs(itemHandlers);
        changed |= exportFluidOutput(this.fluidOutputTankA, fluidHandlers);
        changed |= exportFluidOutput(this.fluidOutputTankB, fluidHandlers);
        changed |= exportKeyOutput(keyInventories);

        if (changed) {
            saveChanges();
            markForClientUpdate();
        }
    }

    private boolean exportItemOutputs(List<IItemHandler> adjacentHandlers) {
        boolean changed = false;
        for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT; i++) {
            int slot = ITEM_OUTPUT_START_SLOT + i;
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

    private boolean exportKeyOutput(List<GenericInternalInventory> adjacentInventories) {
        if (this.keyOutputStack == null || this.keyOutputStack.amount() <= 0 || this.keyOutputStack.what() == null) {
            return false;
        }

        AEKey what = this.keyOutputStack.what();
        long originalAmount = this.keyOutputStack.amount();
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
                for (int slot = 0; slot < inventory.size() && remaining > 0L; slot++) {
                    if (!inventory.isAllowedIn(slot, what)) {
                        continue;
                    }
                    long inserted = inventory.insert(slot, what, remaining, Actionable.MODULATE);
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

        this.keyOutputStack = remaining <= 0L ? null : new GenericStack(what, remaining);
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
        if (this.level == null) {
            return List.of();
        }

        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : outputSides) {
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
        return handlers;
    }

    private List<IFluidHandler> getAdjacentFluidHandlers(Set<Direction> outputSides) {
        if (this.level == null) {
            return List.of();
        }

        List<IFluidHandler> handlers = new ArrayList<>();
        for (Direction direction : outputSides) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            IFluidHandler handler = this.level.getCapability(
                    Capabilities.FluidHandler.BLOCK,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    private List<GenericInternalInventory> getAdjacentKeyInventories(Set<Direction> outputSides) {
        if (this.level == null) {
            return List.of();
        }

        List<GenericInternalInventory> inventories = new ArrayList<>();
        for (Direction direction : outputSides) {
            BlockPos targetPos = this.worldPosition.relative(direction);
            BlockState targetState = this.level.getBlockState(targetPos);
            if (targetState.isAir()) {
                continue;
            }

            GenericInternalInventory inventory = this.level.getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV,
                    targetPos,
                    targetState,
                    this.level.getBlockEntity(targetPos),
                    direction.getOpposite());
            if (inventory != null) {
                inventories.add(inventory);
            }
        }
        return inventories;
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

    private ItemStack[] copyInputSlots() {
        ItemStack[] slots = new ItemStack[ITEM_INPUT_SLOT_COUNT];
        for (int i = 0; i < ITEM_INPUT_SLOT_COUNT; i++) {
            slots[i] = this.storage.getStackInSlot(ITEM_INPUT_START_SLOT + i).copy();
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

            int maxCount = Math.min(this.storage.getSlotLimit(ITEM_INPUT_START_SLOT + i), current.getMaxStackSize());
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

            int maxCount = Math.min(this.storage.getSlotLimit(ITEM_INPUT_START_SLOT + i), prototype.getMaxStackSize());
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
        if (matchesFluidKey(state.fluidInputA, fluidKey)) {
            return fillSimulatedTank(state, true, fluidKey, remaining, FLUID_INPUT_CAPACITY);
        }
        if (matchesFluidKey(state.fluidInputB, fluidKey)) {
            return fillSimulatedTank(state, false, fluidKey, remaining, FLUID_INPUT_CAPACITY);
        }

        if (state.fluidInputA.isEmpty()) {
            return setSimulatedTank(state, true, fluidKey, remaining);
        }
        if (state.fluidInputB.isEmpty()) {
            return setSimulatedTank(state, false, fluidKey, remaining);
        }
        return false;
    }

    private boolean fillSimulatedTank(PatternPushState state, boolean firstTank, AEFluidKey fluidKey, int amount, int capacity) {
        FluidStack current = firstTank ? state.fluidInputA : state.fluidInputB;
        if (!matchesFluidKey(current, fluidKey)) {
            return false;
        }
        long updatedAmount = (long) current.getAmount() + amount;
        if (updatedAmount > capacity) {
            return false;
        }

        FluidStack updated = current.copy();
        updated.setAmount((int) updatedAmount);
        if (firstTank) {
            state.fluidInputA = updated;
        } else {
            state.fluidInputB = updated;
        }
        return true;
    }

    private boolean setSimulatedTank(PatternPushState state, boolean firstTank, AEFluidKey fluidKey, int amount) {
        if (amount > FLUID_INPUT_CAPACITY) {
            return false;
        }

        FluidStack newFluid = fluidKey.toStack(amount);
        if (firstTank) {
            state.fluidInputA = newFluid;
        } else {
            state.fluidInputB = newFluid;
        }
        return true;
    }

    private boolean canAcceptGenericKeyInput(PatternPushState state, AEKey key, long amount) {
        if (amount <= 0 || amount > KEY_INPUT_CAPACITY) {
            return amount <= 0;
        }

        if (state.keyInput == null || state.keyInput.what() == null || state.keyInput.amount() <= 0) {
            state.keyInput = new GenericStack(key, amount);
            return true;
        }

        if (!state.keyInput.what().equals(key)) {
            return false;
        }

        long updatedAmount = state.keyInput.amount() + amount;
        if (updatedAmount > KEY_INPUT_CAPACITY) {
            return false;
        }

        state.keyInput = new GenericStack(key, updatedAmount);
        return true;
    }

    private void applyPatternPushState(PatternPushState state) {
        for (int i = 0; i < ITEM_INPUT_SLOT_COUNT; i++) {
            this.storage.setItemDirect(ITEM_INPUT_START_SLOT + i, state.itemInputs[i]);
        }

        this.fluidInputTankA.setFluid(state.fluidInputA);
        this.fluidInputTankB.setFluid(state.fluidInputB);
        this.keyInputStack = copyKeyStack(state.keyInput);
        syncKeyMenuFromStack();
    }

    private static boolean matchesFluidKey(FluidStack stack, AEFluidKey key) {
        if (stack.isEmpty()) {
            return false;
        }
        AEFluidKey existing = AEFluidKey.of(stack);
        return existing != null && existing.equals(key);
    }

    private static @Nullable GenericStack createFluidGenericStack(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return null;
        }
        AEFluidKey key = AEFluidKey.of(fluid);
        return key == null ? null : new GenericStack(key, fluid.getAmount());
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
        InternalInventory[] inputs = new InternalInventory[ITEM_INPUT_SLOT_COUNT];
        for (int i = 0; i < ITEM_INPUT_SLOT_COUNT; i++) {
            inputs[i] = new FilteredInternalInventory(
                    this.storage.getSlotInv(ITEM_INPUT_START_SLOT + i),
                    new SlotFilter(stack -> true, false));
        }
        return new CombinedInternalInventory(inputs);
    }

    private InternalInventory createExternalOutput() {
        InternalInventory[] outputs = new InternalInventory[ITEM_OUTPUT_SLOT_COUNT];
        for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT; i++) {
            outputs[i] = new FilteredInternalInventory(
                    this.storage.getSlotInv(ITEM_OUTPUT_START_SLOT + i),
                    new SlotFilter(stack -> false, true));
        }
        return new CombinedInternalInventory(outputs);
    }

    private GenericStackInv createFluidMenuInventory(Runnable syncAction, int capacity, Supplier<FluidStack> pairedFluidSupplier) {
        var inv = new GenericStackInv(Set.of(AEKeyType.fluids()), syncAction, GenericStackInv.Mode.STORAGE, 1) {

            {
                this.setFilter((slot, what) -> {
                    if (!(what instanceof AEFluidKey fluidKey)) {
                        return true;
                    }
                    return !conflictsWithExistingFluid(pairedFluidSupplier.get(), fluidKey);
                });
            }
        };
        inv.setCapacity(AEKeyType.fluids(), capacity);
        return inv;
    }

    private GenericStackInv createKeyMenuInventory() {
        var inv = new GenericStackInv(AEKeyTypes.getAll(), this::syncStackFromKeyMenu, GenericStackInv.Mode.STORAGE, 1) {

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
        applyKeyCapacities(inv, KEY_INPUT_CAPACITY);
        return inv;
    }

    private GenericStackInv createKeyOutputMenuInventory() {
        var inv = new GenericStackInv(AEKeyTypes.getAll(), this::syncStackFromKeyOutputMenu, GenericStackInv.Mode.STORAGE, 1) {

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
        applyKeyCapacities(inv, KEY_OUTPUT_CAPACITY);
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
            syncMenuFluidFromTank(this.fluidInputTankA, this.fluidMenuInventoryA);
            syncMenuFluidFromTank(this.fluidInputTankB, this.fluidMenuInventoryB);
            syncMenuFluidFromTank(this.fluidOutputTankA, this.fluidOutputMenuInventoryA);
            syncMenuFluidFromTank(this.fluidOutputTankB, this.fluidOutputMenuInventoryB);
        } finally {
            this.syncingFluidMenu = false;
        }
    }

    private void syncMenuFluidFromTank(FluidTank tank, GenericStackInv menuInventory) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            menuInventory.setStack(0, null);
        } else {
            AEFluidKey key = AEFluidKey.of(fluid);
            menuInventory.setStack(0, key == null ? null : new GenericStack(key, fluid.getAmount()));
        }
    }

    private void syncTankAFromMenuFluid() {
        syncTankFromMenuFluid(this.fluidInputTankA, this.fluidInputTankB, this.fluidMenuInventoryA);
    }

    private void syncTankBFromMenuFluid() {
        syncTankFromMenuFluid(this.fluidInputTankB, this.fluidInputTankA, this.fluidMenuInventoryB);
    }

    private void syncOutputTankAFromMenuFluid() {
        syncTankFromMenuFluid(this.fluidOutputTankA, this.fluidOutputTankB, this.fluidOutputMenuInventoryA);
    }

    private void syncOutputTankBFromMenuFluid() {
        syncTankFromMenuFluid(this.fluidOutputTankB, this.fluidOutputTankA, this.fluidOutputMenuInventoryB);
    }

    private void syncTankFromMenuFluid(FluidTank tank, FluidTank pairedTank, GenericStackInv menuInventory) {
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
                if (conflictsWithPairedTank(pairedTank, newFluid)) {
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

    private boolean conflictsWithPairedTank(FluidTank pairedTank, FluidStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        FluidStack paired = pairedTank.getFluid();
        return !paired.isEmpty() && FluidStack.isSameFluidSameComponents(paired, candidate);
    }

    private boolean conflictsWithExistingFluid(FluidStack existing, AEFluidKey candidate) {
        if (existing.isEmpty()) {
            return false;
        }
        AEFluidKey existingKey = AEFluidKey.of(existing);
        return existingKey != null && existingKey.equals(candidate);
    }

    private void syncKeyMenuFromStack() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            this.keyMenuInventory.setStack(0, this.keyInputStack);
            this.keyOutputMenuInventory.setStack(0, this.keyOutputStack);
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
            var previous = this.keyInputStack;
            var stack = this.keyMenuInventory.getStack(0);
            if (!isCompatibleKeyReplacement(previous, stack)) {
                this.keyMenuInventory.setStack(0, previous);
                return;
            }
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                this.keyInputStack = null;
            } else {
                this.keyInputStack = clampKeyStack(stack, KEY_INPUT_CAPACITY);
            }
            saveChanges();
            markForClientUpdate();
        } finally {
            this.syncingKeyMenu = false;
        }
    }

    private void syncStackFromKeyOutputMenu() {
        if (this.syncingKeyMenu) {
            return;
        }

        this.syncingKeyMenu = true;
        try {
            var previous = this.keyOutputStack;
            var stack = this.keyOutputMenuInventory.getStack(0);
            if (!isCompatibleKeyReplacement(previous, stack)) {
                this.keyOutputMenuInventory.setStack(0, previous);
                return;
            }
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                this.keyOutputStack = null;
            } else {
                this.keyOutputStack = clampKeyStack(stack, KEY_OUTPUT_CAPACITY);
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
            return KEY_SLOT_COUNT;
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            return switch (slot) {
                case KEY_INPUT_SLOT -> keyInputStack;
                case KEY_OUTPUT_SLOT -> keyOutputStack;
                default -> null;
            };
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
            return isAllowedMenuKey(key) ? Math.max(KEY_INPUT_CAPACITY, KEY_OUTPUT_CAPACITY) : 0L;
        }

        @Override
        public long getCapacity(AEKeyType keyType) {
            return isSupportedType(keyType) ? Math.max(KEY_INPUT_CAPACITY, KEY_OUTPUT_CAPACITY) : 0L;
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
            if (slot != KEY_INPUT_SLOT) {
                return;
            }

            GenericStack normalized = null;
            if (newStack != null && newStack.what() != null && newStack.amount() > 0L) {
                if (!isAllowedMenuKey(newStack.what()) || !isCompatibleKeyReplacement(keyInputStack, newStack)) {
                    return;
                }
                normalized = clampKeyStack(newStack, KEY_INPUT_CAPACITY);
            }

            boolean changed = keyInputStack == null ? normalized != null : !keyInputStack.equals(normalized);
            if (!changed) {
                return;
            }

            keyInputStack = normalized;
            syncKeyMenuFromStack();
            onChange();
        }

        @Override
        public boolean isSupportedType(AEKeyType type) {
            return type != null && type != AEKeyType.items() && type != AEKeyType.fluids();
        }

        @Override
        public boolean isAllowedIn(int slot, AEKey what) {
            if (slot != KEY_INPUT_SLOT || !isAllowedMenuKey(what)) {
                return false;
            }
            return keyInputStack == null || keyInputStack.what() == null || keyInputStack.what().equals(what);
        }

        @Override
        public long insert(int slot, AEKey what, long amount, Actionable mode) {
            if (!isAllowedIn(slot, what) || amount <= 0L) {
                return 0L;
            }

            long stored = keyInputStack == null ? 0L : keyInputStack.amount();
            long inserted = Math.min(amount, Math.max(0L, KEY_INPUT_CAPACITY - stored));
            if (inserted <= 0L) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                keyInputStack = new GenericStack(what, stored + inserted);
                syncKeyMenuFromStack();
                onChange();
            }
            return inserted;
        }

        @Override
        public long extract(int slot, AEKey what, long amount, Actionable mode) {
            if (slot != KEY_OUTPUT_SLOT || what == null || amount <= 0L || keyOutputStack == null || !what.equals(keyOutputStack.what())) {
                return 0L;
            }

            long extracted = Math.min(amount, keyOutputStack.amount());
            if (mode == Actionable.MODULATE) {
                long remaining = keyOutputStack.amount() - extracted;
                keyOutputStack = remaining <= 0L ? null : new GenericStack(what, remaining);
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

            PatternPushState state = new PatternPushState(copyInputSlots(),
                    fluidInputTankA.getFluid().copy(),
                    fluidInputTankB.getFluid().copy(),
                    copyKeyStack(keyInputStack));
            if (!canAcceptPatternInput(state, what, amount)) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                applyPatternPushState(state);
                saveChanges();
                markForClientUpdate();
            }
            return amount;
        }

        @Override
        public Component getDescription() {
            return ModBlocks.DATA_RIPPER_REASSEMBLER.get().getName();
        }
    }

    public GenericStack getKeyInputStack() {
        return this.keyInputStack;
    }

    public GenericStack getKeyOutputStack() {
        return this.keyOutputStack;
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

            if (this == fluidInputTankA) {
                return !conflictsWithPairedTank(fluidInputTankB, stack);
            }
            if (this == fluidInputTankB) {
                return !conflictsWithPairedTank(fluidInputTankA, stack);
            }
            if (this == fluidOutputTankA) {
                return !conflictsWithPairedTank(fluidOutputTankB, stack);
            }
            if (this == fluidOutputTankB) {
                return !conflictsWithPairedTank(fluidOutputTankA, stack);
            }
            return true;
        }

        @Override
        protected void onContentsChanged() {
            syncMenuFluidsFromTanks();
            saveChanges();
            markForClientUpdate();
        }
    }

    private final class ReassemblerFluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 4;
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
            return tank < 2 && this.getTank(tank).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = fluidInputTankA.fill(resource, action);
            if (filled >= resource.getAmount()) {
                return filled;
            }

            FluidStack remaining = resource.copy();
            remaining.shrink(filled);
            return filled + fluidInputTankB.fill(remaining, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            FluidStack drained = fluidOutputTankA.drain(resource, action);
            if (!drained.isEmpty()) {
                return drained;
            }
            return fluidOutputTankB.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = fluidOutputTankA.drain(maxDrain, action);
            if (!drained.isEmpty()) {
                return drained;
            }
            return fluidOutputTankB.drain(maxDrain, action);
        }

        private FluidTank getTank(int tank) {
            return switch (tank) {
                case 0 -> fluidInputTankA;
                case 1 -> fluidInputTankB;
                case 2 -> fluidOutputTankA;
                case 3 -> fluidOutputTankB;
                default -> throw new IndexOutOfBoundsException("Invalid tank index: " + tank);
            };
        }
    }

    private static final class RecipeProcessingState {

        private final ItemStack[] itemSlots;
        private final FluidStack fluidInputA;
        private final FluidStack fluidInputB;
        private final FluidStack fluidOutputA;
        private final FluidStack fluidOutputB;
        private final GenericStack keyInput;
        private final GenericStack keyOutput;

        private RecipeProcessingState(ItemStack[] itemSlots, FluidStack fluidInputA, FluidStack fluidInputB,
                                      FluidStack fluidOutputA, FluidStack fluidOutputB,
                                      @Nullable GenericStack keyInput, @Nullable GenericStack keyOutput) {
            this.itemSlots = itemSlots;
            this.fluidInputA = fluidInputA;
            this.fluidInputB = fluidInputB;
            this.fluidOutputA = fluidOutputA;
            this.fluidOutputB = fluidOutputB;
            this.keyInput = keyInput;
            this.keyOutput = keyOutput;
        }
    }

    private static final class PatternPushState {

        private final ItemStack[] itemInputs;
        private FluidStack fluidInputA;
        private FluidStack fluidInputB;
        private GenericStack keyInput;

        private PatternPushState(ItemStack[] itemInputs, FluidStack fluidInputA, FluidStack fluidInputB,
                                 @Nullable GenericStack keyInput) {
            this.itemInputs = itemInputs;
            this.fluidInputA = fluidInputA;
            this.fluidInputB = fluidInputB;
            this.keyInput = keyInput;
        }
    }
}
