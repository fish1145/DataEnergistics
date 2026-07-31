package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;

import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.fish_dan_.data_energistics.ae2.ChannelHubAllocationPolicy.Allocator.AE2;
import static com.fish_dan_.data_energistics.ae2.ChannelHubAllocationPolicy.Allocator.DATA_ENERGISTICS_SHARED_POOL;
import static com.fish_dan_.data_energistics.ae2.ChannelHubAllocationPolicy.Allocator.EXTERNAL_MAX_FLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies channel-hub capacity rules directly against controller-state and geometry snapshots.
 */
final class ChannelHubCapacityImplTest {

    /**
     * Calculator under test.
     */
    private final ChannelHubCapacity capacity = new ChannelHubCapacityImpl();

    /**
     * Verifies that all six faces of one controller provide dense-channel capacity.
     */
    @Test
    void singleControllerExposesOneHundredNinetyTwoChannels() {
        assertEquals(192, this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                Set.of(BlockPos.ZERO)));
    }

    /**
     * Verifies that the shared face between adjacent controllers is not counted twice.
     */
    @Test
    void adjacentControllersExposeTenFaces() {
        assertEquals(320, this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                Set.of(BlockPos.ZERO, BlockPos.ZERO.east())));
    }

    /**
     * Verifies single, multiple and mixed overloaded-controller supply without duplicating ordinary face capacity.
     */
    @Test
    void overloadedControllerSupplyCombinesWithOrdinaryGeometry() {
        assertEquals(128, this.capacity.calculateCombined(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                Set.of(),
                Set.of(BlockPos.ZERO),
                128));
        assertEquals(256, this.capacity.calculateCombined(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                Set.of(),
                Set.of(BlockPos.ZERO, BlockPos.ZERO.east()),
                256));
        assertEquals(288, this.capacity.calculateCombined(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                Set.of(BlockPos.ZERO),
                Set.of(BlockPos.ZERO, BlockPos.ZERO.east()),
                128));

        var noHub = decide(false, 2, 1, ControllerState.CONTROLLER_ONLINE, ChannelMode.DEFAULT, true);
        assertEquals(EXTERNAL_MAX_FLOW, noHub.allocator());
        assertFalse(noHub.bypassExternalMaxFlow());

        for (var topology : new int[][] { { 1, 0 }, { 2, 0 }, { 0, 2 }, { 1, 2 } }) {
            var withHub = decide(
                    true, topology[0], topology[1], ControllerState.CONTROLLER_ONLINE, ChannelMode.DEFAULT, true);
            assertEquals(DATA_ENERGISTICS_SHARED_POOL, withHub.allocator());
            assertTrue(withHub.bypassExternalMaxFlow());
            assertEquals(1, withHub.hubUpstreamChannels());
        }

        var controllerless = decide(true, 0, 0, ControllerState.NO_CONTROLLER, ChannelMode.DEFAULT, true);
        assertEquals(DATA_ENERGISTICS_SHARED_POOL, controllerless.allocator());
        assertFalse(controllerless.bypassExternalMaxFlow());

        var infiniteHub = decide(true, 1, 1, ControllerState.CONTROLLER_ONLINE, ChannelMode.INFINITE, true);
        assertEquals(DATA_ENERGISTICS_SHARED_POOL, infiniteHub.allocator());
        assertFalse(infiniteHub.bypassExternalMaxFlow());
        assertEquals(AE2, decide(
                false, 1, 0, ControllerState.CONTROLLER_ONLINE, ChannelMode.INFINITE, true).allocator());
    }

    /**
     * Verifies that AE2 channel-mode multipliers apply to every exposed face.
     */
    @Test
    void channelModesScaleExposedFaceCapacity() {
        assertEquals(384, this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.X2,
                Set.of(BlockPos.ZERO)));
        assertEquals(768, this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.X4,
                Set.of(BlockPos.ZERO)));
    }

    /**
     * Verifies that controller conflicts disable the shared pool.
     */
    @Test
    void controllerConflictHasNoCapacity() {
        assertEquals(0, this.capacity.calculate(
                ControllerState.CONTROLLER_CONFLICT,
                ChannelMode.DEFAULT,
                Set.of(BlockPos.ZERO)));
    }

    /**
     * Verifies that controller-less grids retain the tower's former dense ad-hoc capacity.
     */
    @Test
    void controllerlessGridKeepsDenseAdHocCapacity() {
        assertEquals(32, this.capacity.calculate(
                ControllerState.NO_CONTROLLER,
                ChannelMode.DEFAULT,
                List.of()));
    }

    /**
     * Verifies that infinite channel mode remains mathematically unbounded.
     */
    @Test
    void infiniteModeUsesMaximumIntegerCapacity() {
        assertEquals(Integer.MAX_VALUE, this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.INFINITE,
                Set.of(BlockPos.ZERO)));
    }

    /**
     * Verifies fail-fast handling for an impossible online-controller snapshot.
     */
    @Test
    void onlineControllerStateRequiresGeometry() {
        assertThrows(IllegalArgumentException.class, () -> this.capacity.calculate(
                ControllerState.CONTROLLER_ONLINE,
                ChannelMode.DEFAULT,
                List.of()));
    }

    private static ChannelHubAllocationPolicy.Decision decide(boolean hasHub,
                                                              int normalControllers,
                                                              int overloadedControllers,
                                                              ControllerState controllerState,
                                                              ChannelMode channelMode,
                                                              boolean externalMaxFlowAvailable) {
        return ChannelHubAllocationPolicy.decide(new ChannelHubAllocationPolicy.Topology(
                hasHub,
                normalControllers,
                overloadedControllers,
                controllerState,
                channelMode,
                externalMaxFlowAvailable));
    }
}
