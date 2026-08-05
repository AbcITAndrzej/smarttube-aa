package com.liskovsoft.smartyoutubetv2.common.misc;

import java.util.List;

/**
 * Android-independent resolver for touch targets.
 *
 * TV layouts often contain controls whose visual bounds are comfortable for a
 * remote focus ring but too small or too sparse for fingers. The resolver
 * expands the logical hit area to a configurable minimum while preferring the
 * real visual bounds, clickable controls, and the deepest visible child.
 */
public final class MobileTouchTargetResolver {
    public static final class Candidate {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final boolean clickable;
        public final boolean focusable;
        public final boolean enabled;
        public final boolean visible;
        public final int depth;
        public final int drawingOrder;

        public Candidate(
                int left,
                int top,
                int right,
                int bottom,
                boolean clickable,
                boolean focusable,
                boolean enabled,
                boolean visible,
                int depth,
                int drawingOrder) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.clickable = clickable;
            this.focusable = focusable;
            this.enabled = enabled;
            this.visible = visible;
            this.depth = depth;
            this.drawingOrder = drawingOrder;
        }

        int width() {
            return Math.max(0, right - left);
        }

        int height() {
            return Math.max(0, bottom - top);
        }
    }

    private MobileTouchTargetResolver() {
    }

    /**
     * Returns the index of the best candidate or -1 when none can receive the tap.
     */
    public static int findBest(
            List<Candidate> candidates,
            float x,
            float y,
            int minimumTargetPx) {
        if (candidates == null || candidates.isEmpty()) {
            return -1;
        }

        int safeMinimum = Math.max(1, minimumTargetPx);
        int bestIndex = -1;
        double bestScore = -Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (!isEligible(candidate)) {
                continue;
            }

            boolean exact = contains(
                    candidate.left, candidate.top, candidate.right, candidate.bottom, x, y);
            float expandedLeft = expandedStart(candidate.left, candidate.right, safeMinimum);
            float expandedRight = expandedEnd(candidate.left, candidate.right, safeMinimum);
            float expandedTop = expandedStart(candidate.top, candidate.bottom, safeMinimum);
            float expandedBottom = expandedEnd(candidate.top, candidate.bottom, safeMinimum);

            if (!contains(expandedLeft, expandedTop, expandedRight, expandedBottom, x, y)) {
                continue;
            }

            float centerX = (candidate.left + candidate.right) / 2f;
            float centerY = (candidate.top + candidate.bottom) / 2f;
            double distanceSquared = square(x - centerX) + square(y - centerY);
            double area = Math.max(1d, (double) candidate.width() * candidate.height());

            // Score bands are deliberately wide so semantic priority always
            // wins over a small geometric difference.
            double score = 0;
            if (exact) {
                score += 1_000_000_000d;
            }
            if (candidate.clickable) {
                score += 100_000_000d;
            }
            if (candidate.focusable) {
                score += 20_000_000d;
            }
            score += candidate.depth * 100_000d;
            score += candidate.drawingOrder * 100d;
            score -= distanceSquared;
            score -= area * 0.0001d;

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private static boolean isEligible(Candidate candidate) {
        return candidate != null
                && candidate.visible
                && candidate.enabled
                && candidate.right > candidate.left
                && candidate.bottom > candidate.top
                && (candidate.clickable || candidate.focusable);
    }

    private static float expandedStart(int start, int end, int minimumTargetPx) {
        int size = Math.max(0, end - start);
        float extension = Math.max(0, minimumTargetPx - size) / 2f;
        return start - extension;
    }

    private static float expandedEnd(int start, int end, int minimumTargetPx) {
        int size = Math.max(0, end - start);
        float extension = Math.max(0, minimumTargetPx - size) / 2f;
        return end + extension;
    }

    private static boolean contains(
            float left, float top, float right, float bottom, float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private static double square(double value) {
        return value * value;
    }
}
