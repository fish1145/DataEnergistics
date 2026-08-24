package com.fish_dan_.data_energistics.integration.map.xaero.client;

/**
 * Marks a Xaero fullscreen-map class whose orbital click bridge was applied successfully.
 *
 * <p>
 * The optional adapter checks this contract on the Minecraft client thread before it opens a map. Its only purpose is
 * to distinguish a usable exact-version Mixin bridge from a missing or rejected optional Mixin; it owns no state, has
 * no server lifetime and must never be referenced outside the Xaero-gated client integration.
 * </p>
 */
public interface XaeroOrbitalMapBridge {}
