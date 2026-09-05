package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchBridge;
import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.patternprovider.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.patternprovider.PatternProviderBatching;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.reusable.AdaptiveReusableCraftingState;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.TargetedCountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.BoundPatternInputProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.integration.ModFlags;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AdaptivePatternProviderLogic extends PatternProviderLogic
                                          implements PatternProviderLogicAccessor, TargetedCountedCraftingProvider, BoundPatternInputProvider, ReusableCraftingProviderAdapter {

    private static final String RESONATING_PATTERN_DETAILS_CLASS = "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails";
    private static final String ADVANCED_AE_PATTERN_DETAILS_INTERFACE = "net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails";
    private static final String AE2CS_GENERIC_STACK_INV_HELPER_CLASS = "io.github.lounode.ae2cs.api.util.GenericStackInvHelper";
    private static final String CREATE_MECHANICAL_CRAFTER_BE_CLASS = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity";
    private static final String CREATE_RECIPE_GRID_HANDLER_CLASS = "com.simibubi.create.content.kinetics.crafter.RecipeGridHandler";
    private static final String CREATE_MECHANICAL_CRAFTER_BLOCK_CLASS = "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock";
    private static final int METEORITE_ENERGY_PER_WORK = 50;
    private static final double METEORITE_ENERGY_TOLERANCE = 1.0e-9;
    private static final int METEORITE_MAX_WORKS_PER_ROUND = 8;
    private static final int EXPANDED_RETURN_SLOTS = 18;
    private static final String NBT_CRAFTED_CONTENTS = "adaptive_crafted_contents";
    private static final String NBT_ADVANCED_SEND_LIST = "adaptive_advanced_send_list";
    private static final String NBT_ADVANCED_SEND_DIRECTION = "adaptive_advanced_send_direction";
    private static final String NBT_ADVANCED_DIRECTION_MAP = "adaptive_advanced_direction_map";
    private static final String NBT_PATTERN_SLOT_OVERFLOW = "adaptive_pattern_slot_overflow";
    private static final String NBT_RECONCILED_PATTERN_SLOT_COUNT = "adaptive_reconciled_pattern_slot_count";
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ConcurrentHashMap<Class<?>, Optional<SparsePatternAccess>> SPARSE_PATTERN_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<ResolvedTargetAccess>> RESOLVED_TARGET_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<DirectionalPatternAccess>> DIRECTIONAL_PATTERN_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Optional<MechanicalRecipeAccess>> MECHANICAL_RECIPE_ACCESS_CACHE = new ConcurrentHashMap<>();
    private static final Optional<AppliedCreateAccess> APPLIED_CREATE_ACCESS = findAppliedCreateAccess();
    private static final Object APPLIED_CREATE_RECIPE_INDEX_LOCK = new Object();
    private static volatile AppliedCreateRecipeIndex appliedCreateRecipeIndex = AppliedCreateRecipeIndex.empty();

    private final PatternProviderLogicHost host;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private int localRoundRobinIndex;
    private final Object2LongOpenHashMap<AEKey> craftedContents = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> advancedDirectionalSendList = new Object2LongOpenHashMap<>();
    private final HashMap<AEKey, Direction> advancedDirectionalMap = new HashMap<>();
    private final List<ItemStack> patternSlotOverflow = new ArrayList<>();
    private final Set<AEKey> trackedCrafts = new HashSet<>();
    private final HashSet<AEKey> outputCache = new HashSet<>();
    private @Nullable IStackWatcher craftingWatcher;
    private @Nullable Direction advancedSendDirection;
    private int worksInRound;
    private AdaptiveReusableCraftingState reusableCrafting = new AdaptiveReusableCraftingState();
    private @Nullable CompoundTag reusableItemHandoff;
    private final Int2ObjectLinkedOpenHashMap<NativePatternSlot> nativePatternSlots = new Int2ObjectLinkedOpenHashMap<>();
    private long nativeRecipeEpoch = RecipeReloadEpoch.current();
    private final @Nullable MethodHandle ae2csAdjacentMeStorageMethod;
    private boolean dataEnergistics$dispatchPulsePending;
    private int reconciledPatternSlotCount = -1;
    private int suppressedPatternInventoryCallbacks;
    private boolean patternInventoryChangedWhileCallbacksSuppressed;

    public AdaptivePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        this.host = host;
        this.mainNode = mainNode
                .addService(IGridTickable.class, new Ticker())
                .addService(ICraftingWatcherNode.class, new AdaptiveCraftingWatcherNode());
        this.actionSource = new MachineSource(mainNode::getNode);
        this.ae2csAdjacentMeStorageMethod = findAe2CsAdjacentMeStorageMethod();
        installExpandedReturnInventory();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.suppressedPatternInventoryCallbacks > 0) {
            this.patternInventoryChangedWhileCallbacksSuppressed = true;
            return;
        }

        super.onChangeInventory(inv, slot);
        refreshAdaptivePatternTracking();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        if (this.suppressedPatternInventoryCallbacks > 0) {
            return;
        }

        super.saveChangedInventory(inv);
    }

    @Override
    public void updatePatterns() {
        rebuildPatternsForConfiguredSlots();
        refreshAdaptivePatternTracking();
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);

        ListTag craftedContentsTag = new ListTag();
        for (var entry : this.craftedContents.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                craftedContentsTag.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(NBT_CRAFTED_CONTENTS, craftedContentsTag);

        ListTag sendListTag = new ListTag();
        for (var entry : this.advancedDirectionalSendList.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                sendListTag.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(NBT_ADVANCED_SEND_LIST, sendListTag);

        if (this.advancedSendDirection != null) {
            tag.putByte(NBT_ADVANCED_SEND_DIRECTION, (byte) this.advancedSendDirection.get3DDataValue());
        }

        ListTag directionMapTag = new ListTag();
        for (Map.Entry<AEKey, Direction> entry : this.advancedDirectionalMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            CompoundTag directionTag = new CompoundTag();
            directionTag.put("aekey", entry.getKey().toTagGeneric(registries));
            Direction direction = entry.getValue();
            directionTag.putByte("dir", direction == null ? (byte) -1 : (byte) direction.get3DDataValue());
            directionMapTag.add(directionTag);
        }
        tag.put(NBT_ADVANCED_DIRECTION_MAP, directionMapTag);

        writePatternSlotOverflowToNBT(tag, registries);
        tag.put(AdaptiveReusableCraftingState.NBT_KEY, this.reusableCrafting.writeToTag(registries));
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        AdaptiveReusableCraftingState restoredReusable;
        if (tag.contains(AdaptiveReusableCraftingState.NBT_KEY)) {
            if (!(tag.get(AdaptiveReusableCraftingState.NBT_KEY) instanceof CompoundTag reusableTag)) {
                throw new IllegalArgumentException("Adaptive reusable state must be a compound");
            }
            restoredReusable = AdaptiveReusableCraftingState.readFromTag(reusableTag, registries);
        } else {
            restoredReusable = new AdaptiveReusableCraftingState();
        }
        super.readFromNBT(tag, registries);

        this.craftedContents.clear();
        this.advancedDirectionalSendList.clear();
        this.advancedDirectionalMap.clear();
        this.advancedSendDirection = null;

        ListTag craftedContentsTag = tag.getList(NBT_CRAFTED_CONTENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < craftedContentsTag.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, craftedContentsTag.getCompound(i));
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                this.craftedContents.addTo(stack.what(), stack.amount());
            }
        }

        ListTag sendListTag = tag.getList(NBT_ADVANCED_SEND_LIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < sendListTag.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, sendListTag.getCompound(i));
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                this.advancedDirectionalSendList.addTo(stack.what(), stack.amount());
            }
        }

        if (tag.contains(NBT_ADVANCED_SEND_DIRECTION)) {
            this.advancedSendDirection = Direction.from3DDataValue(tag.getByte(NBT_ADVANCED_SEND_DIRECTION));
        }

        ListTag directionMapTag = tag.getList(NBT_ADVANCED_DIRECTION_MAP, Tag.TAG_COMPOUND);
        for (int i = 0; i < directionMapTag.size(); i++) {
            CompoundTag directionTag = directionMapTag.getCompound(i);
            AEKey key = AEKey.fromTagGeneric(registries, directionTag.getCompound("aekey"));
            if (key == null) {
                continue;
            }

            byte rawDirection = directionTag.getByte("dir");
            Direction direction = rawDirection == -1 ? null : Direction.from3DDataValue(rawDirection);
            this.advancedDirectionalMap.put(key, direction);
        }

        readPatternSlotOverflowFromNBT(tag, registries);
        this.reusableCrafting = restoredReusable;
        this.reusableItemHandoff = null;
    }

    /** Dismantled physical items carry escrow independently from copyable MemoryCard settings. */
    public void exportReusableItem(DataComponentMap.Builder builder, HolderLookup.Provider registries) {
        CompoundTag handoff = this.reusableItemHandoff;
        if (handoff == null) {
            if (this.reusableCrafting.handoffPrepared()) {
                throw new IllegalStateException("Persisted adaptive item handoff is unresolved; refusing a second asset copy");
            }
            handoff = this.reusableCrafting.prepareItemHandoff(registries);
            this.reusableItemHandoff = handoff;
            this.host.saveChanges();
        }
        CustomData previous = builder.build().get(DataComponents.CUSTOM_DATA);
        CompoundTag custom = previous == null ? new CompoundTag() : previous.copyTag();
        custom.put(AdaptiveReusableCraftingState.NBT_KEY, handoff.copy());
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    public void importReusableItem(DataComponentMap components, HolderLookup.Provider registries) {
        CustomData custom = components.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(AdaptiveReusableCraftingState.NBT_KEY)) {
            return;
        }
        CompoundTag source = custom.copyTag();
        if (!(source.get(AdaptiveReusableCraftingState.NBT_KEY) instanceof CompoundTag payload)) {
            throw new IllegalArgumentException("Adaptive reusable item payload must be a compound");
        }
        AdaptiveReusableCraftingState restored = AdaptiveReusableCraftingState.readFromTag(payload, registries);
        if (this.reusableCrafting.hasResidents()) {
            throw new IllegalStateException("Cannot replace live adaptive reusable ownership during item placement");
        }
        this.reusableCrafting = restored;
        this.reusableItemHandoff = null;
        onHostStateChanged();
        this.host.saveChanges();
    }

    /**
     * Reconciles the backing pattern inventory with the host's current visible slot count.
     *
     * @return whether the visible boundary or stored pattern state changed
     */
    public boolean reconcileConfiguredPatternSlots() {
        return reconcileConfiguredPatternSlots(false);
    }

    /**
     * Runs an imported pattern update without publishing every intermediate inventory state.
     */
    public boolean runWithPatternInventoryCallbacksSuppressed(Runnable action) {
        boolean outermostSuppression = this.suppressedPatternInventoryCallbacks == 0;
        if (outermostSuppression) {
            this.patternInventoryChangedWhileCallbacksSuppressed = false;
        }

        boolean changed = false;
        this.suppressedPatternInventoryCallbacks++;
        try {
            action.run();
        } finally {
            this.suppressedPatternInventoryCallbacks--;
            if (outermostSuppression) {
                changed = this.patternInventoryChangedWhileCallbacksSuppressed;
                this.patternInventoryChangedWhileCallbacksSuppressed = false;
            }
        }
        return changed;
    }

    /**
     * Reconciles an imported pattern inventory even if its configured slot count did not change.
     *
     * @return whether the visible boundary or stored pattern state changed
     */
    public boolean reconcileConfiguredPatternSlotsAfterSettingsImport() {
        return reconcileConfiguredPatternSlots(true);
    }

    /**
     * Gives every persisted overflow pattern to the supplied recovery path in queue order.
     * A queue entry is removed only after that path returns normally.
     *
     * @return whether an overflow stack was recovered
     */
    public boolean returnPatternSlotOverflow(Consumer<ItemStack> recoveryPath) {
        boolean recovered = false;
        while (!this.patternSlotOverflow.isEmpty()) {
            ItemStack stack = this.patternSlotOverflow.getFirst().copy();
            recoveryPath.accept(stack);
            this.patternSlotOverflow.removeFirst();
            this.host.saveChanges();
            recovered = true;
        }
        return recovered;
    }

    private boolean reconcileConfiguredPatternSlots(boolean force) {
        int configuredSlotCount = getConfiguredPatternSlotCount();
        if (!force && configuredSlotCount == this.reconciledPatternSlotCount) {
            return false;
        }

        List<ItemStack> plannedInventory = copyPatternInventory();
        List<ItemStack> plannedOverflow = copyPatternStacks(this.patternSlotOverflow);
        if (this.reconciledPatternSlotCount >= 0 && configuredSlotCount > this.reconciledPatternSlotCount) {
            restorePatternSlotOverflow(plannedInventory, plannedOverflow, this.reconciledPatternSlotCount, configuredSlotCount);
        }
        moveHiddenPatternSlotsToVisibleOrOverflow(plannedInventory, plannedOverflow, configuredSlotCount);

        boolean inventoryChanged = !matchesPatternInventory(plannedInventory);
        boolean overflowChanged = !matchesPatternStacks(this.patternSlotOverflow, plannedOverflow);
        boolean slotCountChanged = this.reconciledPatternSlotCount != configuredSlotCount;
        if (!inventoryChanged && !overflowChanged && !slotCountChanged) {
            return false;
        }

        runWithPatternInventoryCallbacksSuppressed(() -> applyPatternInventory(plannedInventory));
        this.patternSlotOverflow.clear();
        this.patternSlotOverflow.addAll(plannedOverflow);
        this.reconciledPatternSlotCount = configuredSlotCount;
        updatePatterns();
        return true;
    }

    private int getConfiguredPatternSlotCount() {
        if (this.host instanceof AdaptivePatternProviderHost adaptiveHost) {
            return Math.max(0, Math.min(adaptiveHost.getPatternSlotCountForMenu(), this.patternInventory.size()));
        }
        return this.patternInventory.size();
    }

    private List<ItemStack> copyPatternInventory() {
        List<ItemStack> copiedInventory = new ArrayList<>(this.patternInventory.size());
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            copiedInventory.add(this.patternInventory.getStackInSlot(slot).copy());
        }
        return copiedInventory;
    }

    private static List<ItemStack> copyPatternStacks(List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copiedStacks.add(stack.copy());
        }
        return copiedStacks;
    }

    private void restorePatternSlotOverflow(List<ItemStack> plannedInventory, List<ItemStack> plannedOverflow, int previousSlotCount, int configuredSlotCount) {
        int restoredCount = 0;
        for (int slot = previousSlotCount; slot < configuredSlotCount && restoredCount < plannedOverflow.size(); slot++) {
            if (plannedInventory.get(slot).isEmpty()) {
                plannedInventory.set(slot, plannedOverflow.get(restoredCount));
                restoredCount++;
            }
        }
        if (restoredCount > 0) {
            plannedOverflow.subList(0, restoredCount).clear();
        }
    }

    private static void moveHiddenPatternSlotsToVisibleOrOverflow(List<ItemStack> plannedInventory, List<ItemStack> plannedOverflow, int configuredSlotCount) {
        List<ItemStack> hiddenPatterns = new ArrayList<>();
        for (int slot = configuredSlotCount; slot < plannedInventory.size(); slot++) {
            ItemStack stack = plannedInventory.get(slot);
            if (!stack.isEmpty()) {
                hiddenPatterns.add(stack);
                plannedInventory.set(slot, ItemStack.EMPTY);
            }
        }

        int movedCount = 0;
        for (int slot = 0; slot < configuredSlotCount && movedCount < hiddenPatterns.size(); slot++) {
            if (plannedInventory.get(slot).isEmpty()) {
                plannedInventory.set(slot, hiddenPatterns.get(movedCount));
                movedCount++;
            }
        }
        if (movedCount < hiddenPatterns.size()) {
            plannedOverflow.addAll(0, hiddenPatterns.subList(movedCount, hiddenPatterns.size()));
        }
    }

    private boolean matchesPatternInventory(List<ItemStack> plannedInventory) {
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            if (!ItemStack.matches(this.patternInventory.getStackInSlot(slot), plannedInventory.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPatternStacks(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private void applyPatternInventory(List<ItemStack> plannedInventory) {
        for (int slot = 0; slot < this.patternInventory.size(); slot++) {
            ItemStack plannedStack = plannedInventory.get(slot);
            if (!ItemStack.matches(this.patternInventory.getStackInSlot(slot), plannedStack)) {
                this.patternInventory.setItemDirect(slot, plannedStack);
            }
        }
    }

    private void writePatternSlotOverflowToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag overflowTag = new ListTag();
        for (ItemStack stack : this.patternSlotOverflow) {
            overflowTag.add(stack.saveOptional(registries));
        }
        tag.put(NBT_PATTERN_SLOT_OVERFLOW, overflowTag);
        tag.putInt(NBT_RECONCILED_PATTERN_SLOT_COUNT, this.reconciledPatternSlotCount);
    }

    private void readPatternSlotOverflowFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        this.patternSlotOverflow.clear();
        ListTag overflowTag = tag.getList(NBT_PATTERN_SLOT_OVERFLOW, Tag.TAG_COMPOUND);
        for (int index = 0; index < overflowTag.size(); index++) {
            ItemStack stack = ItemStack.parseOptional(registries, overflowTag.getCompound(index));
            if (!stack.isEmpty()) {
                this.patternSlotOverflow.add(stack);
            }
        }
        this.reconciledPatternSlotCount = tag.contains(NBT_RECONCILED_PATTERN_SLOT_COUNT, Tag.TAG_INT) ? tag.getInt(NBT_RECONCILED_PATTERN_SLOT_COUNT) : -1;
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBatch(
                                                 IPatternDetails patternDetails,
                                                 KeyCounter[] prototype,
                                                 long requestedCount) {
        return prepareBatch(
                patternDetails,
                prototype,
                requestedCount,
                CraftingDispatchTargetAvailability.all()).admission();
    }

    @Override
    public CountedCraftingPreparation prepareBatch(
                                                   IPatternDetails patternDetails,
                                                   KeyCounter[] prototype,
                                                   long requestedCount,
                                                   CraftingDispatchTargetAvailability targetAvailability) {
        if (targetAvailability == null) {
            throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
        }
        if (usesSpecialBatchRoute(patternDetails)) {
            CraftingDispatchTarget target = CraftingDispatchTarget.provider();
            if (!targetAvailability.canAttempt(target)) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
            }
            return CountedCraftingPreparation.accepted(
                    PatternProviderBatching.prepareSingle(this, patternDetails, prototype, requestedCount),
                    target);
        }
        return ((PatternProviderBatchBridge) this).dataEnergistics$prepareStandardBatch(
                patternDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                targetAvailability);
    }

    @Override
    public CountedCraftingPreparation prepareBoundInputBatch(
                                                             IPatternDetails patternDetails,
                                                             IPatternDetails extractionDetails,
                                                             KeyCounter[] prototype,
                                                             long requestedCount,
                                                             CraftingDispatchTargetAvailability targetAvailability) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return prepareBatch(patternDetails, prototype, requestedCount, targetAvailability);
        }
        return ((PatternProviderBatchBridge) this).dataEnergistics$prepareStandardBoundInputBatch(
                patternDetails,
                extractionDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                targetAvailability);
    }

    @Override
    public List<ProviderCapacitySnapshot> snapshotCapacity(
                                                           CraftingProviderId providerId,
                                                           IPatternDetails patternDetails,
                                                           KeyCounter[] prototype,
                                                           long requestedCrafts,
                                                           String patternIdentity,
                                                           long publicationRevision,
                                                           long capacityRevision,
                                                           long captureTick) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return List.of(new ProviderCapacitySnapshot(
                    providerId,
                    CraftingDispatchTarget.provider(),
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    ProviderRoutingMode.UNKNOWN,
                    DispatchCapacity.Unknown.INSTANCE,
                    new DispatchCapacity.Known(1L)));
        }
        return PatternProviderBatching.snapshotStandardCapacity(
                this,
                (PatternProviderBatchAccess) this,
                providerId,
                patternDetails,
                prototype,
                requestedCrafts,
                patternIdentity,
                publicationRevision,
                capacityRevision,
                captureTick);
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBatchForTarget(
                                                          IPatternDetails patternDetails,
                                                          KeyCounter[] prototype,
                                                          long requestedCount,
                                                          CraftingDispatchTarget target) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return target.equals(CraftingDispatchTarget.provider()) ?
                    PatternProviderBatching.prepareSingle(this, patternDetails, prototype, requestedCount) :
                    null;
        }
        return PatternProviderBatching.prepareStandardBatchForTarget(
                this,
                (PatternProviderBatchAccess) this,
                patternDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                target);
    }

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBoundInputBatchForTarget(
                                                                    IPatternDetails patternDetails,
                                                                    IPatternDetails extractionDetails,
                                                                    KeyCounter[] prototype,
                                                                    long requestedCount,
                                                                    CraftingDispatchTarget target) {
        if (usesSpecialBatchRoute(patternDetails)) {
            return prepareBatchForTarget(patternDetails, prototype, requestedCount, target);
        }
        return PatternProviderBatching.prepareStandardBatchForTarget(
                this,
                (PatternProviderBatchAccess) this,
                patternDetails,
                extractionDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterPushPattern,
                target);
    }

    private boolean usesSpecialBatchRoute(IPatternDetails patternDetails) {
        if (!(this.host instanceof AdaptivePatternProviderHost adaptiveHost)) {
            return true;
        }
        return adaptiveHost.isAdvancedAeProviderSelected() || adaptiveHost.isAppliedCreateMechanicalProviderSelected() || adaptiveHost.isMeteoriteProviderSelected() || adaptiveHost.isResonatingProviderSelected() || isResonatingPatternDetails(patternDetails);
    }

    @Override
    public List<Target> reusableTargets(IPatternDetails pattern, IActionSource source, ServerLevel level) {
        if (!reusableNativeAvailable() || this.host.getBlockEntity().getLevel() != level || !(pattern instanceof IMolecularAssemblerSupportedPattern)) {
            return List.of();
        }
        List<Target> targets = new ObjectArrayList<>();
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            if (entry.getValue().pattern().getDefinition().equals(pattern.getDefinition())) {
                String identity = this.reusableCrafting.targetIdentity(entry.getIntKey());
                targets.add(new Target(identity, CountedCraftingTarget.route(identity), Optional.of(AdaptiveReusableCraftingState.MODE)));
            }
        }
        return List.copyOf(targets);
    }

    @Override
    public @Nullable ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
        if (!reusableNativeAvailable() || this.host.getBlockEntity().getLevel() != request.level() ||
                !request.target().mode().equals(Optional.of(AdaptiveReusableCraftingState.MODE))) {
            return null;
        }
        int slot = -1;
        NativePatternSlot nativePattern = null;
        for (var entry : this.nativePatternSlots.int2ObjectEntrySet()) {
            if (this.reusableCrafting.targetIdentity(entry.getIntKey()).equals(request.target().persistentIdentity()) &&
                    entry.getValue().pattern().getDefinition().equals(request.pattern().getDefinition())) {
                slot = entry.getIntKey();
                nativePattern = entry.getValue();
                break;
            }
        }
        if (nativePattern == null) {
            return null;
        }
        int nativeSlot = slot;
        ResourceLocation recipe = nativePattern.recipe();
        ReusableCraftingAdmission prepared = this.reusableCrafting.prepare(nativeSlot, recipe, request,
                request.level().getGameTime(), availableReusableAdmissions(), reusableHost(nativeSlot, recipe));
        if (prepared == null) {
            return null;
        }
        return new ReusableCraftingAdmission() {

            @Override
            public long count() {
                return prepared.count();
            }

            @Override
            public List<SlotStack> physicalInputs() {
                return prepared.physicalInputs();
            }

            @Override
            public boolean replay() {
                return prepared.replay();
            }

            @Override
            public boolean hasTransferredInputOwnership() {
                return prepared.hasTransferredInputOwnership();
            }

            @Override
            public boolean commit(KeyCounter[] delivery) {
                if (!prepared.replay() && (!reusableNativeAvailable() || prepared.count() > availableReusableAdmissions())) {
                    return false;
                }
                boolean accepted = prepared.commit(delivery);
                if (accepted && !prepared.replay()) {
                    dataEnergistics$afterPushPattern();
                }
                return accepted;
            }
        };
    }

    @Override
    public Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot == null ? Optional.empty() : slot.endpoint().query(sessionId);
    }

    @Override
    public Optional<AppendReceipt> reusableReceipt(UUID sessionId, long sequence) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot == null ? Optional.empty() : slot.endpoint().receipt(sessionId, sequence);
    }

    @Override
    public void closeReusableSession(UUID sessionId) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        if (slot != null) {
            slot.endpoint().close(sessionId, reusableHost(slot.index(), slot.recipe()));
        }
    }

    @Override
    public boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver) {
        AdaptiveReusableCraftingState.Slot slot = this.reusableCrafting.locate(sessionId);
        return slot != null && slot.endpoint().settle(sessionId, receiver, reusableHost(slot.index(), slot.recipe()));
    }

    private boolean reusableNativeAvailable() {
        return !this.reusableCrafting.handoffPrepared() && isMeteoritePatternProvider() && this.mainNode.isActive() &&
                !super.isBusy() && !this.host.getBlockEntity().isRemoved();
    }

    private long availableReusableAdmissions() {
        return Math.max(0, getMeteoriteMaxWorksPerRound() - this.worksInRound - this.reusableCrafting.pendingOperations());
    }

    private Host reusableHost(int slot, ResourceLocation recipe) {
        return new Host() {

            private @Nullable Binding checkedBinding;
            private @Nullable NativePatternSlot checkedPattern;

            @Override
            public boolean isAvailable(Binding binding) {
                NativePatternSlot current = nativePatternSlots.get(slot);
                if (!reusableNativeAvailable() || current == null || !current.recipe().equals(recipe) ||
                        !binding.identity().mode().equals(Optional.of(AdaptiveReusableCraftingState.MODE.toString())) ||
                        binding.recipeId().isPresent() && !binding.recipeId().orElseThrow().equals(recipe.toString()) ||
                        !binding.publicationIdentity().equals(current.identity())) {
                    return false;
                }
                if (checkedPattern != current || !binding.equals(checkedBinding)) {
                    var inputs = current.pattern().getInputs();
                    for (var material : binding.consumed()) {
                        if (!inputs[material.slot()].isValid(material.stack().what(), host.getBlockEntity().getLevel())) {
                            return false;
                        }
                    }
                    if (!NativeReusableCrafting.supports(current.pattern(), binding)) {
                        return false;
                    }
                    checkedPattern = current;
                    checkedBinding = binding;
                }
                return worksInRound < getMeteoriteMaxWorksPerRound() && hasMeteoriteEnergy();
            }

            @Override
            public NativeResult execute(Binding binding, Operation operation) {
                NativePatternSlot current = nativePatternSlots.get(slot);
                var grid = getGrid();
                if (current == null || grid == null || !(host.getBlockEntity().getLevel() instanceof ServerLevel level) ||
                        worksInRound >= getMeteoriteMaxWorksPerRound()) {
                    return NativeResult.paused();
                }
                double cost = getMeteoriteEnergyPerWork();
                IEnergyService energy = grid.getEnergyService();
                if (!tryConsumeMeteoriteEnergy()) {
                    return NativeResult.paused();
                }
                worksInRound++;
                NativeResult result = NativeReusableCrafting.execute(current.pattern(), binding, operation, level, recipe);
                if (!result.executed()) {
                    worksInRound--;
                    energy.injectPower(cost, Actionable.MODULATE);
                }
                return result;
            }

            @Override
            public void acceptOutputs(Identity identity, List<GenericStack> outputs) {
                Object2LongOpenHashMap<AEKey> next = new Object2LongOpenHashMap<>(craftedContents);
                for (GenericStack output : outputs) {
                    next.put(output.what(), Math.addExact(next.getLong(output.what()), output.amount()));
                }
                craftedContents.clear();
                craftedContents.putAll(next);
            }

            @Override
            public void persistChanges() {
                host.saveChanges();
                mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            }
        };
    }

    private boolean tickReusableCrafting() {
        if (this.reusableCrafting.handoffPrepared() || !(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return false;
        }
        if (this.nativeRecipeEpoch != RecipeReloadEpoch.current()) {
            rebuildPatternsForConfiguredSlots();
        }
        boolean worked = false;
        for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
            if (!slot.endpoint().hasResidentSession()) {
                continue;
            }
            Host host = reusableHost(slot.index(), slot.recipe());
            if (slot.closing()) {
                slot.close(host);
            }
            int budget = Math.max(0, getMeteoriteMaxWorksPerRound() - this.worksInRound);
            worked |= slot.endpoint().tick(level.getGameTime(), budget, false, host) > 0;
        }
        return worked;
    }

    private record NativePatternSlot(IMolecularAssemblerSupportedPattern pattern, ResourceLocation recipe,
                                     TrinityPatternIdentity identity) {}

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        boolean pushed;

        if (isAdvancedAeDirectionalPattern(patternDetails)) {
            pushed = pushAdvancedAeDirectionalPattern(patternDetails, inputHolder, false);
            if (pushed) {
                dataEnergistics$afterPushPattern();
            }
            return pushed;
        }

        if (isAppliedCreateMechanicalProviderSelected() && pushAppliedCreateMechanicalPattern(patternDetails, inputHolder)) {
            dataEnergistics$afterPushPattern();
            return true;
        }

        if (isMeteoritePatternProvider() && patternDetails instanceof IMolecularAssemblerSupportedPattern molecularAssemblerSupportedPattern) {
            pushed = pushMeteoritePattern(molecularAssemblerSupportedPattern, inputHolder);
            if (pushed) {
                dataEnergistics$afterPushPattern();
            }
            return pushed;
        }

        if (!isResonatingPatternDetails(patternDetails)) {
            pushed = super.pushPattern(patternDetails, inputHolder);
            if (pushed) {
                dataEnergistics$afterPushPattern();
            }
            return pushed;
        }

        if (super.isBusy() || !this.mainNode.isActive() || !getAvailablePatterns().contains(patternDetails)) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }

        KeyCounter[] remaining = copyKeyCounters(inputHolder);
        ArrayList<MarkedInput> markedInputs = new ArrayList<>();
        List<GenericStack> sparseInputs = getSparseInputs(patternDetails);

        for (int sparseIndex = 0; sparseIndex < sparseInputs.size(); sparseIndex++) {
            GenericStack sparseInput = sparseInputs.get(sparseIndex);
            if (sparseInput == null) {
                continue;
            }

            Optional<ResolvedTarget> optionalTarget = getResolvedTarget(patternDetails, sparseIndex);
            if (optionalTarget.isEmpty()) {
                continue;
            }

            if (!removeFromRemaining(remaining, sparseInput.what(), sparseInput.amount())) {
                return false;
            }

            markedInputs.add(new MarkedInput(sparseInput.what(), sparseInput.amount(), optionalTarget.get()));
        }

        for (MarkedInput markedInput : markedInputs) {
            PatternProviderTarget target = findTarget(markedInput.target(), level);
            if (target == null) {
                return false;
            }
            if (isBlockedByMode(target)) {
                return false;
            }
            long simulated = target.insert(markedInput.key(), markedInput.amount(), Actionable.SIMULATE);
            if (simulated < markedInput.amount()) {
                return false;
            }
        }

        PatternProviderTarget fallbackTarget = null;
        if (!isEmpty(remaining)) {
            if (!patternDetails.supportsPushInputsToExternalInventory()) {
                return false;
            }

            ArrayList<FallbackTarget> candidates = new ArrayList<>();
            for (Direction side : getActiveSidesFiltered()) {
                BlockPos adjacentPos = blockEntity.getBlockPos().relative(side);
                PatternProviderTarget target = getExternalTarget(level, adjacentPos, side.getOpposite());
                if (target == null) {
                    continue;
                }
                if (isBlockedByMode(target)) {
                    continue;
                }
                candidates.add(new FallbackTarget(side, target));
            }

            rearrangeRoundRobin(candidates);
            for (int i = 0; i < candidates.size(); i++) {
                FallbackTarget candidate = candidates.get(i);
                if (adapterAcceptsAll(candidate.target(), remaining)) {
                    fallbackTarget = candidate.target();
                    this.localRoundRobinIndex += i + 1;
                    break;
                }
            }

            if (fallbackTarget == null) {
                return false;
            }
        }

        for (MarkedInput markedInput : markedInputs) {
            PatternProviderTarget target = findTarget(markedInput.target(), level);
            if (target == null) {
                return false;
            }
            long inserted = target.insert(markedInput.key(), markedInput.amount(), Actionable.MODULATE);
            if (inserted < markedInput.amount()) {
                return false;
            }
        }

        if (fallbackTarget != null) {
            final PatternProviderTarget target = fallbackTarget;
            patternDetails.pushInputsToExternalInventory(remaining, (what, amount) -> {
                long inserted = target.insert(what, amount, Actionable.MODULATE);
                if (inserted < amount) {
                    throw new IllegalStateException("Fallback target refused resonating pattern input.");
                }
            });
        }

        invokePatternSuccess(patternDetails);
        dataEnergistics$afterPushPattern();
        return true;
    }

    @Override
    public boolean isBusy() {
        return super.isBusy();
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);

        for (ItemStack stack : this.patternSlotOverflow) {
            drops.add(stack.copy());
        }

        for (var entry : this.craftedContents.object2LongEntrySet()) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key != null && amount > 0) {
                key.addDrops(amount, drops, this.host.getBlockEntity().getLevel(), this.host.getBlockEntity().getBlockPos());
            }
        }

        for (var entry : this.advancedDirectionalSendList.object2LongEntrySet()) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key != null && amount > 0) {
                key.addDrops(amount, drops, this.host.getBlockEntity().getLevel(), this.host.getBlockEntity().getBlockPos());
            }
        }
    }

    @Override
    public void clearContent() {
        this.reusableCrafting.ensureCanClear();
        super.clearContent();
        this.craftedContents.clear();
        this.advancedDirectionalSendList.clear();
        this.advancedDirectionalMap.clear();
        this.advancedSendDirection = null;
        this.patternSlotOverflow.clear();
        this.reconciledPatternSlotCount = -1;
        this.reusableCrafting = new AdaptiveReusableCraftingState();
        this.reusableItemHandoff = null;
    }

    private boolean pushAdvancedAeDirectionalPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, boolean skipAvailabilityCheck) {
        if (hasAdvancedDirectionalWork() || super.isBusy() || !this.mainNode.isActive() || (!skipAvailabilityCheck && !getAvailablePatterns().contains(patternDetails))) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        ArrayList<FallbackTarget> candidates = new ArrayList<>();
        for (Direction side : getActiveSidesFiltered()) {
            BlockPos adjacentPos = blockEntity.getBlockPos().relative(side);
            Direction adjacentFace = side.getOpposite();

            ICraftingMachine craftingMachine = ICraftingMachine.of(level, adjacentPos, adjacentFace);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                if (craftingMachine.pushPattern(patternDetails, inputHolder, adjacentFace)) {
                    invokePatternSuccess(patternDetails);
                    return true;
                }
                continue;
            }

            PatternProviderTarget target = getExternalTarget(level, adjacentPos, adjacentFace);
            if (target != null) {
                candidates.add(new FallbackTarget(side, target));
            }
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        rearrangeRoundRobin(candidates);
        for (int i = 0; i < candidates.size(); i++) {
            FallbackTarget candidate = candidates.get(i);
            PatternProviderTarget target = candidate.target();
            if (this.isBlocking() && target.containsPatternInput(getPatternInputs())) {
                continue;
            }

            if (pushAdvancedDirectionalInputs(candidate.direction(), inputHolder, patternDetails)) {
                this.localRoundRobinIndex += i + 1;
                return true;
            }
        }

        return false;
    }

    private boolean pushMeteoritePattern(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputHolder) {
        if (this.reusableCrafting.handoffPrepared() || availableReusableAdmissions() <= 0 || super.isBusy() || !this.mainNode.isActive()) {
            return false;
        }
        boolean freeNativeSlot = false;
        for (int slot = 0; slot < getConfiguredPatternSlotCount(); slot++) {
            ItemStack installed = this.patternInventory.getStackInSlot(slot);
            if (!installed.isEmpty() && AEItemKey.of(installed).equals(pattern.getDefinition())) {
                AdaptiveReusableCraftingState.Slot resident = this.reusableCrafting.slot(slot);
                if (resident == null || !resident.endpoint().hasResidentSession()) {
                    freeNativeSlot = true;
                    break;
                }
            }
        }
        if (!freeNativeSlot) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }

        if (!hasMeteoriteEnergy()) {
            return false;
        }

        List<GenericStack> output = getMeteoritePatternOutput(pattern, inputHolder, level);
        if (output == null || output.stream().noneMatch(stack -> stack.amount() > 0)) {
            return false;
        }

        if (!tryConsumeMeteoriteEnergy()) {
            return false;
        }

        boolean wasEmpty = this.craftedContents.isEmpty();

        for (GenericStack stack : output) {
            if (stack.amount() > 0) {
                this.craftedContents.addTo(stack.what(), stack.amount());
            }
        }

        this.worksInRound++;
        this.saveChanges();

        if (wasEmpty && !this.craftedContents.isEmpty()) {
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
        return true;
    }

    public Set<AEKey> getTrackedCrafts() {
        return this.trackedCrafts;
    }

    public HashSet<AEKey> getOutputCache() {
        return this.outputCache;
    }

    private boolean isResonatingPatternDetails(IPatternDetails patternDetails) {
        return patternDetails != null && patternDetails.getClass().getName().equals(RESONATING_PATTERN_DETAILS_CLASS);
    }

    private boolean isResonatingPullEnabled() {
        return this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost && adaptivePatternProviderHost.isResonatingProviderSelected() && adaptivePatternProviderHost.isResonatingPullEnabled();
    }

    private boolean isAdvancedAeDirectionalPattern(IPatternDetails patternDetails) {
        return isAdvancedAeProviderSelected() && implementsAdvancedAePatternInterface(patternDetails) && hasDirectionalInputs(patternDetails);
    }

    private void rebuildPatternsForConfiguredSlots() {
        this.patterns.clear();
        this.patternInputs.clear();
        this.nativePatternSlots.clear();

        var level = this.host.getBlockEntity().getLevel();
        int configuredSlotCount = getConfiguredPatternSlotCount();
        for (int slot = 0; slot < configuredSlotCount; slot++) {
            ItemStack patternStack = this.patternInventory.getStackInSlot(slot);
            IPatternDetails details = PatternDetailsHelper.decodePattern(patternStack, level);
            if (details == null) {
                continue;
            }

            this.patterns.add(details);
            if (level instanceof ServerLevel serverLevel && details instanceof IMolecularAssemblerSupportedPattern molecular) {
                var resolution = DataEnergisticsEntrypointLoader.snapshot().trinityPatternRecipes().resolve(molecular);
                if (resolution.isPresent()) {
                    this.nativePatternSlots.put(slot, new NativePatternSlot(molecular, resolution.orElseThrow().recipeId(),
                            TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(molecular), serverLevel.registryAccess())));
                }
            }
            for (var input : details.getInputs()) {
                for (var possibleInput : input.getPossibleInputs()) {
                    this.patternInputs.add(possibleInput.what().dropSecondary());
                }
            }
        }

        boolean reloaded = this.nativeRecipeEpoch != RecipeReloadEpoch.current();
        this.nativeRecipeEpoch = RecipeReloadEpoch.current();
        if (!this.reusableCrafting.handoffPrepared()) {
            for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
                NativePatternSlot current = this.nativePatternSlots.get(slot.index());
                if (slot.endpoint().hasResidentSession() && (reloaded || current == null ||
                        !current.pattern().getDefinition().equals(slot.pattern()) || !current.recipe().equals(slot.recipe()))) {
                    slot.requestClose();
                }
            }
        }

        ICraftingProvider.requestUpdate(this.mainNode);
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private boolean isAdvancedAeProviderSelected() {
        return this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost && adaptivePatternProviderHost.isAdvancedAeProviderSelected();
    }

    private boolean isAppliedCreateMechanicalProviderSelected() {
        return ModFlags.isAppliedCreateMechanicalProviderSupportLoaded() && this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost && adaptivePatternProviderHost.isAppliedCreateMechanicalProviderSelected();
    }

    private boolean isMeteoritePatternProvider() {
        return this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost && adaptivePatternProviderHost.isMeteoriteProviderSelected();
    }

    private boolean pushAppliedCreateMechanicalPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (super.isBusy() || !this.mainNode.isActive() || !getAvailablePatterns().contains(patternDetails)) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        Level level = blockEntity.getLevel();
        if (level == null) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        GenericStack primaryOutput = patternDetails.getPrimaryOutput();
        if (primaryOutput == null || !(primaryOutput.what() instanceof AEItemKey primaryOutputKey)) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        List<AppliedCreateCrafterCandidate> candidates = collectAppliedCreateCrafterCandidates(level, blockEntity.getBlockPos());
        if (candidates.isEmpty()) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        List<AppliedCreateRecipeInfo> recipes = collectAppliedCreateRecipeInfos(level, primaryOutputKey);
        if (recipes.isEmpty()) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        List<ItemStack> flattenedInputs = flattenAppliedCreateInputs(inputHolder);
        if (flattenedInputs.isEmpty()) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        for (AppliedCreateCrafterCandidate candidate : candidates) {
            if (!tryPushAppliedCreateRecipe(recipes, candidate.crafterChain(), candidate.crafterGrid(), flattenedInputs)) {
                continue;
            }

            invokePatternSuccess(patternDetails);
            return true;
        }

        return super.pushPattern(patternDetails, inputHolder);
    }

    private List<AppliedCreateCrafterCandidate> collectAppliedCreateCrafterCandidates(Level level, BlockPos providerPos) {
        ArrayList<AppliedCreateCrafterCandidate> candidates = new ArrayList<>();
        for (Direction side : this.host.getTargets()) {
            BlockEntity adjacentBlockEntity = level.getBlockEntity(providerPos.relative(side));
            if (!isMechanicalCrafterBlockEntity(adjacentBlockEntity)) {
                continue;
            }

            List<?> crafterChain = getAllMechanicalCraftersOfChain(adjacentBlockEntity);
            if (crafterChain == null || crafterChain.isEmpty()) {
                continue;
            }

            Map<GridCoord, Object> crafterGrid = computeCrafterGridPositions(crafterChain);
            if (crafterGrid == null) {
                continue;
            }

            candidates.add(new AppliedCreateCrafterCandidate(crafterChain, crafterGrid));
        }
        return candidates;
    }

    @SuppressWarnings("unchecked")
    private List<GenericStack> getSparseInputs(IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return List.of();
        }

        Optional<SparsePatternAccess> access = SPARSE_PATTERN_ACCESS_CACHE.computeIfAbsent(
                patternDetails.getClass(),
                AdaptivePatternProviderLogic::findSparsePatternAccess);
        if (access.isEmpty()) {
            return List.of();
        }

        try {
            Object result = access.get().sparseInputs().invoke(patternDetails);
            return result instanceof List<?> list ? (List<GenericStack>) list : List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private Optional<ResolvedTarget> getResolvedTarget(IPatternDetails patternDetails, int sparseIndex) {
        if (patternDetails == null) {
            return Optional.empty();
        }

        Optional<SparsePatternAccess> access = SPARSE_PATTERN_ACCESS_CACHE.computeIfAbsent(
                patternDetails.getClass(),
                AdaptivePatternProviderLogic::findSparsePatternAccess);
        if (access.isEmpty()) {
            return Optional.empty();
        }

        try {
            Object optionalObject = access.get().targetForSparseInputIndex().invoke(patternDetails, sparseIndex);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) {
                return Optional.empty();
            }

            Object target = optional.get();
            Optional<ResolvedTargetAccess> targetAccess = RESOLVED_TARGET_ACCESS_CACHE.computeIfAbsent(
                    target.getClass(),
                    AdaptivePatternProviderLogic::findResolvedTargetAccess);
            if (targetAccess.isEmpty()) {
                return Optional.empty();
            }

            Object globalPosObject = targetAccess.get().position().invoke(target);
            if (!(globalPosObject instanceof GlobalPos globalPos)) {
                return Optional.empty();
            }

            Object faceObject = targetAccess.get().face().invoke(target);
            if (!(faceObject instanceof Direction face)) {
                return Optional.empty();
            }

            return Optional.of(new ResolvedTarget(globalPos, face));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private boolean isBlockedByMode(PatternProviderTarget target) {
        return this.isBlocking() && target.containsPatternInput(getPatternInputs());
    }

    private List<AppliedCreateRecipeInfo> collectAppliedCreateRecipeInfos(Level level, AEItemKey expectedOutput) {
        return getAppliedCreateRecipeIndex(level).recipesFor(expectedOutput);
    }

    private static AppliedCreateRecipeIndex getAppliedCreateRecipeIndex(Level level) {
        long reloadEpoch = RecipeReloadEpoch.current();
        if (!(level instanceof ServerLevel)) {
            return buildAppliedCreateRecipeIndex(level, reloadEpoch);
        }

        AppliedCreateRecipeIndex current = appliedCreateRecipeIndex;
        if (current.reloadEpoch() == reloadEpoch) {
            return current;
        }

        synchronized (APPLIED_CREATE_RECIPE_INDEX_LOCK) {
            reloadEpoch = RecipeReloadEpoch.current();
            current = appliedCreateRecipeIndex;
            if (current.reloadEpoch() != reloadEpoch) {
                current = buildAppliedCreateRecipeIndex(level, reloadEpoch);
                appliedCreateRecipeIndex = current;
            }
            return current;
        }
    }

    private static AppliedCreateRecipeIndex buildAppliedCreateRecipeIndex(Level level, long reloadEpoch) {
        Map<AEItemKey, List<AppliedCreateRecipeInfo>> recipesByOutput = new HashMap<>();
        HolderLookup.Provider registries = level.registryAccess();

        var mechanicalRecipeType = BuiltInRegistries.RECIPE_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafting"))
                .orElse(null);
        if (mechanicalRecipeType instanceof RecipeType<?> rawMechanicalType) {
            @SuppressWarnings({ "rawtypes", "unchecked" })
            List<RecipeHolder<?>> recipeHolders = (List) level.getRecipeManager().getAllRecipesFor((RecipeType) rawMechanicalType);
            for (RecipeHolder<?> holder : recipeHolders) {
                Object recipe = holder.value();
                try {
                    Optional<MechanicalRecipeAccess> access = getMechanicalRecipeAccess(recipe);
                    if (access.isEmpty()) {
                        continue;
                    }

                    ItemStack result = (ItemStack) access.get().getResultItem().invoke(recipe, registries);
                    AEItemKey outputKey = AEItemKey.of(result);
                    if (outputKey == null) {
                        continue;
                    }
                    int width = (Integer) access.get().getWidth().invoke(recipe);
                    int height = (Integer) access.get().getHeight().invoke(recipe);
                    @SuppressWarnings("unchecked")
                    List<Ingredient> ingredients = (List<Ingredient>) access.get().getIngredients().invoke(recipe);
                    addAppliedCreateRecipe(
                            recipesByOutput,
                            outputKey,
                            new AppliedCreateRecipeInfo(width, height, ingredients));
                } catch (Throwable e) {
                    Data_Energistics.LOGGER.debug("Could not inspect Applied Create mechanical recipe {}", holder.id(), e);
                }
            }
        }

        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof ShapedRecipe shapedRecipe)) {
                continue;
            }
            AEItemKey outputKey = AEItemKey.of(shapedRecipe.getResultItem(registries));
            if (outputKey == null) {
                continue;
            }
            addAppliedCreateRecipe(
                    recipesByOutput,
                    outputKey,
                    new AppliedCreateRecipeInfo(
                            shapedRecipe.getWidth(),
                            shapedRecipe.getHeight(),
                            shapedRecipe.getIngredients()));
        }

        return new AppliedCreateRecipeIndex(reloadEpoch, recipesByOutput);
    }

    private static void addAppliedCreateRecipe(
                                               Map<AEItemKey, List<AppliedCreateRecipeInfo>> recipesByOutput,
                                               AEItemKey output,
                                               AppliedCreateRecipeInfo recipe) {
        recipesByOutput.computeIfAbsent(output, ignored -> new ArrayList<>()).add(recipe);
    }

    private List<ItemStack> flattenAppliedCreateInputs(KeyCounter[] inputHolder) {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (KeyCounter input : inputHolder) {
            for (var entry : input) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                int amount = (int) Math.min(Integer.MAX_VALUE, entry.getLongValue());
                for (int i = 0; i < amount; i++) {
                    stacks.add(itemKey.toStack(1));
                }
            }
        }
        return stacks;
    }

    @Nullable
    private Map<GridCoord, Object> computeCrafterGridPositions(List<?> crafters) {
        if (crafters.isEmpty()) {
            return null;
        }

        Set<Object> crafterSet = new HashSet<>(crafters);
        Map<Object, Object> parentByCrafter = new HashMap<>();
        for (Object crafter : crafters) {
            Object target = getTargetingCrafter(crafter);
            parentByCrafter.put(crafter, target != null && crafterSet.contains(target) ? target : null);
        }

        Object root = null;
        for (Object crafter : crafters) {
            if (parentByCrafter.get(crafter) == null) {
                root = crafter;
                break;
            }
        }
        if (root == null) {
            return null;
        }

        Map<Object, GridCoord> rawPositions = new HashMap<>();
        ArrayList<Object> queue = new ArrayList<>();
        Set<Object> visited = new HashSet<>();
        rawPositions.put(root, new GridCoord(0, 0));
        queue.add(root);
        visited.add(root);

        while (!queue.isEmpty()) {
            Object current = queue.removeFirst();
            GridCoord currentCoord = rawPositions.get(current);
            if (currentCoord == null) {
                continue;
            }

            for (Object candidate : crafters) {
                if (visited.contains(candidate) || parentByCrafter.get(candidate) != current) {
                    continue;
                }

                String pointingName = getCrafterPointingName(candidate);
                int dx = switch (pointingName) {
                    case "RIGHT" -> 1;
                    case "LEFT" -> -1;
                    default -> 0;
                };
                int dy = switch (pointingName) {
                    case "UP" -> 1;
                    case "DOWN" -> -1;
                    default -> 0;
                };

                rawPositions.put(candidate, new GridCoord(currentCoord.x() + dx, currentCoord.y() + dy));
                visited.add(candidate);
                queue.add(candidate);
            }
        }

        if (rawPositions.size() != crafters.size()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (GridCoord coord : rawPositions.values()) {
            minX = Math.min(minX, coord.x());
            minY = Math.min(minY, coord.y());
        }

        Map<GridCoord, Object> normalized = new HashMap<>();
        for (Map.Entry<Object, GridCoord> entry : rawPositions.entrySet()) {
            GridCoord coord = entry.getValue();
            normalized.put(new GridCoord(coord.x() - minX, coord.y() - minY), entry.getKey());
        }
        return normalized;
    }

    private boolean tryPushAppliedCreateRecipe(List<AppliedCreateRecipeInfo> recipes,
                                               List<?> crafterChain,
                                               Map<GridCoord, Object> crafterGrid,
                                               List<ItemStack> flattenedInputs) {
        int gridWidth = 0;
        int gridHeight = 0;
        for (GridCoord coord : crafterGrid.keySet()) {
            gridWidth = Math.max(gridWidth, coord.x() + 1);
            gridHeight = Math.max(gridHeight, coord.y() + 1);
        }

        for (AppliedCreateRecipeInfo recipe : recipes) {
            int width = recipe.width();
            int height = recipe.height();
            if (width > gridWidth || height > gridHeight) {
                continue;
            }

            for (int offsetX = 0; offsetX <= gridWidth - width; offsetX++) {
                for (int offsetY = 0; offsetY <= gridHeight - height; offsetY++) {
                    ArrayList<AppliedCreateSlotAssignment> assignments = new ArrayList<>();
                    ArrayList<ItemStack> remainingInputs = new ArrayList<>(flattenedInputs);
                    boolean matched = true;

                    for (int row = 0; row < height && matched; row++) {
                        for (int col = 0; col < width; col++) {
                            int ingredientIndex = col + row * width;
                            Ingredient ingredient = ingredientIndex < recipe.ingredients().size() ? recipe.ingredients().get(ingredientIndex) : null;
                            if (ingredient == null || ingredient.isEmpty()) {
                                continue;
                            }

                            int gridX = col + offsetX;
                            int gridY = row + offsetY;
                            Object crafter = crafterGrid.get(new GridCoord(gridX, gridY));
                            if (crafter == null) {
                                matched = false;
                                break;
                            }

                            int inputIndex = findMatchingAppliedCreateInput(remainingInputs, ingredient);
                            if (inputIndex < 0) {
                                matched = false;
                                break;
                            }

                            ItemStack inputStack = remainingInputs.get(inputIndex);
                            ItemStack simulatedRemainder = insertIntoCrafter(crafter, inputStack.copy(), true);
                            if (!simulatedRemainder.isEmpty()) {
                                matched = false;
                                break;
                            }

                            assignments.add(new AppliedCreateSlotAssignment(crafter, inputStack.copy()));
                            remainingInputs.remove(inputIndex);
                        }
                    }

                    if (!matched) {
                        continue;
                    }

                    for (AppliedCreateSlotAssignment assignment : assignments) {
                        insertIntoCrafter(assignment.crafter(), assignment.stack(), false);
                    }

                    Object firstCrafter = crafterChain.isEmpty() ? null : crafterChain.getFirst();
                    if (firstCrafter != null) {
                        triggerCrafterRecipeCheck(firstCrafter);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private int findMatchingAppliedCreateInput(List<ItemStack> inputs, Ingredient ingredient) {
        for (int i = 0; i < inputs.size(); i++) {
            if (ingredient.test(inputs.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private String getCrafterPointingName(Object crafter) {
        Optional<AppliedCreateAccess> access = APPLIED_CREATE_ACCESS;
        if (access.isEmpty()) {
            return "UP";
        }

        try {
            Object blockStateObject = access.get().getBlockState().invoke(crafter);
            Object pointingProperty = access.get().pointingProperty().get();
            if (!(blockStateObject instanceof BlockState blockState) || !(pointingProperty instanceof Property<?> property)) {
                return "UP";
            }

            Object pointing = blockState.getValue(property);
            return String.valueOf(pointing);
        } catch (Throwable ignored) {
            return "UP";
        }
    }

    private ItemStack insertIntoCrafter(Object crafter, ItemStack stack, boolean simulate) {
        Optional<AppliedCreateAccess> access = APPLIED_CREATE_ACCESS;
        if (access.isEmpty()) {
            return stack;
        }

        try {
            Object inventory = access.get().getInventory().invoke(crafter);
            Object result = access.get().insertItem().invoke(inventory, 0, stack, simulate);
            return result instanceof ItemStack itemStack ? itemStack : stack;
        } catch (Throwable ignored) {
            return stack;
        }
    }

    private void triggerCrafterRecipeCheck(Object crafter) {
        Optional<AppliedCreateAccess> access = APPLIED_CREATE_ACCESS;
        if (access.isEmpty()) {
            return;
        }

        try {
            access.get().checkCompletedRecipe().invoke(crafter, true);
        } catch (Throwable e) {
            Data_Energistics.LOGGER.debug("Could not trigger Applied Create mechanical crafter recipe check", e);
        }
    }

    private boolean isMechanicalCrafterBlockEntity(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        return APPLIED_CREATE_ACCESS.map(access -> access.crafterClass().isInstance(value)).orElse(false);
    }

    @Nullable
    private List<?> getAllMechanicalCraftersOfChain(Object crafter) {
        Optional<AppliedCreateAccess> access = APPLIED_CREATE_ACCESS;
        if (access.isEmpty()) {
            return null;
        }

        try {
            Object result = access.get().getAllCraftersOfChain().invoke(crafter);
            return result instanceof List<?> list ? list : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private Object getTargetingCrafter(Object crafter) {
        Optional<AppliedCreateAccess> access = APPLIED_CREATE_ACCESS;
        if (access.isEmpty()) {
            return null;
        }

        try {
            return access.get().getTargetingCrafter().invoke(crafter);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Set<AEKey> getPatternInputs() {
        return this.patternInputs;
    }

    private void invokePatternSuccess(IPatternDetails patternDetails) {
        super.onPushPatternSuccess(patternDetails);
    }

    private boolean invokeBaseDoWork() {
        return super.doWork();
    }

    private boolean invokeBaseHasWorkToDo() {
        return super.hasWorkToDo();
    }

    private boolean pushAdvancedDirectionalInputs(Direction primaryDirection, KeyCounter[] inputHolder, IPatternDetails patternDetails) {
        var blockEntity = this.host.getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        BlockPos adjacentPos = blockEntity.getBlockPos().relative(primaryDirection);
        Direction defaultSide = primaryDirection.getOpposite();
        HashMap<AEKey, PatternProviderTarget> targetsByKey = new HashMap<>();
        HashMap<AEKey, Direction> directionMap = new HashMap<>();

        for (KeyCounter input : inputHolder) {
            AEKey firstKey = input.getFirstKey();
            if (firstKey == null) {
                continue;
            }

            Direction inputSide = getAdvancedInputSide(patternDetails, firstKey);
            Direction targetSide = inputSide != null ? inputSide : defaultSide;
            PatternProviderTarget target = getExternalTarget(level, adjacentPos, targetSide);
            targetsByKey.put(firstKey, target);
            directionMap.put(firstKey, inputSide);

            if (!adapterAcceptsItem(target, input)) {
                return false;
            }
        }

        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            PatternProviderTarget target = targetsByKey.get(what);
            long inserted = target == null ? 0 : target.insert(what, amount, Actionable.MODULATE);
            if (inserted < amount) {
                queueAdvancedDirectionalRemainder(what, amount - inserted, primaryDirection, directionMap.get(what));
            }
        });

        invokePatternSuccess(patternDetails);
        this.advancedSendDirection = primaryDirection;

        Map<AEKey, Direction> patternDirectionMap = getAdvancedDirectionMap(patternDetails);
        this.advancedDirectionalMap.clear();
        if (patternDirectionMap != null && !patternDirectionMap.isEmpty()) {
            this.advancedDirectionalMap.putAll(patternDirectionMap);
        } else {
            this.advancedDirectionalMap.putAll(directionMap);
        }

        flushAdvancedDirectionalSendList();
        this.host.saveChanges();
        return true;
    }

    private boolean adapterAcceptsItem(@Nullable PatternProviderTarget target, KeyCounter counter) {
        if (target == null) {
            return false;
        }

        for (var entry : counter) {
            long inserted = target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE);
            if (inserted == 0) {
                return false;
            }
        }
        return true;
    }

    private void queueAdvancedDirectionalRemainder(AEKey key, long amount, Direction primaryDirection, @Nullable Direction inputSide) {
        if (key == null || amount <= 0) {
            return;
        }

        if (this.advancedSendDirection == null) {
            this.advancedSendDirection = primaryDirection;
        }

        this.advancedDirectionalSendList.addTo(key, amount);
        this.advancedDirectionalMap.put(key, inputSide);
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private boolean flushAdvancedDirectionalSendList() {
        if (this.advancedDirectionalSendList.isEmpty()) {
            this.advancedSendDirection = null;
            this.advancedDirectionalMap.clear();
            return false;
        }

        if (this.advancedSendDirection == null) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }

        BlockPos adjacentPos = blockEntity.getBlockPos().relative(this.advancedSendDirection);
        Direction defaultSide = this.advancedSendDirection.getOpposite();
        boolean didSomething = false;

        var iterator = this.advancedDirectionalSendList.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry<AEKey> entry = iterator.next();
            AEKey key = entry.getKey();
            long remaining = entry.getLongValue();
            if (key == null || remaining <= 0) {
                iterator.remove();
                continue;
            }

            Direction inputSide = this.advancedDirectionalMap.get(key);
            Direction targetSide = inputSide != null ? inputSide : defaultSide;
            PatternProviderTarget target = getExternalTarget(level, adjacentPos, targetSide);
            if (target == null) {
                continue;
            }

            long inserted = target.insert(key, remaining, Actionable.MODULATE);
            if (inserted > 0) {
                didSomething = true;
                remaining -= inserted;
            }

            if (remaining <= 0) {
                iterator.remove();
                this.advancedDirectionalMap.remove(key);
            } else {
                entry.setValue(remaining);
            }
        }

        if (this.advancedDirectionalSendList.isEmpty()) {
            this.advancedSendDirection = null;
            this.advancedDirectionalMap.clear();
        }

        if (didSomething) {
            this.host.saveChanges();
        }

        return didSomething;
    }

    private boolean hasAdvancedDirectionalWork() {
        return !this.advancedDirectionalSendList.isEmpty();
    }

    private boolean implementsAdvancedAePatternInterface(IPatternDetails patternDetails) {
        return implementsNamedInterface(patternDetails, ADVANCED_AE_PATTERN_DETAILS_INTERFACE);
    }

    private boolean implementsNamedInterface(IPatternDetails patternDetails, String interfaceName) {
        if (patternDetails == null) {
            return false;
        }

        Class<?> type = patternDetails.getClass();
        if (type.getName().equals(interfaceName)) {
            return true;
        }

        for (Class<?> iface : type.getInterfaces()) {
            if (iface.getName().equals(interfaceName)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasDirectionalInputs(IPatternDetails patternDetails) {
        Optional<DirectionalPatternAccess> access = getDirectionalPatternAccess(patternDetails);
        if (access.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(access.get().directionalInputsSet().invoke(patternDetails));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private Direction getAdvancedInputSide(IPatternDetails patternDetails, AEKey key) {
        Optional<DirectionalPatternAccess> access = getDirectionalPatternAccess(patternDetails);
        if (access.isEmpty()) {
            return null;
        }
        try {
            Object result = access.get().directionSideForInputKey().invoke(patternDetails, key);
            return result instanceof Direction direction ? direction : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private Map<AEKey, Direction> getAdvancedDirectionMap(IPatternDetails patternDetails) {
        Optional<DirectionalPatternAccess> access = getDirectionalPatternAccess(patternDetails);
        if (access.isEmpty()) {
            return null;
        }
        try {
            Object result = access.get().directionMap().invoke(patternDetails);
            return result instanceof Map<?, ?> map ? (Map<AEKey, Direction>) map : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Optional<DirectionalPatternAccess> getDirectionalPatternAccess(@Nullable IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return Optional.empty();
        }
        return DIRECTIONAL_PATTERN_ACCESS_CACHE.computeIfAbsent(
                patternDetails.getClass(),
                AdaptivePatternProviderLogic::findDirectionalPatternAccess);
    }

    private static Optional<MechanicalRecipeAccess> getMechanicalRecipeAccess(Object recipe) {
        return MECHANICAL_RECIPE_ACCESS_CACHE.computeIfAbsent(
                recipe.getClass(),
                AdaptivePatternProviderLogic::findMechanicalRecipeAccess);
    }

    private static Optional<MechanicalRecipeAccess> findMechanicalRecipeAccess(Class<?> type) {
        Optional<MethodHandle> getResultItem = findDuckMethod(type, "getResultItem", HolderLookup.Provider.class);
        Optional<MethodHandle> getWidth = findDuckMethod(type, "getWidth");
        Optional<MethodHandle> getHeight = findDuckMethod(type, "getHeight");
        Optional<MethodHandle> getIngredients = findDuckMethod(type, "getIngredients");
        if (getResultItem.isEmpty() || getWidth.isEmpty() || getHeight.isEmpty() || getIngredients.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MechanicalRecipeAccess(
                getResultItem.get(),
                getWidth.get(),
                getHeight.get(),
                getIngredients.get()));
    }

    private static Optional<SparsePatternAccess> findSparsePatternAccess(Class<?> type) {
        Optional<MethodHandle> sparseInputs = findDuckMethod(type, "getSparseInputs");
        Optional<MethodHandle> targetForSparseInputIndex = findDuckMethod(type, "getTargetForSparseInputIndex", int.class);
        if (sparseInputs.isEmpty() || targetForSparseInputIndex.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SparsePatternAccess(sparseInputs.get(), targetForSparseInputIndex.get()));
    }

    private static Optional<ResolvedTargetAccess> findResolvedTargetAccess(Class<?> type) {
        Optional<MethodHandle> position = findDuckMethod(type, "pos");
        Optional<MethodHandle> face = findDuckMethod(type, "face");
        if (position.isEmpty() || face.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedTargetAccess(position.get(), face.get()));
    }

    private static Optional<DirectionalPatternAccess> findDirectionalPatternAccess(Class<?> type) {
        Optional<MethodHandle> directionalInputsSet = findDuckMethod(type, "directionalInputsSet");
        Optional<MethodHandle> directionSideForInputKey = findDuckMethod(type, "getDirectionSideForInputKey", AEKey.class);
        Optional<MethodHandle> directionMap = findDuckMethod(type, "getDirectionMap");
        if (directionalInputsSet.isEmpty() || directionSideForInputKey.isEmpty() || directionMap.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DirectionalPatternAccess(
                directionalInputsSet.get(),
                directionSideForInputKey.get(),
                directionMap.get()));
    }

    private static Optional<AppliedCreateAccess> findAppliedCreateAccess() {
        try {
            Class<?> crafterClass = Class.forName(CREATE_MECHANICAL_CRAFTER_BE_CLASS);
            MethodHandles.Lookup crafterLookup = MethodHandles.privateLookupIn(crafterClass, LOOKUP);
            MethodHandle getBlockState = crafterLookup.findVirtual(crafterClass, "getBlockState",
                    MethodType.methodType(BlockState.class));
            Method getInventoryMethod = crafterClass.getMethod("getInventory");
            MethodHandle getInventory = MethodHandles.privateLookupIn(getInventoryMethod.getDeclaringClass(), LOOKUP)
                    .unreflect(getInventoryMethod);
            MethodHandle checkCompletedRecipe = crafterLookup.findVirtual(crafterClass, "checkCompletedRecipe",
                    MethodType.methodType(void.class, boolean.class));

            Class<?> blockClass = Class.forName(CREATE_MECHANICAL_CRAFTER_BLOCK_CLASS);
            VarHandle pointingProperty = MethodHandles.privateLookupIn(blockClass, LOOKUP)
                    .findStaticVarHandle(blockClass, "POINTING", Property.class);

            Class<?> handlerClass = Class.forName(CREATE_RECIPE_GRID_HANDLER_CLASS);
            MethodHandles.Lookup handlerLookup = MethodHandles.privateLookupIn(handlerClass, LOOKUP);
            MethodHandle getAllCraftersOfChain = handlerLookup.findStatic(handlerClass, "getAllCraftersOfChain",
                    MethodType.methodType(List.class, crafterClass));
            MethodHandle getTargetingCrafter = handlerLookup.findStatic(handlerClass, "getTargetingCrafter",
                    MethodType.methodType(crafterClass, crafterClass));

            MethodHandle insertItem = findAppliedCreateInsertItem(getInventoryMethod.getReturnType());
            if (insertItem == null) {
                return Optional.empty();
            }

            return Optional.of(new AppliedCreateAccess(
                    crafterClass,
                    getBlockState,
                    pointingProperty,
                    getInventory,
                    insertItem,
                    checkCompletedRecipe,
                    getAllCraftersOfChain,
                    getTargetingCrafter));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    @Nullable
    private static MethodHandle findAppliedCreateInsertItem(Class<?> inventoryClass) {
        try {
            return MethodHandles.privateLookupIn(inventoryClass, LOOKUP)
                    .findVirtual(inventoryClass, "insertItem",
                            MethodType.methodType(ItemStack.class, int.class, ItemStack.class, boolean.class));
        } catch (ReflectiveOperationException | SecurityException e) {
            Data_Energistics.LOGGER.debug("Could not resolve Applied Create inventory insertItem access", e);
        }

        return null;
    }

    private static Optional<MethodHandle> findDuckMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return Optional.of(MethodHandles.privateLookupIn(method.getDeclaringClass(), LOOKUP).unreflect(method));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private void refreshAdaptivePatternTracking() {
        this.outputCache.clear();

        IStackWatcher watcher = this.craftingWatcher;
        if (watcher != null) {
            watcher.reset();
        }

        for (IPatternDetails pattern : getAvailablePatterns()) {
            if (pattern == null) {
                continue;
            }

            for (GenericStack output : pattern.getOutputs()) {
                if (output == null || output.what() == null) {
                    continue;
                }
                this.outputCache.add(output.what());
                if (watcher != null) {
                    watcher.add(output.what());
                }
            }
        }
    }

    public void onHostStateChanged() {
        if (!isMeteoritePatternProvider() && !this.reusableCrafting.handoffPrepared()) {
            for (AdaptiveReusableCraftingState.Slot slot : this.reusableCrafting.slots()) {
                if (slot.endpoint().hasResidentSession()) {
                    slot.requestClose();
                }
            }
            this.host.saveChanges();
        }
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void dataEnergistics$afterPushPattern() {
        this.dataEnergistics$dispatchPulsePending = true;
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!this.sendList.isEmpty() || !this.advancedDirectionalSendList.isEmpty()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = false;
        if (this.host instanceof RedstoneTuningAwareHost tuningHost) {
            tuningHost.dataEnergistics$onRedstoneTuningDispatch();
        }
    }

    private void dataEnergistics$updatePulseUnlockState() {
        if (!(this.host instanceof RedstoneTuningAwareHost tuningHost)) {
            return;
        }

        tuningHost.dataEnergistics$scheduleRedstoneInputCheck();
        tuningHost.dataEnergistics$serverTick();

        if (!tuningHost.dataEnergistics$hasRedstoneTuningCard() || tuningHost.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }

        var blockEntity = this.host.getBlockEntity();
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide()) {
            return;
        }

        if (tuningHost.dataEnergistics$consumeRedstoneInputPulse() && blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                    serverLevel,
                    this.host.getGrid(),
                    this.actionSource,
                    getAvailablePatterns());
        }
    }

    @Override
    public boolean dataEnergistics$forcePulseUnlock() {
        if (!(this.host instanceof RedstoneTuningAwareHost tuningHost) || !tuningHost.dataEnergistics$hasRedstoneTuningCard() || tuningHost.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return false;
        }

        BlockEntity blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }

        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.host.getGrid(),
                this.actionSource,
                getAvailablePatterns());
        return true;
    }

    @Nullable
    private MethodHandle findAe2CsAdjacentMeStorageMethod() {
        try {
            Class<?> helperClass = Class.forName(AE2CS_GENERIC_STACK_INV_HELPER_CLASS);
            Method method = helperClass.getMethod("getAdjacentMeStorage",
                    Level.class, BlockPos.class, BlockEntity.class, Direction.class);
            return MethodHandles.privateLookupIn(helperClass, LOOKUP).unreflect(method);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private MEStorage getAe2CsAdjacentMeStorage(Level level, BlockPos pos, @Nullable BlockEntity blockEntity, Direction side) {
        if (this.ae2csAdjacentMeStorageMethod == null) {
            return null;
        }

        try {
            Object result = this.ae2csAdjacentMeStorageMethod.invoke(level, pos, blockEntity, side);
            return result instanceof MEStorage storage ? storage : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private List<GenericStack> getMeteoritePatternOutput(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputHolder, ServerLevel level) {
        final ItemStack[] grid3x3 = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            grid3x3[i] = ItemStack.EMPTY;
        }

        try {
            KeyCounter[] inputHolderCopy = copyKeyCounters(inputHolder);
            pattern.fillCraftingGrid(inputHolderCopy, (slot, stack) -> {
                if (slot >= 0 && slot < 9) {
                    grid3x3[slot] = stack == null ? ItemStack.EMPTY : stack;
                }
            });
        } catch (RuntimeException exception) {
            return null;
        }

        int minX = 3;
        int minY = 3;
        int maxX = -1;
        int maxY = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = grid3x3[slot];
            if (!stack.isEmpty()) {
                int x = slot % 3;
                int y = slot / 3;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX < 0) {
            return null;
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        List<ItemStack> compressedItems = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcSlot = (minX + x) + (minY + y) * 3;
                compressedItems.add(grid3x3[srcSlot]);
            }
        }

        CraftingInput input = CraftingInput.of(width, height, compressedItems);
        ItemStack output = pattern.assemble(input, level);
        if (output == null || output.isEmpty()) {
            return null;
        }

        NonNullList<ItemStack> remainders = pattern.getRemainingItems(input);
        List<GenericStack> finalOutput = new ArrayList<>();
        GenericStack outputStack = GenericStack.fromItemStack(output);
        if (outputStack != null) {
            finalOutput.add(outputStack);
        }
        for (ItemStack remainder : remainders) {
            GenericStack remainingStack = GenericStack.fromItemStack(remainder);
            if (remainingStack != null) {
                finalOutput.add(remainingStack);
            }
        }
        return finalOutput;
    }

    private void flushCraftedOutputs() {
        this.worksInRound = 0;
        if (this.craftedContents.isEmpty()) {
            return;
        }

        boolean contentsChanged = false;
        var iterator = this.craftedContents.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry<AEKey> entry = iterator.next();
            AEKey key = entry.getKey();
            long remaining = entry.getLongValue();
            if (key == null || remaining <= 0) {
                iterator.remove();
                contentsChanged = true;
                continue;
            }

            long inserted = getReturnInv().insert(key, remaining, Actionable.MODULATE, this.actionSource);
            if (inserted <= 0) {
                continue;
            }
            remaining -= inserted;

            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
            contentsChanged = true;
        }

        if (contentsChanged) {
            this.saveChanges();
        }
    }

    private boolean doResonatingPullWork() {
        if (!isResonatingPullEnabled()) {
            return false;
        }

        var hostBe = this.host.getBlockEntity();
        if (!(hostBe.getLevel() instanceof ServerLevel hostLevel) || !this.mainNode.isActive()) {
            return false;
        }

        var returnInv = getReturnInv();
        var grid = getGrid();
        MEStorage networkStorage = grid == null ? null : grid.getStorageService().getInventory();
        var sides = getActiveSidesFiltered();
        if (sides.isEmpty()) {
            return false;
        }

        final int maxKeysPerTick = 32;
        int scanned = 0;

        for (var dir : sides) {
            BlockPos adjacentPos = hostBe.getBlockPos().relative(dir);
            Direction adjacentFace = dir.getOpposite();
            if (!hostLevel.hasChunkAt(adjacentPos) || AdaptivePatternProviderResolver.isPatternProviderAttachment(hostLevel, adjacentPos, adjacentFace)) {
                continue;
            }

            var externalStorage = getAe2CsAdjacentMeStorage(hostLevel, adjacentPos, null, adjacentFace);
            if (externalStorage == null) {
                continue;
            }

            for (var stack : externalStorage.getAvailableStacks()) {
                if (scanned++ >= maxKeysPerTick) {
                    return false;
                }

                AEKey key = stack.getKey();
                long available = stack.getLongValue();
                if (key == null || available <= 0) {
                    continue;
                }

                long request = Math.min(available, 4000);
                long canInsertIntoNetwork = networkStorage == null ? 0 : networkStorage.insert(key, request, Actionable.SIMULATE, this.actionSource);
                long remainingRequest = request - canInsertIntoNetwork;
                long canBuffer = remainingRequest <= 0 ? 0 : returnInv.insert(key, remainingRequest, Actionable.SIMULATE, this.actionSource);
                long pullAmount = canInsertIntoNetwork + canBuffer;
                if (pullAmount <= 0) {
                    continue;
                }

                long extracted = externalStorage.extract(key, pullAmount, Actionable.MODULATE, this.actionSource);
                if (extracted <= 0) {
                    continue;
                }

                long insertedIntoNetwork = networkStorage == null ? 0 : networkStorage.insert(key, extracted, Actionable.MODULATE, this.actionSource);
                long leftover = extracted - insertedIntoNetwork;
                long buffered = leftover <= 0 ? 0 : returnInv.insert(key, leftover, Actionable.MODULATE, this.actionSource);
                leftover -= buffered;
                if (leftover > 0) {
                    externalStorage.insert(key, leftover, Actionable.MODULATE, this.actionSource);
                }
                return true;
            }
        }

        return false;
    }

    public int getReturnInventorySlotCount() {
        return getReturnInv().size();
    }

    public ItemStack getReturnInventoryStack(int slot) {
        if (slot < 0 || slot >= getReturnInv().size()) {
            return ItemStack.EMPTY;
        }

        GenericStack stack = getReturnInv().getStack(slot);
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey) || stack.amount() <= 0) {
            return ItemStack.EMPTY;
        }

        return itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount()));
    }

    public ItemStack insertReturnInventoryItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= getReturnInv().size()) {
            return stack;
        }

        AEItemKey itemKey = AEItemKey.of(stack);
        if (itemKey == null) {
            return stack;
        }

        long inserted = getReturnInv().insert(slot, itemKey, stack.getCount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        if (inserted <= 0) {
            return stack;
        }

        if (inserted >= stack.getCount()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        remainder.shrink((int) inserted);
        return remainder;
    }

    public long insertReturnInventoryStack(int slot, GenericStack stack, boolean simulate) {
        if (stack == null || stack.what() == null || stack.amount() <= 0 || slot < 0 || slot >= getReturnInv().size()) {
            return 0;
        }

        long inserted = getReturnInv().insert(
                slot,
                stack.what(),
                stack.amount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE);
        return inserted;
    }

    public long insertReturnInventoryKey(AEKey key, long amount, boolean simulate) {
        if (key == null || amount <= 0) {
            return 0;
        }

        long inserted = getReturnInv().insert(
                key,
                amount,
                simulate ? Actionable.SIMULATE : Actionable.MODULATE,
                this.actionSource);
        return inserted;
    }

    private boolean isAdvancedAeFilteredImportEnabled() {
        return this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost && adaptivePatternProviderHost.isAdvancedAeProviderSelected() && adaptivePatternProviderHost.isAdvancedAeFilteredImportEnabled();
    }

    private boolean hasMeteoriteEnergy() {
        var grid = getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energyService = grid.getEnergyService();
        if (energyService == null) {
            return false;
        }

        double requiredEnergy = getMeteoriteEnergyPerWork();
        double extracted = energyService.extractAEPower(requiredEnergy, Actionable.SIMULATE, PowerMultiplier.ONE);
        return isMeteoriteEnergyRequirementMet(extracted, requiredEnergy);
    }

    private boolean tryConsumeMeteoriteEnergy() {
        var grid = getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energyService = grid.getEnergyService();
        if (energyService == null) {
            return false;
        }

        double requiredEnergy = getMeteoriteEnergyPerWork();
        double extracted = energyService.extractAEPower(requiredEnergy, Actionable.MODULATE, PowerMultiplier.ONE);
        if (isMeteoriteEnergyRequirementMet(extracted, requiredEnergy)) {
            return true;
        }

        try {
            energyService.injectPower(extracted, Actionable.MODULATE);
        } catch (Throwable e) {
            Data_Energistics.LOGGER.debug("Could not refund extracted meteorite provider energy", e);
        }
        return false;
    }

    private static boolean isMeteoriteEnergyRequirementMet(double extractedEnergy, double requiredEnergy) {
        return extractedEnergy + METEORITE_ENERGY_TOLERANCE >= requiredEnergy;
    }

    private int getMeteoriteSpeedCardCount() {
        if (!(this.host instanceof AdaptivePatternProviderHost adaptivePatternProviderHost) || !adaptivePatternProviderHost.isMeteoriteProviderSelected()) {
            return 0;
        }

        return Math.max(0, adaptivePatternProviderHost.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    private int getMeteoriteMaxWorksPerRound() {
        return METEORITE_MAX_WORKS_PER_ROUND << Math.min(4, getMeteoriteSpeedCardCount());
    }

    private double getMeteoriteEnergyPerWork() {
        return (double) (METEORITE_ENERGY_PER_WORK << Math.min(4, getMeteoriteSpeedCardCount()));
    }

    private void installExpandedReturnInventory() {
        this.returnInv = new ExpandedReturnInventory(this::onReturnInventoryChanged, this);
    }

    private void onReturnInventoryChanged() {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.host.saveChanges();
    }

    private Set<Direction> getActiveSidesFiltered() {
        var sides = EnumSet.copyOf(this.host.getTargets());
        var node = this.mainNode.getNode();
        if (node == null) {
            return sides;
        }

        for (var entry : node.getInWorldConnections().entrySet()) {
            var otherNode = entry.getValue().getOtherSide(node);
            Object owner = otherNode.getOwner();
            if (owner instanceof PatternProviderLogicHost || owner instanceof InterfaceLogicHost && otherNode.getGrid() != null && otherNode.getGrid().equals(this.mainNode.getGrid())) {
                sides.remove(entry.getKey());
            }
        }

        return sides;
    }

    @Nullable
    private PatternProviderTarget findTarget(ResolvedTarget target, ServerLevel sourceLevel) {
        var targetLevel = sourceLevel.getServer().getLevel(target.position().dimension());
        if (targetLevel == null) {
            return null;
        }
        var pos = target.position().pos();
        if (!targetLevel.isLoaded(pos)) {
            return null;
        }
        return PatternProviderTarget.get(targetLevel, pos, null, target.face(), this.actionSource);
    }

    private static KeyCounter[] copyKeyCounters(KeyCounter[] inputHolder) {
        var copy = new KeyCounter[inputHolder.length];
        for (int i = 0; i < inputHolder.length; i++) {
            copy[i] = new KeyCounter();
            copy[i].addAll(inputHolder[i]);
        }
        return copy;
    }

    private static boolean removeFromRemaining(KeyCounter[] remaining, AEKey key, long amount) {
        long toRemove = amount;
        for (KeyCounter counter : remaining) {
            long available = counter.get(key);
            if (available <= 0) {
                continue;
            }
            long taken = Math.min(available, toRemove);
            counter.remove(key, taken);
            toRemove -= taken;
            if (toRemove <= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            for (var entry : counter) {
                if (entry.getLongValue() > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputHolder) {
        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                long inserted = target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE);
                if (inserted < entry.getLongValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private PatternProviderTarget getExternalTarget(Level level, BlockPos adjacentPos, Direction targetSide) {
        if (AdaptivePatternProviderResolver.isPatternProviderAttachment(level, adjacentPos, targetSide)) {
            return null;
        }
        return PatternProviderTarget.get(level, adjacentPos, null, targetSide, this.actionSource);
    }

    private <T> void rearrangeRoundRobin(List<T> list) {
        if (list.isEmpty()) {
            return;
        }
        int idx = Math.floorMod(this.localRoundRobinIndex, list.size());
        if (idx == 0) {
            return;
        }
        var head = new ArrayList<>(list.subList(0, idx));
        list.subList(0, idx).clear();
        list.addAll(head);
    }

    private record ResolvedTarget(GlobalPos position, Direction face) {}

    private record MarkedInput(AEKey key, long amount, ResolvedTarget target) {}

    private record FallbackTarget(Direction direction, PatternProviderTarget target) {}

    private record GridCoord(int x, int y) {}

    private record AppliedCreateCrafterCandidate(List<?> crafterChain, Map<GridCoord, Object> crafterGrid) {}

    private record AppliedCreateRecipeInfo(int width, int height, List<Ingredient> ingredients) {

        private AppliedCreateRecipeInfo {
            ingredients = List.copyOf(ingredients);
        }
    }

    private record AppliedCreateRecipeIndex(long reloadEpoch,
                                            Map<AEItemKey, List<AppliedCreateRecipeInfo>> recipesByOutput) {

        private AppliedCreateRecipeIndex {
            Map<AEItemKey, List<AppliedCreateRecipeInfo>> immutableRecipes = new HashMap<>();
            recipesByOutput.forEach((output, recipes) -> immutableRecipes.put(output, List.copyOf(recipes)));
            recipesByOutput = Map.copyOf(immutableRecipes);
        }

        private static AppliedCreateRecipeIndex empty() {
            return new AppliedCreateRecipeIndex(Long.MIN_VALUE, Map.of());
        }

        private List<AppliedCreateRecipeInfo> recipesFor(AEItemKey output) {
            return this.recipesByOutput.getOrDefault(output, List.of());
        }
    }

    private record AppliedCreateSlotAssignment(Object crafter, ItemStack stack) {}

    private record MechanicalRecipeAccess(MethodHandle getResultItem,
                                          MethodHandle getWidth,
                                          MethodHandle getHeight,
                                          MethodHandle getIngredients) {}

    private record AppliedCreateAccess(Class<?> crafterClass,
                                       MethodHandle getBlockState,
                                       VarHandle pointingProperty,
                                       MethodHandle getInventory,
                                       MethodHandle insertItem,
                                       MethodHandle checkCompletedRecipe,
                                       MethodHandle getAllCraftersOfChain,
                                       MethodHandle getTargetingCrafter) {}

    private record SparsePatternAccess(MethodHandle sparseInputs, MethodHandle targetForSparseInputIndex) {}

    private record ResolvedTargetAccess(MethodHandle position, MethodHandle face) {}

    private record DirectionalPatternAccess(MethodHandle directionalInputsSet,
                                            MethodHandle directionSideForInputKey,
                                            MethodHandle directionMap) {}

    private final class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            boolean sleeping = !invokeBaseHasWorkToDo() && !hasAdvancedDirectionalWork() && craftedContents.isEmpty() && getReturnInv().isEmpty() && !isResonatingPullEnabled() && !reusableCrafting.hasResidents();
            return new TickingRequest(
                    TickRates.Interface,
                    sleeping);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            dataEnergistics$updatePulseUnlockState();
            boolean couldDoWork = invokeBaseDoWork();
            couldDoWork = flushAdvancedDirectionalSendList() || couldDoWork;
            dataEnergistics$tryFinishDispatchPulse();
            couldDoWork = doResonatingPullWork() || couldDoWork;
            couldDoWork = tickReusableCrafting() || couldDoWork;
            int before = craftedContents.size();
            flushCraftedOutputs();
            boolean workedForCrafter = craftedContents.size() != before || before > 0;
            couldDoWork = couldDoWork || workedForCrafter;
            boolean hasWork = invokeBaseHasWorkToDo() || isResonatingPullEnabled() || hasAdvancedDirectionalWork() || !craftedContents.isEmpty() || !getReturnInv().isEmpty() || reusableCrafting.hasResidents();
            return hasWork ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER) : TickRateModulation.SLEEP;
        }
    }

    private final class AdaptiveCraftingWatcherNode implements ICraftingWatcherNode {

        @Override
        public void updateWatcher(IStackWatcher watcher) {
            craftingWatcher = watcher;
            refreshAdaptivePatternTracking();
        }

        @Override
        public void onRequestChange(AEKey what) {
            if (what == null) {
                return;
            }

            if (trackedCrafts.contains(what)) {
                trackedCrafts.remove(what);
            } else {
                trackedCrafts.add(what);
            }
        }

        @Override
        public void onCraftableChange(AEKey what) {}
    }

    private static final class ExpandedReturnInventory extends PatternProviderReturnInventory {

        private static final ThreadLocal<Integer> PREVIOUS_SLOT_COUNT = new ThreadLocal<>();

        private final AdaptivePatternProviderLogic logic;

        private ExpandedReturnInventory(Runnable listener, AdaptivePatternProviderLogic logic) {
            super(prepare(listener));
            this.logic = logic;
            this.setFilter(this::isAllowed);
            Integer previous = PREVIOUS_SLOT_COUNT.get();
            if (previous != null) {
                PatternProviderReturnInventory.NUMBER_OF_SLOTS = previous;
            }
            PREVIOUS_SLOT_COUNT.remove();
        }

        private boolean isAllowed(int slot, AEKey key) {
            if (key == null || !this.logic.isAdvancedAeFilteredImportEnabled()) {
                return true;
            }

            Set<AEKey> trackedCrafts = this.logic.getTrackedCrafts();
            if (!trackedCrafts.isEmpty() && trackedCrafts.contains(key)) {
                return true;
            }

            return this.logic.getOutputCache().contains(key);
        }

        private static Runnable prepare(Runnable listener) {
            PREVIOUS_SLOT_COUNT.set(PatternProviderReturnInventory.NUMBER_OF_SLOTS);
            PatternProviderReturnInventory.NUMBER_OF_SLOTS = EXPANDED_RETURN_SLOTS;
            return listener;
        }
    }
}
