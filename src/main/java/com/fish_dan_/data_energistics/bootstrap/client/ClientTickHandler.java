package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.integration.viewer.xei.XeiLayoutRefreshQueue;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DEMobEffects;
import com.fish_dan_.data_energistics.registry.DEParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import appeng.core.definitions.AEItems;
import appeng.items.misc.PaintBallItem;
import org.joml.Vector3f;

final class ClientTickHandler {

    private ClientTickHandler() {}

    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        XeiLayoutRefreshQueue.drain();
        if (minecraft.isPaused() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        while (ClientInputHandler.consumeToggleDepotBucketModeClick()) {
            ClientInputHandler.toggleDepotBucketMode(minecraft);
        }

        if ((minecraft.player.tickCount & 1) != 0) {
            return;
        }

        spawnRadixLossParticles(minecraft);
        spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.MAIN_HAND);
        spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.OFF_HAND);
    }

    private static void spawnMatterConvergingCrossbowParticles(Minecraft minecraft, InteractionHand hand) {
        var player = minecraft.player;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(DEItems.MATTER_CONVERGING_CROSSBOW.get())) {
            return;
        }

        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (charged.isEmpty()) {
            return;
        }

        ItemStack ammo = charged.getItems().getFirst();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();

        Vec3 base = player.getEyePosition()
                .add(look.scale(0.78D))
                .add(right.scale(0D * getHandSide(player.getMainArm(), hand)))
                .add(up.scale(-0.30D));
        Vec3 velocity = look.scale(0.02D).add(up.scale(0.002D));

        Integer color = getMatterBallParticleColor(ammo);
        if (color == null) {
            return;
        }

        Vector3f rgb = new Vector3f(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F);
        DustParticleOptions particle = new DustParticleOptions(rgb, 0.85F);
        if (ammo.is(AEItems.SINGULARITY.asItem())) {
            Vec3 singularityBase = base.add(up.scale(-0.05D));
            minecraft.level.addParticle(particle,
                    singularityBase.x, singularityBase.y, singularityBase.z,
                    velocity.x, velocity.y, velocity.z);
            minecraft.level.addParticle(ParticleTypes.DRAGON_BREATH,
                    singularityBase.x, singularityBase.y, singularityBase.z,
                    velocity.x * 0.2D, velocity.y * 0.2D, velocity.z * 0.2D);
            return;
        }
        minecraft.level.addParticle(particle, base.x, base.y, base.z, velocity.x, velocity.y, velocity.z);
    }

    private static void spawnRadixLossParticles(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.hasEffect(DEMobEffects.RADIX_LOSS) || livingEntity.isInvisible()) {
                continue;
            }

            double radius = Math.max(0.25D, livingEntity.getBbWidth() * 0.65D);
            double angle = livingEntity.getRandom().nextDouble() * Math.PI * 2.0D;
            double x = livingEntity.getX() + Math.cos(angle) * radius;
            double y = livingEntity.getY() + livingEntity.getRandom().nextDouble() * livingEntity.getBbHeight();
            double z = livingEntity.getZ() + Math.sin(angle) * radius;
            double xSpeed = (livingEntity.getRandom().nextDouble() - 0.5D) * 0.015D;
            double ySpeed = 0.015D + livingEntity.getRandom().nextDouble() * 0.02D;
            double zSpeed = (livingEntity.getRandom().nextDouble() - 0.5D) * 0.015D;
            minecraft.level.addParticle(DEParticles.RADIX_LOSS.get(), x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    private static double getHandSide(HumanoidArm mainArm, InteractionHand hand) {
        boolean isRight = (hand == InteractionHand.MAIN_HAND) == (mainArm == HumanoidArm.RIGHT);
        return isRight ? 1.0D : -1.0D;
    }

    private static Integer getMatterBallParticleColor(ItemStack ammo) {
        Item item = ammo.getItem();
        if (item instanceof PaintBallItem paintBallItem) {
            return paintBallItem.getColor().mediumVariant;
        }
        if (ammo.is(AEItems.SINGULARITY.asItem())) {
            return 0x7A3DFF;
        }
        if (item == AEItems.MATTER_BALL.asItem()) {
            return 0xD8D8D8;
        }
        return null;
    }
}
