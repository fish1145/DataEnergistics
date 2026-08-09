package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridge;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridgeException;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridgeInternal;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridNode;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomain;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomainChange;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.events.GridEvent;
import appeng.api.networking.pathing.IPathingService;
import appeng.me.Grid;
import appeng.me.GridNode;
import appeng.me.helpers.GridServiceContainer;
import com.google.common.collect.SetMultimap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps subordinate grids physically independent while bridging their enabled nodes into primary-grid services.
 */
@Mixin(Grid.class)
public abstract class GridMixin implements VirtualGridBridgeInternal {

    @Final
    @Shadow
    private SetMultimap<Class<?>, IGridNode> machines;

    @Final
    @Shadow
    private GridServiceContainer services;

    @Nullable
    @Unique
    private IGrid dataEnergistics$virtualPrimaryGrid;

    @Unique
    private final Set<IGridNode> dataEnergistics$outgoingNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private final Set<IGridNode> dataEnergistics$outgoingActiveNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private final Map<IGridNode, IGrid> dataEnergistics$incomingNodes = new IdentityHashMap<>();

    @Unique
    private int dataEnergistics$localServiceAccessDepth;

    @Override
    @Nullable
    public IGrid virtualPrimaryGrid() {
        return this.dataEnergistics$virtualPrimaryGrid;
    }

    @Override
    public Set<IGridNode> incomingVirtualMembers() {
        Set<IGridNode> snapshot = Collections.newSetFromMap(new IdentityHashMap<>());
        snapshot.addAll(this.dataEnergistics$incomingNodes.keySet());
        return Collections.unmodifiableSet(snapshot);
    }

    @Override
    public boolean containsIncomingVirtualMember(IGridNode node) {
        return this.dataEnergistics$incomingNodes.containsKey(node);
    }

    @Override
    public int physicalNodeCount() {
        return this.machines.size() - this.dataEnergistics$incomingNodes.size();
    }

    @Override
    public void replaceVirtualMembers(IGrid primaryGrid,
                                      Collection<? extends IGridNode> allNodes,
                                      Collection<? extends IGridNode> activeNodes) {
        if (primaryGrid == null || allNodes == null || activeNodes == null) {
            throw new IllegalArgumentException("Virtual grid replacement arguments must not be null");
        }
        IGrid localGrid = (IGrid) (Object) this;
        if (primaryGrid == localGrid) {
            throw new VirtualGridBridgeException("A grid cannot be subordinate to itself");
        }
        if (!(primaryGrid instanceof VirtualGridBridge primaryBridge)) {
            throw new VirtualGridBridgeException("Primary grid does not expose the virtual bridge");
        }
        if (primaryBridge.virtualPrimaryGrid() != null) {
            throw new VirtualGridBridgeException("A subordinate grid cannot become another grid's primary");
        }
        if (this.dataEnergistics$virtualPrimaryGrid != null && this.dataEnergistics$virtualPrimaryGrid != primaryGrid) {
            throw new VirtualGridBridgeException("Subordinate grid is already attached to a different primary");
        }

        Set<IGridNode> normalizedAll = dataEnergistics$identitySet(allNodes);
        Set<IGridNode> normalizedActive = dataEnergistics$identitySet(activeNodes);
        if (!normalizedAll.containsAll(normalizedActive)) {
            throw new VirtualGridBridgeException("Active virtual nodes must be part of the subordinate grid snapshot");
        }
        for (IGridNode node : normalizedAll) {
            if (node.getGrid() != localGrid) {
                throw new VirtualGridBridgeException("Virtual node does not belong to the subordinate grid");
            }
            if (!(node instanceof VirtualGridNode)) {
                throw new VirtualGridBridgeException("Virtual node does not expose typed membership state");
            }
        }

        IGrid previousPrimary = this.dataEnergistics$virtualPrimaryGrid;
        Set<IGridNode> previousAll = dataEnergistics$identitySet(this.dataEnergistics$outgoingNodes);
        Set<IGridNode> previousActive = dataEnergistics$identitySet(this.dataEnergistics$outgoingActiveNodes);
        if (previousPrimary == primaryGrid && dataEnergistics$identitySetEquals(previousAll, normalizedAll) && dataEnergistics$identitySetEquals(previousActive, normalizedActive)) {
            return;
        }
        this.dataEnergistics$virtualPrimaryGrid = primaryGrid;
        ArrayList<IGridNode> registeredNodes = new ArrayList<>();
        ArrayList<IGridNode> removedNodes = new ArrayList<>();
        try {
            for (IGridNode node : normalizedActive) {
                if (previousActive.contains(node)) {
                    continue;
                }
                try {
                    ((VirtualGridBridgeInternal) primaryGrid).registerIncomingVirtualNode(localGrid, node);
                    registeredNodes.add(node);
                } catch (RuntimeException exception) {
                    if (primaryBridge.containsIncomingVirtualMember(node)) {
                        registeredNodes.add(node);
                    }
                    throw exception;
                }
                ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, true);
            }
            for (IGridNode node : previousActive) {
                if (normalizedActive.contains(node)) {
                    continue;
                }
                ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                ((VirtualGridBridgeInternal) primaryGrid).unregisterIncomingVirtualNode(localGrid, node);
                removedNodes.add(node);
            }
        } catch (RuntimeException exception) {
            for (int index = removedNodes.size() - 1; index >= 0; index--) {
                IGridNode node = removedNodes.get(index);
                try {
                    ((VirtualGridBridgeInternal) primaryGrid).registerIncomingVirtualNode(localGrid, node);
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, true);
                } catch (RuntimeException rollbackFailure) {
                    this.dataEnergistics$outgoingActiveNodes.remove(node);
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                    exception.addSuppressed(rollbackFailure);
                }
            }
            for (int index = registeredNodes.size() - 1; index >= 0; index--) {
                IGridNode node = registeredNodes.get(index);
                try {
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                    ((VirtualGridBridgeInternal) primaryGrid).unregisterIncomingVirtualNode(localGrid, node);
                    if (previousPrimary != primaryGrid) {
                        dataEnergistics$updateMembershipUsingLocalServices(node, previousPrimary, false);
                    }
                } catch (RuntimeException rollbackFailure) {
                    this.dataEnergistics$outgoingNodes.add(node);
                    this.dataEnergistics$outgoingActiveNodes.add(node);
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, true);
                    exception.addSuppressed(rollbackFailure);
                }
            }
            for (IGridNode node : previousAll) {
                ((VirtualGridNode) node).updateVirtualMembership(
                        previousPrimary, this.dataEnergistics$outgoingActiveNodes.contains(node));
            }
            this.dataEnergistics$virtualPrimaryGrid = previousPrimary != null ? previousPrimary : this.dataEnergistics$outgoingActiveNodes.isEmpty() ? null : primaryGrid;
            dataEnergistics$invalidatePrimary(primaryGrid);
            throw new VirtualGridBridgeException("Failed to register virtual grid services atomically", exception);
        }

        for (IGridNode node : normalizedAll) {
            if (!normalizedActive.contains(node)) {
                if (previousAll.contains(node)) {
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                } else {
                    dataEnergistics$updateMembershipUsingLocalServices(node, primaryGrid, false);
                }
            }
        }
        for (IGridNode node : List.copyOf(this.dataEnergistics$outgoingNodes)) {
            if (!normalizedAll.contains(node)) {
                dataEnergistics$updateMembershipUsingLocalServices(node, null, false);
            }
        }

        this.dataEnergistics$outgoingNodes.clear();
        this.dataEnergistics$outgoingNodes.addAll(normalizedAll);
        this.dataEnergistics$outgoingActiveNodes.clear();
        this.dataEnergistics$outgoingActiveNodes.addAll(normalizedActive);
        dataEnergistics$invalidatePrimary(primaryGrid);
    }

    @Override
    public void releasePhysicalNode(IGridNode node) {
        if (!this.dataEnergistics$outgoingNodes.contains(node)) {
            return;
        }
        IGrid primaryGrid = this.dataEnergistics$virtualPrimaryGrid;
        if (primaryGrid != null && this.dataEnergistics$outgoingActiveNodes.contains(node)) {
            try {
                ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                ((VirtualGridBridgeInternal) primaryGrid).unregisterIncomingVirtualNode((IGrid) (Object) this, node);
            } catch (RuntimeException exception) {
                try {
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, true);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                Data_Energistics.LOGGER.error(
                        "Failed to release removed virtual grid node {}; reconciliation will retry",
                        node,
                        exception);
                dataEnergistics$invalidatePrimary(primaryGrid);
                return;
            }
        }
        this.dataEnergistics$outgoingActiveNodes.remove(node);
        this.dataEnergistics$outgoingNodes.remove(node);
        try {
            dataEnergistics$updateMembershipUsingLocalServices(node, null, false);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error("Failed to restore local service state for removed virtual node {}", node,
                    exception);
        }
        if (primaryGrid != null) {
            dataEnergistics$invalidatePrimary(primaryGrid);
        }
    }

    @Override
    public void clearVirtualMembers() {
        IGrid primaryGrid = this.dataEnergistics$virtualPrimaryGrid;
        if (primaryGrid == null) {
            return;
        }

        VirtualGridBridgeException removalFailure = null;
        for (IGridNode node : List.copyOf(this.dataEnergistics$outgoingActiveNodes)) {
            try {
                ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, false);
                ((VirtualGridBridgeInternal) primaryGrid).unregisterIncomingVirtualNode((IGrid) (Object) this, node);
                this.dataEnergistics$outgoingActiveNodes.remove(node);
            } catch (RuntimeException exception) {
                try {
                    ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, true);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                VirtualGridBridgeException bridgeException = exception instanceof VirtualGridBridgeException existing ? existing : new VirtualGridBridgeException("Virtual node rejected subordinate release", exception);
                if (removalFailure == null) {
                    removalFailure = bridgeException;
                } else {
                    removalFailure.addSuppressed(bridgeException);
                }
            }
        }
        dataEnergistics$invalidatePrimary(primaryGrid);
        if (removalFailure != null) {
            throw removalFailure;
        }
        for (IGridNode node : List.copyOf(this.dataEnergistics$outgoingNodes)) {
            dataEnergistics$updateMembershipUsingLocalServices(node, null, false);
        }
        this.dataEnergistics$outgoingNodes.clear();
        this.dataEnergistics$virtualPrimaryGrid = null;
        getService(IPathingService.class).repath();
    }

    @Inject(method = "getService", at = @At("HEAD"), cancellable = true, require = 1)
    private <C extends IGridService> void dataEnergistics$delegateService(
                                                                          Class<C> serviceClass, CallbackInfoReturnable<C> callback) {
        IGrid primaryGrid = this.dataEnergistics$virtualPrimaryGrid;
        if (primaryGrid == null || this.dataEnergistics$localServiceAccessDepth > 0 || serviceClass == IPathingService.class || serviceClass == TowerNetworkDomain.class) {
            return;
        }
        callback.setReturnValue(primaryGrid.getService(serviceClass));
    }

    @WrapMethod(method = "postEvent")
    private <T extends GridEvent> T dataEnergistics$forwardEvent(T event, Operation<T> original) {
        IGrid primaryGrid = this.dataEnergistics$virtualPrimaryGrid;
        if (primaryGrid == null) {
            return original.call(event);
        }

        this.dataEnergistics$localServiceAccessDepth++;
        try {
            original.call(event);
        } finally {
            this.dataEnergistics$localServiceAccessDepth--;
        }
        primaryGrid.postEvent(event);
        return event;
    }

    @Inject(method = "add", at = @At("TAIL"), require = 1)
    private void dataEnergistics$trackAddedPhysicalNode(
                                                        GridNode node, @Nullable CompoundTag savedData, CallbackInfo callback) {
        IGrid primaryGrid = this.dataEnergistics$virtualPrimaryGrid;
        if (primaryGrid == null) {
            return;
        }
        this.dataEnergistics$outgoingNodes.add(node);
        dataEnergistics$updateMembershipUsingLocalServices(node, primaryGrid, false);
        dataEnergistics$invalidatePrimary(primaryGrid);
    }

    @Inject(method = "remove", at = @At("HEAD"), require = 1)
    private void dataEnergistics$releaseRemovedPhysicalNode(GridNode node, CallbackInfo callback) {
        releasePhysicalNode(node);
    }

    @Inject(method = "onServerStartTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$suppressSubordinateServerStartTick(CallbackInfo callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.cancel();
        }
    }

    @Inject(method = "onLevelStartTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$suppressSubordinateLevelStartTick(Level level, CallbackInfo callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.cancel();
        }
    }

    @Inject(method = "onLevelEndTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$suppressSubordinateLevelEndTick(Level level, CallbackInfo callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.cancel();
        }
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$suppressSubordinateServerEndTick(CallbackInfo callback) {
        if (this.dataEnergistics$virtualPrimaryGrid != null) {
            callback.cancel();
        }
    }

    @Shadow
    public abstract <C extends IGridService> C getService(Class<C> serviceClass);

    @Override
    public void registerIncomingVirtualNode(IGrid sourceGrid, IGridNode node) {
        IGrid existingSource = this.dataEnergistics$incomingNodes.get(node);
        if (existingSource != null) {
            if (existingSource == sourceGrid) {
                return;
            }
            throw new VirtualGridBridgeException("Virtual node is already registered by another subordinate grid");
        }
        if (node.getGrid() == (IGrid) (Object) this) {
            throw new VirtualGridBridgeException("Physical primary-grid node cannot be registered virtually");
        }

        this.dataEnergistics$incomingNodes.put(node, sourceGrid);
        this.machines.put(node.getOwner().getClass(), node);
        ArrayList<IGridServiceProvider> addedProviders = new ArrayList<>();
        try {
            for (IGridServiceProvider provider : dataEnergistics$bridgedProviders()) {
                provider.addNode(node, null);
                addedProviders.add(provider);
            }
        } catch (RuntimeException exception) {
            boolean rollbackComplete = true;
            for (int index = addedProviders.size() - 1; index >= 0; index--) {
                try {
                    addedProviders.get(index).removeNode(node);
                } catch (RuntimeException rollbackFailure) {
                    rollbackComplete = false;
                    exception.addSuppressed(rollbackFailure);
                }
            }
            if (rollbackComplete) {
                this.machines.remove(node.getOwner().getClass(), node);
                this.dataEnergistics$incomingNodes.remove(node);
            }
            throw new VirtualGridBridgeException("Grid service rejected a virtual member", exception);
        }
    }

    @Override
    public void unregisterIncomingVirtualNode(IGrid sourceGrid, IGridNode node) {
        IGrid existingSource = this.dataEnergistics$incomingNodes.get(node);
        if (existingSource == null) {
            return;
        }
        if (existingSource != sourceGrid) {
            throw new VirtualGridBridgeException("Virtual node release came from a non-owning subordinate grid");
        }

        RuntimeException providerFailure = null;
        ArrayList<IGridServiceProvider> removedProviders = new ArrayList<>();
        for (IGridServiceProvider provider : dataEnergistics$bridgedProviders()) {
            try {
                provider.removeNode(node);
                removedProviders.add(provider);
            } catch (RuntimeException exception) {
                providerFailure = exception;
                break;
            }
        }
        if (providerFailure != null) {
            for (int index = removedProviders.size() - 1; index >= 0; index--) {
                try {
                    removedProviders.get(index).addNode(node, null);
                } catch (RuntimeException rollbackFailure) {
                    providerFailure.addSuppressed(rollbackFailure);
                }
            }
            throw new VirtualGridBridgeException("Grid service failed to release a virtual member", providerFailure);
        }
        this.machines.remove(node.getOwner().getClass(), node);
        this.dataEnergistics$incomingNodes.remove(node);
    }

    @Unique
    private List<IGridServiceProvider> dataEnergistics$bridgedProviders() {
        ArrayList<IGridServiceProvider> providers = new ArrayList<>();
        Set<IGridServiceProvider> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<Class<?>, IGridServiceProvider> entry : this.services.services().entrySet()) {
            if (entry.getKey() == IPathingService.class || entry.getKey() == TowerNetworkDomain.class || !seen.add(entry.getValue())) {
                continue;
            }
            providers.add(entry.getValue());
        }
        return providers;
    }

    @Unique
    private static Set<IGridNode> dataEnergistics$identitySet(Collection<? extends IGridNode> nodes) {
        Set<IGridNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(nodes);
        return result;
    }

    @Unique
    private static boolean dataEnergistics$identitySetEquals(Set<IGridNode> left, Set<IGridNode> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (IGridNode node : right) {
            if (!left.contains(node)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void dataEnergistics$updateMembershipUsingLocalServices(
                                                                    IGridNode node, @Nullable IGrid primaryGrid, boolean active) {
        this.dataEnergistics$localServiceAccessDepth++;
        try {
            ((VirtualGridNode) node).updateVirtualMembership(primaryGrid, active);
        } finally {
            this.dataEnergistics$localServiceAccessDepth--;
        }
    }

    @Unique
    private static void dataEnergistics$invalidatePrimary(IGrid primaryGrid) {
        primaryGrid.getService(TowerNetworkDomain.class).invalidate(TowerNetworkDomainChange.VIRTUAL_MEMBERSHIP);
    }
}
