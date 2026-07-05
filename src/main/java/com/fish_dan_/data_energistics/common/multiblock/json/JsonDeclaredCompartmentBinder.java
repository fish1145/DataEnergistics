package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default reusable binder for JSON-declared multiblock compartments.
 */
public final class JsonDeclaredCompartmentBinder implements JsonMultiBlockCompartmentBinder {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    @Nullable
    @Override
    public PatternDiagnostic validate(StructureWorldView world,
                                      StructureMatchResult result,
                                      Map<BlockPos, CompartmentType> declaredCompartments) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(declaredCompartments, "declaredCompartments");

        for (Map.Entry<BlockPos, CompartmentType> entry : declaredCompartments.entrySet()) {
            PatternDiagnostic diagnostic = validateDeclaredPart(world, entry.getKey(), entry.getValue());
            if (diagnostic != null) {
                return diagnostic;
            }
        }
        for (BlockPos pos : result.positions()) {
            if (declaredCompartments.containsKey(pos)) {
                continue;
            }
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CompartmentPart part) {
                String message = "Structure matched undeclared compartment " + part.compartmentType().id() +
                        " at " + pos;
                LOGGER.warn(message);
                return PatternDiagnostic.of("compartment_part_undeclared", message, pos, List.of());
            }
        }
        return null;
    }

    @Override
    public void bind(StructureWorldView world,
                     String structureName,
                     CompartmentHost host,
                     Map<BlockPos, CompartmentType> declaredCompartments) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(declaredCompartments, "declaredCompartments");

        for (Map.Entry<BlockPos, CompartmentType> entry : declaredCompartments.entrySet()) {
            bindDeclaredPart(world, structureName, host, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void ensureBound(StructureWorldView world,
                            String structureName,
                            CompartmentHost host,
                            Map<BlockPos, CompartmentType> declaredCompartments) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(declaredCompartments, "declaredCompartments");

        Map<BlockPos, CompartmentPart> currentDeclaredParts = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CompartmentType> entry : declaredCompartments.entrySet()) {
            currentDeclaredParts.put(entry.getKey(), requireDeclaredPart(world, entry.getKey(), entry.getValue()));
        }

        for (CompartmentPart registeredPart : List.copyOf(host.compartmentHost$getCompartments(structureName))) {
            CompartmentPart currentPart = currentDeclaredParts.get(toBlockPos(registeredPart.compartmentPos()));
            if (registeredPart != currentPart) {
                registeredPart.compartment$unbindFromHost(structureName, host);
            }
        }

        for (CompartmentPart currentPart : currentDeclaredParts.values()) {
            if (!host.compartmentHost$getCompartments(structureName).contains(currentPart) ||
                    currentPart.compartmentHost() != host ||
                    !structureName.equals(currentPart.compartmentStructureName())) {
                currentPart.compartment$bindToHost(structureName, host);
            }
        }
    }

    @Override
    public void unbind(String structureName, CompartmentHost host) {
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(host, "host");

        for (CompartmentPart part : List.copyOf(host.compartmentHost$getCompartments(structureName))) {
            part.compartment$unbindFromHost(structureName, host);
        }
    }

    @Nullable
    private static PatternDiagnostic validateDeclaredPart(StructureWorldView world,
                                                          BlockPos pos,
                                                          CompartmentType declaredType) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CompartmentPart part)) {
            String message = "JSON multiblock declared a compartment at " + pos +
                    " but no compartment part exists";
            LOGGER.warn(message);
            return PatternDiagnostic.of("compartment_part_missing", message, pos, List.of(declaredType.id()));
        }
        if (part.compartmentType() != declaredType) {
            String message = "JSON multiblock declared compartment type " + declaredType.id() + " at " + pos +
                    " but found " + part.compartmentType().id();
            LOGGER.warn(message);
            return PatternDiagnostic.of("compartment_part_mismatch", message, pos, List.of(declaredType.id()));
        }
        return null;
    }

    private static void bindDeclaredPart(StructureWorldView world,
                                         String structureName,
                                         CompartmentHost host,
                                         BlockPos pos,
                                         CompartmentType declaredType) {
        requireDeclaredPart(world, pos, declaredType).compartment$bindToHost(structureName, host);
    }

    private static CompartmentPart requireDeclaredPart(StructureWorldView world,
                                                       BlockPos pos,
                                                       CompartmentType declaredType) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CompartmentPart part)) {
            throw failDeclaredPart("JSON multiblock declared a compartment at " + pos +
                    " but no compartment part exists");
        }
        if (part.compartmentType() != declaredType) {
            throw failDeclaredPart("JSON multiblock declared compartment type " + declaredType.id() + " at " + pos +
                    " but found " + part.compartmentType().id());
        }
        return part;
    }

    private static IllegalStateException failDeclaredPart(String message) {
        LOGGER.error(message);
        return new IllegalStateException(message);
    }

    private static BlockPos toBlockPos(VerticalMultiBlockPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }
}
