/**
 * Public registration API for transactional machine state changes coordinated with encoded-pattern uploads.
 *
 * <p>
 * Adapters own machine-specific decisions and reversible state. The runtime owns provider discovery, inventory-delta
 * confirmation and the apply/complete/rollback lifecycle.
 * </p>
 */
@NullMarked
package com.fish_dan_.data_energistics.api.registry.machine.upload;

import org.jspecify.annotations.NullMarked;
