package net.spectre.client.module.impl.render;
import net.minecraft.client.Minecraft;
import net.spectre.client.event.Stage;
import net.spectre.client.event.impl.TickEvent;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;
import net.spectre.client.module.setting.NumberSetting;
public final class TimeChangerModule extends ClientModule {
    public static volatile TimeChangerModule active;
    private final NumberSetting time=addSetting("time",new NumberSetting("Time",0,-20000,20000).step(1));
    public TimeChangerModule(){super("time-changer","Time Changer","Makes you able to set a custom time.",ModuleCategory.RENDER);}
    @Override protected void onEnable(){active=this;on(TickEvent.TYPE,Stage.POST,e->invalidate());invalidate();}
    @Override protected void onDisable(){active=null;invalidate();}
    private static void invalidate(){var level=Minecraft.getInstance().level;if(level!=null)level.environmentAttributes().invalidateTickCache();}
    public long forcedDayTime(){return (long)time.get();}
}
