package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineScope;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationContext;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationPreparation;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PreparedPatternUploadChange;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSource;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationSourceContext;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderWorkstationTarget;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.fish_dan_.data_energistics.common.entrypoint.machine.PatternUploadWorkstationAdapters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Resolves actual provider routes and prepares machine-owned upload changes for one exact provider leaf. */
final class PatternProviderUploadWorkstations {

    private PatternProviderUploadWorkstations() {}

    static Preparation prepare(ServerPlayer player,
                               PatternContainer provider,
                               PatternProviderIdentity providerIdentity,
                               @Nullable PatternProviderWorkstationSource registeredSource,
                               IPatternDetails patternDetails,
                               @Nullable ResourceLocation recipeTypeId,
                               int requestedPatternCount) {
        ObjectList<PatternProviderWorkstationTarget> targets = resolveTargets(
                player,
                provider,
                providerIdentity,
                registeredSource,
                patternDetails,
                recipeTypeId,
                requestedPatternCount);
        if (targets.isEmpty()) {
            return Preparation.accepted(ObjectLists.emptyList());
        }

        ObjectList<PreparedWorkstationChange> changes = new ObjectArrayList<>(targets.size());
        ReferenceSet<BlockEntity> seenBlockEntities = new ReferenceOpenHashSet<>();
        Reference2ObjectMap<BlockEntity, EnumSet<Direction>> seenInputSides = new Reference2ObjectOpenHashMap<>();
        for (PatternProviderWorkstationTarget target : targets) {
            BlockEntity workstation = target.workstation();
            if (workstation.isRemoved() || !(workstation.getLevel() instanceof ServerLevel workstationLevel) ||
                    workstationLevel.getBlockEntity(workstation.getBlockPos()) != workstation) {
                continue;
            }

            PatternUploadWorkstationRegistration registration = PatternUploadWorkstationAdapters.resolve(
                    workstation);
            if (registration == null) {
                continue;
            }
            if (!markUnseen(registration.scope(), workstation, target.inputSide(), seenBlockEntities, seenInputSides)) {
                continue;
            }

            PatternUploadWorkstationContext context = new PatternUploadWorkstationContext(
                    player,
                    provider,
                    providerIdentity,
                    workstationLevel,
                    workstation.getBlockPos(),
                    target.inputSide(),
                    workstation,
                    patternDetails,
                    recipeTypeId,
                    requestedPatternCount);
            PatternUploadWorkstationPreparation preparation = PatternUploadWorkstationAdapters.prepare(
                    registration,
                    context);
            switch (preparation) {
                case PatternUploadWorkstationPreparation.Pass ignored -> {}
                case PatternUploadWorkstationPreparation.Prepared prepared -> changes.add(new PreparedWorkstationChange(
                        prepared.change(),
                        registration.registrationId(),
                        workstationLevel.dimension().location(),
                        workstation.getBlockPos(),
                        target.inputSide()));
                case PatternUploadWorkstationPreparation.Rejected rejected -> {
                    return Preparation.rejected(rejected.message());
                }
            }
        }
        return Preparation.accepted(ObjectLists.unmodifiable(changes));
    }

    private static ObjectList<PatternProviderWorkstationTarget> resolveTargets(
                                                                               ServerPlayer player,
                                                                               PatternContainer provider,
                                                                               PatternProviderIdentity providerIdentity,
                                                                               @Nullable PatternProviderWorkstationSource registeredSource,
                                                                               IPatternDetails patternDetails,
                                                                               @Nullable ResourceLocation recipeTypeId,
                                                                               int requestedPatternCount) {
        PatternProviderWorkstationSource source = registeredSource;
        if (source == null && provider instanceof PatternProviderWorkstationSource directSource) {
            source = directSource;
        }
        if (source != null) {
            ObjectList<PatternProviderWorkstationTarget> resolvedTargets = source.resolveWorkstations(
                    new PatternProviderWorkstationSourceContext(
                            player,
                            provider,
                            providerIdentity,
                            patternDetails,
                            recipeTypeId,
                            requestedPatternCount));
            if (resolvedTargets == null) {
                throw new IllegalStateException("Pattern provider workstation source returned null");
            }
            ObjectList<PatternProviderWorkstationTarget> targets = new ObjectArrayList<>(resolvedTargets.size());
            for (PatternProviderWorkstationTarget target : resolvedTargets) {
                if (target == null) {
                    throw new IllegalStateException("Pattern provider workstation source returned a null target");
                }
                targets.add(target);
            }
            return ObjectLists.unmodifiable(targets);
        }
        return resolveStandardTargets(provider);
    }

    private static ObjectList<PatternProviderWorkstationTarget> resolveStandardTargets(PatternContainer provider) {
        if (!(provider instanceof PatternProviderLogicHost providerHost) ||
                !(providerHost.getLogic() instanceof PatternProviderBatchAccess logic)) {
            return ObjectLists.emptyList();
        }
        PatternProviderLogicHost host = logic.dataEnergistics$getHost();
        BlockEntity hostBlockEntity = host.getBlockEntity();
        if (!(hostBlockEntity.getLevel() instanceof ServerLevel level)) {
            return ObjectLists.emptyList();
        }

        var activeSides = logic.dataEnergistics$invokeGetActiveSides();
        ObjectList<PatternProviderWorkstationTarget> targets = new ObjectArrayList<>(activeSides.size());
        for (Direction side : Direction.values()) {
            if (!activeSides.contains(side)) {
                continue;
            }
            BlockEntity workstation = level.getBlockEntity(hostBlockEntity.getBlockPos().relative(side));
            if (workstation != null) {
                targets.add(new PatternProviderWorkstationTarget(workstation, side.getOpposite()));
            }
        }
        return targets;
    }

    private static boolean markUnseen(CraftingMachineScope scope,
                                      BlockEntity workstation,
                                      Direction inputSide,
                                      ReferenceSet<BlockEntity> seenBlockEntities,
                                      Reference2ObjectMap<BlockEntity, EnumSet<Direction>> seenInputSides) {
        if (scope == CraftingMachineScope.BLOCK_ENTITY) {
            return seenBlockEntities.add(workstation);
        }
        EnumSet<Direction> sides = seenInputSides.get(workstation);
        if (sides == null) {
            sides = EnumSet.noneOf(Direction.class);
            seenInputSides.put(workstation, sides);
        }
        return sides.add(inputSide);
    }

    record PreparedWorkstationChange(PreparedPatternUploadChange change,
                                     ResourceLocation registrationId,
                                     ResourceLocation dimensionId,
                                     BlockPos workstationPosition,
                                     Direction inputSide) {

        PreparedWorkstationChange {
            workstationPosition = workstationPosition.immutable();
        }
    }

    record Preparation(ObjectList<PreparedWorkstationChange> changes,
                       @Nullable Component rejectionMessage) {

        static Preparation accepted(ObjectList<PreparedWorkstationChange> changes) {
            return new Preparation(changes, null);
        }

        static Preparation rejected(Component message) {
            return new Preparation(ObjectLists.emptyList(), message);
        }

        boolean rejected() {
            return this.rejectionMessage != null;
        }

        Component rejectionMessageOrThrow() {
            if (this.rejectionMessage == null) {
                throw new IllegalStateException("Accepted workstation preparation has no rejection message");
            }
            return this.rejectionMessage;
        }
    }
}
