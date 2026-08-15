package com.fish_dan_.data_energistics.common.pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Items;

import appeng.helpers.patternprovider.PatternContainer;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Resolves stable provider identities from AE2 terminal containers without reflection or menu-session state.
 */
public interface ProviderIdentityResolver {

    /**
     * Creates the resolver backed by live Minecraft and AE2 registry metadata.
     *
     * @return production resolver
     */
    static ProviderIdentityResolver create() {
        return new RegistryBackedProviderIdentityResolver();
    }

    /**
     * Resolves one provider according to physical, Trinity and virtual identity precedence.
     *
     * @param provider discovered AE2 terminal container
     * @return stable provider identity
     * @throws IllegalStateException when a purported physical provider has incomplete or ambiguous world metadata
     */
    ProviderIdentity resolve(PatternContainer provider);

    /**
     * Builds the canonical structured fallback used consistently by live and degraded virtual providers.
     */
    static ProviderIdentity.Virtual virtualIdentity(@Nullable ResourceLocation iconItemId, Component name) {
        ResourceLocation airId = BuiltInRegistries.ITEM.getKey(Items.AIR);
        Optional<ResourceLocation> normalizedIcon = Optional.ofNullable(iconItemId)
                .filter(id -> !id.equals(airId));
        String componentEncoding = GsonHelper.toStableString(
                ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, name).getOrThrow());
        return new ProviderIdentity.Virtual(normalizedIcon, componentEncoding);
    }
}
