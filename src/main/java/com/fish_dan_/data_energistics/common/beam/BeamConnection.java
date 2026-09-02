package com.fish_dan_.data_energistics.common.beam;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

/** One canonical owner and one physical AE connection, shared by the two endpoint indexes. */
final class BeamConnection {

    final BeamEndpoint owner;
    final BeamEndpoint peer;
    private final IGridNode ownerNode;
    private final IGridNode peerNode;
    private final IGridConnection connection;
    private boolean closed;

    BeamConnection(BeamEndpoint owner, BeamEndpoint peer, IGridNode ownerNode, IGridNode peerNode,
                   IGridConnection connection) {
        this.owner = owner;
        this.peer = peer;
        this.ownerNode = ownerNode;
        this.peerNode = peerNode;
        this.connection = connection;
    }

    BeamEndpoint other(BeamEndpoint endpoint) {
        return endpoint == this.owner ? this.peer : this.owner;
    }

    boolean live() {
        return !this.closed && this.owner.beamNode().getNode() == this.ownerNode &&
                this.peer.beamNode().getNode() == this.peerNode &&
                this.ownerNode.getConnections().contains(this.connection) &&
                this.peerNode.getConnections().contains(this.connection);
    }

    void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.owner.beamState().removeConnection(this.peer, this);
        this.peer.beamState().removeConnection(this.owner, this);
        // AE2 may already have destroyed this edge during node removal.
        if (this.ownerNode.getConnections().contains(this.connection)) {
            this.connection.destroy();
        }
    }
}
