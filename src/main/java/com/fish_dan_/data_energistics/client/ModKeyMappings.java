package com.fish_dan_.data_energistics.client;

import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {

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

    private ModKeyMappings() {}
}
