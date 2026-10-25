package net.spectre.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.level.block.state.BlockState;

/** Isolated submissions for offscreen icons; never drains vanilla's hand/GUI queue. */
public final class RenderBridge {
    private static SubmitNodeStorage icons=new SubmitNodeStorage();
    private RenderBridge(){}
    public static SubmitNodeStorage icons(){return icons;}
    public static void clearIcons(){icons=new SubmitNodeStorage();}
    public static void flushIcons(){
        try{Minecraft.getInstance().gameRenderer.featureRenderDispatcher().renderAllFeatures(icons);}
        finally{clearIcons();}
    }
    public static boolean translucent(BlockState state){
        var material=Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state);
        return material.forceTranslucent()||material.sprite().contents().transparency().hasTranslucent();
    }
    public static int tint(BlockState state){
        var source=Minecraft.getInstance().getBlockColors().getTintSource(state,0);
        return source==null?-1:source.color(state);
    }
}
