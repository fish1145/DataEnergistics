package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.orbital.astronomy.InterferenceArrayCoreBlock;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exercises the high-tier array's configured diminishing tiers and exclusive mirror ownership in a real AE world.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class InterferenceArrayScalingGameTest {

    private static final BlockPos CORE = new BlockPos(25, 2, 25);
    private static final BlockPos SECOND_CORE = new BlockPos(25, 2, 40);
    private static final List<List<BlockPos>> MIRROR_ROUNDS = List.of(
            List.of(mirror(0, -3), mirror(0, 3), mirror(-3, 0), mirror(3, 0)),
            List.of(mirror(0, -6), mirror(0, 6), mirror(-6, 0), mirror(6, 0)),
            List.of(mirror(0, -9), mirror(0, 9), mirror(-9, 0), mirror(9, 0)),
            List.of(mirror(0, -12), mirror(0, 12), mirror(-12, 0), mirror(12, 0)));
    private static final ResourceLocation CREATIVE_ENERGY_CELL_ID = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "creative_energy_cell");
    private static final BlockPos ARRAY_CONSOLE = new BlockPos(22, 1, 25);
    private static final BlockPos ARRAY_BEACON = new BlockPos(23, 1, 25);
    private static final BlockPos ARRAY_STORAGE = new BlockPos(24, 1, 25);
    private static final BlockPos ARRAY_ENERGY = new BlockPos(25, 1, 25);
    private static final BlockPos SECOND_CONSOLE = new BlockPos(22, 1, 40);
    private static final BlockPos SECOND_BEACON = new BlockPos(23, 1, 40);
    private static final BlockPos SECOND_STORAGE = new BlockPos(24, 1, 40);
    private static final BlockPos SECOND_ENERGY = new BlockPos(25, 1, 40);
    private static final BlockPos SECOND_CORE_BRIDGE = new BlockPos(25, 3, 37);

    private InterferenceArrayScalingGameTest() {}

    @TestHolder("interference_array_scales_tiers_and_transfers_exclusive_mirrors")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 1_000)
    public static void scalesTiersAndTransfersExclusiveMirrors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer firstOwner = createPlayer(level, "interference-array-first-owner");
        ServerPlayer secondOwner = createPlayer(level, "interference-array-second-owner");
        level.setDayTime(14_000L);
        level.setWeatherParameters(6_000, 0, false, false);

        buildCoreBase(helper, CORE);
        buildWaveguideArms(helper);
        placeNetwork(helper, ARRAY_CONSOLE, ARRAY_BEACON, ARRAY_STORAGE, ARRAY_ENERGY, firstOwner);
        UUID firstWeapon = data.ownedBy(firstOwner.getUUID()).orElseThrow().weaponId();
        AtomicLong outputCheckpoint = new AtomicLong();
        AtomicBoolean rainingCheckpoint = new AtomicBoolean();

        helper.startSequence()
                .thenExecute(() -> addMirrors(helper, MIRROR_ROUNDS.get(0)))
                .thenWaitUntil(() -> helper.assertTrue(
                        isProducing(helper, CORE),
                        "The four connected mirror units must activate the array"))
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        160L,
                        "four mirrors"))
                .thenExecute(() -> addMirrors(helper, MIRROR_ROUNDS.get(1)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        280L,
                        "eight mirrors"))
                .thenExecute(() -> addMirrors(helper, MIRROR_ROUNDS.get(2)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        360L,
                        "twelve mirrors"))
                .thenExecute(() -> addMirrors(helper, MIRROR_ROUNDS.get(3)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        400L,
                        "sixteen mirrors"))
                .thenExecute(() -> removeMirrors(helper, MIRROR_ROUNDS.get(0)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        360L,
                        "twelve remaining mirrors"))
                .thenExecute(() -> removeMirrors(helper, MIRROR_ROUNDS.get(1)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        280L,
                        "eight remaining mirrors"))
                .thenExecute(() -> removeMirrors(helper, MIRROR_ROUNDS.get(2)))
                .thenIdle(25)
                .thenExecute(() -> checkpoint(level, data, firstWeapon, outputCheckpoint, rainingCheckpoint))
                .thenIdle(1)
                .thenExecute(() -> assertOneTickOutput(
                        helper,
                        data,
                        firstWeapon,
                        outputCheckpoint,
                        rainingCheckpoint,
                        160L,
                        "four remaining mirrors"))
                .thenExecute(() -> addMirrors(
                        helper,
                        List.of(mirror(0, 3), mirror(0, 6), mirror(0, 9))))
                .thenIdle(25)
                .thenExecute(() -> {
                    buildCoreBase(helper, SECOND_CORE);
                    placeNetwork(
                            helper,
                            SECOND_CONSOLE,
                            SECOND_BEACON,
                            SECOND_STORAGE,
                            SECOND_ENERGY,
                            secondOwner);
                    placeBlock(helper, SECOND_CORE_BRIDGE, DEBlocks.CELESTIAL_WAVEGUIDE.get());
                })
                .thenExecute(() -> {
                    UUID secondWeapon = data.ownedBy(secondOwner.getUUID()).orElseThrow().weaponId();
                    DataEnergisticsConfiguration.AstronomySchema settings = DataEnergisticsConfiguration.INSTANCE.astronomy;
                    helper.assertTrue(
                            InterferenceArrayPattern.hasValidCoreBase(
                                    level,
                                    helper.absolutePos(SECOND_CORE)),
                            "The second core's 5x5x3 base must be valid before mirror competition");
                    helper.assertTrue(
                            InterferenceArrayPattern.findConnectedMirrors(
                                    level,
                                    helper.absolutePos(SECOND_CORE),
                                    settings)
                                    .size() >= 4,
                            "The second core must reach the four south mirror units through its bridge");
                    helper.assertFalse(
                            isProducing(helper, SECOND_CORE),
                            "A second powered core must not produce while the four mirrors are claimed by the first core");
                    helper.assertValueEqual(
                            celestialReserve(data, secondWeapon),
                            0L,
                            "A shared mirror must not charge the second core's weapon");
                })
                .thenIdle(45)
                .thenExecute(() -> helper.assertFalse(
                        isProducing(helper, SECOND_CORE),
                        "A second powered core must not produce while the four mirrors are claimed by the first core"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            helper.getLevel().destroyBlock(helper.absolutePos(CORE), false),
                            "The first core must be removable for an ownership transfer");
                })
                .thenIdle(45)
                .thenExecute(() -> helper.assertTrue(
                        isProducing(helper, SECOND_CORE),
                        "After the first core is removed, the second core must claim the released mirror units"))
                .thenExecute(() -> {
                    UUID secondWeapon = data.ownedBy(secondOwner.getUUID()).orElseThrow().weaponId();
                    checkpoint(level, data, secondWeapon, outputCheckpoint, rainingCheckpoint);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    UUID secondWeapon = data.ownedBy(secondOwner.getUUID()).orElseThrow().weaponId();
                    assertOneTickOutput(
                            helper,
                            data,
                            secondWeapon,
                            outputCheckpoint,
                            rainingCheckpoint,
                            160L,
                            "transferred four mirrors");
                })
                .thenSucceed();
    }

    private static void buildCoreBase(GameTestHelper helper, BlockPos core) {
        for (int y = 0; y < 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    boolean port = y == 1 &&
                            ((Math.abs(x) == 2 && z == 0) || (x == 0 && Math.abs(z) == 2));
                    placeBlock(
                            helper,
                            core.offset(x, y, z),
                            port ? DEBlocks.CELESTIAL_WAVEGUIDE.get() : DEBlocks.DATA_FRAMEWORK.get());
                }
            }
        }
        placeBlock(helper, core, DEBlocks.INTERFERENCE_ARRAY_CORE.get());
    }

    private static void buildWaveguideArms(GameTestHelper helper) {
        for (List<BlockPos> round : MIRROR_ROUNDS) {
            for (BlockPos mirror : round) {
                int deltaX = mirror.getX() - CORE.getX();
                int deltaZ = mirror.getZ() - CORE.getZ();
                if (deltaX != 0) {
                    int direction = Integer.signum(deltaX);
                    for (int x = 3; x <= Math.abs(deltaX); x++) {
                        placeBlock(
                                helper,
                                new BlockPos(CORE.getX() + direction * x, CORE.getY() + 1, CORE.getZ()),
                                DEBlocks.CELESTIAL_WAVEGUIDE.get());
                    }
                } else {
                    int direction = Integer.signum(deltaZ);
                    for (int z = 3; z <= Math.abs(deltaZ); z++) {
                        placeBlock(
                                helper,
                                new BlockPos(CORE.getX(), CORE.getY() + 1, CORE.getZ() + direction * z),
                                DEBlocks.CELESTIAL_WAVEGUIDE.get());
                    }
                }
                for (int y = CORE.getY() + 2; y <= CORE.getY() + 3; y++) {
                    placeBlock(
                            helper,
                            new BlockPos(mirror.getX(), y, mirror.getZ()),
                            DEBlocks.CELESTIAL_WAVEGUIDE.get());
                }
            }
        }
    }

    private static void addMirrors(GameTestHelper helper, List<BlockPos> mirrors) {
        for (BlockPos center : mirrors) {
            placeBlock(helper, center, DEBlocks.ASTRONOMICAL_MIRROR.get());
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) {
                        placeBlock(
                                helper,
                                center.offset(x, 0, z),
                                DEBlocks.ASTRONOMICAL_MIRROR_PANEL.get());
                    }
                }
            }
        }
    }

    private static void removeMirrors(GameTestHelper helper, List<BlockPos> mirrors) {
        for (BlockPos center : mirrors) {
            helper.assertTrue(
                    helper.getLevel().destroyBlock(helper.absolutePos(center), false),
                    "A mirror center must be removable during the scaling scenario");
        }
    }

    private static void placeNetwork(
                                     GameTestHelper helper,
                                     BlockPos console,
                                     BlockPos beacon,
                                     BlockPos storage,
                                     BlockPos energy,
                                     ServerPlayer owner) {
        placeBlock(helper, console, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, beacon, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        placeBlock(helper, storage, DEBlocks.DIGITAL_STORAGE_DEPOT.get(), owner);
        placeRegisteredBlock(helper, energy, CREATIVE_ENERGY_CELL_ID);
    }

    private static void placeBlock(GameTestHelper helper, BlockPos relativePos, Block block) {
        placeBlock(helper, relativePos, block, null);
    }

    private static void placeBlock(
                                   GameTestHelper helper,
                                   BlockPos relativePos,
                                   Block block,
                                   @Nullable ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL) &&
                !level.getBlockState(absolutePos).is(block)) {
            throw new IllegalStateException("Failed to place test block at " + absolutePos);
        }
        if (placer != null) {
            state.getBlock().setPlacedBy(level, absolutePos, state, placer, ItemStack.EMPTY);
        }
    }

    private static void placeRegisteredBlock(GameTestHelper helper, BlockPos relativePos, ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        if (block == Blocks.AIR ||
                !helper.getLevel().setBlock(absolutePos, block.defaultBlockState(), Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place registered block " + blockId + " at " + absolutePos);
        }
    }

    private static boolean isProducing(GameTestHelper helper, BlockPos corePos) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(corePos));
        return state.getBlock() == DEBlocks.INTERFERENCE_ARRAY_CORE.get() &&
                state.getValue(InterferenceArrayCoreBlock.LIT);
    }

    private static void assertOneTickOutput(
                                            GameTestHelper helper,
                                            OrbitalWeaponSavedData data,
                                            UUID weaponId,
                                            AtomicLong checkpoint,
                                            AtomicBoolean raining,
                                            long expected,
                                            String stage) {
        long after = celestialReserve(data, weaponId);
        long weatherAdjustedExpected = raining.get() ? (long) Math.floor(expected * DataEnergisticsConfiguration.INSTANCE.astronomy.rainOutputMultiplier()) : expected;
        helper.assertValueEqual(
                after - checkpoint.get(),
                weatherAdjustedExpected,
                "A real AE storage tick must produce the configured " + stage + " output" + (raining.get() ? " while raining" : " under clear weather"));
    }

    private static void checkpoint(
                                   ServerLevel level,
                                   OrbitalWeaponSavedData data,
                                   UUID weaponId,
                                   AtomicLong output,
                                   AtomicBoolean raining) {
        raining.set(level.isRaining());
        output.set(celestialReserve(data, weaponId));
    }

    private static long celestialReserve(OrbitalWeaponSavedData data, UUID weaponId) {
        return data.find(weaponId).orElseThrow().reserve().celestialEnergy();
    }

    private static BlockPos mirror(int deltaX, int deltaZ) {
        return new BlockPos(CORE.getX() + deltaX, CORE.getY() + 4, CORE.getZ() + deltaZ);
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
