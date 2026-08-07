package com.fish_dan_.data_energistics.api.entrypoint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one public Data Energistics plugin entrypoint discovered during common setup.
 *
 * <p>The annotation intentionally carries no business metadata. A single plugin can register any number of typed
 * extensions through {@link DataEnergisticsPlugin#register(DataEnergisticsRegistry)}.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataEnergisticsEntrypoint {
}
