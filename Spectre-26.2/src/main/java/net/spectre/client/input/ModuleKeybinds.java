package net.spectre.client.input;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.IntPredicate;
import net.spectre.client.module.ClientModule;
import net.spectre.client.module.ModuleRevision;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ModuleKeybinds {
   public static final int MOUSE_OFFSET = 1000;
   private static final int KEY_COUNT = 1008;
   private final boolean[] previous = new boolean[1008];
   private final boolean[] current = new boolean[1008];
   private final boolean[] sampled = new boolean[1008];
   private int[] sampledKeys = new int[1008];
   private int[] previousKeys = new int[1008];
   private int sampledCount;
   private int previousCount;
   private final Map<ClientModule, Integer> held = new IdentityHashMap<>();
   private boolean wasAcceptingInput = true;
   private final List<ClientModule> bound = new ArrayList<>();
   private List<ClientModule> boundFrom;
   private long boundRevision;

   public void update(List<ClientModule> modules, IntPredicate isDown, boolean acceptInput) {
      List<ClientModule> candidates = this.boundModules(modules);

      for (int i = 0; i < this.previousCount; i++) {
         int key = this.previousKeys[i];
         this.current[key] = false;
         this.sampled[key] = false;
      }

      this.sampledCount = 0;

      for (ClientModule module : candidates) {
         int key = module.keybind();
         if (!this.sampled[key]) {
            this.current[key] = isDown.test(key);
            this.sampled[key] = true;
            this.sampledKeys[this.sampledCount++] = key;
         }
      }

      if (!this.held.isEmpty()) {
         Iterator<Entry<ClientModule, Integer>> iterator = this.held.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<ClientModule, Integer> entry = iterator.next();
            ClientModule modulex = entry.getKey();
            int key = entry.getValue();
            if (!acceptInput || !modulex.isHoldMode() || modulex.keybind() != key || !this.current[key]) {
               iterator.remove();
               modulex.setEnabled(false);
            }
         }
      }

      if (acceptInput && this.wasAcceptingInput) {
         for (ClientModule modulex : candidates) {
            int key = modulex.keybind();
            if (isBindable(key) && this.current[key] && !this.previous[key]) {
               if (modulex.isAction()) {
                  modulex.runAction();
               } else if (modulex.isHoldMode()) {
                  modulex.setEnabled(true);
                  if (modulex.isEnabled()) {
                     this.held.put(modulex, key);
                  }
               } else {
                  modulex.toggle();
               }
            }
         }
      }

      for (int i = 0; i < this.previousCount; i++) {
         this.previous[this.previousKeys[i]] = false;
      }

      for (int i = 0; i < this.sampledCount; i++) {
         this.previous[this.sampledKeys[i]] = this.current[this.sampledKeys[i]];
      }

      int[] swap = this.previousKeys;
      this.previousKeys = this.sampledKeys;
      this.sampledKeys = swap;
      this.previousCount = this.sampledCount;
      this.wasAcceptingInput = acceptInput;
   }

   private List<ClientModule> boundModules(List<ClientModule> modules) {
      long revision = ModuleRevision.current();
      if (modules != this.boundFrom || revision != this.boundRevision) {
         this.bound.clear();

         for (ClientModule module : modules) {
            if (isBindable(module.keybind())) {
               this.bound.add(module);
            }
         }

         this.boundFrom = modules;
         this.boundRevision = revision;
      }

      return this.bound;
   }

   public static boolean isBindable(int key) {
      return key >= 32 && key <= 348 || key >= 1000 && key < 1008;
   }
}
