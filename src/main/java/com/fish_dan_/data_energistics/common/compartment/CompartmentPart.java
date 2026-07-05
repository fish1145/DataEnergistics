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
            String structureName = compartmentStructureName();
            if (structureName == null) {
                throw new IllegalStateException("Cannot unbind compartment without a bound structure name");
            }
            compartment$unbindFromHost(structureName, host);
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
