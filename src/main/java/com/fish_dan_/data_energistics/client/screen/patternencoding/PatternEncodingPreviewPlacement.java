package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/** Chooses a stable, low-overlap automatic anchor for a pattern-provider preview panel. */
final class PatternEncodingPreviewPlacement {

    private static final int SCREEN_MARGIN = 4;

    private PatternEncodingPreviewPlacement() {}

    static Rect2i findBestBounds(Rect2i anchor, int panelWidth, int panelHeight, int preferredY,
                                 int rightOffset, int leftOffset, int verticalGap,
                                 int screenWidth, int screenHeight, List<Rect2i> occupiedZones) {
        int rightX = anchor.getX() + anchor.getWidth() + rightOffset;
        int leftX = anchor.getX() - panelWidth + leftOffset;
        int centeredX = anchor.getX() + (anchor.getWidth() - panelWidth) / 2;
        int aboveY = anchor.getY() - panelHeight - verticalGap;
        int belowY = anchor.getY() + anchor.getHeight() + verticalGap;

        List<Rect2i> candidates = new ArrayList<>();
        addCandidate(candidates, rightX, preferredY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, preferredY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, rightX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, rightX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, leftX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, centeredX, aboveY, panelWidth, panelHeight, screenWidth, screenHeight);
        addCandidate(candidates, centeredX, belowY, panelWidth, panelHeight, screenWidth, screenHeight);

        Rect2i bestCandidate = candidates.get(0);
        long bestScore = computeScore(bestCandidate, anchor, occupiedZones);
        for (int index = 1; index < candidates.size(); index++) {
            Rect2i candidate = candidates.get(index);
            long score = computeScore(candidate, anchor, occupiedZones);
            if (score < bestScore) {
                bestCandidate = candidate;
                bestScore = score;
            }
        }
        return bestCandidate;
    }

    private static void addCandidate(List<Rect2i> candidates, int x, int y, int width, int height,
                                     int screenWidth, int screenHeight) {
        candidates.add(clamp(x, y, width, height, screenWidth, screenHeight));
    }

    private static Rect2i clamp(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        int clampedX = Math.max(SCREEN_MARGIN, Math.min(x, screenWidth - width - SCREEN_MARGIN));
        int clampedY = Math.max(SCREEN_MARGIN, Math.min(y, screenHeight - height - SCREEN_MARGIN));
        return new Rect2i(clampedX, clampedY, width, height);
    }

    private static long computeScore(Rect2i candidate, Rect2i anchor, List<Rect2i> occupiedZones) {
        long score = computeOverlapArea(candidate, anchor) * 8L;
        for (Rect2i occupiedZone : occupiedZones) {
            score += computeOverlapArea(candidate, occupiedZone);
        }
        return score;
    }

    private static int computeOverlapArea(Rect2i first, Rect2i second) {
        int overlapWidth = Math.min(first.getX() + first.getWidth(), second.getX() + second.getWidth()) - Math.max(first.getX(), second.getX());
        int overlapHeight = Math.min(first.getY() + first.getHeight(), second.getY() + second.getHeight()) - Math.max(first.getY(), second.getY());
        if (overlapWidth <= 0 || overlapHeight <= 0) {
            return 0;
        }
        return overlapWidth * overlapHeight;
    }
}
