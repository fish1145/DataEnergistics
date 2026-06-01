package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.util.ReflectionAccess;
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

        Object body = ReflectionAccess.newInstance(
                CLIENT_BODY_CLASS,
                new Class<?>[] { DataRipperReassemblerRecipe.class },
                recipe);
        if (body instanceof LytBlock block) {
            return block;
        }
        throw new IllegalStateException("Failed to create GuideME data reassembler recipe body");
    }
}
