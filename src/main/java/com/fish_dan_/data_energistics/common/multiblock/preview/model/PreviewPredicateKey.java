package com.fish_dan_.data_energistics.common.multiblock.preview.model;

/**
 * Stable unexpanded source coordinate used to retain a predicate candidate choice across repeat changes.
 *
 * @param sourceLayer source aisle index before repeat expansion
 * @param y           source row index
 * @param x           source character index
 */
public record PreviewPredicateKey(int sourceLayer, int y, int x) {

    /**
     * Rejects coordinates that cannot address an MDLib source pattern.
     */
    public PreviewPredicateKey {
        if (sourceLayer < 0 || y < 0 || x < 0) {
            throw new IllegalArgumentException("Preview predicate source coordinates cannot be negative");
        }
    }
}
