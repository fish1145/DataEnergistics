package com.fish_dan_.data_energistics.client.render.orbital.animation;

/** Maps sparse server baselines to a continuous, pause-aware client tick clock. One instance belongs to one level. */
public final class OrbitalAnimationClock {

    private long revision = -1;
    private long localOrigin;
    private long serverOrigin;

    /**
     * Samples cosmetic time on the client thread. Partial ticks must be in [0, 1]. A lower revision or local tick
     * restarts the mapping after a reconnect; a newer delayed baseline cannot rewind an ongoing animation.
     */
    public double sample(long serverRevision, long clientTick, float partialTick) {
        if (revision < 0 || serverRevision < revision || clientTick < localOrigin) {
            serverOrigin = serverRevision;
            localOrigin = clientTick;
        } else if (serverRevision != revision) {
            serverOrigin = Math.max(serverRevision, serverOrigin + clientTick - localOrigin);
            localOrigin = clientTick;
        }
        revision = serverRevision;
        return serverOrigin + (clientTick - localOrigin) + (double) partialTick;
    }

    /** Bounded trigonometric input preserves sub-tick motion even in very old worlds. */
    public static float angle(double ticks, long seed, double ticksPerTurn) {
        double phase = (ticks % ticksPerTurn + Math.floorMod(seed, 10_000L)) % ticksPerTurn;
        return (float) (phase * (Math.PI * 2.0 / ticksPerTurn));
    }
}
