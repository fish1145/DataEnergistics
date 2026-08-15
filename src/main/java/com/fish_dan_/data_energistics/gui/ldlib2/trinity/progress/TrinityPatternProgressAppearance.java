package com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

/** Selects the authored texture used for one pattern-maintenance state. */
public enum TrinityPatternProgressAppearance {

    CAPACITY("capacity_vertical"),
    MIGRATION("migration_vertical"),
    REFUND("refund_vertical"),
    COMPLETED("completed_vertical"),
    FAILED("failed_vertical");

    private static final String TEXTURE_ROOT = "textures/guis/trinity/progress/";

    private final ResourceLocation verticalTexture;

    TrinityPatternProgressAppearance(String verticalTexture) {
        this.verticalTexture = texture(verticalTexture);
    }

    ResourceLocation verticalTexture() {
        return this.verticalTexture;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, TEXTURE_ROOT + name + ".png");
    }
}
