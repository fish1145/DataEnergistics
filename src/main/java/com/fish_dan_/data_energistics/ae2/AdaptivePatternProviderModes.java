package com.fish_dan_.data_energistics.ae2;

public final class AdaptivePatternProviderModes {

    private AdaptivePatternProviderModes() {}

    public enum Ae2LtProviderMode {

        NORMAL,
        WIRELESS;

        public Ae2LtProviderMode next() {
            return this == NORMAL ? WIRELESS : NORMAL;
        }
    }

    public enum Ae2LtReturnMode {

        OFF,
        AUTO,
        EJECT;

        public Ae2LtReturnMode next() {
            return switch (this) {
                case OFF -> AUTO;
                case AUTO -> EJECT;
                case EJECT -> OFF;
            };
        }
    }

    public enum Ae2LtWirelessDispatchMode {

        EVEN_DISTRIBUTION,
        SINGLE_TARGET;

        public Ae2LtWirelessDispatchMode next() {
            return this == EVEN_DISTRIBUTION ? SINGLE_TARGET : EVEN_DISTRIBUTION;
        }
    }

    public enum Ae2LtWirelessSpeedMode {

        NORMAL,
        FAST;

        public Ae2LtWirelessSpeedMode next() {
            return this == NORMAL ? FAST : NORMAL;
        }
    }
}
