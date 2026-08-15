package com.fish_dan_.data_energistics.blockentity.storage;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public enum DigitalStorageDepotOutputType {

    ITEMS("items"),
    FLUIDS("fluids"),
    KEYS("keys");

    private final String serializedName;

    DigitalStorageDepotOutputType(String serializedName) {
        this.serializedName = serializedName;
    }

    public DigitalStorageDepotOutputType next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static @Nullable DigitalStorageDepotOutputType fromSerializedName(String serializedName) {
        if (serializedName == null || serializedName.isBlank()) {
            return null;
        }

        for (DigitalStorageDepotOutputType type : values()) {
            if (type.serializedName.equalsIgnoreCase(serializedName)) {
                return type;
            }
        }

        return null;
    }
}
