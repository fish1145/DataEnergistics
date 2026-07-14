package com.fish_dan_.data_energistics.common.multiblock.transfer;

import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferTarget;

/**
 * Reconstructs and atomically fills an AE2 pattern terminal from an untrusted multiblock XEI request.
 */
public interface PatternEncodingMultiblockTransfer {

    /**
     * Rebuilds the ordinary recipe view from the current server catalog and verifies its complete identity.
     *
     * @param request untrusted revision-bound request
     * @return authoritative current recipe view
     */
    MultiblockRecipeView resolveRecipe(MultiblockPatternTransferRequest request);

    /**
     * Resolves and atomically writes one current recipe without encoding a pattern item.
     *
     * @param request untrusted revision-bound request
     * @param target  live pattern terminal state
     */
    void transfer(MultiblockPatternTransferRequest request, PatternEncodingMultiblockTransferTarget target);
}
