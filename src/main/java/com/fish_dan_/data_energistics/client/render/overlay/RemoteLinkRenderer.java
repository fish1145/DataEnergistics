package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.client.render.overlay.connector.ConnectorLinkGeometry;
import com.fish_dan_.data_energistics.item.connector.ConnectorHostType;
import com.fish_dan_.data_energistics.item.connector.RemoteLinkConnectorData;
import com.fish_dan_.data_energistics.item.connector.RemoteLinkConnectorItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.List;
import java.util.OptionalDouble;

/** Renders client-synchronized bindings while their connector is held in either hand. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class RemoteLinkRenderer {

    private static final Color INPUT_CURRENT = new Color(0.2F, 0.85F, 1.0F, 1.0F);
    private static final Color INPUT_OTHER = new Color(0.18F, 0.62F, 0.95F, 0.95F);
    private static final Color PULL_CURRENT = new Color(1.0F, 0.9F, 0.2F, 1.0F);
    private static final Color PULL_OTHER = new Color(0.9F, 0.78F, 0.16F, 0.95F);
    private static final Color BOTH_CURRENT = new Color(0.85F, 0.35F, 1.0F, 1.0F);
    private static final Color BOTH_OTHER = new Color(0.68F, 0.32F, 0.92F, 0.95F);
    private static final Color SOURCE = new Color(0.85F, 0.85F, 0.85F, 0.8F);
    private static final Color MISSING = new Color(1.0F, 0.2F, 0.2F, 0.85F);
    private static final Color UNLOADED = new Color(0.6F, 0.6F, 0.6F, 0.7F);
    private static final RenderType LINK_LINES = RenderType.create(
            "data_energistics_connector_links", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            1536, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0D)))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private RemoteLinkRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        ItemStack stack = RemoteLinkConnectorItem.isConnectorStack(minecraft.player.getMainHandItem()) ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
        if (!RemoteLinkConnectorItem.isConnectorStack(stack)) {
            return;
        }
        RemoteLinkConnectorData data = RemoteLinkConnectorItem.readData(stack);
        if (!data.hasSelection()) {
            return;
        }

        if (data.providerSide() != -1 || !(data.targetType() == ConnectorHostType.TOWER || data.isAdaptiveProvider() || data.isInterface()) || !level.dimension().location().toString().equals(data.targetType() == ConnectorHostType.TOWER ? data.dimensionId() : data.providerDimensionId())) {
            return;
        }

        BlockPos provider = data.targetType() == ConnectorHostType.TOWER ? data.getTowerPos() : data.getProviderPos();
        ConnectorEndpoint endpoint = RemoteLinkConnectorItem.resolveEndpoint(level, data);
        Vec3 source = new Vec3(0.5D, 0.5D, 0.5D);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(LINK_LINES);
        pose.pushPose();
        try {
            pose.translate(provider.getX() - camera.x, provider.getY() - camera.y, provider.getZ() - camera.z);
            List<ConnectorLink> targets = endpoint != null ? endpoint.bindingsFast() : List.of();
            int selected = targets.isEmpty() ? -1 : Math.floorMod(data.selectedBindingIndex(), targets.size());
            Color sourceColor = endpoint == null ? level.isLoaded(provider) ? MISSING : UNLOADED : selected >= 0 ? currentColor(targets.get(selected).mode(), true) : SOURCE;
            LevelRenderer.renderLineBox(pose, lines, new AABB(source, source).inflate(0.15D),
                    sourceColor.red(), sourceColor.green(), sourceColor.blue(), sourceColor.alpha());
            if (endpoint == null) {
                return;
            }
            for (int index = 0; index < targets.size(); index++) {
                var target = targets.get(index);
                boolean selectedLink = data.allLinksSelected() || index == selected;
                Color color = !level.isLoaded(target.position()) ? UNLOADED : level.getBlockState(target.position()).isAir() ? MISSING : currentColor(target.mode(), selectedLink);
                // Client capabilities may legitimately be absent for server-only inventories. The synchronized
                // binding is authoritative; only loaded world geometry determines a missing marker here.
                var face = ConnectorLinkGeometry.face(target.position().subtract(provider), target.side());
                line(pose, lines, source, face.approach(), color);
                line(pose, lines, face.approach(), face.center(), color);
                for (int corner = 0; corner < face.corners().size(); corner++) {
                    line(pose, lines, face.corners().get(corner), face.corners().get((corner + 1) % 4), color);
                }
                if (selectedLink) {
                    line(pose, lines, face.corners().get(0), face.corners().get(2), color);
                    line(pose, lines, face.corners().get(1), face.corners().get(3), color);
                }
            }
        } finally {
            buffers.endBatch(LINK_LINES);
            pose.popPose();
        }
    }

    private static void line(PoseStack pose, VertexConsumer vertices, Vec3 from, Vec3 to, Color color) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-10D) {
            return;
        }
        Vec3 normal = direction.normalize();
        var transform = pose.last();
        vertices.addVertex(transform.pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(color.red(), color.green(), color.blue(), color.alpha())
                .setNormal(transform, (float) normal.x, (float) normal.y, (float) normal.z);
        vertices.addVertex(transform.pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(color.red(), color.green(), color.blue(), color.alpha())
                .setNormal(transform, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Color currentColor(ConnectorMode mode, boolean selected) {
        if (mode == ConnectorMode.INPUT) {
            return selected ? INPUT_CURRENT : INPUT_OTHER;
        }
        if (mode == ConnectorMode.BOTH) {
            return selected ? BOTH_CURRENT : BOTH_OTHER;
        }
        return selected ? PULL_CURRENT : PULL_OTHER;
    }

    private record Color(float red, float green, float blue, float alpha) {}
}
