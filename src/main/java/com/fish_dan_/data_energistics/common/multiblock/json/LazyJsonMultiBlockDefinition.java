package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Built-in definition that delays pattern construction until registry objects are ready to read.
 */
public final class LazyJsonMultiBlockDefinition implements JsonMultiBlockDefinition {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final JsonMultiBlockStructureKey key;
    private final Supplier<JsonMultiBlockDefinition> definitionFactory;
    private JsonMultiBlockDefinition definition;

    public LazyJsonMultiBlockDefinition(JsonMultiBlockStructureKey key, BlockPatternFactory patternFactory) {
        this(key, patternFactory, Optional.empty(), Map.of());
    }

    public LazyJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                        BlockPatternFactory patternFactory,
                                        String displayNameTranslationKey) {
        this(key, patternFactory, Optional.of(displayNameTranslationKey), Map.of());
    }

    private LazyJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                         Supplier<JsonMultiBlockDefinition> definitionFactory) {
        this.key = key;
        this.definitionFactory = definitionFactory;
    }

    public static LazyJsonMultiBlockDefinition fromDefinition(JsonMultiBlockStructureKey key,
                                                              Supplier<JsonMultiBlockDefinition> definitionFactory) {
        return new LazyJsonMultiBlockDefinition(key, definitionFactory);
    }

    private LazyJsonMultiBlockDefinition(JsonMultiBlockStructureKey key,
                                         BlockPatternFactory patternFactory,
                                         Optional<String> displayNameTranslationKey,
                                         Map<String, CompartmentType> compartmentTypes) {
        this(
                key,
                () -> new ResolvedJsonMultiBlockDefinition(
                        key,
                        patternFactory.get(),
                        displayNameTranslationKey,
                        compartmentTypes,
                        Map.of()));
    }

    @Override
    public JsonMultiBlockStructureKey key() {
        return this.key;
    }

    @Override
    public synchronized BlockPattern pattern() {
        return definition().pattern();
    }

    @Override
    public Optional<String> displayNameTranslationKey() {
        return definition().displayNameTranslationKey();
    }

    @Override
    public Map<String, CompartmentType> compartmentTypes() {
        return definition().compartmentTypes();
    }

    @Override
    public Map<String, Set<CompartmentType>> replaceableCompartmentTypes() {
        return definition().replaceableCompartmentTypes();
    }

    private synchronized JsonMultiBlockDefinition definition() {
        if (this.definition == null) {
            try {
                JsonMultiBlockDefinition resolvedDefinition = this.definitionFactory.get();
                if (!this.key.equals(resolvedDefinition.key())) {
                    throw new IllegalStateException("Built-in JSON multiblock definition key mismatch: expected " +
                            this.key + " but received " + resolvedDefinition.key());
                }
                this.definition = resolvedDefinition;
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to build built-in JSON multiblock definition {}", this.key, exception);
                throw exception;
            }
        }
        return this.definition;
    }

    @FunctionalInterface
    public interface BlockPatternFactory {

        BlockPattern get();
    }
}
