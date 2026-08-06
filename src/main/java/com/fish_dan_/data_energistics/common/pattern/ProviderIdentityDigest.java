package com.fish_dan_.data_energistics.common.pattern;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Encodes identity fields without delimiters or locale-sensitive formatting before hashing them.
 */
final class ProviderIdentityDigest {

    /**
     * Digest algorithm required by the persisted provider-key contract.
     */
    private static final String SHA_256 = "SHA-256";
    /**
     * External marker that makes the digest algorithm explicit in persisted configuration.
     */
    private static final String DIGEST_PREFIX = "sha256:";

    private ProviderIdentityDigest() {}

    /**
     * Hashes one identity's canonical binary representation.
     *
     * @param identity validated provider identity
     * @return algorithm-prefixed lowercase digest
     */
    static String digest(ProviderIdentity identity) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The JVM does not provide the required SHA-256 digest", exception);
        }
        return DIGEST_PREFIX + HexFormat.of().formatHex(digest.digest(encode(identity)));
    }

    /**
     * Writes version, kind and kind-specific fields in the permanent schema order.
     *
     * @param identity identity to encode
     * @return canonical binary representation
     */
    private static byte[] encode(ProviderIdentity identity) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.writeInt(identity.version());
        writer.writeByte(identity.kind().stableCode());
        switch (identity) {
            case ProviderIdentity.Block block -> {
                writer.writeResourceLocation(block.dimensionId());
                writer.writeBlockPosition(block.blockPos().getX(), block.blockPos().getY(), block.blockPos().getZ());
                writer.writeResourceLocation(block.blockEntityTypeId());
            }
            case ProviderIdentity.Part part -> {
                writer.writeResourceLocation(part.dimensionId());
                writer.writeBlockPosition(part.blockPos().getX(), part.blockPos().getY(), part.blockPos().getZ());
                writer.writeByte(part.mount().stableCode());
                writer.writeResourceLocation(part.partItemId());
            }
            case ProviderIdentity.Trinity trinity -> {
                writer.writeLong(trinity.hostId().getMostSignificantBits());
                writer.writeLong(trinity.hostId().getLeastSignificantBits());
                writer.writeLong(trinity.coreId().getMostSignificantBits());
                writer.writeLong(trinity.coreId().getLeastSignificantBits());
                writer.writeInt(trinity.partitionIndex());
            }
            case ProviderIdentity.Matrix matrix -> {
                writer.writeResourceLocation(matrix.dimensionId());
                writer.writeBlockPosition(matrix.blockPos().getX(), matrix.blockPos().getY(), matrix.blockPos().getZ());
                writer.writeByte(matrix.plus() ? 1 : 0);
            }
            case ProviderIdentity.Virtual virtual -> {
                writer.writeByte(virtual.terminalGroupIconId().isPresent() ? 1 : 0);
                virtual.terminalGroupIconId().ifPresent(writer::writeResourceLocation);
                writer.writeString(virtual.terminalGroupNameEncoding());
            }
        }
        return writer.toByteArray();
    }

    /**
     * Small big-endian writer that length-prefixes every UTF-8 string.
     */
    private static final class CanonicalWriter {

        /**
         * Accumulates the canonical representation without checked I/O failure paths.
         */
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        /**
         * Writes the low eight bits of one value.
         */
        private void writeByte(int value) {
            this.output.write(value & 0xff);
        }

        /**
         * Writes one signed integer in big-endian order.
         */
        private void writeInt(int value) {
            writeByte(value >>> 24);
            writeByte(value >>> 16);
            writeByte(value >>> 8);
            writeByte(value);
        }

        /**
         * Writes one signed long in big-endian order.
         */
        private void writeLong(long value) {
            writeInt((int) (value >>> 32));
            writeInt((int) value);
        }

        /**
         * Writes the three signed block coordinates in x/y/z order.
         */
        private void writeBlockPosition(int x, int y, int z) {
            writeInt(x);
            writeInt(y);
            writeInt(z);
        }

        /**
         * Writes a namespaced ID through its canonical textual representation.
         */
        private void writeResourceLocation(ResourceLocation id) {
            writeString(id.toString());
        }

        /**
         * Writes a byte-length-prefixed UTF-8 value so field boundaries cannot collide.
         */
        private void writeString(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeInt(bytes.length);
            this.output.writeBytes(bytes);
        }

        /**
         * Returns a defensive copy of the completed canonical representation.
         */
        private byte[] toByteArray() {
            return this.output.toByteArray();
        }
    }
}
