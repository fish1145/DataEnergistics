package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.core.BlockPos;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Binds JSON-declared compartment symbols to a multiblock host after MDLib matching succeeds.
 *
 * <p>
 * JSON multiblocks decide which symbols are compartment positions through
 * {@code metadata.compartments}. This contract keeps the runtime validation and host binding reusable for any JSON
 * multiblock controller that implements {@link CompartmentHost}.
 */
public interface JsonMultiBlockCompartmentBinder {

    /**
     * Validates that every declared compartment position contains the declared compartment type.
     *
     * <p>
     * This method is called after a normal MDLib match and before the host marks the structure as formed, so a
     * compartment mismatch can still fail the structure instead of forming a partially usable host.
     *
     * @param world                matched world view used to resolve block entities
     * @param result               successful MDLib match result that provides matched positions
     * @param declaredCompartments positions collected from JSON compartment predicates
     * @return diagnostic when the structure must fail, otherwise {@code null}
     */
    @Nullable
    PatternDiagnostic validate(StructureWorldView world,
                               StructureMatchResult result,
                               Map<BlockPos, CompartmentType> declaredCompartments);

    /**
     * Binds all declared compartment parts to the host for a formed named structure.
     *
     * <p>
     * The host owns the resulting compartment list; the binder only resolves the parts from JSON-declared positions
     * and dispatches the bind callback to each part.
     *
     * @param world                matched world view used to resolve block entities
     * @param structureName        formed structure name
     * @param host                 controller accepting compartment parts
     * @param declaredCompartments positions collected from JSON compartment predicates
     */
    void bind(StructureWorldView world,
              String structureName,
              CompartmentHost host,
              Map<BlockPos, CompartmentType> declaredCompartments);

    /**
     * Recreates missing host bindings when a structure remains formed across a recheck.
     *
     * <p>
     * Persistent hosts may reload with an already-formed structure but an empty runtime compartment list. This method
     * fills only that missing runtime state without duplicating existing bindings.
     *
     * @param world                matched world view used to resolve block entities
     * @param structureName        formed structure name
     * @param host                 controller accepting compartment parts
     * @param declaredCompartments positions collected from JSON compartment predicates
     */
    void ensureBound(StructureWorldView world,
                     String structureName,
                     CompartmentHost host,
                     Map<BlockPos, CompartmentType> declaredCompartments);

    /**
     * Unbinds every currently registered compartment from a named host structure.
     *
     * <p>
     * This is called when the structure invalidates or reforms with different positions, guaranteeing ME IO and host
     * logic stop seeing stale compartment parts.
     *
     * @param structureName invalidated structure name
     * @param host          controller that previously accepted compartment parts
     */
    void unbind(String structureName, CompartmentHost host);
}
