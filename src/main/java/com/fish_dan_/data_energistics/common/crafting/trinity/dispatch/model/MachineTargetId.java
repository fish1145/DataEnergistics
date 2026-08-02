package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * Names one physical machine independently from the provider route used to reach it.
 *
 * <p>
 * Adapters build this identity from stable physical facts such as dimension, position, face, or a documented addon
 * connection ID. Two providers reaching the same machine must produce equal IDs so later reservation logic cannot
 * oversell it.
 * </p>
 *
 * @param stableIdentity provider-independent physical machine identity
 */
public record MachineTargetId(String stableIdentity) {

    public MachineTargetId {
        if (stableIdentity == null || stableIdentity.isBlank()) {
            throw new IllegalArgumentException("Machine target identity must not be blank");
        }
    }
}
