package com.fish_dan_.data_energistics.mixin.client;

import net.minecraft.world.item.ItemStack;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import appeng.client.gui.widgets.AETextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

/**
 * Exposes only the native pattern-terminal caches and refresh operation needed when the Trinity search scope changes.
 */
@Mixin(PatternAccessTermScreen.class)
public interface PatternAccessTermScreenAccessor {

    /**
     * Returns AE2's native field so per-pattern feedback uses the exact visible query.
     */
    @Accessor("searchField")
    AETextField dataEnergistics$getSearchField();

    /**
     * Returns AE2's search-result cache so a mode change cannot reuse provider matches from the prior scope.
     */
    @Accessor("cachedSearches")
    Map<String, Set<Object>> dataEnergistics$getCachedSearches();

    /**
     * Returns AE2's decoded-pattern text cache so a mode change rebuilds input/output candidates.
     */
    @Accessor("patternSearchText")
    Map<ItemStack, String> dataEnergistics$getPatternSearchText();

    /**
     * Reapplies AE2's existing provider filtering, row construction, and scrollbar update.
     */
    @Invoker("refreshList")
    void dataEnergistics$refreshList();
}
