package net.spectre.client.module;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ModuleProvider {
   String id();

   List<ClientModule> createModules();
}
