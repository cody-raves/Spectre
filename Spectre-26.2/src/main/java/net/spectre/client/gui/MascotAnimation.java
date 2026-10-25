package net.spectre.client.gui;

/** Continuous facial choreography, independent of frame rate and Minecraft. */
final class MascotAnimation {
    enum Mood { IDLE, WATCH, WORK, SALE, PAUSED, SLEEP, ALERT }

    record Pose(double gazeX, double gazeY, double leftOpen, double rightOpen,
                double mouthOpen, double smile, double brow, double pupil,
                double money, double leftReel, double rightReel, double dots) {
        Pose blend(Pose to, double t) {
            return new Pose(mix(gazeX, to.gazeX, t), mix(gazeY, to.gazeY, t),
                    mix(leftOpen, to.leftOpen, t), mix(rightOpen, to.rightOpen, t),
                    mix(mouthOpen, to.mouthOpen, t), mix(smile, to.smile, t),
                    mix(brow, to.brow, t), mix(pupil, to.pupil, t),
                    mix(money, to.money, t), to.leftReel, to.rightReel, to.dots);
        }
    }

    private Mood current;
    private long started;
    private long event;
    private Pose from;
    private Pose displayed;

    Pose frame(Mood mood, long millis, long eventId, boolean reduced) {
        if (current != mood || (mood == Mood.SALE && event != eventId)) {
            from = displayed;
            current = mood;
            started = millis;
            event = eventId;
        }
        long elapsed = Math.max(0, millis - started);
        Pose target = sample(mood, elapsed, reduced);
        displayed = reduced || from == null ? target : from.blend(target, smooth(elapsed / 180.0));
        return displayed;
    }

    static Pose sample(Mood mood, long millis, boolean reduced) {
        // Reduced motion has a representative pose, independent of the clock.
        double t = reduced ? 0 : Math.max(0, millis) / 1000.0;
        double gx = 0, gy = 0, open = 1, right = 1, mouth = 0, smile = .7, brow = 0, pupil = 1;
        double money = 0, leftReel = 0, rightReel = 0, dots = -1;
        switch (mood) {
            case IDLE -> {
                double p = t % 11;
                gx = -.85 * window(p, 1.0, 1.25, 2.0, 2.25)
                        + .8 * window(p, 2.35, 2.65, 3.35, 3.65);
                gy = -.9 * window(p, 4.15, 4.55, 5.7, 6.05);
                double yawn = window(p, 7.15, 7.65, 8.25, 8.9);
                open = (.55 - .39 * yawn) * blink(p, .6, 3.85, 6.2, 9.6);
                right = open;
                mouth = .9 * yawn;
                smile = .05 - .2 * yawn;
                brow = -.15;
            }
            case WATCH -> {
                double p = t % 3.6;
                double line = p % 1.2;
                // Read left to right, then quickly return to the next line.
                gx = line < .96 ? -.95 + 1.9 * smooth(line / .96)
                        : .95 - 1.9 * smooth((line - .96) / .24);
                double row = Math.floor(p / 1.2);
                gy = -.4 + row * .4;
                if (line > .96) gy += (row == 2 ? -.8 : .4) * smooth((line - .96) / .24);
                open = right = .95 * blink(p, 3.39);
                smile = .35;
                brow = .18;
                pupil = .86;
                if (reduced) { gx = -.35; gy = 0; }
            }
            case WORK -> {
                gx = .5 * Math.sin(t * 2.1);
                gy = .22 * Math.sin(t * 1.6);
                open = right = .75 * blink(t % 4.8, 3.8);
                brow = .65;
                dots = reduced ? 0 : t * 2.8;
                smile = .1;
            }
            case SALE -> {
                money = 1;
                leftReel = reduced ? 9 : reel(t, 0, 1.65, 9);
                rightReel = reduced ? 11 : reel(t, .13, 2.1, 11);
                mouth = .5 + (reduced ? 0 : .12 * Math.sin(t * 5));
                smile = 1;
                brow = -.4;
            }
            case PAUSED -> {
                gx = reduced ? 0 : .45 * Math.sin(t * .9);
                open = .58 * blink(t % 5.4, 3.6);
                right = .78 * blink(t % 5.4, 3.6);
                smile = -.55;
                brow = -.65;
            }
            case SLEEP -> {
                open = right = .04;
                mouth = reduced ? .15 : .1 + .22 * (1 + Math.sin(t * 1.5)) / 2;
                smile = .3;
            }
            case ALERT -> {
                open = right = 1;
                pupil = .55;
                gx = reduced ? 0 : .6 * Math.sin(t * 7) * Math.exp(-t * .7);
                mouth = .7;
                smile = 0;
                brow = -.5;
            }
        }
        return new Pose(gx, gy, open, right, mouth, smile, brow, pupil, money, leftReel, rightReel, dots);
    }

    private static double reel(double time, double delay, double duration, int turns) {
        double p = Math.clamp((time - delay) / duration, 0, 1);
        return turns * (1 - Math.pow(1 - p, 3));
    }

    private static double blink(double t, double... at) {
        double open = 1;
        for (double start : at) {
            double p = (t - start) / .24;
            if (p >= 0 && p <= 1) open = Math.min(open, 1 - Math.sin(Math.PI * p));
        }
        return Math.max(.03, open);
    }

    private static double window(double p, double in, double peak, double end, double out) {
        return smooth((p - in) / (peak - in)) * (1 - smooth((p - end) / (out - end)));
    }

    private static double smooth(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
