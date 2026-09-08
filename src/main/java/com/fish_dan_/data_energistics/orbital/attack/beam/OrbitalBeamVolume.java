package com.fish_dan_.data_energistics.orbital.attack.beam;

import com.fish_dan_.data_energistics.orbital.attack.entity.geometry.OrbitalEntityHitGeometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The finite oriented square prism drawn by the outer beam shell. Its width and orientation are shared with the
 * renderer. Separating-axis tests keep diagonal rays exact and do not extend damage beyond the processed end plane.
 */
public final class OrbitalBeamVolume {

    public static final float RADIUS = 3.0F;
    private static final double HALF_WIDTH = RADIUS / Math.sqrt(2);
    private static final Vec3[] WORLD_AXES = { new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1) };
    private final Vec3 center;
    private final Vec3[] axes;
    private final Vec3[] halfAxes;
    private final AABB bounds;

    public OrbitalBeamVolume(OrbitalBeamScan.Segment beam) {
        Vec3 delta = beam.tip().subtract(beam.origin());
        double length = delta.length();
        if (length <= 0) {
            throw new IllegalArgumentException("A damaging beam must have a nonzero length");
        }
        Quaternionf rotation = orientation(delta.normalize());
        Vec3 y = delta.scale(1.0 / length);
        Vec3 rotatedX = vector(rotation.transform(new Vector3f(1, 0, 0)));
        Vec3 x = rotatedX.subtract(y.scale(rotatedX.dot(y))).normalize();
        Vec3 z = x.cross(y).normalize();
        this.center = beam.origin().add(delta.scale(0.5));
        this.axes = new Vec3[] { x, y, z };
        this.halfAxes = new Vec3[] { x.scale(HALF_WIDTH), delta.scale(0.5), z.scale(HALF_WIDTH) };
        double extentX = Math.abs(this.halfAxes[0].x) + Math.abs(this.halfAxes[1].x) + Math.abs(this.halfAxes[2].x);
        double extentY = Math.abs(this.halfAxes[0].y) + Math.abs(this.halfAxes[1].y) + Math.abs(this.halfAxes[2].y);
        double extentZ = Math.abs(this.halfAxes[0].z) + Math.abs(this.halfAxes[1].z) + Math.abs(this.halfAxes[2].z);
        this.bounds = new AABB(this.center.subtract(extentX, extentY, extentZ), this.center.add(extentX, extentY, extentZ))
                .inflate(OrbitalEntityHitGeometry.CONTACT_EPSILON);
    }

    /** Maps the authored +Y beam axis to the same direction used for its collision prism. */
    public static Quaternionf orientation(Vec3 direction) {
        double horizontal = Math.hypot(direction.x, direction.z);
        if (horizontal == 0) {
            return direction.y < 0 ? new Quaternionf(1, 0, 0, 0) : new Quaternionf();
        }
        // Avoid rotationTo's near-antiparallel fallback, which flattens small but visible downward aim angles.
        double halfAngle = Math.atan2(horizontal, direction.y) * 0.5;
        double sine = Math.sin(halfAngle) / horizontal;
        return new Quaternionf((float) (direction.z * sine), 0, (float) (-direction.x * sine), (float) Math.cos(halfAngle)).normalize();
    }

    public AABB bounds() {
        return this.bounds;
    }

    public boolean intersects(AABB body) {
        Vec3 offset = body.getCenter().subtract(this.center);
        Vec3 halfBody = new Vec3(body.getXsize() * 0.5, body.getYsize() * 0.5, body.getZsize() * 0.5);
        for (Vec3 worldAxis : WORLD_AXES) {
            if (separated(offset, halfBody, worldAxis)) {
                return false;
            }
        }
        for (Vec3 beamAxis : this.axes) {
            if (separated(offset, halfBody, beamAxis)) {
                return false;
            }
            for (Vec3 worldAxis : WORLD_AXES) {
                if (separated(offset, halfBody, worldAxis.cross(beamAxis))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean separated(Vec3 offset, Vec3 halfBody, Vec3 axis) {
        double lengthSquared = axis.lengthSqr();
        if (lengthSquared < 1.0E-14) {
            return false;
        }
        double bodyRadius = halfBody.x * Math.abs(axis.x) + halfBody.y * Math.abs(axis.y) + halfBody.z * Math.abs(axis.z);
        double beamRadius = Math.abs(this.halfAxes[0].dot(axis)) + Math.abs(this.halfAxes[1].dot(axis)) + Math.abs(this.halfAxes[2].dot(axis));
        return Math.abs(offset.dot(axis)) > bodyRadius + beamRadius + OrbitalEntityHitGeometry.CONTACT_EPSILON * Math.sqrt(lengthSquared);
    }

    private static Vec3 vector(Vector3f vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }
}
