package com.fish_dan_.data_energistics.common.multiblock.vertical;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalMultiBlockRegistryTest {

    @Test
    void registersDefinitionsById() {
        VerticalMultiBlockRegistry<String> registry = new VerticalMultiBlockRegistry<>();
        VerticalMultiBlockDefinition<String> definition = definition("test:vertical");

        registry.register(definition);

        assertEquals(1, registry.size());
        assertTrue(registry.get("test:vertical").isPresent());
    }

    @Test
    void duplicateDefinitionIdFailsFast() {
        VerticalMultiBlockRegistry<String> registry = new VerticalMultiBlockRegistry<>();
        registry.register(definition("test:vertical"));

        assertThrows(IllegalStateException.class, () -> registry.register(definition("test:vertical")));
    }

    @Test
    void missingTemplatesFailFast() {
        assertThrows(IllegalStateException.class, () -> VerticalMultiBlockDefinition.<String>builder("test:missing")
                .bottomLayer(layer("A"))
                .topLayer(layer("A"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .build());
    }

    private static VerticalMultiBlockDefinition<String> definition(String id) {
        return VerticalMultiBlockDefinition.<String>builder(id)
                .bottomLayer(layer("B"))
                .middleLayer(layer("M"))
                .topLayer(layer("T"))
                .controllerCandidates(List.of(new VerticalMultiBlockPos(0, 0, 0)))
                .heightRange(2, 4)
                .build();
    }

    private static VerticalMultiBlockLayer<String> layer(String state) {
        return VerticalMultiBlockLayer.ofRows(List.of(VerticalMultiBlockPredicate.state(state)));
    }
}
