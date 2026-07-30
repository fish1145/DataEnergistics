package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.EchoKey;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies every Data Energistics custom AE resource that has a native EMI stack.
 */
@Getter
@Accessors(fluent = true)
public enum DataResourceEmiKey {

    DATA(DataKey.ID, DataKey.of()),
    DATA_FLOW(DataFlowKey.ID, DataFlowKey.of()),
    ECHO(EchoKey.ID, EchoKey.of());

    private final ResourceLocation id;
    private final AEKey aeKey;

    DataResourceEmiKey(ResourceLocation id, AEKey aeKey) {
        if (!ModAE2Keys.isCustomKey(aeKey) || !id.equals(aeKey.getId())) {
            throw invalid("EMI identity is not backed by a matching Data Energistics custom key: " + id);
        }
        this.id = id;
        this.aeKey = aeKey;
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
        if (!ModAE2Keys.isCustomKey(aeKey)) {
            return null;
        }
        for (DataResourceEmiKey key : values()) {
            if (key.aeKey.equals(aeKey)) {
                return key;
            }
        }
        throw invalid("Missing native EMI identity for Data Energistics custom key: " + aeKey);
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
