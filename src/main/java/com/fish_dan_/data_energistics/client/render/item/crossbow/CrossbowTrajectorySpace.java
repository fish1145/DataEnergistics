package com.fish_dan_.data_energistics.client.render.item.crossbow;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Per-render mapping between the actual item pose and camera-relative world trajectory points. */
public final class CrossbowTrajectorySpace {

    private final Matrix4f modelToWorldView;
    private final Matrix4f worldViewToRender;
    private final Quaternionf cameraRotation;
    private final Quaternionf inverseCameraRotation;

    public CrossbowTrajectorySpace(Matrix4f modelToRender, Matrix4f modelView,
                                   Matrix4f renderProjection, Matrix4f worldProjection, Quaternionf cameraRotation) {
        Matrix4f renderToClip = new Matrix4f(renderProjection).mul(modelView);
        this.modelToWorldView = new Matrix4f(worldProjection).invert().mul(renderToClip).mul(modelToRender);
        this.worldViewToRender = renderToClip.invert().mul(worldProjection);
        this.cameraRotation = new Quaternionf(cameraRotation);
        this.inverseCameraRotation = new Quaternionf(cameraRotation).conjugate();
    }

    /** Center of the folded barrel opening: x=7..9, y=6..8, front face z=4 (model pixels). */
    public Vector3f muzzleOffset() {
        return this.modelToWorldView.transformProject(new Vector3f(8.0F, 7.0F, 4.0F).div(16.0F))
                .rotate(this.cameraRotation);
    }

    public Vector3f modelPoint(Vector3f point) {
        return this.modelToWorldView.transformProject(new Vector3f(point)).rotate(this.cameraRotation);
    }

    /** The authored firing axis is -Z; use two projected points to account for hand/world FOV differences. */
    public Vector3f firingDirection() {
        return this.modelToWorldView.transformProject(new Vector3f(8.0F, 7.0F, -12.0F).div(16.0F))
                .rotate(this.cameraRotation).sub(muzzleOffset()).normalize();
    }

    /** Accepts a world offset from the camera, never a large absolute world coordinate. */
    public Vector3f renderPosition(Vector3f cameraRelativeWorldPosition) {
        return this.worldViewToRender.transformProject(new Vector3f(cameraRelativeWorldPosition).rotate(this.inverseCameraRotation));
    }
}
