package com.fish_dan_.data_energistics.common.recipe;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Advances the shared recipe epoch after the authoritative server data reload completes. */
public final class RecipeReloadEventHandler {

    /**
     * Invalidates all recipe-derived caches after tags and recipes have been rebound.
     *
     * @param event tag update event emitted at the end of data reload
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            RecipeReloadEpoch.advance();
        }
    }
}
