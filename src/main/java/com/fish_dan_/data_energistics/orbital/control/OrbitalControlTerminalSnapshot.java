package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
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
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        OrbitalWeaponSavedData weaponData = OrbitalWeaponSavedData.get(server);
        List<OrbitalWeaponRecord> accessibleWeapons = weaponData
                .accessibleTo(playerId)
                .stream()
                .toList();
        boolean truncated = accessibleWeapons.size() > MAX_WEAPONS;
        List<WeaponEntry> entries = accessibleWeapons.stream()
                .limit(MAX_WEAPONS)
                .map(weapon -> WeaponEntry.from(weapon, playerId, attacks.forWeapon(weapon.weaponId())))
                .toList();
        UUID preferred = weaponData.preferredWeaponId(playerId).orElse(null);
        UUID selected = null;
        if (preferred != null && entries.stream().anyMatch(entry -> entry.weaponId().equals(preferred))) {
            selected = preferred;
        } else if (!entries.isEmpty()) {
            selected = entries.getFirst().weaponId();
        }
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
            if (entry.weaponId().equals(this.selectedWeaponId)) {
                result = result
                        .append(Component.literal("\n"))
                        .append(Component.translatable("screen.data_energistics.orbital_control_terminal.selected_marker"));
            }
            result = result
                    .append(Component.literal("\n"))
                    .append(Component.translatable(
                            "screen.data_energistics.orbital_control_terminal.weapon",
                            Component.literal(entry.weaponId().toString()),
                            Component.literal(entry.ownerId().toString()),
                            entry.roleComponent(),
                            entry.lifecycleComponent(),
                            Integer.toString(entry.endpointCount()),
                            Long.toString(entry.celestialEnergy()),
                            Long.toString(entry.aeEnergy())));
            for (AttackEntry attack : entry.attacks()) {
                result = result
                        .append(Component.literal("\n  "))
                        .append(Component.translatable(
                                "screen.data_energistics.orbital_control_terminal.attack",
                                attack.modeComponent(),
                                attack.phaseComponent(),
                                Component.literal(attack.dimensionId().toString()),
                                Integer.toString(attack.target().getX()),
                                Integer.toString(attack.target().getY()),
                                Integer.toString(attack.target().getZ()),
                                Integer.toString(attack.warningTicksRemaining()),
                                Integer.toString(attack.cooldownTicksRemaining()),
                                Long.toString(attack.workCursor())));
            }
        }
        if (this.truncated) {
            result = result
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.data_energistics.orbital_control_terminal.truncated"));
        }
        return result;
    }

    /**
     * Builds the compact status sent to the world HUD. Only the persisted selected weapon is included, keeping the
     * server-to-client update bounded even when a player can access many delegated weapons.
     */
    public Component toHudComponent() {
        if (this.selectedWeaponId == null) {
            return Component.translatable("screen.data_energistics.orbital_control_hud.empty");
        }
        WeaponEntry selected = this.weapons.stream()
                .filter(entry -> entry.weaponId().equals(this.selectedWeaponId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return Component.translatable("screen.data_energistics.orbital_control_hud.empty");
        }

        MutableComponent result = Component.translatable(
                "screen.data_energistics.orbital_control_hud.selected",
                Component.literal(selected.weaponId().toString()),
                selected.roleComponent(),
                selected.lifecycleComponent(),
                Long.toString(selected.celestialEnergy()),
                Long.toString(selected.aeEnergy()));
        if (selected.attacks().isEmpty()) {
            return result
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.data_energistics.orbital_control_hud.idle"));
        }

        int shown = 0;
        for (AttackEntry attack : selected.attacks()) {
            if (shown++ >= 8) {
                result = result
                        .append(Component.literal("\n"))
                        .append(Component.translatable("screen.data_energistics.orbital_control_hud.more"));
                break;
            }
            result = result
                    .append(Component.literal("\n"))
                    .append(Component.translatable(
                            "screen.data_energistics.orbital_control_hud.attack",
                            attack.modeComponent(),
                            attack.phaseComponent(),
                            Integer.toString(attack.target().getX()),
                            Integer.toString(attack.target().getY()),
                            Integer.toString(attack.target().getZ()),
                            Integer.toString(attack.warningTicksRemaining()),
                            Integer.toString(attack.cooldownTicksRemaining())));
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
                              OrbitalWeaponLifecycleState lifecycleState,
                              int graceTicksRemaining,
                              long celestialEnergy,
                              long aeEnergy,
                              List<AttackEntry> attacks) {

        public WeaponEntry {
            attacks = List.copyOf(attacks);
            if (endpointCount < 0 || graceTicksRemaining < 0 || celestialEnergy < 0L || aeEnergy < 0L) {
                throw new IllegalArgumentException("Orbital terminal reserve values must not be negative");
            }
            if (owner && delegatedRole != null) {
                throw new IllegalArgumentException("An owner entry cannot also carry a delegated role");
            }
            if (!owner && delegatedRole == null) {
                throw new IllegalArgumentException("A non-owner entry must carry its delegated role");
            }
        }

        private static WeaponEntry from(
                                        OrbitalWeaponRecord weapon,
                                        UUID playerId,
                                        List<OrbitalAttackRecord> attacks) {
            boolean owner = weapon.ownerId().equals(playerId);
            return new WeaponEntry(
                    weapon.weaponId(),
                    weapon.ownerId(),
                    owner,
                    owner ? null : weapon.delegatedRoles().get(playerId),
                    weapon.endpoints().size(),
                    weapon.lifecycle().state(),
                    weapon.lifecycle().graceTicksRemaining(),
                    weapon.reserve().celestialEnergy(),
                    weapon.reserve().aeEnergy(),
                    attacks.stream().map(AttackEntry::from).toList());
        }

        private Component roleComponent() {
            String key = this.owner ? "screen.data_energistics.orbital_control_terminal.role.owner" : switch (this.delegatedRole) {
                case OPERATOR -> "screen.data_energistics.orbital_control_terminal.role.operator";
                case OBSERVER -> "screen.data_energistics.orbital_control_terminal.role.observer";
            };
            return Component.translatable(key);
        }

        private Component lifecycleComponent() {
            String key = "screen.data_energistics.orbital_control_terminal.lifecycle."
                    + this.lifecycleState.name().toLowerCase(Locale.ROOT);
            return this.lifecycleState == OrbitalWeaponLifecycleState.RESERVE_GRACE
                    ? Component.translatable(key, Integer.toString(this.graceTicksRemaining))
                    : Component.translatable(key);
        }
    }

    /** Public attack state needed by the read-only LDLib2 overview and HUD cache. */
    public record AttackEntry(
                              UUID attackId,
                              OrbitalAttackMode mode,
                              OrbitalAttackPhase phase,
                              ResourceLocation dimensionId,
                              BlockPos target,
                              int warningTicksRemaining,
                              int cooldownTicksRemaining,
                              long workCursor) {

        public AttackEntry {
            target = target.immutable();
            if (warningTicksRemaining < 0 || cooldownTicksRemaining < 0 || workCursor < 0L) {
                throw new IllegalArgumentException("Orbital terminal attack progress must not be negative");
            }
        }

        private static AttackEntry from(OrbitalAttackRecord attack) {
            return new AttackEntry(
                    attack.attackId(),
                    attack.mode(),
                    attack.phase(),
                    attack.dimensionId(),
                    attack.target(),
                    attack.warningTicksRemaining(),
                    attack.cooldownTicksRemaining(),
                    attack.workCursor());
        }

        private Component modeComponent() {
            return Component.translatable(
                    "screen.data_energistics.orbital_control_terminal.mode." + this.mode.name().toLowerCase(Locale.ROOT));
        }

        private Component phaseComponent() {
            return Component.translatable(
                    "screen.data_energistics.orbital_control_terminal.phase." + this.phase.name().toLowerCase(Locale.ROOT));
        }
    }
}
