package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockLayer;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPredicate;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRegistry;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Code registration entry point for vertical multiblock definitions.
 *
 * <p>
 * Definitions are intentionally code-driven in v1. The data framework column uses existing Data Framework blocks as a
 * first real vertical structure without adding production sample blocks.
 */
public final class ModVerticalMultiBlocks {

    public static final String DATA_FRAMEWORK_COLUMN_ID = Data_Energistics.id("data_framework_column").toString();
    public static final int DATA_FRAMEWORK_COLUMN_MIN_HEIGHT = 3;
    public static final int DATA_FRAMEWORK_COLUMN_MAX_HEIGHT = 8;

    public static final VerticalMultiBlockRegistry<BlockState> VERTICAL_MULTI_BLOCKS = new VerticalMultiBlockRegistry<>();

    private ModVerticalMultiBlocks() {}

    public static void init() {
        VERTICAL_MULTI_BLOCKS.register(VerticalMultiBlockDefinition.<BlockState>builder(DATA_FRAMEWORK_COLUMN_ID)
                .bottomLayer(dataFrameworkLayer())
                .middleLayer(dataFrameworkLayer())
                .topLayer(dataFrameworkLayer())
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(DATA_FRAMEWORK_COLUMN_MIN_HEIGHT, DATA_FRAMEWORK_COLUMN_MAX_HEIGHT)
                .build());
    }

    private static VerticalMultiBlockLayer<BlockState> dataFrameworkLayer() {
        VerticalMultiBlockPredicate<BlockState> dataFramework = (state, pos) -> state.is(ModBlocks.DATA_FRAMEWORK.get());
        return VerticalMultiBlockLayer.ofRows(List.of(dataFramework));
    }
}
