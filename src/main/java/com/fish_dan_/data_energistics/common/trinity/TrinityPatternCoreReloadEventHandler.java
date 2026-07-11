package com.fish_dan_.data_energistics.common.trinity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Invalidates retained P-core recipe caches whenever server data packs finish rebinding tags and recipes.
 */
public final class TrinityPatternCoreReloadEventHandler {

    /**
     * Advances the shared epoch only for the authoritative server data load.
     *
     * @param event tag update event emitted at the end of data reload
     */
    @SubscribeEvent
    public void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            TrinityPatternCoreReloadEpoch.advance();
        }
    }
}
