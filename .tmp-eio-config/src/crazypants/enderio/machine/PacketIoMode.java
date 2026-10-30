package crazypants.enderio.machine;

import com.enderio.core.common.util.BlockCoord;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import crazypants.enderio.network.PacketUtil;
import crazypants.util.ForgeDirections;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class PacketIoMode implements IMessage, IMessageHandler {
   private int x;
   private int y;
   private int z;
   private IoMode mode;
   private ForgeDirection face;

   public PacketIoMode() {
   }

   public PacketIoMode(IIoConfigurable cont) {
      BlockCoord location = cont.getLocation();
      this.x = location.x;
      this.y = location.y;
      this.z = location.z;
      this.mode = IoMode.NONE;
      this.face = ForgeDirection.UNKNOWN;
   }

   public PacketIoMode(IIoConfigurable cont, ForgeDirection face) {
      BlockCoord location = cont.getLocation();
      this.x = location.x;
      this.y = location.y;
      this.z = location.z;
      this.face = face;
      this.mode = cont.getIoMode(face);
   }

   public void toBytes(ByteBuf buf) {
      buf.writeInt(this.x);
      buf.writeInt(this.y);
      buf.writeInt(this.z);
      buf.writeShort((short)this.mode.ordinal());
      buf.writeShort((short)this.face.ordinal());
   }

   public void fromBytes(ByteBuf buf) {
      this.x = buf.readInt();
      this.y = buf.readInt();
      this.z = buf.readInt();
      this.mode = IoMode.values()[buf.readShort()];
      this.face = ForgeDirections.DIRECTIONS[buf.readShort()];
   }

   public IMessage onMessage(PacketIoMode message, MessageContext ctx) {
      EntityPlayer player = ctx.getServerHandler().field_147369_b;
      TileEntity te = player.field_70170_p.func_147438_o(message.x, message.y, message.z);
      if (PacketUtil.isInvalidPacketForGui(ctx, te, this.getClass())) {
         return null;
      } else {
         if (te instanceof IIoConfigurable) {
            IIoConfigurable me = (IIoConfigurable)te;
            if (message.face == ForgeDirection.UNKNOWN) {
               me.clearAllIoModes();
            } else {
               me.setIoMode(message.face, message.mode);
            }
         }

         return null;
      }
   }
}
