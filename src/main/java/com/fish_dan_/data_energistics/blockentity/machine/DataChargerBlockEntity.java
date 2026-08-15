package com.fish_dan_.data_energistics.blockentity.machine;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.block.DataChargerBlock;
import com.fish_dan_.data_energistics.common.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipe;
import com.fish_dan_.data_energistics.recipe.charger.DataChargerRecipeInput;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class DataChargerBlockEntity extends AENetworkedPoweredBlockEntity implements InternalInventoryHost {

    public static final int REGULAR_SLOT_COUNT = 1;
    public static final int EXTENDED_SLOT_COUNT = 4;
    public static final double REGULAR_POWER_CAPACITY = 1600.0D;
    public static final double EXTENDED_POWER_CAPACITY = REGULAR_POWER_CAPACITY * 4.0D;
    public static final long REGULAR_DATA_FLOW_CAPACITY = 1600L;
    public static final long EXTENDED_DATA_FLOW_CAPACITY = REGULAR_DATA_FLOW_CAPACITY * 4L;
    private static final double REGULAR_POWER_REFILL_PER_TICK = 800.0D;
    private static final double EXTENDED_POWER_REFILL_PER_TICK = REGULAR_POWER_REFILL_PER_TICK * 4.0D;
    private static final double AE_CHARGER_RECIPE_POWER = 1600.0D;
    private static final long REGULAR_DATA_FLOW_REFILL_PER_TICK = 160L;
    private static final long EXTENDED_DATA_FLOW_REFILL_PER_TICK = REGULAR_DATA_FLOW_REFILL_PER_TICK * 4L;
    private static final int RECIPE_LOOKUP_CACHE_LIMIT = 32;
    private static final String STORAGE_TAG = "storage";
    private static final String DATA_FLOW_TAG = "data_flow";

    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, EXTENDED_SLOT_COUNT, 1);
    private final IItemHandler regularExternalItemHandler = this.storage.getSlotInv(0).toItemHandler();
    private final IItemHandler extendedExternalItemHandler = this.storage.toItemHandler();
    private final LinkedHashMap<RecipeLookupKey, RecipeLookup> recipeLookupCache = new LinkedHashMap<>(RECIPE_LOOKUP_CACHE_LIMIT, 0.75F, true);
    private long recipeLookupCacheEpoch = Long.MIN_VALUE;
    private long storedDataFlow;
    private boolean working;

    public DataChargerBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(), pos, state);
        var connectableSides = getGridConnectableSides(BlockOrientation.get(state));
        this.getMainNode()
                .setVisualRepresentation(isExtended() ? DEBlocks.EXTENDED_DATA_CHARGER.get() : DEBlocks.DATA_CHARGER.get())
                .setExposedOnSides(connectableSides)
                .setIdlePowerUsage(0.0D);
        this.storage.setFilter(new ChargerItemFilter());
        this.setInternalMaxPower(getPowerCapacity());
        this.setPowerSides(connectableSides);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return getGridConnectableSides(getOrientation()).contains(dir) ? AECableType.COVERED : AECableType.NONE;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        var connectableSides = getGridConnectableSides(orientation);
        this.getMainNode().setExposedOnSides(connectableSides);
        this.setPowerSides(connectableSides);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.storage;
    }

    public IItemHandler getExternalItemHandler() {
        return isExtended() ? this.extendedExternalItemHandler : this.regularExternalItemHandler;
    }

    public ItemStack getDisplayStack(int slot) {
        if (slot < 0 || slot >= getActiveSlotCount()) {
            return ItemStack.EMPTY;
        }
        return this.storage.getStackInSlot(slot);
    }

    public int getActiveSlotCount() {
        return isExtended() ? EXTENDED_SLOT_COUNT : REGULAR_SLOT_COUNT;
    }

    public boolean isExtended() {
        return DataChargerBlock.isExtended(this.getBlockState());
    }

    public double getPowerCapacity() {
        return isExtended() ? EXTENDED_POWER_CAPACITY : REGULAR_POWER_CAPACITY;
    }

    public long getDataFlowCapacity() {
        return isExtended() ? EXTENDED_DATA_FLOW_CAPACITY : REGULAR_DATA_FLOW_CAPACITY;
    }

    public long getStoredDataFlow() {
        return Math.min(this.storedDataFlow, getDataFlowCapacity());
    }

    public boolean isWorking() {
        return this.working;
    }

    public boolean isOnline() {
        return this.getMainNode().isOnline();
    }

    public boolean hasChargeablePowerItem() {
        int activeSlotCount = getActiveSlotCount();
        for (int slot = 0; slot < activeSlotCount; slot++) {
            if (supportsAePower(this.storage.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasChargeableDataFlowItem() {
        int activeSlotCount = getActiveSlotCount();
        for (int slot = 0; slot < activeSlotCount; slot++) {
            if (supportsDataFlow(this.storage.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    public boolean tryInsertDisplayStack(ItemStack source) {
        if (!canAcceptStack(source)) {
            return false;
        }
        int activeSlotCount = getActiveSlotCount();
        for (int slot = 0; slot < activeSlotCount; slot++) {
            if (this.storage.getStackInSlot(slot).isEmpty()) {
                this.storage.setItemDirect(slot, source.split(1));
                saveChanges();
                markForClientUpdate();
                return true;
            }
        }
        return false;
    }

    public ItemStack extractFirstDisplayStack() {
        int activeSlotCount = getActiveSlotCount();
        for (int slot = activeSlotCount - 1; slot >= 0; slot--) {
            ItemStack stack = this.storage.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                this.storage.setItemDirect(slot, ItemStack.EMPTY);
                saveChanges();
                markForClientUpdate();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < this.storage.size(); slot++) {
            ItemStack stack = this.storage.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
                this.storage.setItemDirect(slot, ItemStack.EMPTY);
            }
        }
        saveChanges();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        clampCapacities();
        refillPowerBuffer();
        refillDataFlowBuffer();

        boolean wasWorking = this.working;
        this.working = false;
        boolean changed = false;
        int activeSlotCount = getActiveSlotCount();
        for (int slot = 0; slot < activeSlotCount; slot++) {
            ItemStack stack = this.storage.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (processRecipe(slot, stack)) {
                changed = true;
                continue;
            }
            changed |= chargeAePower(stack);
            changed |= chargeDataFlow(stack);
        }

        if (changed) {
            saveChanges();
        }
        if (changed || this.working != wasWorking) {
            markForClientUpdate();
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.storage.readFromNBT(data, STORAGE_TAG, registries);
        this.storedDataFlow = Math.max(0L, data.getLong(DATA_FLOW_TAG));
        clampCapacities();
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        this.storage.writeToNBT(data, STORAGE_TAG, registries);
        data.putLong(DATA_FLOW_TAG, getStoredDataFlow());
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.working);
        data.writeVarLong(getStoredDataFlow());
        data.writeVarLong(getDataFlowCapacity());
        data.writeVarInt(getActiveSlotCount());
        for (int slot = 0; slot < this.storage.size(); slot++) {
            ItemStack stack = slot < getActiveSlotCount() ? this.storage.getStackInSlot(slot) : ItemStack.EMPTY;
            data.writeBoolean(!stack.isEmpty());
            if (!stack.isEmpty()) {
                ItemStack.STREAM_CODEC.encode(data, stack);
            }
        }
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean working = data.readBoolean();
        long storedDataFlow = data.readVarLong();
        data.readVarLong();
        int activeSlotCount = Math.min(data.readVarInt(), EXTENDED_SLOT_COUNT);
        if (this.working != working) {
            this.working = working;
            changed = true;
        }
        if (this.storedDataFlow != storedDataFlow) {
            this.storedDataFlow = storedDataFlow;
            changed = true;
        }
        for (int slot = 0; slot < this.storage.size(); slot++) {
            ItemStack syncedStack = data.readBoolean() ? ItemStack.STREAM_CODEC.decode(data) : ItemStack.EMPTY;
            if (slot >= activeSlotCount) {
                syncedStack = ItemStack.EMPTY;
            }
            if (!ItemStack.matches(this.storage.getStackInSlot(slot), syncedStack)) {
                this.storage.setItemDirect(slot, syncedStack);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        saveChangedInventory(inv);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.storage.clear();
        this.storedDataFlow = 0L;
    }

    public static boolean canChargeStack(ItemStack stack) {
        return !stack.isEmpty() && (supportsAePower(stack) || supportsDataFlow(stack));
    }

    public boolean canAcceptStack(ItemStack stack) {
        if (canChargeStack(stack)) {
            return true;
        }
        RecipeLookup lookup = findRecipeLookup(stack);
        return lookup != null && lookup.hasRecipe();
    }

    public static boolean supportsAePower(ItemStack stack) {
        return Platform.isChargeable(stack) && stack.getItem() instanceof IAEItemPowerStorage;
    }

    public static boolean supportsDataFlow(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof IBasicCellItem basicCellItem && basicCellItem.getKeyType() == DigitalizationKeyType.TYPE && basicCellItem.getBytes(stack) > 0 && !basicCellItem.isBlackListed(stack, DataFlowKey.of())) {
            return true;
        }

        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && (cellInventory.isPreferredStorageFor(DataFlowKey.of(), IActionSource.empty()) || cellInventory.insert(DataFlowKey.of(), 1L, Actionable.SIMULATE, IActionSource.empty()) > 0L || cellInventory.getAvailableStacks().get(DataFlowKey.of()) > 0L);
    }

    public static boolean canChargeAePower(ItemStack stack) {
        return supportsAePower(stack) && stack.getItem() instanceof IAEItemPowerStorage powerStorage && powerStorage.getAECurrentPower(stack) + 1.0E-4D < powerStorage.getAEMaxPower(stack);
    }

    public static boolean canChargeDataFlow(ItemStack stack) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        return cellInventory != null && cellInventory.insert(DataFlowKey.of(), 1L, Actionable.SIMULATE, IActionSource.empty()) > 0L;
    }

    private boolean chargeAePower(ItemStack stack) {
        if (!canChargeAePower(stack) || !(stack.getItem() instanceof IAEItemPowerStorage powerStorage)) {
            return false;
        }

        double currentPower = powerStorage.getAECurrentPower(stack);
        double missingPower = Math.max(0.0D, powerStorage.getAEMaxPower(stack) - currentPower);
        double chargeRate = Math.min(powerStorage.getChargeRate(stack), missingPower);
        double available = Math.min(chargeRate, this.getInternalCurrentPower());
        if (available <= 1.0E-4D) {
            return false;
        }

        double overflow = powerStorage.injectAEPower(stack, available, Actionable.MODULATE);
        double accepted = Math.max(0.0D, available - overflow);
        if (accepted <= 1.0E-4D) {
            return false;
        }

        this.extractAEPower(accepted, Actionable.MODULATE, PowerMultiplier.ONE);
        this.working = true;
        return true;
    }

    private boolean chargeDataFlow(ItemStack stack) {
        if (this.storedDataFlow <= 0) {
            return false;
        }

        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return false;
        }

        long offered = Math.min(this.storedDataFlow, getDataFlowRefillPerTick());
        long accepted = cellInventory.insert(DataFlowKey.of(), offered, Actionable.MODULATE, IActionSource.ofMachine(this));
        if (accepted <= 0L) {
            return false;
        }

        this.storedDataFlow = Math.max(0L, this.storedDataFlow - accepted);
        this.working = true;
        return true;
    }

    private boolean processRecipe(int slot, ItemStack stack) {
        RecipeLookup lookup = findRecipeLookup(stack);
        if (lookup == null) {
            return false;
        }

        RecipeHolder<DataChargerRecipe> dataChargerRecipe = resolveDataChargerRecipe(this.level, lookup.dataChargerRecipeId());
        if (dataChargerRecipe != null && processDataChargerRecipe(slot, stack, dataChargerRecipe.value())) {
            return true;
        }

        RecipeHolder<ChargerRecipe> aeChargerRecipe = resolveAeChargerRecipe(this.level, lookup.aeChargerRecipeId());
        return aeChargerRecipe != null && processAeChargerRecipe(slot, stack, aeChargerRecipe.value());
    }

    private boolean processDataChargerRecipe(int slot, ItemStack stack, DataChargerRecipe recipe) {
        ItemStack result = recipe.getResult();
        if (result.isEmpty() || !canReplaceWithResult(stack, result)) {
            return false;
        }
        if (this.getInternalCurrentPower() + 1.0E-4D < recipe.getPower() || this.storedDataFlow < recipe.getDataFlow()) {
            return false;
        }

        this.extractAEPower(recipe.getPower(), Actionable.MODULATE, PowerMultiplier.ONE);
        this.storedDataFlow = Math.max(0L, this.storedDataFlow - recipe.getDataFlow());
        this.storage.setItemDirect(slot, result.copy());
        this.working = true;
        return true;
    }

    private boolean processAeChargerRecipe(int slot, ItemStack stack, ChargerRecipe recipe) {
        ItemStack result = recipe.getResultItem();
        if (result.isEmpty() || !canReplaceWithResult(stack, result)) {
            return false;
        }
        if (this.getInternalCurrentPower() + 1.0E-4D < AE_CHARGER_RECIPE_POWER) {
            return false;
        }

        this.extractAEPower(AE_CHARGER_RECIPE_POWER, Actionable.MODULATE, PowerMultiplier.ONE);
        this.storage.setItemDirect(slot, result.copy());
        this.working = true;
        return true;
    }

    private @Nullable RecipeLookup findRecipeLookup(ItemStack stack) {
        Level currentLevel = this.level;
        if (currentLevel == null || stack.isEmpty()) {
            return null;
        }

        long reloadEpoch = RecipeReloadEpoch.current();
        if (this.recipeLookupCacheEpoch != reloadEpoch) {
            this.recipeLookupCache.clear();
            this.recipeLookupCacheEpoch = reloadEpoch;
        }

        RecipeLookupKey key = new RecipeLookupKey(reloadEpoch, AEItemKey.of(stack));
        RecipeLookup cached = this.recipeLookupCache.get(key);
        if (cached != null) {
            return cached;
        }

        RecipeLookup computed = computeRecipeLookup(currentLevel, stack);
        this.recipeLookupCache.put(key, computed);
        if (this.recipeLookupCache.size() > RECIPE_LOOKUP_CACHE_LIMIT) {
            var eldest = this.recipeLookupCache.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
        return computed;
    }

    private static RecipeLookup computeRecipeLookup(Level level, ItemStack stack) {
        ResourceLocation dataChargerRecipeId = null;
        DataChargerRecipeInput input = new DataChargerRecipeInput(stack);
        for (RecipeHolder<DataChargerRecipe> holder : level.getRecipeManager().getAllRecipesFor(DERecipes.DATA_CHARGER_TYPE.get())) {
            if (holder.value().matches(input, level)) {
                dataChargerRecipeId = holder.id();
                break;
            }
        }

        ResourceLocation aeChargerRecipeId = null;
        for (RecipeHolder<ChargerRecipe> holder : level.getRecipeManager().getAllRecipesFor(AERecipeTypes.CHARGER)) {
            if (holder.value().getIngredient().test(stack)) {
                aeChargerRecipeId = holder.id();
                break;
            }
        }
        return new RecipeLookup(dataChargerRecipeId, aeChargerRecipeId);
    }

    private static @Nullable RecipeHolder<DataChargerRecipe> resolveDataChargerRecipe(
                                                                                      Level level, @Nullable ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof DataChargerRecipe recipe)) {
            return null;
        }
        return new RecipeHolder<>(holder.id(), recipe);
    }

    private static @Nullable RecipeHolder<ChargerRecipe> resolveAeChargerRecipe(
                                                                                Level level, @Nullable ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof ChargerRecipe recipe)) {
            return null;
        }
        return new RecipeHolder<>(holder.id(), recipe);
    }

    private static boolean canReplaceWithResult(ItemStack input, ItemStack result) {
        if (input.getCount() != 1) {
            return false;
        }
        return !ItemStack.isSameItemSameComponents(input, result);
    }

    private void refillPowerBuffer() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null) {
            return;
        }

        double missing = this.getInternalMaxPower() - this.getInternalCurrentPower();
        if (missing <= 1.0E-4D) {
            return;
        }

        double requested = Math.min(getPowerRefillPerTick(), missing);
        double extracted = node.getGrid().getEnergyService().extractAEPower(requested, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted > 1.0E-4D) {
            this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
        }
    }

    private void refillDataFlowBuffer() {
        IGridNode node = this.getMainNode().getNode();
        if (node == null || node.getGrid() == null || !node.isActive()) {
            return;
        }

        long missing = getDataFlowCapacity() - getStoredDataFlow();
        if (missing <= 0L) {
            return;
        }

        var storageService = node.getGrid().getStorageService();
        if (storageService == null || storageService.getInventory() == null) {
            return;
        }

        long requested = Math.min(getDataFlowRefillPerTick(), missing);
        long extracted = storageService.getInventory().extract(DataFlowKey.of(), requested, Actionable.MODULATE, IActionSource.ofMachine(this));
        if (extracted > 0L) {
            this.storedDataFlow += extracted;
            setChanged();
        }
    }

    private void clampCapacities() {
        this.setInternalMaxPower(getPowerCapacity());
        if (this.getInternalCurrentPower() > this.getInternalMaxPower()) {
            this.extractAEPower(this.getInternalCurrentPower() - this.getInternalMaxPower(), Actionable.MODULATE, PowerMultiplier.ONE);
        }
        this.storedDataFlow = Math.min(Math.max(0L, this.storedDataFlow), getDataFlowCapacity());
    }

    private double getPowerRefillPerTick() {
        return isExtended() ? EXTENDED_POWER_REFILL_PER_TICK : REGULAR_POWER_REFILL_PER_TICK;
    }

    private long getDataFlowRefillPerTick() {
        return isExtended() ? EXTENDED_DATA_FLOW_REFILL_PER_TICK : REGULAR_DATA_FLOW_REFILL_PER_TICK;
    }

    private record RecipeLookupKey(long reloadEpoch, AEItemKey item) {}

    private record RecipeLookup(@Nullable ResourceLocation dataChargerRecipeId,
                                @Nullable ResourceLocation aeChargerRecipeId) {

        private boolean hasRecipe() {
            return this.dataChargerRecipeId != null || this.aeChargerRecipeId != null;
        }
    }

    private final class ChargerItemFilter implements IAEItemFilter {

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return canAcceptStack(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return false;
            }
            RecipeLookup lookup = findRecipeLookup(stack);
            if (lookup != null && lookup.hasRecipe()) {
                return false;
            }
            return !canChargeAePower(stack) && !canChargeDataFlow(stack);
        }
    }
}
