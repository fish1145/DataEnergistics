package com.fish_dan_.data_energistics.api.entrypoint.emi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one public Data Energistics EMI plugin entrypoint discovered while Data Energistics registers its own EMI
 * integration.
 *
 * <p>
 * The scanner exists only when Data Energistics and EMI are loaded. Integrations therefore do not need to declare
 * {@code data_energistics} as a required mod; {@link #requiredMods()} is only for additional optional types used by
 * the annotated class.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataEnergisticsEmiEntrypoint {

    /**
     * Mod IDs that must be loaded before the annotated class may be resolved.
     *
     * <p>
     * The scanner reads this member from NeoForge metadata before resolving the class, allowing an integration to
     * reference another optional-mod type safely.
     * </p>
     *
     * @return required loaded mod IDs
     */
    String[] requiredMods() default {};
}
