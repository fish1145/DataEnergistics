package com.fish_dan_.data_energistics.common.crafting.trinity.serialization;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;

import java.math.BigInteger;

/** Shared bounded binary representation for exact Trinity quantities crossing persistence or network boundaries. */
public final class TrinityBigIntegerEncoding {

    public static final int MAX_BYTES = 512;

    private TrinityBigIntegerEncoding() {}

    /** Encodes one exact value after enforcing the common transport width. */
    public static byte[] encode(BigInteger value, String role) {
        byte[] encoded = value.toByteArray();
        if (encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("Trinity " + role + " exceeds the exact quantity encoding limit");
        }
        return encoded;
    }

    /** Decodes one exact value at an untrusted data boundary. */
    public static BigInteger decode(byte[] encoded, String role) {
        if (encoded.length == 0 || encoded.length > MAX_BYTES) {
            throw new IllegalArgumentException("Trinity " + role + " has an invalid exact quantity encoding");
        }
        return new BigInteger(encoded);
    }

    /** Reads a current exact quantity or its legacy long representation, without silently accepting other tag types. */
    public static BigInteger readTag(CompoundTag tag, String field, String role) {
        return switch (tag.get(field)) {
            case LongTag value -> BigInteger.valueOf(value.getAsLong());
            case ByteArrayTag value -> decode(value.getAsByteArray(), role);
            case null, default -> throw new IllegalArgumentException("Trinity " + role + " has a missing or invalid amount tag");
        };
    }
}
