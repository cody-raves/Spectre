package net.spectre.client.map;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Resource reloads keep the same manager instance, so track completion explicitly. */
public final class AtlasResources {
    private static volatile long generation;
    private AtlasResources() {}
    public static long generation(){return generation;}
    public static void install(){
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            Identifier.fromNamespaceAndPath("spectre","hive_map"),
            (ResourceManagerReloadListener) manager -> generation++);
    }
}
