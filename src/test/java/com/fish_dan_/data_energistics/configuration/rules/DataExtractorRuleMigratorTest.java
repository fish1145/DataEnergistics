package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.config.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable.DataType;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataExtractorRuleMigratorTest {

    private static final DefaultRuleValues TEST_DEFAULTS = new DefaultRuleValues(
            "minecraft:wheat_seeds=minecraft:wheat@0.5",
            4096.0F,
            4096.0F);

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsV1StrictlyAndDeduplicatesOnlyIdenticalOutputs() throws IOException {
        Path target = temporaryDirectory.resolve("data_energistics-data_extractor_rules.json");
        byte[] original = validV1().getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);

        LoadedRules loaded = DataExtractorRuleMigrator.load(target, TEST_DEFAULTS);

        assertEquals(1, loaded.inputRules().size());
        assertEquals(1, loaded.outputRules().size());
        assertEquals(1, loaded.outputRules().getFirst().outputs().size());
        assertEquals(2, loaded.outputRules().getFirst().outputs().getFirst().count());
        assertThrows(UnsupportedOperationException.class, () -> loaded.inputRules().clear());
        assertThrows(UnsupportedOperationException.class, () -> loaded.outputRules().getFirst().outputs().clear());
        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(DataExtractorRuleMigrator.backupPath(target)));
    }

    @ParameterizedTest
    @MethodSource("invalidV1Documents")
    void rejectsInvalidV1WithLocatedRepairDiagnostics(String document, String expectedLocation) throws IOException {
        Path target = temporaryDirectory.resolve("invalid-" + Math.abs(document.hashCode()) + ".json");
        Files.writeString(target, document, StandardCharsets.UTF_8);

        RuleFormatException exception = assertThrows(
                RuleFormatException.class,
                () -> DataExtractorRuleMigrator.load(target, TEST_DEFAULTS));

        assertTrue(exception.getMessage().contains(expectedLocation), exception.getMessage());
        assertTrue(exception.getMessage().contains("repair="), exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("legacyCarrierAliases")
    void migratesEverySupportedV0CarrierAliasAndPreservesMetadata(
                                                                  String carrierAlias,
                                                                  String typeDeclaration) throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(carrierAlias));
        Path target = directory.resolve("data_energistics-data_extractor_rules.json");
        String v0 = """
                {
                "_root_metadata": {"owner": "pack"},
                "%s": [{
                	"_note": "keep me",
                	"slot": "crop",
                	%s,
                	"input_item": "minecraft:wheat_seeds",
                	"recorded_item": "minecraft:wheat",
                	"progress_per_item": 0.5,
                	"required_amount": 2048,
                	"mimetic_outputs": [{"item": "minecraft:wheat", "count": 2, "_chance": 1.0}]
                }],
                "output_rules": [{
                	"_source": "legacy",
                	"data_type": "crop_data_carrier",
                	"recorded_id": "minecraft:beetroot",
                	"outputs": [{"item": "minecraft:beetroot", "count": 1}]
                }],
                "_mob_rule_examples": [{"arbitrary_unvalidated_example": true}]
                }
                """.formatted(carrierAlias, typeDeclaration);
        byte[] original = v0.getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);

        LoadedRules loaded = DataExtractorRuleMigrator.load(target, TEST_DEFAULTS);

        assertEquals(1, loaded.inputRules().size());
        assertEquals(2, loaded.outputRules().size());
        assertArrayEquals(original, Files.readAllBytes(DataExtractorRuleMigrator.backupPath(target)));

        JsonObject migrated = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, migrated.get("schema_version").getAsInt());
        assertEquals("pack", migrated.getAsJsonObject("_root_metadata").get("owner").getAsString());
        assertEquals(
                "keep me",
                migrated.getAsJsonArray("carrier_rules").get(0).getAsJsonObject().get("_note").getAsString());
        assertEquals(
                1.0D,
                migrated.getAsJsonArray("output_rules")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("outputs")
                        .get(0)
                        .getAsJsonObject()
                        .get("_chance")
                        .getAsDouble());
        assertTrue(migrated.has("_mob_rule_examples"));
    }

    @Test
    void refusesAConflictingBackupWithoutChangingTheV0Source() throws IOException {
        Path target = temporaryDirectory.resolve("data_energistics-data_extractor_rules.json");
        byte[] source = "{\"rules\":[]}".getBytes(StandardCharsets.UTF_8);
        Files.write(target, source);
        Files.writeString(
                DataExtractorRuleMigrator.backupPath(target),
                "different backup",
                StandardCharsets.UTF_8);

        RuleFormatException exception = assertThrows(
                RuleFormatException.class,
                () -> DataExtractorRuleMigrator.load(target, TEST_DEFAULTS));

        assertTrue(exception.getMessage().contains("differs byte-for-byte"));
        assertArrayEquals(source, Files.readAllBytes(target));
    }

    @Test
    void createsTheFullDefaultV1FileAndKeepsMobExamplesNonExecutable() throws IOException {
        Path target = temporaryDirectory.resolve("data_energistics-data_extractor_rules.json");

        DataExtractorRuleTable.load(target);
        LoadedRules loaded = DataExtractorRuleTable.snapshot();

        assertEquals(38, loaded.inputRules().size());
        assertTrue(loaded.inputRules().stream().noneMatch(rule -> rule.dataType() == DataType.MOB));
        assertTrue(loaded.inputRules().stream().anyMatch(rule -> rule.inputItemId().equals(id("minecraft:oak_sapling"))));
        assertTrue(loaded.inputRules().stream().anyMatch(rule -> rule.inputItemId().equals(id("minecraft:raw_gold")) && rule.recordedItemId().equals(id("minecraft:gold_ore"))));
        assertEquals(4, loaded.outputRules().stream()
                .filter(rule -> rule.recordedId().equals(id("minecraft:oak_sapling")))
                .findFirst()
                .orElseThrow()
                .outputs()
                .size());
        JsonObject root = JsonParser.parseString(Files.readString(target, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("schema_version").getAsInt());
        assertTrue(root.has("_mob_rule_examples"));
        assertFalse(Files.exists(DataExtractorRuleMigrator.backupPath(target)));
    }

    @Test
    void publishesOnlyAfterACompleteSuccessfulLoad() throws IOException {
        Path valid = temporaryDirectory.resolve("valid.json");
        Files.writeString(valid, validV1(), StandardCharsets.UTF_8);
        DataExtractorRuleTable.load(valid);
        LoadedRules beforeFailure = DataExtractorRuleTable.snapshot();
        Path invalid = temporaryDirectory.resolve("invalid.json");
        Files.writeString(invalid, "{\"schema_version\":1}", StandardCharsets.UTF_8);

        assertThrows(RuleFormatException.class, () -> DataExtractorRuleTable.load(invalid));
        assertSame(beforeFailure, DataExtractorRuleTable.snapshot());
    }

    private static Stream<Arguments> invalidV1Documents() {
        return Stream.of(
                Arguments.of("{\"schema_version\":1,\"schema_version\":1,\"carrier_rules\":[],\"output_rules\":[]}", "$.schema_version"),
                Arguments.of("{\"schema_version\":2,\"carrier_rules\":[],\"output_rules\":[]}", "$.schema_version"),
                Arguments.of("{\"schema_version\":1,\"carrier_rules\":[],\"output_rules\":[],\"mystery\":true}", "$.mystery"),
                Arguments.of("{\"schema_version\":1,\"carrier_rules\":[]}", "$.output_rules"),
                Arguments.of("""
                        {"schema_version":1,"carrier_rules":[{
                        "slot":"crop","data_type":"crop","input_item":"INVALID ID","recorded_item":"minecraft:wheat",
                        "progress_per_item":1,"required_amount":1
                        }],"output_rules":[]}
                        """, "$.carrier_rules[0].input_item"),
                Arguments.of("""
                        {"schema_version":1,"carrier_rules":[{
                        "slot":"crop","data_type":"crop","input_item":"minecraft:wheat_seeds","recorded_item":"minecraft:wheat",
                        "progress_per_item":0,"required_amount":1
                        }],"output_rules":[]}
                        """, "$.carrier_rules[0].progress_per_item"),
                Arguments.of("""
                        {"schema_version":1,"carrier_rules":[],"output_rules":[{
                        "data_type":"crop","recorded_item":"minecraft:wheat","outputs":[{"item":"minecraft:wheat","count":1.5}]
                        }]}
                        """, "$.output_rules[0].outputs[0].count"),
                Arguments.of("""
                        {"schema_version":1,"carrier_rules":[],"output_rules":[{
                        "data_type":"crop","recorded_item":"minecraft:wheat","outputs":[
                        	{"item":"minecraft:wheat","count":1},{"item":"minecraft:wheat","count":2}
                        ]
                        }]}
                        """, "$.output_rules[0].outputs[1]"),
                Arguments.of("""
                        {"schema_version":1,"carrier_rules":[],"output_rules":[
                        {"data_type":"crop","recorded_item":"minecraft:wheat","outputs":[{"item":"minecraft:wheat","count":1}]},
                        {"data_type":"crop","recorded_item":"minecraft:wheat","outputs":[{"item":"minecraft:wheat","count":2}]}
                        ]}
                        """, "$.output_rules[1]"));
    }

    private static Stream<Arguments> legacyCarrierAliases() {
        return Stream.of(
                Arguments.of("carrier_rules", "\"final_carrier\": \"crop\""),
                Arguments.of("input_rules", "\"data_type\": \"crop_data_carrier\""),
                Arguments.of("rules", "\"final_carrier_item\": \"data_energistics:crop_data_carrier\""));
    }

    private static String validV1() {
        return """
                {
                "schema_version": 1,
                "carrier_rules": [{
                	"slot": "crop",
                	"data_type": "crop",
                	"input_item": "minecraft:wheat_seeds",
                	"recorded_item": "minecraft:wheat",
                	"progress_per_item": 0.5,
                	"required_amount": 4096
                }],
                "output_rules": [
                	{"data_type":"crop","recorded_item":"minecraft:wheat","outputs":[
                	{"item":"minecraft:wheat","count":2},{"item":"minecraft:wheat","count":2}
                	]},
                	{"data_type":"crop","recorded_item":"minecraft:wheat","outputs":[
                	{"item":"minecraft:wheat","count":2}
                	]}
                ],
                "_metadata": {"arbitrary": {"nested": true}}
                }
                """;
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
