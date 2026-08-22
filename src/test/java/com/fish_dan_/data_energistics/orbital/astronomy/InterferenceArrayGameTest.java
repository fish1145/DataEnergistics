package com.fish_dan_.data_energistics.orbital.astronomy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.orbital.astronomy.InterferenceArrayCoreBlock;
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
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class InterferenceArrayGameTest {

    private static final BlockPos CORE = new BlockPos(25, 2, 25);
    private static final List<BlockPos> MIRROR_CENTERS = List.of(
            new BlockPos(25, 6, 22),
            new BlockPos(25, 6, 28),
            new BlockPos(22, 6, 25),
            new BlockPos(28, 6, 25));
    private static final List<BlockPos> WAVEGUIDE_BREAK_PATH = List.of(
            new BlockPos(25, 3, 22),
            new BlockPos(25, 4, 22),
            new BlockPos(25, 5, 22));
    private static final BlockPos SKY_BLOCKER = MIRROR_CENTERS.getFirst().above();
    private static final ResourceLocation CREATIVE_ENERGY_CELL_ID = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "creative_energy_cell");
    private static final BlockPos ENERGY_CELL = new BlockPos(25, 1, 25);
    private static final BlockPos STORAGE_DEPOT = new BlockPos(24, 1, 25);
    private static final BlockPos UPLINK_BEACON = new BlockPos(23, 1, 25);
    private static final BlockPos CONTROL_CONSOLE = new BlockPos(22, 1, 25);

    private InterferenceArrayGameTest() {}

    @TestHolder("interference_array_produces_and_stops_when_mirror_path_or_sky_is_invalid")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 700)
    public static void producesAndStopsWhenMirrorPathOrSkyIsInvalid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "interference-array-owner");
        long originalDayTime = level.getDayTime();
        boolean originalRaining = level.isRaining();
        boolean originalThundering = level.isThundering();
        AtomicLong checkpoint = new AtomicLong();

        level.setDayTime(14_000L);
        setClearWeather(level);
        buildCoreBase(helper);
        buildFourMirrors(helper);
        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, UPLINK_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        placeBlock(helper, STORAGE_DEPOT, DEBlocks.DIGITAL_STORAGE_DEPOT.get(), owner);
        placeRegisteredBlock(helper, ENERGY_CELL, CREATIVE_ENERGY_CELL_ID);

        UUID weaponId = data.ownedBy(owner.getUUID()).orElseThrow().weaponId();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isCoreProducing(helper),
                            "A formed four-mirror array with AE storage and power must produce Celestial Energy");
                    helper.assertTrue(
                            celestialReserve(data, weaponId) > 0L,
                            "High-tier production must reach the weapon reserve through the real AE network");
                })
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(8)
                .thenExecute(() -> helper.assertTrue(
                        celestialReserve(data, weaponId) > checkpoint.get(),
                        "A valid four-mirror array must continue adding Celestial Energy"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(WAVEGUIDE_BREAK_PATH.get(1)), false),
                            "A waveguide segment must be removable to invalidate one mirror path");
                })
                .thenWaitUntil(() -> helper.assertFalse(
                        isCoreProducing(helper),
                        "Removing one of four required mirror paths must stop the array"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(8)
                .thenExecute(() -> helper.assertValueEqual(
                        celestialReserve(data, weaponId),
                        checkpoint.get(),
                        "A path-invalid array must not add Celestial Energy"))
                .thenExecute(() -> placeBlock(
                        helper,
                        WAVEGUIDE_BREAK_PATH.get(1),
                        DEBlocks.CELESTIAL_WAVEGUIDE.get(),
                        owner))
                .thenWaitUntil(() -> helper.assertTrue(
                        isCoreProducing(helper),
                        "Restoring the waveguide must recover the existing array"))
                .thenExecute(() -> helper.setBlock(SKY_BLOCKER, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertFalse(
                        isCoreProducing(helper),
                        "Blocking one mirror's 3x3 sky aperture must stop the four-mirror minimum array"))
                .thenExecute(() -> helper.destroyBlock(SKY_BLOCKER))
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isCoreProducing(helper),
                            "Restoring the mirror sky aperture must recover production");
                    helper.assertTrue(
                            celestialReserve(data, weaponId) > checkpoint.get(),
                            "Recovered array production must reach the same weapon reserve");
                })
                .thenExecute(() -> restoreEnvironment(level, originalDayTime, originalRaining, originalThundering))
                .thenSucceed();
    }

    private static void buildCoreBase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absoluteCore = helper.absolutePos(CORE);
        for (int y = 0; y < 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos relative = CORE.offset(x, y, z);
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    boolean port = y == 1 &&
                            ((Math.abs(x) == 2 && z == 0) || (x == 0 && Math.abs(z) == 2));
                    placeBlock(
                            helper,
                            relative,
                            port ? DEBlocks.CELESTIAL_WAVEGUIDE.get() : DEBlocks.DATA_FRAMEWORK.get(),
                            null);
                }
            }
        }
        placeBlock(helper, CORE, DEBlocks.INTERFERENCE_ARRAY_CORE.get(), null);
        if (level.getBlockState(absoluteCore).getBlock() != DEBlocks.INTERFERENCE_ARRAY_CORE.get()) {
            throw new IllegalStateException("Failed to place the interference array core");
        }
    }

    private static void buildFourMirrors(GameTestHelper helper) {
        for (int index = 0; index < MIRROR_CENTERS.size(); index++) {
            BlockPos center = MIRROR_CENTERS.get(index);
            placeBlock(helper, center, DEBlocks.ASTRONOMICAL_MIRROR.get(), null);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || z != 0) {
                        placeBlock(
                                helper,
                                center.offset(x, 0, z),
                                DEBlocks.ASTRONOMICAL_MIRROR_PANEL.get(),
                                null);
                    }
                }
            }
            for (BlockPos path : pathForMirror(index)) {
                placeBlock(helper, path, DEBlocks.CELESTIAL_WAVEGUIDE.get(), null);
            }
        }
    }

    private static List<BlockPos> pathForMirror(int index) {
        BlockPos center = MIRROR_CENTERS.get(index);
        BlockPos port = new BlockPos(
                Integer.signum(center.getX() - CORE.getX()) * 2 + CORE.getX(),
                CORE.getY() + 1,
                Integer.signum(center.getZ() - CORE.getZ()) * 2 + CORE.getZ());
        BlockPos first = new BlockPos(center.getX(), port.getY(), center.getZ());
        return List.of(
                new BlockPos(first.getX(), port.getY(), first.getZ()),
                new BlockPos(first.getX(), center.getY() - 2, first.getZ()),
                new BlockPos(first.getX(), center.getY() - 1, first.getZ()));
    }

    private static void placeBlock(GameTestHelper helper, BlockPos relativePos, Block block, @Nullable ServerPlayer placer) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
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

    private static boolean isCoreProducing(GameTestHelper helper) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(CORE));
        return state.getBlock() == DEBlocks.INTERFERENCE_ARRAY_CORE.get() &&
                state.getValue(InterferenceArrayCoreBlock.LIT);
    }

    private static long celestialReserve(OrbitalWeaponSavedData data, UUID weaponId) {
        return data.find(weaponId).orElseThrow().reserve().celestialEnergy();
    }

    private static void setClearWeather(ServerLevel level) {
        level.setWeatherParameters(6_000, 0, false, false);
    }

    private static void restoreEnvironment(
                                           ServerLevel level,
                                           long dayTime,
                                           boolean raining,
                                           boolean thundering) {
        level.setDayTime(dayTime);
        level.setWeatherParameters(raining ? 0 : 6_000, raining ? 6_000 : 0, raining, thundering);
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
