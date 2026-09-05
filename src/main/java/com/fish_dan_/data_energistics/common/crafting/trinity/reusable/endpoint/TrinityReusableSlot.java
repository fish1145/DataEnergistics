package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Route-owned reusable assets that travel with one physical Trinity core slot. */
public final class TrinityReusableSlot {

    private final PatternRoute route;
    private final PersistentReusableCraftingEndpoint endpoint;
    private boolean closeRequested;

    public TrinityReusableSlot(PatternRoute route) {
        this(route, new PersistentReusableCraftingEndpoint(targetIdentity(route.coreId(), route.slot())), false);
    }

    private TrinityReusableSlot(PatternRoute route, PersistentReusableCraftingEndpoint endpoint, boolean closeRequested) {
        this.route = route;
        this.endpoint = endpoint;
        this.closeRequested = closeRequested;
    }

    public PatternRoute route() {
        return route;
    }

    public PersistentReusableCraftingEndpoint endpoint() {
        return endpoint;
    }

    public boolean hasWork() {
        return endpoint.hasResidentSession();
    }

    public boolean closeRequested() {
        return closeRequested;
    }

    public void requestClose() {
        closeRequested = true;
    }

    public void clearCloseRequest() {
        closeRequested = false;
    }

    public void closeSessions(Host host) {
        for (var session : endpoint.snapshot()) {
            endpoint.close(session.binding().identity().sessionId(), host);
        }
        closeRequested = false;
        host.persistChanges();
    }

    public boolean containsSession(UUID sessionId) {
        return endpoint.query(sessionId).isPresent();
    }

    /** Revalidates loaded recipe semantics once, before restored assets can resume native execution. */
    public void validateRestoredPublication(@Nullable IMolecularAssemblerSupportedPattern pattern, HolderLookup.Provider registries) {
        if (!hasWork()) {
            return;
        }
        if (pattern == null) {
            requestClose();
            return;
        }
        TrinityPatternIdentity current = TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(pattern), registries);
        for (var entry : endpoint.snapshot()) {
            if (!entry.settlementAcknowledged() && !entry.binding().publicationIdentity().equals(current)) {
                requestClose();
            }
        }
    }

    public CompoundTag writeToTag(HolderLookup.Provider registries, boolean removingPattern) {
        CompoundTag tag = new CompoundTag();
        tag.put("route", route.writeToTag());
        tag.put("endpoint", ReusableCraftingEndpointNbtCodec.encode(endpoint, registries));
        tag.putBoolean("close_requested", closeRequested || removingPattern);
        return tag;
    }

    public static TrinityReusableSlot readFromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("route", Tag.TAG_COMPOUND) || !tag.contains("endpoint", Tag.TAG_COMPOUND) ||
                !tag.contains("close_requested", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Incomplete persisted reusable core slot");
        }
        PatternRoute route = PatternRoute.readFromTag(tag.getCompound("route"));
        PersistentReusableCraftingEndpoint endpoint = ReusableCraftingEndpointNbtCodec.decode(tag.getCompound("endpoint"), registries);
        if (!targetIdentity(route.coreId(), route.slot()).equals(endpoint.targetIdentity())) {
            throw new IllegalArgumentException("Reusable endpoint does not match its persisted core route");
        }
        return new TrinityReusableSlot(route, endpoint, tag.getBoolean("close_requested"));
    }

    public static String targetIdentity(UUID coreId, int slot) {
        return "trinity-core:" + coreId + "/slot:" + slot;
    }
}
