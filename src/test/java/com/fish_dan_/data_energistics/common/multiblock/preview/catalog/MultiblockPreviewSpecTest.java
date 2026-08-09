package com.fish_dan_.data_energistics.common.multiblock.preview.catalog;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierOption;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MultiblockPreviewSpecTest {

    @Test
    void tierDomainCopiesLabelsAndPreservesUniqueOptionOrder() {
        MutableComponent mutableLabel = Component.literal("tier one");
        PreviewTierOption first = new PreviewTierOption(
                1,
                mutableLabel,
                ResourceLocation.parse("minecraft:iron_block"));
        PreviewTierOption second = new PreviewTierOption(
                2,
                Component.literal("tier two"),
                ResourceLocation.parse("minecraft:gold_block"));
        List<PreviewTierOption> mutableOptions = new ArrayList<>(List.of(first, second));

        PreviewTierDomain domain = new PreviewTierDomain(
                "core",
                Component.literal("Core tier"),
                mutableOptions,
                1);
        mutableLabel.append(" changed");
        mutableOptions.clear();

        assertEquals(List.of(first, second), domain.options());
        assertEquals(first, domain.option(1));
        assertEquals("tier one", domain.option(1).label().getString());
        assertTrue(domain.containsBlock(ResourceLocation.parse("minecraft:gold_block")));
        assertFalse(domain.containsBlock(ResourceLocation.parse("minecraft:diamond_block")));
        assertThrows(UnsupportedOperationException.class, () -> domain.options().add(first));
        assertThrows(IllegalArgumentException.class, () -> domain.option(3));
    }

    @Test
    void tierDomainRejectsInvalidOrAmbiguousDefinitions() {
        PreviewTierOption first = tierOption(1, "minecraft:iron_block");
        PreviewTierOption duplicateValue = tierOption(1, "minecraft:gold_block");
        PreviewTierOption duplicateBlock = tierOption(2, "minecraft:iron_block");
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierOption(0, Component.literal("invalid"), first.blockId()));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierDomain(" ", Component.literal("tier"), List.of(first), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierDomain("core", Component.literal("tier"), List.of(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierDomain("core", Component.literal("tier"), List.of(first), 2));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierDomain(
                        "core", Component.literal("tier"), List.of(first, duplicateValue), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PreviewTierDomain(
                        "core", Component.literal("tier"), List.of(first, duplicateBlock), 1));
    }

    private static PreviewTierOption tierOption(int value, String blockId) {
        return new PreviewTierOption(value, Component.literal("tier " + value), ResourceLocation.parse(blockId));
    }
}
