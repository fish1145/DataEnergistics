package com.fish_dan_.data_energistics.part.beam;

import appeng.api.parts.IPartCollisionHelper;

/** Collision boxes of the north-facing part model, converted to AE2's face-local collision basis. */
final class BeamFormerPartShapes {

    private BeamFormerPartShapes() {}

    static void addBoxes(IPartCollisionHelper boxes) {
        // part/me_beam_former.json is north-facing, including the protruding negative-Z elements.
        addModelBox(boxes, 4.99, 4.99, -1, 11.01, 11.01, 5);
        addModelBox(boxes, 4, 4, 2.25, 12, 12, 4.25);
        addModelBox(boxes, 7, 7, -4, 9, 9, -2);
        addModelBox(boxes, 6.8, 6.8, -4.2, 9.2, 9.2, -1.8);
        addModelBox(boxes, 4, 7, 3.25, 5, 9, 4.25);
        addModelBox(boxes, 7, 4, 3.25, 9, 5, 4.25);
        addModelBox(boxes, 11, 7, 3.25, 12, 9, 4.25);
        addModelBox(boxes, 7, 11, 3.25, 9, 12, 4.25);
        addModelBox(boxes, 4, 6, -1, 5, 10, 2.25);
        addModelBox(boxes, 6, 4, -1, 10, 5, 2.25);
        addModelBox(boxes, 11, 6, -1, 12, 10, 2.25);
        addModelBox(boxes, 6, 11, -1, 10, 12, 2.25);
        addModelBox(boxes, 7, 5, -5, 9, 6, -1);
        addModelBox(boxes, 10, 7, -5, 11, 9, -1);
        addModelBox(boxes, 7, 10, -5, 9, 11, -1);
        addModelBox(boxes, 5, 7, -5, 6, 9, -1);
        addModelBox(boxes, 5, 9, -3.5, 6, 11, -2.5);
        addModelBox(boxes, 6, 10, -3.5, 7, 11, -2.5);
        addModelBox(boxes, 5, 5, -3.5, 7, 6, -2.5);
        addModelBox(boxes, 5, 6, -3.5, 6, 7, -2.5);
        addModelBox(boxes, 9, 10, -3.5, 11, 11, -2.5);
        addModelBox(boxes, 10, 9, -3.5, 11, 10, -2.5);
        addModelBox(boxes, 10, 5, -3.5, 11, 7, -2.5);
        addModelBox(boxes, 9, 5, -3.5, 10, 6, -2.5);
    }

    private static void addModelBox(IPartCollisionHelper boxes, double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
        // Convert model coordinates into the helper's face-local axes; the helper applies the world rotation.
        switch (boxes.getWorldZ()) {
            case NORTH, SOUTH -> boxes.addBox(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
            case EAST, WEST, DOWN -> boxes.addBox(minX, minY, 16 - maxZ, maxX, maxY, 16 - minZ);
            case UP -> boxes.addBox(16 - maxX, 16 - maxY, 16 - maxZ, 16 - minX, 16 - minY, 16 - minZ);
        }
    }
}
