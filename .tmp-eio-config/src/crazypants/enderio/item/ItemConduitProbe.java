package crazypants.enderio.item;

import com.enderio.core.api.client.gui.IResourceTooltipProvider;
import com.enderio.core.common.util.ChatUtil;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIOTab;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.tool.IHideFacades;
import crazypants.enderio.conduit.IConduit;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.network.PacketHandler;
import java.text.NumberFormat;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class ItemConduitProbe extends Item implements IResourceTooltipProvider, IHideFacades {
   private static final NumberFormat NF = NumberFormat.getIntegerInstance();

   public static ItemConduitProbe create() {
      PacketHandler.INSTANCE.registerMessage(PacketConduitProbe.class, PacketConduitProbe.class, PacketHandler.nextID(), Side.SERVER);
      PacketHandler.INSTANCE.registerMessage(PacketConduitProbeMode.class, PacketConduitProbeMode.class, PacketHandler.nextID(), Side.SERVER);
      ItemConduitProbe result = new ItemConduitProbe();
      result.init();
      return result;
   }

   public static boolean copyPasteSettings(EntityPlayer player, ItemStack stack, IConduitBundle bundle, ForgeDirection dir) {
      boolean isCopy = player.func_70093_af();
      boolean clearedData = false;
      NBTTagCompound nbt = stack.field_77990_d;
      if (nbt == null) {
         nbt = new NBTTagCompound();
         stack.field_77990_d = nbt;
      }

      boolean performedAction = false;

      for(IConduit conduit : bundle.getConduits()) {
         if (conduit.getExternalConnections().contains(dir)) {
            if (isCopy && !clearedData) {
               nbt = new NBTTagCompound();
               stack.field_77990_d = nbt;
               clearedData = true;
            }

            if (isCopy) {
               performedAction |= conduit.writeConnectionSettingsToNBT(dir, nbt);
            } else {
               performedAction |= conduit.readConduitSettingsFromNBT(dir, nbt);
            }
         }
      }

      if (isCopy && performedAction && player.field_70170_p.field_72995_K) {
         ChatUtil.sendNoSpamClient(new String[]{"Copied conduit settings"});
      }

      return performedAction;
   }

   protected ItemConduitProbe() {
      this.func_77637_a(EnderIOTab.tabEnderIO);
      this.func_77655_b("enderio." + ModObject.itemConduitProbe.name());
      this.func_77625_d(1);
      this.func_77627_a(true);
   }

   public boolean func_77648_a(ItemStack itemStack, EntityPlayer player, World world, int x, int y, int z, int side, float par8, float par9, float par10) {
      TileEntity te = world.func_147438_o(x, y, z);
      if (!(te instanceof IConduitBundle)) {
         return false;
      } else if (itemStack.func_77960_j() == 0 && PacketConduitProbe.canCreatePacket(world, x, y, z)) {
         if (world.field_72995_K) {
            PacketHandler.INSTANCE.sendToServer(new PacketConduitProbe(x, y, z, side));
         }

         return true;
      } else {
         return false;
      }
   }

   protected void init() {
      GameRegistry.registerItem(this, ModObject.itemConduitProbe.unlocalisedName);
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister IIconRegister) {
      this.field_77791_bV = IIconRegister.func_94245_a("enderio:mJReader");
   }

   public String getUnlocalizedNameForTooltip(ItemStack stack) {
      return this.func_77658_a();
   }

   public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
      return true;
   }

   public boolean shouldHideFacades(ItemStack stack, EntityPlayer player) {
      return true;
   }
}
