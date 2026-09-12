package com.fish_dan_.data_energistics.orbital.attack.entity.geometry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry.KineticCraterProfile;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalKineticStrike;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamVolume;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalEntityHitGeometryGameTest {

    private static final BlockPos TARGET = new BlockPos(25, 12, 25);

    private OrbitalEntityHitGeometryGameTest() {}

    @TestHolder("orbital_beam_prism_matches_visible_width_and_flat_processed_end")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_hit_geometry")
    public static void beamPrismHasRealWidthAndFiniteEnds(GameTestHelper helper) {
        OrbitalBeamVolume vertical = new OrbitalBeamVolume(new OrbitalBeamScan.Segment(new Vec3(0, 10, 0), Vec3.ZERO));
        helper.assertTrue(vertical.intersects(new AABB(2.0, 2, -0.3, 2.6, 3.8, 0.3)),
                "A player's intersecting shoulder must count even when its center is outside the shell");
        helper.assertFalse(vertical.intersects(new AABB(2.2, 2, -0.3, 2.8, 3.8, 0.3)),
                "A body outside the actual square shell must not be hit by its broad-phase bounds");
        helper.assertFalse(vertical.intersects(new AABB(-0.3, -2, -0.3, 0.3, -0.01, 0.3)),
                "Beam width must not add a rounded damage cap beyond the processed frontier");
        helper.assertTrue(vertical.intersects(new AABB(-0.3, -1, -0.3, 0.3, 0, 0.3)),
                "Contact with the flat end plane must count");
        OrbitalBeamVolume diagonal = new OrbitalBeamVolume(new OrbitalBeamScan.Segment(new Vec3(0, 10, 0), new Vec3(10, 0, 0)));
        helper.assertTrue(diagonal.intersects(new AABB(3.7, 3.1, -0.3, 4.3, 4.9, 0.3)), "A slanted shell must hit an intersecting body");
        helper.assertFalse(diagonal.intersects(new AABB(-0.3, -0.9, -0.3, 0.3, 0.9, 0.3)),
                "A diagonal beam's enclosing AABB must not create false hits far from the beam");
        helper.assertFalse(diagonal.intersects(new AABB(10.8, -1.2, -0.2, 11.2, -0.8, 0.2)),
                "A slanted beam must also stop at its actual endpoint");
        Vec3 slightAim = new Vec3(1, -4000, 0).normalize();
        Vector3f direction = OrbitalBeamVolume.orientation(slightAim).transform(new Vector3f(0, 1, 0));
        helper.assertTrue(Math.abs(direction.x - slightAim.x) < 1.0E-6,
                "A shallow aim offset must not be flattened to a vertical beam");
        helper.succeed();
    }

    @TestHolder("orbital_beam_hits_players_along_its_full_width_and_held_shaft")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_hit_geometry")
    public static void beamHitsTheWholeVisibleShaft(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 center = Vec3.atCenterOf(target);
        var geometry = new OrbitalAttackGeometry.DirectedEnergy(1, OrbitalDirectedEnergyDepth.DEPTH_32, 8);
        OrbitalErasureStrike strike = strike();
        long cursor = level.getMaxBuildHeight() - 1L - (target.getY() - 1L);
        GameTestPlayer edge = player(helper, center.add(2.3, 12, 0));
        GameTestPlayer outside = player(helper, center.add(2.5, 12, 0));
        GameTestPlayer sky = player(helper, new Vec3(center.x, level.getMaxBuildHeight() + 32, center.z));
        GameTestPlayer below = player(helper, center.add(0, -4, 0));
        helper.setBlock(TARGET.below(), Blocks.STONE);
        helper.setBlock(TARGET.below(2), Blocks.STONE);
        var slice = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, cursor, strike, 1, chunk -> true);
        helper.assertFalse(edge.isAlive(), "The visible beam width must hit a body far above the currently processed voxel");
        helper.assertFalse(sky.isAlive(), "The beam's sky section must hit flying players above build height");
        helper.assertTrue(outside.isAlive() && below.isAlive(), "Outside bodies and bodies beyond the frontier must survive");
        helper.assertTrue(level.getBlockState(helper.absolutePos(TARGET.below(2))).is(Blocks.STONE),
                "Entity beam width must not advance the terrain cursor");
        GameTestPlayer enteringHeldBeam = player(helper, center.add(0, 8, 0));
        var waiting = OrbitalDirectedEnergyStrike.applyBudget(level, target, geometry, slice.nextCursor(), strike, 1, chunk -> false);
        helper.assertValueEqual(waiting.nextCursor(), slice.nextCursor(), "A waiting beam must keep its terrain cursor");
        helper.assertFalse(enteringHeldBeam.isAlive(), "A player entering the already visible shaft must be hit while its next chunk is pending");
        GameTestPlayer budgetWait = player(helper, center.add(0, 6, 0));
        OrbitalDirectedEnergyStrike.eraseCurrentBeam(level, target, geometry, waiting.nextCursor(), strike);
        helper.assertFalse(budgetWait.isAlive(), "The held beam must also retain collision while waiting for terrain budget");
        helper.assertTrue(outside.isAlive() && below.isAlive(), "Holding the beam must not enlarge its finite prism");
        helper.succeed();
    }

    @TestHolder("orbital_kinetic_hits_intersecting_bodies_and_the_entire_penetration_column")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_hit_geometry")
    public static void kineticIncludesBodyContactAndPenetration(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 center = Vec3.atCenterOf(target);
        var geometry = new OrbitalAttackGeometry.Kinetic(1, 8, 1, 1, 4, KineticCraterProfile.BOWL);
        GameTestPlayer edge = player(helper, center.add(4.25, -0.9, 0));
        GameTestPlayer outside = player(helper, center.add(4.4, -0.9, 0));
        GameTestPlayer columnEdge = player(helper, center.add(1.75, 12, 0));
        GameTestPlayer columnOutside = player(helper, center.add(1.85, 12, 0));
        GameTestPlayer bottomContact = player(helper, new Vec3(center.x, target.getY() - 9.5, center.z));
        GameTestPlayer below = player(helper, new Vec3(center.x, target.getY() - 10, center.z));
        OrbitalKineticStrike.eraseImpactEntities(level, target, geometry, strike());
        helper.assertFalse(edge.isAlive(), "A body touching the shock sphere must be hit even with its feet point outside");
        helper.assertFalse(columnEdge.isAlive(), "The falling mass must hit the outer block-cell edge of the penetration column");
        helper.assertFalse(bottomContact.isAlive(), "Head contact with the bottom of the penetration column must count");
        helper.assertTrue(outside.isAlive() && columnOutside.isAlive() && below.isAlive(), "Bodies outside all real kinetic volumes must survive");
        helper.succeed();
    }

    @TestHolder("orbital_kinetic_crater_collision_respects_captured_bowl_profile")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_hit_geometry")
    public static void craterContactUsesItsActualProfile(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(TARGET);
        BlockPos target = new BlockPos(base.getX(), helper.getLevel().getMaxBuildHeight() - 32, base.getZ());
        Vec3 center = Vec3.atCenterOf(target);
        var bowl = new OrbitalAttackGeometry.Kinetic(1, 1, 4, 4, 1, KineticCraterProfile.BOWL, target.getY() + 4);
        GameTestPlayer crater = player(helper, new Vec3(center.x + 2, target.getY() + 3, center.z));
        GameTestPlayer retainedWall = player(helper, new Vec3(center.x + 3, target.getY() - 4.9, center.z));
        GameTestPlayer cornerOutside = player(helper, new Vec3(center.x + 3.3, target.getY() - 1, center.z + 3.3));
        OrbitalKineticStrike.eraseImpactEntities(helper.getLevel(), target, bowl, strike());
        helper.assertFalse(crater.isAlive(), "Actual crater excavation must hit bodies even outside a smaller configured shock sphere");
        helper.assertTrue(retainedWall.isAlive(), "The retained lower bowl wall must not become an oversized cylindrical hit volume");
        helper.assertTrue(cornerOutside.isAlive(), "The crater bounding square must not hit outside the disk");
        helper.succeed();
    }

    private static GameTestPlayer player(GameTestHelper helper, Vec3 position) {
        GameTestPlayer player = new ExtendedGameTestHelper(helper.testInfo).makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        player.moveTo(position);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        return player;
    }

    private static OrbitalErasureStrike strike() {
        return new OrbitalErasureStrike(UUID.randomUUID(), null, Set.of());
    }
}
