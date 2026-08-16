package com.fish_dan_.data_energistics.orbital.astronomy;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AstronomicalObservatoryGameTest {

    private static final BlockPos CONTROL_CONSOLE = new BlockPos(1, 2, 1);
    private static final BlockPos UPLINK_BEACON = new BlockPos(2, 2, 1);
    private static final BlockPos DIGITAL_STORAGE_DEPOT = new BlockPos(3, 2, 1);
    private static final BlockPos OBSERVATORY = new BlockPos(3, 2, 2);
    private static final BlockPos CREATIVE_ENERGY_CELL = new BlockPos(3, 2, 3);
    private static final BlockPos SKY_BLOCKER = OBSERVATORY.above();
    private static final ResourceLocation CREATIVE_ENERGY_CELL_ID = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "creative_energy_cell");

    private AstronomicalObservatoryGameTest() {}

    @TestHolder("astronomical_observatory_obeys_environment_and_preserves_transactions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 500)
    public static void obeysEnvironmentAndPreservesTransactions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level, "observatory-owner");
        long originalDayTime = level.getDayTime();
        boolean originalRaining = level.isRaining();
        boolean originalThundering = level.isThundering();
        AtomicLong checkpoint = new AtomicLong();
        AtomicLong clearWeatherGain = new AtomicLong();

        level.setDayTime(14_000L);
        setClearWeather(level);
        placeBlock(helper, CONTROL_CONSOLE, DEBlocks.ORBITAL_CONTROL_CONSOLE.get(), owner);
        placeBlock(helper, UPLINK_BEACON, DEBlocks.ORBITAL_UPLINK_BEACON.get(), owner);
        placeBlock(helper, OBSERVATORY, DEBlocks.ASTRONOMICAL_OBSERVATORY.get(), owner);
        placeRegisteredBlock(helper, CREATIVE_ENERGY_CELL, CREATIVE_ENERGY_CELL_ID);
        UUID weaponId = data.ownedBy(owner.getUUID()).orElseThrow().weaponId();

        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertFalse(
                            isObservatoryProducing(helper),
                            "An observatory without network storage must remain stopped");
                    helper.assertValueEqual(
                            celestialReserve(data, weaponId),
                            0L,
                            "A storage-blocked observatory must not create or buffer Celestial Energy");
                })
                .thenExecute(() -> placeBlock(
                        helper,
                        DIGITAL_STORAGE_DEPOT,
                        DEBlocks.DIGITAL_STORAGE_DEPOT.get(),
                        owner))
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isObservatoryProducing(helper),
                            "A powered observatory with an open night sky and storage must operate");
                    helper.assertTrue(
                            celestialReserve(data, weaponId) > 0L,
                            "Produced Celestial Energy must travel through the real AE grid into the weapon reserve");
                })
                .thenExecute(() -> helper.setBlock(SKY_BLOCKER, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertFalse(
                        isObservatoryProducing(helper),
                        "Blocking the observatory's center sky view must stop production"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        celestialReserve(data, weaponId),
                        checkpoint.get(),
                        "A sky-blocked observatory must not add Celestial Energy"))
                .thenExecute(() -> helper.destroyBlock(SKY_BLOCKER))
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isObservatoryProducing(helper),
                            "Restoring the sky view must resume observation without replacing the block");
                    helper.assertTrue(
                            celestialReserve(data, weaponId) > checkpoint.get(),
                            "Restoring the sky view must resume Celestial Energy delivery");
                })
                .thenExecute(() -> level.setDayTime(6_000L))
                .thenWaitUntil(() -> helper.assertFalse(
                        isObservatoryProducing(helper),
                        "A normal observable dimension must stop the observatory outside the night window"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        celestialReserve(data, weaponId),
                        checkpoint.get(),
                        "Daytime must not add Celestial Energy"))
                .thenExecute(() -> {
                    level.setDayTime(14_000L);
                    setClearWeather(level);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        isObservatoryProducing(helper),
                        "Returning to clear night conditions must resume observation"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(8)
                .thenExecute(() -> clearWeatherGain.set(celestialReserve(data, weaponId) - checkpoint.get()))
                .thenExecute(() -> setRain(level, false))
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.isRaining(), "Rain must be active before its output is measured");
                    helper.assertTrue(
                            isObservatoryProducing(helper),
                            "Rain must reduce output rather than stop observation");
                })
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(8)
                .thenExecute(() -> {
                    long rainGain = celestialReserve(data, weaponId) - checkpoint.get();
                    helper.assertTrue(rainGain > 0L, "Rain must still produce Celestial Energy");
                    helper.assertValueEqual(
                            rainGain * 4L,
                            clearWeatherGain.get(),
                            "Default rain output must be 25 percent of clear-weather output");
                })
                .thenExecute(() -> setRain(level, true))
                .thenWaitUntil(() -> helper.assertFalse(
                        isObservatoryProducing(helper),
                        "A thunderstorm must stop observation"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        celestialReserve(data, weaponId),
                        checkpoint.get(),
                        "A thunderstorm must not add Celestial Energy"))
                .thenExecute(() -> {
                    setClearWeather(level);
                    helper.assertTrue(
                            level.destroyBlock(helper.absolutePos(CREATIVE_ENERGY_CELL), false),
                            "The observatory's AE power source must be removable");
                })
                .thenWaitUntil(() -> helper.assertFalse(
                        isObservatoryProducing(helper),
                        "An observatory without sufficient AE grid power must stop"))
                .thenExecute(() -> checkpoint.set(celestialReserve(data, weaponId)))
                .thenIdle(5)
                .thenExecute(() -> helper.assertValueEqual(
                        celestialReserve(data, weaponId),
                        checkpoint.get(),
                        "An unpowered observatory must not add Celestial Energy"))
                .thenExecute(() -> placeRegisteredBlock(helper, CREATIVE_ENERGY_CELL, CREATIVE_ENERGY_CELL_ID))
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isObservatoryProducing(helper),
                            "Restoring AE power must recover the existing observatory");
                    helper.assertTrue(
                            celestialReserve(data, weaponId) > checkpoint.get(),
                            "Recovered observation must resume delivery to the same weapon reserve");
                })
                .thenExecute(() -> restoreEnvironment(level, originalDayTime, originalRaining, originalThundering))
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
            throw new IllegalStateException("Failed to place test block at " + absolutePos);
        }
        state.getBlock().setPlacedBy(level, absolutePos, state, placer, ItemStack.EMPTY);
    }

    private static void placeRegisteredBlock(
                                             GameTestHelper helper,
                                             BlockPos relativePos,
                                             ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        BlockPos absolutePos = helper.absolutePos(relativePos);
        if (block == Blocks.AIR ||
                !helper.getLevel().setBlock(absolutePos, block.defaultBlockState(), Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place registered block " + blockId + " at " + absolutePos);
        }
    }

    private static boolean isObservatoryProducing(GameTestHelper helper) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(OBSERVATORY));
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    private static long celestialReserve(OrbitalWeaponSavedData data, UUID weaponId) {
        return data.find(weaponId).orElseThrow().reserve().celestialEnergy();
    }

    private static void setClearWeather(ServerLevel level) {
        level.setWeatherParameters(6_000, 0, false, false);
    }

    private static void setRain(ServerLevel level, boolean thundering) {
        level.setWeatherParameters(0, 6_000, true, thundering);
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
