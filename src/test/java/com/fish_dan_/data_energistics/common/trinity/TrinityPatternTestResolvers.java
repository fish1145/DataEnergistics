package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

/**
 * Supplies a strict isolated recipe resolver for simplified pattern details used by Trinity logic tests.
 */
final class TrinityPatternTestResolvers {

    private TrinityPatternTestResolvers() {}

    /**
     * @return isolated registry that recognizes the paper/map test definitions
     */
    static TrinityPatternRecipeIdResolvers create() {
        TrinityPatternRecipeIdResolvers resolvers = new TrinityPatternRecipeIdResolvers();
        resolvers.register(TestResolver.INSTANCE);
        return resolvers;
    }

    /**
     * Stable resolver for the two intentionally simplified test definitions.
     */
    private enum TestResolver implements TrinityPatternRecipeIdResolver {

        /**
         * Sole stateless resolver instance.
         */
        INSTANCE;

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "logic_test_pattern");
        }

        @Override
        public boolean supports(IMolecularAssemblerSupportedPattern pattern) {
            return pattern.getDefinition().toStack().is(Items.PAPER) ||
                    pattern.getDefinition().toStack().is(Items.MAP);
        }

        @Override
        public ResourceLocation recipeId(IMolecularAssemblerSupportedPattern pattern) {
            String path = pattern.getDefinition().toStack().is(Items.PAPER) ? "paper" : "map";
            return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "logic_test/" + path);
        }
    }
}
