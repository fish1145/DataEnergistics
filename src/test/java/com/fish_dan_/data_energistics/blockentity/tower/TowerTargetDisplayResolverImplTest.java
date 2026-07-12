package com.fish_dan_.data_energistics.blockentity.tower;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TowerTargetDisplayResolverImplTest {

    @Test
    void ordersTargetPositionsByXThenYThenZ() {
        List<BlockPos> positions = new ArrayList<>(List.of(
                new BlockPos(1, -5, 9),
                new BlockPos(0, 4, 3),
                new BlockPos(0, 4, -2),
                new BlockPos(0, -1, 30),
                new BlockPos(-2, 99, 99)));

        positions.sort(TowerTargetDisplayResolverImpl::compareBlockPos);

        assertEquals(List.of(
                new BlockPos(-2, 99, 99),
                new BlockPos(0, -1, 30),
                new BlockPos(0, 4, -2),
                new BlockPos(0, 4, 3),
                new BlockPos(1, -5, 9)), positions);
    }
}
