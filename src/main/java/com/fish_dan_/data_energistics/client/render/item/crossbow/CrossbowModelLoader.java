package com.fish_dan_.data_energistics.client.render.item.crossbow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrossbowModelLoader implements IGeometryLoader<CrossbowGeometry> {

    @Override
    public CrossbowGeometry read(JsonObject json, JsonDeserializationContext context) {
        JsonObject poses = GsonHelper.getAsJsonObject(json, "poses");
        List<List<CrossbowGeometry.Element>> frames = new ArrayList<>();
        for (String name : List.of("off", "on", "0", "1", "2")) {
            List<CrossbowGeometry.Element> frame = readPose(poses, name, context);
            if (frame.isEmpty() || !frames.isEmpty() && frame.size() != frames.getFirst().size()) {
                throw new JsonParseException("Crossbow pose " + name + " must contain the same nonzero number of parts");
            }
            frames.add(frame);
        }
        return new CrossbowGeometry(List.copyOf(frames), readPose(poses, "light_saber", context));
    }

    private static List<CrossbowGeometry.Element> readPose(JsonObject poses, String name,
                                                           JsonDeserializationContext context) {
        ResourceLocation file = ResourceLocation.parse(GsonHelper.getAsString(poses, name));
        try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()) {
            JsonArray elements = GsonHelper.getAsJsonArray(GsonHelper.parse(reader), "elements");
            List<CrossbowGeometry.Element> result = new ArrayList<>(elements.size());
            for (var value : elements) {
                JsonObject element = value.getAsJsonObject().deepCopy();
                Quaternionf rotation = new Quaternionf();
                Vector3f origin = new Vector3f();
                if (element.has("rotation")) {
                    JsonObject transform = element.remove("rotation").getAsJsonObject();
                    origin = origin(transform);
                    if (transform.has("axis")) {
                        float angle = radians(GsonHelper.getAsFloat(transform, "angle"));
                        switch (GsonHelper.getAsString(transform, "axis")) {
                            case "x" -> rotation.rotationX(angle);
                            case "y" -> rotation.rotationY(angle);
                            case "z" -> rotation.rotationZ(angle);
                            default -> throw new JsonParseException("Invalid crossbow rotation axis in " + file);
                        }
                    } else {
                        // Blockbench 1.21.11 exports folded quarter turns as Euler rotations.
                        rotation.rotationXYZ(radians(GsonHelper.getAsFloat(transform, "x")),
                                radians(GsonHelper.getAsFloat(transform, "y")),
                                radians(GsonHelper.getAsFloat(transform, "z")));
                    }
                    if (GsonHelper.getAsBoolean(transform, "rescale", false)) {
                        throw new JsonParseException("Crossbow poses must bake rescale into their geometry: " + file);
                    }
                }
                BlockElement cube = context.deserialize(element, BlockElement.class);
                Vector3f center = new Vector3f(cube.from).add(cube.to).mul(0.5F);
                center.sub(origin).rotate(rotation).add(origin).div(16.0F);
                Vector3f size = new Vector3f(cube.to).sub(cube.from).div(16.0F);
                if (!center.isFinite() || !size.isFinite() || size.x == 0.0F || size.y == 0.0F || size.z == 0.0F) {
                    throw new JsonParseException("Crossbow part must have finite nonzero dimensions: " + file);
                }
                CrossbowMotion motion = CrossbowMotion.valueOf(GsonHelper.getAsString(element, "motion", "fixed").toUpperCase(Locale.ROOT));
                CrossbowDeployment deployment = CrossbowDeployment.valueOf(GsonHelper.getAsString(element, "deployment", "frame").toUpperCase(Locale.ROOT));
                result.add(new CrossbowGeometry.Element(cube, new CrossbowPartPose(center, rotation, size), motion, deployment));
            }
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException exception) {
            throw new JsonParseException("Unable to read crossbow pose " + file, exception);
        }
    }

    private static float radians(float degrees) {
        if (!Float.isFinite(degrees)) {
            throw new JsonParseException("Crossbow rotation must be finite");
        }
        return (float) Math.toRadians(degrees);
    }

    private static Vector3f origin(JsonObject json) {
        JsonArray values = GsonHelper.getAsJsonArray(json, "origin");
        if (values.size() != 3) {
            throw new JsonParseException("Expected three coordinates for origin");
        }
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }
}
