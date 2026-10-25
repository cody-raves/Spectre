package net.spectre.client.module;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class ModuleList extends AbstractList<ClientModule> implements RandomAccess {
   static final ModuleList EMPTY = new ModuleList(new ClientModule[0]);
   private final ClientModule[] modules;

   private ModuleList(ClientModule[] modules) {
      this.modules = modules;
   }

   static ModuleList of(Collection<ClientModule> modules) {
      return modules.isEmpty() ? EMPTY : new ModuleList(modules.toArray(new ClientModule[0]));
   }

   ModuleList with(ClientModule module) {
      ClientModule[] next = Arrays.copyOf(this.modules, this.modules.length + 1);
      next[this.modules.length] = module;
      return new ModuleList(next);
   }

   ModuleList without(ClientModule module) {
      int index = this.indexOfIdentity(module);
      if (index < 0) {
         return this;
      } else if (this.modules.length == 1) {
         return EMPTY;
      } else {
         ClientModule[] next = new ClientModule[this.modules.length - 1];
         System.arraycopy(this.modules, 0, next, 0, index);
         System.arraycopy(this.modules, index + 1, next, index, this.modules.length - index - 1);
         return new ModuleList(next);
      }
   }

   boolean containsIdentity(ClientModule module) {
      return this.indexOfIdentity(module) >= 0;
   }

   private int indexOfIdentity(ClientModule module) {
      for (int i = 0; i < this.modules.length; i++) {
         if (this.modules[i] == module) {
            return i;
         }
      }

      return -1;
   }

   public ClientModule get(int index) {
      return this.modules[index];
   }

   @Override
   public int size() {
      return this.modules.length;
   }

   @Override
   public Iterator<ClientModule> iterator() {
      return new Iterator<ClientModule>() {
         private int next;

         @Override
         public boolean hasNext() {
            return this.next < ModuleList.this.modules.length;
         }

         public ClientModule next() {
            if (this.next >= ModuleList.this.modules.length) {
               throw new NoSuchElementException();
            } else {
               return ModuleList.this.modules[this.next++];
            }
         }
      };
   }

   @Override
   public void forEach(Consumer<? super ClientModule> action) {
      for (ClientModule module : this.modules) {
         action.accept(module);
      }
   }

   @Override
   public Object[] toArray() {
      return Arrays.copyOf(this.modules, this.modules.length, Object[].class);
   }
}
