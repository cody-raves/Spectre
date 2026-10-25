package net.spectre.client.media;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import me.friwi.jcefmaven.CefAppBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefRequestContext;

/** Lazy, isolated browser profile. No disabled web security or imported browser sessions. */
final class MediaRuntime {
    private static CompletableFuture<CefClient> future;
    private static volatile CefApp app;
    private static volatile boolean stopped;
    static volatile String progress="Preparing browser";
    static synchronized CompletableFuture<CefClient> initialize(){
        if(future!=null)return future;
        future=CompletableFuture.supplyAsync(()->{
            try{
                System.setProperty("java.awt.headless","false");
                var directory=FabricLoader.getInstance().getConfigDir().resolve("spectre/media").toAbsolutePath();
                Files.createDirectories(directory);
                var builder=new CefAppBuilder();
                builder.setInstallDir(directory.resolve("runtime").toFile());
                builder.getCefSettings().windowless_rendering_enabled=true;
                builder.getCefSettings().root_cache_path=directory.resolve("profile").toString();
                builder.setProgressHandler((stage,percent)->progress=stage.name().replace('_',' ')+(percent>=0?" · "+Math.round(percent)+"%":""));
                app=builder.build();
                if(stopped){app.dispose();throw new IllegalStateException("Client closed");}
                return app.createClient();
            }catch(Exception e){progress="Browser could not start. Reopen the player to see its status.";throw new java.util.concurrent.CompletionException(e);}
        });
        return future;
    }
    static VideoBrowser create(CefClient client,String url){
        var browser=new VideoBrowser(client,url,CefRequestContext.getGlobalContext());
        browser.setCloseAllowed();browser.createImmediately();return browser;
    }
    static void shutdown(){stopped=true;if(app!=null)app.dispose();}
}
