package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.item.vacuum.MeVacuumMenuHost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.client.render.tesr.CellLedRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public final class MeVacuumItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ModelResourceLocation ME_VACUUM_MODEL = ModelResourceLocation.inventory(Data_Energistics.id("me_vacuum"));
    private static final CellLedTransform[] LED_TRANSFORMS = {
            CellLedTransform.left(7.25F, 4.00F, 6.00F),
            CellLedTransform.left(7.25F, 4.00F, 9.00F),
            CellLedTransform.right(8.75F, 4.00F, 6.00F),
            CellLedTransform.right(8.75F, 4.00F, 9.00F)
    };

    public MeVacuumItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet entityModelSet) {
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
        if (shouldRenderFirstPersonWind(minecraft, displayContext, stack)) {
            MeVacuumBreezeVisualRenderer.renderFirstPersonItemWind(minecraft, poseStack, bufferSource);
        }
    }

    private static boolean shouldRenderFirstPersonWind(Minecraft minecraft, ItemDisplayContext displayContext,
                                                       ItemStack stack) {
        Player player = minecraft.player;
        return displayContext.firstPerson() && player != null && player.isUsingItem() && !player.isShiftKeyDown() && MeVacuumBreezeVisualRenderer.isWorkingVacuum(stack) && ItemStack.isSameItemSameComponents(player.getUseItem(), stack);
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
        CellState[] states = MeVacuumMenuHost.readStoredCellStates(stack, registries);
        VacuumLedDrive ledDrive = new VacuumLedDrive(cells, states, isPowered(stack));
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

    static CellState getCellLedState(NonNullList<ItemStack> cells, CellState[] states, int slot) {
        if (slot < 0 || slot >= cells.size() || slot >= states.length || cells.get(slot).isEmpty()) {
            return CellState.ABSENT;
        }

        return states[slot];
    }

    static boolean isPowered(ItemStack stack) {
        return stack.getItem() instanceof PoweredEnergyItem poweredItem && poweredItem.getAECurrentPower(stack) > 0.0D;
    }

    private record CellLedTransform(float x, float y, float z, boolean rightSide) {

        private Matrix4f matrix() {
            float tx = (this.x + (this.rightSide ? 2.0F : -2.0F)) / 16.0F;
            float ty = this.y / 16.0F;
            float tz = (this.z + (this.rightSide ? 1.0F : 0.0F)) / 16.0F;
            if (this.rightSide) {
                return new Matrix4f().set(
                        0.0F, 1.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, -1.0F, 0.0F,
                        -1.0F, 0.0F, 0.0F, 0.0F,
                        tx, ty, tz, 1.0F);
            }

            return new Matrix4f().set(
                    0.0F, 1.0F, 0.0F, 0.0F,
                    0.0F, 0.0F, 1.0F, 0.0F,
                    1.0F, 0.0F, 0.0F, 0.0F,
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
        private final CellState[] states;
        private final boolean powered;

        private VacuumLedDrive(NonNullList<ItemStack> cells, CellState[] states, boolean powered) {
            super(BlockEntityType.BARREL, BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
            this.cells = cells;
            this.states = states;
            this.powered = powered;
        }

        @Override
        public int getCellCount() {
            return this.cells.size();
        }

        @Override
        public CellState getCellStatus(int slot) {
            return getCellLedState(this.cells, this.states, slot);
        }

        @Override
        public boolean isPowered() {
            return this.powered;
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
            return null;
        }

        @Override
        public StorageCell getOriginalCellInventory(int slot) {
            return null;
        }

        @Override
        public IGridNode getActionableNode() {
            return null;
        }
    }
}
