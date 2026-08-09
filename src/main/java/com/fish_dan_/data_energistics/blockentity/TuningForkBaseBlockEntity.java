package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

import java.util.EnumSet;
import java.util.Set;

/**
 * Channel-using AE network base that gates Echo insertion behind one digitalization core.
 */
public class TuningForkBaseBlockEntity extends AENetworkedBlockEntity {

    private static final String CORE_TAG = "core";

    private final MachineSource actionSource = new MachineSource(this);
    private ItemStack core = ItemStack.EMPTY;

    public TuningForkBaseBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.TUNING_FORK_BASE_BLOCK_ENTITY.get(), pos, state);
        this.getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(DEBlocks.TUNING_FORK_BASE.get())
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

    public boolean hasCore() {
        return !this.core.isEmpty();
    }

    public boolean canProduceEcho() {
        return hasCore() && this.getMainNode().isOnline();
    }

    public boolean installCore(ItemStack heldStack) {
        if (hasCore() || !heldStack.is(DEItems.RESONANCE_DIGITALIZATION_CORE.get())) {
            return false;
        }
        this.core = heldStack.copyWithCount(1);
        setChanged();
        return true;
    }

    public ItemStack removeCore() {
        if (!hasCore()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = this.core;
        this.core = ItemStack.EMPTY;
        setChanged();
        return removed;
    }

    /**
     * Simulates the complete request, then inserts exactly the accepted partial amount.
     */
    public boolean tryInsertEcho(TuningForkVariant variant, int requestedAmount) {
        if (requestedAmount <= 0) {
            throw new IllegalArgumentException("Echo request must be positive: " + requestedAmount);
        }
        if (!canProduceEcho()) {
            return false;
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        long simulated = storage.insert(EchoKey.of(), requestedAmount, Actionable.SIMULATE, this.actionSource);
        if (simulated < 0 || simulated > requestedAmount) {
            Data_Energistics.LOGGER.error(
                    "Tuning fork base at {} received invalid simulated Echo insertion for variant {}: expected at most {}, actual {}",
                    this.worldPosition,
                    variant.serializedName(),
                    requestedAmount,
                    simulated);
            return false;
        }
        if (simulated == 0) {
            return false;
        }

        long inserted = storage.insert(EchoKey.of(), simulated, Actionable.MODULATE, this.actionSource);
        if (inserted != simulated) {
            Data_Energistics.LOGGER.error(
                    "Tuning fork base at {} produced an inconsistent Echo insertion for variant {}: expected {}, actual {}",
                    this.worldPosition,
                    variant.serializedName(),
                    simulated,
                    inserted);
        }
        return inserted > 0;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.core = tag.contains(CORE_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(CORE_TAG))
                : ItemStack.EMPTY;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasCore()) {
            tag.put(CORE_TAG, this.core.saveOptional(registries));
        }
    }
}
