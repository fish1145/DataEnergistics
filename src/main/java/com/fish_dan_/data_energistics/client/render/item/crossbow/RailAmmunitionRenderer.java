package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.client.render.item.crossbow.plasma.RailPlasmaRenderer;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailLauncher;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The same physical item or AE resource icon is drawn in the rail chamber and on its fired entity. */
public final class RailAmmunitionRenderer {

    private RailAmmunitionRenderer() {}

    public static void chamber(LivingEntity owner, ItemStack weapon, CrossbowAnimation.Pose pose,
                               PoseStack matrix, Matrix4f root, Vector3f chamber) {
        if (pose.mode() != MatterConvergingCrossbowMode.RAIL || pose.railDeployment() <= 0 || RailLauncher.cooling(weapon, owner.level().getGameTime())) return;
        ItemStack ammo = MountedAmmoCells.peek(weapon, MatterConvergingCrossbowMode.RAIL);
        if (ammo.isEmpty()) return;
        matrix.pushPose();
        matrix.translate(-0.5, -0.5, -0.5);
        matrix.mulPose(root);
        matrix.translate(chamber.x, chamber.y, chamber.z);
        draw(ammo, owner.level(), matrix, Minecraft.getInstance().renderBuffers().bufferSource(), LevelRenderer.getLightColor(owner.level(), owner.blockPosition()));
        matrix.popPose();
    }

    /** Local firing direction is -Z. Callers supply the model/entity world transform. */
    public static void draw(ItemStack ammo, Level level, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        var generic = GenericStack.unwrapItemStack(ammo);
        var kind = generic == null ? null : RailAmmunition.fromKey(generic.what());
        if (kind == RailAmmunition.DATA || kind == RailAmmunition.FE) {
            double time = level.getGameTime() % 8192 + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            RailPlasmaRenderer.draw(kind, time, pose, buffers);
        } else if (generic != null) {
            // Crossed, double-sided resource faces preserve the selected AE icon in all hand/world views.
            for (int side = 0; side < 4; side++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(90 * side));
                AEKeyRendering.drawOnBlockFace(pose, buffers, generic.what(), 0.30F, LightTexture.FULL_BRIGHT, level);
                pose.popPose();
            }
        } else {
            pose.scale(0.25F, 0.25F, 0.25F);
            if (ammo.is(Items.BLAZE_ROD)) {
                // Roll around the firing axis so the broad faces point through the side openings.
                pose.mulPose(Axis.ZP.rotationDegrees(90));
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees(45));
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(ammo, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, level, 0);
        }
        pose.popPose();
    }
}
