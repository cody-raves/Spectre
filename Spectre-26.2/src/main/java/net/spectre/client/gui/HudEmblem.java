package net.spectre.client.gui;

import imgui.ImDrawList;

/** Uses the HUD mascot's own character geometry for the compact brand mark. */
final class HudEmblem {
    private static Icon sized;
    private HudEmblem() {}
    record Icon(int width,int height) {}
    static Icon forHeight(int height) {
        if (height < 4) return null;
        if (sized == null || sized.height() != height)
            sized = new Icon(height, height);
        return sized;
    }
    static void draw(ImDrawList dl,float x,float y,int size,float alpha) {
        WorkspaceMascot.drawHudEmblem(dl,x+size*.5f,y+size*.5f,size,alpha);
    }
}
