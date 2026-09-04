package com.fish_dan_.data_energistics.client.crafting.tree.preferences;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanLayoutMode;

import net.neoforged.fml.loading.FMLPaths;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Client-local presentation preferences, never synchronized to or persisted by a server. */
public record CraftingPlanTreePreferences(int autoExpandBudget, boolean compact, boolean missingOnly,
                                          boolean screenshotAmounts, CraftingPlanLayoutMode layoutMode) {

    public static final CraftingPlanTreePreferences DEFAULT = new CraftingPlanTreePreferences(256, false, false,
            false, CraftingPlanLayoutMode.LAYERED);

    public CraftingPlanTreePreferences {
        if (autoExpandBudget < 64 || autoExpandBudget > 4096) throw new IllegalArgumentException("Plan-tree budget outside [64, 4096]");
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("data_energistics").resolve("crafting_plan_tree.json");
    }

    public static CraftingPlanTreePreferences load() {
        Path path = path();
        if (!Files.exists(path)) return DEFAULT;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return new CraftingPlanTreePreferences(json.get("autoExpandBudget").getAsInt(),
                    json.get("compact").getAsBoolean(), json.get("missingOnly").getAsBoolean(),
                    json.get("screenshotAmounts").getAsBoolean(),
                    CraftingPlanLayoutMode.valueOf(json.get("layoutMode").getAsString()));
        } catch (IOException | RuntimeException failure) {
            Data_Energistics.LOGGER.warn("Cannot read plan-tree preferences at {}; using defaults without overwriting the file", path, failure);
            return DEFAULT;
        }
    }

    /** Atomic replacement keeps a crash during saving from corrupting an otherwise valid preference file. */
    public void save() {
        Path target = path();
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("autoExpandBudget", this.autoExpandBudget);
            json.addProperty("compact", this.compact);
            json.addProperty("missingOnly", this.missingOnly);
            json.addProperty("screenshotAmounts", this.screenshotAmounts);
            json.addProperty("layoutMode", this.layoutMode.name());
            temporary = Files.createTempFile(target.getParent(), "crafting-plan-tree-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            Data_Energistics.LOGGER.error("Cannot save plan-tree preferences at {}", target, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    Data_Energistics.LOGGER.warn("Cannot remove plan-tree temporary file {}", temporary, failure);
                }
            }
        }
    }
}
