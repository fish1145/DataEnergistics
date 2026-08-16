package com.fish_dan_.data_energistics.orbital.endpoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalUplinkBeaconGameTest {

    private static final BlockPos UNBOUND_BEACON = new BlockPos(1, 2, 1);
    private static final BlockPos CONTROL_CONSOLE = new BlockPos(2, 2, 1);
    private static final BlockPos BOUND_BEACON = new BlockPos(3, 2, 1);
    private static final BlockPos DISTANT_BEACON = new BlockPos(160 * 16, 2, 64 * 16);
    private static final ResourceLocation CREATIVE_ENERGY_CELL_ID = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "creative_energy_cell");

    private OrbitalUplinkBeaconGameTest() {}

    @TestHolder("orbital_uplink_beacon_requires_existing_weapon_and_releases_binding")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void requiresExistingWeaponAndReleasesBinding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "uplink-owner");

        placeBlock(helper, UNBOUND_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        OrbitalEndpointLocation unboundLocation = location(helper, UNBOUND_BEACON);
        helper.assertTrue(
                data.ownedBy(owner.getUUID()).isEmpty(),
                "Placing an uplink beacon must not create an orbital weapon");
        helper.assertTrue(
                data.weaponAt(unboundLocation).isEmpty(),
                "An uplink beacon must remain unbound until its player owns a weapon");

        helper.assertTrue(
                level.destroyBlock(helper.absolutePos(UNBOUND_BEACON), false),
                "The rejected uplink beacon must remain removable");
        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        OrbitalEndpointLocation consoleLocation = location(helper, CONTROL_CONSOLE);
        UUID weaponId = data.weaponAt(consoleLocation).orElseThrow().weaponId();

        placeBlock(helper, BOUND_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        OrbitalEndpointLocation beaconLocation = location(helper, BOUND_BEACON);
        helper.assertValueEqual(
                data.weaponAt(beaconLocation).orElseThrow().weaponId(),
                weaponId,
                "After console provisioning, an uplink beacon must bind the same owned weapon");
        helper.assertValueEqual(
                data.weaponAt(beaconLocation).orElseThrow().endpoints().get(beaconLocation).kind(),
                OrbitalEndpointKind.UPLINK_BEACON,
                "The physical beacon must be persisted as an uplink endpoint");

        helper.assertTrue(
                level.destroyBlock(helper.absolutePos(BOUND_BEACON), false),
                "A bound uplink beacon must be removable");
        helper.assertTrue(
                data.weaponAt(beaconLocation).isEmpty(),
                "Destroying the beacon must release its endpoint binding");
        helper.assertValueEqual(
                data.weaponAt(consoleLocation).orElseThrow().weaponId(),
                weaponId,
                "Destroying the beacon must preserve the weapon's control-console endpoint");
        helper.succeed();
    }

    @TestHolder("orbital_uplink_beacon_stays_loaded_and_recovers_after_power_cycle")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void staysLoadedAndRecoversAfterPowerCycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "uplink-ticket-owner");
        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        UUID weaponId = data.ownedBy(owner.getUUID()).orElseThrow().weaponId();

        BlockPos beaconPos = helper.absolutePos(DISTANT_BEACON);
        BlockPos energyCellPos = beaconPos.below();
        level.getChunkAt(beaconPos);
        ChunkPos beaconChunk = new ChunkPos(beaconPos);
        OrbitalEndpointLocation beaconLocation = new OrbitalEndpointLocation(level.dimension().location(), beaconPos);
        AtomicLong reserveAtPowerLoss = new AtomicLong();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, beaconChunk),
                        "The distant chunk must not be force-ticked before an uplink beacon is bound"))
                .thenExecute(() -> placeBlock(helper, DISTANT_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner))
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            data.weaponAt(beaconLocation).isPresent(),
                            "The distant uplink beacon must be bound before it owns a chunk ticket");
                    helper.assertTrue(
                            isForceTicked(level, beaconChunk),
                            "A bound uplink beacon must force-tick its own distant chunk");
                    helper.assertTrue(
                            level.getChunkSource().isPositionTicking(beaconChunk.toLong()),
                            "The uplink beacon's chunk must receive full server ticks without a nearby player");
                    helper.assertFalse(
                            data.hasOnlineEndpoint(level.getServer(), weaponId, level.dimension().location()),
                            "A bound endpoint without AE power must remain offline");
                })
                .thenExecute(() -> placeCreativeEnergyCell(level, energyCellPos))
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            data.hasOnlineEndpoint(level.getServer(), weaponId, level.dimension().location()),
                            "Supplying a booted AE grid must bring the endpoint online");
                    helper.assertTrue(
                            data.find(weaponId).orElseThrow().reserve().aeEnergy() > 0L,
                            "The online beacon must charge its weapon from the real AE grid");
                })
                .thenExecute(() -> helper.assertTrue(
                        level.destroyBlock(energyCellPos, false),
                        "The endpoint's AE power source must be removable"))
                .thenWaitUntil(() -> {
                    helper.assertFalse(
                            data.hasOnlineEndpoint(level.getServer(), weaponId, level.dimension().location()),
                            "Removing AE power must take the endpoint offline");
                    helper.assertTrue(
                            isForceTicked(level, beaconChunk),
                            "An offline endpoint must retain its chunk ticket so it can detect restored power");
                })
                .thenExecute(() -> reserveAtPowerLoss.set(
                        data.find(weaponId).orElseThrow().reserve().aeEnergy()))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        data.find(weaponId).orElseThrow().reserve().aeEnergy(),
                        reserveAtPowerLoss.get(),
                        "An offline endpoint must stop drawing AE energy into the orbital reserve"))
                .thenExecute(() -> placeCreativeEnergyCell(level, energyCellPos))
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            data.hasOnlineEndpoint(level.getServer(), weaponId, level.dimension().location()),
                            "Restoring AE power must recover the existing endpoint without replacing it");
                    helper.assertTrue(
                            data.find(weaponId).orElseThrow().reserve().aeEnergy() > reserveAtPowerLoss.get(),
                            "The recovered endpoint must resume charging the same weapon reserve");
                })
                .thenExecute(() -> helper.assertTrue(
                        level.destroyBlock(beaconPos, false),
                        "The force-loaded uplink beacon must be removable"))
                .thenWaitUntil(() -> {
                    helper.assertFalse(
                            isForceTicked(level, beaconChunk),
                            "Destroying the uplink beacon must release its chunk ticket");
                    helper.assertTrue(
                            data.weaponAt(beaconLocation).isEmpty(),
                            "Destroying the uplink beacon must also release its endpoint binding");
                })
                .thenExecute(() -> level.destroyBlock(energyCellPos, false))
                .thenSucceed();
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
            throw new IllegalStateException("Failed to place orbital endpoint at " + absolutePos);
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

    private static OrbitalEndpointLocation location(GameTestHelper helper, BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        return new OrbitalEndpointLocation(level.dimension().location(), helper.absolutePos(relativePos));
    }

    private static boolean isForceTicked(ServerLevel level, ChunkPos chunkPos) {
        return level.getChunkSource().chunkMap.getDistanceManager().shouldForceTicks(chunkPos.toLong());
    }

    private static void placeCreativeEnergyCell(ServerLevel level, BlockPos pos) {
        Block energyCell = BuiltInRegistries.BLOCK.get(CREATIVE_ENERGY_CELL_ID);
        if (energyCell == Blocks.AIR || !level.setBlock(pos, energyCell.defaultBlockState(), Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place the AE creative energy cell at " + pos);
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
