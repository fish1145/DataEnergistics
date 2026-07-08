package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BreezeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class MeVacuumBreezeVisualRenderer {

    private static final ResourceLocation BREEZE_WIND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
    private static final double[] WIND_DISTANCES = { 0.45D, 0.85D, 1.25D };
    private static final float[] WIND_SCALES = { 2.50F, 3.50F, 4.50F };
    private static final double MODEL_VERTICAL_OFFSET = -1.5D;
    private static final float WIND_TEXTURE_SCROLL_SPEED = 0.02F;
    private static final float WIND_ROTATION_SPEED = 14.0F;
    private static final float WIND_SEGMENT_ROTATION_OFFSET = 48.0F;
    private static final float PLAYER_RENDER_SCALE = 0.9375F;
    private static final float MODEL_SPACE_SCALE = 1.0F / 16.0F;
    private static final float THIRD_PERSON_ITEM_SCALE = 0.6F;
    private static final float THIRD_PERSON_RIGHT_Y = 2.5F * MODEL_SPACE_SCALE;
    private static final float THIRD_PERSON_LEFT_Y = 2.25F * MODEL_SPACE_SCALE;
    private static final float THIRD_PERSON_Z = -2.0F * MODEL_SPACE_SCALE;
    private static final float FIRST_PERSON_ITEM_MUZZLE_X = 8.0F * MODEL_SPACE_SCALE;
    private static final float FIRST_PERSON_ITEM_MUZZLE_Y = 7.0F * MODEL_SPACE_SCALE;
    private static final float FIRST_PERSON_ITEM_MUZZLE_Z = -0.30F;
    private static final Vector3f MUZZLE_CENTER = new Vector3f(8.0F * MODEL_SPACE_SCALE, 7.0F * MODEL_SPACE_SCALE,
            0.0F);

    private static BreezeModel<Breeze> windModel;
    private static PlayerModel<AbstractClientPlayer> widePlayerModel;
    private static PlayerModel<AbstractClientPlayer> slimPlayerModel;

    private MeVacuumBreezeVisualRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isUsingItem() || player.isShiftKeyDown()) {
            return;
        }

        ItemStack usedItem = player.getUseItem();
        if (!isWorkingVacuum(usedItem)) {
            return;
        }

        renderWindColumn(event, minecraft, player);
    }

    static boolean isWorkingVacuum(ItemStack stack) {
        return stack.is(ModItems.ME_VACUUM.get()) && stack.getItem() instanceof PoweredEnergyItem poweredItem && poweredItem.hasSufficientEnergy(stack);
    }

    static void renderFirstPersonItemWind(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource) {
        BreezeModel<Breeze> model = getWindModel(minecraft);
        Player player = minecraft.player;
        float age = player == null ? 0.0F : player.tickCount;
        RenderType renderType = RenderType.breezeWind(BREEZE_WIND_TEXTURE,
                age * WIND_TEXTURE_SCROLL_SPEED % 1.0F, 0.0F);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        resetWindModel(model);

        poseStack.pushPose();
        poseStack.translate(FIRST_PERSON_ITEM_MUZZLE_X, FIRST_PERSON_ITEM_MUZZLE_Y, FIRST_PERSON_ITEM_MUZZLE_Z);
        poseStack.mulPose(Axis.XN.rotationDegrees(90.0F));
        for (int segment = 0; segment < WIND_DISTANCES.length; segment++) {
            renderItemWindSegment(model, poseStack, consumer, age, segment);
        }
        poseStack.popPose();
    }

    private static void renderWindColumn(RenderLevelStageEvent event, Minecraft minecraft, LocalPlayer player) {
        BreezeModel<Breeze> model = getWindModel(minecraft);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        MuzzlePose muzzlePose = resolveThirdPersonMuzzlePose(minecraft, player, partialTick);
        Vec3 muzzleOrigin = muzzlePose.origin();
        Vec3 muzzleAxis = muzzlePose.axis();
        Vec3 camera = event.getCamera().getPosition();
        float age = player.tickCount + partialTick;
        Quaternionf muzzleAlignment = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) muzzleAxis.x,
                (float) muzzleAxis.y, (float) muzzleAxis.z);
        RenderType renderType = RenderType.breezeWind(BREEZE_WIND_TEXTURE,
                age * WIND_TEXTURE_SCROLL_SPEED % 1.0F, 0.0F);

        resetWindModel(model);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(renderType);
        for (int segment = 0; segment < WIND_DISTANCES.length; segment++) {
            Vec3 position = muzzleOrigin.add(muzzleAxis.scale(WIND_DISTANCES[segment]));
            renderWindSegment(model, poseStack, consumer, camera, position, muzzleAlignment, age, segment);
        }
        buffer.endBatch(renderType);
    }

    private static MuzzlePose resolveThirdPersonMuzzlePose(Minecraft minecraft, AbstractClientPlayer player,
                                                           float partialTick) {
        PlayerModel<AbstractClientPlayer> model = getPlayerModel(minecraft, player);
        HumanoidArm arm = getUsedArm(player);
        preparePlayerModel(model, player, partialTick);

        PoseStack muzzleStack = new PoseStack();
        Vec3 playerPosition = getInterpolatedPosition(player, partialTick);
        Vec3 renderOffset = player.isCrouching() ? new Vec3(0.0D, (double) (player.getScale() * -2.0F) / 16.0D, 0.0D) : Vec3.ZERO;
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        muzzleStack.translate(playerPosition.x + renderOffset.x, playerPosition.y + renderOffset.y,
                playerPosition.z + renderOffset.z);
        muzzleStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        muzzleStack.scale(-1.0F, -1.0F, 1.0F);
        muzzleStack.scale(PLAYER_RENDER_SCALE, PLAYER_RENDER_SCALE, PLAYER_RENDER_SCALE);
        muzzleStack.translate(0.0F, -1.501F, 0.0F);
        model.translateToHand(arm, muzzleStack);
        muzzleStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        muzzleStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        boolean leftHand = arm == HumanoidArm.LEFT;
        muzzleStack.translate((float) (leftHand ? -1 : 1) * MODEL_SPACE_SCALE, 0.125F, -0.625F);
        applyMeVacuumThirdPersonTransform(muzzleStack, leftHand);
        muzzleStack.translate(-0.5F, -0.5F, -0.5F);
        return makeMuzzlePose(muzzleStack);
    }

    private static MuzzlePose makeMuzzlePose(PoseStack muzzleStack) {
        Matrix4f pose = muzzleStack.last().pose();
        Vector3f muzzle = pose.transformPosition(MUZZLE_CENTER, new Vector3f());
        Vector3f axis = muzzleStack.last().transformNormal(0.0F, 0.0F, -1.0F, new Vector3f()).normalize();
        return new MuzzlePose(new Vec3(muzzle.x, muzzle.y, muzzle.z), new Vec3(axis.x, axis.y, axis.z));
    }

    private static PlayerModel<AbstractClientPlayer> getPlayerModel(Minecraft minecraft, AbstractClientPlayer player) {
        if (player.getSkin().model() == PlayerSkin.Model.SLIM) {
            if (slimPlayerModel == null) {
                slimPlayerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM),
                        true);
            }
            return slimPlayerModel;
        }

        if (widePlayerModel == null) {
            widePlayerModel = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        return widePlayerModel;
    }

    private static HumanoidArm getUsedArm(Player player) {
        InteractionHand usedHand = player.getUsedItemHand();
        if (usedHand == InteractionHand.MAIN_HAND) {
            return player.getMainArm();
        }
        return player.getMainArm().getOpposite();
    }

    private static void preparePlayerModel(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player,
                                           float partialTick) {
        HumanoidModel.ArmPose mainPose = getArmPose(player, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = getArmPose(player, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded()) {
            offPose = player.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyYaw);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float age = player.tickCount + partialTick;
        model.attackTime = player.getAttackAnim(partialTick);
        model.riding = player.isPassenger();
        model.young = false;
        model.crouching = player.isCrouching();
        model.prepareMobModel(player, 0.0F, 0.0F, partialTick);
        model.setupAnim(player, 0.0F, 0.0F, age, headYaw, headPitch);
    }

    private static HumanoidModel.ArmPose getArmPose(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0 && stack.is(ModItems.ME_VACUUM.get())) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    private static Vec3 getInterpolatedPosition(Player player, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, player.xOld, player.getX()),
                Mth.lerp(partialTick, player.yOld, player.getY()),
                Mth.lerp(partialTick, player.zOld, player.getZ()));
    }

    private static void applyMeVacuumThirdPersonTransform(PoseStack poseStack, boolean leftHand) {
        poseStack.translate(0.0F, leftHand ? THIRD_PERSON_LEFT_Y : THIRD_PERSON_RIGHT_Y, THIRD_PERSON_Z);
        poseStack.scale(THIRD_PERSON_ITEM_SCALE, THIRD_PERSON_ITEM_SCALE, THIRD_PERSON_ITEM_SCALE);
    }

    private static void renderWindSegment(BreezeModel<Breeze> model, PoseStack poseStack, VertexConsumer consumer,
                                          Vec3 camera, Vec3 position, Quaternionf muzzleAlignment, float age,
                                          int segment) {
        poseStack.pushPose();
        poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        poseStack.mulPose(muzzleAlignment);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * WIND_ROTATION_SPEED + segment * WIND_SEGMENT_ROTATION_OFFSET));
        poseStack.scale(-WIND_SCALES[segment], -WIND_SCALES[segment], WIND_SCALES[segment]);
        poseStack.translate(0.0D, MODEL_VERTICAL_OFFSET, 0.0D);
        BreezeRenderer.enable(model, model.wind()).renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void renderItemWindSegment(BreezeModel<Breeze> model, PoseStack poseStack, VertexConsumer consumer,
                                              float age, int segment) {
        poseStack.pushPose();
        poseStack.translate(0.0D, WIND_DISTANCES[segment], 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * WIND_ROTATION_SPEED + segment * WIND_SEGMENT_ROTATION_OFFSET));
        poseStack.scale(-WIND_SCALES[segment], -WIND_SCALES[segment], WIND_SCALES[segment]);
        poseStack.translate(0.0D, MODEL_VERTICAL_OFFSET, 0.0D);
        BreezeRenderer.enable(model, model.wind()).renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void resetWindModel(BreezeModel<Breeze> model) {
        model.root().getAllParts().forEach(part -> {
            part.resetPose();
            part.visible = true;
        });
    }

    private static BreezeModel<Breeze> getWindModel(Minecraft minecraft) {
        if (windModel == null) {
            windModel = new BreezeModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.BREEZE_WIND));
        }
        return windModel;
    }

    private record MuzzlePose(Vec3 origin, Vec3 axis) {}
}
