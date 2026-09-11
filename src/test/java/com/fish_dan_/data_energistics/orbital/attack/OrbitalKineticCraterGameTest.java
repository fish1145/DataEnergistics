package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry.KineticCraterProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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

    private static final BlockPos TARGET = new BlockPos(25, 20, 25);
    private static final List<BlockPos> EXCAVATED = List.of(
            TARGET, TARGET.below(8), TARGET.offset(9, -1, 0), TARGET.offset(6, -3, 0),
            TARGET.offset(4, -6, 0), TARGET.offset(6, -2, 6), TARGET.offset(0, -1, 10));
    private static final List<BlockPos> SLOPING_WALL = List.of(
            TARGET.offset(9, -3, 0), TARGET.offset(6, -5, 0), TARGET.offset(6, -3, 6));
    private static final List<BlockPos> OUTSIDE = List.of(
            TARGET.below(9), TARGET.offset(4, -7, 0), TARGET.offset(11, -1, 0), TARGET.offset(8, -1, 8));

    private OrbitalKineticCraterGameTest() {}

    @TestHolder("orbital_kinetic_bowl_preserves_sloping_walls_and_central_shaft_after_budget_resume")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 100)
    public static void bowlPreservesSlopingWallsAndCentralShaft(GameTestHelper helper) {
        CompoundTag geometryTag = legacyGeometryTag();
        geometryTag.putString("kinetic_crater_profile", KineticCraterProfile.BOWL.name());
        runCrater(helper, OrbitalAttackSavedData.readKineticGeometry(geometryTag), true);
    }

    @TestHolder("orbital_kinetic_saved_attack_without_profile_keeps_original_cylindrical_excavation")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 100)
    public static void preUpgradeAttackKeepsCylindricalExcavation(GameTestHelper helper) {
        runCrater(helper, OrbitalAttackSavedData.readKineticGeometry(legacyGeometryTag()), false);
    }

    private static void runCrater(GameTestHelper helper, OrbitalAttackGeometry.Kinetic geometry, boolean bowl) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        int radius = geometry.terrainRadius();
        for (int chunkX = Math.floorDiv(target.getX() - radius, 16); chunkX <= Math.floorDiv(target.getX() + radius, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(target.getZ() - radius, 16); chunkZ <= Math.floorDiv(target.getZ() + radius, 16); chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        for (List<BlockPos> markers : List.of(EXCAVATED, SLOPING_WALL, OUTSIDE)) {
            for (BlockPos marker : markers) {
                helper.setBlock(marker, Blocks.STONE);
            }
        }
        var first = OrbitalKineticStrike.applyBudget(level, target, geometry, 0, 257,
                chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
        CompoundTag persistedProgress = new CompoundTag();
        persistedProgress.putLong("work_cursor", first.nextCursor());
        AtomicReference<OrbitalKineticStrike.WorkSlice> work = new AtomicReference<>(first);
        helper.startSequence().thenIdle(2).thenExecuteFor(60, () -> {
            if (!work.get().complete()) {
                var next = OrbitalKineticStrike.applyBudget(level, target, geometry,
                        persistedProgress.getLong("work_cursor"), 257,
                        chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null);
                work.set(next);
                persistedProgress.putLong("work_cursor", next.nextCursor());
            }
        }).thenExecute(() -> {
            helper.assertTrue(work.get().complete(), "Budgeted work must finish after resuming its saved cursor");
            for (BlockPos marker : EXCAVATED) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(marker)).isAir(),
                        "The central shaft and inner crater must be excavated at " + marker);
            }
            for (BlockPos marker : SLOPING_WALL) {
                helper.assertTrue(bowl ? level.getBlockState(helper.absolutePos(marker)).is(Blocks.STONE) : level.getBlockState(helper.absolutePos(marker)).isAir(),
                        "The decoded crater profile must control the actual sidewall at " + marker);
            }
            for (BlockPos marker : OUTSIDE) {
                helper.assertTrue(level.getBlockState(helper.absolutePos(marker)).is(Blocks.STONE),
                        "The captured radius and depth must preserve outside terrain at " + marker);
            }
        }).thenSucceed();
    }

    private static CompoundTag legacyGeometryTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("kinetic_column_radius", 2);
        tag.putInt("kinetic_column_depth", 8);
        tag.putInt("kinetic_crater_radius", 10);
        tag.putInt("kinetic_crater_depth", 6);
        tag.putInt("kinetic_shockwave_radius", 12);
        tag.putLong("kinetic_entity_damage", 500);
        tag.putDouble("kinetic_knockback_strength", 4);
        return tag;
    }
}
