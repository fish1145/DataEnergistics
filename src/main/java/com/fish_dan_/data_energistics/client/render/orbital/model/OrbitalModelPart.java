package com.fish_dan_.data_energistics.client.render.orbital.model;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** The independently editable body, reduced mesh and luminous inserts of each construct module. */
public enum OrbitalModelPart {

    CORE("core"),
    RAIL("rail"),
    RING_SEGMENT("ring_segment"),
    CRADLE("cradle"),
    PAYLOAD("payload");

    private final ModelResourceLocation body;
    private final ModelResourceLocation reduced;
    private final ModelResourceLocation light;

    OrbitalModelPart(String path) {
        body = location(path, "body");
        reduced = location(path, "reduced");
        light = location(path, "light");
    }

    private static ModelResourceLocation location(String part, String variant) {
        return ModelResourceLocation.standalone(Data_Energistics.id("orbital/" + part + "/" + variant));
    }

    ModelResourceLocation model(boolean detailed, boolean emissive) {
        return emissive ? light : (detailed ? body : reduced);
    }

    /** Registers standalone models on the client mod bus so resource packs and reloads replace them normally. */
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (OrbitalModelPart part : values()) {
            event.register(part.body);
            event.register(part.reduced);
            event.register(part.light);
        }
    }
}
