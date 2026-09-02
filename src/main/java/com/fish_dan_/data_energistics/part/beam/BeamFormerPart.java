package com.fish_dan_.data_energistics.part.beam;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.common.beam.BeamEndpointState;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import org.jspecify.annotations.Nullable;

public final class BeamFormerPart extends UpgradeablePart implements IGridTickable, BeamEndpoint {

    @PartModels
    private static final IPartModel MODEL = new PartModel(Data_Energistics.id("part/me_beam_former"));
    private static final TickingRequest TICKING = new TickingRequest(5, 20, false);
    private final BeamEndpointState beam = new BeamEndpointState(this, BeamDeviceKind.PART);

    public BeamFormerPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(1).addService(IGridTickable.class, this);
    }

    @Override
    protected int getUpgradeSlots() {
        return BeamDeviceKind.UPGRADE_SLOTS;
    }

    @Override
    public void upgradesChanged() {
        this.beam.upgradesChanged();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.beam.upgradesChanged();
    }

    @Override
    public void removeFromWorld() {
        this.beam.disconnect();
        super.removeFromWorld();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        this.beam.requestCheck();
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.beam.requestCheck();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return TICKING;
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.beam.tick();
        return TickRateModulation.SLOWER;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 hit) {
        if (!player.level().isClientSide()) {
            if (player.isShiftKeyDown()) {
                this.beam.toggleHidden();
            } else {
                MenuOpener.open(DEMenus.BEAM_FORMER.get(), player, MenuLocators.forPart(this));
            }
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }

    @Override
    public AECableType getExternalCableConnectionType() {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    @Override
    public void getBoxes(IPartCollisionHelper boxes) {
        boxes.addBox(4, 4, 11, 12, 12, 17);
        boxes.addBox(5, 5, 17, 11, 11, 19);
        boxes.addBox(6, 6, 19, 10, 10, 21);
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        this.beam.save(tag);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        this.beam.load(tag);
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        this.beam.write(data);
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        return this.beam.read(data) || changed;
    }

    @Override
    public @Nullable Level beamLevel() {
        var blockEntity = getBlockEntity();
        return blockEntity == null ? null : blockEntity.getLevel();
    }

    @Override
    public BlockPos beamPosition() {
        return getBlockEntity().getBlockPos();
    }

    @Override
    public Direction beamFacing() {
        return getSide();
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
        AEColor color = getHost().getColor();
        return color == AEColor.TRANSPARENT ? -1 : color.blackVariant;
    }

    @Override
    public void beamChanged(boolean persist) {
        var host = getHost();
        if (host != null) {
            if (persist) {
                host.markForSave();
            }
            host.markForUpdate();
        }
    }
}
