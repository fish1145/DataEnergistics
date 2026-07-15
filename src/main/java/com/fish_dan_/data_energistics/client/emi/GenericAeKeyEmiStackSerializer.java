package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiStackSerializer;

/**
 * Persists a custom AE2 key through AE2's GenericStack codec while EMI stores its real amount separately.
 */
public final class GenericAeKeyEmiStackSerializer implements EmiStackSerializer<GenericAeKeyEmiStack> {

    public static final GenericAeKeyEmiStackSerializer INSTANCE = new GenericAeKeyEmiStackSerializer();
    public static final String TYPE = "data_energistics_ae_key";

    private GenericAeKeyEmiStackSerializer() {}

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public EmiStack create(ResourceLocation id, DataComponentPatch componentChanges, long amount) {
        if (amount <= 0L) {
            throw invalid("Generic AE key EMI stack amount must be positive: " + amount);
        }
        if (componentChanges.size() != 1) {
            throw invalid("Generic AE key EMI stacks require exactly one wrapped-stack component: " + id);
        }
        var wrappedComponent = componentChanges.get(AEComponents.WRAPPED_STACK);
        if (wrappedComponent == null || wrappedComponent.isEmpty()) {
            throw invalid("Generic AE key EMI stack is missing its wrapped-stack identity: " + id);
        }
        GenericStack identity = wrappedComponent.get();
        if (identity.amount() != 1L) {
            throw invalid("Generic AE key EMI stack identity amount must be one: " + identity.amount());
        }
        if (!GenericAeKeyEmiStack.isSupportedKey(identity.what())) {
            throw invalid(
                    "Generic AE key EMI stacks do not support item, fluid, Data, or DataFlow identities: " + identity.what());
        }
        if (!id.equals(identity.what().getId())) {
            throw invalid("Generic AE key EMI stack id does not match its wrapped identity: " + id);
        }
        return new GenericAeKeyEmiStack(identity.what(), amount);
    }

    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
