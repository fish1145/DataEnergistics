package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.weapon.draconicevolution.DraconicReactorPayload;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** Resolves explosive grenade ammunition without loading absent optional mods. */
public final class GrenadePayload {

    private GrenadePayload() {}

    /** May be used on either game side; only registered TNT or the loaded mod's reactor core is accepted. */
    public static boolean isExplosive(ItemStack ammunition) {
        return TntPayload.isTnt(ammunition) || isReactorCore(ammunition);
    }

    /**
     * Triggers the native payload on the server collision tick. Ammunition must satisfy {@link #isExplosive}.
     * The impact face is absent for entity hits; the shooter may be absent after reload.
     */
    public static void detonate(ServerLevel level, ItemStack ammunition, Vec3 impact, @Nullable Direction face,
                                @Nullable LivingEntity owner) {
        if (isReactorCore(ammunition)) {
            try {
                DraconicReactorPayload.detonate(level, impact);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to start reactor core grenade explosion at {} in {}", impact,
                        level.dimension().location(), exception);
            }
            return;
        }
        TntPayload.detonate(level, ammunition, impact, face, owner);
    }

    private static boolean isReactorCore(ItemStack ammunition) {
        return ModFlags.isDraconicEvolutionLoaded() && DraconicReactorPayload.isReactorCore(ammunition);
    }
}
