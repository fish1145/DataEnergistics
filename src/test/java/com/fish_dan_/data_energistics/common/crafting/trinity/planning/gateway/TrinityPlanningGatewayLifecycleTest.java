package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityPlanningGatewayLifecycleTest {

    @AfterEach
    void stopGateway() {
        TrinityPlanningGatewayLifecycle.stop();
    }

    @Test
    void ownsExactlyOneBoundedGatewayPerServerLifetime() {
        TrinityPlanningGatewayLifecycle.stop();
        TrinityPlanningGatewayLifecycle.start(new TrinityCraftingSchema());
        TrinityPlanningGateway first = TrinityPlanningGatewayLifecycle.gateway();

        assertSame(first, TrinityPlanningGatewayLifecycle.gateway());
        assertThrows(
                IllegalStateException.class,
                () -> TrinityPlanningGatewayLifecycle.start(new TrinityCraftingSchema()));

        TrinityPlanningGatewayLifecycle.stop();
        assertThrows(IllegalStateException.class, TrinityPlanningGatewayLifecycle::gateway);
    }
}
