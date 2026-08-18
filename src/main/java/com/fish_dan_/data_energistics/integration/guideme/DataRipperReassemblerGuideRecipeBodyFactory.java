package com.fish_dan_.data_energistics.integration.guideme;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;

import net.neoforged.fml.loading.FMLEnvironment;

import guideme.document.block.LytBlock;
import guideme.document.block.LytParagraph;

final class DataRipperReassemblerGuideRecipeBodyFactory {

    private DataRipperReassemblerGuideRecipeBodyFactory() {}

    static LytBlock create(DataRipperReassemblerRecipe recipe) {
        if (!FMLEnvironment.dist.isClient()) {
            return LytParagraph.of("");
        }

        return DataEnergisticsClientBridgeAccess.get().createDataRipperReassemblerGuideRecipeBody(recipe);
    }
}
