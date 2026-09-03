package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Bounds;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Screen-pixel edge coverage without widening dense world-space route lanes. Render-thread scoped. */
final class CraftingPlanGraphStrokes {

    private static final int QUADS_PER_BATCH = 2048;
    private final GuiGraphics graphics;
    private final Matrix4f pose;
    private final double feather;
    private final @Nullable Bounds viewport;
    private @Nullable VertexConsumer vertices;
    private int quads;

    CraftingPlanGraphStrokes(GuiGraphics graphics, float pixelScale, @Nullable Bounds viewport) {
        this.graphics = graphics;
        this.pose = graphics.pose().last().pose();
        this.feather = 0.5 / pixelScale;
        this.viewport = viewport;
    }

    void line(double ax, double ay, double bx, double by, double width, int color) {
        double dx = bx - ax;
        double dy = by - ay;
        double length = Math.hypot(dx, dy);
        if (length == 0) return;
        double half = width / 2;
        double outer = half + this.feather;
        // Clip before tessellation: distant coordinates and offscreen route lengths never enter the buffer.
        if (this.viewport != null) {
            double minX = this.viewport.x() - outer;
            double maxX = this.viewport.x() + this.viewport.width() + outer;
            double minY = this.viewport.y() - outer;
            double maxY = this.viewport.y() + this.viewport.height() + outer;
            double start = 0;
            double end = 1;
            if (dx == 0) {
                if (ax < minX || ax > maxX) return;
            } else {
                double first = (minX - ax) / dx;
                double last = (maxX - ax) / dx;
                start = Math.max(start, Math.min(first, last));
                end = Math.min(end, Math.max(first, last));
            }
            if (dy == 0) {
                if (ay < minY || ay > maxY) return;
            } else {
                double first = (minY - ay) / dy;
                double last = (maxY - ay) / dy;
                start = Math.max(start, Math.min(first, last));
                end = Math.min(end, Math.max(first, last));
            }
            if (start >= end) return;
            bx = ax + dx * end;
            by = ay + dy * end;
            ax += dx * start;
            ay += dy * start;
        }
        double nx = dy / length;
        double ny = -dx / length;
        // Pixel-box coverage retains a flat center even below one pixel; clamping it to zero dims twice.
        double inner = Math.abs(half - this.feather);
        // A subpixel line has proportionally less coverage, not a many-lanes-wide opaque stroke.
        int covered = (int) Math.round((color >>> 24) * Math.min(1, half / this.feather)) << 24 | color & 0xFFFFFF;
        int transparent = color & 0xFFFFFF;
        if (inner > 0) strip(ax, ay, bx, by, nx, ny, -inner, inner, covered, covered);
        strip(ax, ay, bx, by, nx, ny, -outer, -inner, transparent, covered);
        strip(ax, ay, bx, by, nx, ny, inner, outer, covered, transparent);
    }

    void arrow(double fromX, double fromY, double tipX, double tipY, double size, double width, int color) {
        double length = Math.hypot(tipX - fromX, tipY - fromY);
        if (length == 0) return;
        double dx = (tipX - fromX) / length;
        double dy = (tipY - fromY) / length;
        double depth = Math.min(size, length);
        double x = tipX - dx * depth;
        double y = tipY - dy * depth;
        line(x + dy * depth * 0.55, y - dx * depth * 0.55, tipX, tipY, width, color);
        line(x - dy * depth * 0.55, y + dx * depth * 0.55, tipX, tipY, width, color);
    }

    void fill(double left, double top, double right, double bottom, int color) {
        VertexConsumer buffer = nextQuad();
        buffer.addVertex(this.pose, (float) left, (float) top, 0).setColor(color);
        buffer.addVertex(this.pose, (float) left, (float) bottom, 0).setColor(color);
        buffer.addVertex(this.pose, (float) right, (float) bottom, 0).setColor(color);
        buffer.addVertex(this.pose, (float) right, (float) top, 0).setColor(color);
    }

    private void strip(double ax, double ay, double bx, double by, double nx, double ny,
                       double first, double last, int firstColor, int lastColor) {
        VertexConsumer buffer = nextQuad();
        buffer.addVertex(this.pose, (float) (ax + nx * last), (float) (ay + ny * last), 0).setColor(lastColor);
        buffer.addVertex(this.pose, (float) (ax + nx * first), (float) (ay + ny * first), 0).setColor(firstColor);
        buffer.addVertex(this.pose, (float) (bx + nx * first), (float) (by + ny * first), 0).setColor(firstColor);
        buffer.addVertex(this.pose, (float) (bx + nx * last), (float) (by + ny * last), 0).setColor(lastColor);
    }

    private VertexConsumer nextQuad() {
        if (this.quads == QUADS_PER_BATCH) flush();
        if (this.vertices == null) this.vertices = this.graphics.bufferSource().getBuffer(RenderType.gui());
        this.quads++;
        return this.vertices;
    }

    void flush() {
        this.graphics.bufferSource().endBatch(RenderType.gui());
        this.vertices = null;
        this.quads = 0;
    }
}
