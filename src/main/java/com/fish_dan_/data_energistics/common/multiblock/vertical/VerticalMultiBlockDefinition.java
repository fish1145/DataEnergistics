package com.fish_dan_.data_energistics.common.multiblock.vertical;

import com.fish_dan_.data_energistics.Data_Energistics;

import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;

/**
 * Defines one ECO-style vertical multiblock structure.
 *
 * @param <S> block state representation used by the caller
 */
public record VerticalMultiBlockDefinition<S>(String id,
                                              VerticalMultiBlockLayer<S> bottomLayer,
                                              VerticalMultiBlockLayer<S> middleLayer,
                                              VerticalMultiBlockLayer<S> topLayer,
                                              List<VerticalMultiBlockPos> controllerCandidates,
                                              int minHeight,
                                              int maxHeight) {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    public VerticalMultiBlockDefinition {
        id = requireId(id);
        bottomLayer = Objects.requireNonNull(bottomLayer, "bottomLayer");
        middleLayer = Objects.requireNonNull(middleLayer, "middleLayer");
        topLayer = Objects.requireNonNull(topLayer, "topLayer");
        controllerCandidates = List.copyOf(controllerCandidates);
        if (controllerCandidates.isEmpty()) {
            throw new IllegalStateException("Vertical multiblock " + id + " has no controller candidates");
        }
        if (minHeight < 2) {
            throw new IllegalArgumentException("Vertical multiblock " + id + " minHeight must be at least 2");
        }
        if (maxHeight < minHeight) {
            throw new IllegalArgumentException("Vertical multiblock " + id + " maxHeight must be >= minHeight");
        }
        validateLayerDimensions(id, bottomLayer, middleLayer, topLayer);
        validateControllerCandidates(id, controllerCandidates, bottomLayer.width(), bottomLayer.depth(), maxHeight);
    }

    public int middleLayerCount(int height) {
        validateHeight(height);
        return height - 2;
    }

    public int width() {
        return this.bottomLayer.width();
    }

    public int depth() {
        return this.bottomLayer.depth();
    }

    private static void validateLayerDimensions(String id,
                                                VerticalMultiBlockLayer<?> bottomLayer,
                                                VerticalMultiBlockLayer<?> middleLayer,
                                                VerticalMultiBlockLayer<?> topLayer) {
        int width = bottomLayer.width();
        int depth = bottomLayer.depth();
        if (middleLayer.width() != width || middleLayer.depth() != depth || topLayer.width() != width || topLayer.depth() != depth) {
            throw new IllegalArgumentException("Vertical multiblock " + id + " layer sizes must match");
        }
    }

    private static void validateControllerCandidates(String id,
                                                     List<VerticalMultiBlockPos> controllerCandidates,
                                                     int width,
                                                     int depth,
                                                     int maxHeight) {
        for (VerticalMultiBlockPos candidate : controllerCandidates) {
            if (candidate.x() < 0 || candidate.x() >= width || candidate.z() < 0 || candidate.z() >= depth) {
                throw new IllegalArgumentException("Vertical multiblock " + id + " controller candidate outside layer bounds: " + candidate);
            }
            if (candidate.y() < 0 || candidate.y() >= maxHeight) {
                throw new IllegalArgumentException("Vertical multiblock " + id + " controller candidate outside height bounds: " + candidate);
            }
        }
    }

    private void validateHeight(int height) {
        if (height < this.minHeight || height > this.maxHeight) {
            throw new IllegalArgumentException("Height " + height + " outside vertical multiblock " + this.id + " bounds");
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vertical multiblock id must not be blank");
        }
        return id;
    }

    public static final class Builder<S> {

        private final String id;
        private VerticalMultiBlockLayer<S> bottomLayer;
        private VerticalMultiBlockLayer<S> middleLayer;
        private VerticalMultiBlockLayer<S> topLayer;
        private List<VerticalMultiBlockPos> controllerCandidates = List.of();
        private int minHeight = 2;
        private int maxHeight = 16;

        private Builder(String id) {
            this.id = id;
        }

        public Builder<S> bottomLayer(VerticalMultiBlockLayer<S> bottomLayer) {
            this.bottomLayer = bottomLayer;
            return this;
        }

        public Builder<S> middleLayer(VerticalMultiBlockLayer<S> middleLayer) {
            this.middleLayer = middleLayer;
            return this;
        }

        public Builder<S> topLayer(VerticalMultiBlockLayer<S> topLayer) {
            this.topLayer = topLayer;
            return this;
        }

        public Builder<S> controllerCandidates(List<VerticalMultiBlockPos> controllerCandidates) {
            this.controllerCandidates = List.copyOf(controllerCandidates);
            return this;
        }

        public Builder<S> heightRange(int minHeight, int maxHeight) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            return this;
        }

        public VerticalMultiBlockDefinition<S> build() {
            if (this.bottomLayer == null || this.middleLayer == null || this.topLayer == null) {
                String message = "Vertical multiblock " + this.id + " is missing one or more layer templates";
                LOGGER.error(message);
                throw new IllegalStateException(message);
            }
            return new VerticalMultiBlockDefinition<>(
                    this.id,
                    this.bottomLayer,
                    this.middleLayer,
                    this.topLayer,
                    this.controllerCandidates,
                    this.minHeight,
                    this.maxHeight);
        }
    }

    public static <S> Builder<S> builder(String id) {
        return new Builder<>(id);
    }
}
