package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalWeaponLifecycleGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(1, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(3, 2, 2);
    private static final BlockPos FIRST_BEACON = new BlockPos(4, 2, 2);
    private static final BlockPos SECOND_BEACON = new BlockPos(5, 2, 2);
    private static final BlockPos EARLY_TARGET = new BlockPos(25, 20, 25);
    private static final BlockPos FINAL_TARGET = new BlockPos(35, 20, 25);
    private static final String REDEPLOYMENT_BATCH = "orbital_redeployment_reserve_grace";
    private static LifecycleConfigurationSnapshot originalConfiguration = LifecycleConfigurationSnapshot.capture(
            DataEnergisticsConfiguration.INSTANCE.orbitalWeapon);

    private OrbitalWeaponLifecycleGameTest() {}

    @BeforeBatch(batch = REDEPLOYMENT_BATCH)
    public static void configureRedeploymentScenario(ServerLevel level) {
        requireServerThread(level);
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        originalConfiguration = LifecycleConfigurationSnapshot.capture(settings);
        LifecycleConfigurationSnapshot.testConfiguration().applyTo(settings);
    }

    @AfterBatch(batch = REDEPLOYMENT_BATCH)
    public static void restoreRedeploymentConfiguration(ServerLevel level) {
        requireServerThread(level);
        originalConfiguration.applyTo(DataEnergisticsConfiguration.INSTANCE.orbitalWeapon);
    }

    @TestHolder("orbital_weapon_lifecycle_deploys_drains_sleeps_and_redeploys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void deploysDrainsSleepsAndRedeploys(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        ServerPlayer owner = createPlayer(level, "orbital-lifecycle-owner");

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        long deploymentCelestialEnergy = deploymentTarget(
                settings.celestialEnergyCapacity,
                settings.deploymentThreshold);

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The lifecycle test must use a real powered AE endpoint"))
                .thenExecute(() -> {
                    OrbitalWeaponRecord dormant = weapons.find(weaponId).orElseThrow();
                    helper.assertValueEqual(
                            dormant.lifecycle().state(),
                            OrbitalWeaponLifecycleState.DORMANT,
                            "A newly provisioned orbital weapon must begin dormant");

                    insertCelestialEnergy(helper, deploymentCelestialEnergy);
                    weapons.chargeReserves(server);
                    OrbitalWeaponRecord partiallyCharged = weapons.find(weaponId).orElseThrow();
                    helper.assertTrue(
                            partiallyCharged.reserve().celestialEnergy() > 0L && partiallyCharged.reserve().aeEnergy() > 0L,
                            "The real endpoint must transfer both independent reserves");
                    helper.assertFalse(
                            weapons.tryDebitReserve(server, weaponId, owner.getUUID(), 1L, 1L),
                            "A partially funded dormant weapon must reject a new attack debit");
                    helper.assertValueEqual(
                            weapons.find(weaponId).orElseThrow().reserve(),
                            partiallyCharged.reserve(),
                            "A dormant rejection must not mutate either reserve");

                    chargeUntilDeployed(weapons, server, weaponId, settings);
                    OrbitalWeaponRecord deployed = weapons.find(weaponId).orElseThrow();
                    helper.assertValueEqual(
                            deployed.lifecycle().state(),
                            OrbitalWeaponLifecycleState.DEPLOYED,
                            "Reaching both configured thresholds must deploy the orbital weapon");
                    helper.assertTrue(
                            deployed.reserve().meetsDeploymentThreshold(settings),
                            "Deployment must be backed by both persisted reserves rather than a transient flag");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The lifecycle test must be able to remove AE power before observing upkeep");
                })
                .thenIdle(5)
                .thenWaitUntil(() -> helper.assertFalse(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "Removing AE power must make the endpoint unavailable for reserve charging"))
                .thenExecute(() -> {
                    OrbitalEnergyReserve beforeMaintenance = weapons.find(weaponId).orElseThrow().reserve();
                    weapons.chargeReserves(server);
                    OrbitalWeaponRecord maintained = weapons.find(weaponId).orElseThrow();
                    helper.assertValueEqual(
                            beforeMaintenance.celestialEnergy() - maintained.reserve().celestialEnergy(),
                            Math.min(beforeMaintenance.celestialEnergy(), settings.celestialEnergyUpkeepPerTick),
                            "A deployed tick without input must consume configured Celestial Energy upkeep");
                    helper.assertValueEqual(
                            beforeMaintenance.aeEnergy() - maintained.reserve().aeEnergy(),
                            Math.min(beforeMaintenance.aeEnergy(), settings.aeEnergyUpkeepPerTick),
                            "A deployed tick without input must consume configured AE upkeep");
                    helper.assertValueEqual(
                            maintained.lifecycle().state(),
                            OrbitalWeaponLifecycleState.DEPLOYED,
                            "Losing an endpoint must not immediately deconstruct a funded projection");

                    helper.assertTrue(
                            weapons.tryDebitReserve(
                                    server,
                                    weaponId,
                                    owner.getUUID(),
                                    maintained.reserve().celestialEnergy(),
                                    maintained.reserve().aeEnergy()),
                            "The deployed reserve transaction must be able to consume the final stored units");
                    OrbitalWeaponRecord grace = weapons.find(weaponId).orElseThrow();
                    OrbitalWeaponLifecycleState expectedGraceState = settings.reserveGraceTicks == 0 ? OrbitalWeaponLifecycleState.DORMANT : OrbitalWeaponLifecycleState.RESERVE_GRACE;
                    helper.assertValueEqual(
                            grace.lifecycle().state(),
                            expectedGraceState,
                            "Exhausting either reserve must immediately disable the deployed state");
                    helper.assertFalse(
                            weapons.tryDebitReserve(server, weaponId, owner.getUUID(), 1L, 1L),
                            "Reserve grace must reject every new attack debit");

                    for (int tick = 0; tick < settings.reserveGraceTicks; tick++) {
                        weapons.chargeReserves(server);
                    }
                    helper.assertValueEqual(
                            weapons.find(weaponId).orElseThrow().lifecycle().state(),
                            OrbitalWeaponLifecycleState.DORMANT,
                            "An unfunded projection must return to dormancy after its configured grace period");
                    placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
                })
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "Restoring AE power must make the bound endpoint operational again"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, deploymentCelestialEnergy);
                    chargeUntilDeployed(weapons, server, weaponId, settings);
                    helper.assertValueEqual(
                            weapons.find(weaponId).orElseThrow().lifecycle().state(),
                            OrbitalWeaponLifecycleState.DEPLOYED,
                            "A dormant weapon must redeploy after both reserves are replenished to threshold");
                })
                .thenSucceed();
    }

    @TestHolder("orbital_weapon_redeployment_keeps_maintenance_and_reserve_grace")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = REDEPLOYMENT_BATCH, timeoutTicks = 500)
    public static void redeploymentKeepsMaintenanceAndReserveGrace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "orbital-redeployment-owner");
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        placeBlock(helper, FIRST_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        placeBlock(helper, SECOND_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        installInfiniteCell(helper);
        helper.setBlock(EARLY_TARGET, Blocks.STONE);
        helper.setBlock(FINAL_TARGET, Blocks.STONE);
        BlockPos absoluteEarlyTarget = helper.absolutePos(EARLY_TARGET);
        BlockPos absoluteFinalTarget = helper.absolutePos(FINAL_TARGET);
        level.getChunkAt(absoluteEarlyTarget);
        level.getChunkAt(absoluteFinalTarget);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        OrbitalEndpointLocation firstBeacon = new OrbitalEndpointLocation(
                level.dimension().location(),
                helper.absolutePos(FIRST_BEACON));
        OrbitalEndpointLocation secondBeacon = new OrbitalEndpointLocation(
                level.dimension().location(),
                helper.absolutePos(SECOND_BEACON));

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The redeployment scenario must use real powered orbital endpoints"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, 1_000L);
                    chargeUntilDeployed(weapons, server, weaponId, settings);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    OrbitalEndpointLocation currentAnchor = weapons.find(weaponId).orElseThrow().primaryAnchor();
                    OrbitalEndpointLocation replacement = firstBeacon.equals(currentAnchor) ? secondBeacon : firstBeacon;
                    if (!weapons.selectPrimaryAnchor(
                            server,
                            owner.getUUID(),
                            weaponId,
                            replacement)) {
                        throw new IllegalStateException("The second real uplink beacon could not become primary");
                    }
                    removeInfiniteCell(helper);
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    installInfiniteCell(helper);
                    insertCelestialEnergy(helper, 1_000L);
                })
                .thenIdle(9)
                .thenExecute(() -> attacks.tryConfirmKinetic(
                        server,
                        owner.getUUID(),
                        weaponId,
                        level.dimension().location(),
                        absoluteEarlyTarget))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(
                        level.getBlockState(absoluteEarlyTarget).is(Blocks.STONE),
                        "Reserve recovery must continue the paused rebuild instead of firing immediately"))
                .thenIdle(3)
                .thenExecute(() -> attacks.tryConfirmKinetic(
                        server,
                        owner.getUUID(),
                        weaponId,
                        level.dimension().location(),
                        absoluteFinalTarget)
                        .orElseThrow(() -> new IllegalStateException(
                                "The rebuilt weapon could not launch its real kinetic strike")))
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(absoluteFinalTarget).isAir(),
                        "Completing the resumed rebuild must enable a real kinetic world effect"))
                .thenIdle(3)
                .thenSucceed();
    }

    private static void chargeUntilDeployed(
                                            OrbitalWeaponSavedData weapons,
                                            MinecraftServer server,
                                            UUID weaponId,
                                            DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        long requiredCalls = Math.max(
                ceilingDivision(
                        deploymentTarget(settings.celestialEnergyCapacity, settings.deploymentThreshold),
                        settings.celestialEnergyChargePerTick),
                ceilingDivision(
                        deploymentTarget(settings.aeEnergyCapacity, settings.deploymentThreshold),
                        settings.aeEnergyChargePerTick));
        if (requiredCalls > Integer.MAX_VALUE - 2L) {
            throw new IllegalStateException("The configured deployment threshold is too slow for this GameTest");
        }
        for (int attempt = 0; attempt < (int) requiredCalls + 2; attempt++) {
            if (weapons.find(weaponId).orElseThrow().allowsNewAttacks()) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not deploy the orbital weapon at both thresholds");
    }

    private static long deploymentTarget(long capacity, double threshold) {
        return Math.max(1L, (long) Math.ceil(capacity * threshold));
    }

    private static long ceilingDivision(long value, long divisor) {
        return ((value - 1L) / divisor) + 1L;
    }

    private static void installInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The lifecycle test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_CELL_INFINITY.toStack());
    }

    private static void removeInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The lifecycle test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
    }

    private static void insertCelestialEnergy(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(CONTROL_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The lifecycle test console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The lifecycle test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                CelestialEnergyKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The lifecycle test could not seed Celestial Energy storage");
        }
    }

    private static void placeBlock(GameTestHelper helper, BlockPos relativePos, Block block, ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place lifecycle test block at " + absolutePos);
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

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Lifecycle GameTest batch hooks must run on the server thread");
        }
    }

    private record LifecycleConfigurationSnapshot(
                                                  long celestialCapacity,
                                                  long aeCapacity,
                                                  long celestialUpkeep,
                                                  long aeUpkeep,
                                                  long celestialCharge,
                                                  long aeCharge,
                                                  int reserveGraceTicks,
                                                  double deploymentThreshold,
                                                  int redeploymentTicks,
                                                  int warningTicks,
                                                  long kineticCelestialCost,
                                                  long kineticAeCost,
                                                  int kineticCooldownTicks,
                                                  int columnRadius,
                                                  int columnDepth,
                                                  int craterRadius,
                                                  int craterDepth,
                                                  int shockwaveRadius,
                                                  long entityDamage,
                                                  double knockbackStrength) {

        private static LifecycleConfigurationSnapshot capture(
                                                              DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
            return new LifecycleConfigurationSnapshot(
                    settings.celestialEnergyCapacity,
                    settings.aeEnergyCapacity,
                    settings.celestialEnergyUpkeepPerTick,
                    settings.aeEnergyUpkeepPerTick,
                    settings.celestialEnergyChargePerTick,
                    settings.aeEnergyChargePerTick,
                    settings.reserveGraceTicks,
                    settings.deploymentThreshold,
                    settings.redeploymentTicks,
                    settings.attackWarningTicks,
                    settings.kineticCelestialEnergyCost,
                    settings.kineticAeEnergyCost,
                    settings.kineticCooldownTicks,
                    settings.kineticColumnRadius,
                    settings.kineticColumnDepth,
                    settings.kineticCraterRadius,
                    settings.kineticCraterDepth,
                    settings.kineticShockwaveRadius,
                    settings.kineticEntityDamage,
                    settings.kineticKnockbackStrength);
        }

        private static LifecycleConfigurationSnapshot testConfiguration() {
            return new LifecycleConfigurationSnapshot(
                    100L,
                    100L,
                    10L,
                    10L,
                    100L,
                    100L,
                    30,
                    0.10D,
                    20,
                    1,
                    1L,
                    1L,
                    1,
                    1,
                    1,
                    1,
                    1,
                    2,
                    500L,
                    1.0D);
        }

        private void applyTo(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
            settings.celestialEnergyCapacity = this.celestialCapacity;
            settings.aeEnergyCapacity = this.aeCapacity;
            settings.celestialEnergyUpkeepPerTick = this.celestialUpkeep;
            settings.aeEnergyUpkeepPerTick = this.aeUpkeep;
            settings.celestialEnergyChargePerTick = this.celestialCharge;
            settings.aeEnergyChargePerTick = this.aeCharge;
            settings.reserveGraceTicks = this.reserveGraceTicks;
            settings.deploymentThreshold = this.deploymentThreshold;
            settings.redeploymentTicks = this.redeploymentTicks;
            settings.attackWarningTicks = this.warningTicks;
            settings.kineticCelestialEnergyCost = this.kineticCelestialCost;
            settings.kineticAeEnergyCost = this.kineticAeCost;
            settings.kineticCooldownTicks = this.kineticCooldownTicks;
            settings.kineticColumnRadius = this.columnRadius;
            settings.kineticColumnDepth = this.columnDepth;
            settings.kineticCraterRadius = this.craterRadius;
            settings.kineticCraterDepth = this.craterDepth;
            settings.kineticShockwaveRadius = this.shockwaveRadius;
            settings.kineticEntityDamage = this.entityDamage;
            settings.kineticKnockbackStrength = this.knockbackStrength;
        }
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
