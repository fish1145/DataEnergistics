package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationAccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.parts.encoding.EncodingMode;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the exact inputs that make one encoding menu's provider presentation stale.
 *
 * <p>
 * Provider discovery remains on the server thread. Stable menus only compare identities, a monotonic provider
 * revision, and their small local context instead of repeatedly traversing every machine on the grid.
 * </p>
 */
public final class PatternProviderSyncTracker {

    /**
     * Conservative fallback for third-party presentation changes that do not publish an AE2 crafting update.
     */
    private static final int CONSISTENCY_REFRESH_INTERVAL_TICKS = 100;

    private long publicationScope = Long.MIN_VALUE;
    private long providerRevision = Long.MIN_VALUE;
    private long refreshedTick = Long.MIN_VALUE;
    @Nullable
    private ResourceLocation preferredWorkstationId;
    @Nullable
    private EncodingMode encodingMode;
    private ItemStack encodedPattern = ItemStack.EMPTY;
    private boolean dirty = true;

    /**
     * Determines whether the cached menu rows are stale.
     *
     * @param currentPublication    current grid-local provider publication identity
     * @param currentTick           server game time used only for conservative presentation validation
     * @param currentPreferredId    current preferred workstation
     * @param currentMode           current encoding mode
     * @param currentEncodedPattern currently encoded pattern
     * @return {@code true} when provider discovery must run
     */
    public boolean needsRefresh(
                                PublicationVersion currentPublication,
                                long currentTick,
                                @Nullable ResourceLocation currentPreferredId,
                                EncodingMode currentMode,
                                ItemStack currentEncodedPattern) {
        return this.dirty ||
                this.publicationScope != currentPublication.scope() ||
                this.providerRevision != currentPublication.revision() ||
                !sameResourceLocation(this.preferredWorkstationId, currentPreferredId) ||
                this.encodingMode != currentMode ||
                !ItemStack.isSameItemSameComponents(this.encodedPattern, currentEncodedPattern) ||
                currentTick - this.refreshedTick >= CONSISTENCY_REFRESH_INTERVAL_TICKS;
    }

    /**
     * Records the inputs used by a completed server-thread provider discovery.
     */
    public void refreshed(
                          PublicationVersion currentPublication,
                          long currentTick,
                          @Nullable ResourceLocation currentPreferredId,
                          EncodingMode currentMode,
                          ItemStack currentEncodedPattern) {
        this.publicationScope = currentPublication.scope();
        this.providerRevision = currentPublication.revision();
        this.refreshedTick = currentTick;
        this.preferredWorkstationId = currentPreferredId;
        this.encodingMode = currentMode;
        this.encodedPattern = currentEncodedPattern.copy();
        this.dirty = false;
    }

    /**
     * Clears every grid-bound cache key when the menu loses its active grid.
     */
    public void clear() {
        this.publicationScope = Long.MIN_VALUE;
        this.providerRevision = Long.MIN_VALUE;
        this.refreshedTick = Long.MIN_VALUE;
        this.preferredWorkstationId = null;
        this.encodingMode = null;
        this.encodedPattern = ItemStack.EMPTY;
        this.dirty = true;
    }

    private static boolean sameResourceLocation(
                                                @Nullable ResourceLocation left,
                                                @Nullable ResourceLocation right) {
        return left == right || left != null && left.equals(right);
    }

    /**
     * Captures the O(1) publication identity used to invalidate one menu without enumerating provider machines.
     *
     * @param grid active server-side grid
     * @return current publication scope and revision
     */
    public static PublicationVersion capturePublicationVersion(IGrid grid) {
        if (!(grid.getCraftingService() instanceof CraftingProviderPublicationAccess publicationAccess)) {
            throw new IllegalStateException("Crafting service does not expose its provider publication revision");
        }
        var publications = publicationAccess.data_energistics$craftingProviderPublicationIndex();
        return new PublicationVersion(publications.publicationScope(), publications.publicationRevision());
    }

    /**
     * Immutable identity of one grid-local provider publication snapshot.
     */
    public record PublicationVersion(long scope, long revision) {}
}
