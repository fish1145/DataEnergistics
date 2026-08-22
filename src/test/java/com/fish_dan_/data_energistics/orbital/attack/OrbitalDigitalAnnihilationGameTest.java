package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalDigitalAnnihilationGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(4, 2, 2);
    private static final BlockPos TARGET = new BlockPos(25, 20, 25);

    private OrbitalDigitalAnnihilationGameTest() {}

    @BeforeBatch(batch = "orbital_boundary_recovery")
    public static void clearPriorBatchAttacks(ServerLevel level) {
        MinecraftServer server = level.getServer();
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        for (ServerLevel serverLevel : server.getAllLevels()) {
            for (OrbitalAttackRecord attack : attacks.publicForDimension(serverLevel.dimension().location())) {
                if (attack.phase() == OrbitalAttackPhase.RESERVED_WARNING) {
                    weapons.find(attack.weaponId()).ifPresent(weapon -> attacks.cancelWarning(
                            server,
                            weapon.ownerId(),
                            attack.attackId()));
                } else {
                    attacks.adminAbort(server, attack.attackId());
                }
            }
        }
    }

    @TestHolder("orbital_digital_annihilation_payload_descends_and_materializes_fuse")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void payloadDescendsAndMaterializesFuse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-annihilation-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
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
        AtomicReference<Zombie> victim = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The digital payload must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
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
                            before.celestialEnergy() - after.celestialEnergy(),
                            cost.celestialEnergy(),
                            "Digital confirmation must escrow its configured Celestial Energy cost");
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
                .thenIdle(Math.max(1, settings.attackWarningTicks() / 2))
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
                    Zombie spawned = helper.spawn(EntityType.ZOMBIE, TARGET.above());
                    spawned.setNoAi(true);
                    victim.set(spawned);
                })
                .thenIdle(90)
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord delivery = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            delivery.phase(),
                            OrbitalAttackPhase.DELIVERY,
                            "The attack must remain in delivery while its spawned annihilator is active");
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) != null,
                            "The spawned annihilator must remain tracked after the fuse activates");
                    helper.assertTrue(
                            level.getEntity(delivery.payloadEntityId()) instanceof DataNukePrimedEntity nuke && nuke.isActive(),
                            "The materialized fuse must transition into the existing active annihilation behavior");
                    helper.assertFalse(victim.get().isAlive(), "The active annihilator must consume a non-exempt entity");
                    helper.assertValueEqual(
                            delivery.celestialEscrow(),
                            cost.celestialEnergy(),
                            "The running payload must retain its single prepaid Celestial Energy escrow");
                    helper.assertValueEqual(
                            delivery.aeEscrow(),
                            cost.aeEnergy(),
                            "The running payload must retain its single prepaid AE energy escrow");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_accepts_unloaded_target_chunk")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void acceptsUnloadedTargetChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-unloaded-target-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
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
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
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
                .thenIdle(settings.attackWarningTicks())
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
                .thenSucceed();
    }

    @TestHolder("orbital_control_emergency_abort_discards_committed_payload_without_refund")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void emergencyAbortDiscardsCommittedPayloadWithoutRefund(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-emergency-abort-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
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
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
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
                .thenIdle(settings.attackWarningTicks())
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
                            OrbitalControlActionDispatcher.cancelOrAbortFirst(owner),
                            "The LDLib2 stop action must route a committed attack to emergency abort");
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

    @TestHolder("orbital_digital_annihilation_keeps_confirmed_world_effect_after_config_reload")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void keepsConfirmedWorldEffectAfterConfigReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-config-snapshot-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);
        BlockPos relativeOutsideSnapshot = TARGET.offset(3, 0, 0);
        BlockPos absoluteTarget = helper.absolutePos(TARGET);
        BlockPos absoluteOutsideSnapshot = helper.absolutePos(relativeOutsideSnapshot);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(relativeOutsideSnapshot, Blocks.STONE);
        level.getChunkAt(absoluteTarget);
        level.getChunkAt(absoluteOutsideSnapshot);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The configuration-snapshot attack must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    var liveSettings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
                    int originalWorkInterval = liveSettings.workIntervalTicks;
                    int originalMaxRadius = liveSettings.maxRadius;
                    double originalCenterRadius = liveSettings.centerEntityConsumeRadius;
                    try {
                        liveSettings.workIntervalTicks = 1;
                        liveSettings.maxRadius = 1;
                        liveSettings.centerEntityConsumeRadius = 0.0D;
                        attacks.tryConfirmDigitalAnnihilation(
                                server,
                                owner.getUUID(),
                                weaponId,
                                level.dimension().location(),
                                absoluteTarget)
                                .orElseThrow(() -> new IllegalStateException("The funded configuration-snapshot attack was rejected"));
                    } finally {
                        liveSettings.workIntervalTicks = originalWorkInterval;
                        liveSettings.maxRadius = originalMaxRadius;
                        liveSettings.centerEntityConsumeRadius = originalCenterRadius;
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(absoluteTarget).isAir(),
                        "The confirmed orbital payload must traverse warning, delivery, fuse and shared terrain work"))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(
                                level.getBlockState(absoluteOutsideSnapshot).is(Blocks.STONE),
                                "Reloaded settings must not expand the confirmed orbital world effect");
                    } finally {
                        OrbitalControlActionDispatcher.cancelOrAbortFirst(owner);
                    }
                })
                .thenSucceed();
    }

    @TestHolder("orbital_digital_annihilation_recovers_after_world_border_fault")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_boundary_recovery", timeoutTicks = 1_000)
    public static void recoversAfterWorldBorderFault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "digital-border-recovery-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
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
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    var liveNukeSettings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
                    int originalWarningTicks = settings.attackWarningTicks;
                    int originalWorkInterval = liveNukeSettings.workIntervalTicks;
                    int originalMaxRadius = liveNukeSettings.maxRadius;
                    double originalCenterRadius = liveNukeSettings.centerEntityConsumeRadius;
                    try {
                        settings.attackWarningTicks = 1;
                        liveNukeSettings.workIntervalTicks = 1;
                        liveNukeSettings.maxRadius = 1;
                        liveNukeSettings.centerEntityConsumeRadius = 0.0D;
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
                        liveNukeSettings.workIntervalTicks = originalWorkInterval;
                        liveNukeSettings.maxRadius = originalMaxRadius;
                        liveNukeSettings.centerEntityConsumeRadius = originalCenterRadius;
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
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(absoluteTarget).isAir(),
                        "The retried attack must complete real delivery, fuse and terrain work after the border recovers"))
                .thenExecute(() -> OrbitalControlActionDispatcher.cancelOrAbortFirst(owner))
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

    private static void insertCelestialEnergy(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(CONTROL_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The digital test console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The digital test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                CelestialEnergyKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The digital test could not seed Celestial Energy storage");
        }
    }

    private static void primeReserve(
                                     OrbitalWeaponSavedData weapons,
                                     MinecraftServer server,
                                     UUID weaponId,
                                     DataEnergisticsConfiguration.OrbitalWeaponSchema settings,
                                     OrbitalAttackCost cost) {
        long requiredCelestialEnergy = Math.max(
                cost.celestialEnergy(),
                deploymentTarget(settings.celestialEnergyCapacity(), settings.deploymentThreshold));
        long requiredAeEnergy = Math.max(
                cost.aeEnergy(),
                deploymentTarget(settings.aeEnergyCapacity(), settings.deploymentThreshold));
        for (int attempts = 0; attempts < 20_000; attempts++) {
            var weapon = weapons.find(weaponId).orElseThrow();
            if (weapon.allowsNewAttacks() && weapon.reserve().canAfford(requiredCelestialEnergy, requiredAeEnergy)) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not fund one digital payload");
    }

    private static long requiredCelestialEnergy(
                                                DataEnergisticsConfiguration.OrbitalWeaponSchema settings,
                                                OrbitalAttackCost cost) {
        return Math.max(
                Math.multiplyExact(cost.celestialEnergy(), 2L),
                deploymentTarget(settings.celestialEnergyCapacity(), settings.deploymentThreshold));
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
