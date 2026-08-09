package com.fish_dan_.data_energistics.common.trinity.preview;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistrySnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpecFactory;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierDomain;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewTierOption;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructurePreviewSpec;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.SubstructureSelection;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;
import com.modularmc.mdl.api.multiblock.PatternUnit;
import com.modularmc.mdl.api.multiblock.RepeatRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Declares Trinity's three named structures and reuses its authoritative auto-build tier registrations.
 */
public final class TrinityMultiblockPreviewSpecFactory implements MultiblockPreviewSpecFactory {

    @Override
    public ResourceLocation controllerId() {
        return ModVerticalMultiBlocks.trinityDataCoreId();
    }

    @Override
    public MultiblockPreviewSpec create(JsonMultiBlockDefinitionRegistrySnapshot definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("Trinity preview definitions cannot be null");
        }
        JsonMultiBlockDefinition main = requireDefinition(
                definitions,
                ModVerticalMultiBlocks.trinityDataCoreMainKey());
        JsonMultiBlockDefinition cpu = requireDefinition(
                definitions,
                ModVerticalMultiBlocks.trinityDataCoreCpuKey());
        JsonMultiBlockDefinition crafting = requireDefinition(
                definitions,
                ModVerticalMultiBlocks.trinityDataCoreCraftingKey());
        AEItemKey ownerOutput = AEItemKey.of(ModBlocks.TRINITY_DATA_CORE.get());
        if (ownerOutput == null) {
            throw new IllegalStateException("Trinity data core block does not expose an owner item");
        }
        return new MultiblockPreviewSpec(
                controllerId(),
                Component.translatable("multiblock.data_energistics.trinity_data_core"),
                ownerOutput,
                definitions.revision(),
                List.of(
                        substructure(
                                main,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.main",
                                TrinityAutoBuildBlockMap.STORAGE_CORE),
                        substructure(
                                cpu,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.cpu",
                                TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE),
                        substructure(
                                crafting,
                                "screen.data_energistics.trinity_data_core.auto_build.structure.crafting",
                                TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE)));
    }

    private static SubstructurePreviewSpec substructure(JsonMultiBlockDefinition definition,
                                                        String titleTranslationKey,
                                                        String tierCategory) {
        PreviewTierDomain tierDomain = tierDomain(tierCategory);
        List<Integer> repeatCounts = definition.pattern().getLayout().units().stream()
                .map(PatternUnit::repeats)
                .map(RepeatRange::min)
                .toList();
        return new SubstructurePreviewSpec(
                List.of(definition),
                Component.translatable(titleTranslationKey),
                List.of(tierDomain),
                new SubstructureSelection(
                        0,
                        repeatCounts,
                        Map.of(tierCategory, tierDomain.defaultValue()),
                        Map.of()));
    }

    private static PreviewTierDomain tierDomain(String category) {
        List<ResourceLocation> blockIds = TrinityAutoBuildBlockMap.categories().get(category);
        if (blockIds == null || blockIds.isEmpty()) {
            throw new IllegalStateException("Trinity preview tier category is not registered: " + category);
        }
        List<PreviewTierOption> options = new ArrayList<>(blockIds.size());
        for (int index = 0; index < blockIds.size(); index++) {
            ResourceLocation blockId = blockIds.get(index);
            options.add(new PreviewTierOption(
                    index + 1,
                    Component.translatable(Util.makeDescriptionId("block", blockId)),
                    blockId));
        }
        return new PreviewTierDomain(
                category,
                Component.translatable(tierLabel(category)),
                options,
                1);
    }

    private static String tierLabel(String category) {
        return switch (category) {
            case TrinityAutoBuildBlockMap.STORAGE_CORE -> "screen.data_energistics.trinity_data_core.auto_build.storage_tier";
            case TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE -> "screen.data_energistics.trinity_data_core.auto_build.cpu_tier";
            case TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE -> "screen.data_energistics.trinity_data_core.auto_build.pattern_tier";
            default -> throw new IllegalArgumentException("Unknown Trinity preview tier category: " + category);
        };
    }

    private static JsonMultiBlockDefinition requireDefinition(JsonMultiBlockDefinitionRegistrySnapshot definitions,
                                                              JsonMultiBlockStructureKey key) {
        JsonMultiBlockDefinition definition = definitions.definitions().get(key);
        if (definition == null) {
            throw new IllegalStateException("Missing Trinity preview definition: " + key);
        }
        return definition;
    }
}
