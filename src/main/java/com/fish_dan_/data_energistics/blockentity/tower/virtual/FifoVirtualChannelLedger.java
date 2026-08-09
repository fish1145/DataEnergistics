package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Deterministic in-memory {@link VirtualChannelLedger} that allocates manual and automatic requests in FIFO order.
 *
 * @param <B> binding key type
 * @param <N> node key type
 */
public final class FifoVirtualChannelLedger<B, N> implements VirtualChannelLedger<B, N> {

    private static final Comparator<MutableBinding<?, ?>> BINDING_ORDER = Comparator
            .comparingInt((MutableBinding<?, ?> binding) -> binding.source.allocationPriority())
            .thenComparingLong(binding -> binding.fifoOrder);
    private static final Comparator<VirtualChannelNodeRequest<?>> NODE_ORDER = Comparator.comparingLong(VirtualChannelNodeRequest::stableOrder);

    private final Map<B, MutableBinding<B, N>> bindings = new HashMap<>();
    private VirtualChannelCapacity totalCapacity;
    private long physicalChannelUsage;

    /**
     * Creates a ledger with the supplied initial total capacity.
     *
     * @param totalCapacity initial finite or unlimited channel budget
     */
    public FifoVirtualChannelLedger(VirtualChannelCapacity totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    @Override
    public void setTotalCapacity(VirtualChannelCapacity capacity) {
        this.totalCapacity = capacity;
    }

    @Override
    public void setPhysicalChannelUsage(long channelUsage) {
        if (channelUsage < 0) {
            throw new IllegalArgumentException("Physical channel usage must not be negative");
        }
        this.physicalChannelUsage = channelUsage;
    }

    @Override
    public void upsertBinding(VirtualChannelBindingRequest<B, N> request) {
        MutableBinding<B, N> existing = this.bindings.get(request.bindingKey());
        if (existing == null) {
            ensureUniqueBindingOrder(request);
            this.bindings.put(request.bindingKey(), new MutableBinding<>(request));
            return;
        }
        existing.update(request);
    }

    @Override
    public boolean removeBinding(B bindingKey) {
        return this.bindings.remove(bindingKey) != null;
    }

    @Override
    public boolean setBindingEnabled(B bindingKey, boolean enabled) {
        MutableBinding<B, N> binding = this.bindings.get(bindingKey);
        if (binding == null) {
            return false;
        }
        binding.enabled = enabled;
        return true;
    }

    @Override
    public VirtualChannelLedgerSnapshot<B, N> snapshot() {
        List<MutableBinding<B, N>> orderedBindings = new ArrayList<>(this.bindings.values());
        orderedBindings.sort(bindingComparator());

        boolean unlimited = this.totalCapacity.isUnlimited();
        long remaining = unlimited ? Long.MAX_VALUE : finiteVirtualBudget();
        long virtualUsage = 0;
        List<VirtualChannelBindingAllocation<B, N>> allocations = new ArrayList<>(orderedBindings.size());
        for (MutableBinding<B, N> binding : orderedBindings) {
            List<VirtualChannelNodeRequest<N>> orderedNodes = new ArrayList<>(binding.nodes.values());
            orderedNodes.sort(nodeComparator());
            List<VirtualChannelNodeAllocation<N>> nodeAllocations = new ArrayList<>(orderedNodes.size());
            for (VirtualChannelNodeRequest<N> node : orderedNodes) {
                VirtualChannelNodeState state;
                if (!binding.enabled) {
                    state = VirtualChannelNodeState.DISABLED;
                } else if (!node.requiresChannel()) {
                    state = VirtualChannelNodeState.AVAILABLE_WITHOUT_CHANNEL;
                } else if (unlimited || remaining > 0) {
                    state = VirtualChannelNodeState.LEASED;
                    virtualUsage++;
                    if (!unlimited) {
                        remaining--;
                    }
                } else {
                    state = VirtualChannelNodeState.WAITING_CHANNEL;
                }
                nodeAllocations.add(new VirtualChannelNodeAllocation<>(
                        node.nodeKey(), node.stableOrder(), node.requiresChannel(), state));
            }
            allocations.add(new VirtualChannelBindingAllocation<>(
                    binding.bindingKey, binding.source, binding.fifoOrder, binding.enabled, nodeAllocations));
        }

        OptionalLong remainingCapacity = unlimited ? OptionalLong.empty() : OptionalLong.of(remaining);
        return new VirtualChannelLedgerSnapshot<>(this.totalCapacity, this.physicalChannelUsage,
                virtualUsage, remainingCapacity, allocations);
    }

    private void ensureUniqueBindingOrder(VirtualChannelBindingRequest<B, N> request) {
        boolean duplicate = this.bindings.values().stream()
                .anyMatch(binding -> binding.source == request.source() && binding.fifoOrder == request.fifoOrder());
        if (duplicate) {
            throw new IllegalArgumentException("Duplicate virtual binding FIFO order " + request.fifoOrder() + " in " + request.source() + " queue");
        }
    }

    private long finiteVirtualBudget() {
        long limit = this.totalCapacity.finiteLimit().orElseThrow();
        if (this.physicalChannelUsage >= limit) {
            return 0;
        }
        return limit - this.physicalChannelUsage;
    }

    @SuppressWarnings("unchecked")
    private static <B, N> Comparator<MutableBinding<B, N>> bindingComparator() {
        return (Comparator<MutableBinding<B, N>>) (Comparator<?>) BINDING_ORDER;
    }

    @SuppressWarnings("unchecked")
    private static <N> Comparator<VirtualChannelNodeRequest<N>> nodeComparator() {
        return (Comparator<VirtualChannelNodeRequest<N>>) (Comparator<?>) NODE_ORDER;
    }

    private static final class MutableBinding<B, N> {

        private final B bindingKey;
        private final VirtualChannelBindingSource source;
        private final long fifoOrder;
        private final Map<N, VirtualChannelNodeRequest<N>> nodes = new HashMap<>();
        private boolean enabled;

        private MutableBinding(VirtualChannelBindingRequest<B, N> request) {
            this.bindingKey = request.bindingKey();
            this.source = request.source();
            this.fifoOrder = request.fifoOrder();
            this.enabled = request.enabled();
            request.nodes().forEach(node -> this.nodes.put(node.nodeKey(), node));
        }

        private void update(VirtualChannelBindingRequest<B, N> request) {
            if (this.source != request.source() || this.fifoOrder != request.fifoOrder()) {
                throw new IllegalArgumentException("Virtual binding ordering metadata is immutable for key " + this.bindingKey);
            }
            Map<N, VirtualChannelNodeRequest<N>> refreshedNodes = new HashMap<>();
            for (VirtualChannelNodeRequest<N> node : request.nodes()) {
                VirtualChannelNodeRequest<N> existingNode = this.nodes.get(node.nodeKey());
                if (existingNode != null && existingNode.stableOrder() != node.stableOrder()) {
                    throw new IllegalArgumentException("Virtual node order is immutable for key " + node.nodeKey());
                }
                refreshedNodes.put(node.nodeKey(), node);
            }
            this.nodes.clear();
            this.nodes.putAll(refreshedNodes);
            this.enabled = request.enabled();
        }
    }
}
