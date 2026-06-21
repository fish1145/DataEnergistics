package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.client.DataMeteoriteCompassClientCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import appeng.client.render.model.MeteoriteCompassBakedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

public class DataMeteoriteCompassBakedModel extends BakedModelWrapper<MeteoriteCompassBakedModel> {

    private static final double TARGET_REACHED_DISTANCE_SQ = 6.0D * 6.0D;
    private static final long TARGET_REACHED_SPIN_MS = 500L;
    private static final long IDLE_SPIN_MS = 3000L;

    public DataMeteoriteCompassBakedModel(MeteoriteCompassBakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ItemOverrides getOverrides() {
        return new ItemOverrides() {

            @Override
            public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel level,
                                      @Nullable LivingEntity entity, int seed) {
                boolean requestServerTarget = level != null && entity instanceof Player player && isCarriedBy(player, stack);
                float rotation = level != null && entity != null && requestServerTarget ? getAnimatedRotation(entity.position(), true, 0.0F) : getAnimatedRotation(null, false, 0.0F);
                return new FixedRotationModel(rotation);
            }
        };
    }

    private static boolean isCarriedBy(Player player, ItemStack renderedStack) {
        if (ItemStack.isSameItemSameComponents(player.getMainHandItem(), renderedStack) || ItemStack.isSameItemSameComponents(player.getOffhandItem(), renderedStack)) {
            return true;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(player.getInventory().getItem(slot), renderedStack)) {
                return true;
            }
        }
        return false;
    }

    public static float getAnimatedRotation(@Nullable Vec3 pos, boolean prefetch, float playerRotation) {
        if (pos != null) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.containing(pos));
            BlockPos closestMeteorite = DataMeteoriteCompassClientCache.getClosestMeteorite(chunkPos, prefetch);
            if (closestMeteorite == null) {
                return getSpinningRotation(IDLE_SPIN_MS);
            }

            double dx = pos.x() - closestMeteorite.getX();
            double dz = pos.z() - closestMeteorite.getZ();
            double horizontalDistanceSq = dx * dx + dz * dz;
            if (horizontalDistanceSq > TARGET_REACHED_DISTANCE_SQ) {
                return (float) rad(pos.x(), pos.z(), closestMeteorite.getX(), closestMeteorite.getZ()) + playerRotation;
            }

            return getSpinningRotation(TARGET_REACHED_SPIN_MS);
        }

        return getSpinningRotation(IDLE_SPIN_MS);
    }

    private static float getSpinningRotation(long cycleMillis) {
        long timeMillis = System.currentTimeMillis() % cycleMillis;
        return timeMillis / (float) cycleMillis * (float) Math.PI * 2.0F;
    }

    private static double rad(double ax, double az, double bx, double bz) {
        double up = bz - az;
        double side = bx - ax;
        return Math.atan2(-up, side) - Math.PI / 2.0D;
    }

    private class FixedRotationModel extends BakedModelWrapper<DataMeteoriteCompassBakedModel> {

        private final float rotation;

        private FixedRotationModel(float rotation) {
            super(DataMeteoriteCompassBakedModel.this);
            this.rotation = rotation;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state,
                                        @Nullable Direction side,
                                        RandomSource rand) {
            ModelData modelData = ModelData.builder()
                    .with(MeteoriteCompassBakedModel.ROTATION, this.rotation)
                    .build();
            return DataMeteoriteCompassBakedModel.this.originalModel.getQuads(state, side, rand, modelData, null);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext cameraTransformType, PoseStack poseStack,
                                         boolean applyLeftHandTransform) {
            super.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);

            Vector3f pointerNormal = poseStack.last().transformNormal(0.0F, 0.0F, 1.0F, new Vector3f());
            pointerNormal.y = 0.0F;
            pointerNormal.normalize();

            double cameraRotation = Mth.atan2(pointerNormal.z, pointerNormal.x) - Mth.atan2(1.0D, 0.0D);
            if (cameraTransformType == ItemDisplayContext.GUI && Minecraft.getInstance().player != null) {
                float playerRotation = (float) (Minecraft.getInstance().player.getYRot() / 180.0F * Math.PI + Math.PI);
                cameraRotation += playerRotation;
            }

            return new FixedRotationModel((float) cameraRotation + this.rotation);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
            return List.of(this);
        }
    }
}
