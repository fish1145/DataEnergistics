package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponAction;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Server-side entry point for the small set of actions exposed by the LDLib2 orbital control surface.
 *
 * <p>
 * The client supplies only a bounded target intent. The server resolves weapon identity, target height, cost,
 * configuration/state revisions and endpoint availability, while every mutation is delegated to the existing
 * SavedData transaction for the final access, reserve, lifecycle and task-capacity checks.
 * </p>
 */
public final class OrbitalControlActionDispatcher {

    private static final double TARGET_DISTANCE = 256.0D;
    private static final float TARGET_TICK_DELTA = 1.0F;
    private static final int DIRECTED_RADIUS = OrbitalDirectedEnergyStrike.MIN_RADIUS;
    private static final OrbitalAttackPreviewSessions PREVIEWS = new OrbitalAttackPreviewSessions();

    private OrbitalControlActionDispatcher() {}

    /**
     * Captures a server-side target preview. No reserve is debited until the matching release has held for the full
     * confirmation interval.
     */
    public static Optional<OrbitalAttackPreviewSessions.Preview> beginFireAtLookTarget(
                                                                                       ServerPlayer player,
                                                                                       OrbitalAttackMode mode,
                                                                                       BooleanSupplier sourceValid) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || !sourceValid.getAsBoolean()) {
            return Optional.empty();
        }
        BlockPos target = blockTarget(player);
        if (target == null) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.data_energistics.orbital_control_terminal.no_block_target"),
                    true);
            return Optional.empty();
        }
        OrbitalDirectedEnergyDepth depth = mode == OrbitalAttackMode.DIRECTED_ENERGY ? OrbitalDirectedEnergyDepth.DEPTH_32 : null;
        int directedRadius = mode == OrbitalAttackMode.DIRECTED_ENERGY ? DIRECTED_RADIUS : 0;
        return beginFireAtTarget(
                player,
                mode,
                player.level().dimension().location(),
                target.getX(),
                target.getZ(),
                OrbitalTargetYMode.ABSOLUTE,
                target.getY(),
                directedRadius,
                depth,
                sourceValid);
    }

    /** Captures a map-selected target and immediately starts the legacy one-gesture confirmation hold. */
    public static Optional<OrbitalAttackPreviewSessions.Preview> beginFireAtTarget(
                                                                                   ServerPlayer player,
                                                                                   OrbitalAttackMode mode,
                                                                                   ResourceLocation dimensionId,
                                                                                   int targetX,
                                                                                   int targetZ,
                                                                                   OrbitalTargetYMode targetYMode,
                                                                                   int targetYValue,
                                                                                   int directedRadius,
                                                                                   @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                                                                   BooleanSupplier sourceValid) {
        return captureFireTarget(
                player,
                mode,
                dimensionId,
                targetX,
                targetZ,
                targetYMode,
                targetYValue,
                directedRadius,
                directedDepth,
                sourceValid,
                true);
    }

    /** Captures a server-side preview without starting its independent 60-tick confirmation hold. */
    public static void previewFireAtTarget(
                                           ServerPlayer player,
                                           OrbitalAttackMode mode,
                                           ResourceLocation dimensionId,
                                           int targetX,
                                           int targetZ,
                                           OrbitalTargetYMode targetYMode,
                                           int targetYValue,
                                           int directedRadius,
                                           @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                           BooleanSupplier sourceValid) {
        captureFireTarget(
                player,
                mode,
                dimensionId,
                targetX,
                targetZ,
                targetYMode,
                targetYValue,
                directedRadius,
                directedDepth,
                sourceValid,
                false);
    }

    /**
     * Captures a server-side preview for a map-selected target. Surface-relative height is resolved only from an
     * already loaded chunk; the map intent never causes an unbounded synchronous generation.
     */
    private static Optional<OrbitalAttackPreviewSessions.Preview> captureFireTarget(
                                                                                    ServerPlayer player,
                                                                                    OrbitalAttackMode mode,
                                                                                    ResourceLocation dimensionId,
                                                                                    int targetX,
                                                                                    int targetZ,
                                                                                    OrbitalTargetYMode targetYMode,
                                                                                    int targetYValue,
                                                                                    int directedRadius,
                                                                                    @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                                                                    BooleanSupplier sourceValid,
                                                                                    boolean startHold) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || !sourceValid.getAsBoolean()) {
            return Optional.empty();
        }
        DataEnergisticsConfiguration configuration = DataEnergisticsConfiguration.INSTANCE;
        boolean attackEnabled = switch (mode) {
            case KINETIC -> configuration.orbitalWeapon.kineticAttackEnabled;
            case DIRECTED_ENERGY -> configuration.orbitalWeapon.directedEnergyAttackEnabled;
            case DIGITAL_ANNIHILATION -> configuration.orbitalWeapon.digitalAnnihilationAttackEnabled;
        };
        if (!attackEnabled) {
            return Optional.empty();
        }
        if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
            try {
                OrbitalDirectedEnergyStrike.validateRadius(directedRadius);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            if (directedDepth == null) {
                return Optional.empty();
            }
        } else if (directedRadius != 0 || directedDepth != null) {
            return Optional.empty();
        }
        PREVIEWS.expire(server);
        ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (targetLevel == null) {
            return Optional.empty();
        }
        int targetY;
        try {
            targetY = resolveTargetY(targetLevel, targetX, targetZ, targetYMode, targetYValue);
        } catch (IllegalArgumentException exception) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.target_out_of_bounds"),
                    true);
            return Optional.empty();
        }
        BlockPos target = new BlockPos(targetX, targetY, targetZ);
        int boundaryRadius = switch (mode) {
            case KINETIC -> OrbitalAttackGeometry.Kinetic.fromSettings(configuration.orbitalWeapon).maximumRadius();
            case DIRECTED_ENERGY -> directedRadius;
            case DIGITAL_ANNIHILATION -> configuration.explosives.dataNuke.maxRadius;
        };
        if (!validTarget(targetLevel, target, boundaryRadius)) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.target_out_of_bounds"),
                    true);
            return Optional.empty();
        }

        OrbitalWeaponSavedData weaponData = OrbitalWeaponSavedData.get(server);
        Optional<UUID> preferredWeaponId = weaponData.preferredWeaponId(player.getUUID());
        Optional<OrbitalWeaponRecord> weapon = weaponData.accessibleTo(player.getUUID())
                .stream()
                .filter(candidate -> preferredWeaponId.map(id -> id.equals(candidate.weaponId())).orElse(true))
                .filter(candidate -> candidate.canPerform(player.getUUID(), OrbitalWeaponAction.FIRE))
                .findFirst();
        if (weapon.isEmpty()) {
            return Optional.empty();
        }
        UUID weaponId = weapon.orElseThrow().weaponId();
        if (!weaponData.hasOnlineEndpoint(server, weaponId, dimensionId)) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.endpoint_unavailable"),
                    true);
            return Optional.empty();
        }
        if (!PREVIEWS.acceptsBegin(server, player.getUUID())) {
            return Optional.empty();
        }
        try {
            OrbitalAttackPreviewEstimate estimate = OrbitalAttackPreviewEstimate.capture(
                    configuration,
                    targetLevel,
                    target,
                    mode,
                    mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedRadius : 0,
                    mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedDepth : null,
                    weapon.orElseThrow().reserve());
            Optional<OrbitalAttackPreviewSessions.Preview> preview = PREVIEWS.begin(
                    server,
                    player.getUUID(),
                    mode,
                    dimensionId,
                    target,
                    weaponId,
                    mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedRadius : 0,
                    mode == OrbitalAttackMode.DIRECTED_ENERGY ? directedDepth : null,
                    configuration.revision(),
                    stateRevision(weapon.orElseThrow()),
                    estimate);
            if (startHold && preview.isPresent() && !PREVIEWS.beginHold(server, player.getUUID(), mode)) {
                PREVIEWS.cancel(server, player.getUUID());
                return Optional.empty();
            }
            preview.ifPresent(ignored -> player.displayClientMessage(
                    Component.translatable(
                            startHold ? "message.data_energistics.orbital_control_terminal.preview_started" : "message.data_energistics.orbital_control_terminal.preview_ready",
                            mode.name()),
                    true));
            return preview;
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital control target preview {} failed for player {} at {}",
                    mode,
                    player.getUUID(),
                    target,
                    exception);
            return Optional.empty();
        }
    }

    private static int resolveTargetY(
                                      ServerLevel level,
                                      int targetX,
                                      int targetZ,
                                      OrbitalTargetYMode mode,
                                      int value) {
        if (mode == OrbitalTargetYMode.ABSOLUTE) {
            return value;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(targetX >> 4, targetZ >> 4);
        if (chunk == null) {
            throw new IllegalArgumentException("Surface-relative target requires a loaded target chunk");
        }
        int surfaceY = chunk.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                Math.floorMod(targetX, 16),
                Math.floorMod(targetZ, 16));
        try {
            return Math.addExact(surfaceY, value);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Surface-relative target Y overflow", exception);
        }
    }

    /** Starts the 60-tick confirmation clock for the player's existing immutable target preview. */
    public static boolean startFireHold(
                                        ServerPlayer player,
                                        OrbitalAttackMode mode,
                                        BooleanSupplier sourceValid) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || !sourceValid.getAsBoolean()) {
            return false;
        }
        boolean started = PREVIEWS.beginHold(server, player.getUUID(), mode);
        if (started) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.hold_started"),
                    true);
        }
        return started;
    }

    /** Stops an incomplete confirmation hold while retaining the underlying preview until its normal expiry. */
    public static void cancelFireHold(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && server.isSameThread()) {
            PREVIEWS.cancelHold(server, player.getUUID());
        }
    }

    /**
     * Commits a previously captured target after the server-clock hold and configuration/state revision checks pass.
     */
    public static Optional<OrbitalAttackRecord> releaseFireAtTarget(
                                                                    ServerPlayer player,
                                                                    OrbitalAttackMode mode,
                                                                    BooleanSupplier sourceValid) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        Optional<OrbitalAttackPreviewSessions.Preview> released = PREVIEWS.release(server, player.getUUID(), mode);
        if (released.isEmpty()) {
            if (sourceValid.getAsBoolean()) {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.orbital_control_terminal.hold_incomplete"),
                        true);
            }
            return Optional.empty();
        }
        OrbitalAttackPreviewSessions.Preview preview = released.orElseThrow();
        if (!sourceValid.getAsBoolean()) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.preview_expired"),
                    true);
            return Optional.empty();
        }
        if (DataEnergisticsConfiguration.INSTANCE.revision() != preview.configurationRevision()) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.configuration_changed"),
                    true);
            return Optional.empty();
        }

        OrbitalWeaponSavedData weaponData = OrbitalWeaponSavedData.get(server);
        Optional<OrbitalWeaponRecord> weapon = weaponData.find(preview.weaponId());
        if (weapon.isEmpty() || stateRevision(weapon.orElseThrow()) != preview.stateRevision()) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.preview_expired"),
                    true);
            return Optional.empty();
        }

        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        Optional<OrbitalAttackRecord> result;
        try {
            result = switch (preview.mode()) {
                case KINETIC -> attacks.tryConfirmKinetic(
                        server,
                        player.getUUID(),
                        preview.weaponId(),
                        preview.dimensionId(),
                        preview.target());
                case DIRECTED_ENERGY -> attacks.tryConfirmDirectedEnergy(
                        server,
                        player.getUUID(),
                        preview.weaponId(),
                        preview.dimensionId(),
                        preview.target(),
                        preview.directedRadius(),
                        preview.directedDepth());
                case DIGITAL_ANNIHILATION -> attacks.tryConfirmDigitalAnnihilation(
                        server,
                        player.getUUID(),
                        preview.weaponId(),
                        preview.dimensionId(),
                        preview.target());
            };
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital control confirmation {} failed for player {} at {}",
                    mode,
                    player.getUUID(),
                    preview.target(),
                    exception);
            return Optional.empty();
        }
        result.ifPresent(ignored -> player.displayClientMessage(
                Component.translatable(
                        "message.data_energistics.orbital_control_terminal.warning_reserved",
                        mode.name()),
                true));
        if (result.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.orbital_control_terminal.action_rejected"),
                    true);
        }
        return result;
    }

    /** Compatibility name retained for the handheld look-target buttons. */
    public static Optional<OrbitalAttackRecord> releaseFireAtLookTarget(
                                                                        ServerPlayer player,
                                                                        OrbitalAttackMode mode,
                                                                        BooleanSupplier sourceValid) {
        return releaseFireAtTarget(player, mode, sourceValid);
    }

    /** Cancels a player's uncommitted target hold without touching weapon reserves. */
    public static boolean cancelFirePreview(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.isSameThread() && PREVIEWS.cancel(server, player.getUUID());
    }

    /** Expires abandoned previews from the server tick lifecycle. */
    public static void expirePreviews(MinecraftServer server) {
        if (server.isSameThread()) {
            PREVIEWS.expire(server);
        }
    }

    /** Returns the live server-owned target and workload preview synchronized into the open LDLib2 control surface. */
    public static Component currentPreviewStatus(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return Component.empty();
        }
        Optional<OrbitalAttackPreviewSessions.Preview> current = PREVIEWS.current(server, player.getUUID());
        if (current.isEmpty()) {
            return Component.translatable("screen.data_energistics.orbital_control_terminal.preview.none");
        }
        OrbitalAttackPreviewSessions.Preview preview = current.orElseThrow();
        OrbitalAttackPreviewEstimate estimate = preview.estimate();
        long now = server.overworld().getGameTime();
        long remainingTicks = Math.max(0L, preview.expiresAt() - now);
        long remainingSeconds = remainingTicks == 0L ? 0L : 1L + (remainingTicks - 1L) / 20L;
        MutableComponent status = Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.target",
                modeName(preview.mode()),
                preview.dimensionId(),
                preview.target().getX(),
                preview.target().getY(),
                preview.target().getZ());
        status.append("\n").append(Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.geometry",
                estimate.effectRadius(),
                preview.mode() == OrbitalAttackMode.DIRECTED_ENERGY ? depthName(preview.directedDepth()) : Component.translatable("screen.data_energistics.orbital_control_terminal.preview.depth.not_applicable")));
        status.append("\n").append(Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.chunks",
                estimate.affectedChunks(),
                estimate.unloadedChunks()));
        status.append("\n").append(Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.work",
                estimate.scheduledBlocks(),
                estimate.minimumExecutionTicks()));
        if (estimate.scheduledCoordinates() > 0L) {
            status.append("\n").append(Component.translatable(
                    "screen.data_energistics.orbital_control_terminal.preview.coordinates",
                    estimate.scheduledCoordinates()));
        }
        status.append("\n").append(Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.cost",
                estimate.cost().celestialEnergy(),
                estimate.cost().aeEnergy()));
        status.append("\n").append(Component.translatable(
                "screen.data_energistics.orbital_control_terminal.preview.reserve",
                estimate.availableCelestialEnergy(),
                estimate.availableAeEnergy(),
                Component.translatable(estimate.affordable() ? "screen.data_energistics.orbital_control_terminal.preview.affordable" : "screen.data_energistics.orbital_control_terminal.preview.unaffordable")));
        status.append("\n").append(Component.translatable(
                preview.holdStarted() ? "screen.data_energistics.orbital_control_terminal.preview.holding" : "screen.data_energistics.orbital_control_terminal.preview.ready",
                Math.min(preview.heldTicks(now), OrbitalAttackPreviewSessions.DEFAULT_HOLD_TICKS),
                OrbitalAttackPreviewSessions.DEFAULT_HOLD_TICKS,
                remainingSeconds));
        return status;
    }

    private static Component modeName(OrbitalAttackMode mode) {
        return Component.translatable(switch (mode) {
            case KINETIC -> "screen.data_energistics.orbital_control_terminal.mode.kinetic";
            case DIRECTED_ENERGY -> "screen.data_energistics.orbital_control_terminal.mode.directed_energy";
            case DIGITAL_ANNIHILATION -> "screen.data_energistics.orbital_control_terminal.mode.digital_annihilation";
        });
    }

    private static Component depthName(@Nullable OrbitalDirectedEnergyDepth depth) {
        if (depth == null) {
            throw new IllegalStateException("Directed-energy preview is missing its captured depth");
        }
        return Component.translatable(switch (depth) {
            case DEPTH_32 -> "screen.data_energistics.orbital_control_terminal.depth.32";
            case DEPTH_128 -> "screen.data_energistics.orbital_control_terminal.depth.128";
            case DEPTH_512 -> "screen.data_energistics.orbital_control_terminal.depth.512";
            case THROUGH -> "screen.data_energistics.orbital_control_terminal.depth.through";
        });
    }

    /**
     * Advances the player's persisted server-side weapon selection. The UI sends only the direction; the server
     * resolves the accessible UUID list and never trusts a client-supplied weapon identity.
     */
    public static Optional<UUID> cycleWeapon(ServerPlayer player, boolean forward) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return Optional.empty();
        }
        return OrbitalWeaponSavedData.get(server).selectNext(server, player.getUUID(), forward);
    }

    /**
     * Cancels the first warning attack the player is currently authorized to cancel.
     *
     * @return true only after the warning escrow was refunded by the authoritative attack store
     */
    public static boolean cancelFirstWarning(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        try {
            return OrbitalWeaponSavedData.get(server)
                    .accessibleTo(player.getUUID())
                    .stream()
                    .flatMap(weapon -> attacks.forWeapon(weapon.weaponId()).stream())
                    .filter(attack -> attack.phase() == OrbitalAttackPhase.RESERVED_WARNING)
                    .sorted(Comparator.comparing(OrbitalAttackRecord::attackId))
                    .map(attack -> attacks.cancelWarning(server, player.getUUID(), attack.attackId()))
                    .filter(Boolean::booleanValue)
                    .findFirst()
                    .orElse(false);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital warning cancellation failed for player {}",
                    player.getUUID(),
                    exception);
            return false;
        }
    }

    /**
     * Performs the button's stop action: refunds a warning when one exists, otherwise emergency-aborts the first
     * committed or delivered attack. The server decides which weapon and attack are eligible.
     */
    public static boolean cancelOrAbortFirst(ServerPlayer player) {
        if (cancelFirePreview(player)) {
            return true;
        }
        if (cancelFirstWarning(player)) {
            return true;
        }
        return emergencyAbortFirst(player);
    }

    /** Aborts the first active attack for which the player has the server-side emergency-abort permission. */
    public static boolean emergencyAbortFirst(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        try {
            return OrbitalWeaponSavedData.get(server)
                    .accessibleTo(player.getUUID())
                    .stream()
                    .flatMap(weapon -> attacks.forWeapon(weapon.weaponId()).stream())
                    .filter(attack -> attack.phase() == OrbitalAttackPhase.COMMITTED || attack.phase() == OrbitalAttackPhase.DELIVERY)
                    .sorted(Comparator.comparing(OrbitalAttackRecord::attackId))
                    .map(attack -> attacks.emergencyAbort(server, player.getUUID(), attack.attackId()))
                    .filter(Boolean::booleanValue)
                    .findFirst()
                    .orElse(false);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Orbital emergency abort failed for player {}",
                    player.getUUID(),
                    exception);
            return false;
        }
    }

    @Nullable
    private static BlockPos blockTarget(ServerPlayer player) {
        HitResult hit = player.pick(TARGET_DISTANCE, TARGET_TICK_DELTA, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        return blockHit.getBlockPos().immutable();
    }

    private static boolean validTarget(ServerLevel level, BlockPos target, int radius) {
        if (target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        return level.getWorldBorder().isWithinBounds(target) && level.getWorldBorder().isWithinBounds(target.offset(-radius, 0, -radius)) && level.getWorldBorder().isWithinBounds(target.offset(radius, 0, radius));
    }

    private static long stateRevision(OrbitalWeaponRecord weapon) {
        long revision = 17L;
        revision = 31L * revision + weapon.ownerId().hashCode();
        revision = 31L * revision + weapon.delegatedRoles().hashCode();
        revision = 31L * revision + weapon.endpoints().hashCode();
        revision = 31L * revision + weapon.lifecycle().hashCode();
        revision = 31L * revision + (weapon.primaryAnchor() == null ? 0 : weapon.primaryAnchor().hashCode());
        return revision & Long.MAX_VALUE;
    }
}
