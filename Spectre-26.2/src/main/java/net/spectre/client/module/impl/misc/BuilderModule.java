package net.spectre.client.module.impl.misc;

import net.spectre.client.gui.BuilderPanel;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleCategory;

public final class BuilderModule extends ClientModule {
    public BuilderModule(){super("builder","Base Builder","Import a schematic, preview its placement and plan materials and shulker storage.",ModuleCategory.BASE_BUILDER);}
    @Override public boolean isAction(){return true;}
    @Override public boolean mascotTaskActive(){
        var screen=net.minecraft.client.Minecraft.getInstance().gui.screen();
        return net.spectre.client.builder.CreativeBuilder.INSTANCE.running()
            ||net.spectre.client.builder.SchematicPreview.INSTANCE.placing()
            ||screen instanceof net.spectre.client.gui.ImGuiScreen gui&&gui.builderWorkspaceOpen();
    }
    @Override public void onClick(){BuilderPanel.open();}
    @Override public boolean persistEnabled(){return false;}
}
