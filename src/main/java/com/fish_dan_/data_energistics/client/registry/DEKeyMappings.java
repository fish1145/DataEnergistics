package com.fish_dan_.data_energistics.client.registry;

import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class DEKeyMappings {

    public static final String KEY_CATEGORY = "key.categories.data_energistics";
    public static final KeyMapping OPEN_PATTERN_PROVIDER = new KeyMapping(
            "key.data_energistics.open_pattern_provider",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            KEY_CATEGORY);
    public static final KeyMapping RENAME_PATTERN_PROVIDER = new KeyMapping(
            "key.data_energistics.rename_pattern_provider",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE = new KeyMapping(
            "key.data_energistics.toggle_digital_storage_depot_bucket_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY);
    public static final KeyMapping OPEN_ORBITAL_CONTROL = new KeyMapping(
            "key.data_energistics.open_orbital_control",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_ORBITAL_CONTROL_HUD = new KeyMapping(
            "key.data_energistics.toggle_orbital_control_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY);
    public static final KeyMapping PREVIOUS_TRINITY_CYCLE = new KeyMapping(
            "key.data_energistics.previous_trinity_cycle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KEY_CATEGORY);
    public static final KeyMapping NEXT_TRINITY_CYCLE = new KeyMapping(
            "key.data_energistics.next_trinity_cycle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_CROSSBOW_RAIL = new KeyMapping(
            "key.data_energistics.toggle_crossbow_rail",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_CROSSBOW_ARMS = new KeyMapping(
            "key.data_energistics.toggle_crossbow_arms",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY);

    private DEKeyMappings() {}
}
