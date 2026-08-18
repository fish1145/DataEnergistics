package com.fish_dan_.data_energistics.integration.jei.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.xei.ingredient.DataResourceKey;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Native JEI ingredient identity for one Data Energistics custom AE resource and amount.
 */
public record DataResourceJeiIngredient(DataResourceKey key, long amount) {

    /**
     * JEI type identity used by recipe slots and clickable ingredients.
     */
    public static final IIngredientType<DataResourceJeiIngredient> TYPE = () -> DataResourceJeiIngredient.class;

    /**
     * JEI persistence codec for custom resource identities and their amounts.
     */
    public static final Codec<DataResourceJeiIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("key").forGetter(ingredient -> ingredient.key().id()),
            Codec.LONG.fieldOf("amount").forGetter(DataResourceJeiIngredient::amount))
            .apply(instance, DataResourceJeiIngredient::fromId));

    /**
     * The normalized resources that JEI displays in its ingredient list.
     */
    public static final List<DataResourceJeiIngredient> ALL_INGREDIENTS = List.of(
            new DataResourceJeiIngredient(DataResourceKey.DATA, 1L),
            new DataResourceJeiIngredient(DataResourceKey.DATA_FLOW, 1L),
            new DataResourceJeiIngredient(DataResourceKey.ECHO, 1L));

    /**
     * Rejects invalid amounts before JEI can cache or serialize them.
     */
    public DataResourceJeiIngredient {
        if (amount <= 0L) {
            throw invalid("JEI data resource ingredient amount must be positive: " + amount);
        }
    }

    /**
     * Converts a custom AE stack into its native JEI ingredient shape.
     *
     * @return the native ingredient for a Data Energistics key, or {@code null} for other AE keys
     */
    public static @Nullable DataResourceJeiIngredient from(GenericStack stack) {
        DataResourceKey key = DataResourceKey.fromAeKey(stack.what());
        if (key == null) {
            return null;
        }
        if (stack.amount() < 0L) {
            throw invalid("Cannot convert a negative AE2 stack amount to JEI: " + stack.amount());
        }
        long amount = stack.amount() == 0L ? 1L : stack.amount();
        return new DataResourceJeiIngredient(key, amount);
    }

    /**
     * Reconstructs an ingredient from its serialized resource id.
     */
    private static DataResourceJeiIngredient fromId(ResourceLocation id, long amount) {
        return new DataResourceJeiIngredient(DataResourceKey.fromId(id), amount);
    }

    /**
     * Converts this viewer ingredient back to AE2's generic stack representation.
     */
    public GenericStack asGenericStack() {
        return new GenericStack(this.key.aeKey(), this.amount);
    }

    /**
     * Creates a consistent invalid-input exception and records the rejected state.
     */
    private static IllegalArgumentException invalid(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }
}
