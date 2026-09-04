package com.fish_dan_.data_energistics.block.beam;

import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Server-safe collision geometry in the same north-facing coordinates as the block models. */
final class BeamFormerShapes {

    private static final double MODEL_SCALE = 16.0;
    private static final double SLICE_SIZE = 0.25;
    private static final VoxelShape[] DIRECTIONAL = orientations(directionalNorth());
    private static final VoxelShape[] OMNI = orientations(omniNorth());

    private BeamFormerShapes() {}

    static VoxelShape forFacing(BeamDeviceKind kind, Direction facing) {
        return (kind == BeamDeviceKind.OMNI ? OMNI : DIRECTIONAL)[facing.get3DDataValue()];
    }

    private static VoxelShape directionalNorth() {
        List<AABB> boxes = new ObjectArrayList<>();
        // me_beam_former.json: zero-thickness light faces have no collision volume.
        boxes.add(new AABB(4, 4, 9, 12, 12, 16));
        boxes.add(new AABB(3, 3, 12, 13, 13, 15));
        addRotated(boxes, new AABB(3, 5, 9, 5, 11, 12), Direction.Axis.Y, 12, 3, -22.5);
        addRotated(boxes, new AABB(11, 5, 9, 13, 11, 12), Direction.Axis.Y, 12, 13, 22.5);
        addRotated(boxes, new AABB(5, 3, 9, 11, 5, 12), Direction.Axis.X, 3.04, 12, 22.5);
        addRotated(boxes, new AABB(5, 11, 9, 11, 13, 12), Direction.Axis.X, 13, 12, -22.5);
        // The reversed winding of core_outline does not reverse its occupied volume; it encloses core.
        addRotated(boxes, new AABB(6.55, 6.55, 5.05, 9.95, 9.95, 8.45), Direction.Axis.X, 8.75, 6.25, 45);
        boxes.add(new AABB(11, 7, 3.5, 12, 9, 9));
        boxes.add(new AABB(7, 11, 3.5, 9, 12, 9));
        boxes.add(new AABB(4, 7, 3.5, 5, 9, 9));
        boxes.add(new AABB(7, 4, 3.5, 9, 5, 9));
        boxes.add(new AABB(4, 5, 7, 5, 7, 8));
        boxes.add(new AABB(5, 4, 7, 7, 5, 8));
        boxes.add(new AABB(9, 4, 7, 11, 5, 8));
        boxes.add(new AABB(11, 5, 7, 12, 7, 8));
        boxes.add(new AABB(11, 9, 7, 12, 11, 8));
        boxes.add(new AABB(9, 11, 7, 11, 12, 8));
        boxes.add(new AABB(5, 11, 7, 7, 12, 8));
        boxes.add(new AABB(4, 9, 7, 5, 11, 8));
        boxes.add(new AABB(4, 5, 5, 5, 7, 6));
        boxes.add(new AABB(5, 4, 5, 7, 5, 6));
        boxes.add(new AABB(4, 4, 5, 5, 5, 9));
        boxes.add(new AABB(9, 4, 5, 11, 5, 6));
        boxes.add(new AABB(11, 5, 5, 12, 7, 6));
        boxes.add(new AABB(11, 4, 5, 12, 5, 9));
        boxes.add(new AABB(11, 9, 5, 12, 11, 6));
        boxes.add(new AABB(9, 11, 5, 11, 12, 6));
        boxes.add(new AABB(5, 11, 5, 7, 12, 6));
        boxes.add(new AABB(4, 9, 5, 5, 11, 6));
        boxes.add(new AABB(4, 11, 5, 5, 12, 9));
        boxes.add(new AABB(11, 11, 5, 12, 12, 9));
        return combineModelBoxes(boxes);
    }

    private static VoxelShape omniNorth() {
        // me_omni_beam_former.json: keep the openings between the two rings and the central core.
        return combineModelBoxes(List.of(
                new AABB(6, 6, 9, 10, 10, 14),
                new AABB(5, 5, 14, 11, 11, 15),
                new AABB(4, 4, 15, 12, 12, 16),
                new AABB(6.3, 6.3, 2.8, 9.7, 9.7, 6.2),
                new AABB(4, 5, 6, 5, 11, 7),
                new AABB(10.5, 4.5, 8, 11.5, 11.5, 9),
                new AABB(4.5, 4.5, 8, 5.5, 11.5, 9),
                new AABB(11, 5, 6, 12, 11, 7),
                new AABB(5, 11, 6, 11, 12, 7),
                new AABB(5.5, 10.5, 8, 10.5, 11.5, 9),
                new AABB(5.5, 4.5, 8, 10.5, 5.5, 9),
                new AABB(5, 4, 6, 11, 5, 7),
                new AABB(4, 11, 6, 5, 12, 7),
                new AABB(11, 11, 6, 12, 12, 7),
                new AABB(11, 4, 6, 12, 5, 7),
                new AABB(4, 4, 6, 5, 5, 7)));
    }

    private static VoxelShape combineModelBoxes(List<AABB> boxes) {
        VoxelShape result = Shapes.empty();
        for (AABB box : boxes) {
            result = Shapes.joinUnoptimized(result, Shapes.box(
                    box.minX / MODEL_SCALE, box.minY / MODEL_SCALE, box.minZ / MODEL_SCALE,
                    box.maxX / MODEL_SCALE, box.maxY / MODEL_SCALE, box.maxZ / MODEL_SCALE), BooleanOp.OR);
        }
        return result.optimize();
    }

    private static VoxelShape[] orientations(VoxelShape north) {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length];
        List<AABB> boxes = north.toAabbs();
        for (Direction facing : Direction.values()) {
            if (facing == Direction.NORTH) {
                shapes[facing.get3DDataValue()] = north;
                continue;
            }
            VoxelShape rotated = Shapes.empty();
            for (AABB box : boxes) {
                // Match the blockstate's Y rotation, followed by its special UP/DOWN X/Y combination.
                AABB transformed = switch (facing) {
                    case SOUTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ,
                            1 - box.minX, box.maxY, 1 - box.minZ);
                    case EAST -> new AABB(1 - box.maxZ, box.minY, box.minX,
                            1 - box.minZ, box.maxY, box.maxX);
                    case WEST -> new AABB(box.minZ, box.minY, 1 - box.maxX,
                            box.maxZ, box.maxY, 1 - box.minX);
                    case UP -> new AABB(1 - box.maxX, 1 - box.maxZ, 1 - box.maxY,
                            1 - box.minX, 1 - box.minZ, 1 - box.minY);
                    case DOWN -> new AABB(1 - box.maxX, box.minZ, box.minY,
                            1 - box.minX, box.maxZ, box.maxY);
                    case NORTH -> box;
                };
                rotated = Shapes.joinUnoptimized(rotated, Shapes.create(transformed), BooleanOp.OR);
            }
            shapes[facing.get3DDataValue()] = rotated.optimize();
        }
        return shapes;
    }

    /**
     * Encloses each clipped strip of a rotated model cuboid. Quantizing outward to a quarter model pixel keeps the
     * stair-step approximation conservative and the cached voxel grid bounded, without filling a whole diagonal AABB.
     * The cross-section uses (y,z) around X and (z,x) around Y, preserving the model's right-handed rotation.
     */
    private static void addRotated(List<AABB> boxes, AABB box, Direction.Axis axis,
                                   double originU, double originV, double angle) {
        boolean aroundX = axis == Direction.Axis.X;
        double minU = aroundX ? box.minY : box.minZ;
        double maxU = aroundX ? box.maxY : box.maxZ;
        double minV = aroundX ? box.minZ : box.minX;
        double maxV = aroundX ? box.maxZ : box.maxX;
        double[] u = { minU, maxU, maxU, minU };
        double[] v = { minV, minV, maxV, maxV };
        double sin = Math.sin(Math.toRadians(angle));
        double cos = Math.cos(Math.toRadians(angle));
        double rotatedMinU = Double.POSITIVE_INFINITY;
        double rotatedMaxU = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < u.length; index++) {
            double relativeU = u[index] - originU;
            double relativeV = v[index] - originV;
            u[index] = originU + cos * relativeU - sin * relativeV;
            v[index] = originV + sin * relativeU + cos * relativeV;
            rotatedMinU = Math.min(rotatedMinU, u[index]);
            rotatedMaxU = Math.max(rotatedMaxU, u[index]);
        }
        int firstSlice = (int) Math.floor(rotatedMinU / SLICE_SIZE);
        int lastSlice = (int) Math.ceil(rotatedMaxU / SLICE_SIZE);
        for (int slice = firstSlice; slice < lastSlice; slice++) {
            double sliceMinU = slice * SLICE_SIZE;
            double sliceMaxU = sliceMinU + SLICE_SIZE;
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
            for (int index = 0; index < u.length; index++) {
                int next = (index + 1) % u.length;
                if (u[index] >= sliceMinU && u[index] <= sliceMaxU) {
                    low = Math.min(low, v[index]);
                    high = Math.max(high, v[index]);
                }
                if (u[index] == u[next]) {
                    continue;
                }
                for (int boundary = 0; boundary < 2; boundary++) {
                    double edge = boundary == 0 ? sliceMinU : sliceMaxU;
                    double fraction = (edge - u[index]) / (u[next] - u[index]);
                    if (fraction >= 0 && fraction <= 1) {
                        double intersection = v[index] + fraction * (v[next] - v[index]);
                        low = Math.min(low, intersection);
                        high = Math.max(high, intersection);
                    }
                }
            }
            double sliceMinV = Math.floor(low / SLICE_SIZE) * SLICE_SIZE;
            double sliceMaxV = Math.ceil(high / SLICE_SIZE) * SLICE_SIZE;
            boxes.add(aroundX ?
                    new AABB(box.minX, sliceMinU, sliceMinV, box.maxX, sliceMaxU, sliceMaxV) :
                    new AABB(sliceMinV, box.minY, sliceMinU, sliceMaxV, box.maxY, sliceMaxU));
        }
    }
}
