package com.fish_dan_.data_energistics.ae2;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

public final class DataFlowBusStrategies {
    private static boolean registered;

    private DataFlowBusStrategies() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        StackImportStrategy.register(DataFlowKeyType.TYPE, (level, pos, side) -> NoopImportStrategy.INSTANCE);
        StackExportStrategy.register(DataFlowKeyType.TYPE, (level, pos, side) -> NoopExportStrategy.INSTANCE);
    }

    private enum NoopImportStrategy implements StackImportStrategy {
        INSTANCE;

        @Override
        public boolean transfer(StackTransferContext context) {
            return false;
        }
    }

    private enum NoopExportStrategy implements StackExportStrategy {
        INSTANCE;

        @Override
        public long transfer(StackTransferContext context, AEKey what, long amount) {
            return 0;
        }

        @Override
        public long push(AEKey what, long amount, Actionable mode) {
            return 0;
        }
    }
}
