package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry.KineticCraterProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalKineticCraterGameTest {

    private OrbitalKineticCraterGameTest() {}

    @TestHolder("orbital_kinetic_captured_crater_removes_upper_terrain_and_resumes_without_height_drift")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 100)
    public static void capturedCraterRemovesUpperTerrainAndResumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        List<BlockPos> excavated = List.of(target, target.below(8), target.offset(3, 4, 0),
                target.offset(-3, 2, 0), target.offset(4, -1, 0));
        List<BlockPos> retained = List.of(target.below(9), target.offset(9, -5, 0),
                target.offset(11, 2, 0), target.offset(8, 4, 8));
        for (BlockPos pos : excavated) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos pos : retained) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        CompoundTag saved = geometryTag(target.getY() + 4);
        OrbitalAttackGeometry.Kinetic geometry = OrbitalAttackSavedData.readKineticGeometry(saved);
        long total = OrbitalKineticStrike.totalWork(level, target, geometry);
        var waiting = OrbitalKineticStrike.applyBudget(level, target, geometry, 0, 257, chunk -> false);
        helper.assertValueEqual(waiting.nextCursor(), 0L, "Unready initial chunks must not consume pending work");
        var first = OrbitalKineticStrike.applyBudget(level, target, geometry, 0, 257, chunk -> true);
        saved.putLong("work_cursor", first.nextCursor());
        AtomicReference<OrbitalKineticStrike.WorkSlice> work = new AtomicReference<>(first);
        helper.startSequence().thenIdle(2).thenExecuteFor(32, () -> {
            if (work.get().complete()) return;
            // Reload the actual current geometry and cursor between budgets, after earlier work changed the world.
            var restored = OrbitalAttackSavedData.readKineticGeometry(saved);
            long cursor = saved.getLong("work_cursor");
            var next = OrbitalKineticStrike.applyBudget(level, target, restored, cursor, 257, chunk -> true);
            helper.assertTrue(next.nextCursor() - cursor <= 257, "Resume must obey the caller's work budget");
            helper.assertValueEqual(next.totalWork(), total, "Excavation must not move the frozen top or alter the cursor span");
            work.set(next);
            saved.putLong("work_cursor", next.nextCursor());
        }).thenExecute(() -> {
            helper.assertTrue(work.get().complete(), "Captured terrain work must complete");
            for (BlockPos pos : excavated) helper.assertTrue(level.getBlockState(pos).isAir(),
                    "Upper terrain and the interior of the captured crater must be removed at " + pos);
            for (BlockPos pos : retained) helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE),
                    "Outer terrain, deep floor and lower sidewall must be retained at " + pos);
        }).thenSucceed();
    }

    @TestHolder("orbital_kinetic_current_rim_is_irregular_but_keeps_its_outer_bound")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void currentRimIsIrregularButBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = target(helper);
        int top = target.getY() + 4;
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                level.setBlock(new BlockPos(target.getX() + x, top, target.getZ() + z),
                        Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        var geometry = OrbitalAttackSavedData.readKineticGeometry(geometryTag(top));
        var slice = OrbitalKineticStrike.applyBudget(level, target, geometry, 0, 10_000, chunk -> true);
        helper.assertTrue(slice.complete(), "The current captured terrain stream must complete");
        boolean asymmetricRim = false;
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                boolean cleared = level.getBlockState(new BlockPos(target.getX() + x, top, target.getZ() + z)).isAir();
                int distance = x * x + z * z;
                if (distance <= 64) helper.assertTrue(cleared, "Perturbations must leave a connected inner opening");
                if (distance > 100) helper.assertFalse(cleared, "Roughness must not escape the captured disk");
                if (distance > 64 && distance <= 100) {
                    boolean opposite = level.getBlockState(new BlockPos(target.getX() - x, top, target.getZ() - z)).isAir();
                    asymmetricRim |= cleared != opposite;
                }
            }
        }
        helper.assertTrue(asymmetricRim, "The new rim must not regress to a perfect symmetric circle");
        helper.succeed();
    }

    private static BlockPos target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(25, 20, 25));
        BlockPos target = new BlockPos(origin.getX(), level.getMaxBuildHeight() - 32, origin.getZ());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) level.getChunkAt(target.offset(x * 16, 0, z * 16));
        }
        return target;
    }

    private static CompoundTag geometryTag(int top) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("kinetic_column_radius", 2);
        tag.putInt("kinetic_column_depth", 8);
        tag.putInt("kinetic_crater_radius", 10);
        tag.putInt("kinetic_crater_depth", 6);
        tag.putInt("kinetic_shockwave_radius", 12);
        tag.putString("kinetic_crater_profile", KineticCraterProfile.BOWL.name());
        tag.putInt("kinetic_crater_top", top);
        return tag;
    }
}
