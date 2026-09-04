package com.fish_dan_.data_energistics.common.entrypoint.machine;

import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspection;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationInspectionContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationPreparation;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Installed immutable lookup for machine-owned pattern-upload transactions. */
public final class PatternUploadWorkstationAdapters {

    private static Object2ObjectMap<ResourceLocation, PatternUploadWorkstationRegistration> registrations = Object2ObjectMaps.emptyMap();
    private static boolean installed;

    private PatternUploadWorkstationAdapters() {}

    /** Installs the frozen common-setup declarations exactly once. */
    public static synchronized void install(List<PatternUploadWorkstationRegistration> declarations) {
        if (installed) {
            throw new IllegalStateException("Pattern upload workstation adapters are already installed");
        }
        Object2ObjectMap<ResourceLocation, PatternUploadWorkstationRegistration> indexed = new Object2ObjectLinkedOpenHashMap<>(
                declarations.size());
        for (PatternUploadWorkstationRegistration declaration : declarations) {
            ResourceLocation blockEntityTypeId = declaration.blockEntityTypeId();
            PatternUploadWorkstationRegistration existing = indexed.putIfAbsent(blockEntityTypeId, declaration);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate frozen pattern upload workstation block-entity type: " + blockEntityTypeId);
            }
        }
        registrations = Object2ObjectMaps.unmodifiable(indexed);
        installed = true;
    }

    /** Resolves the exact registration selected by a live workstation block-entity type. */
    public static @Nullable PatternUploadWorkstationRegistration resolve(BlockEntity workstation) {
        requireInstalled();
        ResourceLocation blockEntityTypeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(workstation.getType());
        return registrations.get(blockEntityTypeId);
    }

    /** Invokes one already-resolved registration at the untrusted plugin callback boundary. */
    public static PatternUploadWorkstationPreparation prepare(PatternUploadWorkstationRegistration registration,
                                                              PatternUploadWorkstationContext context) {
        PatternUploadWorkstationPreparation preparation = Objects.requireNonNull(
                registration.adapter().prepare(context),
                "Pattern upload workstation adapter returned null");
        return preparation;
    }

    /** Invokes one read-only live-variant inspection at the untrusted plugin callback boundary. */
    public static PatternUploadWorkstationInspection inspect(PatternUploadWorkstationRegistration registration,
                                                             PatternUploadWorkstationInspectionContext context) {
        return Objects.requireNonNull(
                registration.adapter().inspect(context),
                "Pattern upload workstation adapter returned null inspection");
    }

    private static void requireInstalled() {
        if (!installed) {
            throw new IllegalStateException("Pattern upload workstation adapters are not installed");
        }
    }
}
