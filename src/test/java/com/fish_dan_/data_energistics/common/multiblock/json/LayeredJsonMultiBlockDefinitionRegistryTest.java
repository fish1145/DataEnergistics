package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;

import com.modularmc.mdl.api.multiblock.FactoryBlockPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LayeredJsonMultiBlockDefinitionRegistryTest {

    @Test
    void publishesDefinitionsAndRevisionAsOneOrderedSnapshot() {
        LayeredJsonMultiBlockDefinitionRegistry registry = new LayeredJsonMultiBlockDefinitionRegistry();
        JsonMultiBlockDefinition main = definition("main");
        JsonMultiBlockDefinition child = definition("child");

        assertEquals(0L, registry.snapshot().revision());
        registry.registerBuiltin(main);
        registry.registerBuiltin(child);

        JsonMultiBlockDefinitionRegistrySnapshot builtins = registry.snapshot();
        assertEquals(2L, builtins.revision());
        assertEquals(List.of(main.key(), child.key()), List.copyOf(builtins.definitions().keySet()));
        assertEquals(List.of(main, child), List.copyOf(registry.values()));
        assertSame(builtins, registry.snapshot());
        assertEquals(builtins.revision(), registry.revision());

        JsonMultiBlockDefinition childOverride = definition("child");
        JsonMultiBlockDefinition extra = definition("extra");
        ArrayList<JsonMultiBlockDefinition> jsonDefinitions = new ArrayList<>(List.of(childOverride, extra));
        registry.applyJsonDefinitions(jsonDefinitions);
        jsonDefinitions.clear();

        JsonMultiBlockDefinitionRegistrySnapshot merged = registry.snapshot();
        assertEquals(3L, merged.revision());
        assertEquals(List.of(main.key(), child.key(), extra.key()), List.copyOf(merged.definitions().keySet()));
        assertSame(main, merged.definitions().get(main.key()));
        assertSame(childOverride, merged.definitions().get(child.key()));
        assertSame(extra, merged.definitions().get(extra.key()));
        assertThrows(UnsupportedOperationException.class, () -> merged.definitions().clear());
    }

    @Test
    void replacingTheJsonLayerFallsBackToBuiltinsRemovedByReload() {
        LayeredJsonMultiBlockDefinitionRegistry registry = new LayeredJsonMultiBlockDefinitionRegistry();
        JsonMultiBlockDefinition builtin = definition("main");
        JsonMultiBlockDefinition override = definition("main");
        registry.registerBuiltin(builtin);
        registry.applyJsonDefinitions(List.of(override));

        assertSame(override, registry.get(builtin.key()).orElseThrow());

        registry.applyJsonDefinitions(List.of());

        assertSame(builtin, registry.get(builtin.key()).orElseThrow());
        assertEquals(3L, registry.revision());
    }

    @Test
    void duplicateJsonKeysLeaveThePublishedGenerationUntouched() {
        LayeredJsonMultiBlockDefinitionRegistry registry = new LayeredJsonMultiBlockDefinitionRegistry();
        JsonMultiBlockDefinition builtin = definition("main");
        registry.registerBuiltin(builtin);
        JsonMultiBlockDefinitionRegistrySnapshot before = registry.snapshot();

        assertThrows(IllegalStateException.class, () -> registry.applyJsonDefinitions(List.of(
                definition("extra"),
                definition("extra"))));

        assertSame(before, registry.snapshot());
        assertEquals(1L, registry.revision());
        assertSame(builtin, registry.get(builtin.key()).orElseThrow());
    }

    @Test
    void duplicateBuiltinKeysLeaveThePublishedGenerationUntouched() {
        LayeredJsonMultiBlockDefinitionRegistry registry = new LayeredJsonMultiBlockDefinitionRegistry();
        JsonMultiBlockDefinition builtin = definition("main");
        registry.registerBuiltin(builtin);
        JsonMultiBlockDefinitionRegistrySnapshot before = registry.snapshot();

        assertThrows(IllegalStateException.class, () -> registry.registerBuiltin(definition("main")));

        assertSame(before, registry.snapshot());
        assertSame(builtin, registry.get(builtin.key()).orElseThrow());
    }

    @Test
    void snapshotRejectsEntriesWhoseMapKeyDoesNotMatchTheDefinition() {
        JsonMultiBlockDefinition definition = definition("main");
        JsonMultiBlockStructureKey wrongKey = key("machine", "child");

        assertThrows(IllegalArgumentException.class,
                () -> new JsonMultiBlockDefinitionRegistrySnapshot(1L, Map.of(wrongKey, definition)));
        assertThrows(IllegalArgumentException.class,
                () -> new JsonMultiBlockDefinitionRegistrySnapshot(-1L, Map.of()));
    }

    private static JsonMultiBlockDefinition definition(String structure) {
        return new ResolvedJsonMultiBlockDefinition(
                key("machine", structure),
                FactoryBlockPattern.start().aisle("~").build());
    }

    private static JsonMultiBlockStructureKey key(String machine, String structure) {
        return new JsonMultiBlockStructureKey(
                ResourceLocation.fromNamespaceAndPath("registry_test", machine),
                structure);
    }
}
