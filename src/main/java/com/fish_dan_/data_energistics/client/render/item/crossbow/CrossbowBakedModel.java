package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

final class CrossbowBakedModel extends BakedModelWrapper<BakedModel> {

    // The handle center after its authored -22.5 degree rotation, in model coordinates.
    private static final Vector3f GRIP = new Vector3f(8.0F, 3.072985F, 11.880588F).div(16.0F);

    private final List<List<CrossbowGeometry.Part>> frames;
    private final List<CrossbowGeometry.Part> specialAmmo;
    private final Matrix4f root;
    private final ItemOverrides overrides;
    private final List<BakedQuad> foldedQuads;
    private final List<BakedQuad> idleQuads;
    private final List<BakedQuad> loadedQuads;
    private final List<BakedQuad> specialQuads;

    CrossbowBakedModel(BakedModel originalModel, List<List<CrossbowGeometry.Part>> frames,
                       List<CrossbowGeometry.Part> specialAmmo, Matrix4f root, ItemOverrides resourceOverrides) {
        super(originalModel);
        this.frames = frames;
        this.specialAmmo = specialAmmo;
        this.root = root;
        this.foldedQuads = CrossbowGeometry.render(frames, specialAmmo, CrossbowAnimation.Pose.stationary(false), false, root);
        this.idleQuads = CrossbowGeometry.render(frames, specialAmmo, new CrossbowAnimation.Pose(1.0F, 0.0F, 0), false, root);
        this.loadedQuads = CrossbowGeometry.render(frames, specialAmmo, CrossbowAnimation.Pose.stationary(true), false, root);
        this.specialQuads = CrossbowGeometry.render(frames, specialAmmo, CrossbowAnimation.Pose.stationary(true), true, root);
        this.overrides = new ItemOverrides() {

            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                BakedModel replacement = resourceOverrides.resolve(model, stack, level, entity, seed);
                if (replacement != null && replacement != model) {
                    return replacement;
                }
                return new RenderedCrossbow(stack, entity);
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }

    private final class RenderedCrossbow extends BakedModelWrapper<BakedModel> {

        private final @Nullable LivingEntity entity;
        private final boolean special;
        private List<BakedQuad> quads;

        private RenderedCrossbow(ItemStack stack, @Nullable LivingEntity entity) {
            super(CrossbowBakedModel.this.originalModel);
            this.entity = entity;
            ChargedProjectiles projectiles = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            this.special = !projectiles.isEmpty() && MatterConvergingCrossbowItem.isSpecialLightSaberAmmo(projectiles.getItems().getFirst());
            this.quads = foldedQuads;
        }

        private List<BakedQuad> geometry(CrossbowAnimation.Pose pose) {
            if (pose.deployment() == 1.0F && pose.draw() == 0.0F && pose.stage() == 0) {
                return idleQuads;
            }
            if (pose.deployment() == 1.0F && pose.draw() == 1.0F && pose.stage() == 3) {
                return this.special ? specialQuads : loadedQuads;
            }
            return CrossbowGeometry.render(frames, specialAmmo, pose, this.special, root);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            this.originalModel.applyTransform(context, poseStack, leftHand);
            if (context.firstPerson()) {
                // ItemRenderer subsequently subtracts 0.5 from every axis.
                poseStack.translate(0.5F - GRIP.x, 0.5F - GRIP.y, 0.5F - GRIP.z);
            }
            this.quads = foldedQuads;
            if (this.entity != null && isHand(context)) {
                HumanoidArm arm = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                InteractionHand hand = this.entity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                this.quads = geometry(CrossbowAnimationStates.pose(this.entity, hand, partial));
            }
            return this;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? this.quads : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType renderType) {
            return getQuads(state, side, random);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }
    }

    private static boolean isHand(ItemDisplayContext context) {
        return context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
