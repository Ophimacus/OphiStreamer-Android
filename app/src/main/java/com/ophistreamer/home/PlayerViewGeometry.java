package com.ophistreamer.home;

/** Normalized source rectangles; player numbers describe picture sections, not gamepad slots. */
public final class PlayerViewGeometry {
    private PlayerViewGeometry() { }

    public static float[] region(String player, String split) {
        boolean second = "p2".equals(player);
        if (!"p1".equals(player) && !second) return new float[]{0, 0, 1, 1};
        return "stacked".equals(split)
                ? new float[]{0, second ? .5f : 0, 1, .5f}
                : new float[]{second ? .5f : 0, 0, .5f, 1};
    }

    /** {viewportWidth, viewportHeight, sourceWidth, sourceHeight, sourceLeft, sourceTop}. */
    public static int[] fit(int availableWidth, int availableHeight, double sourceAspect, float[] r) {
        if (availableWidth <= 0 || availableHeight <= 0 || !Double.isFinite(sourceAspect) || sourceAspect <= 0)
            return new int[]{0, 0, 0, 0, 0, 0};
        double aspect = sourceAspect * r[2] / r[3];
        int w = Math.max(1, Math.min(availableWidth, (int) Math.round(availableHeight * aspect)));
        int h = Math.max(1, Math.min(availableHeight, (int) Math.round(w / aspect)));
        int sourceWidth = Math.max(1, Math.round(w / r[2]));
        int sourceHeight = Math.max(1, Math.round(h / r[3]));
        return new int[]{w, h, sourceWidth, sourceHeight, -Math.round(sourceWidth * r[0]), -Math.round(sourceHeight * r[1])};
    }
}
