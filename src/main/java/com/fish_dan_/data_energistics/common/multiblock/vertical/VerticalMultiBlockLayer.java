package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.ArrayList;
import java.util.List;

/**
 * One fixed horizontal layer in a vertical multiblock definition.
 *
 * <p>
 * The layer stores predicates by local {@code x,z} coordinates. Empty cells are not supported in v1: every cell in
 * the layer must explicitly define what is valid, making missing structure rules fail during registration.
 *
 * @param <S> block state representation used by the caller
 */
public record VerticalMultiBlockLayer<S>(List<List<VerticalMultiBlockPredicate<S>>> rows, int width, int depth) {

    public VerticalMultiBlockLayer {
        rows = copyRows(rows);
        depth = rows.size();
        width = rows.getFirst().size();
    }

    public static <S> VerticalMultiBlockLayer<S> of(List<List<VerticalMultiBlockPredicate<S>>> rows) {
        return new VerticalMultiBlockLayer<>(rows, -1, -1);
    }

    @SafeVarargs
    public static <S> VerticalMultiBlockLayer<S> ofRows(List<VerticalMultiBlockPredicate<S>>... rows) {
        return new VerticalMultiBlockLayer<>(List.of(rows), -1, -1);
    }

    public VerticalMultiBlockPredicate<S> predicateAt(int x, int z) {
        if (x < 0 || x >= this.width || z < 0 || z >= this.depth) {
            throw new IndexOutOfBoundsException("Layer coordinate outside bounds: " + x + "," + z);
        }
        return this.rows.get(z).get(x);
    }

    private static <S> List<List<VerticalMultiBlockPredicate<S>>> copyRows(List<List<VerticalMultiBlockPredicate<S>>> inputRows) {
        if (inputRows.isEmpty()) {
            throw new IllegalArgumentException("Vertical multiblock layer must have at least one row");
        }

        int width = -1;
        ArrayList<List<VerticalMultiBlockPredicate<S>>> copied = new ArrayList<>(inputRows.size());
        for (List<VerticalMultiBlockPredicate<S>> inputRow : inputRows) {
            if (inputRow.isEmpty()) {
                throw new IllegalArgumentException("Vertical multiblock layer rows must not be empty");
            }
            if (width < 0) {
                width = inputRow.size();
            } else if (inputRow.size() != width) {
                throw new IllegalArgumentException("Vertical multiblock layer rows must have equal width");
            }

            ArrayList<VerticalMultiBlockPredicate<S>> row = new ArrayList<>(inputRow.size());
            for (VerticalMultiBlockPredicate<S> predicate : inputRow) {
                row.add(predicate);
            }
            copied.add(List.copyOf(row));
        }
        return List.copyOf(copied);
    }
}
