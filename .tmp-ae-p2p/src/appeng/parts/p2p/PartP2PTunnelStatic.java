package appeng.parts.p2p;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartItemStack;
import appeng.me.GridAccessException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

public abstract class PartP2PTunnelStatic extends PartP2PTunnel {
   public PartP2PTunnelStatic(ItemStack is) {
      super(is);
   }

   public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
      ItemStack is = player.field_71071_by.func_70448_g();
      if (is != null) {
         Item data = is.func_77973_b();
         if (data instanceof IMemoryCard) {
            IMemoryCard mc = (IMemoryCard)data;
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) {
               return false;
            }

            NBTTagCompound data = mc.getData(is);
            ItemStack newType = ItemStack.func_77949_a(data);
            if (newType != null) {
               Item testPart = newType.func_77973_b();
               if (testPart instanceof IPartItem) {
                  IPartItem partItem = (IPartItem)testPart;
                  IPart testPart = partItem.createPartFromItemStack(newType);
                  if (this.getClass().isInstance(testPart)) {
                     this.getHost().removePart(this.getSide(), true);
                     ForgeDirection dir = this.getHost().addPart(newType, this.getSide(), player);
                     IPart newBus = this.getHost().getPart(dir);
                     if (newBus instanceof PartP2PTunnel) {
                        PartP2PTunnel<?> newTunnel = (PartP2PTunnel)newBus;
                        newTunnel.setOutput(true);

                        try {
                           this.pasteMemoryCardData(newTunnel, data);
                        } catch (GridAccessException var13) {
                        }

                        newTunnel.onTunnelNetworkChange();
                     }

                     mc.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                     return true;
                  }
               }
            }

            mc.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
         }
      }

      return false;
   }

   public ItemStack getItemStack(PartItemStack type) {
      return type != PartItemStack.World && type != PartItemStack.Network && type != PartItemStack.Wrench ? super.getItemStack(PartItemStack.Pick) : super.getItemStack(type);
   }
}
