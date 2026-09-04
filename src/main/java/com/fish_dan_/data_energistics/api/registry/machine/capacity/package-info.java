/**
 * Public registration API for read-only remaining capacity of machines reached by ordinary AE2 pattern providers.
 *
 * <p>
 * These adapters supplement inventory insertion simulation; they never receive inputs or create dispatch admissions.
 * Plugins whose provider itself owns batch submission should continue using the counted-dispatch provider API.
 * </p>
 */
@NullMarked
package com.fish_dan_.data_energistics.api.registry.machine.capacity;

import org.jspecify.annotations.NullMarked;
