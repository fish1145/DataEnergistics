package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycle;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NBT boundary for orbital weapon records. Malformed external data is normalized here before it reaches runtime state.
 */
final class OrbitalWeaponNbtCodec {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int OLDEST_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int SCHEMA_VERSION = 3;
    private static final String WEAPONS_TAG = "weapons";
    private static final String WEAPON_ID_TAG = "weapon_id";
    private static final String OWNER_ID_TAG = "owner_id";
    private static final String DELEGATED_ROLES_TAG = "delegated_roles";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String ROLE_TAG = "role";
    private static final String ENDPOINTS_TAG = "endpoints";
    private static final String DIMENSION_TAG = "dimension";
    private static final String POSITION_TAG = "pos";
    private static final String KIND_TAG = "kind";
    private static final String PRIORITY_TAG = "priority";
    private static final String RESERVE_TAG = "reserve";
    private static final String CELESTIAL_ENERGY_TAG = "celestial_energy";
    private static final String AE_ENERGY_TAG = "ae_energy";
    private static final String LIFECYCLE_STATE_TAG = "lifecycle_state";
    private static final String GRACE_TICKS_TAG = "grace_ticks";
    private static final Comparator<OrbitalEndpointRecord> ENDPOINT_ORDER = Comparator
            .comparingInt(OrbitalEndpointRecord::priority)
            .thenComparing(endpoint -> endpoint.location().dimensionId().toString())
            .thenComparingInt(endpoint -> endpoint.location().pos().getX())
            .thenComparingInt(endpoint -> endpoint.location().pos().getY())
            .thenComparingInt(endpoint -> endpoint.location().pos().getZ());

    private OrbitalWeaponNbtCodec() {}

    static CompoundTag save(CompoundTag tag, Collection<OrbitalWeaponRecord> weapons) {
        tag.putInt(SCHEMA_VERSION_TAG, SCHEMA_VERSION);
        ListTag weaponList = new ListTag();
        weapons.stream()
                .sorted(Comparator.comparing(OrbitalWeaponRecord::weaponId))
                .map(OrbitalWeaponNbtCodec::writeWeapon)
                .forEach(weaponList::add);
        tag.put(WEAPONS_TAG, weaponList);
        return tag;
    }

    static List<OrbitalWeaponRecord> load(CompoundTag tag) {
        if (!tag.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            LOGGER.warn("Ignoring orbital weapon SavedData without a schema version");
            return List.of();
        }
        int schemaVersion = tag.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion < OLDEST_SUPPORTED_SCHEMA_VERSION || schemaVersion > SCHEMA_VERSION) {
            LOGGER.warn(
                    "Ignoring orbital weapon SavedData schema version {}; supported range is {}-{}",
                    schemaVersion,
                    OLDEST_SUPPORTED_SCHEMA_VERSION,
                    SCHEMA_VERSION);
            return List.of();
        }

        Tag weaponsTag = tag.get(WEAPONS_TAG);
        if (!(weaponsTag instanceof ListTag weaponList)) {
            return List.of();
        }

        ArrayList<OrbitalWeaponRecord> weapons = new ArrayList<>();
        for (Tag weaponTag : weaponList) {
            if (weaponTag instanceof CompoundTag weaponEntry) {
                OrbitalWeaponRecord weapon = readWeapon(weaponEntry, schemaVersion);
                if (weapon != null) {
                    weapons.add(weapon);
                }
            }
        }
        return List.copyOf(weapons);
    }

    private static CompoundTag writeWeapon(OrbitalWeaponRecord weapon) {
        CompoundTag weaponTag = new CompoundTag();
        weaponTag.putUUID(WEAPON_ID_TAG, weapon.weaponId());
        weaponTag.putUUID(OWNER_ID_TAG, weapon.ownerId());

        ListTag roleList = new ListTag();
        weapon.delegatedRoles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(OrbitalWeaponNbtCodec::writeRole)
                .forEach(roleList::add);
        weaponTag.put(DELEGATED_ROLES_TAG, roleList);

        ListTag endpointList = new ListTag();
        weapon.endpoints().values().stream()
                .sorted(ENDPOINT_ORDER)
                .map(OrbitalWeaponNbtCodec::writeEndpoint)
                .forEach(endpointList::add);
        weaponTag.put(ENDPOINTS_TAG, endpointList);

        CompoundTag reserveTag = new CompoundTag();
        reserveTag.putLong(CELESTIAL_ENERGY_TAG, weapon.reserve().celestialEnergy());
        reserveTag.putLong(AE_ENERGY_TAG, weapon.reserve().aeEnergy());
        weaponTag.put(RESERVE_TAG, reserveTag);
        weaponTag.putString(LIFECYCLE_STATE_TAG, weapon.lifecycle().state().name());
        weaponTag.putInt(GRACE_TICKS_TAG, weapon.lifecycle().graceTicksRemaining());
        return weaponTag;
    }

    private static CompoundTag writeRole(Map.Entry<UUID, OrbitalAccessRole> entry) {
        CompoundTag roleTag = new CompoundTag();
        roleTag.putUUID(PLAYER_ID_TAG, entry.getKey());
        roleTag.putString(ROLE_TAG, entry.getValue().name());
        return roleTag;
    }

    private static CompoundTag writeEndpoint(OrbitalEndpointRecord endpoint) {
        CompoundTag endpointTag = new CompoundTag();
        endpointTag.putString(DIMENSION_TAG, endpoint.location().dimensionId().toString());
        endpointTag.put(POSITION_TAG, NbtUtils.writeBlockPos(endpoint.location().pos()));
        endpointTag.putString(KIND_TAG, endpoint.kind().name());
        endpointTag.putInt(PRIORITY_TAG, endpoint.priority());
        return endpointTag;
    }

    private static @Nullable OrbitalWeaponRecord readWeapon(CompoundTag weaponTag, int schemaVersion) {
        UUID weaponId = readUuid(weaponTag, WEAPON_ID_TAG, "weapon id");
        UUID ownerId = readUuid(weaponTag, OWNER_ID_TAG, "owner id");
        if (weaponId == null || ownerId == null) {
            return null;
        }

        LinkedHashMap<UUID, OrbitalAccessRole> roles = new LinkedHashMap<>();
        Tag rolesTag = weaponTag.get(DELEGATED_ROLES_TAG);
        if (rolesTag instanceof ListTag roleList) {
            readRoles(weaponId, ownerId, roleList, roles);
        }

        LinkedHashMap<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints = new LinkedHashMap<>();
        Tag endpointsTag = weaponTag.get(ENDPOINTS_TAG);
        if (endpointsTag instanceof ListTag endpointList) {
            readEndpoints(weaponId, endpointList, endpoints);
        }
        OrbitalEnergyReserve reserve = schemaVersion >= 2 ? readReserve(weaponId, weaponTag) : OrbitalEnergyReserve.empty();
        OrbitalWeaponLifecycle lifecycle = schemaVersion >= 3 ? readLifecycle(weaponId, weaponTag) : OrbitalWeaponLifecycle.dormant();
        return new OrbitalWeaponRecord(weaponId, ownerId, roles, endpoints, reserve, lifecycle);
    }

    private static OrbitalWeaponLifecycle readLifecycle(UUID weaponId, CompoundTag weaponTag) {
        try {
            OrbitalWeaponLifecycleState state = OrbitalWeaponLifecycleState.valueOf(weaponTag.getString(LIFECYCLE_STATE_TAG));
            int graceTicks = Math.max(0, weaponTag.getInt(GRACE_TICKS_TAG));
            return switch (state) {
                case DORMANT -> OrbitalWeaponLifecycle.dormant();
                case DEPLOYED -> OrbitalWeaponLifecycle.deployed();
                case RESERVE_GRACE -> OrbitalWeaponLifecycle.reserveGrace(graceTicks);
            };
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Resetting invalid lifecycle state on orbital weapon {}", weaponId);
            return OrbitalWeaponLifecycle.dormant();
        }
    }

    private static OrbitalEnergyReserve readReserve(UUID weaponId, CompoundTag weaponTag) {
        Tag rawReserve = weaponTag.get(RESERVE_TAG);
        if (!(rawReserve instanceof CompoundTag reserveTag)) {
            LOGGER.warn("Resetting missing or invalid reserve on orbital weapon {}", weaponId);
            return OrbitalEnergyReserve.empty();
        }
        return new OrbitalEnergyReserve(
                readReserveAmount(weaponId, reserveTag, CELESTIAL_ENERGY_TAG),
                readReserveAmount(weaponId, reserveTag, AE_ENERGY_TAG));
    }

    private static long readReserveAmount(UUID weaponId, CompoundTag reserveTag, String key) {
        if (!reserveTag.contains(key, Tag.TAG_LONG) || reserveTag.getLong(key) < 0L) {
            LOGGER.warn("Resetting invalid reserve field '{}' on orbital weapon {}", key, weaponId);
            return 0L;
        }
        return reserveTag.getLong(key);
    }

    private static void readRoles(
                                  UUID weaponId,
                                  UUID ownerId,
                                  ListTag roleList,
                                  Map<UUID, OrbitalAccessRole> roles) {
        for (Tag roleTag : roleList) {
            if (!(roleTag instanceof CompoundTag roleEntry)) {
                continue;
            }
            UUID playerId = readUuid(roleEntry, PLAYER_ID_TAG, "delegated player id");
            if (playerId == null) {
                continue;
            }
            if (ownerId.equals(playerId)) {
                LOGGER.warn("Ignoring delegated role for owner {} on orbital weapon {}", ownerId, weaponId);
                continue;
            }

            OrbitalAccessRole role;
            try {
                role = OrbitalAccessRole.valueOf(roleEntry.getString(ROLE_TAG));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn(
                        "Ignoring invalid delegated role '{}' for player {} on orbital weapon {}",
                        roleEntry.getString(ROLE_TAG),
                        playerId,
                        weaponId);
                continue;
            }
            if (roles.putIfAbsent(playerId, role) != null) {
                LOGGER.warn("Ignoring duplicate delegated player {} on orbital weapon {}", playerId, weaponId);
            }
        }
    }

    private static void readEndpoints(
                                      UUID weaponId,
                                      ListTag endpointList,
                                      Map<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints) {
        for (Tag endpointTag : endpointList) {
            if (!(endpointTag instanceof CompoundTag endpointEntry)) {
                continue;
            }
            OrbitalEndpointRecord endpoint = readEndpoint(weaponId, endpointEntry);
            if (endpoint == null) {
                continue;
            }
            if (endpoints.putIfAbsent(endpoint.location(), endpoint) != null) {
                LOGGER.warn(
                        "Ignoring duplicate endpoint {} on orbital weapon {}",
                        endpoint.location(),
                        weaponId);
            }
        }
    }

    private static @Nullable OrbitalEndpointRecord readEndpoint(UUID weaponId, CompoundTag endpointTag) {
        ResourceLocation dimensionId;
        try {
            dimensionId = ResourceLocation.parse(endpointTag.getString(DIMENSION_TAG));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn(
                    "Ignoring endpoint with invalid dimension '{}' on orbital weapon {}",
                    endpointTag.getString(DIMENSION_TAG),
                    weaponId);
            return null;
        }

        BlockPos pos = NbtUtils.readBlockPos(endpointTag, POSITION_TAG).orElse(null);
        if (pos == null) {
            LOGGER.warn("Ignoring endpoint without a valid position on orbital weapon {}", weaponId);
            return null;
        }

        OrbitalEndpointKind kind;
        try {
            kind = OrbitalEndpointKind.valueOf(endpointTag.getString(KIND_TAG));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn(
                    "Ignoring endpoint with invalid kind '{}' on orbital weapon {}",
                    endpointTag.getString(KIND_TAG),
                    weaponId);
            return null;
        }

        if (!endpointTag.contains(PRIORITY_TAG, Tag.TAG_INT) || endpointTag.getInt(PRIORITY_TAG) < 0) {
            LOGGER.warn("Ignoring endpoint without a valid priority on orbital weapon {}", weaponId);
            return null;
        }
        return new OrbitalEndpointRecord(
                new OrbitalEndpointLocation(dimensionId, pos),
                kind,
                endpointTag.getInt(PRIORITY_TAG));
    }

    private static @Nullable UUID readUuid(CompoundTag tag, String key, String description) {
        if (!tag.hasUUID(key)) {
            LOGGER.warn("Ignoring orbital weapon data with missing or invalid {}", description);
            return null;
        }
        return tag.getUUID(key);
    }
}
