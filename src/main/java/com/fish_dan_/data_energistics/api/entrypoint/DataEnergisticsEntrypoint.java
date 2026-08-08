package com.fish_dan_.data_energistics.api.entrypoint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one public Data Energistics plugin entrypoint discovered during common setup.
 *
 * <p>
 * The annotation carries only class-loading prerequisites. A single plugin can register any number of typed
 * extensions through {@link DataEnergisticsPlugin#register(DataEnergisticsRegistry)} after every required mod is
 * known to be loaded.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataEnergisticsEntrypoint {

    /**
     * Mod IDs that must be loaded before the annotated class may be resolved.
     *
     * <p>
     * The scanner reads this value directly from bytecode scan data, so optional integration classes can reference
     * absent-mod types without triggering class loading.
     * </p>
     *
     * @return required loaded mod IDs
     */
    String[] requiredMods() default {};
}
