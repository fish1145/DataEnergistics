package com.fish_dan_.data_energistics.ae2;

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
        StackImportStrategy.register(DataFlowKeyType.TYPE, (level, pos, side) -> NoopImportStrategy.INSTANCE);
        StackExportStrategy.register(DataFlowKeyType.TYPE, (level, pos, side) -> new GenericKeyItemExportStrategy(DataFlowKeyType.TYPE, level, pos, side));
        StackImportStrategy.register(DataKeyType.TYPE, (level, pos, side) -> NoopImportStrategy.INSTANCE);
        StackExportStrategy.register(DataKeyType.TYPE, (level, pos, side) -> new GenericKeyItemExportStrategy(DataKeyType.TYPE, level, pos, side));
    }

    private enum NoopImportStrategy implements StackImportStrategy {

        INSTANCE;

        @Override
        public boolean transfer(StackTransferContext context) {
            return false;
        }
    }
}
