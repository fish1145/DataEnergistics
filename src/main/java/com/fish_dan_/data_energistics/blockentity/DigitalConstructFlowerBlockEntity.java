package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorageGroup;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.PatternBufferCompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuProfile;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonDeclaredCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPatternMatcher;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityAccessLease;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreCpuCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreCraftingCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalogImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouter;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouterImpl;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerCraftingStatus;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenuHost;
import com.fish_dan_.data_energistics.network.DigitalConstructFlowerAutoBuildTarget;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData.StorageSummary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.service.CraftingService;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DigitalConstructFlowerBlockEntity extends AENetworkedBlockEntity
                                               implements MultiBlockStatusProvider, CompartmentHost, DigitalConstructFlowerMenuHost {

    private static final int RECHECK_RADIUS = 24;
    private static final int RECHECK_INTERVAL_TICKS = 100;
    private static final String FORMED_TAG = "formed";
    private static final String MATCHED_POSITIONS_TAG = "matched_positions";
    private static final String LAST_FAILURE_REASON_TAG = "last_failure_reason";
    private static final String LAST_FAILURE_POSITION_TAG = "last_failure_position";
    private static final String CPU_STRUCTURE_FORMED_TAG = "cpu_structure_formed";
    private static final String CPU_STRUCTURE_MATCHED_BLOCK_COUNT_TAG = "cpu_structure_matched_block_count";
    private static final String CPU_LAST_FAILURE_REASON_TAG = "cpu_last_failure_reason";
    private static final String CPU_LAST_FAILURE_POSITION_TAG = "cpu_last_failure_position";
    private static final String CRAFTING_STRUCTURE_FORMED_TAG = "crafting_structure_formed";
    private static final String CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT_TAG = "crafting_structure_matched_block_count";
    private static final String CRAFTING_LAST_FAILURE_REASON_TAG = "crafting_last_failure_reason";
    private static final String CRAFTING_LAST_FAILURE_POSITION_TAG = "crafting_last_failure_position";
    private static final String CRAFTING_PATTERN_CORE_COUNT_TAG = "crafting_pattern_core_count";
    private static final String CRAFTING_PATTERN_CAPACITY_TAG = "crafting_pattern_capacity";
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int SCHEMA_VERSION = 1;
    private static final String CRAFTING_RUNTIME_TAG = "trinity_data_core_crafting_runtime";
    private static final String STORAGE_ID_TAG = "trinity_data_core_storage_id";
    private static final String HOST_ID_TAG = "trinity_data_core_host_id";
    private static final String NO_FAILURE = "";
    private static final String MAIN_STRUCTURE_NOT_FORMED = "Main structure is not formed";
    private static final String CPU_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME;
    private static final String CRAFTING_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME;
    private static final int MAIN_STORAGE_CORE_SLOT_COUNT = 1_176;
    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private UUID storageId = UUID.randomUUID();
    private UUID hostId = UUID.randomUUID();
    private TrinityPatternCatalog patternCatalog = new TrinityPatternCatalogImpl(this.hostId);
    private final TrinityPatternOutputRouter patternOutputRouter = new TrinityPatternOutputRouterImpl();
    private boolean patternCatalogValid;
    private boolean formed;
    private List<BlockPos> matchedPositions = List.of();
    private TrinityDataCoreStorageProfile storageProfile = TrinityDataCoreStorageProfile.EMPTY;
    private String lastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos lastFailurePosition;
    @Nullable
    private TrinityDataCoreCpuContribution cpuStructureContribution;
    private boolean cpuStructureFormed;
    private int cpuStructureMatchedBlockCount;
    private String cpuLastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos cpuLastFailurePosition;
    private boolean craftingStructureFormed;
    private int craftingStructureMatchedBlockCount;
    private TrinityDataCoreCraftingCoreProfile craftingProfile = TrinityDataCoreCraftingCoreProfile.EMPTY;
    private String craftingLastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos craftingLastFailurePosition;
    private boolean recheckRequested = true;
    private final CompartmentHostState compartmentHostState = new CompartmentHostState();
    private final JsonMultiBlockCompartmentBinder compartmentBinder = new JsonDeclaredCompartmentBinder();
    private final CompartmentStorage patternBufferStorageView = new CompartmentStorageGroup(this::recognizedPatternBufferStorages);
    private final TrinityDataCoreCraftingRuntime craftingRuntime = new TrinityDataCoreCraftingRuntime(this);
    @Nullable
    private TrinityAccessLease accessLease;
    private long accessLeaseEpoch;

    public DigitalConstructFlowerBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DIGITAL_CONSTRUCT_FLOWER_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get())
                .setExposedOnSides(Set.of())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.craftingRuntime.restorePendingPartitionLogic(level.registryAccess());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.NONE;
    }

    @Override
    public void onReady() {
        super.onReady();
        requireMainJsonDefinitionKey();
        requestStructureRecheck();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        try {
            tickServerState();
        } catch (RuntimeException exception) {
            this.craftingRuntime.setPaused(true);
            LOGGER.error("Failed to tick Trinity Data Core at {}; runtime was paused", this.worldPosition, exception);
        }
    }

    private void tickServerState() {
        boolean periodicRecheck = Math.floorMod(
                this.level.getGameTime() + this.worldPosition.asLong(),
                RECHECK_INTERVAL_TICKS) == 0;
        if (this.recheckRequested || periodicRecheck) {
            this.recheckRequested = false;
            updateStructureMatch(this.level);
        }
        reevaluateAccessLease();
        if (this.patternCatalogValid && this.patternCatalog.refreshChangedPatterns()) {
            notifyTrinityAccessChanged();
        }
        tickOwnedPatternCores();
    }

    @Override
    public boolean isOnline() {
        return hasActiveAccessHatch();
    }

    @Override
    public boolean multiBlock$isOnline() {
        return isOnline();
    }

    @Override
    public boolean isStructureFormed() {
        return this.formed;
    }

    @Override
    public boolean multiBlock$isFormed() {
        return isStructureFormed();
    }

    @Override
    public boolean multiBlock$isController() {
        return this.formed;
    }

    @Override
    public int multiBlock$getHeight() {
        return 0;
    }

    @Override
    public int multiBlock$getMatchedBlockCount() {
        return getMatchedBlockCount();
    }

    @Override
    public int getMatchedBlockCount() {
        return this.matchedPositions.size();
    }

    @Override
    public int getPatternBufferCount() {
        return patternBuffers().size();
    }

    @Override
    public boolean isCpuStructureFormed() {
        return this.cpuStructureFormed;
    }

    @Override
    public int getCpuStructureMatchedBlockCount() {
        return this.cpuStructureMatchedBlockCount;
    }

    @Override
    public String getCpuLastFailureReason() {
        return this.cpuLastFailureReason;
    }

    @Override
    public @Nullable BlockPos getCpuLastFailurePosition() {
        return this.cpuLastFailurePosition;
    }

    @Override
    public boolean isCraftingStructureFormed() {
        return this.craftingStructureFormed;
    }

    @Override
    public int getCraftingStructureMatchedBlockCount() {
        return this.craftingStructureMatchedBlockCount;
    }

    @Override
    public int getCraftingPatternCoreCount() {
        return this.craftingProfile.patternCoreCount();
    }

    @Override
    public int getCraftingPatternCapacity() {
        return this.craftingProfile.patternCapacity();
    }

    @Override
    public String getCraftingLastFailureReason() {
        return this.craftingLastFailureReason;
    }

    @Override
    public @Nullable BlockPos getCraftingLastFailurePosition() {
        return this.craftingLastFailurePosition;
    }

    public boolean isCraftingAvailable() {
        return this.formed && this.craftingStructureFormed && this.craftingProfile.active();
    }

    /**
     * Returns whether the lease owner may publish the aggregated crafting patterns to AE2.
     */
    public boolean isPatternProviderAvailable() {
        return canExposeTrinityCapabilities() && this.patternCatalogValid && this.craftingProfile.active();
    }

    /**
     * Returns whether all three structures are valid, allowing the lease owner to expose storage, CPUs, and patterns.
     */
    public boolean canExposeTrinityCapabilities() {
        return isTrinityRuntimeAvailable();
    }

    /**
     * Returns the stable crafting identity, which is deliberately independent from the saved-data storage UUID.
     */
    public UUID getHostId() {
        return this.hostId;
    }

    /**
     * Returns the aggregate consumed by the lease-owning access hatch's AE2 provider.
     */
    public TrinityPatternCatalog getPatternCatalog() {
        return this.patternCatalog;
    }

    public List<BlockPos> getMatchedPositions() {
        return this.matchedPositions;
    }

    @Override
    public String getLastFailureReason() {
        return this.lastFailureReason;
    }

    @Override
    public String multiBlock$getLastFailureReason() {
        return getLastFailureReason();
    }

    @Override
    public @Nullable BlockPos getLastFailurePosition() {
        return this.lastFailurePosition;
    }

    @Override
    public @Nullable BlockPos multiBlock$getLastFailurePosition() {
        return getLastFailurePosition();
    }

    public void requestStructureRecheck() {
        this.recheckRequested = true;
    }

    public void autoBuildTrinityStructure(ServerPlayer player, DigitalConstructFlowerAutoBuildTarget target) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        JsonMultiBlockDefinition mainDefinition = requireMainJsonDefinition();
        JsonMultiBlockDefinition targetDefinition = autoBuildDefinition(target);
        StructureWorldView world = new LevelStructureWorldView(serverLevel);
        AutoBuildOrientation orientation = resolveAutoBuildOrientation(mainDefinition.pattern(), world, serverLevel);
        DigitalConstructFlowerAutoBuild.Stats stats = DigitalConstructFlowerAutoBuild.buildPattern(
                serverLevel,
                player,
                world,
                targetDefinition.pattern(),
                this.worldPosition,
                autoBuildStructureName(target),
                orientation.front(),
                orientation.flipped());

        requestStructureRecheck();
        player.displayClientMessage(
                Component.translatable(
                        "message.data_energistics.trinity_data_core.auto_build",
                        target.targetName(),
                        stats.placed(),
                        stats.missing(),
                        stats.blocked(),
                        stats.unloaded(),
                        stats.placeFailed()),
                true);
    }

    /**
     * Returns the main structure's aggregate input view without exposing concrete compartment block entities.
     */
    public CompartmentStorage compartmentInputStorage() {
        if (!this.formed) {
            return UnavailableCompartmentStorage.INSTANCE;
        }
        return compartmentHost$inputStorage(mainDefinitionKey().structureName());
    }

    /**
     * Returns the main structure's aggregate output view without exposing concrete compartment block entities.
     */
    public CompartmentStorage compartmentOutputStorage() {
        if (!this.formed) {
            return UnavailableCompartmentStorage.INSTANCE;
        }
        return compartmentHost$outputStorage(mainDefinitionKey().structureName());
    }

    /**
     * Returns the crafting child structure's recognized pattern buffer view without exposing concrete compartment block
     * entities.
     */
    public CompartmentStorage patternBufferStorage() {
        return this.patternBufferStorageView;
    }

    /**
     * Returns main structure pattern buffers that are visible to the current crafting child structure.
     */
    public Collection<PatternBufferCompartmentPart> patternBuffers() {
        return recognizedPatternBuffers();
    }

    private Collection<PatternBufferCompartmentPart> recognizedPatternBuffers() {
        if (!isCraftingAvailable()) {
            return List.of();
        }
        int remainingCapacity = this.craftingProfile.patternCapacity();
        List<PatternBufferCompartmentPart> patternBuffers = new ArrayList<>();
        for (PatternBufferCompartmentPart patternBuffer : compartmentHost$getPatternBuffers(mainDefinitionKey().structureName())) {
            int slotCount = patternBuffer.patternBufferSlotCount();
            if (slotCount < 0) {
                throw new IllegalStateException("Pattern buffer slot count must not be negative");
            }
            if (slotCount == 0) {
                continue;
            }
            if (remainingCapacity <= 0) {
                break;
            }
            patternBuffers.add(patternBuffer);
            remainingCapacity -= Math.min(remainingCapacity, slotCount);
        }
        return List.copyOf(patternBuffers);
    }

    private Collection<CompartmentStorage> recognizedPatternBufferStorages() {
        if (!isCraftingAvailable()) {
            return List.of();
        }
        int remainingCapacity = this.craftingProfile.patternCapacity();
        List<CompartmentStorage> storages = new ArrayList<>();
        for (PatternBufferCompartmentPart patternBuffer : compartmentHost$getPatternBuffers(mainDefinitionKey().structureName())) {
            int slotCount = patternBuffer.patternBufferSlotCount();
            if (slotCount < 0) {
                throw new IllegalStateException("Pattern buffer slot count must not be negative");
            }
            for (int slot = 0; slot < slotCount && remainingCapacity > 0; slot++) {
                storages.add(patternBuffer.patternBufferStorage(slot));
                remainingCapacity--;
            }
            if (remainingCapacity <= 0) {
                break;
            }
        }
        return List.copyOf(storages);
    }

    public UUID getStorageId() {
        return this.storageId;
    }

    public TrinityDataCoreStorageProfile storageProfile() {
        return this.storageProfile;
    }

    public void restoreStorageIdFromItem(ItemStack stack) {
        UUID itemStorageId = stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID);
        if (itemStorageId != null) {
            setStorageId(itemStorageId);
        }
    }

    public void saveStorageIdToItem(ItemStack stack) {
        stack.set(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID, this.storageId);
    }

    /** Restores the stable crafting host identity carried by a moved Trinity host item. */
    public void restoreHostIdFromItem(ItemStack stack) {
        UUID itemHostId = stack.get(ModDataComponents.TRINITY_DATA_CORE_HOST_ID);
        if (itemHostId != null) {
            setHostId(itemHostId);
        }
    }

    /** Saves the crafting host identity independently from the legacy main-storage identity. */
    public void saveHostIdToItem(ItemStack stack) {
        stack.set(ModDataComponents.TRINITY_DATA_CORE_HOST_ID, this.hostId);
    }

    @Override
    public int getStoredTypeCount() {
        return storageSummary().typeCount();
    }

    @Override
    public String getStoredAmountText() {
        return storageSummary().totalAmount();
    }

    @Override
    public String getStoredTypeCapacityText() {
        return storageCapacityText(Integer.toString(this.storageProfile.typeCapacity()));
    }

    @Override
    public String getStoredAmountCapacityText() {
        return storageCapacityText(this.storageProfile.totalCapacity().toString());
    }

    @Override
    public int getCpuPartitionCount() {
        return this.craftingRuntime.profile().partitionCount();
    }

    @Override
    public int getBusyCpuPartitionCount() {
        int busyPartitions = 0;
        for (TrinityDataCoreVirtualCpu cpu : this.craftingRuntime.partitions()) {
            if (cpu.isBusy()) {
                busyPartitions++;
            }
        }
        return busyPartitions;
    }

    @Override
    public long getCpuStorageBytes() {
        return this.craftingRuntime.profile().storageBytes();
    }

    @Override
    public int getCpuCoProcessors() {
        return this.craftingRuntime.profile().coProcessors();
    }

    /**
     * Replaces CPU data contributed by a named child structure.
     *
     * @param structureName child structure name that owns the contribution
     * @param contribution  CPU data contributed by the child structure
     */
    public void setCpuContribution(String structureName, TrinityDataCoreCpuContribution contribution) {
        this.craftingRuntime.setContribution(structureName, contribution);
        notifyTrinityAccessChanged();
        setChanged();
    }

    /**
     * Clears CPU data contributed by a named child structure.
     *
     * @param structureName child structure name to remove
     */
    public void clearCpuContribution(String structureName) {
        this.craftingRuntime.clearContribution(structureName);
        notifyTrinityAccessChanged();
        setChanged();
    }

    /**
     * @return virtual CPU partitions currently exposed by this formed structure
     */
    public List<TrinityDataCoreVirtualCpu> getCpuPartitions() {
        return this.craftingRuntime.partitions();
    }

    /**
     * @return crafting runtime used by AE2 CraftingService mixins
     */
    public TrinityDataCoreCraftingRuntime getCraftingRuntime() {
        return this.craftingRuntime;
    }

    @Override
    public DigitalConstructFlowerCraftingStatus getCraftingStatus() {
        IGrid grid = accessGrid();
        if (grid == null) {
            return DigitalConstructFlowerCraftingStatus.EMPTY;
        }

        int busyCpuCount = 0;
        CraftingJobStatus selectedJob = null;
        for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
            if (!cpu.isBusy()) {
                continue;
            }

            busyCpuCount++;
            CraftingJobStatus jobStatus = cpu.getJobStatus();
            if (jobStatus == null || !hasCraftingTarget(jobStatus.crafting())) {
                continue;
            }
            if (selectedJob == null || jobStatus.elapsedTimeNanos() > selectedJob.elapsedTimeNanos()) {
                selectedJob = jobStatus;
            }
        }

        TrinityDataCoreCpuProfile profile = this.craftingRuntime.profile();
        GenericStack target = selectedJob == null ? null : selectedJob.crafting();
        return new DigitalConstructFlowerCraftingStatus(
                target,
                busyCpuCount,
                profile.partitionCount(),
                getBusyCpuPartitionCount(),
                profile.storageBytes(),
                profile.coProcessors());
    }

    private static boolean hasCraftingTarget(@Nullable GenericStack stack) {
        return stack != null && stack.amount() > 0;
    }

    private StorageSummary storageSummary() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return StorageSummary.EMPTY;
        }
        return TrinityDataCoreStorageSavedData.get(serverLevel.getServer()).summary(this.storageId);
    }

    private String storageCapacityText(String finiteCapacity) {
        return this.storageProfile.unlimited() ? DigitalConstructFlowerMenuHost.UNLIMITED_STORAGE_CAPACITY : finiteCapacity;
    }

    public boolean hasActiveAccessHatch() {
        reevaluateAccessLease();
        return canExposeTrinityCapabilities() && activeLeaseHatch() != null;
    }

    public @Nullable IGrid accessGrid() {
        reevaluateAccessLease();
        if (!canExposeTrinityCapabilities()) {
            return null;
        }
        TrinityAccessHatchBlockEntity hatch = activeLeaseHatch();
        return hatch == null ? null : hatch.connectedGrid();
    }

    public IActionSource accessActionSource() {
        reevaluateAccessLease();
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch && isLeaseOwner(hatch)) {
                return hatch.actionSource();
            }
        }
        throw new IllegalStateException("Trinity Digital Core has no active Trinity access hatch");
    }

    public boolean isLeaseOwner(TrinityAccessHatchBlockEntity hatch) {
        return this.accessLease != null && this.accessLease.matches(hatch.getBlockPos(), hatch.connectedGrid());
    }

    public void requestAccessLeaseReevaluation() {
        reevaluateAccessLease();
    }

    private void reevaluateAccessLease() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        List<TrinityAccessHatchBlockEntity> candidates = compartmentHost$getCompartments(mainDefinitionKey().structureName()).stream()
                .filter(TrinityAccessHatchBlockEntity.class::isInstance)
                .map(TrinityAccessHatchBlockEntity.class::cast)
                .filter(TrinityAccessHatchBlockEntity::isCandidateOnline)
                .sorted((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .toList();

        if (this.accessLease != null) {
            boolean currentStillValid = candidates.stream().anyMatch(this::isLeaseOwner);
            if (currentStillValid || hasPendingTrinityWork()) {
                this.craftingRuntime.setPaused(!currentStillValid || !isTrinityRuntimeAvailable());
                return;
            }
        }

        TrinityAccessLease previous = this.accessLease;
        this.accessLease = candidates.isEmpty() ? null : new TrinityAccessLease(
                candidates.getFirst().getBlockPos(),
                candidates.getFirst().connectedGrid(),
                ++this.accessLeaseEpoch);
        this.craftingRuntime.setPaused(this.accessLease == null || !isTrinityRuntimeAvailable());
        if (!Objects.equals(previous, this.accessLease)) {
            notifyTrinityAccessChanged();
        }
    }

    private boolean hasPendingTrinityWork() {
        return this.craftingRuntime.hasBusyJobs() || this.patternCatalog.hasWork();
    }

    private boolean isTrinityRuntimeAvailable() {
        return this.formed && this.cpuStructureFormed && this.craftingStructureFormed;
    }

    @Nullable
    private TrinityAccessHatchBlockEntity activeLeaseHatch() {
        if (this.accessLease == null) {
            return null;
        }
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch && hatch.isCandidateOnline() && isLeaseOwner(hatch)) {
                return hatch;
            }
        }
        return null;
    }

    private void tickOwnedPatternCores() {
        if (!isPatternProviderAvailable() || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        TrinityAccessHatchBlockEntity hatch = activeLeaseHatch();
        IGrid grid = hatch == null ? null : hatch.connectedGrid();
        if (grid == null) {
            return;
        }
        if (!(grid.getCraftingService() instanceof CraftingService craftingService)) {
            LOGGER.error("Trinity host {} cannot route pattern outputs because its AE crafting service is unsupported",
                    this.worldPosition);
            return;
        }

        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(serverLevel.getServer());
        boolean routedAnyOutput = false;
        for (TrinityPatternCatalog.CoreMount mount : this.patternCatalog.mountedCores()) {
            if (!(mount.core() instanceof TrinityPatternCoreBlockEntity patternCore)) {
                LOGGER.error("Trinity host {} catalog contains a non-block pattern core at {}",
                        this.worldPosition,
                        mount.position());
                continue;
            }
            try {
                patternCore.executeOwnedBatches(this.hostId, this.level.getGameTime());
                routedAnyOutput |= routePendingOutputs(patternCore, craftingService, storage);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to execute or route Trinity pattern core {} at {} for host {}",
                        patternCore.coreId(),
                        mount.position(),
                        this.worldPosition,
                        exception);
            }
        }
        if (routedAnyOutput) {
            notifyTrinityAccessChanged();
        }
    }

    private boolean routePendingOutputs(TrinityPatternCoreBlockEntity core,
                                        CraftingService craftingService,
                                        TrinityDataCoreStorageSavedData storage) {
        boolean changed = false;
        for (int slot = 0; slot < core.patternCapacity(); slot++) {
            PatternRoute route = new PatternRoute(this.hostId, core.coreId(), slot);
            List<ItemStack> pending = core.pendingOutputs(route);
            if (pending.isEmpty()) {
                continue;
            }
            boolean[] checkpointed = { false };
            try {
                this.patternOutputRouter.route(
                        pending,
                        craftingService::getRequestedAmount,
                        craftingService::insertIntoCpus,
                        (key, amount, mode) -> storage.insert(
                                this.storageId,
                                key,
                                amount,
                                mode,
                                this.storageProfile),
                        remaining -> {
                            core.replacePendingOutputs(route, remaining);
                            checkpointed[0] = true;
                        });
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to route Trinity pattern core {} slot {} outputs for host {}",
                        core.coreId(),
                        slot,
                        this.worldPosition,
                        exception);
            }
            changed |= checkpointed[0];
        }
        return changed;
    }

    private void setStorageId(UUID storageId) {
        if (this.storageId.equals(storageId)) {
            return;
        }
        this.storageId = storageId;
        setChanged();
    }

    private void setHostId(UUID hostId) {
        if (this.hostId.equals(hostId)) {
            return;
        }
        this.hostId = hostId;
        this.patternCatalog = new TrinityPatternCatalogImpl(hostId);
        this.patternCatalogValid = false;
        requestStructureRecheck();
        setChanged();
    }

    public static void requestRecheckAround(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel) || level.isClientSide()) {
            return;
        }

        BlockPos min = origin.offset(-RECHECK_RADIUS, -RECHECK_RADIUS, -RECHECK_RADIUS);
        BlockPos max = origin.offset(RECHECK_RADIUS, RECHECK_RADIUS, RECHECK_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DigitalConstructFlowerBlockEntity flower) {
                flower.requestStructureRecheck();
            }
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            discardPersistedTrinityState();
            LOGGER.warn("Ignoring Trinity Data Core block entity data without a schema version at {}", this.worldPosition);
            return;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != SCHEMA_VERSION) {
            discardPersistedTrinityState();
            LOGGER.warn(
                    "Ignoring Trinity Data Core block entity schema version {} at {}; expected {}",
                    schemaVersion,
                    this.worldPosition,
                    SCHEMA_VERSION);
            return;
        }
        if (!data.hasUUID(STORAGE_ID_TAG) || !data.hasUUID(HOST_ID_TAG)) {
            discardPersistedTrinityState();
            LOGGER.warn("Ignoring Trinity Data Core block entity data with missing identities at {}", this.worldPosition);
            return;
        }
        this.storageId = data.getUUID(STORAGE_ID_TAG);
        this.hostId = data.getUUID(HOST_ID_TAG);
        this.patternCatalog = new TrinityPatternCatalogImpl(this.hostId);
        this.patternCatalogValid = false;
        this.formed = data.getBoolean(FORMED_TAG);
        this.matchedPositions = readMatchedPositions(data);
        this.lastFailureReason = data.getString(LAST_FAILURE_REASON_TAG);
        if (data.contains(LAST_FAILURE_POSITION_TAG)) {
            this.lastFailurePosition = BlockPos.of(data.getLong(LAST_FAILURE_POSITION_TAG));
        } else {
            this.lastFailurePosition = null;
        }
        this.cpuStructureFormed = data.getBoolean(CPU_STRUCTURE_FORMED_TAG);
        this.cpuStructureMatchedBlockCount = data.getInt(CPU_STRUCTURE_MATCHED_BLOCK_COUNT_TAG);
        this.cpuLastFailureReason = data.getString(CPU_LAST_FAILURE_REASON_TAG);
        if (data.contains(CPU_LAST_FAILURE_POSITION_TAG)) {
            this.cpuLastFailurePosition = BlockPos.of(data.getLong(CPU_LAST_FAILURE_POSITION_TAG));
        } else {
            this.cpuLastFailurePosition = null;
        }
        this.craftingStructureFormed = data.getBoolean(CRAFTING_STRUCTURE_FORMED_TAG);
        this.craftingStructureMatchedBlockCount = data.getInt(CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT_TAG);
        this.craftingProfile = readCraftingProfile(data);
        this.craftingLastFailureReason = data.getString(CRAFTING_LAST_FAILURE_REASON_TAG);
        if (data.contains(CRAFTING_LAST_FAILURE_POSITION_TAG)) {
            this.craftingLastFailurePosition = BlockPos.of(data.getLong(CRAFTING_LAST_FAILURE_POSITION_TAG));
        } else {
            this.craftingLastFailurePosition = null;
        }
        this.craftingRuntime.setMainStructureFormed(this.formed);
        if (data.contains(CRAFTING_RUNTIME_TAG, Tag.TAG_COMPOUND)) {
            this.craftingRuntime.readFromTag(data.getCompound(CRAFTING_RUNTIME_TAG), registries);
        } else {
            this.craftingRuntime.discardPersistedState();
            LOGGER.warn("Trinity Data Core block entity is missing crafting runtime data at {}", this.worldPosition);
        }
        if (!data.contains(CPU_STRUCTURE_FORMED_TAG)) {
            this.cpuStructureFormed = this.craftingRuntime.hasContribution(CPU_STRUCTURE_NAME);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        data.putUUID(STORAGE_ID_TAG, this.storageId);
        data.putUUID(HOST_ID_TAG, this.hostId);
        data.putBoolean(FORMED_TAG, this.formed);
        data.put(MATCHED_POSITIONS_TAG, createMatchedPositionsTag());
        data.putString(LAST_FAILURE_REASON_TAG, this.lastFailureReason);
        if (this.lastFailurePosition != null) {
            data.putLong(LAST_FAILURE_POSITION_TAG, this.lastFailurePosition.asLong());
        }
        data.putBoolean(CPU_STRUCTURE_FORMED_TAG, this.cpuStructureFormed);
        data.putInt(CPU_STRUCTURE_MATCHED_BLOCK_COUNT_TAG, this.cpuStructureMatchedBlockCount);
        data.putString(CPU_LAST_FAILURE_REASON_TAG, this.cpuLastFailureReason);
        if (this.cpuLastFailurePosition != null) {
            data.putLong(CPU_LAST_FAILURE_POSITION_TAG, this.cpuLastFailurePosition.asLong());
        }
        data.putBoolean(CRAFTING_STRUCTURE_FORMED_TAG, this.craftingStructureFormed);
        data.putInt(CRAFTING_STRUCTURE_MATCHED_BLOCK_COUNT_TAG, this.craftingStructureMatchedBlockCount);
        data.putInt(CRAFTING_PATTERN_CORE_COUNT_TAG, this.craftingProfile.patternCoreCount());
        data.putInt(CRAFTING_PATTERN_CAPACITY_TAG, this.craftingProfile.patternCapacity());
        data.putString(CRAFTING_LAST_FAILURE_REASON_TAG, this.craftingLastFailureReason);
        if (this.craftingLastFailurePosition != null) {
            data.putLong(CRAFTING_LAST_FAILURE_POSITION_TAG, this.craftingLastFailurePosition.asLong());
        }
        CompoundTag runtimeTag = new CompoundTag();
        this.craftingRuntime.writeToTag(runtimeTag, registries);
        data.put(CRAFTING_RUNTIME_TAG, runtimeTag);
    }

    private void discardPersistedTrinityState() {
        this.storageId = UUID.randomUUID();
        this.hostId = UUID.randomUUID();
        this.patternCatalog = new TrinityPatternCatalogImpl(this.hostId);
        this.patternCatalogValid = false;
        this.formed = false;
        this.matchedPositions = List.of();
        this.storageProfile = TrinityDataCoreStorageProfile.EMPTY;
        this.lastFailureReason = NO_FAILURE;
        this.lastFailurePosition = null;
        this.cpuStructureContribution = null;
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = NO_FAILURE;
        this.cpuLastFailurePosition = null;
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingProfile = TrinityDataCoreCraftingCoreProfile.EMPTY;
        this.craftingLastFailureReason = NO_FAILURE;
        this.craftingLastFailurePosition = null;
        this.craftingRuntime.setMainStructureFormed(false);
        this.craftingRuntime.discardPersistedState();
        this.recheckRequested = true;
    }

    private void updateStructureMatch(Level level) {
        JsonMultiBlockDefinition definition = requireMainJsonDefinition();
        Direction preferredFrontFacing = getStructureFrontFacing(level);
        StructureWorldView world = new LevelStructureWorldView(level);
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                definition.pattern(),
                world,
                this.worldPosition,
                preferredFrontFacing,
                mainDefinitionKey().structureName());

        if (result.matched()) {
            Map<BlockPos, CompartmentType> declaredCompartments = JsonMultiBlockCompartmentPredicate.declaredCompartments(
                    result.context());
            PatternDiagnostic compartmentFailure = this.compartmentBinder.validate(world, result, declaredCompartments);
            if (compartmentFailure != null) {
                applyFailure(compartmentFailure, mainDefinitionKey().structureName());
                return;
            }
            applyMatch(world, result.positions(), declaredCompartments, mainDefinitionKey().structureName());
            boolean childStructureFlipped = result.flipped();
            updateCpuStructureMatch(world, result.frontFacing(), childStructureFlipped);
            updateCraftingStructureMatch(world, result.frontFacing(), childStructureFlipped);
        } else {
            applyFailure(result.diagnostic(), mainDefinitionKey().structureName());
        }
    }

    private void updateCpuStructureMatch(StructureWorldView world,
                                         Direction mainStructureFrontFacing,
                                         boolean mainStructureFlipped) {
        JsonMultiBlockDefinition definition = requireCpuJsonDefinition();
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                definition.pattern(),
                world,
                this.worldPosition,
                mainStructureFrontFacing,
                mainStructureFlipped,
                CPU_STRUCTURE_NAME);

        if (result.matched()) {
            applyCpuMatch(world, result.positions());
        } else {
            applyCpuFailure(result.diagnostic());
        }
    }

    private void updateCraftingStructureMatch(StructureWorldView world,
                                              Direction mainStructureFrontFacing,
                                              boolean mainStructureFlipped) {
        JsonMultiBlockDefinition definition = requireCraftingJsonDefinition();
        StructureMatchResult result = JsonMultiBlockPatternMatcher.matchExact(
                definition.pattern(),
                world,
                this.worldPosition,
                mainStructureFrontFacing,
                mainStructureFlipped,
                CRAFTING_STRUCTURE_NAME);

        if (result.matched()) {
            PatternCatalogScanResult scan = scanPatternCores(world, result.positions());
            if (!scan.valid()) {
                this.patternCatalogValid = false;
                applyCraftingFailure(scan.failureReason(), scan.failurePosition());
                return;
            }
            TrinityPatternCatalog.RebuildResult rebuild = this.patternCatalog.rebuild(scan.mounts());
            this.patternCatalogValid = rebuild.valid();
            if (!rebuild.valid()) {
                applyCraftingFailure(rebuild.failureReason(), rebuild.failurePosition());
                return;
            }
            applyCraftingMatch(world, result.positions(), rebuild.changed());
        } else {
            applyCraftingFailure(result.diagnostic());
        }
    }

    private AutoBuildOrientation resolveAutoBuildOrientation(BlockPattern pattern,
                                                             StructureWorldView world,
                                                             ServerLevel serverLevel) {
        Direction preferredFront = getStructureFrontFacing(serverLevel);
        StructureMatchResult result = JsonMultiBlockPatternMatcher.match(
                pattern,
                world,
                this.worldPosition,
                preferredFront,
                mainDefinitionKey().structureName());
        if (result.matched()) {
            return new AutoBuildOrientation(result.frontFacing(), result.flipped());
        }
        return new AutoBuildOrientation(preferredFront, false);
    }

    private Direction getStructureFrontFacing(Level level) {
        BlockState state = level.getBlockState(this.worldPosition);
        return JsonMultiBlockFrontFacing.fromPlacedHost(
                state,
                DataRipperReassemblerBlock.FACING,
                this.worldPosition,
                "Trinity Data Core");
    }

    private void applyMatch(StructureWorldView world,
                            List<BlockPos> positions,
                            Map<BlockPos, CompartmentType> declaredCompartments,
                            String structureName) {
        List<BlockPos> nextPositions = List.copyOf(positions);
        TrinityDataCoreStorageProfile nextStorageProfile = buildStorageProfile(world, nextPositions);
        if (this.formed &&
                this.matchedPositions.equals(nextPositions) &&
                this.storageProfile.equals(nextStorageProfile) &&
                NO_FAILURE.equals(this.lastFailureReason) &&
                this.lastFailurePosition == null) {
            this.compartmentBinder.ensureBound(world, structureName, this, declaredCompartments);
            return;
        }
        clearCompartmentBindings(structureName);
        this.formed = true;
        this.matchedPositions = nextPositions;
        this.storageProfile = nextStorageProfile;
        this.compartmentBinder.bind(world, structureName, this, declaredCompartments);
        this.lastFailureReason = NO_FAILURE;
        this.lastFailurePosition = null;
        this.craftingRuntime.setMainStructureFormed(true);
        this.craftingRuntime.setPaused(!isTrinityRuntimeAvailable());
        notifyTrinityAccessChanged();
        setChanged();
    }

    private void applyCpuMatch(StructureWorldView world, List<BlockPos> positions) {
        TrinityDataCoreCpuContribution contribution = buildCpuContribution(world, positions);
        boolean contributionChanged = !Objects.equals(this.cpuStructureContribution, contribution);
        boolean statusChanged = !this.cpuStructureFormed ||
                this.cpuStructureMatchedBlockCount != positions.size() ||
                !NO_FAILURE.equals(this.cpuLastFailureReason) ||
                this.cpuLastFailurePosition != null;

        this.cpuStructureFormed = true;
        this.cpuStructureMatchedBlockCount = positions.size();
        this.cpuLastFailureReason = NO_FAILURE;
        this.cpuLastFailurePosition = null;
        if (contributionChanged) {
            this.cpuStructureContribution = contribution;
            setCpuContribution(CPU_STRUCTURE_NAME, contribution);
        } else if (statusChanged) {
            notifyTrinityAccessChanged();
            setChanged();
        }
    }

    private void applyCpuFailure(PatternDiagnostic diagnostic) {
        String nextFailureReason;
        BlockPos nextFailurePosition;
        if (diagnostic == null) {
            nextFailureReason = "Structure pattern did not match";
            nextFailurePosition = null;
        } else {
            nextFailureReason = diagnostic.message();
            nextFailurePosition = diagnostic.position();
        }
        if (!this.cpuStructureFormed &&
                this.cpuStructureMatchedBlockCount == 0 &&
                Objects.equals(this.cpuLastFailureReason, nextFailureReason) &&
                Objects.equals(this.cpuLastFailurePosition, nextFailurePosition)) {
            return;
        }
        if (this.cpuStructureFormed && diagnostic != null) {
            LOGGER.warn(
                    "Trinity Digital Core structure '{}' failed at {}: {}",
                    CPU_STRUCTURE_NAME,
                    diagnostic.position(),
                    diagnostic.message());
        }
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = nextFailureReason;
        this.cpuLastFailurePosition = nextFailurePosition;
        notifyTrinityAccessChanged();
        setChanged();
    }

    private void applyCraftingMatch(StructureWorldView world, List<BlockPos> positions, boolean catalogChanged) {
        TrinityDataCoreCraftingCoreProfile nextProfile = buildCraftingProfile(world, positions);
        boolean statusChanged = !this.craftingStructureFormed ||
                this.craftingStructureMatchedBlockCount != positions.size() ||
                !this.craftingProfile.equals(nextProfile) ||
                catalogChanged ||
                !NO_FAILURE.equals(this.craftingLastFailureReason) ||
                this.craftingLastFailurePosition != null;

        this.craftingStructureFormed = true;
        this.craftingStructureMatchedBlockCount = positions.size();
        this.craftingProfile = nextProfile;
        this.craftingLastFailureReason = NO_FAILURE;
        this.craftingLastFailurePosition = null;
        if (statusChanged) {
            notifyTrinityAccessChanged();
            setChanged();
        }
    }

    private void applyCraftingFailure(PatternDiagnostic diagnostic) {
        if (diagnostic == null) {
            applyCraftingFailure("Structure pattern did not match", null);
        } else {
            applyCraftingFailure(diagnostic.message(), diagnostic.position());
        }
    }

    private void applyCraftingFailure(String nextFailureReason, @Nullable BlockPos nextFailurePosition) {
        this.patternCatalogValid = false;
        if (!this.craftingStructureFormed &&
                this.craftingStructureMatchedBlockCount == 0 &&
                Objects.equals(this.craftingLastFailureReason, nextFailureReason) &&
                Objects.equals(this.craftingLastFailurePosition, nextFailurePosition)) {
            return;
        }
        if (this.craftingStructureFormed || !Objects.equals(this.craftingLastFailureReason, nextFailureReason) ||
                !Objects.equals(this.craftingLastFailurePosition, nextFailurePosition)) {
            LOGGER.warn(
                    "Trinity Digital Core structure '{}' failed at {}: {}",
                    CRAFTING_STRUCTURE_NAME,
                    nextFailurePosition,
                    nextFailureReason);
        }
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingLastFailureReason = nextFailureReason;
        this.craftingLastFailurePosition = nextFailurePosition;
        notifyTrinityAccessChanged();
        setChanged();
    }

    private void applyFailure(PatternDiagnostic diagnostic, String structureName) {
        String nextFailureReason;
        BlockPos nextFailurePosition;
        if (diagnostic == null) {
            nextFailureReason = "Structure pattern did not match";
            nextFailurePosition = null;
        } else {
            nextFailureReason = diagnostic.message();
            nextFailurePosition = diagnostic.position();
        }
        if (!this.formed && this.matchedPositions.isEmpty() && Objects.equals(this.lastFailureReason, nextFailureReason) &&
                Objects.equals(this.lastFailurePosition, nextFailurePosition) &&
                !this.cpuStructureFormed &&
                this.cpuStructureMatchedBlockCount == 0 &&
                !this.craftingStructureFormed &&
                this.craftingStructureMatchedBlockCount == 0 &&
                Objects.equals(this.craftingLastFailureReason, MAIN_STRUCTURE_NOT_FORMED) &&
                this.craftingLastFailurePosition == null) {
            return;
        }
        LOGGER.warn(
                "Trinity Data Core structure '{}' failed at {}: {}",
                structureName,
                nextFailurePosition,
                nextFailureReason);
        clearCompartmentBindings(structureName);
        clearCpuStructureStatus(MAIN_STRUCTURE_NOT_FORMED, null);
        clearCraftingStructureStatus(MAIN_STRUCTURE_NOT_FORMED, null);
        this.formed = false;
        this.matchedPositions = List.of();
        this.lastFailureReason = nextFailureReason;
        this.lastFailurePosition = nextFailurePosition;
        this.craftingRuntime.setMainStructureFormed(false);
        this.craftingRuntime.setPaused(true);
        notifyTrinityAccessChanged();
        setChanged();
    }

    private void clearCompartmentBindings(String structureName) {
        this.compartmentBinder.unbind(structureName, this);
        this.compartmentHostState.clear(structureName);
    }

    @Override
    public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {
        this.compartmentHostState.addCompartment(structureName, part);
    }

    @Override
    public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {
        this.compartmentHostState.removeCompartment(structureName, part);
    }

    @Override
    public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
        return this.compartmentHostState.compartments(structureName);
    }

    private static JsonMultiBlockDefinition requireMainJsonDefinition() {
        return requireJsonDefinition(mainDefinitionKey());
    }

    private static JsonMultiBlockDefinition requireCpuJsonDefinition() {
        return requireJsonDefinition(cpuDefinitionKey());
    }

    private static JsonMultiBlockDefinition requireCraftingJsonDefinition() {
        return requireJsonDefinition(craftingDefinitionKey());
    }

    static JsonMultiBlockStructureKey autoBuildDefinitionKey(DigitalConstructFlowerAutoBuildTarget target) {
        return switch (target) {
            case MAIN -> mainDefinitionKey();
            case CPU -> cpuDefinitionKey();
            case CRAFTING -> craftingDefinitionKey();
        };
    }

    static String autoBuildStructureName(DigitalConstructFlowerAutoBuildTarget target) {
        return switch (target) {
            case MAIN -> mainDefinitionKey().structureName();
            case CPU -> CPU_STRUCTURE_NAME;
            case CRAFTING -> CRAFTING_STRUCTURE_NAME;
        };
    }

    private static JsonMultiBlockDefinition autoBuildDefinition(DigitalConstructFlowerAutoBuildTarget target) {
        return requireJsonDefinition(autoBuildDefinitionKey(target));
    }

    private static JsonMultiBlockDefinition requireJsonDefinition(JsonMultiBlockStructureKey key) {
        return ModVerticalMultiBlocks.JSON_MULTI_BLOCKS
                .get(key)
                .orElseThrow(() -> new IllegalStateException("Missing JSON multiblock definition: " + key));
    }

    private static void requireMainJsonDefinitionKey() {
        requireMainJsonDefinition();
    }

    private static JsonMultiBlockStructureKey mainDefinitionKey() {
        return JsonMultiBlockStructureKey.main(ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_ID));
    }

    private static JsonMultiBlockStructureKey cpuDefinitionKey() {
        return new JsonMultiBlockStructureKey(
                ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_ID),
                CPU_STRUCTURE_NAME);
    }

    private static JsonMultiBlockStructureKey craftingDefinitionKey() {
        return new JsonMultiBlockStructureKey(
                ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_ID),
                CRAFTING_STRUCTURE_NAME);
    }

    private static List<BlockPos> readMatchedPositions(CompoundTag data) {
        ListTag positions = data.getList(MATCHED_POSITIONS_TAG, Tag.TAG_LONG);
        return positions.stream()
                .map(tag -> BlockPos.of(((LongTag) tag).getAsLong()))
                .toList();
    }

    private static TrinityDataCoreCraftingCoreProfile readCraftingProfile(CompoundTag data) {
        if (!data.contains(CRAFTING_PATTERN_CORE_COUNT_TAG) && !data.contains(CRAFTING_PATTERN_CAPACITY_TAG)) {
            return TrinityDataCoreCraftingCoreProfile.EMPTY;
        }
        return new TrinityDataCoreCraftingCoreProfile(
                data.getInt(CRAFTING_PATTERN_CORE_COUNT_TAG),
                data.getInt(CRAFTING_PATTERN_CAPACITY_TAG));
    }

    private ListTag createMatchedPositionsTag() {
        ListTag positions = new ListTag();
        for (BlockPos pos : this.matchedPositions) {
            positions.add(LongTag.valueOf(pos.asLong()));
        }
        return positions;
    }

    private void notifyTrinityAccessChanged() {
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch) {
                hatch.refreshTrinityAccess();
            }
        }
    }

    @Override
    public void setRemoved() {
        this.craftingRuntime.setPaused(true);
        clearCompartmentBindings(mainDefinitionKey().structureName());
        this.accessLease = null;
        super.setRemoved();
    }

    /** Cancels active CPU jobs only when the host block is being permanently removed from the world. */
    public void onPermanentRemoval() {
        this.craftingRuntime.cancelAllJobs();
        this.craftingRuntime.setPaused(true);
        clearCompartmentBindings(mainDefinitionKey().structureName());
        this.accessLease = null;
    }

    private static TrinityDataCoreStorageProfile buildStorageProfile(StructureWorldView world, List<BlockPos> positions) {
        TrinityDataCoreStorageProfile.Builder builder = TrinityDataCoreStorageProfile.builder(MAIN_STORAGE_CORE_SLOT_COUNT);
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.STORAGE_TYPES) {
                builder.add(component);
            }
        }
        return builder.build();
    }

    private static TrinityDataCoreCraftingCoreProfile buildCraftingProfile(StructureWorldView world, List<BlockPos> positions) {
        TrinityDataCoreCraftingCoreProfile.Builder builder = TrinityDataCoreCraftingCoreProfile.builder();
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.PATTERN_PROCESSING) {
                builder.add(component);
            }
        }
        return builder.build();
    }

    private static PatternCatalogScanResult scanPatternCores(StructureWorldView world, List<BlockPos> positions) {
        ArrayList<TrinityPatternCatalog.CoreMount> mounts = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof TrinityCoreComponent component) ||
                    component.kind() != TrinityCoreKind.PATTERN_PROCESSING) {
                continue;
            }
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof TrinityPatternCoreBlockEntity patternCore)) {
                return PatternCatalogScanResult.failure(
                        pos,
                        "Trinity pattern processing core at " + pos + " has no matching block entity");
            }
            mounts.add(new TrinityPatternCatalog.CoreMount(pos, component.patternCapacity(), patternCore));
        }
        return PatternCatalogScanResult.success(mounts);
    }

    private TrinityDataCoreCpuContribution buildCpuContribution(StructureWorldView world, List<BlockPos> positions) {
        TrinityDataCoreCpuCoreProfile.Builder builder = TrinityDataCoreCpuCoreProfile.builder();
        Set<Integer> repeatedLayers = new HashSet<>();
        for (BlockPos pos : positions) {
            int localY = cpuLocalY(pos);
            if (localY >= TrinityDataCoreCpuCoreProfile.REPEAT_START_Y &&
                    localY <= TrinityDataCoreCpuCoreProfile.REPEAT_END_Y) {
                repeatedLayers.add(localY);
            }
            if (localY < TrinityDataCoreCpuCoreProfile.CORE_SLOT_START_Y ||
                    localY > TrinityDataCoreCpuCoreProfile.CORE_SLOT_END_Y) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.PARALLEL_CPU) {
                builder.add(component);
            }
        }
        return builder.actualRepeatCount(TrinityDataCoreCpuCoreProfile.actualRepeatCount(repeatedLayers))
                .build()
                .contribution();
    }

    private int cpuLocalY(BlockPos pos) {
        return pos.getY() - this.worldPosition.getY() + TrinityDataCoreCpuCoreProfile.CONTROLLER_LOCAL_Y;
    }

    private void clearCpuStructureStatus(String failureReason, @Nullable BlockPos failurePosition) {
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = failureReason;
        this.cpuLastFailurePosition = failurePosition;
    }

    private void clearCraftingStructureStatus(String failureReason, @Nullable BlockPos failurePosition) {
        this.patternCatalogValid = false;
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingLastFailureReason = failureReason;
        this.craftingLastFailurePosition = failurePosition;
    }

    private record AutoBuildOrientation(Direction front, boolean flipped) {}

    private record PatternCatalogScanResult(boolean valid,
                                            List<TrinityPatternCatalog.CoreMount> mounts,
                                            @Nullable BlockPos failurePosition,
                                            String failureReason) {

        private static PatternCatalogScanResult success(List<TrinityPatternCatalog.CoreMount> mounts) {
            return new PatternCatalogScanResult(true, List.copyOf(mounts), null, "");
        }

        private static PatternCatalogScanResult failure(BlockPos position, String reason) {
            return new PatternCatalogScanResult(false, List.of(), position.immutable(), reason);
        }
    }

    private record LevelStructureWorldView(Level level) implements StructureWorldView {

        @Override
        public boolean isLoaded(BlockPos pos) {
            return this.level.isLoaded(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.level.getBlockEntity(pos);
        }

        @Override
        public HolderLookup.Provider registryAccess() {
            return this.level.registryAccess();
        }
    }
}
