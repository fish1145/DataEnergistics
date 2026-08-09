package com.fish_dan_.data_energistics.client.xei.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies every Data Energistics custom AE resource shared by recipe viewer adapters.
 */
@Getter
@Accessors(fluent = true)
public enum DataResourceKey {

    DATA(DataKey.ID, DataKey.of()),
    DATA_FLOW(DataFlowKey.ID, DataFlowKey.of()),
    ECHO(EchoKey.ID, EchoKey.of());

    private final ResourceLocation id;
    private final AEKey aeKey;

    DataResourceKey(ResourceLocation id, AEKey aeKey) {
        if (!ModAE2Keys.isCustomKey(aeKey) || !id.equals(aeKey.getId())) {
            throw invalid("Data resource key is not backed by a matching Data Energistics custom key: " + id);
        }
        this.id = id;
        this.aeKey = aeKey;
    }

    public static DataResourceKey fromId(ResourceLocation id) {
        for (DataResourceKey key : values()) {
            if (key.id.equals(id)) {
                return key;
            }
        }
        throw invalid("Unsupported Data Energistics resource key id: " + id);
    }

    public static @Nullable DataResourceKey fromAeKey(AEKey aeKey) {
        if (!ModAE2Keys.isCustomKey(aeKey)) {
            return null;
        }
        for (DataResourceKey key : values()) {
            if (key.aeKey.equals(aeKey)) {
                return key;
            }
        }
        throw invalid("Missing shared identity for Data Energistics custom key: " + aeKey);
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
