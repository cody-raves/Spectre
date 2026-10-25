package net.spectre.client.gui;

/** Draws independent moving face parts in the shell's 256-unit coordinate space. */
final class MascotFace {
    interface Canvas {
        void fill(int left, int top, int right, int bottom, int color);
        void clip(int left, int top, int right, int bottom);
        void unclip();
    }

    private static final int EYE = 0xFF4FF1E3;
    private static final int DARK = 0xFF091923;
    private static final int LIGHT = 0xFFDAFFF9;
    private static final int GOLD = 0xFFFFD36C;

    private MascotFace() { }

    static void draw(Canvas c, MascotAnimation.Pose p) {
        double eyesAlpha = 1 - p.money();
        if (eyesAlpha > .01) {
            eye(c, 91, p.leftOpen(), p, eyesAlpha);
            eye(c, 165, p.rightOpen(), p, eyesAlpha);
        }
        if (p.money() > .01) {
            reel(c, 91, p.leftReel(), p.money());
            reel(c, 165, p.rightReel(), p.money());
        }
        // Eyebrows articulate independently of the eyes, with mirrored slopes.
        curve(c, 80, 128 - p.brow() * 5, 91, 124, 101, 128 + p.brow() * 5, 2.1, EYE);
        curve(c, 155, 128 + p.brow() * 5, 165, 124, 176, 128 - p.brow() * 5, 2.1, EYE);
        if (p.dots() >= 0) {
            for (int i = 0; i < 3; i++) {
                double pulse = .5 + .5 * Math.sin(p.dots() - i * .9);
                ellipse(c, 118 + i * 10, 190 - pulse * 3, 2.4 + pulse, 2.4 + pulse, EYE);
            }
        } else if (p.mouthOpen() > .12) {
            double ry = 2 + p.mouthOpen() * 9;
            double rx = 4 + Math.max(0, p.smile()) * 9;
            ellipse(c, 128, 190, rx, ry, EYE);
            ellipse(c, 128, 188, Math.max(2, rx - 3), Math.max(1, ry - 3), DARK);
        } else {
            curve(c, 119, 188, 128, 188 + p.smile() * 14, 137, 188, 2.2, EYE);
        }
    }

    private static void eye(Canvas c, int cx, double openness, MascotAnimation.Pose p, double alpha) {
        int top = (int) Math.round(176 - 42 * openness);
        int bottom = 176;
        if (openness < .12) {
            curve(c, cx - 12, 163, cx, 168, cx + 12, 163, 2.3, fade(EYE, alpha));
            return;
        }
        c.clip(cx - 16, top, cx + 16, bottom);
        ellipse(c, cx, 155, 15, 21, fade(EYE, alpha));
        double px = cx + p.gazeX() * 6;
        double py = 156 + p.gazeY() * 6;
        ellipse(c, px, py, 8.5 * p.pupil(), 11.5 * p.pupil(), fade(DARK, alpha));
        ellipse(c, px - 3, py - 4, 3, 3.4, fade(LIGHT, alpha));
        c.unclip();
    }

    private static void reel(Canvas c, int x, double position, double alpha) {
        c.clip(x - 17, 135, x + 18, 178);
        double fraction = position - Math.floor(position);
        // Adjacent dollar signs pass continuously through the eye window.
        for (int row = -1; row <= 1; row++) {
            double cy = 156 + (row - fraction) * 48;
            double visibility = Math.max(.15, 1 - Math.abs(cy - 156) / 43);
            dollar(c, x, cy, fade(GOLD, alpha * visibility));
        }
        c.unclip();
    }

    private static void dollar(Canvas c, double x, double y, int color) {
        // Rounded, deliberately oversized S and stem, readable at HUD size.
        curve(c, x + 9, y - 11, x - 11, y - 19, x - 10, y - 6, 2.5, color);
        curve(c, x - 10, y - 6, x - 9, y - 1, x + 1, y, 2.5, color);
        curve(c, x + 1, y, x + 12, y + 1, x + 10, y + 9, 2.5, color);
        curve(c, x + 10, y + 9, x + 5, y + 18, x - 10, y + 11, 2.5, color);
        curve(c, x, y - 19, x, y, x, y + 19, 1.7, color);
    }

    private static int fade(int color, double amount) {
        return ((int) Math.round(255 * Math.clamp(amount, 0, 1)) << 24) | (color & 0xFFFFFF);
    }

    private static void curve(Canvas c, double x0, double y0, double x1, double y1,
                              double x2, double y2, double radius, int color) {
        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0, a = 1 - t;
            ellipse(c, a * a * x0 + 2 * a * t * x1 + t * t * x2,
                    a * a * y0 + 2 * a * t * y1 + t * t * y2, radius, radius, color);
        }
    }

    private static void ellipse(Canvas c, double cx, double cy, double rx, double ry, int color) {
        if ((color >>> 24) == 0) return;
        int first = (int) Math.floor(cy - ry), end = (int) Math.ceil(cy + ry);
        for (int y = first; y < end; y++) {
            double dy = (y + .5 - cy) / ry;
            if (Math.abs(dy) >= 1) continue;
            double half = rx * Math.sqrt(1 - dy * dy);
            int left = (int) Math.round(cx - half), right = (int) Math.round(cx + half);
            if (right > left) c.fill(left, y, right, y + 1, color);
        }
    }
}
