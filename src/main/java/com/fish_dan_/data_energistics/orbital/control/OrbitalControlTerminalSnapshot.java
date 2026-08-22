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
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, bounded view state shared by the orbital console, handheld terminal and HUD presentation.
 *
 * <p>
 * The server captures this value from UUID-indexed SavedData. LDLib2 synchronizes the typed record directly, so UI
 * components bind fields instead of parsing or duplicating preformatted status text.
 * </p>
 */
public record OrbitalControlTerminalSnapshot(
                                             @Nullable UUID selectedWeaponId,
                                             List<WeaponEntry> weapons,
                                             boolean truncated) {

    /** Maximum number of accessible weapons carried by one menu snapshot. */
    public static final int MAX_WEAPONS = 128;
    /** A weapon has three live mode slots; the larger bound also tolerates terminal history during transitions. */
    public static final int MAX_ATTACKS_PER_WEAPON = 16;

    private static final Codec<OrbitalAttackMode> ATTACK_MODE_CODEC = enumCodec(OrbitalAttackMode.class);
    private static final Codec<OrbitalAttackPhase> ATTACK_PHASE_CODEC = enumCodec(OrbitalAttackPhase.class);
    private static final Codec<OrbitalAccessRole> ACCESS_ROLE_CODEC = enumCodec(OrbitalAccessRole.class);
    private static final Codec<OrbitalWeaponLifecycleState> LIFECYCLE_CODEC = enumCodec(
            OrbitalWeaponLifecycleState.class);

    public static final OrbitalControlTerminalSnapshot EMPTY = new OrbitalControlTerminalSnapshot(
            null,
            List.of(),
            false);
    public static final Codec<OrbitalControlTerminalSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    UUIDUtil.CODEC.optionalFieldOf("selected_weapon_id").forGetter(snapshot -> Optional.ofNullable(snapshot.selectedWeaponId)),
                    WeaponEntry.CODEC.listOf().fieldOf("weapons").forGetter(OrbitalControlTerminalSnapshot::weapons),
                    Codec.BOOL.fieldOf("truncated").forGetter(OrbitalControlTerminalSnapshot::truncated))
            .apply(instance, (selectedWeaponId, weapons, truncated) -> new OrbitalControlTerminalSnapshot(
                    selectedWeaponId.orElse(null),
                    weapons,
                    truncated)));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalControlTerminalSnapshot> STREAM_CODEC = StreamCodec.of(
            OrbitalControlTerminalSnapshot::encode,
            OrbitalControlTerminalSnapshot::decode);

    public OrbitalControlTerminalSnapshot {
        weapons = List.copyOf(weapons);
        if (weapons.size() > MAX_WEAPONS) {
            throw new IllegalArgumentException("Orbital terminal snapshot exceeds its bounded weapon limit");
        }
        if (selectedWeaponId != null && weapons.stream().noneMatch(entry -> entry.weaponId().equals(selectedWeaponId))) {
            throw new IllegalArgumentException("Orbital terminal selection is not present in its weapon list");
        }
    }

    /** Captures the stable-ID ordered weapons currently visible to one server player. */
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

    /** Returns the selected weapon view without exposing a nullable UI lookup. */
    public Optional<WeaponEntry> selectedWeapon() {
        if (this.selectedWeaponId == null) {
            return Optional.empty();
        }
        return this.weapons.stream()
                .filter(entry -> entry.weaponId().equals(this.selectedWeaponId))
                .findFirst();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrbitalControlTerminalSnapshot snapshot) {
        buffer.writeBoolean(snapshot.selectedWeaponId != null);
        if (snapshot.selectedWeaponId != null) {
            buffer.writeUUID(snapshot.selectedWeaponId);
        }
        buffer.writeVarInt(snapshot.weapons.size());
        for (WeaponEntry weapon : snapshot.weapons) {
            WeaponEntry.encode(buffer, weapon);
        }
        buffer.writeBoolean(snapshot.truncated);
    }

    private static OrbitalControlTerminalSnapshot decode(RegistryFriendlyByteBuf buffer) {
        UUID selectedWeaponId = buffer.readBoolean() ? buffer.readUUID() : null;
        int weaponCount = boundedCount(buffer, MAX_WEAPONS, "weapon");
        ObjectArrayList<WeaponEntry> weapons = new ObjectArrayList<>(weaponCount);
        for (int index = 0; index < weaponCount; index++) {
            weapons.add(WeaponEntry.decode(buffer));
        }
        return new OrbitalControlTerminalSnapshot(selectedWeaponId, weapons, buffer.readBoolean());
    }

    private static int boundedCount(RegistryFriendlyByteBuf buffer, int maximum, String description) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Orbital terminal " + description + " count exceeds " + maximum);
        }
        return count;
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.xmap(name -> Enum.valueOf(type, name), Enum::name);
    }

    private static <E extends Enum<E>> E readEnum(
                                                  RegistryFriendlyByteBuf buffer,
                                                  E[] values,
                                                  String description) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid orbital terminal " + description + " ordinal " + ordinal);
        }
        return values[ordinal];
    }

    /** Selected-weapon data required by the overview, fire-control permissions and HUD. */
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

        public static final Codec<WeaponEntry> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        UUIDUtil.CODEC.fieldOf("weapon_id").forGetter(WeaponEntry::weaponId),
                        UUIDUtil.CODEC.fieldOf("owner_id").forGetter(WeaponEntry::ownerId),
                        Codec.BOOL.fieldOf("owner").forGetter(WeaponEntry::owner),
                        ACCESS_ROLE_CODEC.optionalFieldOf("delegated_role").forGetter(entry -> Optional.ofNullable(entry.delegatedRole)),
                        Codec.INT.fieldOf("endpoint_count").forGetter(WeaponEntry::endpointCount),
                        LIFECYCLE_CODEC.fieldOf("lifecycle_state").forGetter(WeaponEntry::lifecycleState),
                        Codec.INT.fieldOf("grace_ticks_remaining").forGetter(WeaponEntry::graceTicksRemaining),
                        Codec.LONG.fieldOf("celestial_energy").forGetter(WeaponEntry::celestialEnergy),
                        Codec.LONG.fieldOf("ae_energy").forGetter(WeaponEntry::aeEnergy),
                        AttackEntry.CODEC.listOf().fieldOf("attacks").forGetter(WeaponEntry::attacks))
                .apply(instance, (weaponId, ownerId, owner, delegatedRole, endpointCount, lifecycleState,
                                  graceTicksRemaining, celestialEnergy, aeEnergy, attacks) -> new WeaponEntry(
                                          weaponId,
                                          ownerId,
                                          owner,
                                          delegatedRole.orElse(null),
                                          endpointCount,
                                          lifecycleState,
                                          graceTicksRemaining,
                                          celestialEnergy,
                                          aeEnergy,
                                          attacks)));

        public WeaponEntry {
            attacks = List.copyOf(attacks);
            if (attacks.size() > MAX_ATTACKS_PER_WEAPON) {
                throw new IllegalArgumentException("Orbital terminal weapon exceeds its bounded attack limit");
            }
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

        /** Owners and operators may use fire control and act on their selected mode task. */
        public boolean canOperate() {
            return this.owner || this.delegatedRole == OrbitalAccessRole.OPERATOR;
        }

        /** Returns the one persisted task for a mode, if that mode is active or cooling down. */
        public Optional<AttackEntry> attack(OrbitalAttackMode mode) {
            return this.attacks.stream().filter(attack -> attack.mode() == mode).findFirst();
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
                    attacks.stream().limit(MAX_ATTACKS_PER_WEAPON).map(AttackEntry::from).toList());
        }

        private static void encode(RegistryFriendlyByteBuf buffer, WeaponEntry entry) {
            buffer.writeUUID(entry.weaponId);
            buffer.writeUUID(entry.ownerId);
            buffer.writeBoolean(entry.owner);
            if (!entry.owner) {
                buffer.writeVarInt(Objects.requireNonNull(entry.delegatedRole).ordinal());
            }
            buffer.writeVarInt(entry.endpointCount);
            buffer.writeVarInt(entry.lifecycleState.ordinal());
            buffer.writeVarInt(entry.graceTicksRemaining);
            buffer.writeVarLong(entry.celestialEnergy);
            buffer.writeVarLong(entry.aeEnergy);
            buffer.writeVarInt(entry.attacks.size());
            for (AttackEntry attack : entry.attacks) {
                AttackEntry.encode(buffer, attack);
            }
        }

        private static WeaponEntry decode(RegistryFriendlyByteBuf buffer) {
            UUID weaponId = buffer.readUUID();
            UUID ownerId = buffer.readUUID();
            boolean owner = buffer.readBoolean();
            OrbitalAccessRole delegatedRole = owner ? null : readEnum(
                    buffer,
                    OrbitalAccessRole.values(),
                    "access role");
            int endpointCount = buffer.readVarInt();
            OrbitalWeaponLifecycleState lifecycleState = readEnum(
                    buffer,
                    OrbitalWeaponLifecycleState.values(),
                    "lifecycle state");
            int graceTicksRemaining = buffer.readVarInt();
            long celestialEnergy = buffer.readVarLong();
            long aeEnergy = buffer.readVarLong();
            int attackCount = boundedCount(buffer, MAX_ATTACKS_PER_WEAPON, "attack");
            ObjectArrayList<AttackEntry> attacks = new ObjectArrayList<>(attackCount);
            for (int index = 0; index < attackCount; index++) {
                attacks.add(AttackEntry.decode(buffer));
            }
            return new WeaponEntry(
                    weaponId,
                    ownerId,
                    owner,
                    delegatedRole,
                    endpointCount,
                    lifecycleState,
                    graceTicksRemaining,
                    celestialEnergy,
                    aeEnergy,
                    attacks);
        }
    }

    /** Public attack state needed by the overview, safety action and compact HUD. */
    public record AttackEntry(
                              UUID attackId,
                              OrbitalAttackMode mode,
                              OrbitalAttackPhase phase,
                              ResourceLocation dimensionId,
                              BlockPos target,
                              int warningTicksRemaining,
                              int cooldownTicksRemaining,
                              long workCursor) {

        public static final Codec<AttackEntry> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        UUIDUtil.CODEC.fieldOf("attack_id").forGetter(AttackEntry::attackId),
                        ATTACK_MODE_CODEC.fieldOf("mode").forGetter(AttackEntry::mode),
                        ATTACK_PHASE_CODEC.fieldOf("phase").forGetter(AttackEntry::phase),
                        ResourceLocation.CODEC.fieldOf("dimension_id").forGetter(AttackEntry::dimensionId),
                        BlockPos.CODEC.fieldOf("target").forGetter(AttackEntry::target),
                        Codec.INT.fieldOf("warning_ticks_remaining").forGetter(AttackEntry::warningTicksRemaining),
                        Codec.INT.fieldOf("cooldown_ticks_remaining").forGetter(AttackEntry::cooldownTicksRemaining),
                        Codec.LONG.fieldOf("work_cursor").forGetter(AttackEntry::workCursor))
                .apply(instance, AttackEntry::new));

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

        private static void encode(RegistryFriendlyByteBuf buffer, AttackEntry entry) {
            buffer.writeUUID(entry.attackId);
            buffer.writeVarInt(entry.mode.ordinal());
            buffer.writeVarInt(entry.phase.ordinal());
            buffer.writeResourceLocation(entry.dimensionId);
            buffer.writeBlockPos(entry.target);
            buffer.writeVarInt(entry.warningTicksRemaining);
            buffer.writeVarInt(entry.cooldownTicksRemaining);
            buffer.writeVarLong(entry.workCursor);
        }

        private static AttackEntry decode(RegistryFriendlyByteBuf buffer) {
            return new AttackEntry(
                    buffer.readUUID(),
                    readEnum(buffer, OrbitalAttackMode.values(), "attack mode"),
                    readEnum(buffer, OrbitalAttackPhase.values(), "attack phase"),
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarLong());
        }
    }
}
