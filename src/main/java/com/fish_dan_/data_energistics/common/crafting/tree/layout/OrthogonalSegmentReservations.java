package com.fish_dan_.data_energistics.common.crafting.tree.layout;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanGraphLayout.Point;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** Finite directed occupancy with reversible references and geometry-only crossing queries. */
final class OrthogonalSegmentReservations {

    static final double EPSILON = 0.000001;

    private final OrthogonalRoutingAxis x;
    private final OrthogonalRoutingAxis y;
    private final Orientation horizontal;
    private final Orientation vertical;
    private final Long2IntOpenHashMap terminals = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<@Nullable Object2ObjectOpenHashMap<CraftingPlanRouteGroup, @Nullable Turns>> turns = new Long2ObjectOpenHashMap<>();
    private final IntSet coordinateScratch = new IntOpenHashSet();
    private final LongSet intersectionScratch = new LongOpenHashSet();

    OrthogonalSegmentReservations(OrthogonalRoutingAxis x, OrthogonalRoutingAxis y) {
        this.x = x;
        this.y = y;
        horizontal = new Orientation(x);
        vertical = new Orientation(y);
    }

    record Metrics(double length, int crossings, int bends, double sharedLength) {}

    @Nullable
    Metrics measure(List<Point> points, CraftingPlanRouteGroup group) {
        List<Leg> legs = legs(points);
        if (selfOverlaps(legs)) return null;
        double length = 0;
        double shared = 0;
        int bends = 0;
        intersectionScratch.clear();
        for (int index = 0; index < legs.size(); index++) {
            Leg leg = legs.get(index);
            double covered = orientation(leg).shared(leg, new Owner(group, leg.direction()));
            if (covered < 0) return null;
            length += length(leg);
            shared += covered;
            if (index > 0 && legs.get(index - 1).code() != leg.code()) bends++;
            Orientation perpendicular = leg.alongX() ? vertical : horizontal;
            coordinateScratch.clear();
            perpendicular.closed.collect(leg.fixed(), leg.low(), leg.high() + 1, coordinateScratch);
            for (int coordinate : coordinateScratch) {
                long point = leg.point(coordinate);
                boolean endpoint = coordinate == leg.low() || coordinate == leg.high();
                if (hasTurn(point, group, leg.code())) continue;
                if (endpoint) {
                    boolean terminal = point == legs.getFirst().from() || point == legs.getLast().to();
                    if (terminal && terminals.containsKey(point)) continue;
                    if (sharesTurn(legs, index, point, group)) continue;
                    // Two finite ends touching is not a transverse crossing.
                    if (!perpendicular.interior.contains(leg.fixed(), coordinate)) continue;
                }
                intersectionScratch.add(point);
            }
        }
        return new Metrics(length, intersectionScratch.size(), bends, shared);
    }

    private static boolean selfOverlaps(List<Leg> legs) {
        for (int first = 0; first < legs.size(); first++) {
            Leg left = legs.get(first);
            for (int second = first + 1; second < legs.size(); second++) {
                Leg right = legs.get(second);
                if (left.alongX() == right.alongX() && left.fixed() == right.fixed() && Math.max(left.low(), right.low()) < Math.min(left.high(), right.high())) return true;
            }
        }
        return false;
    }

    boolean available(Point from, Point to, CraftingPlanRouteGroup group) {
        Leg leg = leg(from, to);
        return leg.low() == leg.high() || orientation(leg).shared(leg, new Owner(group, leg.direction())) >= 0;
    }

    void reserve(List<Point> points, CraftingPlanRouteGroup group) {
        change(legs(points), group, 1);
    }

    void release(List<Point> points, CraftingPlanRouteGroup group) {
        change(legs(points), group, -1);
    }

    private void change(List<Leg> legs, CraftingPlanRouteGroup group, int delta) {
        // The chosen candidate has already been measured; coverage mutation enforces reference balance once.
        for (Leg leg : legs) orientation(leg).change(leg, new Owner(group, leg.direction()), delta);
        if (legs.isEmpty()) return;
        terminal(legs.getFirst().from(), delta);
        terminal(legs.getLast().to(), delta);
        for (int index = 1; index < legs.size(); index++) {
            Leg before = legs.get(index - 1);
            Leg after = legs.get(index);
            if (before.alongX() == after.alongX()) continue;
            long point = before.to();
            var groups = turns.get(point);
            if (groups == null) {
                if (delta < 0) throw new IllegalStateException("Missing released route turn");
                groups = new Object2ObjectOpenHashMap<>();
                turns.put(point, groups);
            }
            Turns counts = groups.get(group);
            if (counts == null) {
                if (delta < 0) throw new IllegalStateException("Missing released route turn owner");
                counts = new Turns();
                groups.put(group, counts);
            }
            counts.change(before.code(), after.code(), delta);
            if (counts.total == 0) groups.remove(group);
            if (groups.isEmpty()) turns.remove(point);
        }
    }

    private void terminal(long point, int delta) {
        int count = terminals.get(point) + delta;
        if (count < 0) throw new IllegalStateException("Missing released route terminal");
        if (count == 0) terminals.remove(point);
        else terminals.put(point, count);
    }

    private boolean hasTurn(long point, CraftingPlanRouteGroup group, int direction) {
        var groups = turns.get(point);
        Turns counts = groups == null ? null : groups.get(group);
        return counts != null && counts.directions[direction] > 0;
    }

    private boolean sharesTurn(List<Leg> legs, int index, long point, CraftingPlanRouteGroup group) {
        Leg current = legs.get(index);
        if (index > 0) {
            Leg previous = legs.get(index - 1);
            if (previous.alongX() != current.alongX() && previous.to() == point && sharedEnd(previous, point, group)) return true;
        }
        if (index + 1 < legs.size()) {
            Leg next = legs.get(index + 1);
            return next.alongX() != current.alongX() && next.from() == point && sharedEnd(next, point, group);
        }
        return false;
    }

    private boolean sharedEnd(Leg leg, long point, CraftingPlanRouteGroup group) {
        int coordinate = leg.alongX() ? OrthogonalRoutingAxis.x(point) : OrthogonalRoutingAxis.y(point);
        int low = coordinate == leg.low() ? leg.low() : leg.high() - 1;
        return orientation(leg).shared(new Leg(leg.alongX(), leg.fixed(), low, low + 1, leg.direction()),
                new Owner(group, leg.direction())) > 0;
    }

    private List<Leg> legs(List<Point> points) {
        List<Leg> result = new ObjectArrayList<>(Math.max(0, points.size() - 1));
        for (int index = 1; index < points.size(); index++) {
            Leg leg = leg(points.get(index - 1), points.get(index));
            if (leg.low() == leg.high()) continue;
            if (!result.isEmpty()) {
                Leg previous = result.getLast();
                if (previous.alongX() == leg.alongX() && previous.fixed() == leg.fixed() && previous.direction() == leg.direction()) {
                    result.set(result.size() - 1, new Leg(leg.alongX(), leg.fixed(), Math.min(previous.low(), leg.low()),
                            Math.max(previous.high(), leg.high()), leg.direction()));
                    continue;
                }
            }
            result.add(leg);
        }
        return result;
    }

    private Leg leg(Point from, Point to) {
        int fromX = x.index(from.x());
        int toX = x.index(to.x());
        int fromY = y.index(from.y());
        int toY = y.index(to.y());
        if (fromX != toX && fromY != toY) throw new IllegalArgumentException("Route segment must be orthogonal");
        boolean alongX = fromY == toY;
        int start = alongX ? fromX : fromY;
        int end = alongX ? toX : toY;
        return new Leg(alongX, alongX ? fromY : fromX, Math.min(start, end), Math.max(start, end), Integer.compare(end, start));
    }

    private Orientation orientation(Leg leg) {
        return leg.alongX() ? horizontal : vertical;
    }

    private double length(Leg leg) {
        OrthogonalRoutingAxis axis = leg.alongX() ? x : y;
        return axis.value(leg.high()) - axis.value(leg.low());
    }

    private record Owner(CraftingPlanRouteGroup group, int direction) {}

    private record Leg(boolean alongX, int fixed, int low, int high, int direction) {

        private int code() {
            return (alongX ? 0 : 2) + (direction > 0 ? 0 : 1);
        }

        private long point(int coordinate) {
            return alongX ? OrthogonalRoutingAxis.point(coordinate, fixed) : OrthogonalRoutingAxis.point(fixed, coordinate);
        }

        private long from() {
            return point(direction > 0 ? low : high);
        }

        private long to() {
            return point(direction > 0 ? high : low);
        }
    }

    private static final class Turns {

        private final int[] directions = new int[4];
        private int total;

        private void change(int before, int after, int delta) {
            if (total + delta < 0 || directions[before] + delta < 0 || directions[after] + delta < 0) {
                throw new IllegalStateException("Unbalanced route turn reference");
            }
            total += delta;
            directions[before] += delta;
            directions[after] += delta;
        }
    }

    private static final class Orientation {

        private final OrthogonalRoutingAxis axis;
        private final Int2ObjectOpenHashMap<@Nullable Cover> lines = new Int2ObjectOpenHashMap<>();
        private final Stabbing closed;
        private final Stabbing interior;

        private Orientation(OrthogonalRoutingAxis axis) {
            this.axis = axis;
            closed = new Stabbing(axis.size());
            interior = new Stabbing(axis.size());
        }

        private double shared(Leg leg, Owner owner) {
            Cover cover = lines.get(leg.fixed());
            return cover == null ? 0 : cover.shared(leg.low(), leg.high(), owner, axis);
        }

        private void change(Leg leg, Owner owner, int delta) {
            Cover cover = lines.get(leg.fixed());
            if (cover == null) {
                if (delta < 0) throw new IllegalStateException("Missing released line coverage");
                cover = new Cover(0, axis.size() - 1, axis);
                lines.put(leg.fixed(), cover);
            }
            cover.change(leg.low(), leg.high(), owner, delta, axis);
            if (cover.maximum == 0) lines.remove(leg.fixed());
            closed.change(leg.low(), leg.high() + 1, leg.fixed(), delta);
            interior.change(leg.low() + 1, leg.high(), leg.fixed(), delta);
        }
    }

    /** Lazy range-add counts; minimumLength tracks the zero-covered union without expanding shared members. */
    private static final class Cover {

        private final int low;
        private final int high;
        private int minimum;
        private int maximum;
        private int pending;
        private double minimumLength;
        private @Nullable Owner owner;
        private @Nullable Children children;

        private Cover(int low, int high, OrthogonalRoutingAxis axis) {
            this.low = low;
            this.high = high;
            minimumLength = axis.value(high) - axis.value(low);
        }

        private double shared(int start, int end, Owner candidate, OrthogonalRoutingAxis axis) {
            if (end <= low || start >= high || maximum == 0) return 0;
            if (start <= low && high <= end) {
                if (!candidate.equals(owner)) return -1;
                return axis.value(high) - axis.value(low) - (minimum == 0 ? minimumLength : 0);
            }
            if (minimum > 0 && owner != null) {
                return candidate.equals(owner) ? axis.value(Math.min(end, high)) - axis.value(Math.max(start, low)) : -1;
            }
            Children split = split(axis);
            double before = split.left().shared(start, end, candidate, axis);
            if (before < 0) return -1;
            double after = split.right().shared(start, end, candidate, axis);
            return after < 0 ? -1 : before + after;
        }

        private void change(int start, int end, Owner candidate, int delta, OrthogonalRoutingAxis axis) {
            if (end <= low || start >= high) return;
            if (start <= low && high <= end) {
                if (maximum != 0 && !candidate.equals(owner) || delta < 0 && minimum == 0) {
                    throw new IllegalStateException("Incompatible or unbalanced coverage owner");
                }
                apply(delta, candidate);
                return;
            }
            Children split = split(axis);
            Cover left = split.left();
            Cover right = split.right();
            left.change(start, end, candidate, delta, axis);
            right.change(start, end, candidate, delta, axis);
            minimum = Math.min(left.minimum, right.minimum);
            maximum = Math.max(left.maximum, right.maximum);
            minimumLength = (left.minimum == minimum ? left.minimumLength : 0) + (right.minimum == minimum ? right.minimumLength : 0);
            owner = left.maximum == 0 ? right.owner : right.maximum == 0 ? left.owner : left.owner != null && left.owner.equals(right.owner) ? left.owner : null;
            if (minimum == maximum && (owner != null || maximum == 0)) {
                children = null;
                pending = 0;
            }
        }

        private void apply(int delta, Owner candidate) {
            minimum += delta;
            maximum += delta;
            if (minimum < 0) throw new IllegalStateException("Negative route coverage count");
            if (maximum == 0) {
                owner = null;
                children = null;
                pending = 0;
            } else {
                owner = candidate;
                pending += delta;
            }
        }

        private Children split(OrthogonalRoutingAxis axis) {
            if (high - low <= 1) throw new IllegalStateException("Cannot split one compressed routing interval");
            Children result = children;
            if (result == null) {
                int middle = (low + high) >>> 1;
                result = new Children(new Cover(low, middle, axis), new Cover(middle, high, axis));
                if (maximum > 0) {
                    Owner uniformOwner = Objects.requireNonNull(owner, "Uniform coverage must have one owner");
                    result.left().apply(maximum, uniformOwner);
                    result.right().apply(maximum, uniformOwner);
                }
                children = result;
            } else if (pending != 0) {
                Owner pendingOwner = Objects.requireNonNull(owner, "Lazy coverage must have one owner");
                result.left().apply(pending, pendingOwner);
                result.right().apply(pending, pendingOwner);
            }
            pending = 0;
            return result;
        }

        private record Children(Cover left, Cover right) {}
    }

    /** Canonical point-range cover with fixed-coordinate references rather than original route memberships. */
    private static final class Stabbing {

        private final int base;
        private final Int2ObjectOpenHashMap<@Nullable Int2IntAVLTreeMap> members = new Int2ObjectOpenHashMap<>();

        private Stabbing(int coordinates) {
            int value = 1;
            while (value < coordinates) value <<= 1;
            base = value;
        }

        private void change(int start, int end, int fixed, int delta) {
            int left = start + base;
            int right = end + base;
            while (left < right) {
                if ((left & 1) != 0) change(left++, fixed, delta);
                if ((right & 1) != 0) change(--right, fixed, delta);
                left >>= 1;
                right >>= 1;
            }
        }

        private void change(int node, int fixed, int delta) {
            Int2IntAVLTreeMap counts = members.get(node);
            if (counts == null) {
                if (delta < 0) throw new IllegalStateException("Missing released stabbing range");
                counts = new Int2IntAVLTreeMap();
                members.put(node, counts);
            }
            int count = counts.get(fixed) + delta;
            if (count < 0) throw new IllegalStateException("Negative stabbing reference count");
            if (count == 0) counts.remove(fixed);
            else counts.put(fixed, count);
            if (counts.isEmpty()) members.remove(node);
        }

        private void collect(int coordinate, int start, int end, IntSet result) {
            if (start >= end) return;
            for (int node = coordinate + base; node > 0; node >>= 1) {
                Int2IntAVLTreeMap counts = members.get(node);
                if (counts != null) result.addAll(counts.subMap(start, end).keySet());
            }
        }

        private boolean contains(int coordinate, int fixed) {
            for (int node = coordinate + base; node > 0; node >>= 1) {
                Int2IntAVLTreeMap counts = members.get(node);
                if (counts != null && counts.containsKey(fixed)) return true;
            }
            return false;
        }
    }
}
