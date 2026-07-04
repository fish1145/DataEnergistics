package com.fish_dan_.data_energistics.common.multiblock.json;

import com.fish_dan_.data_energistics.block.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

/**
 * Validates block states for JSON symbols declared as compartment positions.
 */
public final class JsonMultiBlockCompartmentValidator {

    private JsonMultiBlockCompartmentValidator() {}

    /**
     * Returns the compartment type declared for a pattern symbol.
     */
    public static Optional<CompartmentType> declaredType(JsonMultiBlockDefinition definition, String symbol) {
        Objects.requireNonNull(definition, "definition");
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definition.compartmentTypes().get(symbol));
    }

    /**
     * Validates that a block state satisfies the compartment type declared for a pattern symbol.
     *
     * <p>Symbols without compartment metadata are accepted. Declared symbols require an actual
     * {@link CompartmentBlock} with the exact matching role.
     */
    public static boolean matchesDeclaredType(JsonMultiBlockDefinition definition, String symbol, BlockState state) {
        Optional<CompartmentType> declared = declaredType(definition, symbol);
        if (declared.isEmpty()) {
            return true;
        }
        if (state == null || !(state.getBlock() instanceof CompartmentBlock block)) {
            return false;
        }
        return block.compartmentType() == declared.orElseThrow();
    }
}
