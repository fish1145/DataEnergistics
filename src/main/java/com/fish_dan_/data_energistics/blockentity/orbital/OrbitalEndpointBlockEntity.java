package com.fish_dan_.data_energistics.blockentity.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shared persistent binding and AE-node behavior for physical orbital-weapon endpoints.
 */
public abstract class OrbitalEndpointBlockEntity extends AENetworkedBlockEntity {

    private static final String WEAPON_ID_TAG = "weapon_id";

    private @Nullable UUID weaponId;

    protected OrbitalEndpointBlockEntity(
                                         BlockEntityType<?> type,
                                         BlockPos pos,
                                         BlockState state,
                                         Block visualRepresentation) {
        super(type, pos, state);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(visualRepresentation)
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    /**
     * Returns the stable weapon identity stored by this endpoint, if placement binding has completed.
     */
    public final Optional<UUID> getWeaponId() {
        return Optional.ofNullable(this.weaponId);
    }

    /**
     * Identifies the persisted endpoint kind represented by this physical block entity.
     */
    public abstract OrbitalEndpointKind endpointKind();

    /**
     * Completes initial placement binding without permitting an endpoint to be rebound to another weapon.
     */
    public final void bindTo(UUID weaponId) {
        if (this.weaponId != null) {
            if (this.weaponId.equals(weaponId)) {
                retainChunkTicket();
                return;
            }
            throw new IllegalStateException("Orbital endpoint is already bound to " + this.weaponId);
        }
        this.weaponId = weaponId;
        saveChanges();
        markForClientUpdate();
        retainChunkTicket();
    }

    /**
     * Removes this endpoint from authoritative SavedData when its block permanently leaves the world.
     */
    public final void releaseBinding(ServerLevel level) {
        OrbitalEndpointLocation location = new OrbitalEndpointLocation(level.dimension().location(), this.worldPosition);
        UUID boundWeaponId = this.weaponId;
        if (boundWeaponId == null) {
            OrbitalEndpointChunkTickets.release(level, location);
            return;
        }

        try {
            boolean removed = OrbitalWeaponSavedData.get(level.getServer())
                    .removeEndpoint(level.getServer(), boundWeaponId, location);
            if (!removed) {
                Data_Energistics.LOGGER.warn(
                        "Orbital endpoint at {} referenced weapon {} without a matching binding",
                        location,
                        boundWeaponId);
            }
        } catch (IllegalStateException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to release orbital endpoint {} from weapon {}",
                    location,
                    boundWeaponId,
                    exception);
        } finally {
            OrbitalEndpointChunkTickets.release(level, location);
        }
        this.weaponId = null;
        saveChanges();
        markForClientUpdate();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.weaponId = data.hasUUID(WEAPON_ID_TAG) ? data.getUUID(WEAPON_ID_TAG) : null;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        if (this.weaponId != null) {
            data.putUUID(WEAPON_ID_TAG, this.weaponId);
        }
    }

    private void retainChunkTicket() {
        if (this.level instanceof ServerLevel serverLevel) {
            OrbitalEndpointChunkTickets.retain(
                    serverLevel,
                    new OrbitalEndpointLocation(serverLevel.dimension().location(), this.worldPosition));
        }
    }
}
