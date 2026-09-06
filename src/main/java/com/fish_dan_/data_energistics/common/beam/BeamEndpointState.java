package com.fish_dan_.data_energistics.common.beam;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.networking.GridHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;

/** Owns one endpoint's bindings, live edges, and immutable client visual snapshot on the world thread. */
public final class BeamEndpointState {

    private final BeamEndpoint host;
    private final BeamDeviceKind kind;
    private final LongSet bindings = new LongLinkedOpenHashSet();
    private final Long2ObjectMap<BeamConnection> connections = new Long2ObjectLinkedOpenHashMap<>();
    private ObjectList<BeamVisual> visuals = ObjectLists.emptyList();
    private int cards;
    private int connectionCount;
    private int boundCount;
    private boolean hidden;
    private boolean faulted;
    private long nextCheck;

    public BeamEndpointState(BeamEndpoint host, BeamDeviceKind kind) {
        this.host = host;
        this.kind = kind;
    }

    public BeamDeviceKind kind() {
        return this.kind;
    }

    public int cards() {
        return this.cards;
    }

    public int range() {
        return this.kind.range(this.cards);
    }

    public int power() {
        return this.kind.idlePower(this.cards, this.connectionCount);
    }

    public int connectionCount() {
        return this.connectionCount;
    }

    public int bindingCount() {
        return this.boundCount;
    }

    public boolean hidden() {
        return this.hidden;
    }

    public boolean faulted() {
        return this.faulted;
    }

    public ObjectList<BeamVisual> visuals() {
        return this.visuals;
    }

    /** Upgrade inventory callbacks and onReady refresh this once, not in every consumer. */
    public void upgradesChanged() {
        readUpgradeCount();
        updateIdlePower();
        requestCheck();
        Level level = this.host.beamLevel();
        if (level != null && !level.isClientSide()) {
            tick();
            this.host.beamChanged(true);
        }
    }

    private void readUpgradeCount() {
        this.cards = this.host.getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get());
        if (this.cards < 0 || this.cards > BeamDeviceKind.UPGRADE_SLOTS) {
            throw new IllegalStateException("Invalid beam upgrade inventory: " + this.cards);
        }
    }

    public void requestCheck() {
        this.nextCheck = 0;
        this.faulted = false;
    }

    public void toggleHidden() {
        this.hidden = !this.hidden;
        publish();
        for (BeamConnection connection : this.connections.values()) {
            connection.other(this.host).beamState().publish();
        }
        this.host.beamChanged(true);
    }

    /** Top-level per-device boundary: an invalid edge stops this endpoint, never the server tick loop. */
    public void tick() {
        Level level = this.host.beamLevel();
        if (level == null || level.isClientSide() || this.faulted || level.getGameTime() < this.nextCheck) {
            return;
        }
        this.nextCheck = level.getGameTime() + 20;
        try {
            var iterator = this.connections.values().iterator();
            while (iterator.hasNext()) {
                BeamConnection connection = iterator.next();
                if (!connection.live()) {
                    iterator.remove();
                    connection.close();
                }
            }
            if (this.kind == BeamDeviceKind.OMNI) {
                checkBindings(level);
            } else {
                checkRay(level);
            }
            publish();
        } catch (RuntimeException exception) {
            try {
                disconnect();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            this.faulted = true;
            Data_Energistics.LOGGER.error("Stopped beam endpoint {} at {} in {} during connection update",
                    this.kind, this.host.beamPosition(), level.dimension().location(), exception);
        }
    }

    private void checkRay(Level level) {
        BeamEndpoint target = BeamTargetResolver.scan(this.host, level);
        if (target == null || BeamTargetResolver.scan(target, level) != this.host) {
            disconnect();
            return;
        }
        long targetPosition = target.beamPosition().asLong();
        if (!this.connections.containsKey(targetPosition) || this.connections.get(targetPosition).other(this.host) != target) {
            disconnect();
            target.beamState().disconnect();
        }
        connect(target);
    }

    private void checkBindings(Level level) {
        boolean changed = false;
        var iterator = this.bindings.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            BlockPos pos = BlockPos.of(packed);
            if (!level.hasChunkAt(pos)) {
                closeConnection(packed);
                continue;
            }
            BeamEndpoint target = BeamTargetResolver.omni(level, pos);
            if (target == null || !target.beamState().bindings.contains(this.host.beamPosition().asLong())) {
                iterator.remove();
                closeConnection(packed);
                changed = true;
            } else if (withinRange(target)) {
                connect(target);
            } else {
                closeConnection(packed);
            }
        }
        if (changed) {
            this.host.beamChanged(true);
        }
    }

    public boolean withinRange(BeamEndpoint target) {
        int limit = Math.min(range(), target.beamState().range());
        return this.host.beamLevel() == target.beamLevel() &&
                this.host.beamPosition().distSqr(target.beamPosition()) <= (double) limit * limit;
    }

    public boolean boundTo(BeamEndpoint target) {
        return this.bindings.contains(target.beamPosition().asLong()) ||
                target.beamState().bindings.contains(this.host.beamPosition().asLong());
    }

    /** Both loaded endpoints persist the same relation; a repeated or reverse action removes it. */
    public boolean toggleBinding(BeamEndpoint target) {
        if (this.kind != BeamDeviceKind.OMNI || target.beamState().kind != BeamDeviceKind.OMNI ||
                target == this.host || this.host.beamLevel() != target.beamLevel() ||
                (!boundTo(target) && !withinRange(target))) {
            throw new IllegalArgumentException("Invalid beam binding endpoints");
        }
        long self = this.host.beamPosition().asLong();
        long peer = target.beamPosition().asLong();
        boolean removed = this.bindings.remove(peer);
        removed |= target.beamState().bindings.remove(self);
        if (removed) {
            closeConnection(peer);
            target.beamState().closeConnection(self);
        } else {
            this.bindings.add(peer);
            target.beamState().bindings.add(self);
        }
        requestCheck();
        target.beamState().requestCheck();
        this.host.beamChanged(true);
        target.beamChanged(true);
        tick();
        target.beamState().tick();
        return !removed;
    }

    private void connect(BeamEndpoint target) {
        if (this.host.beamPosition().compareTo(target.beamPosition()) > 0) {
            target.beamState().connect(this.host);
            return;
        }
        long peer = target.beamPosition().asLong();
        if (this.connections.containsKey(peer)) {
            return;
        }
        var selfNode = this.host.beamNode().getNode();
        var peerNode = target.beamNode().getNode();
        if (selfNode == null || peerNode == null) {
            return;
        }
        for (var existing : selfNode.getConnections()) {
            if (existing.getOtherSide(selfNode) == peerNode) {
                // An adjacent in-world cable edge belongs to AE2, not this wireless binding.
                return;
            }
        }
        var physical = GridHelper.createConnection(selfNode, peerNode);
        BeamConnection connection = new BeamConnection(this.host, target, selfNode, peerNode, physical);
        this.connections.put(peer, connection);
        target.beamState().connections.put(this.host.beamPosition().asLong(), connection);
        publish();
        target.beamState().publish();
    }

    private void closeConnection(long peer) {
        if (this.connections.containsKey(peer)) {
            this.connections.get(peer).close();
        }
    }

    void removeConnection(BeamEndpoint peer, BeamConnection connection) {
        this.connections.remove(peer.beamPosition().asLong(), connection);
        publish();
    }

    /** Releases beam records even after AE2 removes the physical edges; valid bindings survive chunk unloading. */
    public void disconnect() {
        while (!this.connections.isEmpty()) {
            this.connections.values().iterator().next().close();
        }
        publish();
    }

    private void publish() {
        ObjectList<BeamVisual> next = new ObjectArrayList<>();
        for (BeamConnection connection : this.connections.values()) {
            BeamEndpoint peer = connection.other(this.host);
            if (connection.owner == this.host && connection.live() && !this.hidden && !peer.beamState().hidden &&
                    this.host.beamNode().isPowered() && peer.beamNode().isPowered()) {
                next.add(new BeamVisual(peer.beamPosition(), peer.beamFacing(),
                        BeamVisual.blend(this.host.beamColor(), peer.beamColor())));
            }
        }
        next.sort(Comparator.comparing(BeamVisual::target));
        int newConnections = this.connections.size();
        int newBindings = this.bindings.size();
        boolean changed = !next.equals(this.visuals) || this.connectionCount != newConnections || this.boundCount != newBindings;
        this.visuals = ObjectLists.unmodifiable(next);
        this.connectionCount = newConnections;
        this.boundCount = newBindings;
        updateIdlePower();
        if (changed) {
            this.host.beamChanged(false);
        }
    }

    private void updateIdlePower() {
        // ManagedGridNode initialization data cannot be reused after destroy(), including client-side create().
        if (this.host.beamNode().getNode() != null) {
            this.host.beamNode().setIdlePowerUsage(power());
        }
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("beam_hidden", this.hidden);
        if (this.kind == BeamDeviceKind.OMNI) {
            tag.putLongArray("beam_bindings", this.bindings.toLongArray());
        }
    }

    public void load(CompoundTag tag) {
        disconnect();
        this.hidden = tag.getBoolean("beam_hidden");
        this.bindings.clear();
        if (this.kind == BeamDeviceKind.OMNI) {
            for (long packed : tag.getLongArray("beam_bindings")) {
                BlockPos peer = BlockPos.of(packed);
                double distance = this.host.beamPosition().distSqr(peer);
                if (distance > 0 && distance <= 1024.0 * 1024.0) {
                    this.bindings.add(packed);
                } else {
                    Data_Energistics.LOGGER.warn("Discarded invalid saved beam binding from {} to {}", this.host.beamPosition(), peer);
                }
            }
        }
        this.boundCount = this.bindings.size();
        // AE2 reads upgrade inventories without invoking the upgrade callback, including in-place NBT reloads.
        readUpgradeCount();
        updateIdlePower();
        requestCheck();
    }

    public void write(RegistryFriendlyByteBuf data) {
        BeamStateCodec.write(data, this.hidden, this.cards, this.connectionCount, this.boundCount, this.visuals);
    }

    public boolean read(RegistryFriendlyByteBuf data) {
        var decoded = BeamStateCodec.read(data, this.host.beamPosition(), this.kind);
        boolean changed = this.hidden != decoded.hidden() || this.cards != decoded.cards() ||
                this.connectionCount != decoded.connections() || this.boundCount != decoded.bindings() ||
                !this.visuals.equals(decoded.visuals());
        this.hidden = decoded.hidden();
        this.cards = decoded.cards();
        this.connectionCount = decoded.connections();
        this.boundCount = decoded.bindings();
        this.visuals = decoded.visuals();
        return changed;
    }
}
