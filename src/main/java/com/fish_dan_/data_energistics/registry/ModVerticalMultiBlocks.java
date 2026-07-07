package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockReloadEventHandler;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.LayeredJsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.json.LazyJsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.MdlibJsonMultiBlockDefinitionLoader;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Registration entry point for JSON-backed multiblock definitions.
 */
public final class ModVerticalMultiBlocks {

    public static final String TRINITY_DIGITAL_CORE_ID = Data_Energistics.id("trinity_digital_core").toString();
    public static final String TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME = "cpu";
    public static final String TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME = "crafting";
    private static final String TRINITY_DIGITAL_CORE_PATH = "/data/data_energistics/multiblock/trinity_digital_core/main.json";
    private static final String TRINITY_DIGITAL_CORE_CPU_PATH = "/data/data_energistics/multiblock/trinity_digital_core/cpu.json";
    private static final String TRINITY_DIGITAL_CORE_CRAFTING_PATH = "/data/data_energistics/multiblock/trinity_digital_core/crafting.json";

    public static final JsonMultiBlockDefinitionRegistry JSON_MULTI_BLOCKS = new LayeredJsonMultiBlockDefinitionRegistry();

    private ModVerticalMultiBlocks() {}

    public static void init() {
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                JsonMultiBlockStructureKey.main(trinityDigitalCoreId()),
                ModVerticalMultiBlocks::trinityDigitalCoreDefinition));
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                trinityDigitalCoreCpuKey(),
                ModVerticalMultiBlocks::trinityDigitalCoreCpuDefinition));
        JSON_MULTI_BLOCKS.registerBuiltin(LazyJsonMultiBlockDefinition.fromDefinition(
                trinityDigitalCoreCraftingKey(),
                ModVerticalMultiBlocks::trinityDigitalCoreCraftingDefinition));
        NeoForge.EVENT_BUS.register(jsonReloadEventHandler());
    }

    public static JsonMultiBlockReloadEventHandler jsonReloadEventHandler() {
        return new JsonMultiBlockReloadEventHandler(JSON_MULTI_BLOCKS);
    }

    private static ResourceLocation trinityDigitalCoreId() {
        return Data_Energistics.id("trinity_digital_core");
    }

    private static JsonMultiBlockDefinition trinityDigitalCoreDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DIGITAL_CORE_PATH,
                Data_Energistics.id("trinity_digital_core/" + JsonMultiBlockStructureKey.DEFAULT_STRUCTURE_NAME));
    }

    private static JsonMultiBlockStructureKey trinityDigitalCoreCpuKey() {
        return new JsonMultiBlockStructureKey(trinityDigitalCoreId(), TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME);
    }

    private static JsonMultiBlockStructureKey trinityDigitalCoreCraftingKey() {
        return new JsonMultiBlockStructureKey(trinityDigitalCoreId(), TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME);
    }

    private static JsonMultiBlockDefinition trinityDigitalCoreCpuDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DIGITAL_CORE_CPU_PATH,
                Data_Energistics.id("trinity_digital_core/" + TRINITY_DIGITAL_CORE_CPU_STRUCTURE_NAME));
    }

    private static JsonMultiBlockDefinition trinityDigitalCoreCraftingDefinition() {
        return loadBundledJsonDefinition(
                TRINITY_DIGITAL_CORE_CRAFTING_PATH,
                Data_Energistics.id("trinity_digital_core/" + TRINITY_DIGITAL_CORE_CRAFTING_STRUCTURE_NAME));
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
