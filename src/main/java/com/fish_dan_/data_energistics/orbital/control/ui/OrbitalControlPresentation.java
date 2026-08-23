package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewEstimate;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.AttackEntry;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalControlFeedback;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalFireControlSessionSnapshot.PreviewDetails;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** One translation-aware presentation boundary shared by the full control surface and compact HUD. */
public final class OrbitalControlPresentation {

    private static final String PREFIX = "screen.data_energistics.orbital_control_terminal.";

    private OrbitalControlPresentation() {}

    public static Component weaponTitle(OrbitalControlTerminalSnapshot snapshot) {
        return snapshot.selectedWeapon()
                .<Component>map(weapon -> Component.translatable(
                        PREFIX + "overview.weapon_title",
                        shortId(weapon.weaponId())))
                .orElseGet(() -> Component.translatable(PREFIX + "empty"));
    }

    public static Component selectorPosition(OrbitalControlTerminalSnapshot snapshot) {
        if (snapshot.selectedWeaponId() == null) {
            return Component.translatable(PREFIX + "selector.empty");
        }
        int index = 0;
        for (int candidate = 0; candidate < snapshot.weapons().size(); candidate++) {
            if (snapshot.weapons().get(candidate).weaponId().equals(snapshot.selectedWeaponId())) {
                index = candidate + 1;
                break;
            }
        }
        return Component.translatable(
                snapshot.truncated() ? PREFIX + "selector.position_truncated" : PREFIX + "selector.position",
                index,
                snapshot.weapons().size());
    }

    public static Component identity(WeaponEntry weapon) {
        return Component.translatable(
                PREFIX + "overview.identity",
                role(weapon),
                shortId(weapon.ownerId()));
    }

    public static Component lifecycle(WeaponEntry weapon) {
        return Component.translatable(
                PREFIX + "overview.lifecycle",
                lifecycleState(weapon),
                weapon.endpointCount());
    }

    public static Component celestialEnergy(WeaponEntry weapon) {
        return Component.translatable(
                PREFIX + "overview.resource.celestial",
                formatAmount(weapon.celestialEnergy()));
    }

    public static Component aeEnergy(WeaponEntry weapon) {
        return Component.translatable(
                PREFIX + "overview.resource.ae",
                formatAmount(weapon.aeEnergy()));
    }

    public static Component modeCard(WeaponEntry weapon, OrbitalAttackMode mode) {
        AttackEntry attack = weapon.attack(mode).orElse(null);
        if (attack == null) {
            return Component.translatable(PREFIX + "overview.mode.idle", modeName(mode));
        }
        return Component.translatable(
                PREFIX + "overview.mode.active",
                modeName(mode),
                phaseName(attack.phase()),
                Component.literal(attack.dimensionId().toString()),
                attack.target().getX(),
                attack.target().getY(),
                attack.target().getZ(),
                progress(attack));
    }

    /** Compact mode state used by the dashboard rail without repeating target coordinates. */
    public static Component modeRail(WeaponEntry weapon, OrbitalAttackMode mode) {
        AttackEntry attack = weapon.attack(mode).orElse(null);
        if (attack == null) {
            return Component.translatable(PREFIX + "overview.mode.idle", modeName(mode));
        }
        return Component.translatable(
                "screen.data_energistics.orbital_control_hud.attack_compact",
                modeName(mode),
                phaseName(attack.phase()),
                progress(attack));
    }

    public static Component modeAction(WeaponEntry weapon, OrbitalAttackMode mode) {
        AttackEntry attack = weapon.attack(mode).orElse(null);
        if (attack == null) {
            return Component.translatable(PREFIX + "overview.action.none");
        }
        return switch (attack.phase()) {
            case RESERVED_WARNING -> Component.translatable(PREFIX + "overview.action.cancel_warning");
            case COMMITTED, DELIVERY -> Component.translatable(PREFIX + "overview.action.emergency_abort");
            default -> Component.translatable(PREFIX + "overview.action.none");
        };
    }

    /** Builds all localized fire-control text on the receiving client from typed server state. */
    public static Component fireControl(
                                        OrbitalFireControlSessionSnapshot snapshot,
                                        OrbitalControlFeedback feedback) {
        return switch (snapshot.phase()) {
            case IDLE -> Component.translatable(PREFIX + "preview.none");
            case REJECTED -> feedback(feedback == OrbitalControlFeedback.NONE ?
                    OrbitalControlFeedback.ACTION_REJECTED : feedback);
            case CALCULATING -> Component.translatable(
                    PREFIX + "preview.calculating",
                    snapshot.checkedChunks(),
                    snapshot.totalChunks());
            case READY, HOLDING -> preview(snapshot);
        };
    }

    public static boolean modeActionAvailable(WeaponEntry weapon, OrbitalAttackMode mode) {
        if (!weapon.canOperate()) {
            return false;
        }
        AttackEntry attack = weapon.attack(mode).orElse(null);
        return attack != null && switch (attack.phase()) {
            case RESERVED_WARNING, COMMITTED, DELIVERY -> true;
            default -> false;
        };
    }

    /** Builds the compact HUD from the same component functions used by overview cards. */
    public static Component hud(OrbitalControlTerminalSnapshot snapshot) {
        WeaponEntry weapon = snapshot.selectedWeapon().orElse(null);
        if (weapon == null) {
            return Component.translatable("screen.data_energistics.orbital_control_hud.empty");
        }
        MutableComponent result = weaponTitle(snapshot).copy()
                .append(Component.literal("  •  "))
                .append(role(weapon))
                .append(Component.literal("  •  "))
                .append(lifecycleState(weapon))
                .append(Component.literal("\n"))
                .append(Component.translatable(
                        "screen.data_energistics.orbital_control_hud.resources",
                        formatAmount(weapon.celestialEnergy()),
                        formatAmount(weapon.aeEnergy())));
        if (weapon.attacks().isEmpty()) {
            return result
                    .append(Component.literal("\n"))
                    .append(Component.translatable("screen.data_energistics.orbital_control_hud.idle"));
        }
        for (AttackEntry attack : weapon.attacks()) {
            result.append(Component.literal("\n")).append(Component.translatable(
                    "screen.data_energistics.orbital_control_hud.attack_compact",
                    modeName(attack.mode()),
                    phaseName(attack.phase()),
                    progress(attack)));
        }
        return result;
    }

    public static Component modeName(OrbitalAttackMode mode) {
        return Component.translatable(PREFIX + "mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public static Component phaseName(OrbitalAttackPhase phase) {
        return Component.translatable(PREFIX + "phase." + phase.name().toLowerCase(Locale.ROOT));
    }

    private static Component preview(OrbitalFireControlSessionSnapshot snapshot) {
        PreviewDetails details = Objects.requireNonNull(snapshot.preview());
        OrbitalAttackPreviewEstimate estimate = Objects.requireNonNull(details.estimate());
        long remainingTicks = Math.max(0L, snapshot.expiresAt() - snapshot.serverGameTime());
        long remainingSeconds = remainingTicks == 0L ? 0L : 1L + (remainingTicks - 1L) / 20L;
        MutableComponent status = Component.translatable(
                PREFIX + "preview.target",
                modeName(details.mode()),
                Component.literal(details.dimensionId().toString()),
                details.target().getX(),
                details.target().getY(),
                details.target().getZ());
        status.append("\n").append(Component.translatable(
                PREFIX + "preview.geometry",
                estimate.effectRadius(),
                details.mode() == OrbitalAttackMode.DIRECTED_ENERGY ?
                        depthName(Objects.requireNonNull(details.directedDepth())) :
                        Component.translatable(PREFIX + "preview.depth.not_applicable")));
        status.append("\n").append(Component.translatable(
                PREFIX + "preview.chunks",
                estimate.affectedChunks(),
                estimate.unloadedChunks()));
        status.append("\n").append(Component.translatable(
                PREFIX + "preview.work",
                estimate.scheduledBlocks(),
                estimate.minimumExecutionTicks()));
        if (estimate.scheduledCoordinates() > 0L) {
            status.append("\n").append(Component.translatable(
                    PREFIX + "preview.coordinates",
                    estimate.scheduledCoordinates()));
        }
        status.append("\n").append(Component.translatable(
                PREFIX + "preview.cost",
                estimate.cost().celestialEnergy(),
                estimate.cost().aeEnergy()));
        status.append("\n").append(Component.translatable(
                PREFIX + "preview.reserve",
                estimate.availableCelestialEnergy(),
                estimate.availableAeEnergy(),
                Component.translatable(estimate.affordable() ?
                        PREFIX + "preview.affordable" : PREFIX + "preview.unaffordable")));
        status.append("\n").append(Component.translatable(
                snapshot.phase() == OrbitalFireControlSessionSnapshot.Phase.HOLDING ?
                        PREFIX + "preview.holding" : PREFIX + "preview.ready",
                Math.min(snapshot.heldTicks(), snapshot.requiredHoldTicks()),
                snapshot.requiredHoldTicks(),
                remainingSeconds));
        return status;
    }

    /** Returns the one compact header message for the latest server command result. */
    public static Component feedback(OrbitalControlFeedback feedback) {
        return feedback == OrbitalControlFeedback.NONE ? Component.translatable(PREFIX + "subtitle") :
                Component.translatable(PREFIX + "feedback." + feedback.name().toLowerCase(Locale.ROOT));
    }

    private static Component depthName(OrbitalDirectedEnergyDepth depth) {
        return Component.translatable(switch (depth) {
            case DEPTH_32 -> "screen.data_energistics.orbital_control_terminal.depth.32";
            case DEPTH_128 -> "screen.data_energistics.orbital_control_terminal.depth.128";
            case DEPTH_512 -> "screen.data_energistics.orbital_control_terminal.depth.512";
            case THROUGH -> "screen.data_energistics.orbital_control_terminal.depth.through";
        });
    }

    private static Component role(WeaponEntry weapon) {
        String role = weapon.owner() ? "owner" : switch (Objects.requireNonNull(weapon.delegatedRole())) {
            case OPERATOR -> "operator";
            case OBSERVER -> "observer";
        };
        return Component.translatable(PREFIX + "role." + role);
    }

    private static Component lifecycleState(WeaponEntry weapon) {
        String key = PREFIX + "lifecycle." + weapon.lifecycleState().name().toLowerCase(Locale.ROOT);
        return weapon.lifecycleState() == OrbitalWeaponLifecycleState.RESERVE_GRACE ?
                Component.translatable(key, weapon.graceTicksRemaining()) : Component.translatable(key);
    }

    private static Component progress(AttackEntry attack) {
        if (attack.warningTicksRemaining() > 0) {
            return Component.translatable(PREFIX + "overview.progress.warning", attack.warningTicksRemaining());
        }
        if (attack.cooldownTicksRemaining() > 0) {
            return Component.translatable(PREFIX + "overview.progress.cooldown", attack.cooldownTicksRemaining());
        }
        if (attack.workCursor() > 0L) {
            return Component.translatable(PREFIX + "overview.progress.work", formatAmount(attack.workCursor()));
        }
        return Component.translatable(PREFIX + "overview.progress.pending");
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String formatAmount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
