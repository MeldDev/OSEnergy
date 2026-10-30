package appeng.items.tools;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.core.features.AEFeature;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.util.Platform;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

public class ToolMemoryCard extends AEBaseItem implements IMemoryCard {
   public ToolMemoryCard() {
      this.setFeature(EnumSet.of(AEFeature.Core));
      this.func_77625_d(1);
   }

   public void addCheckedInformation(ItemStack stack, EntityPlayer player, List lines, boolean displayMoreInfo) {
      lines.add(this.getLocalizedName(this.getSettingsName(stack) + ".name", this.getSettingsName(stack)));
      NBTTagCompound data = this.getData(stack);
      if (data.func_74764_b("tooltip")) {
         lines.add(StatCollector.func_74838_a(this.getLocalizedName(data.func_74779_i("tooltip") + ".name", data.func_74779_i("tooltip"))));
      }

      if (data.func_74764_b("freq")) {
         long freq = data.func_74763_f("freq");
         String freqTooltip = String.format("%X", freq).replaceAll("(.{4})", "$0 ").trim();
         String local = ButtonToolTips.P2PFrequency.getLocal();
         lines.add(String.format(local, freqTooltip));
      }

      if (data.func_74764_b("custom_name")) {
         lines.add(data.func_74779_i("custom_name"));
      }

   }

   private String getLocalizedName(String... name) {
      for(String n : name) {
         String l = StatCollector.func_74838_a(n);
         if (!l.equals(n)) {
            return l;
         }
      }

      int var7 = name.length;
      byte var8 = 0;
      if (var8 < var7) {
         String n = name[var8];
         return n;
      } else {
         return "";
      }
   }

   public void setMemoryCardContents(ItemStack is, String settingsName, NBTTagCompound data) {
      NBTTagCompound c = Platform.openNbtData(is);
      c.func_74778_a("Config", settingsName);
      c.func_74782_a("Data", data);
   }

   public String getSettingsName(ItemStack is) {
      NBTTagCompound c = Platform.openNbtData(is);
      String name = c.func_74779_i("Config");
      return name != null && !name.isEmpty() ? name : GuiText.Blank.getUnlocalized();
   }

   public NBTTagCompound getData(ItemStack is) {
      NBTTagCompound c = Platform.openNbtData(is);
      NBTTagCompound o = c.func_74775_l("Data");
      if (o == null) {
         o = new NBTTagCompound();
      }

      return (NBTTagCompound)o.func_74737_b();
   }

   public void notifyUser(EntityPlayer player, MemoryCardMessages msg) {
   }

   public boolean func_77648_a(ItemStack is, EntityPlayer player, World w, int x, int y, int z, int side, float hx, float hy, float hz) {
      if (player.func_70093_af() && !w.field_72995_K) {
         if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) {
            return false;
         } else {
            IMemoryCard mem = (IMemoryCard)is.func_77973_b();
            mem.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
            is.func_77982_d((NBTTagCompound)null);
            return true;
         }
      } else {
         return super.func_77648_a(is, player, w, x, y, z, side, hx, hy, hz);
      }
   }

   public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
      return true;
   }
}
