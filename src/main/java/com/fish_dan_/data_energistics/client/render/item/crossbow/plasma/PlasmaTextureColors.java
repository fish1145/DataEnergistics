package com.fish_dan_.data_energistics.client.render.item.crossbow.plasma;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/** Texture IO occurs once per resource reload, never on the per-frame ammunition render path. */
public final class PlasmaTextureColors extends SimplePreparableReloadListener<Map<RailAmmunition, PlasmaPalette>> {

    private static final ResourceLocation DATA_TEXTURE = Data_Energistics.id("textures/block/key/data_flow.png");
    private static final ResourceLocation FE_TEXTURE = ResourceLocation.fromNamespaceAndPath("appflux", "textures/energy/fe.png");
    private static Map<RailAmmunition, PlasmaPalette> colors = Map.of();

    public static PlasmaPalette palette(RailAmmunition ammo) {
        return colors.getOrDefault(ammo, PlasmaPalette.NEUTRAL);
    }

    @Override
    protected Map<RailAmmunition, PlasmaPalette> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<RailAmmunition, PlasmaPalette> result = new EnumMap<>(RailAmmunition.class);
        result.put(RailAmmunition.DATA, read(manager, DATA_TEXTURE));
        // Applied Flux is optional; the FE resource exists whenever that ammunition is available.
        if (manager.getResource(FE_TEXTURE).isPresent()) result.put(RailAmmunition.FE, read(manager, FE_TEXTURE));
        return result;
    }

    private static PlasmaPalette read(ResourceManager manager, ResourceLocation texture) {
        try (var input = manager.getResourceOrThrow(texture).open(); NativeImage image = NativeImage.read(input)) {
            return PlasmaPalette.sample(image.getWidth(), image.getHeight(), image::getPixelRGBA);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read plasma color texture " + texture, exception);
        }
    }

    @Override
    protected void apply(Map<RailAmmunition, PlasmaPalette> prepared, ResourceManager manager, ProfilerFiller profiler) {
        colors = Map.copyOf(prepared);
    }
}
