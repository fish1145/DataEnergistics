package com.fish_dan_.data_energistics.configuration.io;

import com.fish_dan_.data_energistics.configuration.validation.InvalidConfigurationException;

import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.exception.ConfigReadException;
import dev.toma.configuration.config.format.IConfigFormat;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValueReadable;
import dev.toma.configuration.config.value.ObjectValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strictly verifies the Configuration YAML subset before allowing the framework to deserialize it. */
public final class StrictYamlReader {

    private static final byte UTF8_BOM_FIRST = (byte) 0xEF;
    private static final byte UTF8_BOM_SECOND = (byte) 0xBB;
    private static final byte UTF8_BOM_THIRD = (byte) 0xBF;

    private StrictYamlReader() {}

    public static <T> YamlDocument readInto(Path source, ConfigHolder<T> holder) throws IOException {
        YamlShape shape = shape(holder);
        YamlDocument document = parse(source, shape);
        IConfigFormat format = holder.getFormat().createFormat();
        try {
            format.readFile(source.toFile());
        } catch (ConfigReadException exception) {
            throw invalid(source, "$", "Configuration YAML syntax is invalid", exception.getMessage(), exception);
        }
        holder.values().forEach(value -> value.deserializeValue(format));
        holder.save();
        verifyFrameworkValues(source, document, shape);
        return document;
    }

    static <T> YamlShape shape(ConfigHolder<T> holder) {
        Map<String, ConfigValue<?>> leaves = new LinkedHashMap<>();
        Set<String> groups = new LinkedHashSet<>();
        collectShape(holder.getValueMap(), "", leaves, groups);
        return new YamlShape(Map.copyOf(leaves), Set.copyOf(groups));
    }

    private static void collectShape(
                                     Map<String, ConfigValue<?>> values,
                                     String prefix,
                                     Map<String, ConfigValue<?>> leaves,
                                     Set<String> groups) {
        for (Map.Entry<String, ConfigValue<?>> entry : values.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            ConfigValue<?> value = entry.getValue();
            if (value instanceof ObjectValue objectValue) {
                groups.add(path);
                collectShape(
                        objectValue.get(IConfigValueReadable.Mode.SAVED),
                        path,
                        leaves,
                        groups);
            } else {
                leaves.put(path, value);
            }
        }
    }

    private static YamlDocument parse(Path source, YamlShape shape) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        if (hasBom(bytes)) {
            throw invalid(source, "$", "UTF-8 BOM is not allowed", "EF BB BF");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid(source, "$", "file is not valid UTF-8", exception.getMessage(), exception);
        }

        Map<String, YamlValue> values = new LinkedHashMap<>();
        Set<String> mappings = new LinkedHashSet<>();
        List<String> groupStack = new ArrayList<>();
        ArrayContext array = null;
        String[] lines = text.split("\\R", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            int lineNumber = lineIndex + 1;
            if (line.indexOf('\t') >= 0) {
                throw invalid(source, "line " + lineNumber, "tabs are not allowed", line);
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int indentation = leadingSpaces(line);
            if ((indentation & 1) != 0) {
                throw invalid(source, "line " + lineNumber, "indentation must use two-space levels", line);
            }
            if (trimmed.equals("-") || trimmed.startsWith("- ")) {
                if (array == null || indentation != array.entryIndentation()) {
                    throw invalid(source, "line " + lineNumber, "array entry has no matching array field", line);
                }
                String value = trimmed.length() == 1 ? "" : trimmed.substring(2).trim();
                array.values().add(value);
                continue;
            }
            array = null;

            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                throw invalid(source, "line " + lineNumber, "expected key: value", line);
            }
            String key = trimmed.substring(0, colon);
            if (!key.matches("[A-Za-z][A-Za-z0-9]*")) {
                throw invalid(source, "line " + lineNumber, "key must be ASCII lowerCamelCase", key);
            }
            int depth = indentation / 2;
            if (depth > groupStack.size()) {
                throw invalid(source, "line " + lineNumber, "indentation skips a parent group", line);
            }
            while (groupStack.size() > depth) {
                groupStack.removeLast();
            }
            String path = path(groupStack, key);
            if (!mappings.add(path)) {
                throw invalid(source, path, "duplicate YAML key", key);
            }
            String scalar = trimmed.substring(colon + 1).trim();
            if (shape.groups().contains(path)) {
                if (!scalar.isEmpty()) {
                    throw invalid(source, path, "group must not have a scalar value", scalar);
                }
                groupStack.add(key);
                continue;
            }

            ConfigValue<?> expected = shape.leaves().get(path);
            if (expected == null) {
                throw invalid(source, path, "unknown YAML field", key);
            }
            if (expected.getValueType().isArray()) {
                if (!scalar.isEmpty()) {
                    throw invalid(source, path, "array must use '-' entries", scalar);
                }
                List<String> entries = new ArrayList<>();
                values.put(path, new ArrayValue(entries));
                array = new ArrayContext(path, indentation + 2, entries);
            } else {
                values.put(path, new ScalarValue(scalar));
            }
        }

        Set<String> missing = new LinkedHashSet<>(shape.leaves().keySet());
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw invalid(source, missing.iterator().next(), "required YAML field is missing", "<missing>");
        }
        return new YamlDocument(Map.copyOf(values), Arrays.hashCode(bytes));
    }

    private static void verifyFrameworkValues(Path source, YamlDocument document, YamlShape shape)
                                                                                                   throws InvalidConfigurationException {
        for (Map.Entry<String, ConfigValue<?>> entry : shape.leaves().entrySet()) {
            String path = entry.getKey();
            ConfigValue<?> configValue = entry.getValue();
            Object external = normalizedExternal(source, path, document.values().get(path), configValue.getValueType());
            Object saved = normalizedSaved(configValue.get(IConfigValueReadable.Mode.SAVED));
            if (!external.equals(saved)) {
                throw invalid(
                        source,
                        path,
                        "framework corrected or changed the supplied value",
                        "file=" + external + ", framework=" + saved);
            }
        }
    }

    private static Object normalizedExternal(Path source, String path, YamlValue value, Class<?> type)
                                                                                                       throws InvalidConfigurationException {
        if (type.isArray()) {
            if (value instanceof ArrayValue array) {
                Class<?> componentType = type.getComponentType();
                List<Object> parsed = new ArrayList<>(array.values().size());
                for (int index = 0; index < array.values().size(); index++) {
                    parsed.add(normalizedArrayEntry(
                            source,
                            path + "[" + index + "]",
                            array.values().get(index),
                            componentType));
                }
                return List.copyOf(parsed);
            }
            throw invalid(source, path, "expected an array", value.toString());
        }
        if (!(value instanceof ScalarValue scalarValue)) {
            throw invalid(source, path, "expected a scalar value", value.toString());
        }
        String scalar = scalarValue.value();
        try {
            if (type == Integer.class) {
                return Integer.parseInt(scalar);
            }
            if (type == Double.class) {
                return Double.parseDouble(scalar);
            }
            if (type == Boolean.class) {
                if (!scalar.equals("true") && !scalar.equals("false")) {
                    throw invalid(source, path, "boolean must be true or false", scalar);
                }
                return Boolean.parseBoolean(scalar);
            }
            if (type == String.class) {
                return scalar;
            }
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(scalar)) {
                        return scalar;
                    }
                }
                throw invalid(source, path, "unknown enum value", scalar);
            }
        } catch (NumberFormatException exception) {
            throw invalid(source, path, "value has the wrong numeric type", scalar, exception);
        }
        throw invalid(source, path, "unsupported schema value type", type.getName());
    }

    private static Object normalizedArrayEntry(Path source, String path, String value, Class<?> componentType)
                                                                                                               throws InvalidConfigurationException {
        try {
            if (componentType == String.class) {
                return value;
            }
            if (componentType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (componentType == Float.class) {
                return Float.parseFloat(value);
            }
            if (componentType == Double.class) {
                return Double.parseDouble(value);
            }
            if (componentType.isEnum()) {
                for (Object constant : componentType.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equals(value)) {
                        return value;
                    }
                }
                throw invalid(source, path, "unknown enum value", value);
            }
        } catch (NumberFormatException exception) {
            throw invalid(source, path, "array entry has the wrong numeric type", value, exception);
        }
        throw invalid(source, path, "unsupported array component type", componentType.getName());
    }

    private static Object normalizedSaved(Object value) {
        if (value instanceof String[] strings) {
            return List.of(strings);
        }
        if (value instanceof Object[] array) {
            List<Object> copy = new ArrayList<>(array.length);
            for (Object entry : array) {
                copy.add(entry instanceof Enum<?> enumeration ? enumeration.name() : entry);
            }
            return List.copyOf(copy);
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        return value;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String path(List<String> groups, String key) {
        return groups.isEmpty() ? key : String.join(".", groups) + "." + key;
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == UTF8_BOM_FIRST && bytes[1] == UTF8_BOM_SECOND &&
                bytes[2] == UTF8_BOM_THIRD;
    }

    private static InvalidConfigurationException invalid(
                                                         Path source,
                                                         String path,
                                                         String violation,
                                                         String actualValue) {
        return new InvalidConfigurationException(source, path, violation, actualValue);
    }

    private static InvalidConfigurationException invalid(
                                                         Path source,
                                                         String path,
                                                         String violation,
                                                         String actualValue,
                                                         Throwable cause) {
        return new InvalidConfigurationException(source, path, violation, actualValue, cause);
    }

    public record YamlDocument(Map<String, YamlValue> values, int fingerprint) {}

    sealed interface YamlValue permits ScalarValue, ArrayValue {}

    record ScalarValue(String value) implements YamlValue {}

    record ArrayValue(List<String> values) implements YamlValue {}

    record YamlShape(Map<String, ConfigValue<?>> leaves, Set<String> groups) {}

    private record ArrayContext(String path, int entryIndentation, List<String> values) {}
}
