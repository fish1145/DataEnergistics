package com.fish_dan_.data_energistics.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.settings.TickRates;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.GhostOutputBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.EjectModeRegistry;
import com.moakiee.ae2lt.logic.MachineAdapter;
import com.moakiee.ae2lt.logic.MachineAdapterRegistry;
import com.moakiee.ae2lt.logic.PushResult;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AdaptivePatternProviderLogic extends PatternProviderLogic {
    private static final String RESONATING_PATTERN_DETAILS_CLASS =
            "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails";
    private static final String ADVANCED_AE_PATTERN_DETAILS_INTERFACE =
            "net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails";
    private static final String AE2LT_OVERLOADED_PATTERN_DETAILS_INTERFACE =
            "com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails";
    private static final String AE2LT_POWER_COST_UTIL_CLASS =
            "com.moakiee.ae2lt.logic.energy.PowerCostUtil";
    private static final String AE2LT_ALLOWED_OUTPUT_FILTER_CLASS =
            "com.moakiee.ae2lt.logic.AllowedOutputFilter";
    private static final int METEORITE_ENERGY_PER_WORK = 50;
    private static final int METEORITE_MAX_WORKS_PER_ROUND = 8;
    private static final int EXPANDED_RETURN_SLOTS = 18;
    private static final String NBT_ADVANCED_SEND_LIST = "adaptive_advanced_send_list";
    private static final String NBT_ADVANCED_SEND_DIRECTION = "adaptive_advanced_send_direction";
    private static final String NBT_ADVANCED_DIRECTION_MAP = "adaptive_advanced_direction_map";

    private final PatternProviderLogicHost host;
    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private int localRoundRobinIndex;
    private final Object2LongOpenHashMap<AEKey> craftedContents = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> advancedDirectionalSendList = new Object2LongOpenHashMap<>();
    private final HashMap<AEKey, Direction> advancedDirectionalMap = new HashMap<>();
    private final List<GenericStack> ae2ltWirelessSendList = new ArrayList<>();
    private @Nullable OverloadedPatternProviderBlockEntity.WirelessConnection ae2ltWirelessSendConn;
    private final Set<AEKey> trackedCrafts = new HashSet<>();
    private final HashSet<AEKey> outputCache = new HashSet<>();
    private @Nullable Object ae2ltAllowedOutputFilter;
    private boolean ae2ltOutputFilterDirty = true;
    private @Nullable IStackWatcher craftingWatcher;
    private final ICraftingWatcherNode craftingWatcherNode = new AdaptiveCraftingWatcherNode();
    private @Nullable Direction advancedSendDirection;
    private int worksInRound;
    private final Method doWorkMethod;
    private final Method hasWorkToDoMethod;
    private long ae2ltLastAutoReturnTick = -1L;

    public AdaptivePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        this.host = host;
        this.mainNode = mainNode
                .addService(IGridTickable.class, new Ticker())
                .addService(ICraftingWatcherNode.class, this.craftingWatcherNode);
        this.actionSource = new MachineSource(mainNode::getNode);
        this.doWorkMethod = findBaseMethod("doWork");
        this.hasWorkToDoMethod = findBaseMethod("hasWorkToDo");
        installExpandedReturnInventory();
    }

    @Override
    public void onChangeInventory(appeng.util.inv.AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        this.ae2ltOutputFilterDirty = true;
        refreshAdaptivePatternTracking();
    }

    @Override
    public void updatePatterns() {
        if (isAe2LightningTechOverloadedProviderSelected()) {
            rebuildPatternsIncludingAe2LtOverloadPatterns();
        } else {
            super.updatePatterns();
        }
        this.ae2ltOutputFilterDirty = true;
        refreshAdaptivePatternTracking();
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);

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
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);

        this.advancedDirectionalSendList.clear();
        this.advancedDirectionalMap.clear();
        this.advancedSendDirection = null;

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
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (isAe2LightningTechOverloadedPattern(patternDetails)) {
            return pushAe2LightningTechOverloadedPattern(patternDetails, inputHolder);
        }

        if (isAdvancedAeDirectionalPattern(patternDetails)) {
            return pushAdvancedAeDirectionalPattern(patternDetails, inputHolder);
        }

        if (isMeteoritePatternProvider() && patternDetails instanceof IMolecularAssemblerSupportedPattern molecularAssemblerSupportedPattern) {
            return pushMeteoritePattern(molecularAssemblerSupportedPattern, patternDetails, inputHolder);
        }

        if (!isResonatingPatternDetails(patternDetails)) {
            return super.pushPattern(patternDetails, inputHolder);
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
                PatternProviderTarget target = PatternProviderTarget.get(level, adjacentPos, null, side.getOpposite(), this.actionSource);
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
        return true;
    }

    private boolean pushAe2LightningTechOverloadedPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!this.mainNode.isActive() || !getAvailablePatterns().contains(patternDetails)) {
            return false;
        }

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        double totalCost = getAe2LtTotalCost(inputHolder);
        if (!canAffordAe2LtTotalCost(totalCost)) {
            return false;
        }

        boolean pushed;
        if (isAe2LtWirelessMode()) {
            pushed = pushAe2LtWirelessPattern(patternDetails, inputHolder, totalCost);
        } else if (isDirectionalPattern(patternDetails)) {
            pushed = pushAdvancedAeDirectionalPattern(patternDetails, inputHolder);
            if (pushed) {
                consumeAe2LtTotalCost(totalCost);
            }
        } else {
            pushed = super.pushPattern(patternDetails, inputHolder);
            if (pushed) {
                consumeAe2LtTotalCost(totalCost);
            }
        }

        if (!pushed) {
            return false;
        }
        return true;
    }

    private boolean pushAe2LtWirelessPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, double totalCost) {
        flushAe2LtWirelessOverflow();
        if (!this.ae2ltWirelessSendList.isEmpty()) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }

        var orderedConnections = getOrderedWirelessConnections(level);
        if (orderedConnections.isEmpty()) {
            return false;
        }

        for (int i = 0; i < orderedConnections.size(); i++) {
            var connection = orderedConnections.get(i);
            var targetLevel = level.getServer().getLevel(connection.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(connection.pos())) {
                continue;
            }

            boolean pushed = isDirectionalPattern(patternDetails)
                    ? tryPushAdvancedDirectionalToWirelessConnection(patternDetails, inputHolder, connection, targetLevel)
                    : tryPushAe2LtWirelessConnection(patternDetails, inputHolder, connection, targetLevel);
            if (pushed) {
                this.localRoundRobinIndex += i + 1;
                consumeAe2LtTotalCost(totalCost);
                return true;
            }
        }

        return false;
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        super.addDrops(drops);

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
        super.clearContent();
        this.advancedDirectionalSendList.clear();
        this.advancedDirectionalMap.clear();
        this.advancedSendDirection = null;
        this.ae2ltWirelessSendList.clear();
        this.ae2ltWirelessSendConn = null;
        this.ae2ltAllowedOutputFilter = null;
        this.ae2ltOutputFilterDirty = true;
    }

    private boolean pushAdvancedAeDirectionalPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (hasAdvancedDirectionalWork() || super.isBusy() || !this.mainNode.isActive() || !getAvailablePatterns().contains(patternDetails)) {
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

            PatternProviderTarget target = PatternProviderTarget.get(level, adjacentPos, null, adjacentFace, this.actionSource);
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

    private boolean pushMeteoritePattern(IMolecularAssemblerSupportedPattern pattern, IPatternDetails details, KeyCounter[] inputHolder) {
        if (this.worksInRound >= METEORITE_MAX_WORKS_PER_ROUND || super.isBusy() || !this.mainNode.isActive()) {
            return false;
        }

        var blockEntity = this.host.getBlockEntity();
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return false;
        }

        if (!tryConsumeMeteoriteEnergy(METEORITE_ENERGY_PER_WORK)) {
            return false;
        }

        List<GenericStack> output = getMeteoritePatternOutput(pattern, inputHolder, level);
        if (output == null || output.isEmpty()) {
            return false;
        }

        boolean wasEmpty = this.craftedContents.isEmpty();

        for (GenericStack stack : output) {
            if (stack != null && stack.what() != null && stack.amount() > 0) {
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

    private boolean isAdvancedAeDirectionalPattern(IPatternDetails patternDetails) {
        return isAdvancedAeProviderSelected()
                && patternDetails != null
                && implementsAdvancedAePatternInterface(patternDetails)
                && hasDirectionalInputs(patternDetails);
    }

    private boolean isAe2LightningTechOverloadedPattern(IPatternDetails patternDetails) {
        return isAe2LightningTechOverloadedProviderSelected()
                && patternDetails != null
                && implementsNamedInterface(patternDetails, AE2LT_OVERLOADED_PATTERN_DETAILS_INTERFACE);
    }

    @SuppressWarnings("unchecked")
    private void rebuildPatternsIncludingAe2LtOverloadPatterns() {
        try {
            var patternsField = PatternProviderLogic.class.getDeclaredField("patterns");
            patternsField.setAccessible(true);
            var patternInputsField = PatternProviderLogic.class.getDeclaredField("patternInputs");
            patternInputsField.setAccessible(true);
            var patternInventoryField = PatternProviderLogic.class.getDeclaredField("patternInventory");
            patternInventoryField.setAccessible(true);

            List<IPatternDetails> patterns = (List<IPatternDetails>) patternsField.get(this);
            Set<AEKey> patternInputs = (Set<AEKey>) patternInputsField.get(this);
            AppEngInternalInventory patternInventory = (AppEngInternalInventory) patternInventoryField.get(this);

            patterns.clear();
            patternInputs.clear();

            var level = this.host.getBlockEntity().getLevel();
            for (int slot = 0; slot < patternInventory.size(); slot++) {
                ItemStack patternStack = patternInventory.getStackInSlot(slot);
                IPatternDetails details = PatternDetailsHelper.decodePattern(patternStack, level);
                if (details == null) {
                    continue;
                }

                patterns.add(details);
                for (var input : details.getInputs()) {
                    for (var possibleInput : input.getPossibleInputs()) {
                        patternInputs.add(possibleInput.what().dropSecondary());
                    }
                }
            }

            var mainNodeField = PatternProviderLogic.class.getDeclaredField("mainNode");
            mainNodeField.setAccessible(true);
            IManagedGridNode managedGridNode = (IManagedGridNode) mainNodeField.get(this);
            appeng.api.networking.crafting.ICraftingProvider.requestUpdate(managedGridNode);
            this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rebuild overload patterns for adaptive pattern provider", e);
        }
    }

    private boolean isAdvancedAeProviderSelected() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.isAdvancedAeProviderSelected();
    }

    private boolean isAe2LightningTechOverloadedProviderSelected() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.isAe2LightningTechOverloadedProviderSelected();
    }

    private boolean isMeteoritePatternProvider() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.isMeteoriteProviderSelected();
    }

    private boolean isAe2LtWirelessMode() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.isAe2LtWirelessMode();
    }

    private boolean isAe2LtAutoReturnEnabled() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.getAe2LtReturnMode()
                == AdaptivePatternProviderBlockEntity.Ae2LtReturnMode.AUTO;
    }

    private boolean isAe2LtEjectModeEnabled() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.getAe2LtReturnMode()
                == AdaptivePatternProviderBlockEntity.Ae2LtReturnMode.EJECT;
    }

    private boolean isDirectionalPattern(IPatternDetails patternDetails) {
        return hasDirectionalInputs(patternDetails);
    }

    private List<OverloadedPatternProviderBlockEntity.WirelessConnection> getOrderedWirelessConnections(ServerLevel level) {
        if (!(this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity)) {
            return List.of();
        }

        List<OverloadedPatternProviderBlockEntity.WirelessConnection> valid = new ArrayList<>();
        for (var conn : adaptivePatternProviderBlockEntity.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) {
                continue;
            }
            ServerLevel targetLevel = level.getServer().getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos()) || targetLevel.getBlockEntity(conn.pos()) == null) {
                continue;
            }
            valid.add(conn);
        }

        if (valid.isEmpty()) {
            return List.of();
        }

        int idx = Math.floorMod(this.localRoundRobinIndex, valid.size());
        if (idx == 0) {
            return valid;
        }

        ArrayList<OverloadedPatternProviderBlockEntity.WirelessConnection> ordered = new ArrayList<>(valid.size());
        ordered.addAll(valid.subList(idx, valid.size()));
        ordered.addAll(valid.subList(0, idx));
        return ordered;
    }

    private boolean tryPushAe2LtWirelessConnection(IPatternDetails patternDetails,
                                                   KeyCounter[] inputHolder,
                                                   OverloadedPatternProviderBlockEntity.WirelessConnection connection,
                                                   ServerLevel targetLevel) {
        MachineAdapter adapter = MachineAdapterRegistry.find(targetLevel, connection.pos());
        if (adapter == null || !adapter.canAccept(targetLevel, connection.pos(), connection.boundFace(), patternDetails)) {
            return false;
        }

        Set<AEKey> patternInputs = getPatternInputs();
        PushResult result = adapter.pushCopies(
                targetLevel,
                connection.pos(),
                connection.boundFace(),
                patternDetails,
                inputHolder,
                1,
                this.isBlocking(),
                patternInputs,
                this.actionSource
        );
        if (result.acceptedCopies() == 0) {
            return false;
        }

        if (!result.overflow().isEmpty()) {
            this.ae2ltWirelessSendList.addAll(result.overflow());
            this.ae2ltWirelessSendConn = connection;
        }

        invokePatternSuccess(patternDetails);
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.host.saveChanges();
        return true;
    }

    private boolean tryPushAdvancedDirectionalToWirelessConnection(IPatternDetails patternDetails,
                                                                   KeyCounter[] inputHolder,
                                                                   OverloadedPatternProviderBlockEntity.WirelessConnection connection,
                                                                   ServerLevel targetLevel) {
        BlockEntity targetBlockEntity = targetLevel.getBlockEntity(connection.pos());
        if (targetBlockEntity == null || !patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        HashMap<Direction, PatternProviderTarget> targetsByFace = new HashMap<>();
        Direction defaultFace = connection.boundFace();
        for (KeyCounter input : inputHolder) {
            for (var entry : input) {
                Direction dir = getAdvancedInputSide(patternDetails, entry.getKey());
                Direction face = dir != null ? dir : defaultFace;
                PatternProviderTarget target = targetsByFace.computeIfAbsent(face,
                        f -> PatternProviderTarget.get(targetLevel, connection.pos(), targetBlockEntity, f, this.actionSource));
                if (target == null || target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE) == 0) {
                    return false;
                }
            }
        }

        if (this.isBlocking()) {
            var anyTarget = targetsByFace.values().iterator().next();
            if (anyTarget.containsPatternInput(getPatternInputs())) {
                return false;
            }
        }

        List<GenericStack> overflow = new ArrayList<>();
        patternDetails.pushInputsToExternalInventory(inputHolder, (what, amount) -> {
            Direction dir = getAdvancedInputSide(patternDetails, what);
            Direction face = dir != null ? dir : defaultFace;
            PatternProviderTarget target = targetsByFace.get(face);
            long inserted = target == null ? 0 : target.insert(what, amount, Actionable.MODULATE);
            if (inserted < amount) {
                overflow.add(new GenericStack(what, amount - inserted));
            }
        });

        if (!overflow.isEmpty()) {
            this.ae2ltWirelessSendList.addAll(overflow);
            this.ae2ltWirelessSendConn = connection;
        }

        invokePatternSuccess(patternDetails);
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        this.host.saveChanges();
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<GenericStack> getSparseInputs(IPatternDetails patternDetails) {
        try {
            Method method = patternDetails.getClass().getMethod("getSparseInputs");
            Object result = method.invoke(patternDetails);
            return result instanceof List<?> list ? (List<GenericStack>) list : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Optional<ResolvedTarget> getResolvedTarget(IPatternDetails patternDetails, int sparseIndex) {
        try {
            Method targetMethod = patternDetails.getClass().getMethod("getTargetForSparseInputIndex", int.class);
            Object optionalObject = targetMethod.invoke(patternDetails, sparseIndex);
            if (!(optionalObject instanceof Optional<?> optional) || optional.isEmpty()) {
                return Optional.empty();
            }

            Object target = optional.get();
            Method posMethod = target.getClass().getMethod("pos");
            Method faceMethod = target.getClass().getMethod("face");

            Object globalPosObject = posMethod.invoke(target);
            if (!(globalPosObject instanceof GlobalPos globalPos)) {
                return Optional.empty();
            }

            Object faceObject = faceMethod.invoke(target);
            if (!(faceObject instanceof Direction face)) {
                return Optional.empty();
            }

            return Optional.of(new ResolvedTarget(globalPos, face));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean isBlockedByMode(PatternProviderTarget target) {
        return this.isBlocking() && target.containsPatternInput(getPatternInputs());
    }

    private Set<AEKey> getPatternInputs() {
        try {
            var field = PatternProviderLogic.class.getDeclaredField("patternInputs");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<AEKey> value = (Set<AEKey>) field.get(this);
            return value;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private void invokePatternSuccess(IPatternDetails patternDetails) {
        try {
            Method method = PatternProviderLogic.class.getDeclaredMethod("onPushPatternSuccess", IPatternDetails.class);
            method.setAccessible(true);
            method.invoke(this, patternDetails);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private Method findBaseMethod(String name) {
        try {
            Method method = PatternProviderLogic.class.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean invokeBaseDoWork() {
        try {
            return this.doWorkMethod != null && Boolean.TRUE.equals(this.doWorkMethod.invoke(this));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean invokeBaseHasWorkToDo() {
        try {
            return this.hasWorkToDoMethod != null && Boolean.TRUE.equals(this.hasWorkToDoMethod.invoke(this));
        } catch (Exception ignored) {
            return false;
        }
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
            PatternProviderTarget target = PatternProviderTarget.get(level, adjacentPos, null, targetSide, this.actionSource);
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
            PatternProviderTarget target = PatternProviderTarget.get(level, adjacentPos, null, targetSide, this.actionSource);
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

    private boolean flushAe2LtWirelessOverflow() {
        if (this.ae2ltWirelessSendConn == null) {
            this.ae2ltWirelessSendList.clear();
            return false;
        }

        if (!(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return false;
        }

        ServerLevel targetLevel = level.getServer().getLevel(this.ae2ltWirelessSendConn.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(this.ae2ltWirelessSendConn.pos())) {
            return false;
        }

        MachineAdapter adapter = MachineAdapterRegistry.find(targetLevel, this.ae2ltWirelessSendConn.pos());
        if (adapter == null) {
            return false;
        }

        boolean flushed = adapter.flushOverflow(
                targetLevel,
                this.ae2ltWirelessSendConn.pos(),
                this.ae2ltWirelessSendConn.boundFace(),
                this.ae2ltWirelessSendList,
                this.actionSource
        );

        if (flushed) {
            this.ae2ltWirelessSendConn = null;
            this.host.saveChanges();
        }

        return flushed;
    }

    private boolean hasAdvancedDirectionalWork() {
        return !this.advancedDirectionalSendList.isEmpty();
    }

    private boolean hasAe2LtWirelessOverflowWork() {
        return !this.ae2ltWirelessSendList.isEmpty();
    }

    private boolean hasAe2LtAutoReturnWork() {
        return isAe2LightningTechOverloadedProviderSelected() && isAe2LtAutoReturnEnabled();
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

    private double getAe2LtTotalCost(KeyCounter[] inputHolder) {
        try {
            Class<?> powerCostUtilClass = Class.forName(AE2LT_POWER_COST_UTIL_CLASS);
            Method totalCostMethod = powerCostUtilClass.getMethod("totalCost", KeyCounter[].class);
            Object result = totalCostMethod.invoke(null, (Object) inputHolder);
            return result instanceof Number number ? number.doubleValue() : 0.0D;
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private boolean canAffordAe2LtTotalCost(double totalCost) {
        try {
            Class<?> powerCostUtilClass = Class.forName(AE2LT_POWER_COST_UTIL_CLASS);
            Method canAffordMethod = powerCostUtilClass.getMethod("canAfford", appeng.api.networking.IGrid.class, double.class);
            Object result = canAffordMethod.invoke(null, getGrid(), totalCost);
            return !(result instanceof Boolean canAfford) || canAfford;
        } catch (Exception ignored) {
            return true;
        }
    }

    private void consumeAe2LtTotalCost(double totalCost) {
        try {
            Class<?> powerCostUtilClass = Class.forName(AE2LT_POWER_COST_UTIL_CLASS);
            Method consumeRawMethod = powerCostUtilClass.getMethod("consumeRaw", appeng.api.networking.IGrid.class, double.class);
            consumeRawMethod.invoke(null, getGrid(), totalCost);
        } catch (Exception ignored) {
        }
    }

    private boolean hasDirectionalInputs(IPatternDetails patternDetails) {
        try {
            Method method = patternDetails.getClass().getMethod("directionalInputsSet");
            return Boolean.TRUE.equals(method.invoke(patternDetails));
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private Direction getAdvancedInputSide(IPatternDetails patternDetails, AEKey key) {
        try {
            Method method = patternDetails.getClass().getMethod("getDirectionSideForInputKey", AEKey.class);
            Object result = method.invoke(patternDetails, key);
            return result instanceof Direction direction ? direction : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private Map<AEKey, Direction> getAdvancedDirectionMap(IPatternDetails patternDetails) {
        try {
            Method method = patternDetails.getClass().getMethod("getDirectionMap");
            Object result = method.invoke(patternDetails);
            return result instanceof Map<?, ?> map ? (Map<AEKey, Direction>) map : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void refreshAdaptivePatternTracking() {
        this.outputCache.clear();

        if (this.craftingWatcher != null) {
            this.craftingWatcher.reset();
        }

        for (IPatternDetails pattern : getAvailablePatterns()) {
            if (pattern == null) {
                continue;
            }

            if (this.craftingWatcher != null) {
                for (GenericStack output : pattern.getOutputs()) {
                    if (output == null || output.what() == null) {
                        continue;
                    }
                    this.craftingWatcher.add(output.what());
                    this.outputCache.add(output.what());
                }
            }
        }
    }

    private void tickAe2LtAutoReturn() {
        if (!hasAe2LtAutoReturnWork() || !this.mainNode.isActive()) {
            return;
        }

        if (!(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime == this.ae2ltLastAutoReturnTick) {
            return;
        }
        this.ae2ltLastAutoReturnTick = gameTime;

        Object allowedOutputFilter = getOrBuildAe2LtAllowedOutputFilter();
        if (allowedOutputFilter == null || isAe2LtAllowedOutputFilterEmpty(allowedOutputFilter)) {
            return;
        }

        if (isAe2LtWirelessMode()) {
            autoReturnAe2LtWireless(level, allowedOutputFilter);
        } else {
            autoReturnAe2LtNormal(level, allowedOutputFilter);
        }
    }

    private void autoReturnAe2LtNormal(ServerLevel level, Object allowedOutputFilter) {
        BlockPos providerPos = this.host.getBlockEntity().getBlockPos();
        for (Direction dir : this.host.getTargets()) {
            BlockPos targetPos = providerPos.relative(dir);
            MachineAdapter adapter = MachineAdapterRegistry.find(level, targetPos);
            if (adapter == null) {
                continue;
            }

            List<GenericStack> outputs = adapter.extractOutputs(level, targetPos, dir.getOpposite(), castAe2LtAllowedOutputFilter(allowedOutputFilter), this.actionSource);
            insertAe2LtOutputsToReturnInventory(outputs);
        }
    }

    private void autoReturnAe2LtWireless(ServerLevel level, Object allowedOutputFilter) {
        for (var conn : getOrderedWirelessConnections(level)) {
            ServerLevel targetLevel = level.getServer().getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                continue;
            }

            MachineAdapter adapter = MachineAdapterRegistry.find(targetLevel, conn.pos());
            if (adapter == null) {
                continue;
            }

            List<GenericStack> outputs = adapter.extractOutputs(
                    targetLevel,
                    conn.pos(),
                    conn.boundFace(),
                    castAe2LtAllowedOutputFilter(allowedOutputFilter),
                    this.actionSource
            );
            insertAe2LtOutputsToReturnInventory(outputs);
        }
    }

    private void insertAe2LtOutputsToReturnInventory(List<GenericStack> outputs) {
        if (outputs.isEmpty()) {
            return;
        }

        var grid = getGrid();
        for (var stack : outputs) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }

            long affordable = PowerCostUtil.maxAffordable(grid, stack.what(), stack.amount());
            if (affordable <= 0) {
                continue;
            }
            long inserted = getReturnInv().insert(stack.what(), affordable, Actionable.MODULATE, this.actionSource);
            if (inserted > 0) {
                PowerCostUtil.consume(grid, stack.what(), inserted);
            }
        }
    }

    public void onHostStateChanged() {
        refreshAe2LtEjectRegistrations();
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void refreshAe2LtEjectRegistrations() {
        if (!(this.host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
            return;
        }

        invalidateAe2LtCapabilities(EjectModeRegistry.unregisterAll(this.host.getBlockEntity(), true), level);

        if (!isAe2LtEjectModeEnabled() || !isAe2LtWirelessMode()) {
            return;
        }

        if (!(this.host instanceof AdaptivePatternProviderBlockEntity adaptive)) {
            return;
        }

        for (var conn : adaptive.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) {
                continue;
            }
            ServerLevel targetLevel = level.getServer().getLevel(conn.dimension());
            if (targetLevel == null) {
                continue;
            }

            BlockPos adjacentPos = conn.pos().relative(conn.boundFace());
            Direction queryFace = conn.boundFace().getOpposite();
            GhostOutputBlockEntity ghostBE = new GhostOutputBlockEntity(adjacentPos);
            ghostBE.setLevel(targetLevel);

            var entry = new EjectModeRegistry.EjectEntry(
                    new java.lang.ref.WeakReference<>(this.host.getBlockEntity()),
                    ghostBE,
                    level.dimension(),
                    this.host.getBlockEntity().getBlockPos()
            );

            EjectModeRegistry.register(targetLevel.dimension(), adjacentPos.asLong(), queryFace, entry);
            invalidateAe2LtCapability(targetLevel, adjacentPos);
        }
    }

    private void invalidateAe2LtCapabilities(List<EjectModeRegistry.DimPos> positions, ServerLevel sourceLevel) {
        var server = sourceLevel.getServer();
        for (var dp : positions) {
            ServerLevel targetLevel = server.getLevel(dp.dimension());
            if (targetLevel != null) {
                targetLevel.invalidateCapabilities(dp.pos());
            }
        }
    }

    private void invalidateAe2LtCapability(@Nullable ServerLevel level, BlockPos pos) {
        if (level != null) {
            level.invalidateCapabilities(pos);
        }
    }

    @Nullable
    private Object getOrBuildAe2LtAllowedOutputFilter() {
        if (!this.ae2ltOutputFilterDirty && this.ae2ltAllowedOutputFilter != null) {
            return this.ae2ltAllowedOutputFilter;
        }

        this.ae2ltAllowedOutputFilter = buildAe2LtAllowedOutputFilter();
        this.ae2ltOutputFilterDirty = false;
        return this.ae2ltAllowedOutputFilter;
    }

    @Nullable
    private Object buildAe2LtAllowedOutputFilter() {
        try {
            Class<?> filterClass = Class.forName(AE2LT_ALLOWED_OUTPUT_FILTER_CLASS);
            Object filter = filterClass.getConstructor().newInstance();
            Method allowStrict = filterClass.getMethod("allowStrict", AEKey.class);
            Method allowIdOnly = filterClass.getMethod("allowIdOnly", AEKey.class);

            for (IPatternDetails pattern : getAvailablePatterns()) {
                if (pattern == null) {
                    continue;
                }

                if (isAe2LightningTechOverloadedPattern(pattern)) {
                    List<GenericStack> ae2Outputs = pattern.getOutputs();
                    Object overloadDetails = pattern.getClass().getMethod("overloadPatternDetailsView").invoke(pattern);
                    @SuppressWarnings("unchecked")
                    List<Object> overloadOutputs = (List<Object>) overloadDetails.getClass().getMethod("outputs").invoke(overloadDetails);
                    int count = Math.min(ae2Outputs.size(), overloadOutputs.size());
                    for (int i = 0; i < count; i++) {
                        AEKey key = ae2Outputs.get(i).what();
                        Object outputSlot = overloadOutputs.get(i);
                        Object matchMode = outputSlot.getClass().getMethod("matchMode").invoke(outputSlot);
                        if ("ID_ONLY".equals(String.valueOf(matchMode))) {
                            allowIdOnly.invoke(filter, key);
                        } else {
                            allowStrict.invoke(filter, key);
                        }
                    }
                    continue;
                }

                for (GenericStack output : pattern.getOutputs()) {
                    if (output != null && output.what() != null) {
                        allowStrict.invoke(filter, output.what());
                    }
                }
            }

            return filter;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isAe2LtAllowedOutputFilterEmpty(Object filter) {
        try {
            Method isEmpty = filter.getClass().getMethod("isEmpty");
            Object result = isEmpty.invoke(filter);
            return !(result instanceof Boolean empty) || empty;
        } catch (Exception ignored) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private com.moakiee.ae2lt.logic.AllowedOutputFilter castAe2LtAllowedOutputFilter(Object filter) {
        return (com.moakiee.ae2lt.logic.AllowedOutputFilter) filter;
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

        @Nullable MEStorage gridInv = null;
        if (getGrid() != null) {
            gridInv = getGrid().getStorageService().getInventory();
        }

        var iterator = this.craftedContents.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry<AEKey> entry = iterator.next();
            AEKey key = entry.getKey();
            long remaining = entry.getLongValue();
            if (key == null || remaining <= 0) {
                iterator.remove();
                continue;
            }

            if (gridInv != null) {
                long inserted = gridInv.insert(key, remaining, Actionable.MODULATE, this.actionSource);
                remaining -= inserted;
            }

            if (remaining > 0) {
                long inserted = getReturnInv().insert(key, remaining, Actionable.MODULATE, this.actionSource);
                remaining -= inserted;
            }

            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private boolean isAdvancedAeFilteredImportEnabled() {
        return this.host instanceof AdaptivePatternProviderBlockEntity adaptivePatternProviderBlockEntity
                && adaptivePatternProviderBlockEntity.isAdvancedAeProviderSelected()
                && adaptivePatternProviderBlockEntity.isAdvancedAeFilteredImportEnabled();
    }

    private boolean tryConsumeMeteoriteEnergy(double energy) {
        var grid = getGrid();
        if (grid == null) {
            return false;
        }
        IEnergyService energyService = grid.getEnergyService();
        if (energyService == null) {
            return false;
        }

        double extracted = energyService.extractAEPower(energy, Actionable.MODULATE, PowerMultiplier.ONE);
        if (extracted + 1.0e-9 >= energy) {
            return true;
        }

        try {
            energyService.injectPower(extracted, Actionable.MODULATE);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void installExpandedReturnInventory() {
        try {
            var field = PatternProviderLogic.class.getDeclaredField("returnInv");
            field.setAccessible(true);
            field.set(this, new ExpandedReturnInventory(this::onReturnInventoryChanged, this));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to expand adaptive pattern provider return inventory", e);
        }
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
            if (owner instanceof PatternProviderLogicHost
                    || owner instanceof InterfaceLogicHost && otherNode.getGrid() != null && otherNode.getGrid().equals(this.mainNode.getGrid())) {
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
        if (!targetLevel.hasChunkAt(pos)) {
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

    private record ResolvedTarget(GlobalPos position, Direction face) {
    }

    private record MarkedInput(AEKey key, long amount, ResolvedTarget target) {
    }

    private record FallbackTarget(Direction direction, PatternProviderTarget target) {
    }

    private final class Ticker implements IGridTickable {
        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(
                    TickRates.Interface,
                    !invokeBaseHasWorkToDo() && craftedContents.isEmpty() && getReturnInv().isEmpty()
            );
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!mainNode.isActive()) {
                return TickRateModulation.SLEEP;
            }

            boolean couldDoWork = invokeBaseDoWork();
            couldDoWork = flushAe2LtWirelessOverflow() || couldDoWork;
            couldDoWork = flushAdvancedDirectionalSendList() || couldDoWork;
            tickAe2LtAutoReturn();
            int before = craftedContents.size();
            flushCraftedOutputs();
            boolean workedForCrafter = craftedContents.size() != before || before > 0;
            couldDoWork = couldDoWork || workedForCrafter;
            boolean hasWork = invokeBaseHasWorkToDo()
                    || hasAe2LtWirelessOverflowWork()
                    || hasAe2LtAutoReturnWork()
                    || hasAdvancedDirectionalWork()
                    || !craftedContents.isEmpty()
                    || !getReturnInv().isEmpty();
            return hasWork
                    ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                    : TickRateModulation.SLEEP;
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
        public void onCraftableChange(AEKey what) {
        }
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

            return this.logic.getOutputCache().stream().anyMatch(key::equals);
        }

        private static Runnable prepare(Runnable listener) {
            PREVIOUS_SLOT_COUNT.set(PatternProviderReturnInventory.NUMBER_OF_SLOTS);
            PatternProviderReturnInventory.NUMBER_OF_SLOTS = EXPANDED_RETURN_SLOTS;
            return listener;
        }
    }
}
