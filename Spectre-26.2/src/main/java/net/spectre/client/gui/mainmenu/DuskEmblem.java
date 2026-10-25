package net.spectre.client.gui.mainmenu;

import net.spectre.client.gui.SpectreBrand;

/** Spectre ghost and wordmark, shared visually with the startup splash. */
final class DuskEmblem {
    static final int LIGHT = SpectreBrand.TEXT;
    private DuskEmblem() {}
    static void draw(MenuContext c, float cx, float cy, float size, float nameY) {
        SpectreBrand.mark(c.bg(), cx, cy, size, c.time(), c.intro(0));
        var font = c.nameFont();
        float track = 6 * c.scale();
        float left = cx - c.spacedWidth(font, "SPECTRE CLIENT", track) * .5f;
        c.spacedText(c.bg(), font, left, nameY, track, SpectreBrand.TEXT, c.intro(.3f), "SPECTRE CLIENT");
    }
    static float buttonPass(MenuContext c, int index) {
        float cycle = (c.time() - 1.1f) % 8;
        float p = (cycle - 1.2f - index * .25f) / .8f;
        return cycle < 0 || p < 0 || p > 1 ? -1 : p;
    }
    static float breath(float t) { return .5f - .5f * (float)Math.cos(t * Math.PI * 2 / 3.5); }
    static float smooth(float x) { x = Math.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
}
