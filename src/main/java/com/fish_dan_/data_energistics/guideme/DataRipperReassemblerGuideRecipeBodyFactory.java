package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;

import net.neoforged.fml.loading.FMLEnvironment;

import guideme.document.block.LytBlock;
import guideme.document.block.LytParagraph;

final class DataRipperReassemblerGuideRecipeBodyFactory {

    private static final String CLIENT_BODY_CLASS = "com.fish_dan_.data_energistics.client.guideme.DataRipperReassemblerGuideRecipeBody";

    private DataRipperReassemblerGuideRecipeBodyFactory() {}

    static LytBlock create(DataRipperReassemblerRecipe recipe) {
        if (!FMLEnvironment.dist.isClient()) {
            return LytParagraph.of("");
        }

        try {
            Class<?> bodyClass = Class.forName(CLIENT_BODY_CLASS);
            return (LytBlock) bodyClass.getConstructor(DataRipperReassemblerRecipe.class).newInstance(recipe);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create GuideME data reassembler recipe body", e);
        }
    }
}
