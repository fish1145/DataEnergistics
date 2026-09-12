package com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One synchronous death invocation, matched by target object and execution identity. It must be closed with
 * try-with-resources; closing drops only the invocation context, not a successfully terminated old life's lock.
 */
public final class OrbitalErasureContext implements AutoCloseable {

    private final ServerPlayer target;
    private final UUID strikeId;
    private final UUID executionId;
    private final @Nullable UUID initiatorId;
    private final OrbitalErasureState state;
    private final float initialHealth;

    public OrbitalErasureContext(ServerPlayer target, UUID strikeId, UUID executionId, @Nullable UUID initiatorId) {
        if (!target.getServer().isSameThread()) {
            throw new IllegalStateException("Player erasure must execute on its server thread");
        }
        this.target = target;
        this.strikeId = strikeId;
        this.executionId = executionId;
        this.initiatorId = initiatorId;
        this.initialHealth = target.getHealth();
        this.state = OrbitalErasureAttachments.begin(target);
        this.state.begin(this);
    }

    public static @Nullable OrbitalErasureContext current(ServerPlayer target, DamageSource cause) {
        OrbitalErasureState state = OrbitalErasureAttachments.find(target);
        OrbitalErasureContext context = state == null ? null : state.context();
        return context != null && context.target == target && cause.is(DamageTypes.GENERIC_KILL) ? context : null;
    }

    public UUID strikeId() {
        return this.strikeId;
    }

    public UUID executionId() {
        return this.executionId;
    }

    public @Nullable UUID initiatorId() {
        return this.initiatorId;
    }

    public OrbitalErasureState state() {
        return this.state;
    }

    public float initialHealth() {
        return this.initialHealth;
    }

    @Override
    public void close() {
        this.state.close(this);
    }
}
