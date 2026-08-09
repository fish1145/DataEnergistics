package com.fish_dan_.data_energistics.ae2.dataflow;

import com.fish_dan_.data_energistics.ae2.ModAE2Keys;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;

public final class DataFlowBusStrategies {

    private static boolean registered;

    private DataFlowBusStrategies() {}

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        for (var type : ModAE2Keys.types()) {
            StackImportStrategy.register(type, (level, pos, side) -> NoopImportStrategy.INSTANCE);
            StackExportStrategy.register(type, (level, pos, side) -> new GenericKeyItemExportStrategy(type, level, pos, side));
        }
    }

    private enum NoopImportStrategy implements StackImportStrategy {

        INSTANCE;

        @Override
        public boolean transfer(StackTransferContext context) {
            return false;
        }
    }
}
