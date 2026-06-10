package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipeInput;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class DataRipperReassemblerBlockEntity extends AENetworkedPoweredBlockEntity
                                              implements InternalInventoryHost, IConfigurableObject, IUpgradeableObject, ICraftingMachine {

    public static final int ITEM_INPUT_START_SLOT = 0;
    public static final int ITEM_INPUT_SLOT_COUNT = 9;
    public static final int ITEM_OUTPUT_START_SLOT = 9;
    public static final int ITEM_OUTPUT_SLOT_COUNT = 3;
    public static final int STORAGE_SLOTS = 12;
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
    private static final String PROGRESS_TAG = "progress";
    private static final String MAX_PROGRESS_TAG = "max_progress";
    private static final String ACTIVE_RECIPE_TAG = "active_recipe";

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(ModBlocks.DATA_RIPPER_REASSEMBLER.get(), UPGRADE_SLOTS, this::onUpgradesChanged);
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, STORAGE_SLOTS);
    private final InternalInventory externalInput = createExternalInput();
    private final InternalInventory externalOutput = createExternalOutput();
    private final InternalInventory externalInventory = new CombinedInternalInventory(this.externalInput, this.externalOutput);

    private final FluidTank fluidInputTankA = new SyncFluidTank(FLUID_INPUT_CAPACITY);
    private final FluidTank fluidInputTankB = new SyncFluidTank(FLUID_INPUT_CAPACITY);
    private final FluidTank fluidOutputTankA = new SyncFluidTank(FLUID_OUTPUT_CAPACITY);
    private final FluidTank fluidOutputTankB = new SyncFluidTank(FLUID_OUTPUT_CAPACITY);
    private final GenericStackInv fluidMenuInventoryA = createFluidMenuInventory(this::syncTankAFromMenuFluid, FLUID_INPUT_CAPACITY, () -> this.fluidInputTankB.getFluid());
    private final GenericStackInv fluidMenuInventoryB = createFluidMenuInventory(this::syncTankBFromMenuFluid, FLUID_INPUT_CAPACITY, () -> this.fluidInputTankA.getFluid());
    private final GenericStackInv fluidOutputMenuInventoryA = createFluidMenuInventory(this::syncOutputTankAFromMenuFluid, FLUID_OUTPUT_CAPACITY, () -> this.fluidOutputTankB.getFluid());
    private final GenericStackInv fluidOutputMenuInventoryB = createFluidMenuInventory(this::syncOutputTankBFromMenuFluid, FLUID_OUTPUT_CAPACITY, () -> this.fluidOutputTankA.getFluid());
    private final GenericStackInv keyMenuInventory = createKeyMenuInventory();
    private final GenericStackInv keyOutputMenuInventory = createKeyOutputMenuInventory();
    private final IFluidHandler externalFluidHandler = new ReassemblerFluidHandler();
    private final MEStorage externalPatternInputStorage = new PatternInputStorage();
    private final ConfigManager configManager = new ConfigManager(this::onConfigChanged);
    private boolean syncingFluidMenu;
    private boolean syncingKeyMenu;
    private GenericStack keyInputStack;
    private GenericStack keyOutputStack;
    private final Set<Direction> outputSides = EnumSet.allOf(Direction.class);
    private int progress;
    private int maxProgress = MAX_PROGRESS;
    private ResourceLocation activeRecipeId;

    public DataRipperReassemblerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DATA_RIPPER_REASSEMBLER.get())
                .setExposedOnSides(getCableExposedSides(blockState))
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
        if (!isCableSideExposed(dir)) {
            return AECableType.NONE;
        }
        return AECableType.COVERED;
    }

    private boolean isCableSideExposed(Direction dir) {
        Direction front = this.getBlockState().getValue(DataRipperReassemblerBlock.FACING);
        return dir != Direction.UP && dir != front;
    }

    private static Set<Direction> getCableExposedSides(BlockState blockState) {
        Direction front = blockState.getValue(DataRipperReassemblerBlock.FACING);
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        sides.remove(front);
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

    public InternalInventory getExternalInventory() {
        return this.externalInventory;
    }

    public IFluidHandler getExternalFluidHandler() {
        return this.externalFluidHandler;
    }

    public MEStorage getExternalPatternInputStorage() {
        return this.externalPatternInputStorage;
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

    public int getProgress() {
        return this.progress;
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    public boolean isAutoExportEnabled() {
        return this.configManager.getSetting(Settings.AUTO_EXPORT) == YesNo.YES;
    }

    public Set<Direction> getOutputSides() {
        if (this.outputSides.isEmpty()) {
            return EnumSet.noneOf(Direction.class);
        }
        return EnumSet.copyOf(this.outputSides);
    }

    public void setOutputSideEnabled(Direction side, boolean enabled) {
        boolean changed = enabled ? this.outputSides.add(side) : this.outputSides.remove(side);
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
        ListTag sides = new ListTag();
        for (Direction side : this.outputSides) {
            sides.add(StringTag.valueOf(side.getName()));
        }
        data.put(OUTPUT_SIDES_TAG, sides);
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
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (ItemStack stack : this.storage) {
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
        if (!canAcceptItemOutputs(recipe)) {
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

        if (!consumeRecipeInputs(recipe)) {
            resetProcessingState();
            return;
        }

        insertRecipeOutputs(recipe);
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

    private boolean canAcceptItemOutputs(DataRipperReassemblerRecipe recipe) {
        ItemStack[] simulated = new ItemStack[ITEM_OUTPUT_SLOT_COUNT];
        for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT; i++) {
            simulated[i] = this.storage.getStackInSlot(ITEM_OUTPUT_START_SLOT + i).copy();
        }

        for (ItemStack output : recipe.getItemOutputs()) {
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

        for (GenericStack requiredFluid : recipe.getFluidInputs()) {
            if (!(requiredFluid.what() instanceof AEFluidKey requiredKeyFluid)) {
                return false;
            }

            int amount = (int) Math.min(Integer.MAX_VALUE, requiredFluid.amount());
            int remaining = amount;

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

    private void insertRecipeOutputs(DataRipperReassemblerRecipe recipe) {
        for (ItemStack output : recipe.getItemOutputs()) {
            if (output.isEmpty()) {
                continue;
            }

            ItemStack remaining = output.copy();
            for (int i = 0; i < ITEM_OUTPUT_SLOT_COUNT && !remaining.isEmpty(); i++) {
                remaining = insertIntoOutputSlot(null, i, remaining, true);
            }
        }

        for (GenericStack fluidOutput : recipe.getFluidOutputs()) {
            if (!(fluidOutput.what() instanceof AEFluidKey fluidKey) || fluidOutput.amount() <= 0) {
                continue;
            }
            insertFluidOutput(fluidKey, fluidOutput.amount());
        }

        GenericStack keyOutput = recipe.getKeyOutput();
        if (keyOutput != null && keyOutput.what() != null && keyOutput.amount() > 0) {
            insertKeyOutput(keyOutput);
        }
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
                simulatedA = fluidKey.toStack(amount);
                continue;
            }
            if (simulatedB.isEmpty()) {
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

        return this.keyOutputStack.amount() + keyOutput.amount() <= KEY_OUTPUT_CAPACITY;
    }

    private void insertKeyOutput(GenericStack stack) {
        if (this.keyOutputStack == null || this.keyOutputStack.what() == null || this.keyOutputStack.amount() <= 0) {
            this.keyOutputStack = clampKeyStack(stack, KEY_OUTPUT_CAPACITY);
        } else if (this.keyOutputStack.what().equals(stack.what())) {
            this.keyOutputStack = clampKeyStack(
                    new GenericStack(stack.what(), this.keyOutputStack.amount() + stack.amount()),
                    KEY_OUTPUT_CAPACITY);
        }
        syncKeyMenuFromStack();
    }

    private void insertFluidOutput(AEFluidKey fluidKey, long amountLong) {
        int amount = (int) Math.min(Integer.MAX_VALUE, amountLong);
        if (matchesFluidKey(this.fluidOutputTankA.getFluid(), fluidKey) || this.fluidOutputTankA.isEmpty()) {
            this.fluidOutputTankA.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        if (matchesFluidKey(this.fluidOutputTankB.getFluid(), fluidKey) || this.fluidOutputTankB.isEmpty()) {
            this.fluidOutputTankB.fill(fluidKey.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
        }
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

        List<IItemHandler> itemHandlers = getAdjacentItemHandlers();
        List<IFluidHandler> fluidHandlers = getAdjacentFluidHandlers();

        boolean changed = exportItemOutputs(itemHandlers);
        changed |= exportFluidOutput(this.fluidOutputTankA, fluidHandlers);
        changed |= exportFluidOutput(this.fluidOutputTankB, fluidHandlers);
        changed |= exportKeyOutput(itemHandlers);

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

    private boolean exportKeyOutput(List<IItemHandler> adjacentHandlers) {
        if (this.keyOutputStack == null || this.keyOutputStack.amount() <= 0 || this.keyOutputStack.what() == null) {
            return false;
        }

        ItemStack wrapped = GenericStack.wrapInItemStack(this.keyOutputStack.what(), this.keyOutputStack.amount());
        ItemStack remaining = insertIntoAdjacentHandlers(wrapped, adjacentHandlers);
        if (ItemStack.matches(wrapped, remaining)) {
            return false;
        }

        GenericStack remainingStack = GenericStack.fromItemStack(remaining);
        if (remainingStack == null || remainingStack.amount() <= 0 || remainingStack.what() == null) {
            this.keyOutputStack = null;
        } else {
            this.keyOutputStack = clampKeyStack(remainingStack, KEY_OUTPUT_CAPACITY);
        }
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

    private List<IItemHandler> getAdjacentItemHandlers() {
        if (this.level == null) {
            return List.of();
        }

        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : getAutoExportSides()) {
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

    private List<IFluidHandler> getAdjacentFluidHandlers() {
        if (this.level == null) {
            return List.of();
        }

        List<IFluidHandler> handlers = new ArrayList<>();
        for (Direction direction : getAutoExportSides()) {
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

    private Set<Direction> getAutoExportSides() {
        return this.outputSides;
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
            if (wrapped != null && isAllowedMenuKey(wrapped.what())) {
                return canAcceptWrappedKeyInput(state, wrapped, amount);
            }
            return canAcceptItemInput(state, itemKey, amount);
        }
        if (key instanceof AEFluidKey fluidKey) {
            return canAcceptFluidInput(state, fluidKey, amount);
        }
        return canAcceptGenericKeyInput(state, key, amount);
    }

    private boolean canAcceptWrappedKeyInput(PatternPushState state, GenericStack wrapped, long amount) {
        if (wrapped.amount() <= 0) {
            return false;
        }
        if (amount > Long.MAX_VALUE / wrapped.amount()) {
            return false;
        }

        return canAcceptGenericKeyInput(state, wrapped.what(), wrapped.amount() * amount);
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

    private static final class SlotFilter implements IAEItemFilter {

        private final java.util.function.Predicate<ItemStack> insertPredicate;
        private final boolean allowExtract;

        private SlotFilter(java.util.function.Predicate<ItemStack> insertPredicate, boolean allowExtract) {
            this.insertPredicate = insertPredicate;
            this.allowExtract = allowExtract;
        }

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
        var inv = new GenericStackInv(java.util.Set.of(AEKeyType.fluids()), syncAction, GenericStackInv.Mode.STORAGE, 1) {

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

    private GenericStackInv createFluidMenuInventory(Runnable syncAction) {
        return createFluidMenuInventory(syncAction, FLUID_INPUT_CAPACITY, () -> FluidStack.EMPTY);
    }

    private GenericStackInv createFluidOutputMenuInventory(Runnable syncAction) {
        return createFluidMenuInventory(syncAction, FLUID_OUTPUT_CAPACITY, () -> FluidStack.EMPTY);
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
