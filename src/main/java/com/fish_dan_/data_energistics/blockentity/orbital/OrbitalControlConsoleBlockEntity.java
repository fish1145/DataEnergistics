package com.fish_dan_.data_energistics.blockentity.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AE-connected console endpoint that persists only the stable weapon identity assigned during placement.
 */
public final class OrbitalControlConsoleBlockEntity extends AENetworkedBlockEntity {

    private static final String WEAPON_ID_TAG = "weapon_id";

    private @Nullable UUID weaponId;

    public OrbitalControlConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.ORBITAL_CONTROL_CONSOLE_BLOCK_ENTITY.get(), pos, state);
        getMainNode()
                .setVisualRepresentation(DEBlocks.ORBITAL_CONTROL_CONSOLE.get())
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
     * Returns the stable weapon identity stored by this endpoint, if placement provisioning has completed.
     */
    public Optional<UUID> getWeaponId() {
        return Optional.ofNullable(this.weaponId);
    }

    /**
     * Completes initial placement provisioning without permitting a console to be rebound to another weapon.
     */
    public void bindTo(UUID weaponId) {
        if (this.weaponId != null) {
            if (this.weaponId.equals(weaponId)) {
                return;
            }
            throw new IllegalStateException("Orbital control console is already bound to " + this.weaponId);
        }
        this.weaponId = weaponId;
        saveChanges();
        markForClientUpdate();
    }

    /**
     * Removes this endpoint from authoritative SavedData when the console leaves the world.
     */
    public void releaseBinding(ServerLevel level) {
        UUID boundWeaponId = this.weaponId;
        if (boundWeaponId == null) {
            return;
        }

        OrbitalEndpointLocation location = new OrbitalEndpointLocation(level.dimension().location(), this.worldPosition);
        try {
            boolean removed = OrbitalWeaponSavedData.get(level.getServer())
                    .removeEndpoint(level.getServer(), boundWeaponId, location);
            if (!removed) {
                Data_Energistics.LOGGER.warn(
                        "Orbital control console at {} referenced weapon {} without a matching endpoint",
                        location,
                        boundWeaponId);
            }
        } catch (IllegalStateException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to release orbital control console endpoint {} from weapon {}",
                    location,
                    boundWeaponId,
                    exception);
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
}
