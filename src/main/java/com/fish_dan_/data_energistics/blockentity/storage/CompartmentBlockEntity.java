package com.fish_dan_.data_energistics.blockentity.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.storage.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.AvailabilityCheckedCompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentInventory;
import com.fish_dan_.data_energistics.common.compartment.CompartmentKeyNormalizer;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentStorage;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.compartment.MapBackedCompartmentStorage;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;

import appeng.api.inventories.InternalInventory;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Shared runtime shell for a compartment block entity.
 *
 * <p>
 * Concrete subclasses own the type-specific inventories, upgrade slots, AE IO behavior, and NBT fields.
 */
public abstract class CompartmentBlockEntity extends AEBaseBlockEntity implements CompartmentPart {

    protected static final int MAX_COMPARTMENT_SLOTS = 45;
    protected static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String STORAGE_TAG = "storage";

    private final CompartmentStorage storage = new MapBackedCompartmentStorage(this::onStorageChanged);
    private final CompartmentStorage structureStorageView = new AvailabilityCheckedCompartmentStorage(
            this::isCompartmentBound,
            () -> this.storage);
    @Nullable
    private CompartmentHost compartmentHost;
    @Nullable
    private String structureName;

    protected CompartmentBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state) {
        super(blockEntityType, pos, state);
    }

    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return Set.of();
    }

    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.NONE;
    }

    public final void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        boolean active = isCompartmentBound();
        BlockState state = getBlockState();
        if (state.hasProperty(CompartmentBlock.ACTIVE) && state.getValue(CompartmentBlock.ACTIVE) != active) {
            this.level.setBlock(this.worldPosition, state.setValue(CompartmentBlock.ACTIVE, active), 3);
        }
        if (active) {
            onBoundServerTick();
        }
    }

    protected void onBoundServerTick() {}

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STORAGE_TAG, this.storage.serializeNBT(registries));
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.contains(STORAGE_TAG, Tag.TAG_LIST)) {
            this.storage.deserializeNBT(registries, tag.getList(STORAGE_TAG, Tag.TAG_COMPOUND));
        }
    }

    @Override
    public CompartmentType compartmentType() {
        if (getBlockState().getBlock() instanceof CompartmentBlock compartmentBlock) {
            return compartmentBlock.compartmentType();
        }
        throw new IllegalStateException("Compartment block entity is attached to a non-compartment block at " +
                this.worldPosition);
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

    public final CompartmentStorage storage() {
        return this.storage;
    }

    @Override
    public final CompartmentStorage compartmentStorage() {
        return this.structureStorageView;
    }

    @Nullable
    public MEStorage outputStorage() {
        return null;
    }

    public int unlockedSlotCount() {
        return configurableSlotLimit();
    }

    public int configurableSlotLimit() {
        return MAX_COMPARTMENT_SLOTS;
    }

    public boolean supportsUpgrades() {
        return false;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        return super.getSubInventory(id);
    }

    @Override
    public void compartment$bindToHost(String structureName, CompartmentHost host) {
        if (this.compartmentHost == host && structureName.equals(this.structureName)) {
            if (!host.compartmentHost$getCompartments(structureName).contains(this)) {
                CompartmentPart.super.compartment$bindToHost(structureName, host);
            }
            return;
        }

        if (this.compartmentHost != null) {
            CompartmentPart.super.compartment$unbindFromHost(this.structureName, this.compartmentHost);
        }

        CompartmentPart.super.compartment$bindToHost(structureName, host);
        this.compartmentHost = host;
        this.structureName = structureName;
        invalidateCapabilities();
        setChanged();
        onCompartmentBindingChanged();
    }

    @Override
    public void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        CompartmentPart.super.compartment$unbindFromHost(structureName, host);
        if (this.compartmentHost == host && this.structureName.equals(structureName)) {
            this.compartmentHost = null;
            this.structureName = null;
            invalidateCapabilities();
            setChanged();
            onCompartmentBindingChanged();
        }
    }

    @Override
    public void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                     String structureName,
                                                     VerticalMultiBlockContext<?> context,
                                                     long bindingEpoch) {
        CompartmentPart.super.verticalMultiBlock$addedToController(controller, structureName, context, bindingEpoch);
    }

    @Override
    public void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                         String structureName,
                                                         long bindingEpoch) {
        CompartmentPart.super.verticalMultiBlock$removedFromController(controller, structureName, bindingEpoch);
        if (this.compartmentHost == controller && this.structureName.equals(structureName)) {
            this.compartmentHost = null;
            this.structureName = null;
            invalidateCapabilities();
            setChanged();
        }
    }

    protected final CompartmentStorage mutableStorage() {
        return this.storage;
    }

    protected final void onStorageChanged() {
        setChanged();
        onCompartmentStorageChanged();
    }

    protected final void onContentInventoryChanged() {
        rebuildCanonicalStorage();
        setChanged();
    }

    protected void rebuildCanonicalStorage() {}

    protected void onCompartmentBindingChanged() {}

    protected void onCompartmentStorageChanged() {}

    protected final void copyInventoryToStorage(CompartmentInventory inventory) {
        copyInventoryToStorage(inventory, inventory.size());
    }

    protected final void copyInventoryToStorage(CompartmentInventory inventory, int slots) {
        for (int slot = 0; slot < Math.min(inventory.size(), slots); slot++) {
            if (!inventory.isSlotUnlocked(slot)) {
                continue;
            }
            AEKey key = CompartmentKeyNormalizer.normalize(inventory.getKey(slot));
            long amount = inventory.getAmount(slot);
            if (key != null && amount > 0L) {
                this.storage.insert(key, amount, false);
            }
        }
    }
}
