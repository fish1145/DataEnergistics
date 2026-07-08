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
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCpuProfile;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonDeclaredCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPatternMatcher;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.trinity.DigitalConstructFlowerCpuCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.DigitalConstructFlowerCraftingCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.DigitalConstructFlowerStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerCraftingStatus;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenuHost;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;
import com.fish_dan_.data_energistics.world.DigitalConstructFlowerStorageSavedData;
import com.fish_dan_.data_energistics.world.DigitalConstructFlowerStorageSavedData.StorageSummary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
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
    private static final String CRAFTING_RUNTIME_TAG = "crafting_runtime";
    private static final String STORAGE_ID_TAG = "storage_id";
    private static final String NO_FAILURE = "";
    private static final String MAIN_STRUCTURE_NOT_FORMED = "Main structure is not formed";
    private static final String CPU_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME;
    private static final String CRAFTING_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME;
    private static final int MAIN_STORAGE_CORE_SLOT_COUNT = 1_176;
    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private UUID storageId = UUID.randomUUID();
    private boolean formed;
    private List<BlockPos> matchedPositions = List.of();
    private DigitalConstructFlowerStorageProfile storageProfile = DigitalConstructFlowerStorageProfile.EMPTY;
    private String lastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos lastFailurePosition;
    @Nullable
    private DigitalConstructFlowerCpuContribution cpuStructureContribution;
    private boolean cpuStructureFormed;
    private int cpuStructureMatchedBlockCount;
    private String cpuLastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos cpuLastFailurePosition;
    private boolean craftingStructureFormed;
    private int craftingStructureMatchedBlockCount;
    private DigitalConstructFlowerCraftingCoreProfile craftingProfile = DigitalConstructFlowerCraftingCoreProfile.EMPTY;
    private String craftingLastFailureReason = NO_FAILURE;
    @Nullable
    private BlockPos craftingLastFailurePosition;
    private boolean recheckRequested = true;
    private final CompartmentHostState compartmentHostState = new CompartmentHostState();
    private final JsonMultiBlockCompartmentBinder compartmentBinder = new JsonDeclaredCompartmentBinder();
    private final CompartmentStorage patternBufferStorageView = new CompartmentStorageGroup(this::recognizedPatternBufferStorages);
    private final DigitalConstructFlowerCraftingRuntime craftingRuntime = new DigitalConstructFlowerCraftingRuntime(this);

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
        if (!this.recheckRequested && Math.floorMod(this.level.getGameTime() + this.worldPosition.asLong(), RECHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        this.recheckRequested = false;
        updateStructureMatch(this.level);
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

    public DigitalConstructFlowerStorageProfile storageProfile() {
        return this.storageProfile;
    }

    public void restoreStorageIdFromItem(ItemStack stack) {
        String rawId = stack.get(ModDataComponents.DIGITAL_CONSTRUCT_FLOWER_STORAGE_ID);
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        setStorageId(parseStorageId(rawId, "item component"));
    }

    public void saveStorageIdToItem(ItemStack stack) {
        stack.set(ModDataComponents.DIGITAL_CONSTRUCT_FLOWER_STORAGE_ID, this.storageId.toString());
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
        for (DigitalConstructFlowerVirtualCpu cpu : this.craftingRuntime.partitions()) {
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
    public void setCpuContribution(String structureName, DigitalConstructFlowerCpuContribution contribution) {
        this.craftingRuntime.setContribution(structureName, contribution);
        notifyCraftingCpuChanged();
        setChanged();
    }

    /**
     * Clears CPU data contributed by a named child structure.
     *
     * @param structureName child structure name to remove
     */
    public void clearCpuContribution(String structureName) {
        this.craftingRuntime.clearContribution(structureName);
        notifyCraftingCpuChanged();
        setChanged();
    }

    /**
     * @return virtual CPU partitions currently exposed by this formed structure
     */
    public List<DigitalConstructFlowerVirtualCpu> getCpuPartitions() {
        return this.craftingRuntime.partitions();
    }

    /**
     * @return crafting runtime used by AE2 CraftingService mixins
     */
    public DigitalConstructFlowerCraftingRuntime getCraftingRuntime() {
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

        DigitalConstructFlowerCpuProfile profile = this.craftingRuntime.profile();
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
        return stack != null && stack.what() != null && stack.amount() > 0;
    }

    private StorageSummary storageSummary() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return StorageSummary.EMPTY;
        }
        return DigitalConstructFlowerStorageSavedData.get(serverLevel.getServer()).summary(this.storageId);
    }

    private String storageCapacityText(String finiteCapacity) {
        return this.storageProfile.unlimited() ? DigitalConstructFlowerMenuHost.UNLIMITED_STORAGE_CAPACITY : finiteCapacity;
    }

    public boolean hasActiveAccessHatch() {
        return accessGrid() != null;
    }

    public @Nullable IGrid accessGrid() {
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof MeStorageAccessHatchBlockEntity hatch) {
                IGrid grid = hatch.accessGrid();
                if (grid != null) {
                    return grid;
                }
            }
        }
        return null;
    }

    public IActionSource accessActionSource() {
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof MeStorageAccessHatchBlockEntity hatch && hatch.accessGrid() != null) {
                return hatch.actionSource();
            }
        }
        throw new IllegalStateException("Trinity Digital Core has no active ME storage access hatch");
    }

    private void setStorageId(UUID storageId) {
        if (this.storageId.equals(storageId)) {
            return;
        }
        this.storageId = storageId;
        setChanged();
    }

    private static UUID parseStorageId(String rawId, String source) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid Digital Construct Flower storage id '{}' from {}; generating a replacement", rawId, source, exception);
            return UUID.randomUUID();
        }
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
        if (data.contains(STORAGE_ID_TAG, Tag.TAG_STRING)) {
            setStorageId(parseStorageId(data.getString(STORAGE_ID_TAG), "block entity tag"));
        }
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
        }
        if (!data.contains(CPU_STRUCTURE_FORMED_TAG)) {
            this.cpuStructureFormed = this.craftingRuntime.hasContribution(CPU_STRUCTURE_NAME);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putString(STORAGE_ID_TAG, this.storageId.toString());
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
            boolean childStructureFlipped = !result.flipped();
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
            applyCraftingMatch(world, result.positions());
        } else {
            applyCraftingFailure(result.diagnostic());
        }
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
        DigitalConstructFlowerStorageProfile nextStorageProfile = buildStorageProfile(world, nextPositions);
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
        notifyCraftingCpuChanged();
        setChanged();
    }

    private void applyCpuMatch(StructureWorldView world, List<BlockPos> positions) {
        DigitalConstructFlowerCpuContribution contribution = buildCpuContribution(world, positions);
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
                Objects.equals(this.cpuLastFailurePosition, nextFailurePosition) &&
                !hasCpuStructureContribution()) {
            return;
        }
        if ((this.cpuStructureFormed || hasCpuStructureContribution()) && diagnostic != null) {
            LOGGER.warn(
                    "Trinity Digital Core structure '{}' failed at {}: {}",
                    CPU_STRUCTURE_NAME,
                    diagnostic.position(),
                    diagnostic.message());
        }
        clearCpuStructureContribution();
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = nextFailureReason;
        this.cpuLastFailurePosition = nextFailurePosition;
        setChanged();
    }

    private void applyCraftingMatch(StructureWorldView world, List<BlockPos> positions) {
        DigitalConstructFlowerCraftingCoreProfile nextProfile = buildCraftingProfile(world, positions);
        boolean statusChanged = !this.craftingStructureFormed ||
                this.craftingStructureMatchedBlockCount != positions.size() ||
                !this.craftingProfile.equals(nextProfile) ||
                !NO_FAILURE.equals(this.craftingLastFailureReason) ||
                this.craftingLastFailurePosition != null;

        this.craftingStructureFormed = true;
        this.craftingStructureMatchedBlockCount = positions.size();
        this.craftingProfile = nextProfile;
        this.craftingLastFailureReason = NO_FAILURE;
        this.craftingLastFailurePosition = null;
        if (statusChanged) {
            setChanged();
        }
    }

    private void applyCraftingFailure(PatternDiagnostic diagnostic) {
        String nextFailureReason;
        BlockPos nextFailurePosition;
        if (diagnostic == null) {
            nextFailureReason = "Structure pattern did not match";
            nextFailurePosition = null;
        } else {
            nextFailureReason = diagnostic.message();
            nextFailurePosition = diagnostic.position();
        }
        if (!this.craftingStructureFormed &&
                this.craftingStructureMatchedBlockCount == 0 &&
                this.craftingProfile.equals(DigitalConstructFlowerCraftingCoreProfile.EMPTY) &&
                Objects.equals(this.craftingLastFailureReason, nextFailureReason) &&
                Objects.equals(this.craftingLastFailurePosition, nextFailurePosition)) {
            return;
        }
        if (this.craftingStructureFormed && diagnostic != null) {
            LOGGER.warn(
                    "Trinity Digital Core structure '{}' failed at {}: {}",
                    CRAFTING_STRUCTURE_NAME,
                    diagnostic.position(),
                    diagnostic.message());
        }
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingProfile = DigitalConstructFlowerCraftingCoreProfile.EMPTY;
        this.craftingLastFailureReason = nextFailureReason;
        this.craftingLastFailurePosition = nextFailurePosition;
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
                !hasCpuStructureContribution() &&
                !this.cpuStructureFormed &&
                this.cpuStructureMatchedBlockCount == 0 &&
                !this.craftingStructureFormed &&
                this.craftingStructureMatchedBlockCount == 0 &&
                this.craftingProfile.equals(DigitalConstructFlowerCraftingCoreProfile.EMPTY) &&
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
        this.storageProfile = DigitalConstructFlowerStorageProfile.EMPTY;
        this.lastFailureReason = nextFailureReason;
        this.lastFailurePosition = nextFailurePosition;
        this.craftingRuntime.setMainStructureFormed(false);
        notifyCraftingCpuChanged();
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

    private static DigitalConstructFlowerCraftingCoreProfile readCraftingProfile(CompoundTag data) {
        if (!data.contains(CRAFTING_PATTERN_CORE_COUNT_TAG) && !data.contains(CRAFTING_PATTERN_CAPACITY_TAG)) {
            return DigitalConstructFlowerCraftingCoreProfile.EMPTY;
        }
        return new DigitalConstructFlowerCraftingCoreProfile(
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

    private void notifyCraftingCpuChanged() {
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof MeStorageAccessHatchBlockEntity hatch) {
                var node = hatch.getMainNode().getNode();
                if (node != null) {
                    node.getGrid().postEvent(new GridCraftingCpuChange(node));
                }
            }
        }
    }

    private static DigitalConstructFlowerStorageProfile buildStorageProfile(StructureWorldView world, List<BlockPos> positions) {
        DigitalConstructFlowerStorageProfile.Builder builder = DigitalConstructFlowerStorageProfile.builder(MAIN_STORAGE_CORE_SLOT_COUNT);
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.STORAGE_TYPES) {
                builder.add(component);
            }
        }
        return builder.build();
    }

    private static DigitalConstructFlowerCraftingCoreProfile buildCraftingProfile(StructureWorldView world, List<BlockPos> positions) {
        DigitalConstructFlowerCraftingCoreProfile.Builder builder = DigitalConstructFlowerCraftingCoreProfile.builder();
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.PATTERN_PROCESSING) {
                builder.add(component);
            }
        }
        return builder.build();
    }

    private DigitalConstructFlowerCpuContribution buildCpuContribution(StructureWorldView world, List<BlockPos> positions) {
        DigitalConstructFlowerCpuCoreProfile.Builder builder = DigitalConstructFlowerCpuCoreProfile.builder();
        Set<Integer> repeatedLayers = new HashSet<>();
        for (BlockPos pos : positions) {
            int localY = cpuLocalY(pos);
            if (localY >= DigitalConstructFlowerCpuCoreProfile.REPEAT_START_Y &&
                    localY <= DigitalConstructFlowerCpuCoreProfile.REPEAT_END_Y) {
                repeatedLayers.add(localY);
            }
            if (localY < DigitalConstructFlowerCpuCoreProfile.CORE_SLOT_START_Y ||
                    localY > DigitalConstructFlowerCpuCoreProfile.CORE_SLOT_END_Y) {
                continue;
            }
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof TrinityCoreComponent component && component.kind() == TrinityCoreKind.PARALLEL_CPU) {
                builder.add(component);
            }
        }
        return builder.actualRepeatCount(DigitalConstructFlowerCpuCoreProfile.actualRepeatCount(repeatedLayers))
                .build()
                .contribution();
    }

    private int cpuLocalY(BlockPos pos) {
        return pos.getY() - this.worldPosition.getY() + DigitalConstructFlowerCpuCoreProfile.CONTROLLER_LOCAL_Y;
    }

    private void clearCpuStructureContribution() {
        if (!hasCpuStructureContribution()) {
            return;
        }
        this.cpuStructureContribution = null;
        clearCpuContribution(CPU_STRUCTURE_NAME);
    }

    private void clearCpuStructureStatus(String failureReason, @Nullable BlockPos failurePosition) {
        clearCpuStructureContribution();
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = failureReason;
        this.cpuLastFailurePosition = failurePosition;
    }

    private void clearCraftingStructureStatus(String failureReason, @Nullable BlockPos failurePosition) {
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingProfile = DigitalConstructFlowerCraftingCoreProfile.EMPTY;
        this.craftingLastFailureReason = failureReason;
        this.craftingLastFailurePosition = failurePosition;
    }

    private boolean hasCpuStructureContribution() {
        return this.cpuStructureContribution != null || this.craftingRuntime.hasContribution(CPU_STRUCTURE_NAME);
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
