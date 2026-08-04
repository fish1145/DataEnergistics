package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleCodec.DecodedDocument;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Owns the crash-safe, one-time v0-to-v1 file transition for Data Extractor rules. */
public final class DataExtractorRuleMigrator {

    public static final String BACKUP_FILE_NAME = "data_energistics-data_extractor_rules.v0.json";

    private DataExtractorRuleMigrator() {}

    /**
     * Loads a rule snapshot from an explicit path, creating defaults or atomically migrating v0 when required.
     */
    public static LoadedRules load(Path target, DefaultRuleValues defaults) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTarget.getParent());
        if (Files.notExists(normalizedTarget)) {
            return createDefault(normalizedTarget, defaults);
        }

        byte[] original = Files.readAllBytes(normalizedTarget);
        DecodedDocument decoded = DataExtractorRuleCodec.decode(original, normalizedTarget);
        if (decoded.sourceVersion() == 0) {
            ensureBackup(normalizedTarget, original);
            byte[] migrated = DataExtractorRuleCodec.encode(decoded.v1Document());
            DataExtractorRuleCodec.decode(migrated, normalizedTarget);
            writeAtomically(normalizedTarget, migrated, true, DataExtractorRuleMigrator::validateRuleDocument);
        }
        return decoded.loadedRules();
    }

    /** Resolves the fixed sibling backup path used by a v0 migration. */
    public static Path backupPath(Path target) {
        return target.toAbsolutePath().normalize().resolveSibling(BACKUP_FILE_NAME);
    }

    private static LoadedRules createDefault(Path target, DefaultRuleValues defaults) throws IOException {
        byte[] encoded = DataExtractorRuleCodec.encode(DataExtractorRuleCodec.createDefault(defaults, target));
        DecodedDocument decoded = DataExtractorRuleCodec.decode(encoded, target);
        writeAtomically(target, encoded, false, DataExtractorRuleMigrator::validateRuleDocument);
        return decoded.loadedRules();
    }

    private static void ensureBackup(Path target, byte[] original) throws IOException {
        Path backup = backupPath(target);
        if (Files.exists(backup)) {
            byte[] existing = Files.readAllBytes(backup);
            if (!Arrays.equals(existing, original)) {
                throw new RuleFormatException(
                        backup,
                        "$",
                        "the existing v0 backup differs byte-for-byte from the source document",
                        "backupBytes=" + existing.length + ", sourceBytes=" + original.length,
                        "restore the matching backup or move the conflicting backup aside before retrying");
            }
            return;
        }
        writeAtomically(backup, original, false, temporary -> validateExactBytes(temporary, original));
    }

    private static void validateRuleDocument(Path temporary) throws IOException {
        DataExtractorRuleCodec.decode(Files.readAllBytes(temporary), temporary);
    }

    private static void validateExactBytes(Path temporary, byte[] expected) throws IOException {
        byte[] actual = Files.readAllBytes(temporary);
        if (!Arrays.equals(actual, expected)) {
            throw new IOException("Temporary backup validation failed for " + temporary);
        }
    }

    private static void writeAtomically(
                                        Path target,
                                        byte[] content,
                                        boolean replaceExisting,
                                        TemporaryValidator validator) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            validator.validate(temporary);
            try {
                if (replaceExisting) {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "Atomic replacement is not supported for Data Extractor rule file " + target,
                        exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    private interface TemporaryValidator {

        void validate(Path temporary) throws IOException;
    }
}
