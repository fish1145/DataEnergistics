package com.fish_dan_.data_energistics.integration.emi.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.xei.ingredient.DataResourceKey;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiStackSerializer;

/**
 * Persists component-free Data Energistics EMI stack identities in favorites and resources.
 */
public final class DataResourceEmiStackSerializer implements EmiStackSerializer<DataResourceEmiStack> {

    public static final DataResourceEmiStackSerializer INSTANCE = new DataResourceEmiStackSerializer();
    public static final String TYPE = "data_energistics_key";

    private DataResourceEmiStackSerializer() {}

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public EmiStack create(ResourceLocation id, DataComponentPatch componentChanges, long amount) {
        if (!componentChanges.isEmpty()) {
            throw invalid("Data resource EMI stacks do not support components: " + id);
        }
        return new DataResourceEmiStack(DataResourceKey.fromId(id), amount);
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
