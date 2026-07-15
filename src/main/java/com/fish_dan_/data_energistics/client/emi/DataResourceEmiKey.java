package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies the two Data Energistics resources that have native EMI stacks.
 */
public enum DataResourceEmiKey {

    DATA(DataKey.ID, DataKey.of()),
    DATA_FLOW(DataFlowKey.ID, DataFlowKey.of());

    private final ResourceLocation id;
    private final AEKey aeKey;

    DataResourceEmiKey(ResourceLocation id, AEKey aeKey) {
        this.id = id;
        this.aeKey = aeKey;
    }

    public ResourceLocation id() {
        return id;
    }

    public AEKey aeKey() {
        return aeKey;
    }

    public static DataResourceEmiKey fromId(ResourceLocation id) {
        for (DataResourceEmiKey key : values()) {
            if (key.id.equals(id)) {
                return key;
            }
        }
        throw invalid("Unsupported Data Energistics EMI stack id: " + id);
    }

    public static @Nullable DataResourceEmiKey fromAeKey(AEKey aeKey) {
        if (aeKey instanceof DataKey) {
            return DATA;
        }
        if (aeKey instanceof DataFlowKey) {
            return DATA_FLOW;
        }
        return null;
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
