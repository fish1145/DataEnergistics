package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalWeaponLifecycleGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(1, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(3, 2, 2);

    private OrbitalWeaponLifecycleGameTest() {}

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
