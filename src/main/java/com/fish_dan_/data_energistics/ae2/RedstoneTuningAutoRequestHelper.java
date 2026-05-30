package com.fish_dan_.data_energistics.ae2;

import net.minecraft.server.level.ServerLevel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionSource;

import java.util.List;

public final class RedstoneTuningAutoRequestHelper {

    private RedstoneTuningAutoRequestHelper() {}

    public static void requestPrimaryOutputs(ServerLevel level,
                                             IGrid grid,
                                             IActionSource actionSource,
                                             List<IPatternDetails> patterns) {
        if (grid == null || actionSource == null || patterns == null || patterns.isEmpty()) {
            return;
        }

        var craftingService = grid.getCraftingService();
        for (var pattern : patterns) {
            if (pattern == null) {
                continue;
            }

            var primaryOutput = pattern.getPrimaryOutput();
            if (primaryOutput == null || primaryOutput.what() == null || primaryOutput.amount() <= 0) {
                continue;
            }

            try {
                var planFuture = craftingService.beginCraftingCalculation(
                        level,
                        () -> actionSource,
                        primaryOutput.what(),
                        Math.max(1L, primaryOutput.amount()),
                        CalculationStrategy.CRAFT_LESS);
                var server = level.getServer();
                if (server == null) {
                    continue;
                }

                Thread.ofVirtual().name("data-energistics-redstone-auto-request").start(() -> {
                    try {
                        var plan = planFuture.get();
                        if (plan == null || plan.simulation()) {
                            return;
                        }

                        server.execute(() -> craftingService.submitJob(plan, null, null, true, actionSource));
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }
}
