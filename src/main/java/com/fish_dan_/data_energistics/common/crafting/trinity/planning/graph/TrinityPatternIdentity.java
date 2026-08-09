package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.NotNull;

/**
 * Stable, component-aware identity of one published pattern semantic.
 *
 * <p>
 * Both encodings are captured on the server thread. Background planning can compare and sort this value without
 * retaining registries, decoded patterns, providers, grids, levels, or block entities.
 * </p>
 *
 * @param definitionEncoding  canonical encoded-pattern key including data components
 * @param publicationEncoding canonical ordered input/output and dispatch semantics
 */
public record TrinityPatternIdentity(String definitionEncoding,
                                     String publicationEncoding)
        implements Comparable<TrinityPatternIdentity> {

    /**
     * Rejects partial identities before they can enter an immutable graph.
     */
    public TrinityPatternIdentity {
        if (definitionEncoding == null || definitionEncoding.isBlank() ||
                publicationEncoding == null || publicationEncoding.isBlank()) {
            throw new IllegalArgumentException("A Trinity pattern identity requires both canonical encodings");
        }
    }

    /**
     * Captures one complete semantic identity with registry-aware AE key codecs.
     *
     * @param signature  server-thread publication value
     * @param registries server registry lookup used only during this capture
     * @return immutable, deterministically sortable identity
     */
    public static TrinityPatternIdentity capture(TrinityPatternPublicationSignature signature,
                                                 HolderLookup.Provider registries) {
        CompoundTag publication = new CompoundTag();
        CompoundTag definition = encodeKey(signature.definition(), registries);
        publication.put("definition", definition.copy());
        publication.put("inputs", encodeInputs(signature, registries));
        publication.put("outputs", encodeStacks(signature.outputs(), registries));
        publication.putBoolean(
                "pushes_inputs_to_external_inventory",
                signature.pushesInputsToExternalInventory());
        return new TrinityPatternIdentity(
                TrinityCanonicalNbt.encode(definition),
                TrinityCanonicalNbt.encode(publication));
    }

    @Override
    public int compareTo(@NotNull TrinityPatternIdentity other) {
        int definitionOrder = this.definitionEncoding.compareTo(other.definitionEncoding);
        return definitionOrder != 0 ? definitionOrder :
                this.publicationEncoding.compareTo(other.publicationEncoding);
    }

    private static ListTag encodeInputs(TrinityPatternPublicationSignature signature,
                                        HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        for (TrinityPatternPublicationSignature.Input input : signature.inputs()) {
            CompoundTag inputTag = new CompoundTag();
            inputTag.putLong("multiplier", input.multiplier());
            inputTag.put("alternatives", encodeAlternatives(input.alternatives(), registries));
            encoded.add(inputTag);
        }
        return encoded;
    }

    private static ListTag encodeAlternatives(
                                              Iterable<TrinityPatternPublicationSignature.Alternative> alternatives,
                                              HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        for (TrinityPatternPublicationSignature.Alternative alternative : alternatives) {
            CompoundTag alternativeTag = new CompoundTag();
            alternativeTag.put("stack", encodeStack(alternative.stack(), registries));
            if (alternative.remainingKey() != null) {
                alternativeTag.put("remaining_key", encodeKey(alternative.remainingKey(), registries));
            }
            encoded.add(alternativeTag);
        }
        return encoded;
    }

    private static ListTag encodeStacks(Iterable<GenericStack> stacks,
                                        HolderLookup.Provider registries) {
        ListTag encoded = new ListTag();
        for (GenericStack stack : stacks) {
            encoded.add(encodeStack(stack, registries));
        }
        return encoded;
    }

    private static CompoundTag encodeStack(GenericStack stack, HolderLookup.Provider registries) {
        CompoundTag encoded = new CompoundTag();
        encoded.put("key", encodeKey(stack.what(), registries));
        encoded.putLong("amount", stack.amount());
        return encoded;
    }

    private static CompoundTag encodeKey(AEKey key, HolderLookup.Provider registries) {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("type", key.getType().getId().toString());
        encoded.put("value", key.toTag(registries));
        return encoded;
    }
}
