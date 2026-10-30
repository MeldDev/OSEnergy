package appeng.parts.p2p;

import appeng.api.AEApi;
import appeng.api.config.TunnelType;
import appeng.api.definitions.IParts;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.me.GridAccessException;
import appeng.me.cache.P2PCache;
import appeng.util.Platform;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

public class PartP2PTunnelNormal extends PartP2PTunnel {
   public PartP2PTunnelNormal(ItemStack is) {
      super(is);
   }

   public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
      ItemStack is = player.field_71071_by.func_70448_g();
      TunnelType tt = AEApi.instance().registries().p2pTunnel().getTunnelTypeByItem(is);
      if (is != null) {
         Item newType = is.func_77973_b();
         if (newType instanceof IMemoryCard) {
            IMemoryCard mc = (IMemoryCard)newType;
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) {
               return false;
            }

            NBTTagCompound data = mc.getData(is);
            ItemStack newType = ItemStack.func_77949_a(data);
            long freq = data.func_74763_f("freq");
            if (newType != null && newType.func_77973_b() instanceof IPartItem) {
               IPart testPart = ((IPartItem)newType.func_77973_b()).createPartFromItemStack(newType);
               if (testPart instanceof PartP2PTunnelNormal) {
                  this.getHost().removePart(this.getSide(), true);
                  ForgeDirection dir = this.getHost().addPart(newType, this.getSide(), player);
                  IPart newBus = this.getHost().getPart(dir);
                  if (newBus instanceof PartP2PTunnel) {
                     PartP2PTunnel<?> newTunnel = (PartP2PTunnel)newBus;
                     newTunnel.setOutput(true);

                     try {
                        this.pasteMemoryCardData(newTunnel, data);
                     } catch (GridAccessException var15) {
                     }

                     newTunnel.onTunnelNetworkChange();
                  }

                  mc.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                  return true;
               }
            }

            mc.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return false;
         }
      }

      if (tt != null) {
         ItemStack newType = null;
         IParts parts = AEApi.instance().definitions().parts();
         switch (tt) {
            case LIGHT:
               for(ItemStack stack : parts.p2PTunnelLight().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case RF_POWER:
               for(ItemStack stack : parts.p2PTunnelRF().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case FLUID:
               for(ItemStack stack : parts.p2PTunnelLiquids().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case IC2_POWER:
               for(ItemStack stack : parts.p2PTunnelEU().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case ITEM:
               for(ItemStack stack : parts.p2PTunnelItems().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case ME:
               for(ItemStack stack : parts.p2PTunnelME().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case REDSTONE:
               for(ItemStack stack : parts.p2PTunnelRedstone().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case COMPUTER_MESSAGE:
               for(ItemStack stack : parts.p2PTunnelOpenComputers().maybeStack(1).asSet()) {
                  newType = stack;
               }
               break;
            case PRESSURE:
               for(ItemStack stack : parts.p2PTunnelPneumaticCraft().maybeStack(1).asSet()) {
                  newType = stack;
               }
         }

         if (newType != null && !Platform.isSameItem(newType, this.getItemStack())) {
            if ((new Throwable()).getStackTrace()[2].getMethodName().equals("place")) {
               return true;
            }

            boolean oldOutput = this.isOutput();
            long myFreq = this.getFrequency();
            this.getHost().removePart(this.getSide(), false);
            ForgeDirection dir = this.getHost().addPart(newType, this.getSide(), player);
            IPart newBus = this.getHost().getPart(dir);
            if (newBus instanceof PartP2PTunnel) {
               PartP2PTunnel<?> newTunnel = (PartP2PTunnel)newBus;
               newTunnel.setOutput(oldOutput);
               newTunnel.onTunnelNetworkChange();

               try {
                  P2PCache p2p = newTunnel.getProxy().getP2P();
                  p2p.updateFreq(newTunnel, myFreq);
               } catch (GridAccessException var16) {
               }
            }

            Platform.notifyBlocksOfNeighbors(this.getTile().func_145831_w(), this.getTile().field_145851_c, this.getTile().field_145848_d, this.getTile().field_145849_e);
            return true;
         }
      }

      return false;
   }
}
