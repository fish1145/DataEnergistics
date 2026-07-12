package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;

import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
