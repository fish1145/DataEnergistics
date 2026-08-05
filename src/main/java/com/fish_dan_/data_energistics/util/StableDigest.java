package com.fish_dan_.data_energistics.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Creates stable SHA-256 identifiers without exposing server addresses or filesystem paths in client config. */
public final class StableDigest {

    private StableDigest() {}

    /** Hashes the exact UTF-8 value and prefixes the lower-case digest with its algorithm. */
    public static String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Stable digest input must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256", exception);
        }
    }
}
