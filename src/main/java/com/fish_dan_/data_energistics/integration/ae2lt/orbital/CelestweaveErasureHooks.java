package com.fish_dan_.data_energistics.integration.ae2lt.orbital;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/** Optional LT hook signatures shared by injection declarations and non-loading bytecode diagnostics. */
public final class CelestweaveErasureHooks {

    public static final String TARGET_CLASS = "com.moakiee.ae2lt.celestweave.CelestweaveArmorUndyingHandler";
    public static final String FORCED_DEATH = "tryProtectForcedDeath(Lnet/minecraft/server/level/ServerPlayer;)Z";
    public static final String DEATH_SIDE_EFFECT = "protectBeforeDeathSideEffect(Lnet/minecraft/server/level/ServerPlayer;)Z";
    public static final String PROTECTED_TICK = "wasProtectedThisTick(Lnet/minecraft/world/entity/LivingEntity;)Z";
    public static final String WINDOW = "tryProtectWithinWindow(Lnet/minecraft/server/level/ServerPlayer;J)Z";
    public static final String TRIGGER = "tryTrigger(Lnet/minecraft/server/level/ServerPlayer;J)Z";
    private static final List<String> HOOKS = List.of(FORCED_DEATH, DEATH_SIDE_EFFECT, PROTECTED_TICK, WINDOW, TRIGGER);

    private CelestweaveErasureHooks() {}

    /** Reads the class node already supplied by Mixin; missing or changed methods are diagnostic, never fatal. */
    public static List<String> missingMethods(ClassNode target) {
        ObjectOpenHashSet<String> available = new ObjectOpenHashSet<>();
        for (MethodNode method : target.methods) {
            available.add(method.name + method.desc);
        }
        List<String> missing = new ObjectArrayList<>();
        for (String hook : HOOKS) {
            if (!available.contains(hook)) {
                missing.add(hook);
            }
        }
        return List.copyOf(missing);
    }
}
