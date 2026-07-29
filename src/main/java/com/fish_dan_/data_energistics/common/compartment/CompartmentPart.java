package com.fish_dan_.data_energistics.common.compartment;

import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockContext;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockController;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPart;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime contract for a compartment block entity participating in a multiblock.
 */
public interface CompartmentPart extends VerticalMultiBlockPart {

    /**
     * Returns this compartment's role.
     */
    CompartmentType compartmentType();

    /**
     * Returns the absolute multiblock position for host registration.
     */
    VerticalMultiBlockPos compartmentPos();

    /**
     * Returns the currently bound compartment host, if the owning structure is formed.
     */
    @Nullable
    CompartmentHost compartmentHost();

    /**
     * Returns the currently bound structure name, if the owning structure is formed.
     */
    @Nullable
    default String compartmentStructureName() {
        return null;
    }

    /**
     * Returns this compartment's canonical key-amount storage.
     */
    CompartmentStorage compartmentStorage();

    /**
     * Returns whether the compartment is currently bound to a formed structure.
     */
    default boolean isCompartmentBound() {
        return compartmentHost() != null;
    }

    /**
     * Binds this part to a formed host structure.
     */
    default void compartment$bindToHost(String structureName, CompartmentHost host) {
        host.compartmentHost$addCompartment(structureName, this);
    }

    /**
     * Returns the current opaque registration identity when this part needs identity-aware lifecycle callbacks.
     */
    @Nullable
    default CompartmentBindingHandle compartment$bindingHandle() {
        return null;
    }

    /**
     * Returns whether a previously failed release for this exact host and structure must be retried by the binder.
     */
    default boolean compartment$requiresBindingRetry(String structureName, CompartmentHost host) {
        return false;
    }

    /**
     * Unbinds the exact registration represented by a previously captured identity handle.
     *
     * <p>
     * Parts without an identity-aware lifecycle keep returning {@code null} from {@link #compartment$bindingHandle()},
     * so callers retain the original named overload for them.
     * </p>
     */
    default void compartment$unbindFromHost(CompartmentBindingHandle bindingHandle) {
        if (bindingHandle == null) {
            throw new IllegalArgumentException("Compartment binding handle must not be null");
        }
        throw new IllegalArgumentException("Compartment part does not support identity-aware unbinding");
    }

    /**
     * Unbinds this part from an invalidated host structure.
     */
    default void compartment$unbindFromHost(String structureName, CompartmentHost host) {
        host.compartmentHost$removeCompartment(structureName, this);
    }

    @Override
    default void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                      VerticalMultiBlockContext<?> context) {
        verticalMultiBlock$addedToController(controller, context.structureName(), context);
    }

    @Override
    default void verticalMultiBlock$addedToController(VerticalMultiBlockController controller,
                                                      String structureName,
                                                      VerticalMultiBlockContext<?> context) {
        if (controller instanceof CompartmentHost host) {
            compartment$bindToHost(structureName, host);
        }
    }

    @Override
    default void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller) {
        if (controller instanceof CompartmentHost host) {
            compartment$unbindFromHost(compartmentStructureName(), host);
        }
    }

    @Override
    default void verticalMultiBlock$removedFromController(VerticalMultiBlockController controller,
                                                          String structureName) {
        if (controller instanceof CompartmentHost host) {
            compartment$unbindFromHost(structureName, host);
        }
    }
}
