package com.fish_dan_.data_energistics.ae2;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdaptivePatternProviderStateTest {

    private static final int STREAM_CONNECTION_LIMIT = AdaptivePatternProviderState.MAX_WIRELESS_CONNECTIONS;

    @Test
    void wirelessConnectionLimitStillAllowsExistingTargetUpdates() {
        AdaptivePatternProviderState state = newState();
        for (int index = 0; index < AdaptivePatternProviderState.MAX_WIRELESS_CONNECTIONS; index++) {
            assertTrue(state.addOrUpdateConnection(
                    Level.OVERWORLD,
                    new BlockPos(index, 64, 0),
                    Direction.NORTH));
        }

        assertFalse(state.addOrUpdateConnection(
                Level.OVERWORLD,
                new BlockPos(AdaptivePatternProviderState.MAX_WIRELESS_CONNECTIONS, 64, 0),
                Direction.NORTH));
        assertTrue(state.addOrUpdateConnection(
                Level.OVERWORLD,
                BlockPos.ZERO.atY(64),
                Direction.SOUTH));
        assertEquals(Direction.SOUTH, state.getConnections().getFirst().boundFace());

        assertTrue(state.removeConnection(Level.OVERWORLD, new BlockPos(1, 64, 0)));
        assertTrue(state.addOrUpdateConnection(
                Level.OVERWORLD,
                new BlockPos(AdaptivePatternProviderState.MAX_WIRELESS_CONNECTIONS, 64, 0),
                Direction.NORTH));
        assertEquals(AdaptivePatternProviderState.MAX_WIRELESS_CONNECTIONS, state.getConnections().size());
    }

    @SuppressWarnings("UnstableApiUsage")
    @BeforeAll
    static void bootstrapMinecraft() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void streamBoundaryDecodingKeepsStateBoundedAndAtomic() {
        AdaptivePatternProviderState enumState = newState();
        enumState.cycleAe2LtProviderMode();
        enumState.cycleAe2LtReturnMode();
        enumState.cycleAe2LtWirelessDispatchMode();
        RegistryFriendlyByteBuf firstEnumStream = stateStream(
                true,
                false,
                -1,
                AdaptivePatternProviderModes.Ae2LtReturnMode.EJECT.ordinal(),
                Integer.MAX_VALUE,
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.FAST.ordinal(),
                0);
        try {
            assertTrue(enumState.readFromStream(firstEnumStream));
            assertEquals(AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL, enumState.getAe2LtProviderMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtReturnMode.EJECT, enumState.getAe2LtReturnMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION,
                    enumState.getAe2LtWirelessDispatchMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.FAST,
                    enumState.getAe2LtWirelessSpeedMode());
            assertTrue(enumState.isAdvancedAeFilteredImportEnabled());
        } finally {
            firstEnumStream.release();
        }

        RegistryFriendlyByteBuf secondEnumStream = stateStream(
                false,
                true,
                AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS.ordinal(),
                -1,
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.SINGLE_TARGET.ordinal(),
                Integer.MAX_VALUE,
                0);
        try {
            assertTrue(enumState.readFromStream(secondEnumStream));
            assertEquals(AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS, enumState.getAe2LtProviderMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtReturnMode.OFF, enumState.getAe2LtReturnMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.SINGLE_TARGET,
                    enumState.getAe2LtWirelessDispatchMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL,
                    enumState.getAe2LtWirelessSpeedMode());
            assertTrue(enumState.isResonatingPullEnabled());
        } finally {
            secondEnumStream.release();
        }

        AdaptivePatternProviderState invalidConnectionState = newState();
        AdaptiveWirelessConnection firstConnection = connection(1);
        AdaptiveWirelessConnection secondConnection = connection(2);
        RegistryFriendlyByteBuf invalidConnectionStream = stateStream(
                false,
                false,
                AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL.ordinal(),
                AdaptivePatternProviderModes.Ae2LtReturnMode.OFF.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL.ordinal(),
                4);
        writeConnection(invalidConnectionStream, firstConnection);
        writeConnection(invalidConnectionStream, "invalid dimension", new BlockPos(20, 64, 0), Direction.NORTH.ordinal());
        writeConnection(invalidConnectionStream, "minecraft:overworld", new BlockPos(21, 64, 0), Direction.values().length);
        writeConnection(invalidConnectionStream, secondConnection);
        try {
            assertTrue(invalidConnectionState.readFromStream(invalidConnectionStream));
            assertEquals(List.of(firstConnection, secondConnection), invalidConnectionState.getConnections());
        } finally {
            invalidConnectionStream.release();
        }

        AdaptivePatternProviderState negativeCountState = newState();
        negativeCountState.addOrUpdateConnection(Level.OVERWORLD, new BlockPos(30, 64, 0), Direction.SOUTH);
        RegistryFriendlyByteBuf negativeCountStream = stateStream(
                false,
                false,
                AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL.ordinal(),
                AdaptivePatternProviderModes.Ae2LtReturnMode.OFF.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL.ordinal(),
                -1);
        try {
            assertTrue(negativeCountState.readFromStream(negativeCountStream));
            assertEquals(List.of(), negativeCountState.getConnections());
        } finally {
            negativeCountStream.release();
        }

        AdaptivePatternProviderState exactLimitState = newState();
        RegistryFriendlyByteBuf exactLimitStream = stateStream(
                false,
                false,
                AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL.ordinal(),
                AdaptivePatternProviderModes.Ae2LtReturnMode.OFF.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL.ordinal(),
                STREAM_CONNECTION_LIMIT);
        writeConnections(exactLimitStream, STREAM_CONNECTION_LIMIT);
        try {
            assertTrue(exactLimitState.readFromStream(exactLimitStream));
            assertEquals(STREAM_CONNECTION_LIMIT, exactLimitState.getConnections().size());
            assertEquals(connection(0), exactLimitState.getConnections().getFirst());
            assertEquals(connection(STREAM_CONNECTION_LIMIT - 1),
                    exactLimitState.getConnections().get(STREAM_CONNECTION_LIMIT - 1));
            assertEquals(0, exactLimitStream.readableBytes());
        } finally {
            exactLimitStream.release();
        }

        AdaptivePatternProviderState overflowState = newState();
        RegistryFriendlyByteBuf overflowStream = stateStream(
                false,
                false,
                AdaptivePatternProviderModes.Ae2LtProviderMode.NORMAL.ordinal(),
                AdaptivePatternProviderModes.Ae2LtReturnMode.OFF.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.EVEN_DISTRIBUTION.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.NORMAL.ordinal(),
                STREAM_CONNECTION_LIMIT + 1);
        writeConnections(overflowStream, STREAM_CONNECTION_LIMIT + 1);
        try {
            assertTrue(overflowState.readFromStream(overflowStream));
            assertEquals(STREAM_CONNECTION_LIMIT, overflowState.getConnections().size());
            assertEquals(connection(0), overflowState.getConnections().getFirst());
            assertEquals(connection(STREAM_CONNECTION_LIMIT - 1),
                    overflowState.getConnections().get(STREAM_CONNECTION_LIMIT - 1));
            assertEquals(0, overflowStream.readableBytes());
        } finally {
            overflowStream.release();
        }

        AdaptivePatternProviderState truncatedState = newState();
        AdaptiveWirelessConnection previousConnection = new AdaptiveWirelessConnection(
                Level.OVERWORLD,
                new BlockPos(40, 64, 0),
                Direction.SOUTH);
        truncatedState.addOrUpdateConnection(
                previousConnection.dimension(), previousConnection.pos(), previousConnection.boundFace());
        RegistryFriendlyByteBuf truncatedStream = stateStream(
                true,
                true,
                AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS.ordinal(),
                AdaptivePatternProviderModes.Ae2LtReturnMode.AUTO.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.SINGLE_TARGET.ordinal(),
                AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.FAST.ordinal(),
                1);
        truncatedStream.writeUtf("minecraft:overworld");
        try {
            assertTrue(truncatedState.readFromStream(truncatedStream));
            assertEquals(List.of(previousConnection), truncatedState.getConnections());
            assertTrue(truncatedState.isAdvancedAeFilteredImportEnabled());
            assertTrue(truncatedState.isResonatingPullEnabled());
            assertEquals(AdaptivePatternProviderModes.Ae2LtProviderMode.WIRELESS, truncatedState.getAe2LtProviderMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtReturnMode.AUTO, truncatedState.getAe2LtReturnMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessDispatchMode.SINGLE_TARGET,
                    truncatedState.getAe2LtWirelessDispatchMode());
            assertEquals(AdaptivePatternProviderModes.Ae2LtWirelessSpeedMode.FAST,
                    truncatedState.getAe2LtWirelessSpeedMode());
        } finally {
            truncatedStream.release();
        }
    }

    private static AdaptivePatternProviderState newState() {
        return new AdaptivePatternProviderState(new TestInventoryHost(), () -> AdaptivePatternProviderState.PROVIDER_SLOT_LIMIT);
    }

    private static RegistryFriendlyByteBuf stateStream(boolean advancedAeFilteredImport, boolean resonatingPullEnabled,
                                                       int providerModeOrdinal, int returnModeOrdinal, int dispatchModeOrdinal, int speedModeOrdinal,
                                                       int connectionCount) {
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.OTHER);
        data.writeNbt(null);
        data.writeNbt(null);
        data.writeBoolean(advancedAeFilteredImport);
        data.writeBoolean(resonatingPullEnabled);
        data.writeVarInt(providerModeOrdinal);
        data.writeVarInt(returnModeOrdinal);
        data.writeVarInt(dispatchModeOrdinal);
        data.writeVarInt(speedModeOrdinal);
        data.writeVarInt(connectionCount);
        return data;
    }

    private static void writeConnections(RegistryFriendlyByteBuf data, int count) {
        for (int index = 0; index < count; index++) {
            writeConnection(data, connection(index));
        }
    }

    private static AdaptiveWirelessConnection connection(int index) {
        return new AdaptiveWirelessConnection(Level.OVERWORLD, new BlockPos(index, 64, 0), Direction.NORTH);
    }

    private static void writeConnection(RegistryFriendlyByteBuf data, AdaptiveWirelessConnection connection) {
        writeConnection(
                data,
                connection.dimension().location().toString(),
                connection.pos(),
                connection.boundFace().ordinal());
    }

    private static void writeConnection(RegistryFriendlyByteBuf data, String dimension, BlockPos pos, int faceOrdinal) {
        data.writeUtf(dimension);
        data.writeBlockPos(pos);
        data.writeVarInt(faceOrdinal);
    }

    private static final class TestInventoryHost implements InternalInventoryHost {

        @Override
        public void saveChangedInventory(AppEngInternalInventory inventory) {}

        @Override
        public boolean isClientSide() {
            return true;
        }
    }
}
