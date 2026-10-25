package net.spectre.client.gui;

import imgui.ImDrawList;

/** Shared Spectre artwork and colors for startup, menus and the gameplay HUD. */
public final class SpectreBrand {
    public static final String LOGO_RESOURCE = "/assets/spectre/textures/gui/logo.png";
    public static final String BACKDROP_RESOURCE = "/assets/spectre/textures/gui/spectre_backdrop.png";
    public static final int ACCENT = 0xff72e6ef;
    public static final int TEXT = 0xffeaf8ff;
    public static final int MUTED = 0xff93afc4;
    private SpectreBrand() {}

    public static ThemeImages.Texture logo() { return SpectreDraw.texture(LOGO_RESOURCE); }

    public static void backdrop(ImDrawList dl, float w, float h, float time, float alpha) {
        float t = GuiPalette.animationSpeed() <= 0 ? 0 : time;
        dl.addRectFilledMultiColor(0, 0, w, h,
            SpectreDraw.col(0xff080f1d, alpha), SpectreDraw.col(0xff101a30, alpha),
            SpectreDraw.col(0xff070d18, alpha), SpectreDraw.col(0xff091e28, alpha));
        var backdrop=SpectreDraw.texture(BACKDROP_RESOURCE);
        if(backdrop!=null&&w>0&&h>0){
            float aspect=(float)backdrop.width()/backdrop.height(),screen=w/h;
            float u=screen<aspect?(1-screen/aspect)*.5f:0;
            float v=screen>aspect?(1-aspect/screen)*.5f:0;
            dl.addImage(backdrop.id(),0,0,w,h,u,v,1-u,1-v,SpectreDraw.col(0xffffffff,alpha));
        }
        // Sparse drifting lights, with no textures or per-frame allocations.
        for (int i = 0; i < 36; i++) {
            float x = w * ((i * .618034f) % 1f);
            float y = h * (1f - (i * .381966f + t * (.003f + (i % 3) * .001f)) % 1f);
            float a = .10f + .10f * (float)Math.sin(i + t * .4f);
            dl.addCircleFilled(x, y, 1f + i % 2, SpectreDraw.col(ACCENT, a * alpha), 8);
        }
    }

    public static void mark(ImDrawList dl, float cx, float cy, float size, float time, float alpha) {
        SpectreDraw.glow(dl, cx, cy, size * .66f, ACCENT, .12f * alpha);
        WorkspaceMascot.drawSplash(dl, cx, cy, size, alpha, 1);
    }
}
