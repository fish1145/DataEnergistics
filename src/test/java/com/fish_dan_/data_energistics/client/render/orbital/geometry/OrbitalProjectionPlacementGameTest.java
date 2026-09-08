package com.fish_dan_.data_energistics.client.render.orbital.geometry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalProjectionPlacement.Detail;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalProjectionPlacementGameTest {

    private OrbitalProjectionPlacementGameTest() {}

    @TestHolder("orbital_lod_preserves_distance_boundaries_and_detail_budget")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void lodHonorsBoundariesAndFullDetailBudget(GameTestHelper helper) {
        helper.assertTrue(OrbitalProjectionPlacement.detail(0, true) == Detail.FULL, "Zero distance uses full geometry");
        helper.assertTrue(OrbitalProjectionPlacement.detail(1024.0 * 1024, true) == Detail.FULL, "The full boundary is inclusive");
        helper.assertTrue(OrbitalProjectionPlacement.detail(1024.0 * 1024 + 1, true) == Detail.REDUCED, "Past 1024 uses reduced geometry");
        helper.assertTrue(OrbitalProjectionPlacement.detail(0, false) == Detail.REDUCED, "Exhausted full detail budget uses reduced geometry");
        helper.assertTrue(OrbitalProjectionPlacement.detail(4096.0 * 4096, true) == Detail.REDUCED, "The reduced boundary is inclusive");
        helper.assertTrue(OrbitalProjectionPlacement.detail(4096.0 * 4096 + 1, true) == Detail.DISTANT, "Past 4096 uses distant geometry");
        helper.succeed();
    }

    @TestHolder("orbital_far_projection_preserves_angular_size_and_culling_bounds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void distantProjectionRetainsAngularSizeAndAllBounds(GameTestHelper helper) {
        Vec3 camera = new Vec3(29_999_000, 64, -29_999_000);
        Vec3 origin = camera.add(8000, 576, 4000);
        AABB local = new AABB(-266, -576, -64, 266, 64, 64);
        OrbitalProjectionPlacement placed = OrbitalProjectionPlacement.create(camera, origin, local, 768);
        helper.assertTrue(placed.scale() > 0 && placed.scale() < 1, "The distant construct must be brought inside the far plane");
        helper.assertTrue(placed.cameraOffset().normalize().distanceTo(origin.subtract(camera).normalize()) < 1.0E-10,
                "Depth compression must preserve the direction to the construct");
        double originalRatio = local.getXsize() / origin.distanceTo(camera);
        double placedRatio = placed.bounds().getXsize() / placed.cameraOffset().length();
        helper.assertTrue(Math.abs(originalRatio - placedRatio) < 1.0E-9, "Depth compression must preserve apparent width");
        for (double x : new double[] { local.minX, local.maxX }) {
            for (double y : new double[] { local.minY, local.maxY }) {
                for (double z : new double[] { local.minZ, local.maxZ }) {
                    Vec3 corner = new Vec3(x, y, z).scale(placed.scale()).add(placed.cameraOffset());
                    helper.assertTrue(corner.length() < 768 * 0.81, "All visible geometry must remain inside the far plane");
                    helper.assertTrue(placed.bounds().inflate(1.0E-6).contains(camera.add(corner)),
                            "Culling bounds must include the actual transformed corners, including the beacon link");
                }
            }
        }
        helper.succeed();
    }

    @TestHolder("orbital_near_projection_keeps_world_scale")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void nearbyGeometryIsNotScaled(GameTestHelper helper) {
        AABB local = new AABB(-1, -1, -1, 1, 1, 1);
        Vec3 origin = new Vec3(0, 10, 20);
        OrbitalProjectionPlacement placed = OrbitalProjectionPlacement.create(Vec3.ZERO, origin, local, 768);
        helper.assertTrue(placed.scale() == 1, "Nearby geometry must retain world scale");
        helper.assertTrue(placed.cameraOffset().equals(origin), "Nearby placement must retain its exact position");
        helper.succeed();
    }
}
