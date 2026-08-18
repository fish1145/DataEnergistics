package com.fish_dan_.data_energistics.blockentity.tower.energy.registry;

import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.VerifiedUnlimitedEnergyAccess;
import com.fish_dan_.data_energistics.integration.tower.energy.appflux.AppliedFluxEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.brandonscore.BrandonsCoreEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.brandonscore.BrandonsCoreEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.mekanism.MekanismEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.modernindustrialization.ModernIndustrializationEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.modernindustrialization.ModernIndustrializationEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.integration.tower.energy.oritech.OritechEnergyBridge;
import com.fish_dan_.data_energistics.integration.tower.energy.oritech.OritechEnergyEndpointIntegration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered registry for tower energy endpoint integrations.
 *
 * <p>
 * Capability lookup order and operation dispatch are both driven by registered strategies, so adding a Mod does
 * not require another Mod-specific branch in the tower resolver or transfer engine.
 * </p>
 */
public final class TowerEnergyEndpointIntegrationRegistry {

    private final List<TowerEnergyEndpointIntegration> integrations;
    private final List<TowerEnergyEndpointIntegration> lookupIntegrations;

    private TowerEnergyEndpointIntegrationRegistry(List<TowerEnergyEndpointIntegration> integrations) {
        this.integrations = List.copyOf(integrations);
        ArrayList<TowerEnergyEndpointIntegration> lookupOrder = new ArrayList<>(integrations);
        lookupOrder.sort(Comparator.comparingInt(TowerEnergyEndpointIntegration::lookupOrder));
        this.lookupIntegrations = List.copyOf(lookupOrder);
    }

    /**
     * Creates the production registry with all optional integrations in their precedence order.
     */
    public static TowerEnergyEndpointIntegrationRegistry createDefault() {
        return DefaultHolder.INSTANCE;
    }

    private static TowerEnergyEndpointIntegrationRegistry buildDefault() {
        UnlimitedEnergyAccess unlimitedEnergy = new VerifiedUnlimitedEnergyAccess();
        Builder builder = builder();
        if (ModFlags.isBrandonsCoreLoaded()) {
            builder.register(new BrandonsCoreEnergyEndpointIntegration(new BrandonsCoreEnergyBridge()));
        }
        if (ModFlags.isModernIndustrializationEnergySupportLoaded()) {
            builder.register(new ModernIndustrializationEnergyEndpointIntegration(
                    new ModernIndustrializationEnergyBridge()));
        }
        if (ModFlags.isMekanismLoaded()) {
            builder.register(new MekanismEnergyEndpointIntegration());
        }
        if (ModFlags.isAppFluxEnergySupportLoaded()) {
            builder.register(new AppliedFluxEnergyEndpointIntegration());
        }
        if (ModFlags.isOritechEnergySupportLoaded()) {
            builder.register(new OritechEnergyEndpointIntegration(
                    new OritechEnergyBridge(), unlimitedEnergy));
        }
        return builder.register(new NeoForgeEnergyEndpointIntegration(unlimitedEnergy)).build();
    }

    private static final class DefaultHolder {

        private static final TowerEnergyEndpointIntegrationRegistry INSTANCE = buildDefault();

        private DefaultHolder() {}
    }

    /**
     * Creates a registry builder for tests or a narrowly scoped runtime composition.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Finds the highest-priority capability exposed at a position and side.
     */
    @Nullable
    public IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        for (TowerEnergyEndpointIntegration integration : this.lookupIntegrations) {
            IEnergyStorage storage = integration.findEnergyStorage(level, position, side);
            if (storage != null) {
                return storage;
            }
        }
        return null;
    }

    /**
     * Resolves the registered operation strategy for one route.
     */
    public TowerEnergyEndpointIntegration resolve(TowerEnergyEndpointContext context) {
        for (TowerEnergyEndpointIntegration integration : this.integrations) {
            if (integration.supports(context)) {
                return integration;
            }
        }
        throw new IllegalStateException(
                "No registered tower energy integration supports " + context.storage().getClass().getName());
    }

    /**
     * Resolves the physical backing identity through the registered strategy.
     */
    public Object backingIdentity(TowerEnergyEndpointContext context) {
        return resolve(context).backingIdentity(context);
    }

    /**
     * Fluent registry builder.
     */
    public static final class Builder {

        private final ArrayList<TowerEnergyEndpointIntegration> integrations = new ArrayList<>();

        private Builder() {}

        /**
         * Adds one operation and optional capability lookup strategy.
         */
        public Builder register(TowerEnergyEndpointIntegration integration) {
            if (integration == null) {
                throw new IllegalArgumentException("Tower energy integration cannot be null");
            }
            this.integrations.add(integration);
            return this;
        }

        /**
         * Builds an immutable registry and rejects duplicate registration identifiers.
         */
        public TowerEnergyEndpointIntegrationRegistry build() {
            Set<String> ids = new HashSet<>();
            for (TowerEnergyEndpointIntegration integration : this.integrations) {
                if (!ids.add(integration.id())) {
                    throw new IllegalStateException("Duplicate tower energy integration id: " + integration.id());
                }
            }
            if (this.integrations.isEmpty()) {
                throw new IllegalStateException("Tower energy integration registry cannot be empty");
            }
            return new TowerEnergyEndpointIntegrationRegistry(this.integrations);
        }
    }
}
