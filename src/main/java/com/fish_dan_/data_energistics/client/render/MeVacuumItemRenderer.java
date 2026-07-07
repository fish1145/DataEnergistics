package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.MeVacuumMenuHost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.client.render.tesr.CellLedRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public final class MeVacuumItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ModelResourceLocation ME_VACUUM_MODEL =
            ModelResourceLocation.inventory(Data_Energistics.id("me_vacuum"));
    private static final CellLedTransform[] LED_TRANSFORMS = {
            CellLedTransform.left(7.25F, 4.00F, 6.00F),
            CellLedTransform.left(7.25F, 4.00F, 9.00F),
            CellLedTransform.right(8.75F, 4.00F, 6.00F),
            CellLedTransform.right(8.75F, 4.00F, 9.00F)
    };

    public MeVacuumItemRenderer(BlockEntityRenderDispatcher dispatcher,
                                net.minecraft.client.model.geom.EntityModelSet entityModelSet) {
        super(dispatcher, entityModelSet);
    }

    public static MeVacuumItemRenderer create() {
        Minecraft minecraft = Minecraft.getInstance();
        return new MeVacuumItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(ME_VACUUM_MODEL);
        if (model instanceof MeVacuumBakedModel meVacuumModel) {
            model = meVacuumModel.withoutCustomRenderer();
        }

        renderBakedModel(minecraft.getItemRenderer(), stack, model, poseStack, bufferSource, combinedLight,
                combinedOverlay);
        renderCellLeds(stack, poseStack, bufferSource);
    }

    private static void renderBakedModel(ItemRenderer itemRenderer, ItemStack stack, BakedModel model,
                                         PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
                                         int combinedOverlay) {
        boolean fabulous = true;
        for (BakedModel pass : model.getRenderPasses(stack, fabulous)) {
            for (var renderType : pass.getRenderTypes(stack, fabulous)) {
                var vertexConsumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(pass, stack, combinedLight, combinedOverlay, poseStack, vertexConsumer);
            }
        }
    }

    private static void renderCellLeds(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        HolderLookup.Provider registries = getRegistries();
        if (registries == null) {
            return;
        }

        NonNullList<ItemStack> cells = MeVacuumMenuHost.readStoredCells(stack, registries);
        VacuumLedDrive ledDrive = new VacuumLedDrive(cells);
        var buffer = bufferSource.getBuffer(CellLedRenderer.RENDER_LAYER);

        for (int slot = 0; slot < Math.min(cells.size(), LED_TRANSFORMS.length); slot++) {
            if (cells.get(slot).isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.mulPose(LED_TRANSFORMS[slot].matrix());
            CellLedRenderer.renderLed(ledDrive, slot, buffer, poseStack, 0.0F);
            poseStack.popPose();
        }
    }

    @Nullable
    private static HolderLookup.Provider getRegistries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }

        ClientPacketListener connection = minecraft.getConnection();
        return connection == null ? null : connection.registryAccess();
    }

    private record CellLedTransform(float x, float y, float z, boolean rightSide) {

        private Matrix4f matrix() {
            float tx = (this.x + (this.rightSide ? 2.0F : -2.0F)) / 16.0F;
            float ty = this.y / 16.0F;
            float tz = this.z / 16.0F;
            return new Matrix4f().set(
                    0.0F, 1.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 0.0F,
                    this.rightSide ? -1.0F : 1.0F, 0.0F, 0.0F, 0.0F,
                    tx, ty, tz, 1.0F);
        }

        private static CellLedTransform left(float x, float y, float z) {
            return new CellLedTransform(x, y, z, false);
        }

        private static CellLedTransform right(float x, float y, float z) {
            return new CellLedTransform(x, y, z, true);
        }
    }

    private static final class VacuumLedDrive extends BlockEntity implements IChestOrDrive {

        private final NonNullList<ItemStack> cells;

        private VacuumLedDrive(NonNullList<ItemStack> cells) {
            super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            this.cells = cells;
        }

        @Override
        public int getCellCount() {
            return this.cells.size();
        }

        @Override
        public CellState getCellStatus(int slot) {
            if (slot < 0 || slot >= this.cells.size() || this.cells.get(slot).isEmpty()) {
                return CellState.ABSENT;
            }

            StorageCell cellInventory = StorageCells.getCellInventory(this.cells.get(slot), () -> {});
            return cellInventory == null ? CellState.NOT_EMPTY : cellInventory.getStatus();
        }

        @Override
        public boolean isPowered() {
            return true;
        }

        @Override
        public boolean isCellBlinking(int slot) {
            return false;
        }

        @Override
        public Item getCellItem(int slot) {
            return slot >= 0 && slot < this.cells.size() ? this.cells.get(slot).getItem() : ItemStack.EMPTY.getItem();
        }

        @Override
        public MEStorage getCellInventory(int slot) {
            return slot >= 0 && slot < this.cells.size()
                    ? StorageCells.getCellInventory(this.cells.get(slot), () -> {})
                    : null;
        }

        @Override
        public StorageCell getOriginalCellInventory(int slot) {
            return slot >= 0 && slot < this.cells.size()
                    ? StorageCells.getCellInventory(this.cells.get(slot), () -> {})
                    : null;
        }

        @Override
        public IGridNode getActionableNode() {
            return null;
        }
    }
}
