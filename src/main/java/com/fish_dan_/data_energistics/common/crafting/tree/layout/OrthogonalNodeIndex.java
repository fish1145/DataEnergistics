package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.PlacedNode;
import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Static rectangle BVH for point visibility and nearest axis-aligned obstruction queries. */
final class OrthogonalNodeIndex {

    private static final double EPSILON = OrthogonalSegmentReservations.EPSILON;
    private final Box[] boxes;
    private final Branch root;

    OrthogonalNodeIndex(List<PlacedNode> nodes, double clearance) {
        boxes = new Box[nodes.size()];
        int[] order = new int[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            PlacedNode node = nodes.get(index);
            boxes[index] = new Box(node.id(), clean(node.x() - clearance), clean(node.y() - clearance),
                    clean(node.x() + node.width() + clearance), clean(node.y() + node.height() + clearance));
            order[index] = index;
        }
        root = build(order, 0, order.length);
    }

    boolean contains(Point point) {
        return contains(root, point.x(), point.y());
    }

    @Nullable
    Hit firstHit(Point from, Point to, int allowedNode) {
        Ray ray = new Ray(from, to, allowedNode);
        visit(root, ray);
        if (ray.box < 0) return null;
        double direction = Math.signum(ray.horizontal ? to.x() - from.x() : to.y() - from.y());
        Point point = ray.horizontal ? new Point(clean(from.x() + direction * ray.distance), from.y()) : new Point(from.x(), clean(from.y() + direction * ray.distance));
        return new Hit(point, boxes[ray.box]);
    }

    private Branch build(int[] order, int first, int end) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int index = first; index < end; index++) {
            Box box = boxes[order[index]];
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
        }
        if (end - first == 1) return new Branch(minX, minY, maxX, maxY, order[first], null, null);
        boolean alongX = maxX - minX >= maxY - minY;
        IntArrays.quickSort(order, first, end, (a, b) -> {
            Box left = boxes[a];
            Box right = boxes[b];
            int comparison = Double.compare(alongX ? left.minX() + left.maxX() : left.minY() + left.maxY(),
                    alongX ? right.minX() + right.maxX() : right.minY() + right.maxY());
            return comparison != 0 ? comparison : Integer.compare(left.id(), right.id());
        });
        int middle = (first + end) >>> 1;
        return new Branch(minX, minY, maxX, maxY, -1, build(order, first, middle), build(order, middle, end));
    }

    private boolean contains(@Nullable Branch branch, double x, double y) {
        if (branch == null || x <= branch.minX + EPSILON || x >= branch.maxX - EPSILON || y <= branch.minY + EPSILON || y >= branch.maxY - EPSILON) return false;
        return branch.box >= 0 || contains(branch.left, x, y) || contains(branch.right, x, y);
    }

    private void visit(@Nullable Branch branch, Ray ray) {
        if (branch == null) return;
        double entry = entry(branch, ray);
        if (entry > ray.distance) return;
        if (branch.box >= 0) {
            if (boxes[branch.box].id() != ray.allowedNode && entry < ray.distance) {
                ray.distance = entry;
                ray.box = branch.box;
            }
            return;
        }
        double left = branch.left == null ? Double.POSITIVE_INFINITY : entry(branch.left, ray);
        double right = branch.right == null ? Double.POSITIVE_INFINITY : entry(branch.right, ray);
        if (left <= right) {
            visit(branch.left, ray);
            visit(branch.right, ray);
        } else {
            visit(branch.right, ray);
            visit(branch.left, ray);
        }
    }

    private static double entry(Branch branch, Ray ray) {
        double fixed = ray.horizontal ? ray.from.y() : ray.from.x();
        double low = ray.horizontal ? branch.minY : branch.minX;
        double high = ray.horizontal ? branch.maxY : branch.maxX;
        if (fixed <= low + EPSILON || fixed >= high - EPSILON) return Double.POSITIVE_INFINITY;
        double from = ray.horizontal ? ray.from.x() : ray.from.y();
        double to = ray.horizontal ? ray.to.x() : ray.to.y();
        low = ray.horizontal ? branch.minX : branch.minY;
        high = ray.horizontal ? branch.maxX : branch.maxY;
        if (Math.min(from, to) >= high - EPSILON || Math.max(from, to) <= low + EPSILON) return Double.POSITIVE_INFINITY;
        return Math.max(0, to > from ? low - from : from - high);
    }

    private static double clean(double coordinate) {
        return OrthogonalRoutingAxis.normalize(coordinate);
    }

    record Box(int id, double minX, double minY, double maxX, double maxY) {}

    record Hit(Point point, Box box) {}

    private record Branch(double minX, double minY, double maxX, double maxY, int box,
                          @Nullable Branch left, @Nullable Branch right) {}

    private static final class Ray {

        private final Point from;
        private final Point to;
        private final boolean horizontal;
        private final int allowedNode;
        private double distance;
        private int box = -1;

        private Ray(Point from, Point to, int allowedNode) {
            this.from = from;
            this.to = to;
            this.horizontal = from.y() == to.y();
            this.allowedNode = allowedNode;
            this.distance = Math.abs(to.x() - from.x()) + Math.abs(to.y() - from.y()) + EPSILON;
        }
    }
}
