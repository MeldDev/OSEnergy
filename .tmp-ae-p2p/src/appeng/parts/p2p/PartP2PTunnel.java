package appeng.parts.p2p;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartRenderHelper;
import appeng.api.parts.PartItemStack;
import appeng.client.texture.CableBusTextures;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.me.GridAccessException;
import appeng.me.cache.P2PCache;
import appeng.me.cache.helpers.TunnelCollection;
import appeng.parts.PartBasicState;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

public abstract class PartP2PTunnel extends PartBasicState {
   private final TunnelCollection type = new TunnelCollection((Collection)null, this.getClass());
   public boolean output;
   public long freq;

   public PartP2PTunnel(ItemStack is) {
      super(is);
   }

   public TunnelCollection getCollection(Collection collection, Class c) {
      if (this.type.matches(c)) {
         this.type.setSource(collection);
         return this.type;
      } else {
         return null;
      }
   }

   public PartP2PTunnel getInput() {
      if (this.getFrequency() == 0L) {
         return null;
      } else {
         try {
            PartP2PTunnel tunnel = this.getProxy().getP2P().getInput(this.getFrequency());
            if (this.getClass().isInstance(tunnel)) {
               return tunnel;
            }
         } catch (GridAccessException var2) {
         }

         return null;
      }
   }

   public TunnelCollection getOutputs() throws GridAccessException {
      return this.getProxy().isActive() ? this.getProxy().getP2P().getOutputs(this.getFrequency(), this.getClass()) : new TunnelCollection(new ArrayList(), this.getClass());
   }

   public void getBoxes(IPartCollisionHelper bch) {
      bch.addBox((double)5.0F, (double)5.0F, (double)12.0F, (double)11.0F, (double)11.0F, (double)13.0F);
      bch.addBox((double)3.0F, (double)3.0F, (double)13.0F, (double)13.0F, (double)13.0F, (double)14.0F);
      bch.addBox((double)2.0F, (double)2.0F, (double)14.0F, (double)14.0F, (double)14.0F, (double)16.0F);
   }

   public void renderInventory(IPartRenderHelper rh, RenderBlocks renderer) {
      rh.setTexture(this.getTypeTexture());
      rh.setBounds(2.0F, 2.0F, 14.0F, 14.0F, 14.0F, 16.0F);
      rh.renderInventoryBox(renderer);
      rh.setTexture(CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.BlockP2PTunnel2.getIcon(), this.getItemStack().func_77954_c(), CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.PartTunnelSides.getIcon());
      rh.setBounds(2.0F, 2.0F, 14.0F, 14.0F, 14.0F, 16.0F);
      rh.renderInventoryBox(renderer);
   }

   public IIcon getTypeTexture() {
      Optional<Block> maybeBlock = AEApi.instance().definitions().blocks().quartz().maybeBlock();
      return maybeBlock.isPresent() ? ((Block)maybeBlock.get()).func_149691_a(0, 0) : Blocks.field_150371_ca.func_149691_a(0, 0);
   }

   public void renderStatic(int x, int y, int z, IPartRenderHelper rh, RenderBlocks renderer) {
      this.setRenderCache(rh.useSimplifiedRendering(x, y, z, this, this.getRenderCache()));
      rh.setTexture(this.getTypeTexture());
      rh.setBounds(2.0F, 2.0F, 14.0F, 14.0F, 14.0F, 16.0F);
      rh.renderBlock(x, y, z, renderer);
      rh.setTexture(CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.BlockP2PTunnel2.getIcon(), this.getItemStack().func_77954_c(), CableBusTextures.PartTunnelSides.getIcon(), CableBusTextures.PartTunnelSides.getIcon());
      rh.setBounds(2.0F, 2.0F, 14.0F, 14.0F, 14.0F, 16.0F);
      rh.renderBlock(x, y, z, renderer);
      rh.setBounds(3.0F, 3.0F, 13.0F, 13.0F, 13.0F, 14.0F);
      rh.renderBlock(x, y, z, renderer);
      rh.setTexture(CableBusTextures.BlockP2PTunnel3.getIcon());
      rh.setBounds(6.0F, 5.0F, 12.0F, 10.0F, 11.0F, 13.0F);
      rh.renderBlock(x, y, z, renderer);
      rh.setBounds(5.0F, 6.0F, 12.0F, 11.0F, 10.0F, 13.0F);
      rh.renderBlock(x, y, z, renderer);
      this.renderLights(x, y, z, rh, renderer);
   }

   public ItemStack getItemStack(PartItemStack type) {
      if (type != PartItemStack.World && type != PartItemStack.Network && type != PartItemStack.Wrench && type != PartItemStack.Pick) {
         Optional<ItemStack> maybeMEStack = AEApi.instance().definitions().parts().p2PTunnelME().maybeStack(1);
         return maybeMEStack.isPresent() ? (ItemStack)maybeMEStack.get() : super.getItemStack(type);
      } else {
         return super.getItemStack(type);
      }
   }

   public void readFromNBT(NBTTagCompound data) {
      super.readFromNBT(data);
      this.setOutput(data.func_74767_n("output"));
      this.setFrequency(data.func_74763_f("freq"));
   }

   public NBTTagCompound getMemoryCardData() {
      NBTTagCompound output = new NBTTagCompound();
      if (this.hasCustomName()) {
         NBTTagCompound dsp = new NBTTagCompound();
         dsp.func_74778_a("Name", this.getCustomName());
         output.func_74782_a("display", dsp);
      }

      output.func_74772_a("freq", this.getFrequency());
      return output;
   }

   public void pasteMemoryCardData(PartP2PTunnel newTunnel, NBTTagCompound data) throws GridAccessException {
      long freq = data.func_74763_f("freq");
      P2PCache p2p = newTunnel.getProxy().getP2P();
      p2p.updateFreq(newTunnel, freq);
      PartP2PTunnel input = p2p.getInput(freq);
      if (input != null) {
         newTunnel.setCustomNameInternal(input.getCustomName());
      }

   }

   public void writeToNBT(NBTTagCompound data) {
      super.writeToNBT(data);
      data.func_74757_a("output", this.isOutput());
      data.func_74772_a("freq", this.getFrequency());
   }

   public int cableConnectionRenderTo() {
      return 1;
   }

   public boolean useStandardMemoryCard() {
      return false;
   }

   public abstract boolean onPartActivate(EntityPlayer var1, Vec3 var2);

   protected void printConnectionInfo(EntityPlayer player) {
      if (this.isOutput()) {
         PartP2PTunnel input = this.getInput();
         if (input == null) {
            player.func_145747_a(PlayerMessages.TunnelNotConnected.get());
         } else {
            TileEntity t = input.getTile();
            player.func_145747_a(new ChatComponentTranslation(PlayerMessages.TunnelInputIsAt.getName(), new Object[]{t.field_145851_c, t.field_145848_d, t.field_145849_e}));
         }
      } else {
         try {
            TunnelCollection<T> oo = this.getOutputs();
            if (oo.isEmpty()) {
               player.func_145747_a(PlayerMessages.TunnelHasNoOutputs.get());
            } else {
               player.func_145747_a(PlayerMessages.TunnelOutputsAreAt.get());

               for(PartP2PTunnel t : oo) {
                  TileEntity te = t.getTile();
                  if (te != null) {
                     player.func_145747_a(new ChatComponentText("(" + te.field_145851_c + ", " + te.field_145848_d + ", " + te.field_145849_e + ")"));
                  }
               }
            }
         } catch (GridAccessException var6) {
            player.func_145747_a(PlayerMessages.TunnelNotConnected.get());
         }
      }

   }

   public boolean onPartShiftActivate(EntityPlayer player, Vec3 pos) {
      ItemStack is = player.field_71071_by.func_70448_g();
      if (is != null) {
         Item newFreq = is.func_77973_b();
         if (newFreq instanceof IMemoryCard) {
            IMemoryCard mc = (IMemoryCard)newFreq;
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) {
               return false;
            }

            long newFreq = this.getFrequency();
            boolean wasOutput = this.isOutput();
            this.setOutput(false);
            if (wasOutput || this.getFrequency() == 0L) {
               newFreq = System.currentTimeMillis();
            }

            try {
               this.getProxy().getP2P().updateFreq(this, newFreq);
            } catch (GridAccessException var11) {
            }

            this.onTunnelConfigChange();
            NBTTagCompound data = this.getMemoryCardData();
            ItemStack p2pItem = this.getItemStack(PartItemStack.Wrench);
            String type = p2pItem.func_77977_a();
            p2pItem.func_77955_b(data);
            mc.setMemoryCardContents(is, type + ".name", data);
            mc.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            return true;
         }
      }

      return false;
   }

   public void onTunnelConfigChange() {
   }

   public void onTunnelNetworkChange() {
   }

   public IIcon getBreakingTexture() {
      return CableBusTextures.BlockP2PTunnel2.getIcon();
   }

   protected void queueTunnelDrain(PowerUnits unit, double f) {
      double ae_to_tax = unit.convertTo(PowerUnits.AE, f * AEConfig.TUNNEL_POWER_LOSS);

      try {
         this.getProxy().getEnergy().extractAEPower(ae_to_tax, Actionable.MODULATE, PowerMultiplier.ONE);
      } catch (GridAccessException var7) {
      }

   }

   public long getFrequency() {
      return this.freq;
   }

   public void setFrequency(long freq) {
      this.freq = freq;
   }

   public boolean isOutput() {
      return this.output;
   }

   void setOutput(boolean output) {
      this.output = output;
   }

   public void setCustomName(String name) {
      T i = (T)this.getInput();
      if (i != null) {
         i.setCustomNameInternal(name);

         try {
            for(PartP2PTunnel o : this.getOutputs()) {
               o.setCustomNameInternal(name);
            }
         } catch (GridAccessException var5) {
         }
      } else {
         super.setCustomName(name);
      }

   }

   void setCustomNameInternal(String name) {
      super.setCustomName(name);
   }
}
