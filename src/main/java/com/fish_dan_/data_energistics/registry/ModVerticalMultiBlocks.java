package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionLoaderImpl;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistryImpl;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockReloadEventHandler;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.LazyJsonMultiBlockDefinitionImpl;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockLayer;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPredicate;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import com.modularmc.mdl.api.multiblock.Predicates;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    public static final String DIGITAL_CONSTRUCT_FLOWER_ID = Data_Energistics.id("digital_construct_flower").toString();
    public static final String DIGITAL_CONSTRUCT_FLOWER_DISPLAY_NAME = "multiblock.data_energistics.digital_construct_flower";
    private static final String DIGITAL_CONSTRUCT_FLOWER_PATH = "/data/data_energistics/multiblock/digital_construct_flower.json";
    public static final int DATA_FRAMEWORK_COLUMN_MIN_HEIGHT = 3;
    public static final int DATA_FRAMEWORK_COLUMN_MAX_HEIGHT = 8;

    public static final VerticalMultiBlockRegistry<BlockState> VERTICAL_MULTI_BLOCKS = new VerticalMultiBlockRegistry<>();
    public static final JsonMultiBlockDefinitionRegistry JSON_MULTI_BLOCKS = new JsonMultiBlockDefinitionRegistryImpl();

    private ModVerticalMultiBlocks() {}

    public static void init() {
        VERTICAL_MULTI_BLOCKS.register(VerticalMultiBlockDefinition.<BlockState>builder(DATA_FRAMEWORK_COLUMN_ID)
                .bottomLayer(dataFrameworkControllerLayer())
                .middleLayer(dataFrameworkLayer())
                .topLayer(dataFrameworkLayer())
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(DATA_FRAMEWORK_COLUMN_MIN_HEIGHT, DATA_FRAMEWORK_COLUMN_MAX_HEIGHT)
                .build());
        JSON_MULTI_BLOCKS.registerBuiltin(new LazyJsonMultiBlockDefinitionImpl(
                JsonMultiBlockStructureKey.main(dataFrameworkColumnId()),
                ModVerticalMultiBlocks::dataFrameworkColumnPattern));
        JSON_MULTI_BLOCKS.registerBuiltin(new LazyJsonMultiBlockDefinitionImpl(
                JsonMultiBlockStructureKey.main(digitalConstructFlowerId()),
                ModVerticalMultiBlocks::digitalConstructFlowerPattern,
                DIGITAL_CONSTRUCT_FLOWER_DISPLAY_NAME));
        NeoForge.EVENT_BUS.register(jsonReloadEventHandler());
    }

    public static JsonMultiBlockReloadEventHandler jsonReloadEventHandler() {
        return new JsonMultiBlockReloadEventHandler(JSON_MULTI_BLOCKS);
    }

    private static VerticalMultiBlockLayer<BlockState> dataFrameworkLayer() {
        VerticalMultiBlockPredicate<BlockState> dataFramework = (state, pos) -> state.is(ModBlocks.DATA_FRAMEWORK.get());
        return VerticalMultiBlockLayer.ofRows(List.of(dataFramework));
    }

    private static VerticalMultiBlockLayer<BlockState> dataFrameworkControllerLayer() {
        VerticalMultiBlockPredicate<BlockState> dataFrameworkController = (state, pos) -> state.is(ModBlocks.DATA_FRAMEWORK_MAIN.get());
        return VerticalMultiBlockLayer.ofRows(List.of(dataFrameworkController));
    }

    private static ResourceLocation dataFrameworkColumnId() {
        return Data_Energistics.id("data_framework_column");
    }

    private static ResourceLocation digitalConstructFlowerId() {
        return Data_Energistics.id("digital_construct_flower");
    }

    private static BlockPattern dataFrameworkColumnPattern() {
        return FactoryBlockPattern.start()
                .aisle("~")
                .beginRepeatable()
                .aisle("A")
                .endRepeatable(DATA_FRAMEWORK_COLUMN_MIN_HEIGHT - 1, DATA_FRAMEWORK_COLUMN_MAX_HEIGHT - 1)
                .where('A', Predicates.blocks(ModBlocks.DATA_FRAMEWORK.get()))
                .build();
    }

    private static BlockPattern digitalConstructFlowerPattern() {
        return loadBundledJsonPattern(DIGITAL_CONSTRUCT_FLOWER_PATH, digitalConstructFlowerId());
    }

    private static BlockPattern loadBundledJsonPattern(String path, ResourceLocation resourceId) {
        InputStream stream = ModVerticalMultiBlocks.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled JSON multiblock definition: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new JsonMultiBlockDefinitionLoaderImpl().parse(resourceId, reader).pattern();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load bundled JSON multiblock definition: " + path, exception);
        }
    }
}
