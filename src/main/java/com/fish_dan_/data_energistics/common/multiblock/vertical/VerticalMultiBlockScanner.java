package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.Optional;

/**
 * Detects ECO-style vertical multiblocks from a controller position.
 *
 * <p>
 * The scanner tries every configured controller candidate, every allowed height, and every horizontal direction.
 * It returns the first full match and records the matched absolute positions in the context.
 *
 * @param <S> block state representation used by the caller
 */
public record VerticalMultiBlockScanner<S>(VerticalMultiBlockBlockStateLookup<S> stateLookup) {

    public Optional<VerticalMultiBlockContext<S>> scan(VerticalMultiBlockDefinition<S> definition, VerticalMultiBlockPos controllerPos) {
        for (int height = definition.maxHeight(); height >= definition.minHeight(); height--) {
            for (VerticalMultiBlockDirection direction : VerticalMultiBlockDirection.horizontal()) {
                for (VerticalMultiBlockPos candidate : definition.controllerCandidates()) {
                    if (candidate.y() >= height) {
                        continue;
                    }
                    VerticalMultiBlockPos rotatedCandidate = direction.rotate(candidate, definition.width(), definition.depth());
                    VerticalMultiBlockPos origin = controllerPos.subtract(rotatedCandidate);
                    VerticalMultiBlockContext<S> context = new VerticalMultiBlockContext<>(definition, origin, controllerPos, direction, height);
                    if (matches(definition, context)) {
                        if (hasOverflowLayer(definition, context)) {
                            context.fail("Vertical multiblock " + definition.id() + " exceeds maximum height " + definition.maxHeight());
                            continue;
                        }
                        return Optional.of(context);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean matches(VerticalMultiBlockDefinition<S> definition, VerticalMultiBlockContext<S> context) {
        if (!matchesLayer(definition.bottomLayer(), definition, context, 0, true)) {
            return false;
        }

        int middleCount = definition.middleLayerCount(context.height());
        for (int index = 0; index < middleCount; index++) {
            if (!matchesLayer(definition.middleLayer(), definition, context, 1 + index, true)) {
                return false;
            }
        }

        return matchesLayer(definition.topLayer(), definition, context, context.height() - 1, true);
    }

    private boolean hasOverflowLayer(VerticalMultiBlockDefinition<S> definition, VerticalMultiBlockContext<S> context) {
        if (context.height() < definition.maxHeight()) {
            return false;
        }
        return matchesLayer(definition.middleLayer(), definition, context, context.height(), false) || matchesLayer(definition.topLayer(), definition, context, context.height(), false);
    }

    private boolean matchesLayer(VerticalMultiBlockLayer<S> layer,
                                 VerticalMultiBlockDefinition<S> definition,
                                 VerticalMultiBlockContext<S> context,
                                 int y,
                                 boolean recordMatch) {
        for (int z = 0; z < layer.depth(); z++) {
            for (int x = 0; x < layer.width(); x++) {
                VerticalMultiBlockPos local = new VerticalMultiBlockPos(x, y, z);
                VerticalMultiBlockPos rotated = context.direction().rotate(local, definition.width(), definition.depth());
                VerticalMultiBlockPos worldPos = context.origin().offset(rotated);
                S state = this.stateLookup.get(worldPos);
                if (!layer.predicateAt(x, z).matches(state, worldPos)) {
                    if (recordMatch) {
                        context.fail("Mismatch at " + worldPos + " while checking " + definition.id());
                    }
                    return false;
                }
                if (recordMatch) {
                    context.addMatchedPosition(worldPos);
                }
            }
        }
        return true;
    }
}
