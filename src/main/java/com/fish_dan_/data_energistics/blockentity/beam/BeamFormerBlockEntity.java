package com.fish_dan_.data_energistics.blockentity.beam;

import com.fish_dan_.data_energistics.block.beam.BeamFormerBlock;
import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.common.beam.BeamEndpointState;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.blockentities.IColorableBlockEntity;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class BeamFormerBlockEntity extends AENetworkedBlockEntity implements BeamEndpoint {

    private final BeamEndpointState beam;
    private final IUpgradeInventory upgrades;
    private Direction exposedBack;

    public BeamFormerBlockEntity(BlockPos pos, BlockState state) {
        super(((BeamFormerBlock) state.getBlock()).kind() == BeamDeviceKind.OMNI ?
                DEBlockEntities.ME_OMNI_BEAM_FORMER.get() : DEBlockEntities.ME_BEAM_FORMER.get(), pos, state);
        BeamDeviceKind kind = ((BeamFormerBlock) state.getBlock()).kind();
        this.beam = new BeamEndpointState(this, kind);
        this.upgrades = UpgradeInventories.forMachine(state.getBlock(), BeamDeviceKind.UPGRADE_SLOTS, this.beam::upgradesChanged);
        this.exposedBack = beamFacing().getOpposite();
        getMainNode().setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(kind.idlePower(0, 0));
    }

    @Override
    public ObjectSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return ObjectSets.singleton(beamFacing().getOpposite());
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return direction == beamFacing().getOpposite() ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void onReady() {
        super.onReady();
        this.beam.upgradesChanged();
    }

    public void serverTick() {
        Direction back = beamFacing().getOpposite();
        if (back != this.exposedBack) {
            this.beam.disconnect();
            this.exposedBack = back;
            onGridConnectableSidesChanged();
            this.beam.requestCheck();
        }
        this.beam.tick();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.beam.requestCheck();
        markForUpdate();
    }

    @Override
    public void onChunkUnloaded() {
        this.beam.disconnect();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        this.beam.disconnect();
        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        this.upgrades.readFromNBT(tag, "upgrades", registries);
        this.beam.load(tag);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.upgrades.writeToNBT(tag, "upgrades", registries);
        this.beam.save(tag);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.beam.write(data);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        return this.beam.read(data) || changed;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : this.upgrades) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Override
    public @Nullable InternalInventory getSubInventory(ResourceLocation id) {
        return ISegmentedInventory.UPGRADES.equals(id) ? this.upgrades : super.getSubInventory(id);
    }

    @Override
    public @Nullable Level beamLevel() {
        return this.level;
    }

    @Override
    public BlockPos beamPosition() {
        return this.worldPosition;
    }

    @Override
    public Direction beamFacing() {
        return getBlockState().getValue(BeamFormerBlock.FACING);
    }

    @Override
    public IManagedGridNode beamNode() {
        return getMainNode();
    }

    @Override
    public BeamEndpointState beamState() {
        return this.beam;
    }

    @Override
    public int beamColor() {
        BlockPos back = this.worldPosition.relative(beamFacing().getOpposite());
        if (this.level != null && this.level.hasChunkAt(back) &&
                this.level.getBlockEntity(back) instanceof IColorableBlockEntity colorable) {
            AEColor color = colorable.getColor();
            return color == AEColor.TRANSPARENT ? -1 : color.blackVariant;
        }
        return -1;
    }

    @Override
    public void beamChanged(boolean persist) {
        if (persist) {
            saveChanges();
        }
        if (this.level != null && !isRemoved()) {
            markForUpdate();
        }
    }
}
