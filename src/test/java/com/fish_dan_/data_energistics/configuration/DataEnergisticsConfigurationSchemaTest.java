package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.minecraft.network.chat.contents.TranslatableContents;

import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValueReadable;
import dev.toma.configuration.config.value.ObjectValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DataEnergisticsConfigurationSchemaTest {

    @Test
    void exposesExactlyTheSixtyThreeLocalizedTargetLeaves() {
        ConfigHolder<DataEnergisticsConfiguration> holder = newHolder();
        List<ConfigValue<?>> leaves = leaves(holder.values());

        assertEquals(63, leaves.size());
        assertEquals(3, countPrefix(leaves, "dataRipper."));
        assertEquals(1, countPrefix(leaves, "dataDistributionTower."));
        assertEquals(4, countPrefix(leaves, "dataSanctumInterface."));
        assertEquals(15, countPrefix(leaves, "dataExtractor."));
        assertEquals(14, countPrefix(leaves, "flatteningTnt."));
        assertEquals(4, countPrefix(leaves, "solarPanel."));
        assertEquals(8, countPrefix(leaves, "trinityCrafting."));
        assertEquals(14, countPrefix(leaves, "trinityDispatch."));

        assertFalse(hasPath(leaves, "flatteningTnt.tntConfigurable.displayName"));
        assertFalse(hasPath(leaves, "trinityCrafting.mipTimeoutMs"));
        assertTrue(hasPath(leaves, "trinityDispatch.safeRetryBackoffTicks"));

        for (ConfigValue<?> leaf : leaves) {
            assertEquals(2, leaf.getFileComments().length, leaf.getPath());
            TranslatableContents title = assertInstanceOf(TranslatableContents.class, leaf.getTitle().getContents());
            assertEquals("config.data_energistics.option." + leaf.getPath(), title.getKey());
        }
    }

    @Test
    void preservesCurrentDefaultsAndDynamicPlannerBound() {
        DataEnergisticsConfiguration schema = newHolder().getConfigInstance();

        assertEquals(512, schema.dataRipper.baseCost);
        assertEquals(36, schema.dataExtractor.cropInputMappings.split(",").length);
        assertEquals("minecraft:dirt", schema.flatteningTnt.tntConfigurable.fillBlock);
        assertEquals(32768, schema.trinityCrafting.maxBindingVariants);
        assertEquals(
                DataEnergisticsConfiguration.TrinityCraftingSchema.recommendedPlannerThreads(
                        Runtime.getRuntime().availableProcessors()),
                schema.trinityCrafting.plannerThreads);
        assertEquals(CraftingQuantityMode.NET_NEW, schema.trinityCrafting.defaultQuantityMode);
        assertEquals(8, schema.trinityDispatch.safeRetryBackoffTicks);
    }

    private static ConfigHolder<DataEnergisticsConfiguration> newHolder() {
        return new ConfigHolder<>(
                DataEnergisticsConfiguration.class,
                "data_energistics",
                "data_energistics/data_energistics",
                "data_energistics",
                ConfigFormats.YAML);
    }

    private static List<ConfigValue<?>> leaves(Collection<ConfigValue<?>> values) {
        List<ConfigValue<?>> leaves = new ArrayList<>();
        collectLeaves(values, leaves);
        return List.copyOf(leaves);
    }

    private static void collectLeaves(Collection<ConfigValue<?>> values, List<ConfigValue<?>> leaves) {
        for (ConfigValue<?> value : values) {
            if (value instanceof ObjectValue objectValue) {
                Map<String, ConfigValue<?>> children = objectValue.get(IConfigValueReadable.Mode.SAVED);
                collectLeaves(children.values(), leaves);
            } else {
                leaves.add(value);
            }
        }
    }

    private static long countPrefix(List<ConfigValue<?>> values, String prefix) {
        return values.stream().filter(value -> value.getPath().startsWith(prefix)).count();
    }

    private static boolean hasPath(List<ConfigValue<?>> values, String path) {
        return values.stream().anyMatch(value -> value.getPath().equals(path));
    }
}
