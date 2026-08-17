package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalKineticAttackGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(4, 2, 2);
    private static final BlockPos TARGET = new BlockPos(25, 20, 25);
    private static final BlockPos VICTIM = TARGET.offset(10, 0, 0);

    private OrbitalKineticAttackGameTest() {}

    @TestHolder("orbital_kinetic_attack_refunds_warning_then_commits_world_effect_and_cooldown")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void refundsWarningThenCommitsWorldEffectAndCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "kinetic-owner");
        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        OrbitalAttackCost cost = OrbitalAttackCost.kinetic(settings);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(VICTIM.below(), Blocks.STONE);
        loadTerrainChunks(level, helper.absolutePos(TARGET));

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> firstAttackId = new AtomicReference<>();
        AtomicReference<UUID> secondAttackId = new AtomicReference<>();
        AtomicReference<Zombie> victim = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The kinetic confirmation must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost));
                    primeReserve(weapons, server, weaponId, settings, cost);
                    Zombie spawned = helper.spawn(EntityType.ZOMBIE, VICTIM);
                    spawned.setNoAi(true);
                    victim.set(spawned);
                })
                .thenExecute(() -> {
                    OrbitalEnergyReserve before = weapons.find(weaponId).orElseThrow().reserve();
                    OrbitalAttackRecord warning = attacks.tryConfirmKinetic(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            helper.absolutePos(TARGET))
                            .orElseThrow(() -> new IllegalStateException("A funded kinetic attack was rejected"));
                    firstAttackId.set(warning.attackId());
                    assertDebited(helper, weapons, weaponId, before, cost);
                    OrbitalControlTerminalSnapshot warningSnapshot = OrbitalControlTerminalSnapshot.capture(
                            server,
                            owner.getUUID());
                    helper.assertValueEqual(
                            warningSnapshot.weapons().getFirst().attacks().size(),
                            1,
                            "The LDLib2 control snapshot must expose the active warning for its UUID-visible weapon");
                    helper.assertValueEqual(
                            warningSnapshot.weapons().getFirst().attacks().getFirst().phase(),
                            OrbitalAttackPhase.RESERVED_WARNING,
                            "The control snapshot must expose the server-authoritative warning phase");
                    helper.assertTrue(
                            level.getBlockState(helper.absolutePos(TARGET)).is(Blocks.STONE),
                            "The target block must remain intact throughout the refundable warning");
                    helper.assertTrue(victim.get().isAlive(), "The warning must not damage target entities");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The charging source must be removable to freeze reserve assertions");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CONTROL_CONSOLE), false),
                            "Removing the endpoint must prevent its buffered grid from charging during cancellation");
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    OrbitalEnergyReserve beforeRefund = weapons.find(weaponId).orElseThrow().reserve();
                    helper.assertTrue(
                            attacks.cancelWarning(server, owner.getUUID(), firstAttackId.get()),
                            "The owner must be able to cancel a refundable warning");
                    OrbitalEnergyReserve afterRefund = weapons.find(weaponId).orElseThrow().reserve();
                    helper.assertValueEqual(
                            afterRefund.celestialEnergy() - beforeRefund.celestialEnergy(),
                            cost.celestialEnergy(),
                            "Cancelling the warning must return its complete Celestial Energy escrow");
                    helper.assertValueEqual(
                            afterRefund.aeEnergy() - beforeRefund.aeEnergy(),
                            cost.aeEnergy(),
                            "Cancelling the warning must return its complete AE energy escrow");
                    helper.assertTrue(
                            attacks.find(firstAttackId.get()).isEmpty(),
                            "A cancelled warning must leave no active mode slot");
                    helper.assertTrue(
                            OrbitalControlTerminalSnapshot.capture(server, owner.getUUID())
                                    .weapons()
                                    .getFirst()
                                    .attacks()
                                    .isEmpty(),
                            "Cancelling a warning must remove it from the next LDLib2 control snapshot");
                    helper.assertTrue(
                            level.getBlockState(helper.absolutePos(TARGET)).is(Blocks.STONE),
                            "Cancelling the warning must leave the target world unchanged");
                })
                .thenExecute(() -> {
                    placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
                    placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
                })
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "Restoring AE power must make the endpoint valid for a second confirmation"))
                .thenExecute(() -> {
                    OrbitalEnergyReserve before = weapons.find(weaponId).orElseThrow().reserve();
                    OrbitalAttackRecord warning = attacks.tryConfirmKinetic(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            helper.absolutePos(TARGET))
                            .orElseThrow(() -> new IllegalStateException("The second kinetic warning was rejected"));
                    secondAttackId.set(warning.attackId());
                    assertDebited(helper, weapons, weaponId, before, cost);
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The second warning must run without additional reserve charging");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CONTROL_CONSOLE), false),
                            "The committed attack must no longer depend on a live endpoint");
                })
                .thenIdle(Math.max(1, settings.attackWarningTicks() / 2))
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            attacks.find(secondAttackId.get()).orElseThrow().phase(),
                            OrbitalAttackPhase.RESERVED_WARNING,
                            "The attack must remain refundable halfway through its warning");
                    helper.assertTrue(
                            level.getBlockState(helper.absolutePos(TARGET)).is(Blocks.STONE),
                            "The warning must not begin terrain work early");
                    helper.assertTrue(victim.get().isAlive(), "The warning must not apply impact damage early");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord attack = attacks.find(secondAttackId.get()).orElseThrow();
                    helper.assertFalse(
                            attack.phase() == OrbitalAttackPhase.RESERVED_WARNING,
                            "The warning must eventually commit");
                    helper.assertFalse(victim.get().isAlive(), "The commit tick must apply the kinetic impact damage");
                    helper.assertTrue(
                            level.getBlockState(helper.absolutePos(TARGET)).is(Blocks.STONE),
                            "Impact damage must happen before the bounded terrain cursor reaches the target");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord attack = attacks.find(secondAttackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            attack.phase(),
                            OrbitalAttackPhase.COOLDOWN,
                            "Completing the bounded column and crater work must enter kinetic cooldown");
                    helper.assertTrue(
                            level.getBlockState(helper.absolutePos(TARGET)).isAir(),
                            "The committed kinetic terrain worker must remove the target without drops");
                })
                .thenExecute(() -> {
                    placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
                    placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
                })
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The endpoint must be online when cooldown rejection is checked"))
                .thenExecute(() -> helper.assertTrue(
                        attacks.tryConfirmKinetic(
                                server,
                                owner.getUUID(),
                                weaponId,
                                level.dimension().location(),
                                helper.absolutePos(TARGET))
                                .isEmpty(),
                        "A kinetic mode in cooldown must reject a second funded attack"))
                .thenSucceed();
    }

    private static void installInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The kinetic test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_CELL_INFINITY.toStack());
    }

    private static void insertCelestialEnergy(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(CONTROL_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The kinetic test console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The kinetic test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                CelestialEnergyKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The kinetic test could not seed Celestial Energy storage");
        }
    }

    private static void primeReserve(
                                     OrbitalWeaponSavedData weapons,
                                     MinecraftServer server,
                                     UUID weaponId,
                                     DataEnergisticsSettings.OrbitalWeapon settings,
                                     OrbitalAttackCost cost) {
        long requiredCelestialEnergy = requiredCelestialEnergy(settings, cost);
        long requiredAeEnergy = Math.max(
                Math.multiplyExact(cost.aeEnergy(), 2L),
                deploymentTarget(settings.aeEnergyCapacity(), settings.deploymentThreshold()));
        for (int attempts = 0; attempts < 20_000; attempts++) {
            var weapon = weapons.find(weaponId).orElseThrow();
            if (weapon.allowsNewAttacks() && weapon.reserve().canAfford(requiredCelestialEnergy, requiredAeEnergy)) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not fund two kinetic attacks");
    }

    private static long requiredCelestialEnergy(
                                                DataEnergisticsSettings.OrbitalWeapon settings,
                                                OrbitalAttackCost cost) {
        return Math.max(
                Math.multiplyExact(cost.celestialEnergy(), 2L),
                deploymentTarget(settings.celestialEnergyCapacity(), settings.deploymentThreshold()));
    }

    private static long deploymentTarget(long capacity, double threshold) {
        return Math.max(1L, (long) Math.ceil(capacity * threshold));
    }

    private static void assertDebited(
                                      GameTestHelper helper,
                                      OrbitalWeaponSavedData weapons,
                                      UUID weaponId,
                                      OrbitalEnergyReserve before,
                                      OrbitalAttackCost cost) {
        OrbitalEnergyReserve after = weapons.find(weaponId).orElseThrow().reserve();
        helper.assertValueEqual(
                before.celestialEnergy() - after.celestialEnergy(),
                cost.celestialEnergy(),
                "Kinetic confirmation must escrow its configured Celestial Energy cost");
        helper.assertValueEqual(
                before.aeEnergy() - after.aeEnergy(),
                cost.aeEnergy(),
                "Kinetic confirmation must escrow its configured AE energy cost");
    }

    private static void loadTerrainChunks(ServerLevel level, BlockPos target) {
        int radius = OrbitalKineticStrike.CRATER_RADIUS;
        int minChunkX = Math.floorDiv(target.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(target.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(target.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(target.getZ() + radius, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static void placeBlock(
                                   GameTestHelper helper,
                                   BlockPos relativePos,
                                   Block block,
                                   ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place kinetic test block at " + absolutePos);
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
