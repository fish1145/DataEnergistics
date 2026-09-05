package com.fish_dan_.data_energistics.common.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Server-verified visible edge; no client lookup of the remote chunk is needed to render it. */
public record BeamVisual(BlockPos target, Direction targetFacing, int color) {

    static int blend(int first, int second) {
        if (first < 0) {
            return second < 0 ? 0xFFFFFF : second;
        }
        if (second < 0) {
            return first;
        }
        int result = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (first >> shift) & 255;
            int b = (second >> shift) & 255;
            result |= (int) Math.sqrt((a * a + b * b) / 2.0) << shift;
        }
        return result;
    }
}
