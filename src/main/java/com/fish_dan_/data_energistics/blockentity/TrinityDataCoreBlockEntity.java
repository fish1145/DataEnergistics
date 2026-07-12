package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHostState;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCpuProfile;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.multiblock.MultiBlockStatusProvider;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Context;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Failure;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.FailureType;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.PartSideResolver;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuild.Result;
import com.fish_dan_.data_energistics.common.multiblock.autobuild.MultiBlockAutoBuildImpl;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonDeclaredCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentBinder;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockCompartmentPredicate;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockFrontFacing;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockPatternMatcher;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.TrinityAccessLease;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreComponent;
import com.fish_dan_.data_energistics.common.trinity.TrinityCoreKind;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreCpuCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreCraftingCoreProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalogImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCoreHost;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouter;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouter.PendingOutputCursor;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternOutputRouterImpl;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternSlot;
import com.fish_dan_.data_energistics.common.trinity.TrinityRefundDeliveryImpl;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreCraftingStatus;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.service.CraftingService;
import appeng.parts.networking.CablePart;
import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import com.modularmc.mdl.api.multiblock.TraceabilityPredicate;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class TrinityDataCoreBlockEntity extends AENetworkedBlockEntity
                                        implements MultiBlockStatusProvider, CompartmentHost, TrinityDataCoreMenuHost,
                                        TrinityPatternCoreHost {

    private static final int UNFORMED_MAIN_RECHECK_INTERVAL_TICKS = 100;
    private static final int FORMED_MAIN_RECHECK_INTERVAL_TICKS = 1_200;
    private static final int UNFORMED_CHILD_RECHECK_INTERVAL_TICKS = 100;
    private static final int FORMED_CHILD_RECHECK_INTERVAL_TICKS = 1_200;
    private static final int CRAFTING_RECHECK_PHASE_OFFSET_TICKS = 50;
    private static final int PATTERN_CORE_HEALTH_CHECK_INTERVAL_TICKS = 100;
    private static final String FORMED_TAG = "formed";
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
    private static final String ACCESS_LEASE_HATCH_POSITION_TAG = "trinity_access_lease_hatch_position";
    private static final String ACCESS_LEASE_EPOCH_TAG = "trinity_access_lease_epoch";
    private static final String NO_FAILURE = "";
    private static final String MAIN_STRUCTURE_NOT_FORMED = "Main structure is not formed";
    private static final String CPU_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DATA_CORE_CPU_STRUCTURE_NAME;
    private static final String CRAFTING_STRUCTURE_NAME = ModVerticalMultiBlocks.TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME;
    private static final int MAIN_STORAGE_CORE_SLOT_COUNT = 1_176;
    private static final Logger LOGGER = Data_Energistics.LOGGER;
    /** Atomic inventory-and-world transaction shared by every Trinity structure build request. */
    private static final MultiBlockAutoBuild AUTO_BUILD = new MultiBlockAutoBuildImpl();

    private UUID storageId = UUID.randomUUID();
    private UUID hostId = UUID.randomUUID();
    private TrinityPatternCatalog patternCatalog = new TrinityPatternCatalogImpl(this.hostId);
    private final TrinityPatternOutputRouter patternOutputRouter = new TrinityPatternOutputRouterImpl();
    private boolean patternCatalogValid;
    private boolean loaded;
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
    private boolean cpuStructureRecheckRequested = true;
    private boolean craftingStructureRecheckRequested = true;
    private long lastCpuStructureRecheckTick = Long.MIN_VALUE;
    private long lastCraftingStructureRecheckTick = Long.MIN_VALUE;
    private long lastPatternCoreHealthCheckTick = Long.MIN_VALUE;
    private long observedMultiBlockDefinitionRevision = -1L;
    @Nullable
    private Direction mainStructureFrontFacing;
    private boolean mainStructureFlipped;
    private boolean structureRecheckInProgress;
    private final CompartmentHostState compartmentHostState = new CompartmentHostState();
    private final JsonMultiBlockCompartmentBinder compartmentBinder = new JsonDeclaredCompartmentBinder();
    private final TrinityDataCoreCraftingRuntime craftingRuntime = new TrinityDataCoreCraftingRuntime(this);
    @Nullable
    private TrinityAccessLease accessLease;
    private long accessLeaseEpoch;
    private boolean missingBusyLeaseReported;

    public TrinityDataCoreBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.TRINITY_DATA_CORE_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .setVisualRepresentation(ModBlocks.TRINITY_DATA_CORE.get())
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

    @Override
    public void onLoad() {
        super.onLoad();
        this.loaded = true;
        this.craftingRuntime.setPaused(true);
        requestStructureRecheck();
        notifyTrinityAccessChanged();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        try {
            tickServerState();
        } catch (RuntimeException exception) {
            this.craftingRuntime.setPaused(true);
            notifyTrinityCpuChanged();
            this.cpuStructureRecheckRequested = true;
            this.craftingStructureRecheckRequested = true;
            if (withdrawPatternCatalog()) {
                notifyTrinityPatternLayoutChanged();
            }
            LOGGER.error("Failed to tick Trinity Data Core at {}; runtime was paused", this.worldPosition, exception);
        }
    }

    private void tickServerState() {
        observeMultiBlockDefinitionRevision();
        long gameTime = this.level.getGameTime();
        if (this.patternCatalogValid && this.patternCatalog.layoutSnapshot().active() &&
                this.lastPatternCoreHealthCheckTick != gameTime &&
                isPatternCoreHealthCheckDue(gameTime, this.hostId)) {
            this.lastPatternCoreHealthCheckTick = gameTime;
            if (!hasHealthyPatternCoreIdentities()) {
                boolean changed = withdrawPatternCatalog();
                requestStructureRecheck();
                if (changed) {
                    notifyTrinityPatternLayoutChanged();
                }
                return;
            }
        }
        updateScheduledStructureMatches();
        reevaluateAccessLease();
        if (this.patternCatalogValid) {
            List<TrinityPatternCatalog.CoreMount> mountedBeforeFlush = this.patternCatalog.mountedCores();
            if (this.patternCatalog.refreshChangedPatterns()) {
                this.patternCatalogValid = this.patternCatalog.layoutSnapshot().active();
                if (this.patternCatalogValid) {
                    notifyTrinityPatternPublicationChanged();
                } else {
                    releasePatternCoreBindings(mountedBeforeFlush);
                    requestStructureRecheck();
                    notifyTrinityPatternLayoutChanged();
                }
            }
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
    public boolean hasRefundablePatternState() {
        return this.patternCatalogValid && this.patternCatalog.hasRefundableState();
    }

    @Override
    public boolean tryRefundAll(Player player) {
        if (!this.patternCatalogValid) {
            return false;
        }
        boolean refunded = this.patternCatalog.tryRefundAll(createRefundDelivery(player));
        if (refunded) {
            setChanged();
        }
        return refunded;
    }

    @Override
    public boolean isPatternCoreMounted(TrinityPatternCore core) {
        return this.patternCatalogValid && this.patternCatalog.isCoreMounted(core);
    }

    @Override
    public boolean tryRefundPatternCore(TrinityPatternCore core, Player player) {
        if (!isPatternCoreMounted(core)) {
            return false;
        }
        boolean refunded = core.tryRefundAll(createRefundDelivery(player));
        if (refunded) {
            setChanged();
        }
        return refunded;
    }

    @Override
    public void onPatternCoreChanged(TrinityPatternCore core, TrinityPatternSlot.Change change) {
        if (this.patternCatalogValid) {
            this.patternCatalog.onCoreChanged(core, change);
        }
    }

    @Override
    public void onPatternCoreUnavailable(TrinityPatternCore core) {
        if (!isPatternCoreMounted(core)) {
            return;
        }
        boolean changed = withdrawPatternCatalog();
        requestStructureRecheck();
        if (changed) {
            notifyTrinityPatternLayoutChanged();
        }
        setChanged();
    }

    private TrinityRefundDeliveryImpl createRefundDelivery(Player player) {
        MEStorage networkStorage = null;
        IActionSource actionSource = null;
        IGrid grid = accessGrid();
        if (grid != null) {
            try {
                networkStorage = grid.getStorageService().getInventory();
                actionSource = accessActionSource();
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Trinity host {} could not prepare AE refund delivery; using player and world fallbacks",
                        this.worldPosition,
                        exception);
            }
        }
        return new TrinityRefundDeliveryImpl(
                player,
                networkStorage,
                actionSource);
    }

    @Override
    public String getCraftingLastFailureReason() {
        return this.craftingLastFailureReason;
    }

    @Override
    public @Nullable BlockPos getCraftingLastFailurePosition() {
        return this.craftingLastFailurePosition;
    }

    /**
     * Returns whether the formed and loaded main structure may expose its UUID storage through the elected lease.
     */
    public boolean isStorageAvailable() {
        return this.loaded && this.formed;
    }

    /**
     * Returns whether the CPU child may publish its retained virtual CPU runtime through the elected lease.
     */
    public boolean isCpuProviderAvailable() {
        return isStorageAvailable() && this.cpuStructureFormed;
    }

    /**
     * Returns whether the lease owner may publish the aggregated crafting patterns to AE2.
     */
    public boolean isPatternProviderAvailable() {
        return isStorageAvailable() && this.craftingStructureFormed && this.craftingProfile.active() &&
                this.patternCatalogValid &&
                this.patternCatalog.layoutSnapshot().active();
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
        this.cpuStructureRecheckRequested = true;
        this.craftingStructureRecheckRequested = true;
    }

    /**
     * Executes one validated Trinity structure build requested from the open controller menu.
     *
     * <p>
     * Child structures are intentionally gated on an already formed main structure. A request that only changes UI
     * selections has {@code buildRequested=false} and returns before it can reserve inventory or mutate the world.
     * </p>
     *
     * @param player  server player that supplies placement permissions and materials
     * @param request validated selector, repetition, and tier choices from the client
     */
    public void autoBuildTrinityStructure(Player player, TrinityAutoBuildRequest request) {
        if (!request.options().buildRequested()) {
            return;
        }
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        int structureIndex = request.structureIndex();
        if (isChildStructure(structureIndex) && !this.formed) {
            reportAutoBuildFailure(
                    player,
                    structureIndex,
                    FailureType.BLOCKED,
                    "Main structure must be formed before building a child structure");
            return;
        }

        try {
            JsonMultiBlockDefinition mainDefinition = requireMainJsonDefinition();
            StructureWorldView world = new LevelStructureWorldView(serverLevel);
            AutoBuildOrientation orientation = resolveAutoBuildOrientation(mainDefinition.pattern(), world, serverLevel);
            Result result = executeAutoBuild(
                    serverLevel,
                    player,
                    this.worldPosition,
                    orientation.front(),
                    orientation.flipped(),
                    request);
            if (result.success()) {
                requestStructureRecheck();
            }
            reportAutoBuildResult(player, structureIndex, result);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Rejected Trinity auto-build request {} at {}: {}", structureIndex, this.worldPosition,
                    exception.getMessage());
            reportAutoBuildFailure(player, structureIndex, FailureType.INVALID_TIER_SELECTION, exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Trinity auto-build execution failed for structure {} at {}", structureIndex, this.worldPosition,
                    exception);
            reportAutoBuildFailure(player, structureIndex, FailureType.PLACE_FAILED,
                    "Unexpected auto-build execution failure; see server log");
        }
    }

    /**
     * Builds one Trinity structure through the shared atomic builder after the caller has applied host-specific
     * prerequisites.
     *
     * <p>
     * This package-visible entry point keeps GameTests on the production transaction while allowing fixture builders
     * to prepare a main or child structure without simulating a menu packet. The controller-facing method above is
     * solely responsible for child-structure formation gating.
     * </p>
     *
     * @param serverLevel server world mutated by the transaction
     * @param player      player supplying permissions and materials
     * @param origin      controller origin for pattern coordinates
     * @param front       already resolved main-structure front
     * @param flipped     already resolved main-structure mirror state
     * @param request     validated build request
     * @return complete atomic build result
     */
    static Result executeAutoBuild(ServerLevel serverLevel,
                                   Player player,
                                   BlockPos origin,
                                   Direction front,
                                   boolean flipped,
                                   TrinityAutoBuildRequest request) {
        if (!request.options().buildRequested()) {
            return Result.success(0, 0);
        }

        int structureIndex = request.structureIndex();
        AutoBuildOrientation orientation = new AutoBuildOrientation(front, flipped);
        JsonMultiBlockDefinition definition = autoBuildDefinition(structureIndex);
        String structureName = autoBuildStructureName(structureIndex);
        StructureWorldView world = new LevelStructureWorldView(serverLevel);
        Map<Block, Block> selectedTierBlocks = TrinityAutoBuildBlockMap.selectedTierBlocks(
                structureIndex,
                request.options().repeatCount(),
                request.options().tierSelections());
        PartSideResolver partSideResolver = new AutoBuildPartResolver(
                world,
                origin,
                structureName,
                orientation.front(),
                orientation.flipped(),
                autoBuildPredicates(
                        definition.pattern(),
                        origin,
                        orientation,
                        request.options().repeatCount()));
        Context context = Context.builder()
                .level(serverLevel)
                .player(player)
                .world(world)
                .pattern(definition.pattern())
                .origin(origin)
                .structureName(structureName)
                .front(orientation.front())
                .flipped(orientation.flipped())
                .repeatCount(request.options().repeatCount())
                .selectedTierBlocks(selectedTierBlocks)
                .tierRanks(TrinityAutoBuildBlockMap.tierRanksForStructure(structureIndex))
                .partSideResolver(partSideResolver)
                .build();
        return AUTO_BUILD.execute(context);
    }

    private static boolean isChildStructure(int structureIndex) {
        return structureIndex == TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX ||
                structureIndex == TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX;
    }

    private void reportAutoBuildFailure(Player player,
                                        int structureIndex,
                                        FailureType type,
                                        String detail) {
        reportAutoBuildResult(player, structureIndex, Result.failure(0, new Failure(type, this.worldPosition, detail)));
    }

    private static void reportAutoBuildResult(Player player, int structureIndex, Result result) {
        int missing = 0;
        int blocked = 0;
        int unloaded = 0;
        int placeFailed = 0;
        Failure failure = result.failure();
        if (failure != null) {
            switch (failure.type()) {
                case MISSING_MATERIAL -> missing = 1;
                case BLOCKED -> blocked = 1;
                case UNLOADED -> unloaded = 1;
                default -> placeFailed = 1;
            }
        }
        player.displayClientMessage(
                Component.translatable(
                        "message.data_energistics.trinity_data_core.auto_build",
                        autoBuildTargetName(structureIndex),
                        result.placed(),
                        missing,
                        blocked,
                        unloaded,
                        placeFailed),
                true);
    }

    private static Component autoBuildTargetName(int structureIndex) {
        String structureKey = switch (structureIndex) {
            case TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX -> "screen.data_energistics.trinity_data_core.auto_build.structure.main";
            case TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX -> "screen.data_energistics.trinity_data_core.auto_build.structure.cpu";
            case TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX -> "screen.data_energistics.trinity_data_core.auto_build.structure.crafting";
            default -> throw new IllegalArgumentException("Unknown Trinity auto-build structure index: " + structureIndex);
        };
        return Component.translatable(structureKey);
    }

    private static Map<BlockPos, TraceabilityPredicate> autoBuildPredicates(BlockPattern pattern,
                                                                            BlockPos origin,
                                                                            AutoBuildOrientation orientation,
                                                                            int repeatCount) {
        LinkedHashMap<BlockPos, TraceabilityPredicate> predicates = new LinkedHashMap<>();
        int minX = pattern.getMinX();
        int minY = pattern.getMinY();
        int expandedZ = pattern.getMinZ();
        for (int unit = 0; unit < pattern.aisleRepetitions.length; unit++) {
            int minimum = pattern.aisleRepetitions[unit][0];
            int maximum = pattern.aisleRepetitions[unit][1];
            int repetitions = minimum == maximum ? minimum : repeatCount;
            if (repetitions < minimum || repetitions > maximum) {
                throw new IllegalArgumentException("Requested repetition " + repeatCount + " is outside [" + minimum +
                        ", " + maximum + "] for Trinity pattern unit " + unit);
            }
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (int inner = 0; inner < pattern.unitDepths[unit]; inner++) {
                    int patternZ = pattern.unitStarts[unit] + inner;
                    for (int y = 0; y < pattern.getThumbLength(); y++) {
                        for (int x = 0; x < pattern.getPalmLength(); x++) {
                            TraceabilityPredicate predicate = pattern.getPredicate(patternZ, y, x);
                            if (predicate.isAny() || predicate.isAir()) {
                                continue;
                            }
                            BlockPos target = origin.offset(pattern.getActualRelativeOffset(
                                    minX + x,
                                    minY + y,
                                    expandedZ,
                                    orientation.front(),
                                    Direction.NORTH,
                                    orientation.flipped()));
                            if (!target.equals(origin)) {
                                TraceabilityPredicate previous = predicates.putIfAbsent(target.immutable(), predicate);
                                if (previous != null && previous != predicate) {
                                    throw new IllegalStateException(
                                            "Trinity auto-build pattern resolves conflicting predicates at " + target);
                                }
                            }
                        }
                    }
                    expandedZ++;
                }
            }
        }
        return Map.copyOf(predicates);
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

    public UUID getStorageId() {
        return this.storageId;
    }

    public TrinityDataCoreStorageProfile storageProfile() {
        return this.storageProfile;
    }

    /**
     * Restores the storage and crafting identities carried by one moved host item.
     *
     * <p>
     * Both components form one identity pair. A malformed item containing only one component is rejected so a
     * newly created host cannot accidentally bind half of another host's persistent state.
     * </p>
     *
     * @param stack placed Trinity host item
     * @return whether a complete identity pair was restored
     */
    public boolean restoreIdentityFromItem(ItemStack stack) {
        UUID itemStorageId = stack.get(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID);
        UUID itemHostId = stack.get(ModDataComponents.TRINITY_DATA_CORE_HOST_ID);
        if (itemStorageId == null && itemHostId == null) {
            return false;
        }
        if (itemStorageId == null || itemHostId == null) {
            LOGGER.error("Rejecting partial Trinity Data Core identity on item {}", stack);
            return false;
        }
        setIdentity(itemStorageId, itemHostId);
        return true;
    }

    /** Saves the storage and crafting identities as one typed-component pair on a moved host item. */
    public void saveIdentityToItem(ItemStack stack) {
        stack.set(ModDataComponents.TRINITY_DATA_CORE_STORAGE_ID, this.storageId);
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
        notifyTrinityCpuChanged();
        setChanged();
    }

    /**
     * Clears CPU data contributed by a named child structure.
     *
     * @param structureName child structure name to remove
     */
    public void clearCpuContribution(String structureName) {
        this.craftingRuntime.clearContribution(structureName);
        notifyTrinityCpuChanged();
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
    public TrinityDataCoreCraftingStatus getCraftingStatus() {
        IGrid grid = accessGrid();
        if (grid == null) {
            return TrinityDataCoreCraftingStatus.EMPTY;
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
        return new TrinityDataCoreCraftingStatus(
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
        return this.storageProfile.unlimited() ? TrinityDataCoreMenuHost.UNLIMITED_STORAGE_CAPACITY : finiteCapacity;
    }

    public boolean hasActiveAccessHatch() {
        reevaluateAccessLease();
        return isStorageAvailable() && activeLeaseHatch() != null;
    }

    public @Nullable IGrid accessGrid() {
        reevaluateAccessLease();
        if (!isStorageAvailable()) {
            return null;
        }
        TrinityAccessHatchBlockEntity hatch = activeLeaseHatch();
        return hatch == null ? null : hatch.connectedGrid();
    }

    public IActionSource accessActionSource() {
        reevaluateAccessLease();
        if (!isStorageAvailable()) {
            throw new IllegalStateException("Trinity Data Core storage is not available");
        }
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch && isLeaseOwner(hatch)) {
                return hatch.actionSource();
            }
        }
        throw new IllegalStateException("Trinity Data Core has no active Trinity access hatch");
    }

    public boolean isLeaseOwner(TrinityAccessHatchBlockEntity hatch) {
        return this.accessLease != null && this.accessLease.matches(hatch.getBlockPos(), hatch.connectedGrid());
    }

    public void requestAccessLeaseReevaluation() {
        reevaluateAccessLease();
    }

    private void reevaluateAccessLease() {
        if (this.level == null || this.level.isClientSide() || this.structureRecheckInProgress) {
            return;
        }
        List<TrinityAccessHatchBlockEntity> candidates = isStorageAvailable() ? compartmentHost$getCompartments(mainDefinitionKey().structureName()).stream()
                .filter(TrinityAccessHatchBlockEntity.class::isInstance)
                .map(TrinityAccessHatchBlockEntity.class::cast)
                .filter(TrinityAccessHatchBlockEntity::isCandidateOnline)
                .sorted((left, right) -> left.getBlockPos().compareTo(right.getBlockPos()))
                .toList() : List.of();

        if (this.accessLease != null) {
            TrinityAccessHatchBlockEntity electedHatch = candidates.stream()
                    .filter(candidate -> this.accessLease.identifies(candidate.getBlockPos()))
                    .findFirst()
                    .orElse(null);
            if (electedHatch != null) {
                IGrid electedGrid = electedHatch.connectedGrid();
                if (this.accessLease.matches(electedHatch.getBlockPos(), electedGrid)) {
                    this.craftingRuntime.setPaused(!isCpuProviderAvailable());
                    return;
                }
                if (this.accessLease.grid() != null && hasPendingTrinityWork()) {
                    this.craftingRuntime.setPaused(true);
                    return;
                }
                this.accessLease = this.accessLease.bind(electedGrid);
                this.craftingRuntime.setPaused(!isCpuProviderAvailable());
                notifyTrinityAccessChanged();
                return;
            }
            if (hasPendingTrinityWork()) {
                boolean wasBound = this.accessLease.grid() != null;
                this.accessLease = this.accessLease.unbind();
                this.craftingRuntime.setPaused(true);
                if (wasBound) {
                    notifyTrinityAccessChanged();
                }
                return;
            }
        }

        if (this.accessLease == null && hasPendingTrinityWork()) {
            this.craftingRuntime.setPaused(true);
            if (!this.missingBusyLeaseReported) {
                this.missingBusyLeaseReported = true;
                LOGGER.error("Trinity host {} has retained work without a persistent access lease; execution remains paused",
                        this.worldPosition);
            }
            return;
        }

        BlockPos previousPosition = this.accessLease == null ? null : this.accessLease.hatchPosition();
        IGrid previousGrid = this.accessLease == null ? null : this.accessLease.grid();
        this.accessLease = candidates.isEmpty() ? null : TrinityAccessLease.elect(
                candidates.getFirst().getBlockPos(),
                candidates.getFirst().connectedGrid(),
                this.accessLeaseEpoch = Math.incrementExact(this.accessLeaseEpoch));
        this.missingBusyLeaseReported = false;
        this.craftingRuntime.setPaused(this.accessLease == null || !isCpuProviderAvailable());
        if (leaseChanged(previousPosition, previousGrid)) {
            setChanged();
            notifyTrinityAccessChanged();
        }
    }

    private boolean leaseChanged(@Nullable BlockPos previousPosition, @Nullable IGrid previousGrid) {
        if (this.accessLease == null) {
            return previousPosition != null;
        }
        return !this.accessLease.hatchPosition().equals(previousPosition) || this.accessLease.grid() != previousGrid;
    }

    private boolean hasPendingTrinityWork() {
        return this.craftingRuntime.hasBusyJobs() || this.patternCatalog.hasWork();
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
        long gameTime = this.level.getGameTime();
        for (TrinityPatternCatalog.ActiveSlot activeSlot : this.patternCatalog.activeSlots()) {
            if (!(activeSlot.core() instanceof TrinityPatternCoreBlockEntity patternCore)) {
                LOGGER.error("Trinity host {} catalog contains a non-block pattern core at {}",
                        this.worldPosition,
                        activeSlot.mount().position());
                throw new IllegalStateException("A formed Trinity catalog must contain block-backed pattern cores");
            }
            try {
                patternCore.executeOwnedSlot(this.hostId, activeSlot.coreSlot(), gameTime);
                routedAnyOutput |= routePendingOutputs(
                        patternCore,
                        activeSlot.route(),
                        craftingService,
                        storage);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to execute or route Trinity pattern core {} slot {} at {} for host {}",
                        patternCore.coreId(),
                        activeSlot.coreSlot(),
                        activeSlot.mount().position(),
                        this.worldPosition,
                        exception);
            }
        }
        if (routedAnyOutput) {
            notifyTrinityStorageChanged();
        }
    }

    private boolean routePendingOutputs(TrinityPatternCoreBlockEntity core,
                                        PatternRoute route,
                                        CraftingService craftingService,
                                        TrinityDataCoreStorageSavedData storage) {
        try (PendingOutputCursor pending = core.openPendingOutputCursor(route)) {
            return this.patternOutputRouter.route(
                    pending,
                    craftingService::getRequestedAmount,
                    craftingService::insertIntoCpus,
                    (key, amount, mode) -> storage.insert(
                            this.storageId,
                            key,
                            amount,
                            mode,
                            this.storageProfile));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to route Trinity pattern core {} slot {} outputs for host {}",
                    core.coreId(),
                    route.slot(),
                    this.worldPosition,
                    exception);
            return false;
        }
    }

    private void setIdentity(UUID storageId, UUID hostId) {
        boolean storageChanged = !this.storageId.equals(storageId);
        boolean hostChanged = !this.hostId.equals(hostId);
        if (!storageChanged && !hostChanged) {
            return;
        }
        if (hostChanged) {
            clearPatternCatalog();
        }
        this.storageId = storageId;
        if (hostChanged) {
            this.hostId = hostId;
            this.patternCatalog = new TrinityPatternCatalogImpl(hostId);
            this.patternCatalogValid = false;
            this.lastPatternCoreHealthCheckTick = Long.MIN_VALUE;
        }
        requestStructureRecheck();
        setChanged();
    }

    public static void requestRecheckAt(Level level, BlockPos origin) {
        if (!(level instanceof ServerLevel)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(origin);
        if (blockEntity instanceof TrinityDataCoreBlockEntity host) {
            host.requestStructureRecheck();
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
        clearPatternCatalog();
        this.storageId = data.getUUID(STORAGE_ID_TAG);
        this.hostId = data.getUUID(HOST_ID_TAG);
        this.patternCatalog = new TrinityPatternCatalogImpl(this.hostId);
        this.patternCatalogValid = false;
        this.formed = data.getBoolean(FORMED_TAG);
        this.matchedPositions = List.of();
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
        restoreAccessLease(data);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        data.putUUID(STORAGE_ID_TAG, this.storageId);
        data.putUUID(HOST_ID_TAG, this.hostId);
        data.putBoolean(FORMED_TAG, this.formed);
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
        data.putLong(ACCESS_LEASE_EPOCH_TAG, this.accessLeaseEpoch);
        if (this.accessLease != null) {
            data.putLong(ACCESS_LEASE_HATCH_POSITION_TAG, this.accessLease.hatchPosition().asLong());
        }
    }

    private void restoreAccessLease(CompoundTag data) {
        long persistedEpoch = data.contains(ACCESS_LEASE_EPOCH_TAG, Tag.TAG_LONG) ? data.getLong(ACCESS_LEASE_EPOCH_TAG) : 0L;
        if (persistedEpoch < 0L) {
            this.accessLease = null;
            this.accessLeaseEpoch = 0L;
            LOGGER.error("Discarding negative Trinity access lease epoch {} at {}", persistedEpoch, this.worldPosition);
            return;
        }
        this.accessLeaseEpoch = persistedEpoch;
        if (data.contains(ACCESS_LEASE_HATCH_POSITION_TAG, Tag.TAG_LONG)) {
            this.accessLease = TrinityAccessLease.restore(
                    BlockPos.of(data.getLong(ACCESS_LEASE_HATCH_POSITION_TAG)),
                    persistedEpoch);
        } else {
            this.accessLease = null;
            if (this.craftingRuntime.hasBusyJobs()) {
                this.missingBusyLeaseReported = true;
                LOGGER.error("Trinity host {} restored CPU work without an access lease; a network cannot be recovered safely",
                        this.worldPosition);
            }
        }
    }

    private void discardPersistedTrinityState() {
        clearPatternCatalog();
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
        this.accessLease = null;
        this.accessLeaseEpoch = 0L;
        this.missingBusyLeaseReported = false;
        this.recheckRequested = true;
        this.cpuStructureRecheckRequested = true;
        this.craftingStructureRecheckRequested = true;
        this.lastCpuStructureRecheckTick = Long.MIN_VALUE;
        this.lastCraftingStructureRecheckTick = Long.MIN_VALUE;
        this.lastPatternCoreHealthCheckTick = Long.MIN_VALUE;
        this.mainStructureFrontFacing = null;
        this.mainStructureFlipped = false;
    }

    private void observeMultiBlockDefinitionRevision() {
        long currentRevision = ModVerticalMultiBlocks.JSON_MULTI_BLOCKS.revision();
        if (this.observedMultiBlockDefinitionRevision != currentRevision) {
            this.observedMultiBlockDefinitionRevision = currentRevision;
            requestStructureRecheck();
        }
    }

    private void updateScheduledStructureMatches() {
        long gameTime = this.level.getGameTime();
        int mainRecheckInterval = this.formed ? FORMED_MAIN_RECHECK_INTERVAL_TICKS : UNFORMED_MAIN_RECHECK_INTERVAL_TICKS;
        boolean mainRecheckDue = this.recheckRequested || isPeriodicRecheckDue(gameTime, mainRecheckInterval, 0);
        boolean cpuRecheckDue = this.cpuStructureRecheckRequested || isChildPeriodicRecheckDue(
                gameTime,
                this.cpuStructureFormed,
                0,
                this.lastCpuStructureRecheckTick);
        boolean craftingRecheckDue = this.craftingStructureRecheckRequested || isChildPeriodicRecheckDue(
                gameTime,
                this.craftingStructureFormed,
                CRAFTING_RECHECK_PHASE_OFFSET_TICKS,
                this.lastCraftingStructureRecheckTick);
        boolean childRechecksAvailable = this.formed && this.mainStructureFrontFacing != null;
        if (!mainRecheckDue && (!childRechecksAvailable || (!cpuRecheckDue && !craftingRecheckDue))) {
            return;
        }

        this.structureRecheckInProgress = true;
        try {
            if (mainRecheckDue) {
                recheckMainStructure();
            }
            if (!this.formed || this.mainStructureFrontFacing == null) {
                return;
            }
            if (this.cpuStructureRecheckRequested || isChildPeriodicRecheckDue(
                    gameTime,
                    this.cpuStructureFormed,
                    0,
                    this.lastCpuStructureRecheckTick)) {
                recheckCpuStructure(gameTime);
            }
            if (this.craftingStructureRecheckRequested || isChildPeriodicRecheckDue(
                    gameTime,
                    this.craftingStructureFormed,
                    CRAFTING_RECHECK_PHASE_OFFSET_TICKS,
                    this.lastCraftingStructureRecheckTick)) {
                recheckCraftingStructure(gameTime);
            }
        } finally {
            this.structureRecheckInProgress = false;
        }
    }

    private void recheckMainStructure() {
        this.recheckRequested = false;
        try {
            if (updateMainStructureMatch(this.level)) {
                this.cpuStructureRecheckRequested = true;
                this.craftingStructureRecheckRequested = true;
            }
        } catch (RuntimeException exception) {
            this.recheckRequested = true;
            this.cpuStructureRecheckRequested = true;
            this.craftingStructureRecheckRequested = true;
            throw exception;
        }
    }

    private void recheckCpuStructure(long gameTime) {
        this.cpuStructureRecheckRequested = false;
        try {
            updateCpuStructureMatch(
                    new LevelStructureWorldView(this.level),
                    this.mainStructureFrontFacing,
                    this.mainStructureFlipped);
            this.lastCpuStructureRecheckTick = gameTime;
        } catch (RuntimeException exception) {
            this.cpuStructureRecheckRequested = true;
            throw exception;
        }
    }

    private void recheckCraftingStructure(long gameTime) {
        this.craftingStructureRecheckRequested = false;
        try {
            updateCraftingStructureMatch(
                    new LevelStructureWorldView(this.level),
                    this.mainStructureFrontFacing,
                    this.mainStructureFlipped);
            this.lastCraftingStructureRecheckTick = gameTime;
        } catch (RuntimeException exception) {
            this.craftingStructureRecheckRequested = true;
            throw exception;
        }
    }

    private boolean isChildPeriodicRecheckDue(long gameTime,
                                              boolean childFormed,
                                              int phaseOffset,
                                              long lastRecheckTick) {
        if (lastRecheckTick == gameTime) {
            return false;
        }
        int interval = childFormed ? FORMED_CHILD_RECHECK_INTERVAL_TICKS : UNFORMED_CHILD_RECHECK_INTERVAL_TICKS;
        return isPeriodicRecheckDue(gameTime, interval, phaseOffset);
    }

    private boolean isPeriodicRecheckDue(long gameTime, int interval, int phaseOffset) {
        return Math.floorMod(gameTime + this.worldPosition.asLong() + phaseOffset, interval) == 0;
    }

    static int patternCoreHealthCheckPhase(UUID hostId) {
        return Math.floorMod(hostId.hashCode(), PATTERN_CORE_HEALTH_CHECK_INTERVAL_TICKS);
    }

    static boolean isPatternCoreHealthCheckDue(long gameTime, UUID hostId) {
        return Math.floorMod(gameTime, (long) PATTERN_CORE_HEALTH_CHECK_INTERVAL_TICKS) ==
                patternCoreHealthCheckPhase(hostId);
    }

    private boolean hasHealthyPatternCoreIdentities() {
        long layoutRevision = this.patternCatalog.layoutSnapshot().revision();
        for (TrinityPatternCatalog.CoreMount mount : this.patternCatalog.mountedCores()) {
            BlockEntity blockEntity = this.level.getBlockEntity(mount.position());
            BlockState state = this.level.getBlockState(mount.position());
            if (blockEntity != mount.core() ||
                    !(state.getBlock() instanceof TrinityCoreComponent component) ||
                    component.kind() != TrinityCoreKind.PATTERN_PROCESSING ||
                    component.patternCapacity() != mount.blockCapacity() ||
                    mount.core().patternCapacity() != mount.blockCapacity() ||
                    !this.patternCatalog.isMountCurrent(layoutRevision, mount)) {
                LOGGER.warn(
                        "Invalidating Trinity host {} pattern catalog after core identity health check failed at {}",
                        this.worldPosition,
                        mount.position());
                return false;
            }
        }
        return true;
    }

    private boolean updateMainStructureMatch(Level level) {
        JsonMultiBlockDefinition definition = requireMainJsonDefinition();
        Direction preferredFrontFacing = getStructureFrontFacing(level);
        StructureWorldView world = new LevelStructureWorldView(level);
        StructureMatchResult result;
        if (this.formed && this.mainStructureFrontFacing != null) {
            result = JsonMultiBlockPatternMatcher.matchExact(
                    definition.pattern(),
                    world,
                    this.worldPosition,
                    this.mainStructureFrontFacing,
                    this.mainStructureFlipped,
                    mainDefinitionKey().structureName());
            if (!result.matched()) {
                result = JsonMultiBlockPatternMatcher.match(
                        definition.pattern(),
                        world,
                        this.worldPosition,
                        preferredFrontFacing,
                        mainDefinitionKey().structureName());
            }
        } else {
            result = JsonMultiBlockPatternMatcher.match(
                    definition.pattern(),
                    world,
                    this.worldPosition,
                    preferredFrontFacing,
                    mainDefinitionKey().structureName());
        }

        if (result.matched()) {
            Map<BlockPos, CompartmentType> declaredCompartments = JsonMultiBlockCompartmentPredicate.declaredCompartments(
                    result.context());
            PatternDiagnostic compartmentFailure = this.compartmentBinder.validate(world, result, declaredCompartments);
            if (compartmentFailure != null) {
                boolean topologyChanged = this.formed || this.mainStructureFrontFacing != null;
                applyFailure(compartmentFailure, mainDefinitionKey().structureName());
                this.mainStructureFrontFacing = null;
                this.mainStructureFlipped = false;
                return topologyChanged;
            }
            boolean topologyChanged = !this.formed ||
                    !this.matchedPositions.equals(result.positions()) ||
                    this.mainStructureFrontFacing != result.frontFacing() ||
                    this.mainStructureFlipped != result.flipped();
            applyMatch(world, result.positions(), declaredCompartments, mainDefinitionKey().structureName());
            this.mainStructureFrontFacing = result.frontFacing();
            this.mainStructureFlipped = result.flipped();
            return topologyChanged;
        }

        boolean topologyChanged = this.formed || this.mainStructureFrontFacing != null;
        applyFailure(result.diagnostic(), mainDefinitionKey().structureName());
        this.mainStructureFrontFacing = null;
        this.mainStructureFlipped = false;
        return topologyChanged;
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
            List<TrinityPatternCatalog.CoreMount> previousMounts = this.patternCatalog.mountedCores();
            TrinityPatternCatalog.RebuildResult rebuild = this.patternCatalog.rebuild(scan.mounts());
            this.patternCatalogValid = rebuild.valid();
            if (!rebuild.valid()) {
                releasePatternCoreBindings(previousMounts);
                applyCraftingFailure(rebuild.failureReason(), rebuild.failurePosition());
                return;
            }
            TrinityPatternCatalog.CoreMount bindingFailure = bindPatternCoreBindings(scan.mounts());
            if (bindingFailure != null) {
                releasePatternCoreBindings(scan.mounts());
                releasePatternCoreBindings(previousMounts);
                this.patternCatalog.invalidateLayout();
                this.patternCatalogValid = false;
                applyCraftingFailure(
                        "Trinity pattern processing core is already mounted by another active host",
                        bindingFailure.position());
                return;
            }
            releaseStalePatternCoreBindings(previousMounts, scan.mounts());
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
            notifyTrinityCpuChanged();
            setChanged();
        }
    }

    private void applyCpuFailure(PatternDiagnostic diagnostic) {
        this.craftingRuntime.setPaused(true);
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
                    "Trinity Data Core structure '{}' failed at {}: {}",
                    CPU_STRUCTURE_NAME,
                    diagnostic.position(),
                    diagnostic.message());
        }
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = nextFailureReason;
        this.cpuLastFailurePosition = nextFailurePosition;
        notifyTrinityCpuChanged();
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
            notifyTrinityPatternLayoutChanged();
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
        boolean catalogWithdrawn = withdrawPatternCatalog();
        if (!this.craftingStructureFormed &&
                this.craftingStructureMatchedBlockCount == 0 &&
                Objects.equals(this.craftingLastFailureReason, nextFailureReason) &&
                Objects.equals(this.craftingLastFailurePosition, nextFailurePosition)) {
            if (catalogWithdrawn) {
                notifyTrinityPatternLayoutChanged();
            }
            return;
        }
        if (this.craftingStructureFormed || !Objects.equals(this.craftingLastFailureReason, nextFailureReason) ||
                !Objects.equals(this.craftingLastFailurePosition, nextFailurePosition)) {
            LOGGER.warn(
                    "Trinity Data Core structure '{}' failed at {}: {}",
                    CRAFTING_STRUCTURE_NAME,
                    nextFailurePosition,
                    nextFailureReason);
        }
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingLastFailureReason = nextFailureReason;
        this.craftingLastFailurePosition = nextFailurePosition;
        notifyTrinityPatternLayoutChanged();
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

    static JsonMultiBlockStructureKey autoBuildDefinitionKey(int structureIndex) {
        return switch (structureIndex) {
            case TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX -> mainDefinitionKey();
            case TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX -> cpuDefinitionKey();
            case TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX -> craftingDefinitionKey();
            default -> throw new IllegalArgumentException("Unknown Trinity auto-build structure index: " + structureIndex);
        };
    }

    static String autoBuildStructureName(int structureIndex) {
        return switch (structureIndex) {
            case TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX -> mainDefinitionKey().structureName();
            case TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX -> CPU_STRUCTURE_NAME;
            case TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX -> CRAFTING_STRUCTURE_NAME;
            default -> throw new IllegalArgumentException("Unknown Trinity auto-build structure index: " + structureIndex);
        };
    }

    private static JsonMultiBlockDefinition autoBuildDefinition(int structureIndex) {
        return requireJsonDefinition(autoBuildDefinitionKey(structureIndex));
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
        return JsonMultiBlockStructureKey.main(ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DATA_CORE_ID));
    }

    private static JsonMultiBlockStructureKey cpuDefinitionKey() {
        return new JsonMultiBlockStructureKey(
                ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DATA_CORE_ID),
                CPU_STRUCTURE_NAME);
    }

    private static JsonMultiBlockStructureKey craftingDefinitionKey() {
        return new JsonMultiBlockStructureKey(
                ResourceLocation.parse(ModVerticalMultiBlocks.TRINITY_DATA_CORE_ID),
                CRAFTING_STRUCTURE_NAME);
    }

    private static TrinityDataCoreCraftingCoreProfile readCraftingProfile(CompoundTag data) {
        if (!data.contains(CRAFTING_PATTERN_CORE_COUNT_TAG) && !data.contains(CRAFTING_PATTERN_CAPACITY_TAG)) {
            return TrinityDataCoreCraftingCoreProfile.EMPTY;
        }
        return new TrinityDataCoreCraftingCoreProfile(
                data.getInt(CRAFTING_PATTERN_CORE_COUNT_TAG),
                data.getInt(CRAFTING_PATTERN_CAPACITY_TAG));
    }

    private void notifyTrinityStorageChanged() {
        refreshTrinityAccessHatches(TrinityAccessHatchBlockEntity::refreshTrinityStorageAccess);
    }

    private void notifyTrinityCpuChanged() {
        refreshTrinityAccessHatches(TrinityAccessHatchBlockEntity::refreshTrinityCpuAccess);
    }

    private void notifyTrinityPatternPublicationChanged() {
        refreshTrinityAccessHatches(TrinityAccessHatchBlockEntity::refreshTrinityPatternAccess);
    }

    private void notifyTrinityPatternLayoutChanged() {
        refreshTrinityAccessHatches(TrinityAccessHatchBlockEntity::refreshTrinityPatternAccess);
    }

    private void notifyTrinityAccessChanged() {
        refreshTrinityAccessHatches(TrinityAccessHatchBlockEntity::refreshTrinityAccess);
    }

    private void refreshTrinityAccessHatches(Consumer<TrinityAccessHatchBlockEntity> refresh) {
        for (CompartmentPart part : compartmentHost$getCompartments(mainDefinitionKey().structureName())) {
            if (part instanceof TrinityAccessHatchBlockEntity hatch) {
                refresh.accept(hatch);
            }
        }
    }

    /** Withdraws every public pattern route while retaining queued work for the current network lease. */
    private boolean withdrawPatternCatalog() {
        List<TrinityPatternCatalog.CoreMount> mountedCores = this.patternCatalog.mountedCores();
        boolean changed = this.patternCatalogValid || this.patternCatalog.layoutSnapshot().active();
        this.patternCatalog.invalidateLayout();
        this.patternCatalogValid = false;
        releasePatternCoreBindings(mountedCores);
        return changed;
    }

    private void clearPatternCatalog() {
        List<TrinityPatternCatalog.CoreMount> mountedCores = this.patternCatalog.mountedCores();
        this.patternCatalog.clear();
        releasePatternCoreBindings(mountedCores);
    }

    @Nullable
    private TrinityPatternCatalog.CoreMount bindPatternCoreBindings(
                                                                    List<TrinityPatternCatalog.CoreMount> mounts) {
        for (TrinityPatternCatalog.CoreMount mount : mounts) {
            if (!(mount.core() instanceof TrinityPatternCoreBlockEntity core) || !core.bindPatternHost(this)) {
                return mount;
            }
        }
        return null;
    }

    private void releasePatternCoreBindings(List<TrinityPatternCatalog.CoreMount> mounts) {
        for (TrinityPatternCatalog.CoreMount mount : mounts) {
            if (mount.core() instanceof TrinityPatternCoreBlockEntity core) {
                core.unbindPatternHost(this);
            }
        }
    }

    private void releaseStalePatternCoreBindings(List<TrinityPatternCatalog.CoreMount> previousMounts,
                                                 List<TrinityPatternCatalog.CoreMount> currentMounts) {
        for (TrinityPatternCatalog.CoreMount previous : previousMounts) {
            boolean retained = false;
            for (TrinityPatternCatalog.CoreMount current : currentMounts) {
                if (previous.core() == current.core()) {
                    retained = true;
                    break;
                }
            }
            if (!retained && previous.core() instanceof TrinityPatternCoreBlockEntity core) {
                core.unbindPatternHost(this);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        this.loaded = false;
        this.craftingRuntime.setPaused(true);
        withdrawPatternCatalog();
        if (this.accessLease != null) {
            this.accessLease = this.accessLease.unbind();
        }
        requestStructureRecheck();
        notifyTrinityAccessChanged();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.loaded = false;
        this.craftingRuntime.setPaused(true);
        withdrawPatternCatalog();
        if (this.accessLease != null) {
            this.accessLease = this.accessLease.unbind();
        }
        clearCompartmentBindings(mainDefinitionKey().structureName());
        super.setRemoved();
    }

    /** Cancels active CPU jobs only when the host block is being permanently removed from the world. */
    public void onPermanentRemoval() {
        this.craftingRuntime.cancelAllJobs();
        recoverCancelledCpuInventory();
        this.loaded = false;
        this.craftingRuntime.setPaused(true);
        clearPatternCatalog();
        this.patternCatalogValid = false;
        this.accessLease = null;
        clearCompartmentBindings(mainDefinitionKey().structureName());
    }

    private void recoverCancelledCpuInventory() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            String message = "Trinity host " + this.worldPosition +
                    " cannot durably recover cancelled CPU inventory without a server level";
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(serverLevel.getServer());
        boolean recoveredAll = this.craftingRuntime.recoverCancelledInventory((key, amount) -> storage.insert(
                this.storageId,
                key,
                amount,
                Actionable.MODULATE));
        if (!recoveredAll) {
            String message = "Trinity host " + this.worldPosition +
                    " retained CPU inventory after durable removal recovery";
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
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

    private PatternCatalogScanResult scanPatternCores(StructureWorldView world, List<BlockPos> positions) {
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
            if (!patternCore.isCoreStateReady()) {
                return PatternCatalogScanResult.failure(
                        pos,
                        "Trinity pattern processing core at " + pos + " has rejected persisted state");
            }
            if (!patternCore.canBindPatternHost(this)) {
                return PatternCatalogScanResult.failure(
                        pos,
                        "Trinity pattern processing core at " + pos +
                                " is already mounted by another active host");
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
        withdrawPatternCatalog();
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingLastFailureReason = failureReason;
        this.craftingLastFailurePosition = failurePosition;
    }

    /**
     * Resolves AE2 part host sides from the exact predicate selected for each expanded pattern coordinate.
     */
    private static final class AutoBuildPartResolver implements PartSideResolver {

        /** World view used to evaluate direction suppliers against the current pattern position. */
        private final StructureWorldView world;
        /** Controller origin supplied to MDLib direction callbacks. */
        private final BlockPos origin;
        /** Stable name supplied to MDLib diagnostics and direction callbacks. */
        private final String structureName;
        /** Actual main-structure front selected for this build. */
        private final Direction front;
        /** Actual mirror state selected for this build. */
        private final boolean flipped;
        /** Exact expanded pattern predicate at every buildable world position. */
        private final Map<BlockPos, TraceabilityPredicate> predicates;

        private AutoBuildPartResolver(StructureWorldView world,
                                      BlockPos origin,
                                      String structureName,
                                      Direction front,
                                      boolean flipped,
                                      Map<BlockPos, TraceabilityPredicate> predicates) {
            this.world = world;
            this.origin = origin;
            this.structureName = structureName;
            this.front = front;
            this.flipped = flipped;
            this.predicates = predicates;
        }

        /**
         * Returns the predicate-declared side, or the explicit AE2 center-cable sentinel side.
         *
         * <p>
         * AE2 cable parts always occupy the center slot and ignore the non-null side required by
         * {@code PartPlacement}. {@link Direction#UP} is therefore a stable API sentinel for that documented center
         * behavior, not a side inferred from a position. Every non-cable part without a predicate direction remains
         * unresolved and is rejected by the atomic builder.
         * </p>
         */
        @Nullable
        @Override
        public Direction resolve(BlockPos position, ItemStack partStack) {
            TraceabilityPredicate predicate = this.predicates.get(position);
            if (predicate == null) {
                return null;
            }
            MultiblockState state = new MultiblockState(this.world, this.origin, this.structureName);
            if (!state.update(position, predicate)) {
                return null;
            }
            Direction declaredDirection = predicate.getDirection(state, this.front, Direction.NORTH, this.flipped);
            if (declaredDirection != null) {
                return declaredDirection;
            }
            if (partStack.getItem() instanceof IPartItem<?> partItem &&
                    CablePart.class.isAssignableFrom(partItem.getPartClass())) {
                return Direction.UP;
            }
            return null;
        }
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
