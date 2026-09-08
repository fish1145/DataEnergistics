package com.fish_dan_.data_energistics.orbital.attack.beam;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.List;

/**
 * Shared, world-independent scan geometry. A cursor counts voxel crossings on successive aimed rays, including
 * air. Prefix counts allow restart and visual lookup without storing every voxel. Layouts are immutable and the
 * bounded cache is shared safely by integrated-server and render threads; walkers belong to one caller.
 */
public final class OrbitalBeamScan {

    private static final int MAX_CACHED_LAYOUTS = 8;
    private static final Object2ObjectLinkedOpenHashMap<LayoutKey, Layout> LAYOUTS = new Object2ObjectLinkedOpenHashMap<>();
    private final BlockPos target;
    private final int topY;
    private final Layout layout;

    public OrbitalBeamScan(BlockPos target, int topY, int bottomY, int radius, OrbitalBeamPath path) {
        OrbitalDirectedEnergyStrike.validateSupportedRadius(radius);
        if (bottomY > topY || (long) topY - bottomY >= 4096) {
            throw new IllegalArgumentException("Invalid directed-energy world height");
        }
        this.target = target.immutable();
        this.topY = topY;
        this.layout = layout(new LayoutKey(radius, topY - bottomY + 1, path));
    }

    /** Fixed world-space muzzle; no scan offset is applied to its position. */
    public static Vec3 muzzle(BlockPos target, int topY) {
        return new Vec3(target.getX() + 0.5, topY + 97.0, target.getZ() + 0.5);
    }

    public long totalWork() {
        return this.layout.prefix()[this.layout.offsets().length];
    }

    /** Creates a mutable sequential walker at a valid, unfinished persisted work cursor. */
    public Walker walker(long cursor) {
        if (cursor < 0 || cursor >= totalWork()) {
            throw new IllegalArgumentException("Beam cursor is outside its scan");
        }
        int ray = Arrays.binarySearch(this.layout.prefix(), cursor);
        if (ray < 0) {
            ray = -ray - 2;
        }
        return new Walker(ray, cursor);
    }

    /** Returns the actual processed beam frontier, keeping its tip on the ray rather than a voxel's center. */
    public Segment beamAt(long cursor) {
        return walker(cursor).beam();
    }

    /**
     * Selects a bounded set of afterimages from the completed span. Every selected ray was actually processed;
     * the final ray is always included. The full cursor span remains authoritative even when trails are sampled.
     */
    public List<Segment> completedBeams(long from, long to, int maximum) {
        if (from < 0 || to < from || to > totalWork() || maximum < 1) {
            throw new IllegalArgumentException("Invalid completed beam span");
        }
        if (to == 0) {
            return List.of();
        }
        int first = walker(Math.min(from, to - 1)).ray;
        int last = walker(to - 1).ray;
        int count = Math.min(maximum, last - first + 1);
        List<Segment> result = new ObjectArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int ray = count == 1 ? last : first + (int) ((long) (last - first) * index / (count - 1));
            result.add(beamAt(Math.min(to, this.layout.prefix()[ray + 1]) - 1));
        }
        return List.copyOf(result);
    }

    private Vec3 world(Vec3 local) {
        return local.add(this.target.getX(), this.topY, this.target.getZ());
    }

    private static synchronized Layout layout(LayoutKey key) {
        Layout result = LAYOUTS.computeIfAbsent(key, OrbitalBeamScan::buildLayout);
        LAYOUTS.getAndMoveToLast(key);
        if (LAYOUTS.size() > MAX_CACHED_LAYOUTS) {
            LAYOUTS.removeFirst();
        }
        return result;
    }

    private static Layout buildLayout(LayoutKey key) {
        IntArrayList offsets = new IntArrayList(Math.toIntExact(OrbitalDirectedEnergyStrike.scheduledCoordinateCount(key.radius())));
        int x = 0, z = 0, directionX = 1, directionZ = 0, length = 1, progress = 0, segments = 0;
        int side = key.radius() * 2 + 1;
        for (int emitted = 0; emitted < side * side; emitted++) {
            if ((long) x * x + (long) z * z <= (long) key.radius() * key.radius()) {
                offsets.add(((x + 256) << 10) | (z + 256));
            }
            x += directionX;
            z += directionZ;
            if (++progress == length) {
                progress = 0;
                int rotatedX = -directionZ;
                directionZ = directionX;
                directionX = rotatedX;
                if (++segments % 2 == 0) {
                    length++;
                }
            }
        }
        long[] prefix = new long[offsets.size() + 1];
        Layout result = new Layout(offsets.toIntArray(), prefix, key.height(), key.path());
        for (int index = 0; index < offsets.size(); index++) {
            Ray ray = result.ray(index);
            BlockPos start = BlockPos.containing(ray.start());
            BlockPos end = BlockPos.containing(ray.end());
            prefix[index + 1] = prefix[index] + 1L + Math.abs(end.getX() - start.getX()) + Math.abs(end.getY() - start.getY()) + Math.abs(end.getZ() - start.getZ());
        }
        return result;
    }

    /** A beam segment in world coordinates. Both endpoints are derived from the same traversal as block work. */
    public record Segment(Vec3 origin, Vec3 tip) {}

    public final class Walker {

        private int ray;
        private long cursor;
        private Ray geometry;
        private BlockPos.MutableBlockPos position;
        private Vec3 delta;
        private double nextX, nextY, nextZ;

        private Walker(int ray, long cursor) {
            this.ray = ray;
            this.cursor = layout.prefix()[ray];
            startRay();
            while (this.cursor < cursor) {
                advance();
            }
        }

        private void startRay() {
            this.geometry = layout.ray(this.ray);
            this.position = BlockPos.containing(this.geometry.start()).mutable();
            this.delta = this.geometry.end().subtract(this.geometry.start());
            this.nextX = firstCrossing(this.geometry.start().x, this.delta.x);
            this.nextY = firstCrossing(this.geometry.start().y, this.delta.y);
            this.nextZ = firstCrossing(this.geometry.start().z, this.delta.z);
        }

        public BlockPos position() {
            return this.position.offset(target.getX(), topY, target.getZ());
        }

        public Segment beam() {
            double exit = Math.min(1.0, Math.min(this.nextX, Math.min(this.nextY, this.nextZ)));
            return new Segment(world(this.geometry.origin()), world(this.geometry.start().lerp(this.geometry.end(), exit)));
        }

        /** Advances exactly one counted voxel, with deterministic single-axis ordering at edge/corner ties. */
        public void advance() {
            this.cursor++;
            if (this.cursor == layout.prefix()[this.ray + 1]) {
                if (this.cursor < totalWork()) {
                    this.ray++;
                    startRay();
                }
                return;
            }
            if (this.nextX < this.nextY && this.nextX < this.nextZ) {
                this.position.move(Mth.sign(this.delta.x), 0, 0);
                this.nextX += Math.abs(1.0 / this.delta.x);
            } else if (this.nextY < this.nextZ) {
                this.position.move(0, Mth.sign(this.delta.y), 0);
                this.nextY += Math.abs(1.0 / this.delta.y);
            } else {
                this.position.move(0, 0, Mth.sign(this.delta.z));
                this.nextZ += Math.abs(1.0 / this.delta.z);
            }
        }
    }

    private static double firstCrossing(double start, double delta) {
        if (delta == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return ((delta > 0 ? Math.floor(start) + 1 : Math.floor(start)) - start) / delta;
    }

    private record LayoutKey(int radius, int height, OrbitalBeamPath path) {}

    private record Ray(Vec3 origin, Vec3 start, Vec3 end) {}

    private record Layout(int[] offsets, long[] prefix, int height, OrbitalBeamPath path) {

        private Ray ray(int index) {
            int packed = this.offsets[index];
            int x = (packed >>> 10) - 256;
            int z = (packed & 1023) - 256;
            Vec3 end = new Vec3(x + 0.5, 1.5 - this.height, z + 0.5);
            Vec3 origin = this.path == OrbitalBeamPath.AIMED_RAYS ? new Vec3(0.5, 97, 0.5) : new Vec3(x + 0.5, 97, z + 0.5);
            // The sky section is visual only; traversal starts just inside the top build layer.
            Vec3 start = origin.lerp(end, (96.0 + 1.0E-7) / (origin.y - end.y));
            return new Ray(origin, start, end);
        }
    }
}
