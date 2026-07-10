package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternTerminalPartition;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AE network hatch that exposes the bound Digital Construct Flower UUID storage instead of storing contents locally.
 */
public class TrinityAccessHatchBlockEntity extends AENetworkedBlockEntity implements CompartmentPart {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final MEStorage networkStorage = new HatchStorage();
    private final IStorageProvider storageProvider = new HatchStorageProvider();
    private final ICraftingProvider craftingProvider = new HatchCraftingProvider();
    @Nullable
    private CompartmentHost compartmentHost;
    @Nullable
    private String structureName;
    @Nullable
    private String lastUnavailableReason;
    private List<TrinityPatternTerminalPartition> terminalPartitions = List.of();

    public TrinityAccessHatchBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .addService(ICraftingProvider.class, this.craftingProvider)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(ModBlocks.TRINITY_ACCESS_HATCH.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        updateActiveState();
        refreshTerminalPartitionsSafely();
    }

    public void refreshTrinityAccess() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        invalidateCapabilities();
        updateActiveState();
        requestStorageUpdate();
        requestCraftingProviderUpdate();
        notifyCraftingCpuChanged();
        refreshTerminalPartitionsSafely();
        setChanged();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        if (flower != null) {
            flower.requestAccessLeaseReevaluation();
        }
    }

    @Override
    public void onChunkUnloaded() {
        detachTerminalPartitions();
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        super.onChunkUnloaded();
        if (flower != null) {
            flower.requestAccessLeaseReevaluation();
        }
    }

    @Override
    public void setRemoved() {
        detachTerminalPartitions();
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        super.setRemoved();
        if (flower != null) {
            flower.requestAccessLeaseReevaluation();
            flower.requestStructureRecheck();
        }
    }

    private void updateActiveState() {
        boolean active = isAccessOnline();
        BlockState state = getBlockState();
        if (state.hasProperty(CompartmentBlock.ACTIVE) && state.getValue(CompartmentBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(CompartmentBlock.ACTIVE, active), 3);
        }
    }

    @Override
    public CompartmentType compartmentType() {
        return CompartmentType.TRINITY_ACCESS;
    }

    @Override
    public VerticalMultiBlockPos compartmentPos() {
        return new VerticalMultiBlockPos(this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }

    @Nullable
    @Override
    public CompartmentHost compartmentHost() {
        return this.compartmentHost;
    }

    @Nullable
    @Override
    public String compartmentStructureName() {
        return this.structureName;
    }

    @Override
    public CompartmentStorage compartmentStorage() {
        return UnavailableCompartmentStorage.INSTANCE;
    }

    @Override
    public void compartment$bindToHost(String structureName, CompartmentHost host) {
        if (this.compartmentHost == host && structureName.equals(this.structureName)) {
            if (!host.compartmentHost$getCompartments(structureName).contains(this)) {
                CompartmentPart.super.compartment$bindToHost(structureName, host);
                refreshTrinityAccess();
            }
            return;
        }

        if (this.compartmentHost != null) {
            CompartmentPart.super.compartment$unbindFromHost(this.structureName, this.compartmentHost);
        }

        CompartmentPart.super.compartment$bindToHost(structureName, host);
        this.compartmentHost = host;
        this.structureName = structureName;
        this.lastUnavailableReason = null;
        refreshTrinityAccess();
    }

    @Override
    public void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        CompartmentPart.super.compartment$unbindFromHost(structureName, host);
        if (this.compartmentHost == host && this.structureName.equals(structureName)) {
            this.compartmentHost = null;
            this.structureName = null;
            this.lastUnavailableReason = null;
            refreshTrinityAccess();
        }
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context) {
        CompartmentPart.super.verticalMultiBlock$addedToController(controller, structureName, context);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller, String structureName) {
        CompartmentPart.super.verticalMultiBlock$removedFromController(controller, structureName);
        if (this.compartmentHost == controller && this.structureName.equals(structureName)) {
            this.compartmentHost = null;
            this.structureName = null;
            this.lastUnavailableReason = null;
            refreshTrinityAccess();
        }
    }

    public @Nullable TrinityDataCoreCraftingRuntime boundCraftingRuntime() {
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        return flower == null || !isCandidateOnline() || !flower.isLeaseOwner(this) ||
                !flower.canExposeTrinityCapabilities() ? null : flower.getCraftingRuntime();
    }

    public @Nullable IGrid connectedGrid() {
        if (boundFlower(false) == null) {
            return null;
        }
        var node = this.getMainNode().getNode();
        if (node == null) {
            return null;
        }
        return node.getGrid();
    }

    public @Nullable IGrid accessGrid() {
        return isAccessOnline() ? connectedGrid() : null;
    }

    public boolean isCandidateOnline() {
        if (boundFlower(false) == null) {
            return false;
        }
        var node = this.getMainNode().getNode();
        return node != null && node.isActive();
    }

    public boolean isAccessOnline() {
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        return flower != null && flower.isLeaseOwner(this) && flower.canExposeTrinityCapabilities() &&
                isCandidateOnline();
    }

    /** Returns the immutable set of terminal partitions currently owned by this hatch. */
    public List<TrinityPatternTerminalPartition> terminalPartitions() {
        return this.terminalPartitions;
    }

    public IActionSource actionSource() {
        return IActionSource.ofMachine(this);
    }

    @Nullable
    private DigitalConstructFlowerBlockEntity boundFlower() {
        return boundFlower(true);
    }

    @Nullable
    private DigitalConstructFlowerBlockEntity boundFlower(boolean logUnavailable) {
        if (this.compartmentHost == null || this.structureName == null) {
            logUnavailable(logUnavailable, "not bound to a trinity structure");
            return null;
        }
        if (!(this.compartmentHost instanceof DigitalConstructFlowerBlockEntity flower)) {
            logUnavailable(logUnavailable, "bound host is not a Digital Construct Flower");
            return null;
        }
        if (!flower.isStructureFormed()) {
            logUnavailable(logUnavailable, "bound Digital Construct Flower structure is not formed");
            return null;
        }
        this.lastUnavailableReason = null;
        return flower;
    }

    @Nullable
    private DigitalConstructFlowerBlockEntity patternProviderHost() {
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        if (flower == null || !isCandidateOnline() || !flower.isLeaseOwner(this) ||
                !flower.isPatternProviderAvailable()) {
            return null;
        }
        return flower;
    }

    private void logUnavailable(boolean shouldLog, String reason) {
        if (!shouldLog) {
            return;
        }
        if (reason.equals(this.lastUnavailableReason)) {
            return;
        }
        this.lastUnavailableReason = reason;
        LOGGER.warn("Trinity access hatch at {} exposes empty storage: {}", this.worldPosition, reason);
    }

    private void requestStorageUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    private void requestCraftingProviderUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            ICraftingProvider.requestUpdate(this.getMainNode());
        }
    }

    private void notifyCraftingCpuChanged() {
        var node = this.getMainNode().getNode();
        if (node != null) {
            node.getGrid().postEvent(new GridCraftingCpuChange(node));
        }
    }

    private void refreshTerminalPartitionsSafely() {
        try {
            refreshTerminalPartitions();
        } catch (RuntimeException exception) {
            detachTerminalPartitions();
            LOGGER.error("Failed to mount Trinity pattern terminal partitions at {}", this.worldPosition, exception);
        }
    }

    private void refreshTerminalPartitions() {
        DigitalConstructFlowerBlockEntity flower = patternProviderHost();
        IGridNode accessNode = this.getMainNode().getNode();
        if (flower == null || !(this.level instanceof ServerLevel serverLevel) || accessNode == null ||
                !accessNode.isActive()) {
            detachTerminalPartitions();
            return;
        }

        IGrid grid = accessNode.getGrid();
        List<TrinityPatternTerminalPartition> desired = TrinityPatternTerminalPartition.createLayout(
                flower.getHostId(),
                flower.getPatternCatalog().mountedCores(),
                terminalGroup());
        Map<TrinityPatternTerminalPartition.PartitionKey, TrinityPatternTerminalPartition> existingByKey = new HashMap<>();
        for (TrinityPatternTerminalPartition existing : this.terminalPartitions) {
            existingByKey.put(existing.key(), existing);
        }

        List<TrinityPatternTerminalPartition> reconciled = desired.stream().map(next -> {
            TrinityPatternTerminalPartition existing = existingByKey.remove(next.key());
            if (existing != null && existing.hasSameLayout(next)) {
                return existing;
            }
            if (existing != null) {
                existing.detach();
            }
            return next;
        }).toList();
        for (TrinityPatternTerminalPartition removed : existingByKey.values()) {
            removed.detach();
        }

        this.terminalPartitions = List.copyOf(reconciled);
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            if (!partition.isAttachedTo(grid)) {
                partition.detach();
                partition.attach(serverLevel, accessNode);
            }
        }
    }

    private PatternContainerGroup terminalGroup() {
        AEItemKey icon = AEItemKey.of(ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get());
        return new PatternContainerGroup(icon, ModBlocks.DIGITAL_CONSTRUCT_FLOWER.get().getName(), List.of());
    }

    private void detachTerminalPartitions() {
        for (TrinityPatternTerminalPartition partition : this.terminalPartitions) {
            partition.detach();
        }
        this.terminalPartitions = List.of();
    }

    private final class HatchStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            DigitalConstructFlowerBlockEntity flower = boundFlower(false);
            if (flower != null && flower.isLeaseOwner(TrinityAccessHatchBlockEntity.this) &&
                    flower.canExposeTrinityCapabilities()) {
                storageMounts.mount(networkStorage, 0);
            }
        }
    }

    private final class HatchCraftingProvider implements ICraftingProvider {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            DigitalConstructFlowerBlockEntity flower = patternProviderHost();
            return flower == null ? List.of() : flower.getPatternCatalog().getAvailablePatterns();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            DigitalConstructFlowerBlockEntity flower = patternProviderHost();
            if (flower == null || level == null || level.isClientSide()) {
                return false;
            }
            return flower.getPatternCatalog().pushPattern(patternDetails, inputHolder, level.getGameTime());
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private final class HatchStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            DigitalConstructFlowerBlockEntity flower = boundFlower();
            if (!canUseStorage(flower) || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long inserted = TrinityDataCoreStorageSavedData.get(serverLevel.getServer())
                    .insert(flower.getStorageId(), what, amount, mode, flower.storageProfile());
            if (inserted > 0L && mode == Actionable.MODULATE) {
                requestStorageUpdate();
            }
            return inserted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            DigitalConstructFlowerBlockEntity flower = boundFlower();
            if (!canUseStorage(flower) || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long extracted = TrinityDataCoreStorageSavedData.get(serverLevel.getServer())
                    .extract(flower.getStorageId(), what, amount, mode);
            if (extracted > 0L && mode == Actionable.MODULATE) {
                requestStorageUpdate();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            DigitalConstructFlowerBlockEntity flower = boundFlower();
            if (!canUseStorage(flower) || !(level instanceof ServerLevel serverLevel)) {
                return;
            }
            TrinityDataCoreStorageSavedData.get(serverLevel.getServer()).addAvailableStacks(flower.getStorageId(), out);
        }

        @Override
        public Component getDescription() {
            return ModBlocks.TRINITY_ACCESS_HATCH.get().getName();
        }

        private boolean canUseStorage(@Nullable DigitalConstructFlowerBlockEntity flower) {
            return flower != null && isCandidateOnline() &&
                    flower.isLeaseOwner(TrinityAccessHatchBlockEntity.this) &&
                    flower.canExposeTrinityCapabilities();
        }
    }
}
