package net.spectre.client.module.impl.client;

import net.minecraft.client.Minecraft;
import net.spectre.client.map.HiveMapScreen;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;

public final class HiveMapModule extends ClientModule {
    public HiveMapModule(){super("hive-map","Hive Map","Explore nearby chunks in a 3D map with underground and Y-slice views.",ModuleCategory.CLIENT);setKeybind(72);}
    @Override public boolean isAction(){return true;}
    @Override public boolean mascotTaskActive(){return Minecraft.getInstance().gui.screen() instanceof HiveMapScreen;}
    @Override public boolean persistEnabled(){return false;}
    @Override public void onClick(){
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.player!=null)mc.execute(()->mc.gui.setScreen(new HiveMapScreen()));
    }
}
