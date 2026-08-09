package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockReloadEventHandler;
import com.fish_dan_.data_energistics.common.multiblock.json.MdlibJsonMultiBlockDefinitionLoader;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.LazyJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.json.registry.LayeredJsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalog;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockPreviewCatalogImpl;
import com.fish_dan_.data_energistics.common.trinity.preview.TrinityMultiblockPreviewSpecFactory;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Registration entry point for JSON-backed multiblock definitions.
 */
public final class ModVerticalMultiBlocks {

    public static final String TRINITY_DATA_CORE_ID = Data_Energistics.id("trinity_data_core").toString();
    public static final String TRINITY_DATA_CORE_CPU_STRUCTURE_NAME = "cpu";
    public static final String TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME = "crafting";
    private static final String TRINITY_DATA_CORE_PATH = "/data/data_energistics/multiblock/trinity_data_core/main.json";
    private static final String TRINITY_DATA_CORE_CPU_PATH = "/data/data_energistics/multiblock/trinity_data_core/cpu.json";
    private static final String TRINITY_DATA_CORE_CRAFTING_PATH = "/data/data_energistics/multiblock/trinity_data_core/crafting.json";

    public static final JsonMultiBlockDefinitionRegistry JSON_MULTI_BLOCKS = new LayeredJsonMultiBlockDefinitionRegistry();
    /** Common controller preview catalog built from one atomic JSON definition generation per read. */
    public static final MultiblockPreviewCatalog MULTIBLOCK_PREVIEWS = new MultiblockPreviewCatalogImpl(
            JSON_MULTI_BLOCKS,
            List.of(new TrinityMultiblockPreviewSpecFactory()));

    private ModVerticalMultiBlocks() {}

    public static void init() {
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                trinityDataCoreMainKey(),
                ModVerticalMultiBlocks::trinityDataCoreDefinition));
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                trinityDataCoreCpuKey(),
                ModVerticalMultiBlocks::trinityDataCoreCpuDefinition));
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                trinityDataCoreCraftingKey(),
                ModVerticalMultiBlocks::trinityDataCoreCraftingDefinition));
        NeoForge.EVENT_BUS.register(jsonReloadEventHandler());
    }

    public static JsonMultiBlockReloadEventHandler jsonReloadEventHandler() {
        return new JsonMultiBlockReloadEventHandler(JSON_MULTI_BLOCKS);
    }

    /**
     * Returns the stable machine id shared by Trinity's named structure definitions.
     */
    public static ResourceLocation trinityDataCoreId() {
        return Data_Energistics.id("trinity_data_core");
    }

    /**
     * Returns the main Trinity structure key used by matching, preview, and automatic building.
     */
    public static JsonMultiBlockStructureKey trinityDataCoreMainKey() {
        return JsonMultiBlockStructureKey.main(trinityDataCoreId());
    }

    private static JsonMultiBlockDefinition trinityDataCoreDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DATA_CORE_PATH,
                Data_Energistics.id("trinity_data_core/" + JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME));
    }

    /**
     * Returns the CPU child structure key shared by all Trinity consumers.
     */
    public static JsonMultiBlockStructureKey trinityDataCoreCpuKey() {
        return new JsonMultiBlockStructureKey(trinityDataCoreId(), TRINITY_DATA_CORE_CPU_STRUCTURE_NAME);
    }

    /**
     * Returns the crafting child structure key shared by all Trinity consumers.
     */
    public static JsonMultiBlockStructureKey trinityDataCoreCraftingKey() {
        return new JsonMultiBlockStructureKey(trinityDataCoreId(), TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME);
    }

    private static JsonMultiBlockDefinition trinityDataCoreCpuDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DATA_CORE_CPU_PATH,
                Data_Energistics.id("trinity_data_core/" + TRINITY_DATA_CORE_CPU_STRUCTURE_NAME));
    }

    private static JsonMultiBlockDefinition trinityDataCoreCraftingDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DATA_CORE_CRAFTING_PATH,
                Data_Energistics.id("trinity_data_core/" + TRINITY_DATA_CORE_CRAFTING_STRUCTURE_NAME));
    }

    private static JsonMultiBlockDefinition loadBundledJsonDefinition(String path, ResourceLocation resourceId) {
        InputStream stream = ModVerticalMultiBlocks.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled JSON multiblock definition: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new MdlibJsonMultiBlockDefinitionLoader().parse(resourceId, reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load bundled JSON multiblock definition: " + path, exception);
        }
    }
}
