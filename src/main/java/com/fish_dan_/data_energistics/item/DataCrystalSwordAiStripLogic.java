package com.fish_dan_.data_energistics.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class DataCrystalSwordAiStripLogic {

    public static final String TAG_EXPIRE_TICK = "data_energistics:ai_strip_expire_tick";
    public static final String TAG_ORIGINAL_NO_AI = "data_energistics:ai_strip_original_no_ai";

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }

        CompoundTag persistentData = mob.getPersistentData();
        if (!persistentData.contains(TAG_EXPIRE_TICK)) {
            return;
        }

        long expireTick = persistentData.getLong(TAG_EXPIRE_TICK);
        if (mob.level().getGameTime() < expireTick) {
            if (!mob.isNoAi()) {
                mob.setNoAi(true);
            }
            return;
        }

        boolean originalNoAi = persistentData.getBoolean(TAG_ORIGINAL_NO_AI);
        mob.setNoAi(originalNoAi);
        persistentData.remove(TAG_EXPIRE_TICK);
        persistentData.remove(TAG_ORIGINAL_NO_AI);
    }
}
