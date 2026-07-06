package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.UnavailableCompartmentStorage;
import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCraftingRuntime;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.DigitalConstructFlowerStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
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
import java.util.Objects;
import java.util.Set;

/**
 * AE network hatch that exposes the bound Digital Construct Flower UUID storage instead of storing contents locally.
 */
public class MeStorageAccessHatchBlockEntity extends AENetworkedBlockEntity implements CompartmentPart {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final MEStorage networkStorage = new HatchStorage();
    private final IStorageProvider storageProvider = new HatchStorageProvider();
    @Nullable
    private CompartmentHost compartmentHost;
    @Nullable
    private String structureName;
    @Nullable
    private String lastUnavailableReason;

    public MeStorageAccessHatchBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.ME_STORAGE_ACCESS_HATCH_BLOCK_ENTITY.get(), blockPos, blockState);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .setVisualRepresentation(ModBlocks.ME_STORAGE_ACCESS_HATCH.get())
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
        boolean active = accessGrid() != null;
        BlockState state = getBlockState();
        if (state.hasProperty(CompartmentBlock.ACTIVE) && state.getValue(CompartmentBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(CompartmentBlock.ACTIVE, active), 3);
        }
    }

    @Override
    public CompartmentType compartmentType() {
        return CompartmentType.ME_STORAGE_ACCESS;
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
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(host, "host");

        if (this.compartmentHost == host && structureName.equals(this.structureName)) {
            if (!host.compartmentHost$getCompartments(structureName).contains(this)) {
                CompartmentPart.super.compartment$bindToHost(structureName, host);
            }
            return;
        }

        if (this.compartmentHost != null) {
            if (this.structureName == null) {
                throw new IllegalStateException("Bound ME storage access hatch at " + this.worldPosition +
                        " has no structure name before rebinding");
            }
            CompartmentPart.super.compartment$unbindFromHost(this.structureName, this.compartmentHost);
        }

        CompartmentPart.super.compartment$bindToHost(structureName, host);
        this.compartmentHost = host;
        this.structureName = structureName;
        this.lastUnavailableReason = null;
        invalidateCapabilities();
        requestStorageUpdate();
        setChanged();
    }

    @Override
    public void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        CompartmentPart.super.compartment$unbindFromHost(structureName, host);
        if (this.compartmentHost == host && (this.structureName == null || this.structureName.equals(structureName))) {
            this.compartmentHost = null;
            this.structureName = null;
            this.lastUnavailableReason = null;
            invalidateCapabilities();
            requestStorageUpdate();
            setChanged();
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
        if (this.compartmentHost == controller && (this.structureName == null || this.structureName.equals(structureName))) {
            this.compartmentHost = null;
            this.structureName = null;
            this.lastUnavailableReason = null;
            invalidateCapabilities();
            requestStorageUpdate();
            setChanged();
        }
    }

    public @Nullable DigitalConstructFlowerCraftingRuntime boundCraftingRuntime() {
        DigitalConstructFlowerBlockEntity flower = boundFlower(false);
        return flower == null ? null : flower.getCraftingRuntime();
    }

    public @Nullable IGrid accessGrid() {
        if (boundFlower(false) == null) {
            return null;
        }
        var node = this.getMainNode().getNode();
        if (node == null || !node.isActive()) {
            return null;
        }
        return node.getGrid();
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

    private void logUnavailable(boolean shouldLog, String reason) {
        if (!shouldLog) {
            return;
        }
        if (reason.equals(this.lastUnavailableReason)) {
            return;
        }
        this.lastUnavailableReason = reason;
        LOGGER.warn("ME storage access hatch at {} exposes empty storage: {}", this.worldPosition, reason);
    }

    private void requestStorageUpdate() {
        if (this.level != null && !this.level.isClientSide()) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    private final class HatchStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            storageMounts.mount(networkStorage, 0);
        }
    }

    private final class HatchStorage implements MEStorage {

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(what, amount, mode, source);
            DigitalConstructFlowerBlockEntity flower = boundFlower();
            if (amount <= 0L || flower == null || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long inserted = DigitalConstructFlowerStorageSavedData.get(serverLevel.getServer())
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
            if (amount <= 0L || flower == null || !(level instanceof ServerLevel serverLevel)) {
                return 0L;
            }
            long extracted = DigitalConstructFlowerStorageSavedData.get(serverLevel.getServer())
                    .extract(flower.getStorageId(), what, amount, mode);
            if (extracted > 0L && mode == Actionable.MODULATE) {
                requestStorageUpdate();
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            DigitalConstructFlowerBlockEntity flower = boundFlower();
            if (flower == null || !(level instanceof ServerLevel serverLevel)) {
                return;
            }
            DigitalConstructFlowerStorageSavedData.get(serverLevel.getServer()).addAvailableStacks(flower.getStorageId(), out);
        }

        @Override
        public Component getDescription() {
            return ModBlocks.ME_STORAGE_ACCESS_HATCH.get().getName();
        }
    }
}
