package appeng.parts;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.parts.IPartRenderHelper;
import appeng.client.texture.CableBusTextures;
import appeng.me.GridAccessException;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class PartBasicState extends AEBasePart implements IPowerChannelState {
   protected static final int POWERED_FLAG = 1;
   protected static final int CHANNEL_FLAG = 2;
   protected static final int BOOTING_FLAG = 4;
   protected int clientFlags = 0;

   public PartBasicState(ItemStack is) {
      super(is);
      this.getProxy().setFlags(new GridFlags[]{GridFlags.REQUIRE_CHANNEL});
   }

   @MENetworkEventSubscribe
   public void chanRender(MENetworkChannelsChanged c) {
      this.getHost().markForUpdate();
   }

   @MENetworkEventSubscribe
   public void powerRender(MENetworkPowerStatusChange c) {
      this.getHost().markForUpdate();
   }

   public void renderLights(int x, int y, int z, IPartRenderHelper rh, RenderBlocks renderer) {
      rh.normalRendering();
      this.setColors((this.getClientFlags() & 3) == 3, (this.getClientFlags() & 1) == 1);
      rh.renderFace(x, y, z, CableBusTextures.PartMonitorSidesStatusLights.getIcon(), ForgeDirection.EAST, renderer);
      rh.renderFace(x, y, z, CableBusTextures.PartMonitorSidesStatusLights.getIcon(), ForgeDirection.WEST, renderer);
      rh.renderFace(x, y, z, CableBusTextures.PartMonitorSidesStatusLights.getIcon(), ForgeDirection.UP, renderer);
      rh.renderFace(x, y, z, CableBusTextures.PartMonitorSidesStatusLights.getIcon(), ForgeDirection.DOWN, renderer);
   }

   public void setColors(boolean hasChan, boolean hasPower) {
      if (hasChan) {
         int l = 14;
         Tessellator.field_78398_a.func_78380_c(14680288);
         Tessellator.field_78398_a.func_78378_d(this.getColor().blackVariant);
      } else if (hasPower) {
         int l = 9;
         Tessellator.field_78398_a.func_78380_c(9437328);
         Tessellator.field_78398_a.func_78378_d(this.getColor().whiteVariant);
      } else {
         Tessellator.field_78398_a.func_78380_c(0);
         Tessellator.field_78398_a.func_78378_d(0);
      }

   }

   public void writeToStream(ByteBuf data) throws IOException {
      super.writeToStream(data);
      this.setClientFlags(0);

      try {
         if (this.getProxy().getEnergy().isNetworkPowered()) {
            this.setClientFlags(this.getClientFlags() | 1);
         }

         if (this.getProxy().getNode().meetsChannelRequirements()) {
            this.setClientFlags(this.getClientFlags() | 2);
         }

         if (this.getProxy().getPath().isNetworkBooting()) {
            this.setClientFlags(this.getClientFlags() | 4);
         }

         this.setClientFlags(this.populateFlags(this.getClientFlags()));
      } catch (GridAccessException var3) {
      }

      data.writeByte((byte)this.getClientFlags());
   }

   protected int populateFlags(int cf) {
      return cf;
   }

   public boolean readFromStream(ByteBuf data) throws IOException {
      boolean eh = super.readFromStream(data);
      int old = this.getClientFlags();
      this.setClientFlags(data.readByte());
      return eh || old != this.getClientFlags();
   }

   public IIcon getBreakingTexture() {
      return CableBusTextures.PartTransitionPlaneBack.getIcon();
   }

   public boolean isPowered() {
      return (this.getClientFlags() & 1) == 1;
   }

   public boolean isActive() {
      return (this.getClientFlags() & 2) == 2;
   }

   public boolean isBooting() {
      return (this.getClientFlags() & 4) == 4;
   }

   public int getClientFlags() {
      return this.clientFlags;
   }

   private void setClientFlags(int clientFlags) {
      this.clientFlags = clientFlags;
   }
}
