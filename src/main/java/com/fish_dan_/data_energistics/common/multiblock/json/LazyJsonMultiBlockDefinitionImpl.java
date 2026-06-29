package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.modularmc.mdl.api.multiblock.BlockPattern;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Built-in definition implementation that delays pattern construction until registry objects are ready to read.
 */
public final class LazyJsonMultiBlockDefinitionImpl implements JsonMultiBlockDefinition {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private final JsonMultiBlockStructureKey key;
    private final Supplier<BlockPattern> patternFactory;
    private BlockPattern pattern;

    public LazyJsonMultiBlockDefinitionImpl(JsonMultiBlockStructureKey key, Supplier<BlockPattern> patternFactory) {
        this.key = Objects.requireNonNull(key, "key");
        this.patternFactory = Objects.requireNonNull(patternFactory, "patternFactory");
    }

    @Override
    public JsonMultiBlockStructureKey key() {
        return this.key;
    }

    @Override
    public synchronized BlockPattern pattern() {
        if (this.pattern == null) {
            try {
                this.pattern = Objects.requireNonNull(this.patternFactory.get(), "pattern");
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to build built-in JSON multiblock definition {}", this.key, exception);
                throw exception;
            }
        }
        return this.pattern;
    }
}
