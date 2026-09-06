package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

/**
 * Produces a deterministic NBT value encoding without relying on {@link CompoundTag} map iteration order.
 */
public final class TrinityCanonicalNbt {

    private TrinityCanonicalNbt() {}

    /**
     * Encodes one tag after sorting every compound key recursively.
     *
     * @param tag immutable value to encode during server-thread capture
     * @return URL-safe canonical byte representation
     */
    public static String encode(Tag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writeTag(output, tag);
        } catch (IOException exception) {
            throw new IllegalStateException("Canonical NBT encoding failed", exception);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static void writeTag(DataOutputStream output, Tag tag) throws IOException {
        output.writeByte(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> {
                // The type byte fully represents an end tag.
            }
            case Tag.TAG_BYTE -> output.writeByte(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> output.writeShort(((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> output.writeInt(((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> output.writeLong(((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> output.writeInt(Float.floatToRawIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> output.writeLong(Double.doubleToRawLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> writeBytes(output, ((ByteArrayTag) tag).getAsByteArray());
            case Tag.TAG_STRING -> writeString(output, tag.getAsString());
            case Tag.TAG_LIST -> writeList(output, (ListTag) tag);
            case Tag.TAG_COMPOUND -> writeCompound(output, (CompoundTag) tag);
            case Tag.TAG_INT_ARRAY -> writeInts(output, ((IntArrayTag) tag).getAsIntArray());
            case Tag.TAG_LONG_ARRAY -> writeLongs(output, ((LongArrayTag) tag).getAsLongArray());
            default -> throw new IllegalArgumentException("Unsupported NBT tag type " + tag.getId());
        }
    }

    private static void writeCompound(DataOutputStream output, CompoundTag tag) throws IOException {
        ArrayList<String> keys = new ArrayList<>(tag.getAllKeys());
        keys.sort(String::compareTo);
        output.writeInt(keys.size());
        for (String key : keys) {
            writeString(output, key);
            Tag value = tag.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Compound NBT key " + key + " has no value");
            }
            writeTag(output, value);
        }
    }

    private static void writeList(DataOutputStream output, ListTag tag) throws IOException {
        output.writeInt(tag.size());
        for (Tag value : tag) {
            writeTag(output, value);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] values) throws IOException {
        output.writeInt(values.length);
        output.write(values);
    }

    private static void writeInts(DataOutputStream output, int[] values) throws IOException {
        output.writeInt(values.length);
        for (int value : values) {
            output.writeInt(value);
        }
    }

    private static void writeLongs(DataOutputStream output, long[] values) throws IOException {
        output.writeInt(values.length);
        for (long value : values) {
            output.writeLong(value);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeBytes(output, encoded);
    }
}
