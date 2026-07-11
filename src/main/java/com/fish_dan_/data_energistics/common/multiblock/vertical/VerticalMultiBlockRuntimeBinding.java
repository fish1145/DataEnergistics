package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies scan results to a controller and its parts.
 *
 * <p>
 * The binding is intentionally world-agnostic. Callers provide the controller instance, the scanner, and a lookup
 * from matched positions to participating parts.
 *
 * @param <S> block state representation used by the scanner
 */
public record VerticalMultiBlockRuntimeBinding<S>(VerticalMultiBlockScanner<S> scanner) {

    public boolean requestRecheck(VerticalMultiBlockController controller,
                                  VerticalMultiBlockDefinition<S> definition,
                                  VerticalMultiBlockPos controllerPos,
                                  PartLookup partLookup) {
        if (!definition.id().equals(controller.verticalMultiBlock$getDefinitionId())) {
            throw new IllegalStateException("Vertical multiblock controller expected " + controller.verticalMultiBlock$getDefinitionId() + " but received " + definition.id());
        }

        Optional<VerticalMultiBlockContext<S>> result = this.scanner.scan(definition, controllerPos);
        if (result.isEmpty()) {
            invalidate(controller, partLookup, definition.structureName(), "No valid vertical multiblock match");
            return false;
        }

        VerticalMultiBlockContext<S> context = result.orElseThrow();
        bind(controller, context, partLookup);
        return true;
    }

    public void invalidate(VerticalMultiBlockController controller, PartLookup partLookup, String reason) {
        VerticalMultiBlockRuntimeState state = controller.verticalMultiBlock$getRuntimeState();
        String structureName = state.formed() ? state.structureName() : VerticalMultiBlockDefinition.DEFAULT_STRUCTURE_NAME;
        invalidate(controller, partLookup, structureName, reason);
    }

    public void invalidate(VerticalMultiBlockController controller,
                           PartLookup partLookup,
                           String structureName,
                           String reason) {
        VerticalMultiBlockRuntimeState state = controller.verticalMultiBlock$getRuntimeState(structureName);
        String previousStructureName = state.structureName();
        if (state.formed()) {
            for (VerticalMultiBlockPart part : partLookup.get(state.matchedPositions())) {
                part.verticalMultiBlock$removedFromController(controller, previousStructureName);
            }
        }
        controller.verticalMultiBlock$setRuntimeState(structureName, VerticalMultiBlockRuntimeState.unformed());
        controller.verticalMultiBlock$onStructureInvalid(structureName, reason);
    }

    public void bind(VerticalMultiBlockController controller,
                     VerticalMultiBlockContext<S> context,
                     PartLookup partLookup) {
        String structureName = context.structureName();
        VerticalMultiBlockRuntimeState previous = controller.verticalMultiBlock$getRuntimeState(structureName);
        String previousStructureName = previous.structureName();
        if (previous.formed() && !previous.matchedPositions().isEmpty()) {
            for (VerticalMultiBlockPart part : partLookup.get(previous.matchedPositions())) {
                part.verticalMultiBlock$removedFromController(controller, previousStructureName);
            }
        }

        List<VerticalMultiBlockPos> matchedPositions = List.copyOf(context.matchedPositions());
        VerticalMultiBlockRuntimeState runtimeState = new VerticalMultiBlockRuntimeState(
                true,
                context.definition().id(),
                structureName,
                context.height(),
                matchedPositions);
        controller.verticalMultiBlock$setRuntimeState(structureName, runtimeState);
        controller.verticalMultiBlock$onStructureFormed(structureName, context);

        for (VerticalMultiBlockPart part : partLookup.get(matchedPositions)) {
            part.verticalMultiBlock$addedToController(controller, structureName, context);
        }
    }

    @FunctionalInterface
    public interface PartLookup {

        Collection<VerticalMultiBlockPart> get(List<VerticalMultiBlockPos> matchedPositions);
    }

    public static PartLookup emptyPartLookup() {
        return matchedPositions -> List.of();
    }

    public static PartLookup fromSinglePart(VerticalMultiBlockPos pos, VerticalMultiBlockPart part) {
        return matchedPositions -> matchedPositions.contains(pos) ? List.of(part) : List.of();
    }

    public static PartLookup fromParts(Map<VerticalMultiBlockPos, ? extends VerticalMultiBlockPart> parts) {
        return matchedPositions -> {
            ArrayList<VerticalMultiBlockPart> resolved = new ArrayList<>(matchedPositions.size());
            for (VerticalMultiBlockPos pos : matchedPositions) {
                VerticalMultiBlockPart part = parts.get(pos);
                if (part != null) {
                    resolved.add(part);
                }
            }
            return List.copyOf(resolved);
        };
    }
}
