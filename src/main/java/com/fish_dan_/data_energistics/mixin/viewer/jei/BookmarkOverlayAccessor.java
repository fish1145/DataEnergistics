package com.fish_dan_.data_energistics.mixin.viewer.jei;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Client-lifecycle bridge to JEI's bookmark list because its public overlay API only exposes hover lookup.
 * Used only while the current JEI runtime is available; callers must not retain the list across reloads.
 */
@Mixin(value = BookmarkOverlay.class, remap = false)
public interface BookmarkOverlayAccessor {

    /** Returns the non-null live list on the client thread; reading it does not change bookmarks. */
    @Accessor("bookmarkList")
    BookmarkList data_energistics$getBookmarkList();
}
