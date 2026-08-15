package com.fish_dan_.data_energistics.client.render.item;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Marks the normal package model for BEWLR rendering while retaining a directly renderable delegate.
 */
public final class OrderPackageBakedModel extends BakedModelWrapper<BakedModel> {

    /** Whether this wrapper should route the current rendering pass to the custom renderer. */
    private final boolean customRenderer;

    /** Wraps the registered inventory model and enables dynamic target rendering. */
    public OrderPackageBakedModel(BakedModel originalModel) {
        this(originalModel, true);
    }

    private OrderPackageBakedModel(BakedModel originalModel, boolean customRenderer) {
        super(originalModel);
        this.customRenderer = customRenderer;
    }

    /** Returns a model pass that renders the unmarked package texture without re-entering the BEWLR. */
    public BakedModel withoutCustomRenderer() {
        return this.customRenderer ? new OrderPackageBakedModel(this.originalModel, false) : this;
    }

    @Override
    public boolean isCustomRenderer() {
        return this.customRenderer || this.originalModel.isCustomRenderer();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack,
                                     boolean applyLeftHandTransform) {
        BakedModel transformed = this.originalModel.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return transformed == this.originalModel ? this : new OrderPackageBakedModel(transformed, this.customRenderer);
    }
}
