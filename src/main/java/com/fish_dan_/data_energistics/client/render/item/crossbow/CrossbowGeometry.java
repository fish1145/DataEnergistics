package com.fish_dan_.data_energistics.client.render.item.crossbow;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.mojang.math.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class CrossbowGeometry implements IUnbakedGeometry<CrossbowGeometry> {

    private final List<List<Element>> poses;
    private final List<Element> specialAmmo;

    CrossbowGeometry(List<List<Element>> poses, List<Element> specialAmmo) {
        this.poses = poses;
        this.specialAmmo = specialAmmo;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
                           ItemOverrides overrides) {
        FaceBakery bakery = new FaceBakery();
        List<List<Part>> frames = this.poses.stream()
                .map(pose -> bakeParts(pose, context, spriteGetter, bakery)).toList();
        List<Part> ammo = bakeParts(this.specialAmmo, context, spriteGetter, bakery);
        Matrix4f root = modelState.getRotation().applyOrigin(new Vector3f(0.5F))
                .compose(context.getRootTransform()).getMatrix();
        ResourceLocation renderType = context.getRenderTypeHint();
        var builder = IModelBuilder.of(false, context.useBlockLight(), true, context.getTransforms(),
                ItemOverrides.EMPTY, spriteGetter.apply(context.getMaterial("particle")),
                context.getRenderType(renderType != null ? renderType : ResourceLocation.withDefaultNamespace("translucent")));
        render(frames, ammo, CrossbowAnimation.Pose.stationary(false), false, root).forEach(builder::addUnculledFace);
        return new CrossbowBakedModel(builder.build(), frames, ammo, root, overrides);
    }

    private static List<Part> bakeParts(List<Element> elements, IGeometryBakingContext context,
                                        Function<Material, TextureAtlasSprite> sprites, FaceBakery bakery) {
        return elements.stream().map(element -> {
            List<BakedQuad> faces = new ArrayList<>();
            element.cube.faces.forEach((direction, face) -> faces.add(bakery.bakeQuad(
                    new Vector3f(-8.0F), new Vector3f(8.0F), face,
                    sprites.apply(context.getMaterial(face.texture())), direction,
                    BlockModelRotation.X0_Y0, null, element.cube.shade)));
            return new Part(List.copyOf(faces), element.pose, element.motion, element.deployment);
        }).toList();
    }

    static List<BakedQuad> render(List<List<Part>> frames, List<Part> specialAmmo,
                                  CrossbowAnimation.Pose pose, boolean special, Matrix4f root) {
        List<Part> folded = frames.getFirst();
        List<Part> active = frames.get(pose.stage() + 1);
        List<BakedQuad> quads = new ArrayList<>(320);
        for (int i = 0; i < active.size(); i++) {
            Part from = folded.get(i);
            Part to = active.get(i);
            if ((to.motion == CrossbowMotion.LEFT_STRING || to.motion == CrossbowMotion.RIGHT_STRING) && pose.bowPosition() <= 0.001F && !special) {
                // The string stays in its housing until the bow assembly starts ejecting.
                continue;
            }
            float deployment = to.deployment.progress(pose);
            Matrix4f transform = new Matrix4f(root).mul(from.pose.transformTo(to.pose, to.deployment, to.motion, pose));
            append(quads, deployment == 0.0F ? from.quads : to.quads, transform);
        }
        if (special && pose.railDeployment() == 1.0F) {
            for (Part part : specialAmmo) {
                Matrix4f transform = new Matrix4f(root).translate(part.pose.center()).rotate(part.pose.rotation()).scale(part.pose.size());
                int start = quads.size();
                append(quads, part.quads, transform);
                for (int i = start; i < quads.size(); i++) {
                    QuadTransformers.settingMaxEmissivity().processInPlace(quads.get(i));
                    QuadTransformers.applyingColor(0xFFCC88FF).processInPlace(quads.get(i));
                }
            }
        }
        return List.copyOf(quads);
    }

    private static void append(List<BakedQuad> output, List<BakedQuad> source, Matrix4f matrix) {
        Transformation transformation = new Transformation(matrix);
        var transformer = QuadTransformers.applying(transformation);
        for (BakedQuad quad : source) {
            Vector3f normal = new Vector3f(quad.getDirection().step());
            transformation.transformNormal(normal);
            if (matrix.determinant3x3() < 0.0F) {
                normal.negate();
            }
            BakedQuad transformed = new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(),
                    Direction.getNearest(normal.x, normal.y, normal.z), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion());
            transformer.processInPlace(transformed);
            // Keep the authored inverted hull winding and derive normals from the final vertices.
            ClientHooks.fillNormal(transformed.getVertices(), transformed.getDirection());
            output.add(transformed);
        }
    }

    record Element(BlockElement cube, CrossbowPartPose pose, CrossbowMotion motion, CrossbowDeployment deployment) {}

    record Part(List<BakedQuad> quads, CrossbowPartPose pose, CrossbowMotion motion, CrossbowDeployment deployment) {}
}
