package com.fish_dan_.data_energistics.orbital.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalControlConsoleBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackCost;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackRecord;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackSavedData;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalOwnershipTransferGameTest {

    private static final BlockPos OWNER_CONSOLE = new BlockPos(2, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(4, 2, 2);
    private static final BlockPos FIRST_TARGET = new BlockPos(25, 20, 25);
    private static final BlockPos SECOND_TARGET = new BlockPos(35, 20, 25);
    private static final int TEST_DIGITAL_COOLDOWN_TICKS = 400;
    private static final int REJECTED_DELIVERY_OBSERVATION_TICKS =
            OrbitalAnnihilatorProjectileEntity.FLIGHT_TICKS + DataNukePrimedEntity.DEFAULT_FUSE_TICKS + 40;

    private OrbitalOwnershipTransferGameTest() {}

    @TestHolder("orbital_ownership_transfer_requires_both_players_online_until_acceptance")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_transfer_online", timeoutTicks = 800)
    public static void requiresBothPlayersOnlineUntilAcceptance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ExtendedGameTestHelper playerHelper = new ExtendedGameTestHelper(helper.testInfo);
        GameTestPlayer owner = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer recipient = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);
        BlockPos absoluteTarget = helper.absolutePos(FIRST_TARGET);

        placeBlock(helper, OWNER_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(FIRST_TARGET, Blocks.STONE);
        level.getChunkAt(absoluteTarget);

        UUID originalWeaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        AtomicReference<UUID> transferId = new AtomicReference<>();
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, originalWeaponId, level.dimension().location()),
                        "The transfer test must use a real powered owner endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost, 1));
                    primeReserve(weapons, server, originalWeaponId, settings, cost, 1);
                    OrbitalOwnershipTransfer transfer = weapons.requestOwnershipTransfer(
                            server,
                            owner.getUUID(),
                            originalWeaponId,
                            recipient.getUUID())
                            .orElseThrow(() -> new IllegalStateException("Two online eligible players could not create a transfer offer"));
                    transferId.set(transfer.transferId());
                    owner.disconnectGameTest();
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        server.getPlayerList().getPlayer(owner.getUUID()) == null,
                        "The current owner must be genuinely disconnected before acceptance"))
                .thenExecute(() -> {
                    weapons.acceptOwnershipTransfer(server, recipient.getUUID(), transferId.get());
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
                        attacks.tryConfirmDigitalAnnihilation(
                                server,
                                recipient.getUUID(),
                                originalWeaponId,
                                level.dimension().location(),
                                absoluteTarget);
                    } finally {
                        settings.attackWarningTicks = originalWarningTicks;
                        liveNukeSettings.workIntervalTicks = originalWorkInterval;
                        liveNukeSettings.maxRadius = originalMaxRadius;
                        liveNukeSettings.centerEntityConsumeRadius = originalCenterRadius;
                    }
                })
                .thenIdle(220)
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(
                                level.getBlockState(absoluteTarget).is(Blocks.STONE),
                                "A disconnected owner must not leave a transferable deployed weapon for unilateral use");
                    } finally {
                        OrbitalControlActionDispatcher.cancelOrAbortFirst(recipient);
                    }
                })
                .thenSucceed();
    }

    @TestHolder("orbital_ownership_transfer_allows_dormant_and_preserves_cooldown")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_transfer_lifecycle", timeoutTicks = 1800)
    public static void transfersDormantWeaponAndPreservesCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(server);
        OrbitalAttackSavedData attacks = OrbitalAttackSavedData.get(server);
        ExtendedGameTestHelper playerHelper = new ExtendedGameTestHelper(helper.testInfo);
        GameTestPlayer owner = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer recipient = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        GameTestPlayer successor = playerHelper.makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings =
                DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        OrbitalAttackCost cost = OrbitalAttackCost.digitalAnnihilation(settings);
        BlockPos absoluteFirstTarget = helper.absolutePos(FIRST_TARGET);
        BlockPos absoluteSecondTarget = helper.absolutePos(SECOND_TARGET);

        placeBlock(helper, OWNER_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, DRIVE, AEBlocks.DRIVE.block(), owner);
        placeBlock(helper, CREATIVE_ENERGY_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block(), owner);
        installInfiniteCell(helper);
        helper.setBlock(FIRST_TARGET, Blocks.STONE);
        helper.setBlock(SECOND_TARGET, Blocks.STONE);
        level.getChunkAt(absoluteFirstTarget);
        level.getChunkAt(absoluteSecondTarget);

        UUID weaponId = weapons.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        OrbitalOwnershipTransfer dormantTransfer = weapons.requestOwnershipTransfer(
                server,
                owner.getUUID(),
                weaponId,
                recipient.getUUID())
                .orElseThrow(() -> new IllegalStateException("A dormant weapon could not create a transfer offer"));
        weapons.acceptOwnershipTransfer(server, recipient.getUUID(), dormantTransfer.transferId());

        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> helper.assertTrue(
                        weapons.hasOnlineEndpoint(server, weaponId, level.dimension().location()),
                        "The transferred dormant weapon must retain its real powered endpoint"))
                .thenExecute(() -> {
                    insertCelestialEnergy(helper, requiredCelestialEnergy(settings, cost, 2));
                    primeReserve(weapons, server, weaponId, settings, cost, 2);
                    launchRequiredTestDigitalAttack(
                            attacks,
                            server,
                            recipient,
                            weaponId,
                            level,
                            absoluteFirstTarget);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(absoluteFirstTarget).isAir(),
                        "The recipient must be able to fire the dormant weapon after accepting ownership"))
                .thenIdle(20)
                .thenExecute(() -> {
                    OrbitalOwnershipTransfer cooldownTransfer = weapons.requestOwnershipTransfer(
                            server,
                            recipient.getUUID(),
                            weaponId,
                            successor.getUUID())
                            .orElseThrow(() -> new IllegalStateException(
                                    "A cooling weapon could not create a transfer offer"));
                    weapons.acceptOwnershipTransfer(server, successor.getUUID(), cooldownTransfer.transferId());
                    attemptTestDigitalAttack(
                            attacks,
                            server,
                            successor,
                            weaponId,
                            level,
                            absoluteSecondTarget);
                })
                .thenIdle(REJECTED_DELIVERY_OBSERVATION_TICKS)
                .thenExecute(() -> helper.assertTrue(
                        level.getBlockState(absoluteSecondTarget).is(Blocks.STONE),
                        "The consumed cooldown must follow the weapon and block its successor's early payload"))
                .thenIdle(TEST_DIGITAL_COOLDOWN_TICKS)
                .thenExecute(() -> launchRequiredTestDigitalAttack(
                        attacks,
                        server,
                        successor,
                        weaponId,
                        level,
                        absoluteSecondTarget))
                .thenWaitUntil(() -> helper.assertTrue(
                        level.getBlockState(absoluteSecondTarget).isAir(),
                        "The successor must be able to fire after the transferred cooldown expires"))
                .thenIdle(TEST_DIGITAL_COOLDOWN_TICKS + 5)
                .thenSucceed();
    }

    private static void installInfiniteCell(GameTestHelper helper) {
        if (!(helper.getBlockEntity(DRIVE) instanceof DriveBlockEntity drive)) {
            throw new IllegalStateException("The transfer test drive has no block entity");
        }
        drive.getInternalInventory().setItemDirect(0, DEItems.DATA_CELL_INFINITY.toStack());
    }

    private static void insertCelestialEnergy(GameTestHelper helper, long amount) {
        if (!(helper.getBlockEntity(OWNER_CONSOLE) instanceof OrbitalControlConsoleBlockEntity console)) {
            throw new IllegalStateException("The transfer test owner console has no block entity");
        }
        IGrid grid = console.getMainNode().getGrid();
        if (grid == null || !console.getMainNode().isActive()) {
            throw new IllegalStateException("The transfer test AE grid is not active");
        }
        long inserted = grid.getStorageService().getInventory().insert(
                CelestialEnergyKey.of(),
                amount,
                Actionable.MODULATE,
                IActionSource.ofMachine(console));
        if (inserted != amount) {
            throw new IllegalStateException("The transfer test could not seed Celestial Energy storage");
        }
    }

    private static void primeReserve(
                                      OrbitalWeaponSavedData weapons,
                                      MinecraftServer server,
                                      UUID weaponId,
                                      DataEnergisticsConfiguration.OrbitalWeaponSchema settings,
                                      OrbitalAttackCost cost,
                                      int attackCount) {
        long requiredCelestialEnergy = Math.max(
                Math.multiplyExact(cost.celestialEnergy(), attackCount),
                deploymentTarget(settings.celestialEnergyCapacity(), settings.deploymentThreshold));
        long requiredAeEnergy = Math.max(
                Math.multiplyExact(cost.aeEnergy(), attackCount),
                deploymentTarget(settings.aeEnergyCapacity(), settings.deploymentThreshold));
        for (int attempts = 0; attempts < 20_000; attempts++) {
            var weapon = weapons.find(weaponId).orElseThrow();
            if (weapon.allowsNewAttacks() && weapon.reserve().canAfford(requiredCelestialEnergy, requiredAeEnergy)) {
                return;
            }
            weapons.chargeReserves(server);
        }
        throw new IllegalStateException("The real AE endpoint did not fund the transferable weapon");
    }

    private static long requiredCelestialEnergy(
                                                 DataEnergisticsConfiguration.OrbitalWeaponSchema settings,
                                                 OrbitalAttackCost cost,
                                                 int attackCount) {
        return Math.max(
                Math.multiplyExact(cost.celestialEnergy(), attackCount),
                deploymentTarget(settings.celestialEnergyCapacity(), settings.deploymentThreshold));
    }

    private static void launchRequiredTestDigitalAttack(
                                                        OrbitalAttackSavedData attacks,
                                                        MinecraftServer server,
                                                        GameTestPlayer player,
                                                        UUID weaponId,
                                                        ServerLevel level,
                                                        BlockPos target) {
        if (attemptTestDigitalAttack(attacks, server, player, weaponId, level, target).isEmpty()) {
            throw new IllegalStateException("The transferable weapon could not launch its required test payload");
        }
    }

    private static Optional<OrbitalAttackRecord> attemptTestDigitalAttack(
                                                                          OrbitalAttackSavedData attacks,
                                                                          MinecraftServer server,
                                                                          GameTestPlayer player,
                                                                          UUID weaponId,
                                                                          ServerLevel level,
                                                                          BlockPos target) {
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings =
                DataEnergisticsConfiguration.INSTANCE.orbitalWeapon;
        var liveNukeSettings = DataEnergisticsConfiguration.INSTANCE.explosives.dataNuke;
        int originalWarningTicks = settings.attackWarningTicks;
        int originalCooldownTicks = settings.digitalAnnihilationCooldownTicks;
        int originalWorkInterval = liveNukeSettings.workIntervalTicks;
        int originalMaxRadius = liveNukeSettings.maxRadius;
        double originalCenterRadius = liveNukeSettings.centerEntityConsumeRadius;
        try {
            settings.attackWarningTicks = 1;
            settings.digitalAnnihilationCooldownTicks = TEST_DIGITAL_COOLDOWN_TICKS;
            liveNukeSettings.workIntervalTicks = 1;
            liveNukeSettings.maxRadius = 1;
            liveNukeSettings.centerEntityConsumeRadius = 0.0D;
            return attacks.tryConfirmDigitalAnnihilation(
                    server,
                    player.getUUID(),
                    weaponId,
                    level.dimension().location(),
                    target);
        } finally {
            settings.attackWarningTicks = originalWarningTicks;
            settings.digitalAnnihilationCooldownTicks = originalCooldownTicks;
            liveNukeSettings.workIntervalTicks = originalWorkInterval;
            liveNukeSettings.maxRadius = originalMaxRadius;
            liveNukeSettings.centerEntityConsumeRadius = originalCenterRadius;
        }
    }

    private static long deploymentTarget(long capacity, double threshold) {
        return Math.max(1L, (long) Math.ceil(capacity * threshold));
    }

    private static void placeBlock(
                                   GameTestHelper helper,
                                   BlockPos relativePos,
                                   Block block,
                                   GameTestPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place transfer test block at " + absolutePos);
        }
        state.getBlock().setPlacedBy(level, absolutePos, state, placer, ItemStack.EMPTY);
    }
}
