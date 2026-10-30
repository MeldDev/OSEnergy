package li.cil.oc.integration.enderio;

import crazypants.enderio.api.tool.ITool;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class EventHandlerEnderIO$ {
   public static final EventHandlerEnderIO$ MODULE$;

   static {
      new EventHandlerEnderIO$();
   }

   public boolean useWrench(EntityPlayer player, int x, int y, int z, boolean changeDurability) {
      Item var6 = player.func_70694_bm().func_77973_b();
      boolean var8;
      if (var6 instanceof ITool) {
         boolean var10000;
         if (changeDurability) {
            ((ITool)var6).used(player.func_70694_bm(), player, x, y, z);
            var10000 = true;
         } else {
            var10000 = ((ITool)var6).canUse(player.func_70694_bm(), player, x, y, z);
         }

         var8 = var10000;
      } else {
         var8 = false;
      }

      return var8;
   }

   public boolean isWrench(ItemStack stack) {
      return stack.func_77973_b() instanceof ITool;
   }

   private EventHandlerEnderIO$() {
      MODULE$ = this;
   }
}
