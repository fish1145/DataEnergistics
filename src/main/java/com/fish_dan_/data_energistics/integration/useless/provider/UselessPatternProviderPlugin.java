package com.fish_dan_.data_energistics.integration.useless.provider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModBlocks;

import java.util.List;

/** Registers the advanced alloy furnace through the unified provider extension contract. */
@DataEnergisticsEntrypoint
public final class UselessPatternProviderPlugin implements DataEnergisticsPlugin {

    private static final String MOD_ID = "useless_mod";
    private static final ResourceLocation RECIPE_CATEGORY_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "advanced_alloy_furnace");

    /** Public constructor required by the common entrypoint scanner. */
    public UselessPatternProviderPlugin() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }

        ResourceLocation providerTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get());
        ResourceLocation workstationItemId = BuiltInRegistries.ITEM.getKey(
                ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get().asItem());
        PatternProviderMetadata metadata = new PatternProviderMetadata(
                Data_Energistics.id("useless_advanced_alloy_furnace"),
                new ProviderIdentityDescriptor.Block(providerTypeId),
                List.of(RECIPE_CATEGORY_ID),
                List.of(workstationItemId));
        registry.patternProviders().register(new PatternProviderRegistration(
                metadata,
                null,
                null,
                context -> ((AdvancedAlloyFurnaceBlockEntity) context.provider()).markChanged()));
    }
}
