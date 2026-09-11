package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;
import com.fish_dan_.data_energistics.orbital.storage.StellarErasureDeviceSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import com.mojang.authlib.GameProfile;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalDigitalAnnihilationGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(4, 2, 2);
    private static final BlockPos TARGET = new BlockPos(25, 20, 25);
    private static final BlockPos DUPLICATE_TARGET = new BlockPos(40, 20, 40);

    private OrbitalDigitalAnnihilationGameTest() {}

    @TestHolder("orbital_digital_annihilation_payload_descends_and_materializes_fuse")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void payloadDescendsAndMaterializesFuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StellarErasureDeviceSavedData weapons = StellarErasureDeviceSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-annihilation-owner");
        DataEnergisticsConfiguration.StellarErasureDeviceSchema settings = DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);
        level.getChunk(absoluteTarget.getX() >> 4, absoluteTarget.getZ() >> 4);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> attackId = new AtomicReference<>();
        AtomicReference<Double> payloadStartY = new AtomicReference<>();
        AtomicReference<GameTestPlayer> victim = new AtomicReference<>();
        AtomicReference<GameTestPlayer> boundaryVictim = new AtomicReference<>();
        AtomicReference<GameTestPlayer> outsideVictim = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The digital payload must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertStellarFlux(helper, requiredStellarFlux(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    OrbitalEnergyReserve before = weapons.find(weaponId).orElseThrow().reserve();
                    owner.setPos(
                            absoluteTarget.getX() + 0.5D,
                            absoluteTarget.getY() + 3.0D,
                            absoluteTarget.getZ() + 0.5D);
                    owner.setXRot(90.0F);
                    owner.setYRot(0.0F);
                    OrbitalAttackRecord warning = attacks.tryConfirmDigitalAnnihilation(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            absoluteTarget)
                            .orElseThrow(() -> new IllegalStateException("A funded digital payload was rejected"));
                    attackId.set(warning.attackId());
                    OrbitalEnergyReserve after = weapons.find(weaponId).orElseThrow().reserve();
                    helper.assertValueEqual(
                            before.stellarFlux() - after.stellarFlux(),
                            cost.stellarFlux(),
                            "Digital confirmation must escrow its configured Stellar Flux cost");
                    helper.assertValueEqual(
                            before.aeEnergy() - after.aeEnergy(),
                            cost.aeEnergy(),
                            "Digital confirmation must escrow its configured AE energy cost");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "The target must remain unchanged when the public warning begins");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The charging source must be removable before the payload delivery assertions");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CONTROL_CONSOLE), false),
                            "The endpoint must be removable after confirmation without cancelling the payload");
                })
                .thenIdle(Math.max(1, settings.attackWarningTicks / 2))
                .thenExecute(() -> {
                    OrbitalAttackRecord warning = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            warning.phase(),
                            OrbitalAttackPhase.RESERVED_WARNING,
                            "The digital payload must remain refundable during its warning");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "The target block must not be changed during the warning");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            delivery.phase(),
                            OrbitalAttackPhase.DELIVERY,
                            "The warning must commit into a digital payload delivery");
                    helper.assertTrue(
                            delivery.payloadEntityId() != null,
                            "A committed digital attack must persist its payload entity identity");
                    Entity payload = level.getEntity(delivery.payloadEntityId());
                    helper.assertTrue(
                            payload instanceof OrbitalAnnihilatorProjectileEntity,
                            "The committed attack must spawn the dedicated orbital payload entity");
                    payloadStartY.set(payload.getY());
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "The descending payload must not destroy blocks along its path");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    Entity payload = level.getEntity(delivery.payloadEntityId());
                    helper.assertTrue(
                            payload instanceof OrbitalAnnihilatorProjectileEntity,
                            "The payload must still exist halfway through its descent");
                    helper.assertTrue(
                            payload.getY() < payloadStartY.get(),
                            "The payload must descend monotonically toward the captured target Y");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "Passing through a block must not cause early digital annihilation");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertTrue(
                            delivery.payloadArrived(),
                            "The payload must report materialization after exactly 80 flight ticks");
                    Entity nuke = level.getEntity(delivery.payloadEntityId());
                    helper.assertTrue(
                            nuke instanceof DataNukePrimedEntity,
                            "Materialization must use the existing data-nuke fuse entity");
                    DataNukePrimedEntity digitalNuke = (DataNukePrimedEntity) nuke;
                    helper.assertTrue(
                            digitalNuke.orbitalAttackId().equals(attackId.get()),
                            "The fuse entity must retain its originating attack UUID");
                    helper.assertTrue(
                            digitalNuke.damageExemptions().contains(owner.getUUID()),
                            "The fuse entity must retain the frozen owner exemption UUID");
                    helper.assertTrue(
                            digitalNuke.getFuse() <= 80 && digitalNuke.getFuse() >= 78,
                            "The materialized fuse must begin with the configured 80-tick fuse");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "The target must remain unchanged until the fuse completes");
                })
                .thenExecute(() -> {
                    ExtendedGameTestHelper playerHelper = new ExtendedGameTestHelper(helper.testInfo);
                    GameTestPlayer spawned = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                    spawned.moveTo(absoluteTarget.getX() + 0.5, absoluteTarget.getY() + 0.5, absoluteTarget.getZ() + 0.5);
                    spawned.setNoGravity(true);
                    spawned.setInvulnerable(true);
                    spawned.subscribe((LivingIncomingDamageEvent event) -> {
                        if (event.getEntity() == spawned) {
                            event.setCanceled(true);
                        }
                    });
                    victim.set(spawned);
                    OrbitalAttackGeometry.DigitalAnnihilation geometry = (OrbitalAttackGeometry.DigitalAnnihilation) attacks.find(attackId.get()).orElseThrow().geometry();
                    double radius = geometry.centerEntityConsumeRadius();
                    GameTestPlayer boundary = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                    boundary.moveTo(absoluteTarget.getX() + 0.5 + radius + 0.25, absoluteTarget.getY() - 0.4, absoluteTarget.getZ() + 0.5);
                    boundary.setNoGravity(true);
                    boundary.setInvulnerable(true);
                    boundaryVictim.set(boundary);
                    GameTestPlayer outside = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
                    outside.moveTo(absoluteTarget.getX() + 0.5 + radius + 2, absoluteTarget.getY() - 0.4, absoluteTarget.getZ() + 0.5);
                    outside.setNoGravity(true);
                    outside.setInvulnerable(true);
                    outsideVictim.set(outside);
                })
                .thenWaitUntil(() -> helper.assertFalse(victim.get().isAlive(),
                        "The activated orbital fuse must erase the player despite a cancelled damage event"))
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    boolean boundaryWasHit = !boundaryVictim.get().isAlive();
                    boolean outsideSurvived = outsideVictim.get().isAlive();
                    helper.assertTrue(
                            attacks.adminAbort(server, attackId.get()),
                            "The active orbital payload must remain abortable after its first entity erasure");
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) == null,
                            "Aborting the materialized fuse must remove its live payload entity");
                    helper.assertTrue(boundaryWasHit, "The first active sphere must hit an intersecting player body even when the feet point is outside");
                    helper.assertTrue(outsideSurvived, "A player whose entire body is outside the active sphere must survive");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_accepts_unloaded_target_chunk")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void acceptsUnloadedTargetChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StellarErasureDeviceSavedData weapons = StellarErasureDeviceSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-unloaded-target-owner");
        DataEnergisticsConfiguration.StellarErasureDeviceSchema settings = DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);

        BlockPos absoluteTarget = findUnloadedTarget(level, helper.absolutePos(new BlockPos(25, 20, 25)));
        ChunkPos targetChunk = new ChunkPos(absoluteTarget);
        AtomicReference<UUID> attackId = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId(), level.dimension().location()),
                        "The unloaded-target test must use a real powered endpoint"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            level.getChunkSource().getChunkNow(targetChunk.x, targetChunk.z) == null,
                            "The future-generation target must begin outside the loaded test area");
                    insertStellarFlux(helper, requiredStellarFlux(settings, cost));
                    UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
                    primeReserve(weapons, server, weaponId, settings, cost);
                    OrbitalAttackRecord warning = attacks.tryConfirmDigitalAnnihilation(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            absoluteTarget)
                            .orElseThrow(() -> new IllegalStateException("An unloaded digital target was rejected"));
                    attackId.set(warning.attackId());
                    helper.assertValueEqual(
                            warning.phase(),
                            OrbitalAttackPhase.RESERVED_WARNING,
                            "An unloaded target must enter the normal refundable warning phase");
                })
                .thenIdle(settings.attackWarningTicks)
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            delivery.phase(),
                            OrbitalAttackPhase.DELIVERY,
                            "An unloaded target must advance into payload delivery");
                    helper.assertTrue(
                            delivery.payloadEntityId() != null && level.getEntity(delivery.payloadEntityId()) instanceof OrbitalAnnihilatorProjectileEntity,
                            "The payload ticket must materialize the orbital projectile in the previously unloaded chunk");
                })
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertTrue(
                            attacks.adminAbort(server, attackId.get()),
                            "The unloaded-target delivery must be abortable after its ticket is verified");
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) == null,
                            "Aborting the unloaded-target delivery must discard its projectile");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_control_emergency_abort_discards_committed_payload_without_refund")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void emergencyAbortDiscardsCommittedPayloadWithoutRefund(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StellarErasureDeviceSavedData weapons = StellarErasureDeviceSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-emergency-abort-owner");
        DataEnergisticsConfiguration.StellarErasureDeviceSchema settings = DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);
        level.getChunk(absoluteTarget.getX() >> 4, absoluteTarget.getZ() >> 4);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> attackId = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The emergency-abort action must use a real powered endpoint"))
                .thenExecute(() -> {
                    insertStellarFlux(helper, requiredStellarFlux(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    owner.setPos(
                            absoluteTarget.getX() + 0.5D,
                            absoluteTarget.getY() + 3.0D,
                            absoluteTarget.getZ() + 0.5D);
                    owner.setXRot(90.0F);
                    owner.setYRot(0.0F);
                    OrbitalAttackRecord warning = attacks.tryConfirmDigitalAnnihilation(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            absoluteTarget)
                            .orElseThrow(() -> new IllegalStateException("A funded digital payload was rejected"));
                    attackId.set(warning.attackId());
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The charging source must be removed before the no-refund assertion");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CONTROL_CONSOLE), false),
                            "The endpoint must be removed after confirmation without cancelling the payload");
                })
                .thenIdle(settings.attackWarningTicks)
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            delivery.phase(),
                            OrbitalAttackPhase.DELIVERY,
                            "The warning must commit before emergency abort is available");
                    helper.assertTrue(
                            delivery.payloadEntityId() != null && level.getEntity(delivery.payloadEntityId()) instanceof OrbitalAnnihilatorProjectileEntity,
                            "The committed digital attack must expose its live projectile to the abort action");
                })
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    UUID payloadId = delivery.payloadEntityId();
                    OrbitalEnergyReserve reserveBeforeAbort = weapons.find(weaponId).orElseThrow().reserve();
                    helper.assertTrue(
                            OrbitalControlActionDispatcher.cancelOrAbortSelectedMode(
                                    owner,
                                    OrbitalAttackMode.DIGITAL_ANNIHILATION),
                            "The selected digital task action must route a committed attack to emergency abort");
                    OrbitalAttackRecord aborted = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            aborted.phase(),
                            OrbitalAttackPhase.ABORTED,
                            "Emergency abort must persist the ABORTED phase");
                    helper.assertTrue(
                            level.getEntity(payloadId) == null,
                            "Emergency abort must discard the in-flight orbital payload");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "Aborting before payload arrival must leave the target unchanged");
                    OrbitalEnergyReserve after = weapons.find(weaponId).orElseThrow().reserve();
                    helper.assertValueEqual(
                            after,
                            reserveBeforeAbort,
                            "Emergency abort must not refund either already committed escrow resource");
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertValueEqual(
                        attacks.find(attackId.get()).orElseThrow().phase(),
                        OrbitalAttackPhase.COOLDOWN,
                        "An aborted attack must advance into cooldown on the next scheduler tick"))
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_recovers_after_world_border_fault")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_boundary_recovery", timeoutTicks = 1_000)
    public static void recoversAfterWorldBorderFault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StellarErasureDeviceSavedData weapons = StellarErasureDeviceSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-border-recovery-owner");
        DataEnergisticsConfiguration.StellarErasureDeviceSchema settings = DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        level.getChunkAt(absoluteTarget);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> attackId = new AtomicReference<>();
        AtomicReference<UUID> firstPayloadId = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The boundary-recovery attack must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertStellarFlux(helper, requiredStellarFlux(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    int originalWarningTicks = settings.attackWarningTicks;
                    try {
                        settings.attackWarningTicks = 1;
                        OrbitalAttackRecord warning = attacks.tryConfirmDigitalAnnihilation(
                                server,
                                owner.getUUID(),
                                weaponId,
                                level.dimension().location(),
                                absoluteTarget)
                                .orElseThrow(() -> new IllegalStateException("The funded boundary-recovery attack was rejected"));
                        attackId.set(warning.attackId());
                    } finally {
                        settings.attackWarningTicks = originalWarningTicks;
                    }
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    UUID payloadId = delivery.payloadEntityId();
                    helper.assertTrue(
                            payloadId != null && level.getEntity(payloadId) instanceof OrbitalAnnihilatorProjectileEntity,
                            "The committed boundary-recovery attack must create its real orbital payload");
                    firstPayloadId.set(payloadId);
                })
                .thenExecute(() -> {
                    WorldBorder border = level.getWorldBorder();
                    double originalCenterX = border.getCenterX();
                    double originalCenterZ = border.getCenterZ();
                    double originalSize = border.getSize();
                    try {
                        border.setCenter(absoluteTarget.getX() + 1_024.0D, absoluteTarget.getZ() + 1_024.0D);
                        border.setSize(16.0D);
                        attacks.tick(server);
                    } finally {
                        border.setCenter(originalCenterX, originalCenterZ);
                        border.setSize(originalSize);
                    }
                    helper.assertTrue(
                            level.getEntity(firstPayloadId.get()) == null,
                            "A boundary-faulted attack must discard its in-flight payload");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "A boundary fault must not mutate the target before administrator recovery");
                    helper.assertTrue(
                            attacks.retryFaulted(server, attackId.get()),
                            "An administrator retry must resume the faulted attack from its persisted task");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord retriedDelivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            retriedDelivery.phase(),
                            OrbitalAttackPhase.DELIVERY,
                            "The retried attack must resume payload delivery after the border recovers");
                    helper.assertTrue(
                            retriedDelivery.payloadEntityId() != null && !retriedDelivery.payloadEntityId().equals(firstPayloadId.get()) && level.getEntity(retriedDelivery.payloadEntityId()) instanceof OrbitalAnnihilatorProjectileEntity,
                            "A retry must replace the discarded payload with a new tracked projectile");
                })
                .thenExecute(() -> {
                    OrbitalAttackRecord retriedDelivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertTrue(
                            attacks.adminAbort(server, attackId.get()),
                            "The recovered delivery must be abortable before it begins terrain work");
                    helper.assertTrue(
                            level.getEntity(retriedDelivery.payloadEntityId()) == null,
                            "Aborting the recovered delivery must remove its replacement projectile");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "Recovery verification must leave the target unchanged");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_rejects_orphan_payload_before_world_mutation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_orphan_payload", timeoutTicks = 400)
    public static void rejectsOrphanPayloadBeforeWorldMutation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);
        level.getChunkAt(absoluteTarget);
        AABB targetArea = new AABB(absoluteTarget).inflate(2.0D);
        OrbitalAnnihilatorProjectileEntity projectile = new OrbitalAnnihilatorProjectileEntity(
                level,
                UUID.randomUUID(),
                absoluteTarget,
                Set.of(),
                1,
                1,
                0.0D);
        if (!level.addFreshEntity(projectile)) {
            throw new IllegalStateException("The orphan-payload test could not add its real orbital projectile");
        }

        helper.startSequence()
                .thenIdle(OrbitalAnnihilatorProjectileEntity.FLIGHT_TICKS + 1)
                .thenExecute(() -> {
                    helper.assertTrue(
                            level.getEntitiesOfClass(DataNukePrimedEntity.class, targetArea).isEmpty(),
                            "An untracked orbital projectile must not leave a materialized fuse in the world");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "Rejecting an untracked payload must leave its target unchanged");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_rejects_duplicate_projectile_without_blocking_registered_payload")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_duplicate_payload", timeoutTicks = 600)
    public static void rejectsDuplicateProjectileWithoutBlockingRegisteredPayload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        StellarErasureDeviceSavedData weapons = StellarErasureDeviceSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-duplicate-payload-owner");
        DataEnergisticsConfiguration.StellarErasureDeviceSchema settings = DataEnergisticsConfiguration.INSTANCE.stellarErasureDevice;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(DUPLICATE_TARGET, Blocks.STONE);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);
        BlockPos absoluteDuplicateTarget = helper.absolutePos(DUPLICATE_TARGET);
        level.getChunkAt(absoluteTarget);
        level.getChunkAt(absoluteDuplicateTarget);
        AABB duplicateTargetArea = new AABB(absoluteDuplicateTarget).inflate(2.0D);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> attackId = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The duplicate-payload attack must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertStellarFlux(helper, requiredStellarFlux(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    int originalWarningTicks = settings.attackWarningTicks;
                    try {
                        settings.attackWarningTicks = 1;
                        OrbitalAttackRecord warning = attacks.tryConfirmDigitalAnnihilation(
                                server,
                                owner.getUUID(),
                                weaponId,
                                level.dimension().location(),
                                absoluteTarget)
                                .orElseThrow(() -> new IllegalStateException("The funded duplicate-payload attack was rejected"));
                        attackId.set(warning.attackId());
                    } finally {
                        settings.attackWarningTicks = originalWarningTicks;
                    }
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    UUID payloadId = delivery.payloadEntityId();
                    helper.assertTrue(
                            payloadId != null && level.getEntity(payloadId) instanceof OrbitalAnnihilatorProjectileEntity,
                            "The authorized attack must register its real descending projectile before duplicate injection");
                })
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    if (!(delivery.geometry() instanceof OrbitalAttackGeometry.DigitalAnnihilation(int workIntervalTicks, int maxRadius, double centerEntityConsumeRadius))) {
                        throw new IllegalStateException("The duplicate-payload attack lost its frozen digital geometry");
                    }
                    OrbitalAnnihilatorProjectileEntity duplicate = new OrbitalAnnihilatorProjectileEntity(
                            level,
                            delivery.attackId(),
                            absoluteDuplicateTarget,
                            delivery.damageExemptions(),
                            workIntervalTicks,
                            maxRadius,
                            centerEntityConsumeRadius);
                    if (!level.addFreshEntity(duplicate)) {
                        throw new IllegalStateException("The duplicate-payload test could not add its competing projectile");
                    }
                })
                .thenIdle(OrbitalAnnihilatorProjectileEntity.FLIGHT_TICKS + 1)
                .thenExecute(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) instanceof DataNukePrimedEntity,
                            "Only the registered payload may materialize the tracked fuse");
                    helper.assertTrue(
                            level.getBlockState(absoluteDuplicateTarget).is(Blocks.STONE),
                            "A projectile with the wrong entity identity must not redirect the authorized world effect");
                    helper.assertTrue(
                            level.getEntitiesOfClass(DataNukePrimedEntity.class, duplicateTargetArea).isEmpty(),
                            "A duplicate projectile must not materialize an untracked fuse in the world");
                    helper.assertTrue(
                            attacks.adminAbort(server, attackId.get()),
                            "The registered fuse must be abortable before it starts terrain work");
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) == null,
                            "Aborting the registered fuse must remove the final explosive payload");
                    helper.assertTrue(
                            level.getBlockState(absoluteTarget).is(Blocks.STONE),
                            "Duplicate rejection verification must leave the authorized target unchanged");
                })
                .thenSucceed();
    }

    private static void installInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The digital test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_CELL_INFINITY.toStack());
    }

    private static BlockPos findUnloadedTarget(ServerLevel level, BlockPos origin) {
        for (int distance = 64; distance <= 2_048; distance += 16) {
            for (BlockPos candidate : new BlockPos[] {
                    origin.offset(distance, 0, 0),
                    origin.offset(-distance, 0, 0),
                    origin.offset(0, 0, distance),
                    origin.offset(0, 0, -distance),
                    origin.offset(distance, 0, distance),
                    origin.offset(-distance, 0, -distance),
                    origin.offset(distance, 0, -distance),
                    origin.offset(-distance, 0, distance) }) {
                ChunkPos chunk = new ChunkPos(candidate);
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("The game test could not find an unloaded target chunk");
    }

    private static void insertStellarFlux(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(CONTROL_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The digital test console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The digital test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                StellarFluxKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The digital test could not seed Stellar Flux storage");
        }
    }

    private static void primeReserve(
                                     StellarErasureDeviceSavedData weapons,
                                     MinecraftServer server,
                                     UUID weaponId,
                                     DataEnergisticsConfiguration.StellarErasureDeviceSchema settings,
                                     OrbitalAttackCost cost) {
        long requiredStellarFlux = Math.max(
                cost.stellarFlux(),
                deploymentTarget(settings.stellarFluxCapacity, settings.deploymentThreshold));
        long requiredAeEnergy = Math.max(
                cost.aeEnergy(),
                deploymentTarget(settings.aeEnergyCapacity, settings.deploymentThreshold));
        for (int attempts = 0; attempts < 20_000; attempts++) {
            var weapon = weapons.find(weaponId).orElseThrow();
            if (weapon.allowsNewAttacks() && weapon.reserve().canAfford(requiredStellarFlux, requiredAeEnergy)) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not fund one digital payload");
    }

    private static long requiredStellarFlux(
                                            DataEnergisticsConfiguration.StellarErasureDeviceSchema settings,
                                            OrbitalAttackCost cost) {
        return Math.max(
                Math.multiplyExact(cost.stellarFlux(), 2L),
                deploymentTarget(settings.stellarFluxCapacity, settings.deploymentThreshold));
    }

    private static long deploymentTarget(long capacity, double threshold) {
        return Math.max(1L, (long) Math.ceil(capacity * threshold));
    }

    private static void placeBlock(GameTestHelper helper, BlockPos relativePos, Block block, ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place digital test block at " + absolutePos);
        }
        state.getBlock().setPlacedBy(level, absolutePos, state, placer, ItemStack.EMPTY);
    }

    private static ServerPlayer createPlayer(ServerLevel level, String name) {
        return new TestServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static final class TestServerPlayer extends ServerPlayer {

        private TestServerPlayer(
                                 MinecraftServer server,
                                 ServerLevel level,
                                 GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public void displayClientMessage(Component chatComponent, boolean actionBar) {}
    }
}
