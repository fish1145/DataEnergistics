package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
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

/**
 * Verifies a real directed-energy warning, escrow, beam damage and budgeted terrain work against an AE endpoint.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalDirectedEnergyGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(4, 2, 2);
    private static final BlockPos TARGET = new BlockPos(25, 20, 25);

    private OrbitalDirectedEnergyGameTest() {}

    @TestHolder("orbital_directed_energy_scans_disk_and_resumes_budgeted_work")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_200)
    public static void scansDiskAndResumesBudgetedWork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ServerPlayer owner = createPlayer(level, "directed-energy-owner");
        DataEnergisticsSettings.OrbitalWeapon settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon();
        int radius = OrbitalDirectedEnergyStrike.MIN_RADIUS;
        OrbitalDirectedEnergyDepth depth = OrbitalDirectedEnergyDepth.DEPTH_32;
        long coordinateCount = OrbitalDirectedEnergyStrike.scheduledCoordinateCount(radius);
        OrbitalAttackCost cost = OrbitalAttackCost.directedEnergy(settings, coordinateCount);

        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        BlockPos target = helper.absolutePos(TARGET);
        helper.setBlock(TARGET, Blocks.STONE);
        loadTerrainChunks(level, target, radius);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<OrbitalEnergyReserve> reserveBefore = new AtomicReference<>();
        AtomicReference<UUID> attackId = new AtomicReference<>();
        AtomicReference<Zombie> victim = new AtomicReference<>();

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The directed-energy confirmation must use a real powered target-dimension endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, Math.multiplyExact(cost.celestialEnergy(), 2L));
                    primeReserve(weapons, server, weaponId, cost);
                    Zombie spawned = helper.spawn(EntityType.ZOMBIE, TARGET);
                    spawned.setNoAi(true);
                    victim.set(spawned);
                    reserveBefore.set(weapons.find(weaponId).orElseThrow().reserve());
                    OrbitalAttackRecord warning = attacks.tryConfirmDirectedEnergy(
                            server,
                            owner.getUUID(),
                            weaponId,
                            level.dimension().location(),
                            target,
                            radius,
                            depth)
                            .orElseThrow(() -> new IllegalStateException("A funded directed-energy scan was rejected"));
                    attackId.set(warning.attackId());
                    assertDebited(helper, weapons, weaponId, reserveBefore.get(), cost);
                    helper.assertValueEqual(
                            warning.geometry(),
                            new OrbitalAttackGeometry.DirectedEnergy(radius, depth, settings.directedEnergyEntityDamage()),
                            "The warning must persist the selected directed-energy geometry");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The charging source must be removable before the warning is observed");
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CONTROL_CONSOLE), false),
                            "The committed scan must not depend on a live endpoint");
                })
                .thenIdle(Math.max(1, settings.attackWarningTicks() / 2))
                .thenExecute(() -> {
                    OrbitalAttackRecord warning = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            warning.phase(),
                            OrbitalAttackPhase.RESERVED_WARNING,
                            "The directed-energy scan must remain refundable during its public warning (remaining=" + warning.warningTicksRemaining() + ")");
                    helper.assertTrue(
                            level.getBlockState(target).is(Blocks.STONE),
                            "The warning must not mutate target terrain (phase=" + warning.phase() + ", remaining=" + warning.warningTicksRemaining() + ")");
                    helper.assertTrue(victim.get().isAlive(), "The warning must not damage entities");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord attack = attacks.find(attackId.get()).orElseThrow();
                    helper.assertFalse(
                            attack.phase() == OrbitalAttackPhase.RESERVED_WARNING,
                            "The directed-energy scan must eventually commit");
                    helper.assertFalse(victim.get().isAlive(), "A beam column must apply its configured entity damage");
                })
                .thenWaitUntil(() -> {
                    OrbitalAttackRecord attack = attacks.find(attackId.get()).orElseThrow();
                    helper.assertValueEqual(
                            attack.phase(),
                            OrbitalAttackPhase.COOLDOWN,
                            "The complete disk must enter directed-energy cooldown");
                    helper.assertTrue(
                            level.getBlockState(target).isAir(),
                            "Budgeted directed-energy work must clear the target column without drops");
                    assertDebited(helper, weapons, weaponId, reserveBefore.get(), cost);
                })
                .thenExecute(() -> helper.assertTrue(
                        attacks.tryConfirmDirectedEnergy(
                                server,
                                owner.getUUID(),
                                weaponId,
                                level.dimension().location(),
                                target,
                                radius,
                                depth)
                                .isEmpty(),
                        "A directed-energy mode in cooldown must reject another funded scan"))
                .thenSucceed();
    }

    private static void installInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The directed-energy test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_CELL_INFINITY.toStack());
    }

    private static void insertCelestialEnergy(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(CONTROL_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The directed-energy test console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The directed-energy test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                CelestialEnergyKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The directed-energy test could not seed Celestial Energy storage");
        }
    }

    private static void primeReserve(
                                     OrbitalWeaponSavedData weapons,
                                     MinecraftServer server,
                                     UUID weaponId,
                                     OrbitalAttackCost cost) {
        long requiredCelestialEnergy = Math.multiplyExact(cost.celestialEnergy(), 2L);
        long requiredAeEnergy = Math.multiplyExact(cost.aeEnergy(), 2L);
        for (int attempts = 0; attempts < 2_000; attempts++) {
            OrbitalEnergyReserve reserve = weapons.find(weaponId).orElseThrow().reserve();
            if (reserve.canAfford(requiredCelestialEnergy, requiredAeEnergy)) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not fund two directed-energy scans");
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
                "Directed-energy confirmation must escrow its complete coordinate cost");
        helper.assertValueEqual(
                before.aeEnergy() - after.aeEnergy(),
                cost.aeEnergy(),
                "Directed-energy confirmation must escrow its complete AE coordinate cost");
    }

    private static void loadTerrainChunks(ServerLevel level, BlockPos target, int radius) {
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
            throw new IllegalStateException("Failed to place directed-energy test block at " + absolutePos);
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
