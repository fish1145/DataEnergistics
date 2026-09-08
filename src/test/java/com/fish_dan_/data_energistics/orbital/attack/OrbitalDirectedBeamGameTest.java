package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.network.orbital.visual.OrbitalAttackVisualsPayload;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamPath;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamSweep;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalDirectedBeamGameTest {

    private OrbitalDirectedBeamGameTest() {}

    @TestHolder("orbital_aimed_beam_erases_only_visited_ray_voxels_and_resumes_after_chunk_wait")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 100)
    public static void aimedBeamResumesOnlyItsPhysicalRay(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(25, 20, 25));
        CompoundTag saved = geometryTag();
        saved.putString("beam_path", OrbitalBeamPath.AIMED_RAYS.name());
        var geometry = OrbitalAttackSavedData.readDirectedEnergyGeometry(saved);
        OrbitalBeamScan scan = OrbitalDirectedEnergyStrike.scan(level, target, geometry);
        // Resume in the final slanted ray, far from its first voxel and before reaching the ground.
        long from = scan.totalWork() - (level.getMaxBuildHeight() - geometry.bottomY(level, target.getY())) / 2;
        OrbitalBeamScan.Walker walker = scan.walker(from);
        List<BlockPos> visited = new ObjectArrayList<>();
        Set<BlockPos> unique = new ObjectOpenHashSet<>();
        Vec3 muzzle = OrbitalBeamScan.muzzle(target, level.getMaxBuildHeight() - 1);
        var fullRay = scan.beamAt(scan.totalWork() - 1);
        for (long cursor = from; cursor < scan.totalWork(); cursor++) {
            BlockPos position = walker.position();
            helper.assertTrue(unique.add(position), "A ray must not visit the same voxel twice");
            helper.assertTrue(new AABB(position).inflate(1.0E-6).clip(muzzle, fullRay.tip()).isPresent(),
                    "Every erased voxel must intersect the displayed slanted ray: " + position);
            helper.assertTrue(walker.beam().origin().distanceToSqr(muzzle) < 1.0E-12,
                    "Every frontier must retain the fixed muzzle");
            level.getChunkAt(position);
            level.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            visited.add(position);
            walker.advance();
        }
        helper.assertTrue(visited.getFirst().getX() != visited.getLast().getX() || visited.getFirst().getZ() != visited.getLast().getZ(), "The regression fixture must exercise a slanted ray");
        BlockPos untouched = visited.getFirst().offset(3, 0, 0);
        helper.assertFalse(unique.contains(untouched), "The decoy must lie outside the ray");
        level.setBlock(untouched, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        ItemEntity hit = marker(helper, visited.getFirst());
        ItemEntity missed = marker(helper, untouched);
        OrbitalErasureStrike strike = new OrbitalErasureStrike(UUID.randomUUID(), null, Set.of());
        var waiting = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, from, strike, 1, chunk -> false);
        helper.assertValueEqual(waiting.nextCursor(), from, "A pending chunk must consume no cursor work");
        helper.assertTrue(level.getBlockState(visited.getFirst()).is(Blocks.STONE) && !hit.isRemoved(),
                "Neither blocks nor entities may be touched before the chunk is ready");
        var first = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, from, strike, 1, chunk -> true);
        helper.assertTrue(hit.isRemoved() && !missed.isRemoved(), "First contact must erase only the beam's occupied voxel");
        helper.assertTrue(level.getBlockState(visited.get(1)).is(Blocks.STONE), "The next unvisited voxel must remain intact");
        saved.putLong("work_cursor", first.nextCursor());
        saved.put("erasure_journal", strike.save());
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var restored = OrbitalAttackSavedData.readDirectedEnergyGeometry(saved);
            var restoredStrike = OrbitalErasureStrike.load(strike.strikeId(), Set.of(), saved.getCompound("erasure_journal"));
            long cursor = saved.getLong("work_cursor");
            while (cursor < scan.totalWork()) {
                var slice = OrbitalDirectedEnergyStrike.applyBudget(level, target, restored, cursor, restoredStrike, 7, chunk -> true);
                helper.assertTrue(slice.nextCursor() - cursor <= 7, "Resume must honor its mutation budget");
                cursor = slice.nextCursor();
            }
            for (BlockPos position : visited) {
                helper.assertTrue(level.getBlockState(position).isAir(), "Resumed ray must clear its visited terrain");
            }
            helper.assertTrue(level.getBlockState(untouched).is(Blocks.STONE) && !missed.isRemoved(),
                    "Off-ray terrain and entities must survive the whole resumed slice");
            missed.discard();
        }).thenSucceed();
    }

    @TestHolder("orbital_legacy_beam_cursor_retains_vertical_columns")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void legacyCursorRetainsVerticalColumns(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(25, 20, 25));
        var geometry = OrbitalAttackSavedData.readDirectedEnergyGeometry(geometryTag());
        int height = level.getMaxBuildHeight() - geometry.bottomY(level, target.getY());
        long cursor = height + 5L;
        BlockPos oldPosition = new BlockPos(target.getX() + 1, level.getMaxBuildHeight() - 6, target.getZ());
        helper.assertValueEqual(geometry.path(), OrbitalBeamPath.VERTICAL_COLUMNS, "Missing NBT path must select the old format");
        helper.assertValueEqual(OrbitalDirectedEnergyStrike.totalWork(level, target, geometry),
                height * OrbitalDirectedEnergyStrike.scheduledCoordinateCount(geometry.radius()), "Old work totals must not change");
        level.setBlock(oldPosition, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(oldPosition.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, cursor,
                new OrbitalErasureStrike(UUID.randomUUID(), null, Set.of()), 1, chunk -> true);
        helper.assertTrue(level.getBlockState(oldPosition).isAir() && level.getBlockState(oldPosition.below()).is(Blocks.STONE),
                "A restored legacy cursor must process precisely its old voxel");
        helper.succeed();
    }

    @TestHolder("orbital_beam_visual_codec_preserves_completed_span_and_final_frontier")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void codecPreservesCompletedSpan(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(25, 20, 25));
        var geometry = new OrbitalAttackGeometry.DirectedEnergy(8, OrbitalDirectedEnergyDepth.DEPTH_32, 4);
        var level = helper.getLevel();
        OrbitalBeamScan scan = OrbitalDirectedEnergyStrike.scan(level, target, geometry);
        long end = scan.totalWork();
        OrbitalBeamSweep sweep = new OrbitalBeamSweep(level.getMaxBuildHeight() - 1, geometry.bottomY(level, target.getY()),
                geometry.path(), end - 512);
        var snapshot = new OrbitalAttackVisualSnapshot(UUID.randomUUID(), OrbitalAttackMode.DIRECTED_ENERGY,
                level.dimension().location(), target, scan.walker(end - 1).position(), geometry.radius(),
                OrbitalAttackPhase.DELIVERY, 10, 42, end, end, sweep);
        var payload = OrbitalAttackVisualsPayload.batches(40, level.dimension().location(), List.of(snapshot)).getFirst();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            OrbitalAttackVisualsPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = OrbitalAttackVisualsPayload.STREAM_CODEC.decode(buffer);
            helper.assertValueEqual(decoded, payload, "Wire round trip must preserve the authoritative span");
            var trails = sweep.scan(target, geometry.radius()).completedBeams(sweep.fromCursor(), end, 32);
            helper.assertValueEqual(trails.getLast(), scan.beamAt(end - 1), "Final frame must end on the last completed voxel");
            helper.assertTrue(trails.size() > 1, "A multi-ray tick must expose its completed sweep");
            helper.assertValueEqual(scan.completedBeams(end, end, 32), List.of(scan.beamAt(end - 1)),
                    "A paused snapshot must hold the last beam and never advance independently");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static ItemEntity marker(GameTestHelper helper, BlockPos position) {
        ItemEntity item = new ItemEntity(helper.getLevel(), position.getX() + 0.5, position.getY() + 0.4,
                position.getZ() + 0.5, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static CompoundTag geometryTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("geometry_radius", 8);
        tag.putString("geometry_depth", OrbitalDirectedEnergyDepth.DEPTH_32.name());
        tag.putInt("geometry_depth_blocks", 4);
        return tag;
    }
}
