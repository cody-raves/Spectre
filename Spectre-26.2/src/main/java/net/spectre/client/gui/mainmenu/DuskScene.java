package net.spectre.client.gui.mainmenu;

import imgui.ImDrawList;
import net.spectre.client.gui.SpectreDraw;
import net.spectre.client.gui.SpectreBrand;

/** Spectral night backdrop for the Spectre menu, replacing the old desert scene. */
public final class DuskScene {
    public static final float HORIZON = .72f;
    private DuskScene() {}
    public static float horizon(float height) { return height * HORIZON; }
    public static void draw(ImDrawList dl, float width, float height, float scale, float time,
                            float horizon, float sunX, float sunY, float sunRadius, float lookX, float lookY) {
        SpectreBrand.backdrop(dl, width, height, time, 1);
    }
    public static void veil(ImDrawList dl, float width, float height, float amount) {
        dl.addRectFilled(0, 0, width, height, SpectreDraw.col(0xff080f1d, amount));
    }
}
