package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Immutable opening snapshot for the orbital control terminal.
 *
 * <p>
 * The server creates this value from the current player's UUID-indexed SavedData view. The terminal never stores a
 * weapon identity in its item stack; the snapshot is rebuilt from the current UUID whenever the LDLib2 UI synchronizes.
 * </p>
 */
public record OrbitalControlTerminalSnapshot(
                                             @Nullable UUID selectedWeaponId,
                                             List<WeaponEntry> weapons,
                                             boolean truncated) {

    /** Maximum number of entries kept in one synchronized terminal snapshot. */
    public static final int MAX_WEAPONS = 128;

    public OrbitalControlTerminalSnapshot {
        weapons = List.copyOf(weapons);
        if (weapons.size() > MAX_WEAPONS) {
            throw new IllegalArgumentException("Orbital terminal snapshot exceeds its bounded entry limit");
        }
        if (selectedWeaponId != null && weapons.stream().noneMatch(entry -> entry.weaponId().equals(selectedWeaponId))) {
            throw new IllegalArgumentException("Orbital terminal selection is not present in its weapon list");
        }
    }

    /**
     * Captures the weapons currently visible to one server player.
     *
     * @param server   authoritative server whose SavedData must be read on its main thread
     * @param playerId UUID used for the owner and delegated-access indexes
     * @return stable weapon-ID ordered opening snapshot
     */
    public static OrbitalControlTerminalSnapshot capture(MinecraftServer server, UUID playerId) {
        List<OrbitalWeaponRecord> accessibleWeapons = OrbitalWeaponSavedData.get(server)
                .accessibleTo(playerId)
                .stream()
                .toList();
        boolean truncated = accessibleWeapons.size() > MAX_WEAPONS;
        List<WeaponEntry> entries = accessibleWeapons.stream()
                .limit(MAX_WEAPONS)
                .map(weapon -> WeaponEntry.from(weapon, playerId))
                .toList();
        UUID selected = entries.isEmpty() ? null : entries.getFirst().weaponId();
        return new OrbitalControlTerminalSnapshot(selected, entries, truncated);
    }

    /**
     * Builds the read-only overview rendered by the LDLib2 label. Translation keys remain on the component tree, so
     * the same server-authoritative state can be rendered in either supported client language.
     */
    public Component toComponent() {
        if (this.weapons.isEmpty()) {
            return Component.translatable("screen.data_energistics.orbital_control_terminal.empty");
        }

        MutableComponent result = Component.translatable(
                "screen.data_energistics.orbital_control_terminal.available",
                Integer.toString(this.weapons.size()));
        for (WeaponEntry entry : this.weapons) {
            result = result
                    .append(Component.literal("\n"))
                    .append(Component.translatable(
                            "screen.data_energistics.orbital_control_terminal.weapon",
                            Component.literal(entry.weaponId().toString()),
                            Component.literal(entry.ownerId().toString()),
                            entry.roleComponent(),
                            Integer.toString(entry.endpointCount()),
                            Long.toString(entry.celestialEnergy()),
                            Long.toString(entry.aeEnergy())));
        }
        if (this.truncated) {
            result = result
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.data_energistics.orbital_control_terminal.truncated"));
        }
        return result;
    }

    /** One accessible weapon and the resources needed by the opening overview. */
    public record WeaponEntry(
                              UUID weaponId,
                              UUID ownerId,
                              boolean owner,
                              @Nullable OrbitalAccessRole delegatedRole,
                              int endpointCount,
                              long celestialEnergy,
                              long aeEnergy) {

        public WeaponEntry {
            if (endpointCount < 0 || celestialEnergy < 0L || aeEnergy < 0L) {
                throw new IllegalArgumentException("Orbital terminal reserve values must not be negative");
            }
            if (owner && delegatedRole != null) {
                throw new IllegalArgumentException("An owner entry cannot also carry a delegated role");
            }
            if (!owner && delegatedRole == null) {
                throw new IllegalArgumentException("A non-owner entry must carry its delegated role");
            }
        }

        private static WeaponEntry from(OrbitalWeaponRecord weapon, UUID playerId) {
            boolean owner = weapon.ownerId().equals(playerId);
            return new WeaponEntry(
                    weapon.weaponId(),
                    weapon.ownerId(),
                    owner,
                    owner ? null : weapon.delegatedRoles().get(playerId),
                    weapon.endpoints().size(),
                    weapon.reserve().celestialEnergy(),
                    weapon.reserve().aeEnergy());
        }

        private Component roleComponent() {
            String key = this.owner ? "screen.data_energistics.orbital_control_terminal.role.owner" : switch (this.delegatedRole) {
                case OPERATOR -> "screen.data_energistics.orbital_control_terminal.role.operator";
                case OBSERVER -> "screen.data_energistics.orbital_control_terminal.role.observer";
            };
            return Component.translatable(key);
        }
    }
}
