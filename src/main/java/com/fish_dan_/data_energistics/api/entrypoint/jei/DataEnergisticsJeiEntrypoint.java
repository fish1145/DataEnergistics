package com.fish_dan_.data_energistics.api.entrypoint.jei;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one public Data Energistics JEI plugin entrypoint discovered while Data Energistics registers its own JEI
 * integration.
 *
 * <p>
 * The scanner itself only exists when Data Energistics and JEI are present, so integrations must not list
 * {@code data_energistics} as a required mod. Optional foreign types may still be protected with
 * {@link #requiredMods()}.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataEnergisticsJeiEntrypoint {

    /**
     * Mod IDs that must be loaded before the annotated class may be resolved.
     *
     * <p>
     * The scanner reads this value from NeoForge metadata before resolving the class, allowing an integration to
     * reference an additional optional-mod type safely.
     * </p>
     *
     * @return required loaded mod IDs
     */
    String[] requiredMods() default {};
}
