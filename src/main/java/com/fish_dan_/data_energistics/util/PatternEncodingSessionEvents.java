package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceSession;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class PatternEncodingSessionEvents {

    private PatternEncodingSessionEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PatternEncodingSessionState.clear(event.getEntity().getUUID());
        PatternEncodingPreferenceSession.clearForMenu(event.getEntity().containerMenu);
    }
}
