package com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

/** Selects the authored texture used for one pattern-maintenance state. */
public enum TrinityPatternProgressAppearance {

    CAPACITY(null, "capacity_vertical"),
    MIGRATION("migration_horizontal", "migration_vertical"),
    REFUND("refund_horizontal", "refund_vertical"),
    COMPLETED("completed_horizontal", "completed_vertical"),
    FAILED("failed_horizontal", "failed_vertical");

    private static final String TEXTURE_ROOT = "textures/guis/trinity/progress/";

    private final @Nullable ResourceLocation horizontalTexture;
    private final ResourceLocation verticalTexture;

    TrinityPatternProgressAppearance(@Nullable String horizontalTexture, String verticalTexture) {
        this.horizontalTexture = horizontalTexture == null ? null : texture(horizontalTexture);
        this.verticalTexture = texture(verticalTexture);
    }

    ResourceLocation horizontalTexture() {
        if (this.horizontalTexture == null) {
            throw new IllegalStateException(name() + " has no horizontal progress texture");
        }
        return this.horizontalTexture;
    }

    ResourceLocation verticalTexture() {
        return this.verticalTexture;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, TEXTURE_ROOT + name + ".png");
    }
}
