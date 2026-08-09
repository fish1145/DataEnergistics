package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataMimeticFieldBlock;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;

public class DataMimeticFieldRenderer implements BlockEntityRenderer<DataMimeticFieldBlockEntity> {

    // Slot cavities in data_mimetic_field_off/on:
    // X centers = 3, 6, 10, 13; Z centers = 5 and 11; cavity bottom Y = 10.
    // Carrier model bounds are x=3..5, y=8..12, z=0..2, so these are the model-origin
    // translations that place the carrier inside each slot.
    private static final float[] SLOT_X = { 0.125F, 0.3125F, 0.5625F, 0.75F };
    private static final float[] SLOT_ROW_Z = { -0.3125F, 0.0625F };
    private static final float SLOT_Y = 0.125F;
    private static final float MODEL_SCALE = 1.0F;
    private static final float MODEL_OFFSET_X = -0.0625F;
    private static final float MODEL_OFFSET_Y = -0.25F;
    private static final int MIN_BLOCK_LIGHT = 13;
    private static final int MIN_SKY_LIGHT = 13;
    private static final ModelResourceLocation MOB_CARRIER_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/mob_data_carrier"));
    private static final ModelResourceLocation ORE_CARRIER_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/ore_data_carrier"));
    private static final ModelResourceLocation CROP_CARRIER_MODEL = ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/crop_data_carrier"));

    public DataMimeticFieldRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull DataMimeticFieldBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(DataMimeticFieldBlock.FACING) ? state.getValue(DataMimeticFieldBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(getYRotation(facing)));
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        var minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        var inventory = blockEntity.getInternalInventory();
        int visibleSlots = Math.min(DataMimeticFieldBlockEntity.SLOT_COUNT, blockEntity.getActiveSlotCount());

        for (int slot = 0; slot < visibleSlots; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            BakedModel model = getCarrierModel(minecraft, stack);
            if (model == null) {
                continue;
            }

            int row = slot < DataMimeticFieldBlockEntity.BASE_ACTIVE_SLOTS ? 0 : 1;
            int col = slot < DataMimeticFieldBlockEntity.BASE_ACTIVE_SLOTS ? slot : slot - DataMimeticFieldBlockEntity.BASE_ACTIVE_SLOTS;
            if (row >= SLOT_ROW_Z.length || col >= SLOT_X.length) {
                break;
            }

            poseStack.pushPose();
            poseStack.translate(SLOT_X[col], SLOT_Y, SLOT_ROW_Z[row]);
            poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            poseStack.translate(MODEL_OFFSET_X, MODEL_OFFSET_Y, 0.0F);
            int renderLight = getCarrierRenderLight(packedLight);
            for (RenderType renderType : model.getRenderTypes(stack, false)) {
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(model, stack, renderLight, packedOverlay, poseStack, consumer);
            }
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static BakedModel getCarrierModel(Minecraft minecraft, ItemStack stack) {
        if (stack.is(DEItems.MOB_DATA_CARRIER.get())) {
            return minecraft.getModelManager().getModel(MOB_CARRIER_MODEL);
        }
        if (stack.is(DEItems.ORE_DATA_CARRIER.get())) {
            return minecraft.getModelManager().getModel(ORE_CARRIER_MODEL);
        }
        if (stack.is(DEItems.CROP_DATA_CARRIER.get())) {
            return minecraft.getModelManager().getModel(CROP_CARRIER_MODEL);
        }
        return null;
    }

    private static int getCarrierRenderLight(int packedLight) {
        int blockLight = Math.max(LightTexture.block(packedLight), MIN_BLOCK_LIGHT);
        int skyLight = Math.max(LightTexture.sky(packedLight), MIN_SKY_LIGHT);
        return LightTexture.pack(blockLight, skyLight);
    }

    private static float getYRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }
}
