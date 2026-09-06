package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.PersistentTrinityPatternCore;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityItemAmount;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternCore.CachedPattern;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Ephemeral server callback adapter; actual assets, receipts and output queues belong to the movable core. */
public final class TrinityReusableCraftingHost implements Host {

    private final PersistentTrinityPatternCore core;
    private final PatternRoute route;
    private final ServerLevel level;
    private final BooleanSupplier authorized;
    private @Nullable CachedPattern validatedPattern;
    private long validatedRevision = -1;
    private @Nullable TrinityPatternIdentity publication;
    private @Nullable Binding materializedBinding;

    public TrinityReusableCraftingHost(PersistentTrinityPatternCore core, PatternRoute route, ServerLevel level, BooleanSupplier authorized) {
        this.core = core;
        this.route = route;
        this.level = level;
        this.authorized = authorized;
    }

    @Override
    public boolean isAvailable(Binding binding) {
        if (!authorized.getAsBoolean() || binding.identity().mode().isPresent()) {
            return false;
        }
        CachedPattern cached = core.cachedPattern(route.slot());
        if (cached == null) {
            return false;
        }
        IMolecularAssemblerSupportedPattern pattern = cached.details();
        if (pattern == null || binding.recipeId().isPresent() &&
                !binding.recipeId().orElseThrow().equals(cached.recipeResolution().recipeId().toString())) {
            return false;
        }
        if (cached != validatedPattern || cached.runtimeBindingRevision() != validatedRevision) {
            validatedPattern = cached;
            validatedRevision = cached.runtimeBindingRevision();
            publication = TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(pattern), level.registryAccess());
            materializedBinding = null;
        }
        if (!binding.publicationIdentity().equals(publication)) {
            return false;
        }
        if (!binding.equals(materializedBinding)) {
            var inputs = pattern.getInputs();
            for (var consumed : binding.consumed()) {
                if (!inputs[consumed.slot()].isValid(consumed.stack().what(), level)) {
                    return false;
                }
            }
            if (!NativeReusableCrafting.supports(pattern, binding)) {
                return false;
            }
            materializedBinding = binding;
        }
        return true;
    }

    @Override
    public NativeResult execute(Binding binding, Operation operation) {
        CachedPattern cached = core.cachedPattern(route.slot());
        IMolecularAssemblerSupportedPattern pattern = cached == null ? null : cached.details();
        if (pattern == null) {
            return NativeResult.paused();
        }
        return NativeReusableCrafting.execute(pattern, binding, operation, level, cached.recipeResolution().recipeId());
    }

    @Override
    public void acceptOutputs(Identity identity, List<GenericStack> outputs) {
        core.appendPendingOutputs(route, outputs.stream().map(stack -> new TrinityItemAmount((AEItemKey) stack.what(), stack.amount())).toList());
    }

    @Override
    public void persistChanges() {
        core.reusableStateChanged(route.slot());
    }
}
