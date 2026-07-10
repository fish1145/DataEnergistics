package com.fish_dan_.data_energistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Selects which Trinity Data Core structure the GUI auto-build action should place.
 */
public enum TrinityDataCoreAutoBuildTarget {

    MAIN("main"),
    CPU("cpu"),
    CRAFTING("crafting");

    private final String id;

    TrinityDataCoreAutoBuildTarget(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public Component displayName() {
        return Component.translatable("button.data_energistics.trinity_data_core.auto_build.target." + this.id);
    }

    public Component targetName() {
        return Component.translatable("message.data_energistics.trinity_data_core.auto_build.target." + this.id);
    }

    public TrinityDataCoreAutoBuildTarget next() {
        TrinityDataCoreAutoBuildTarget[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static TrinityDataCoreAutoBuildTarget read(RegistryFriendlyByteBuf buf) {
        return buf.readEnum(TrinityDataCoreAutoBuildTarget.class);
    }

    public static void write(RegistryFriendlyByteBuf buf, TrinityDataCoreAutoBuildTarget target) {
        buf.writeEnum(target);
    }
}
